package com.inf.farlands.mixin.outsideWorld;

import com.inf.farlands.Config;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;

/**
 * 六面体边界渲染：renderWorldBorder 六面体版。
 *
 * vanilla 四面板的 Y 顶点 = 相机 ± depthFar，跟随相机的 1280 格高墙——XZ 墙在 Y 上
 * 无世界边界；地板/天花板不渲染。六面体版：
 * 1. XZ 四面板 Y 顶点 = [minY, maxY]，±(borderAbsoluteMax-16)，与 XZ 墙对齐，固定世界 Y 边界
 * 2. 新增地板 y=minY / 天花板 y=maxY 面板，XZ 段 = 视距内
 * 3. 触发条件/透明度三维化：到六面体最近面距离 < d0
 * 4. floorSafe/ceilSafe 并入：Mth.floor/ceil int 溢出在 ±2.14B 边界
 * 5. outside=true → 不渲染
 *
 * float 精度安全：面板顶点 = 边界 - 相机，玩家靠近面板时差值小；玩家距某面 > d0
 * 时该面在视锥外，触发条件保证，2^31 处 float 误差 <=128 格，不可见。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

        @Shadow
        private Minecraft minecraft;

        @Shadow
        private ClientLevel level;

        @Shadow
        @Final
        private static ResourceLocation FORCEFIELD_LOCATION;

        @Unique
        private static int floorSafe(double value) {
                double f = Math.floor(value);
                return f > Integer.MAX_VALUE ? Integer.MAX_VALUE
                                : f < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) f;
        }

        @Unique
        private static int ceilSafe(double value) {
                double c = Math.ceil(value);
                return c > Integer.MAX_VALUE ? Integer.MAX_VALUE
                                : c < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) c;
        }

        @SuppressWarnings("null")
        @Overwrite
        private void renderWorldBorder(Camera camera) {
                if (Config.outside) {
                        return;
                }
                WorldBorder worldborder = this.level.getWorldBorder();
                double d0 = (double) (this.minecraft.options.getEffectiveRenderDistance() * 16);
                double limit = Config.borderAbsoluteMax - 16.0;
                double minY = -limit;
                double maxY = limit;
                double camX = camera.getPosition().x;
                double camY = camera.getPosition().y;
                double camZ = camera.getPosition().z;

                // 触发条件：到六面体最近面距离 < d0
                double dXZ = Math.min(
                                Math.min(camX - worldborder.getMinX(), worldborder.getMaxX() - camX),
                                Math.min(camZ - worldborder.getMinZ(), worldborder.getMaxZ() - camZ));
                double dY = Math.min(camY + limit, limit - camY);
                if (Math.min(dXZ, dY) >= d0) {
                        return;
                }

                double d1 = 1.0 - Math.min(dXZ, dY) / d0;
                d1 = Math.pow(d1, 4.0);
                d1 = Mth.clamp(d1, 0.0, 1.0);
                double d4 = (double) this.minecraft.gameRenderer.getDepthFar();
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.blendFuncSeparate(
                                SourceFactor.SRC_ALPHA,
                                DestFactor.ONE,
                                SourceFactor.ONE,
                                DestFactor.ZERO);
                RenderSystem.setShaderTexture(0, FORCEFIELD_LOCATION);
                RenderSystem.depthMask(Minecraft.useShaderTransparency());
                int i = worldborder.getStatus().getColor();
                float f = (float) (i >> 16 & 0xFF) / 255.0F;
                float f1 = (float) (i >> 8 & 0xFF) / 255.0F;
                float f2 = (float) (i & 0xFF) / 255.0F;
                RenderSystem.setShaderColor(f, f1, f2, (float) d1);
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.polygonOffset(-3.0F, -3.0F);
                RenderSystem.enablePolygonOffset();
                RenderSystem.disableCull();
                float f3 = (float) (Util.getMillis() % 3000L) / 3000.0F;
                float f4 = (float) (-Mth.frac(camera.getPosition().y * 0.5));
                float f5 = f4 + (float) d4;
                BufferBuilder bufferbuilder = Tesselator.getInstance().begin(
                                VertexFormat.Mode.QUADS,
                                DefaultVertexFormat.POSITION_TEX);

                // XZ 四面板竖直范围 = 视锥 ±depthFar 与 世界 Y 边界：
                // 玩家在 Y 中间时墙高 = 原版 1280 格，纹理密度正常；靠近地板/天花板时
                // 墙在边界截断，六面体闭合不穿透。V 保持原版 f4/f5 跨度，不拉伸。
                double yLo = Mth.clamp(camY - d4, minY, maxY);
                double yHi = Mth.clamp(camY + d4, minY, maxY);

                // ---- XZ 四面板：Y 顶点 = [yLo, yHi] 相对相机 ----
                double d5 = Math.max((double) floorSafe(camZ - d0), worldborder.getMinZ());
                double d6 = Math.min((double) ceilSafe(camZ + d0), worldborder.getMaxZ());
                float f6 = (float) (floorSafe(d5) & 1) * 0.5F;

                if (camX > worldborder.getMaxX() - d0) {
                        float f7 = f6;
                        for (double d7 = d5; d7 < d6; f7 += 0.5F) {
                                double d8 = Math.min(1.0, d6 - d7);
                                float f8 = (float) d8 * 0.5F;
                                bufferbuilder
                                                .addVertex((float) (worldborder.getMaxX() - camX), (float) (yLo - camY),
                                                                (float) (d7 - camZ))
                                                .setUv(f3 - f7, f3 + f5);
                                bufferbuilder.addVertex((float) (worldborder.getMaxX() - camX), (float) (yLo - camY),
                                                (float) (d7 + d8 - camZ)).setUv(f3 - (f8 + f7), f3 + f5);
                                bufferbuilder.addVertex((float) (worldborder.getMaxX() - camX), (float) (yHi - camY),
                                                (float) (d7 + d8 - camZ)).setUv(f3 - (f8 + f7), f3 + f4);
                                bufferbuilder
                                                .addVertex((float) (worldborder.getMaxX() - camX), (float) (yHi - camY),
                                                                (float) (d7 - camZ))
                                                .setUv(f3 - f7, f3 + f4);
                                d7++;
                        }
                }

                if (camX < worldborder.getMinX() + d0) {
                        float f9 = f6;
                        for (double d9 = d5; d9 < d6; f9 += 0.5F) {
                                double d12 = Math.min(1.0, d6 - d9);
                                float f12 = (float) d12 * 0.5F;
                                bufferbuilder
                                                .addVertex((float) (worldborder.getMinX() - camX), (float) (yLo - camY),
                                                                (float) (d9 - camZ))
                                                .setUv(f3 + f9, f3 + f5);
                                bufferbuilder.addVertex((float) (worldborder.getMinX() - camX), (float) (yLo - camY),
                                                (float) (d9 + d12 - camZ)).setUv(f3 + f12 + f9, f3 + f5);
                                bufferbuilder.addVertex((float) (worldborder.getMinX() - camX), (float) (yHi - camY),
                                                (float) (d9 + d12 - camZ)).setUv(f3 + f12 + f9, f3 + f4);
                                bufferbuilder
                                                .addVertex((float) (worldborder.getMinX() - camX), (float) (yHi - camY),
                                                                (float) (d9 - camZ))
                                                .setUv(f3 + f9, f3 + f4);
                                d9++;
                        }
                }

                d5 = Math.max((double) floorSafe(camX - d0), worldborder.getMinX());
                d6 = Math.min((double) ceilSafe(camX + d0), worldborder.getMaxX());
                f6 = (float) (floorSafe(d5) & 1) * 0.5F;
                if (camZ > worldborder.getMaxZ() - d0) {
                        float f10 = f6;
                        for (double d10 = d5; d10 < d6; f10 += 0.5F) {
                                double d13 = Math.min(1.0, d6 - d10);
                                float f13 = (float) d13 * 0.5F;
                                bufferbuilder
                                                .addVertex((float) (d10 - camX), (float) (yLo - camY),
                                                                (float) (worldborder.getMaxZ() - camZ))
                                                .setUv(f3 + f10, f3 + f5);
                                bufferbuilder.addVertex((float) (d10 + d13 - camX), (float) (yLo - camY),
                                                (float) (worldborder.getMaxZ() - camZ)).setUv(f3 + f13 + f10, f3 + f5);
                                bufferbuilder.addVertex((float) (d10 + d13 - camX), (float) (yHi - camY),
                                                (float) (worldborder.getMaxZ() - camZ)).setUv(f3 + f13 + f10, f3 + f4);
                                bufferbuilder
                                                .addVertex((float) (d10 - camX), (float) (yHi - camY),
                                                                (float) (worldborder.getMaxZ() - camZ))
                                                .setUv(f3 + f10, f3 + f4);
                                d10++;
                        }
                }

                if (camZ < worldborder.getMinZ() + d0) {
                        float f11 = f6;
                        for (double d11 = d5; d11 < d6; f11 += 0.5F) {
                                double d14 = Math.min(1.0, d6 - d11);
                                float f14 = (float) d14 * 0.5F;
                                bufferbuilder
                                                .addVertex((float) (d11 - camX), (float) (yLo - camY),
                                                                (float) (worldborder.getMinZ() - camZ))
                                                .setUv(f3 - f11, f3 + f5);
                                bufferbuilder.addVertex((float) (d11 + d14 - camX), (float) (yLo - camY),
                                                (float) (worldborder.getMinZ() - camZ))
                                                .setUv(f3 - (f14 + f11), f3 + f5);
                                bufferbuilder.addVertex((float) (d11 + d14 - camX), (float) (yHi - camY),
                                                (float) (worldborder.getMinZ() - camZ))
                                                .setUv(f3 - (f14 + f11), f3 + f4);
                                bufferbuilder
                                                .addVertex((float) (d11 - camX), (float) (yHi - camY),
                                                                (float) (worldborder.getMinZ() - camZ))
                                                .setUv(f3 - f11, f3 + f4);
                                d11++;
                        }
                }

                // ---- 地板 y = minY / 天花板 y = maxY 面板 ----
                double xLo = Math.max((double) floorSafe(camX - d0), worldborder.getMinX());
                double xHi = Math.min((double) ceilSafe(camX + d0), worldborder.getMaxX());
                double zLo = Math.max((double) floorSafe(camZ - d0), worldborder.getMinZ());
                double zHi = Math.min((double) ceilSafe(camZ + d0), worldborder.getMaxZ());

                if (camY < minY + d0) {
                        float fPhase = (float) (floorSafe(xLo) & 1) * 0.5F;
                        for (double d7 = xLo; d7 < xHi; fPhase += 0.5F) {
                                double d8 = Math.min(1.0, xHi - d7);
                                float f8 = (float) d8 * 0.5F;
                                float fPhaseZ = (float) (floorSafe(zLo) & 1) * 0.5F;
                                for (double d9 = zLo; d9 < zHi; fPhaseZ += 0.5F) {
                                        double d10 = Math.min(1.0, zHi - d9);
                                        float f10 = (float) d10 * 0.5F;
                                        bufferbuilder.addVertex((float) (d7 - camX), (float) (minY - camY),
                                                        (float) (d9 - camZ))
                                                        .setUv(f3 + fPhase, f3 + fPhaseZ);
                                        bufferbuilder.addVertex((float) (d7 + d8 - camX), (float) (minY - camY),
                                                        (float) (d9 - camZ))
                                                        .setUv(f3 + f8 + fPhase, f3 + fPhaseZ);
                                        bufferbuilder.addVertex((float) (d7 + d8 - camX), (float) (minY - camY),
                                                        (float) (d9 + d10 - camZ))
                                                        .setUv(f3 + f8 + fPhase, f3 + f10 + fPhaseZ);
                                        bufferbuilder.addVertex((float) (d7 - camX), (float) (minY - camY),
                                                        (float) (d9 + d10 - camZ))
                                                        .setUv(f3 + fPhase, f3 + f10 + fPhaseZ);
                                        d9++;
                                }
                                d7++;
                        }
                }

                if (camY > maxY - d0) {
                        float fPhase = (float) (floorSafe(xLo) & 1) * 0.5F;
                        for (double d7 = xLo; d7 < xHi; fPhase += 0.5F) {
                                double d8 = Math.min(1.0, xHi - d7);
                                float f8 = (float) d8 * 0.5F;
                                float fPhaseZ = (float) (floorSafe(zLo) & 1) * 0.5F;
                                for (double d9 = zLo; d9 < zHi; fPhaseZ += 0.5F) {
                                        double d10 = Math.min(1.0, zHi - d9);
                                        float f10 = (float) d10 * 0.5F;
                                        bufferbuilder.addVertex((float) (d7 - camX), (float) (maxY - camY),
                                                        (float) (d9 - camZ))
                                                        .setUv(f3 + fPhase, f3 + fPhaseZ);
                                        bufferbuilder.addVertex((float) (d7 + d8 - camX), (float) (maxY - camY),
                                                        (float) (d9 - camZ))
                                                        .setUv(f3 + f8 + fPhase, f3 + fPhaseZ);
                                        bufferbuilder.addVertex((float) (d7 + d8 - camX), (float) (maxY - camY),
                                                        (float) (d9 + d10 - camZ))
                                                        .setUv(f3 + f8 + fPhase, f3 + f10 + fPhaseZ);
                                        bufferbuilder.addVertex((float) (d7 - camX), (float) (maxY - camY),
                                                        (float) (d9 + d10 - camZ))
                                                        .setUv(f3 + fPhase, f3 + f10 + fPhaseZ);
                                        d9++;
                                }
                                d7++;
                        }
                }

                MeshData meshdata = bufferbuilder.build();
                if (meshdata != null) {
                        BufferUploader.drawWithShader(meshdata);
                }

                RenderSystem.enableCull();
                RenderSystem.polygonOffset(0.0F, 0.0F);
                RenderSystem.disablePolygonOffset();
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.depthMask(true);
        }
}
