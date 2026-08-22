package com.inf.farlands.mixin.render;

import javax.annotation.Nullable;

import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

import com.inf.farlands.Config;
import com.inf.farlands.WindowedChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ViewArea.class)
public abstract class ViewAreaMixin {

    @Shadow
    protected int sectionGridSizeX;
    @Shadow
    protected int sectionGridSizeY;
    @Shadow
    protected int sectionGridSizeZ;
    @Shadow
    private int viewDistance;
    @Shadow
    protected Level level;
    @Shadow
    public SectionRenderDispatcher.RenderSection[] sections;

    @Overwrite
    protected void setViewDistance(int renderDistanceChunks) {
        int i = renderDistanceChunks * 2 + 1;
        this.sectionGridSizeX = i;
        this.sectionGridSizeY = Config.verticalRenderDistance * 2 + 1;
        this.sectionGridSizeZ = i;
        this.viewDistance = renderDistanceChunks;
    }

    @SuppressWarnings("null")
    @Overwrite
    public void repositionCamera(double viewEntityX, double viewEntityZ) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        double viewEntityY = camera != null ? camera.getY() : 0.0;

        int ix = Mth.ceil(viewEntityX);
        int iy = Mth.ceil(viewEntityY);
        int iz = Mth.ceil(viewEntityZ);

        int spanX = this.sectionGridSizeX * 16;
        int spanY = this.sectionGridSizeY * 16;
        int spanZ = this.sectionGridSizeZ * 16;

        // long 运算防极端坐标溢出：baseY 在 -2.14B 时 int 溢出（-2147483632-288 < MIN）→
        // origin 垃圾 → 编译用 origin 推导错误 section → 放置方块不可见（dev/vanilla 路径）
        long baseX = (long) ix - 8 - spanX / 2;
        long baseY = (long) iy - 8 - spanY / 2;
        long baseZ = (long) iz - 8 - spanZ / 2;

        for (int kx = 0; kx < this.sectionGridSizeX; kx++) {
            long originX = baseX + Math.floorMod((long) kx * 16 - baseX, spanX);

            for (int kz = 0; kz < this.sectionGridSizeZ; kz++) {
                long originZ = baseZ + Math.floorMod((long) kz * 16 - baseZ, spanZ);

                for (int ky = 0; ky < this.sectionGridSizeY; ky++) {
                    long originY = baseY + Math.floorMod((long) ky * 16 - baseY, spanY);

                    SectionRenderDispatcher.RenderSection section = this.sections[gridIndex(kx, ky, kz)];
                    BlockPos origin = section.getOrigin();
                    if ((int) originX != origin.getX() || (int) originY != origin.getY()
                            || (int) originZ != origin.getZ()) {
                        section.setOrigin((int) originX, (int) originY, (int) originZ);
                    }
                }
            }
        }

        // Z: 客户端窗口跟随相机 Y（区块线/视图始终在玩家周围）。
        // 覆盖全部已加载 chunk：RenderChunk 编译快照取 windowSections，
        // 非玩家 chunk 的窗口无人维护会塌缩到构造默认（1 section）→ 不渲染。
        // 每帧调用，buildWindow 早退保证窗口未变时零开销。
        if (camera != null) {
            int camSecY = Mth.floorDiv(Mth.floor(camera.getY()), 16);
            ChunkPos cpos = camera.chunkPosition();
            int radius = this.sectionGridSizeX / 2;
            for (int cx = cpos.x - radius; cx <= cpos.x + radius; cx++) {
                for (int cz = cpos.z - radius; cz <= cpos.z + radius; cz++) {
                    LevelChunk chunk = (LevelChunk) this.level.getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk != null && !(chunk instanceof EmptyLevelChunk)) {
                        ((WindowedChunk) chunk).moveWindowTo(camSecY);
                        discardOutsideHoldBoundary(chunk); // §7.3 滑出丢弃
                    }
                }
            }
        }
    }

    @Overwrite
    public void setDirty(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread) {
        int ix = Math.floorMod(sectionX, this.sectionGridSizeX);
        int iy = Math.floorMod(sectionY, this.sectionGridSizeY);
        int iz = Math.floorMod(sectionZ, this.sectionGridSizeZ);
        this.sections[gridIndex(ix, iy, iz)].setDirty(reRenderOnMainThread);
    }

    @Nullable
    @Overwrite
    protected SectionRenderDispatcher.RenderSection getRenderSectionAt(BlockPos pos) {
        int ix = Mth.positiveModulo(Mth.floorDiv(pos.getX(), 16), this.sectionGridSizeX);
        int iy = Math.floorMod(SectionPos.blockToSectionCoord(pos.getY()), this.sectionGridSizeY);
        int iz = Mth.positiveModulo(Mth.floorDiv(pos.getZ(), 16), this.sectionGridSizeZ);
        return this.sections[gridIndex(ix, iy, iz)];
    }

    private int gridIndex(int x, int y, int z) {
        return (z * this.sectionGridSizeY + y) * this.sectionGridSizeX + x;
    }

    /**
     * §7.3 滑出丢弃（修订：丢弃参考 = 持有边界 ∪ 视图窗口并集）。
     * 持有边界滞后于玩家窗口（无数据 chunk 不发 section 包、快速移动包延迟），
     * 单独用它判定会把 view 内新数据误丢（实测 C4 证据）；view 每帧实时。
     * 保护区间 = [min(hold, view)-2, max(hold, view)+2]，两者之外才丢。
     * 空 section（懒创建产物）不丢——丢弃会触发光照查询再懒创建，形成每帧循环。
     * 滑回由服务端 difference 重发（数据源 = 服务端内存，§8 闭环）。
     */
    @SuppressWarnings("null")
    private void discardOutsideHoldBoundary(LevelChunk chunk) {
        WindowedChunk wc = (WindowedChunk) chunk;
        int viewMin = wc.getWindowMinY();
        int viewMax = wc.getWindowMaxY();
        int holdMin = wc.lastPacketMinY();
        int dropBelow;
        int dropAbove;
        if (holdMin == Integer.MIN_VALUE) {
            dropBelow = viewMin - 2;
            dropAbove = viewMax + 2;
        } else {
            dropBelow = Math.min(holdMin, viewMin) - 2;
            dropAbove = Math.max(wc.lastPacketMaxY(), viewMax) + 2;
        }
        ChunkPos cpos = chunk.getPos();
        LevelLightEngine le = this.level.getLightEngine();
        Iterator<Map.Entry<Integer, LevelChunkSection>> it = wc.windowedAllSections().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, LevelChunkSection> e = it.next();
            int sy = e.getKey();
            LevelChunkSection s = e.getValue();
            if (s == null || s.hasOnlyAir() || (sy >= dropBelow && sy <= dropAbove)) {
                continue;
            }
            it.remove();
            SectionPos spos = SectionPos.of(cpos, sy);
            le.queueSectionData(LightLayer.BLOCK, spos, null);
            le.queueSectionData(LightLayer.SKY, spos, null);
            le.updateSectionStatus(spos, true);
        }
    }
}
