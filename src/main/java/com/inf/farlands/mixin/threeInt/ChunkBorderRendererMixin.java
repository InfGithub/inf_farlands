package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.Constants;
import com.inf.farlands.WorldBoxRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 自定义区块线（三层，粒度从粗到细）：
 * 蓝 3.0：玩家 3x3x3 section 的 16 格线框网格（48 条贯穿线，48x48x48 范围）
 * （跳过玩家 section 的 12 条棱——白层负责，避免同位置重叠 z-fighting 闪烁）
 * 红 4.0：玩家 section 所有面的 2 格刻度（2/6/10/14）
 * 黄 1.5：十字 7 section——中心 4 格步进（4/8/12），邻居 8 格步进（仅 8 处）
 * 白 5.5：玩家 section 的 12 条棱（立方体线框）
 * 粒度错开（蓝 16 / 红 2,6,10,14 / 黄 4,8,12）天然无重叠；
 * 共享面/共享边 = 同一平面同一位置，重复画视觉重合，无需去重标志。
 * 坐标全为相对相机的 float 差值（block 坐标用 double 计算，
 * 极端 Y 下 secY*16+32 会溢出 int，double 全程规避）；每条线 4 顶点
 * （首尾重合 = GL_LINE_STRIP 退化线段断线，必须）。
 */
@Mixin(ChunkBorderRenderer.class)
public class ChunkBorderRendererMixin implements WorldBoxRenderer {

    @Shadow
    @Final
    private Minecraft minecraft;

    /** 十字形 7 个 section 偏移（相对玩家 section，section 单位）。 */
    @Unique
    private static final int[][] RED_OFFSETS = {
            { 0, 0, 0 }, { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }
    };

    /** 一条线 4 顶点 fade 模式（v0/v1 重合、v2/v3 重合 = 退化线段断线）。 */
    @SuppressWarnings("null")
    @Unique
    private static void addLine(VertexConsumer consumer, Matrix4f m,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r, float g, float b) {
        consumer.addVertex(m, x1, y1, z1).setColor(r, g, b, 0.0F);
        consumer.addVertex(m, x1, y1, z1).setColor(r, g, b, 1.0F);
        consumer.addVertex(m, x2, y2, z2).setColor(r, g, b, 1.0F);
        consumer.addVertex(m, x2, y2, z2).setColor(r, g, b, 0.0F);
    }

    /** 渐变线：两端颜色不同，实际线段（v1→v2）颜色插值。 */
    @SuppressWarnings("null")
    @Unique
    private static void addLineGradient(VertexConsumer consumer, Matrix4f m,
            float x1, float y1, float z1, float x2, float y2, float z2,
            float r1, float g1, float b1, float r2, float g2, float b2) {
        consumer.addVertex(m, x1, y1, z1).setColor(r1, g1, b1, 0.0F);
        consumer.addVertex(m, x1, y1, z1).setColor(r1, g1, b1, 1.0F);
        consumer.addVertex(m, x2, y2, z2).setColor(r2, g2, b2, 1.0F);
        consumer.addVertex(m, x2, y2, z2).setColor(r2, g2, b2, 0.0F);
    }

