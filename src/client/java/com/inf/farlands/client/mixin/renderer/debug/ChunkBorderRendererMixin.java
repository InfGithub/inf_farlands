package com.inf.farlands.client.mixin.renderer.debug;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.ChunkBorderRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.debug.DebugValueAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

@Mixin(ChunkBorderRenderer.class)
public class ChunkBorderRendererMixin {

    @Shadow
    private Minecraft minecraft;

    private static final int[][] edges = {
            { 0, 1 }, // X轴方向:
            { 0, 2 }, // Y轴方向:
            { 0, 4 }, // Z轴方向:
            { 1, 3 },
            { 1, 5 },
            { 2, 3 },
            { 2, 6 },
            { 3, 7 },
            { 4, 5 },
            { 4, 6 },
            { 5, 7 },
            { 6, 7 }
    };

    private static final int[][] vertices = {
            { 0, 0, 0 }, { 1, 0, 0 }, { 0, 1, 0 }, { 1, 1, 0 },
            { 0, 0, 1 }, { 1, 0, 1 }, { 0, 1, 1 }, { 1, 1, 1 }
    };
    private static final int[][] RED_OFFSETS = {
            { 0, 0, 0 }, { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }
    };

    private static void drawFaceGrid(
            double xMin,
            double yMin,
            double zMin,
            int axis,
            boolean maxSide,
            int step,
            int color,
            float width) {
        double[] min = { xMin, yMin, zMin };
        double[] max = { xMin + 16.0, yMin + 16.0, zMin + 16.0 };

        double fixed = maxSide ? max[axis] : min[axis];

        int a1 = (axis + 1) % 3;
        int a2 = (axis + 2) % 3;

        double[] p = new double[3];
        double[] q = new double[3];

        for (int v = step; v <= 16 - step; v += step) {
            if (step < 4 && (v & 3) == 0) {
                continue;
            }

            p[axis] = fixed;
            q[axis] = fixed;
            p[a1] = min[a1];
            q[a1] = max[a1];
            p[a2] = min[a2] + v;
            q[a2] = min[a2] + v;

            Gizmos.line(
                    new Vec3(p[0], p[1], p[2]),
                    new Vec3(q[0], q[1], q[2]),
                    color,
                    width);

            p[a1] = min[a1] + v;
            q[a1] = min[a1] + v;
            p[a2] = min[a2];
            q[a2] = max[a2];

            Gizmos.line(
                    new Vec3(p[0], p[1], p[2]),
                    new Vec3(q[0], q[1], q[2]),
                    color,
                    width);
        }
    }

    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void emitGizmos(
            final double camX,
            final double camY,
            final double camZ,
            final DebugValueAccess debugValues,
            final Frustum frustum,
            final float partialTicks,
            CallbackInfo ci) {
        Entity cameraEntity = this.minecraft.getCameraEntity();
        SectionPos cameraPos = SectionPos.of(cameraEntity.blockPosition());
        double xstart = cameraPos.minBlockX();
        double ystart = cameraPos.minBlockY();
        double zstart = cameraPos.minBlockZ();

        // 蓝线，3x3x3 sec，但跳过中间
        for (int dy = -1; dy <= 2; dy++) {
            for (int dz = -1; dz <= 2; dz++) {
                for (int sx = -1; sx <= 1; sx++) {
                    if (sx == 0 && dy >= 0 && dy <= 1 && dz >= 0 && dz <= 1) {
                        continue;
                    }

                    double x = xstart + sx * 16.0;
                    double y = ystart + dy * 16.0;
                    double z = zstart + dz * 16.0;
                    Gizmos.line(
                            new Vec3(x, y, z),
                            new Vec3(x + 16.0, y, z),
                            0xFF0000FF,
                            2.0F);
                }
            }
        }

        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                for (int sy = -1; sy <= 1; sy++) {
                    if (sy == 0 && dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1) {
                        continue;
                    }
                    double x = xstart + dx * 16.0;
                    double z = zstart + dz * 16.0;
                    double y = ystart + sy * 16.0;
                    Gizmos.line(
                            new Vec3(x, y, z),
                            new Vec3(x, y + 16.0, z),
                            0xFF0000FF,
                            2.0F);
                }
            }
        }

        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int sz = -1; sz <= 1; sz++) {
                    if (sz == 0 && dx >= 0 && dx <= 1 && dy >= 0 && dy <= 1) {
                        continue;
                    }
                    double x = xstart + dx * 16.0;
                    double y = ystart + dy * 16.0;
                    double z = zstart + sz * 16.0;
                    Gizmos.line(
                            new Vec3(x, y, z),
                            new Vec3(x, y, z + 16.0),
                            0xFF0000FF,
                            2.0F);
                }
            }
        }

        // 红线
        for (int axis = 0; axis < 3; axis++) {
            drawFaceGrid(xstart, ystart, zstart, axis, false, 2, 0xFFFF0000, 2.5F);
            drawFaceGrid(xstart, ystart, zstart, axis, true, 2, 0xFFFF0000, 2.5F);
        }

        // 黄线
        for (int[] off : RED_OFFSETS) {
            int step = (off[0] == 0 && off[1] == 0 && off[2] == 0) ? 4 : 8;
            double ox = xstart + off[0] * 16.0;
            double oy = ystart + off[1] * 16.0;
            double oz = zstart + off[2] * 16.0;

            for (int axis = 0; axis < 3; axis++) {
                drawFaceGrid(ox, oy, oz, axis, false, step, 0xFFFFFF00, 1.5F);
                drawFaceGrid(ox, oy, oz, axis, true, step, 0xFFFFFF00, 1.5F);
            }
        }
        // 白线，1x1x1 sec
        for (int i = 0; i < edges.length; i++) {
            int v1 = edges[i][0];
            int v2 = edges[i][1];

            Gizmos.line(
                    new Vec3(
                            xstart + vertices[v1][0] * 16.0,
                            ystart + vertices[v1][1] * 16.0,
                            zstart + vertices[v1][2] * 16.0),
                    new Vec3(
                            xstart + vertices[v2][0] * 16.0,
                            ystart + vertices[v2][1] * 16.0,
                            zstart + vertices[v2][2] * 16.0),
                    0xFFFFFFFF,
                    3.0F);
        }
        ci.cancel();
    }
}
