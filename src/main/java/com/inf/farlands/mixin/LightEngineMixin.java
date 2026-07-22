package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightEngine.class)
public abstract class LightEngineMixin {

    @Shadow
    private LongOpenHashSet blockNodesToCheck;
    @Shadow
    protected abstract void clearChunkCache();
    @Shadow
    protected abstract int propagateIncreases();
    @Shadow
    protected abstract int propagateDecreases();
    @Shadow
    protected abstract void checkNode(long packedPos);

    /**
     * Refresh side-channel TTL before the lighting engine touches a section position.
     * ThreadedLevelLightEngine queues tasks asynchronously; section entries must survive
     * however long the queue backlog is.  TTL is 6000 ticks — this is the safety net.
     */
    @Inject(method = "updateSectionStatus", at = @At("HEAD"))
    private void refreshSectionLastAccess(SectionPos pos, boolean isQueueEmpty, CallbackInfo ci) {
        SectionPos.x(pos.asLong()); // triggers lastAccess = tickCounter in SectionPosMixin.x()
    }

    @Overwrite
    public void checkBlock(BlockPos pos) {
        IntBlockPos ibp = new IntBlockPos(pos);
        long key = HashUtil.hashPos((long)ibp.x, (long)ibp.y, (long)ibp.z);
        HashUtil.putBlock(key, ibp);
        this.blockNodesToCheck.add(key);
    }

    @Overwrite
    public int runLightUpdates() {
        LongIterator it = this.blockNodesToCheck.iterator();
        while (it.hasNext()) this.checkNode(it.nextLong());
        this.blockNodesToCheck.clear();
        this.blockNodesToCheck.trim(512);
        int i = this.propagateDecreases() + this.propagateIncreases();
        this.clearChunkCache();
        try {
            MARK_NEW_INCONSISTENCIES.invoke(this.storage, this);
            SWAP_SECTION_MAP.invoke(this.storage);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return i;
    }

    private static final java.lang.reflect.Method MARK_NEW_INCONSISTENCIES;
    private static final java.lang.reflect.Method SWAP_SECTION_MAP;
    static {
        try {
            MARK_NEW_INCONSISTENCIES = LayerLightSectionStorage.class
                .getDeclaredMethod("markNewInconsistencies", LightEngine.class);
            MARK_NEW_INCONSISTENCIES.setAccessible(true);
            SWAP_SECTION_MAP = LayerLightSectionStorage.class
                .getDeclaredMethod("swapSectionMap");
            SWAP_SECTION_MAP.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Overwrite
    public int getLightValue(BlockPos levelPos) {
        IntBlockPos ibp = new IntBlockPos(levelPos);
        long key = HashUtil.hashPos((long)ibp.x, (long)ibp.y, (long)ibp.z);
        HashUtil.putBlock(key, ibp);
        long secKey = SectionPos.asLong(
            SectionPos.blockToSectionCoord(ibp.x),
            SectionPos.blockToSectionCoord(ibp.y),
            SectionPos.blockToSectionCoord(ibp.z)
        );
        DataLayer dl = HashUtil.callGetDataLayer(this.storage, secKey, false);
        if (dl == null) return 15;
        return dl.get(
            SectionPos.sectionRelative(ibp.x),
            SectionPos.sectionRelative(ibp.y),
            SectionPos.sectionRelative(ibp.z)
        );
    }

    @Shadow
    private LayerLightSectionStorage storage;
}