    /**
     * 面内刻度网格：固定轴 axis（min 或 max 侧），另两轴以 step 步进画线。
     * 不含面边界（0/16 位置由粗粒度层负责）；细粒度层（step < 4）跳过
     * 粗粒度位置（黄跳过 4/8/12，红蓝负责）——粒度错开无重叠。
     */
    @Unique
    private static void drawFaceGrid(VertexConsumer consumer, Matrix4f m,
            float xMin, float yMin, float zMin, int axis, boolean maxSide, int step,
            float r, float g, float b) {
        float[] min = { xMin, yMin, zMin };
        float[] max = { xMin + 16.0F, yMin + 16.0F, zMin + 16.0F };
        float fixed = maxSide ? max[axis] : min[axis];
        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;
        for (int v = step; v <= 16 - step; v += step) {
            if (step < 4 && (v & 3) == 0) {
                continue;
            }
            float[] p = new float[3];
            float[] q = new float[3];
            p[axis] = q[axis] = fixed;
            p[a1] = min[a1];
            q[a1] = max[a1];
            p[a2] = min[a2] + v;
            q[a2] = min[a2] + v;
            addLine(consumer, m, p[0], p[1], p[2], q[0], q[1], q[2], r, g, b);
            p[a1] = min[a1] + v;
            q[a1] = min[a1] + v;
            p[a2] = min[a2];
            q[a2] = max[a2];
            addLine(consumer, m, p[0], p[1], p[2], q[0], q[1], q[2], r, g, b);
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Entity entity = this.minecraft.gameRenderer.getMainCamera().getEntity();
        ChunkPos chunkpos = entity.chunkPosition();
        int secY = Mth.floorDiv(entity.getBlockY(), 16);
        float f2 = (float) ((double) chunkpos.getMinBlockX() - camX);
        float f3 = (float) ((double) chunkpos.getMinBlockZ() - camZ);
        float fY0 = (float) ((double) secY * 16.0 - camY);
        Matrix4f matrix4f = poseStack.last().pose();

        // ---- 蓝层（3.0）：3x3x3 section 线框，每段 16 格（132 段）----
        // 只跳过中央 section 的 12 条棱段（白层 5.5 负责）——同位置重叠会 z-fighting 闪烁。
        // 贯穿线按段拆分：格点 (dy,dz) 的 X 向线属于 3 个 section（sx ∈ {-1,0,1}），
        // 跳过整条会误删邻居 section 的同格点棱（缺 8/12 的 bug），只跳过 sx==0 段。
        VertexConsumer blue = bufferSource.getBuffer(RenderType.debugLineStrip(3.0));
        for (int dy = -1; dy <= 2; dy++) {
            for (int dz = -1; dz <= 2; dz++) {
                for (int sx = -1; sx <= 1; sx++) {
                    if (sx == 0 && dy >= 0 && dy <= 1 && dz >= 0 && dz <= 1) {
                        continue;
                    }
                    float y = fY0 + dy * 16.0F;
                    float z = f3 + dz * 16.0F;
                    float x1 = f2 + sx * 16.0F;
                    addLine(blue, matrix4f, x1, y, z, x1 + 16.0F, y, z, 0.0F, 0.0F, 1.0F);
                }
            }
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                for (int sy = -1; sy <= 1; sy++) {
                    if (sy == 0 && dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1) {
                        continue;
                    }
                    float x = f2 + dx * 16.0F;
                    float z = f3 + dz * 16.0F;
                    float y1 = fY0 + sy * 16.0F;
                    addLine(blue, matrix4f, x, y1, z, x, y1 + 16.0F, z, 0.0F, 0.0F, 1.0F);
                }
            }
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int sz = -1; sz <= 1; sz++) {
                    if (sz == 0 && dx >= 0 && dx <= 1 && dy >= 0 && dy <= 1) {
                        continue;
                    }
                    float x = f2 + dx * 16.0F;
                    float y = fY0 + dy * 16.0F;
                    float z1 = f3 + sz * 16.0F;
                    addLine(blue, matrix4f, x, y, z1, x, y, z1 + 16.0F, 0.0F, 0.0F, 1.0F);
                }
            }
        }

        // ---- 红层（4.0）：玩家 section 所有面 2 格步进（2/6/10/14）----
        VertexConsumer red = bufferSource.getBuffer(RenderType.debugLineStrip(4.0));
        for (int axis = 0; axis < 3; axis++) {
            drawFaceGrid(red, matrix4f, f2, fY0, f3, axis, false, 2, 1.0F, 0.0F, 0.0F);
            drawFaceGrid(red, matrix4f, f2, fY0, f3, axis, true, 2, 1.0F, 0.0F, 0.0F);
        }

        // ---- 黄层（1.5）：十字 7 section——中心 4 格步进（4/8/12），邻居 8 格步进（仅 8 处）----
        VertexConsumer yellow = bufferSource.getBuffer(RenderType.debugLineStrip(1.5));
        for (int[] off : RED_OFFSETS) {
            int step = (off[0] == 0 && off[1] == 0 && off[2] == 0) ? 4 : 8;
            float ox = f2 + off[0] * 16.0F;
            float oy = fY0 + off[1] * 16.0F;
            float oz = f3 + off[2] * 16.0F;
            for (int axis = 0; axis < 3; axis++) {
                drawFaceGrid(yellow, matrix4f, ox, oy, oz, axis, false, step, 1.0F, 1.0F, 0.0F);
                drawFaceGrid(yellow, matrix4f, ox, oy, oz, axis, true, step, 1.0F, 1.0F, 0.0F);
            }
        }

        // ---- 白层（5.5）：玩家 section 的 12 条棱（立方体线框，蓝层已跳过同位置）----
        VertexConsumer white = bufferSource.getBuffer(RenderType.debugLineStrip(5.5));
        for (int dy = 0; dy <= 1; dy++) {
            float y = fY0 + dy * 16.0F;
            for (int dz = 0; dz <= 1; dz++) {
                float z = f3 + dz * 16.0F;
                addLine(white, matrix4f, f2, y, z, f2 + 16.0F, y, z, 1.0F, 1.0F, 1.0F);
            }
        }
        for (int dx = 0; dx <= 1; dx++) {
            float x = f2 + dx * 16.0F;
            for (int dz = 0; dz <= 1; dz++) {
                float z = f3 + dz * 16.0F;
                addLine(white, matrix4f, x, fY0, z, x, fY0 + 16.0F, z, 1.0F, 1.0F, 1.0F);
            }
        }
        for (int dx = 0; dx <= 1; dx++) {
            float x = f2 + dx * 16.0F;
            for (int dy = 0; dy <= 1; dy++) {
                float y = fY0 + dy * 16.0F;
                addLine(white, matrix4f, x, y, f3, x, y, f3 + 16.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    /**
     * 世界盒（RGB 渐变，pi 线宽）：位置比例盒，边长 256。
     * 独立于区块线渲染（DebugRenderer 三态：state 1 只画世界盒、state 2 全画）。
     * 顶点颜色 = 世界方向（R=(sx+1)/2, G=(sy+1)/2, B=(sz+1)/2）→ RGB 立方体，
     * 棱上两端颜色插值渐变；颜色贴世界坐标（玩家移动不变色）。
     * 8 顶点 = 盒中心 ± 128，盒中心 = cam − p×128（p = cam/MAX_BLOCK 归一化位置）
     * → 玩家在盒内位置 = 世界位置的比例映射，任何位置相对几何真实。
     * 顶点相对相机 = 128×(±1 − p)，只依赖 p（与 cam 无关），值域 [−256, 256] float 精确。
     */
    @SuppressWarnings("null")
    @Override
    public void renderWorldBox(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY,
            double camZ) {
        Matrix4f matrix4f = poseStack.last().pose();
        VertexConsumer world = bufferSource.getBuffer(RenderType.debugLineStrip(2.7182818 + 3.1415926));
        double pX = Mth.clamp(camX / Constants.MAX_BLOCK, -1.0, 1.0);
        double pY = Mth.clamp(camY / Constants.MAX_BLOCK, -1.0, 1.0);
        double pZ = Mth.clamp(camZ / Constants.MAX_BLOCK, -1.0, 1.0);
        float h = 128.0F;
        for (int sy = -1; sy <= 1; sy += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float y = h * ((float) sy - (float) pY);
                float z = h * ((float) sz - (float) pZ);
                float x1 = h * (-1.0F - (float) pX);
                float x2 = h * (1.0F - (float) pX);
                float gy = (sy + 1) * 0.5F;
                float gz = (sz + 1) * 0.5F;
                addLineGradient(world, matrix4f, x1, y, z, x2, y, z, 0.0F, gy, gz, 1.0F, gy, gz);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                float x = h * ((float) sx - (float) pX);
                float z = h * ((float) sz - (float) pZ);
                float y1 = h * (-1.0F - (float) pY);
                float y2 = h * (1.0F - (float) pY);
                float gx = (sx + 1) * 0.5F;
                float gz = (sz + 1) * 0.5F;
                addLineGradient(world, matrix4f, x, y1, z, x, y2, z, gx, 0.0F, gz, gx, 1.0F, gz);
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                float x = h * ((float) sx - (float) pX);
                float y = h * ((float) sy - (float) pY);
                float z1 = h * (-1.0F - (float) pZ);
                float z2 = h * (1.0F - (float) pZ);
                float gx = (sx + 1) * 0.5F;
                float gy = (sy + 1) * 0.5F;
                addLineGradient(world, matrix4f, x, y, z1, x, y, z2, gx, gy, 0.0F, gx, gy, 1.0F);
            }
        }
    }
}
