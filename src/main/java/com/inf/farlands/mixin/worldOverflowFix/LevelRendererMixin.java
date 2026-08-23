package com.inf.farlands.mixin.worldOverflowFix;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 冻结修复：renderSnowAndRain 循环边界 long 化。
 *
 * vanilla L292-293：for (int j1 = k - l; j1 <= k + l; j1++)——k = floor(camZ)，
 * 玩家坐标 ±2.14B 下 k + l 恰好 = Integer.MAX_VALUE 时（k = 2147483637, l = 10），
 * j1 递增到 MAX_VALUE 后 j1++ 溢出为 MIN_VALUE → 无限循环（CPU 100% 冻结，
 * 实测 camZ=2147483637.84 触发）。修复：循环变量改 long 运算，j1/k1 在体内
 * 转 int（差值被 ±l 约束，int 转换无损）。其余逻辑与 vanilla 逐行一致。
 *
 * 原版玩家坐标 ±3000 万不会触及该边界——本 mod 的 ±2.14B 坐标暴露了 vanilla
 * 边界 bug。
 *
 * priority = 100（后应用，实证：Mixin priority 越大越先应用）：sodium 的
 * features.options.weather.LevelRendererMixin（priority 1000）在 renderSnowAndRain
 * 内 @Redirect useFancyGraphics（天气质量选项）——@Overwrite 的方法带 merged
 * 标记，若我们先应用则 sodium 注入器拒绝注入 → sodium 应用失败 → 启动 CTD。
 * priority 100 < 1000 让我们后应用：sodium 先注入成功（vanilla 方法体），
 * 我们再 @Overwrite 覆盖（sodium 天气选项失效，功能损失可接受；修复完整保留）。
 */
@Mixin(value = LevelRenderer.class, priority = 100)
public class LevelRendererMixin {

    @Shadow
    private static ResourceLocation RAIN_LOCATION;
    @Shadow
    private static ResourceLocation SNOW_LOCATION;
    @Shadow
    private Minecraft minecraft;
    @Shadow
    private ClientLevel level;
    @Shadow
    private int ticks;
    @Shadow
    private float[] rainSizeX;
    @Shadow
    private float[] rainSizeZ;

    @Shadow
    private static int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        throw new AssertionError();
    }

    @SuppressWarnings("null")
    @Overwrite
    private void renderSnowAndRain(LightTexture lightTexture, float partialTick, double camX, double camY,
            double camZ) {
        if (level.effects().renderSnowAndRain(level, ticks, partialTick, lightTexture, camX, camY, camZ))
            return;
        float f = this.minecraft.level.getRainLevel(partialTick);
        if (!(f <= 0.0F)) {
            lightTexture.turnOnLightLayer();
            Level level = this.minecraft.level;
            int i = Mth.floor(camX);
            int j = Mth.floor(camY);
            int k = Mth.floor(camZ);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = null;
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            int l = 5;
            if (Minecraft.useFancyGraphics()) {
                l = 10;
            }

            RenderSystem.depthMask(Minecraft.useShaderTransparency());
            int i1 = -1;
            float f1 = (float) this.ticks + partialTick;
            RenderSystem.setShader(GameRenderer::getParticleShader);
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            // 修复：循环边界 long 运算防 j1++/k1++ 溢出死循环（k+l == MAX_VALUE 场景）
            long j1Lo = (long) k - l;
            long j1Hi = (long) k + l;
            for (long j1L = j1Lo; j1L <= j1Hi; j1L++) {
                int j1 = (int) j1L;
                long k1Lo = (long) i - l;
                long k1Hi = (long) i + l;
                for (long k1L = k1Lo; k1L <= k1Hi; k1L++) {
                    int k1 = (int) k1L;
                    int l1 = (j1 - k + 16) * 32 + k1 - i + 16;
                    double d0 = (double) this.rainSizeX[l1] * 0.5;
                    double d1 = (double) this.rainSizeZ[l1] * 0.5;
                    blockpos$mutableblockpos.set((double) k1, camY, (double) j1);
                    Biome biome = level.getBiome(blockpos$mutableblockpos).value();
                    if (biome.hasPrecipitation()) {
                        continue;
                    }

                    int i2 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, k1, j1);
                    long j2 = (long) j - l;
                    long k2 = (long) j + l;
                    if (j2 < i2) {
                        j2 = i2;
                    }

                    if (k2 < i2) {
                        k2 = i2;
                    }

                    int l2 = i2;
                    if (i2 < j) {
                        l2 = j;
                    }

                    if (j2 != k2) {
                        RandomSource randomsource = RandomSource
                                .create((long) (k1 * k1 * 3121 + k1 * 45238971 ^ j1 * j1 * 418711 + j1 * 13761));
                        blockpos$mutableblockpos.set(k1, (int) j2, j1);
                        Biome.Precipitation biome$precipitation = biome
                                .getPrecipitationAt(blockpos$mutableblockpos);
                        if (biome$precipitation == Biome.Precipitation.RAIN) {
                            if (i1 != 0) {
                                if (i1 >= 0) {
                                    BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                                }

                                i1 = 0;
                                RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                                bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS,
                                        DefaultVertexFormat.PARTICLE);
                            }

                            int i3 = this.ticks & 131071;
                            int j3 = k1 * k1 * 3121 + k1 * 45238971 + j1 * j1 * 418711 + j1 * 13761 & 0xFF;
                            float f2 = 3.0F + randomsource.nextFloat();
                            float f3 = -((float) (i3 + j3) + partialTick) / 32.0F * f2;
                            float f4 = f3 % 32.0F;
                            double d2 = (double) k1 + 0.5 - camX;
                            double d3 = (double) j1 + 0.5 - camZ;
                            float f6 = (float) Math.sqrt(d2 * d2 + d3 * d3) / (float) l;
                            float f7 = ((1.0F - f6 * f6) * 0.5F + 0.5F) * f;
                            blockpos$mutableblockpos.set(k1, l2, j1);
                            int k3 = getLightColor(level, blockpos$mutableblockpos);
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX - d0 + 0.5), (float) ((double) k2 - camY),
                                    (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F, (float) (j2 - j) * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX + d0 + 0.5), (float) ((double) k2 - camY),
                                    (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F, (float) (j2 - j) * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX + d0 + 0.5), (float) ((double) j2 - camY),
                                    (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F, (float) (k2 - j) * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX - d0 + 0.5), (float) ((double) j2 - camY),
                                    (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F, (float) (k2 - j) * 0.25F + f4)
                                    .setColor(1.0F, 1.0F, 1.0F, f7)
                                    .setLight(k3);
                        } else if (biome$precipitation == Biome.Precipitation.SNOW) {
                            if (i1 != 1) {
                                if (i1 >= 0) {
                                    BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                                }

                                i1 = 1;
                                RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                                bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS,
                                        DefaultVertexFormat.PARTICLE);
                            }

                            float f8 = -((float) (this.ticks & 511) + partialTick) / 512.0F;
                            float f9 = (float) (randomsource.nextDouble()
                                    + (double) f1 * 0.01 * (double) ((float) randomsource.nextGaussian()));
                            float f10 = (float) (randomsource.nextDouble()
                                    + (double) (f1 * (float) randomsource.nextGaussian()) * 0.001);
                            double d4 = (double) k1 + 0.5 - camX;
                            double d5 = (double) j1 + 0.5 - camZ;
                            float f11 = (float) Math.sqrt(d4 * d4 + d5 * d5) / (float) l;
                            float f5 = ((1.0F - f11 * f11) * 0.3F + 0.5F) * f;
                            blockpos$mutableblockpos.set(k1, l2, j1);
                            int j4 = getLightColor(level, blockpos$mutableblockpos);
                            int k4 = j4 >> 16 & 65535;
                            int l4 = j4 & 65535;
                            int l3 = (k4 * 3 + 240) / 4;
                            int i4 = (l4 * 3 + 240) / 4;
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX - d0 + 0.5), (float) ((double) k2 - camY),
                                    (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F + f9, (float) (j2 - j) * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX + d0 + 0.5), (float) ((double) k2 - camY),
                                    (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F + f9, (float) (j2 - j) * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX + d0 + 0.5), (float) ((double) j2 - camY),
                                    (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F + f9, (float) (k2 - j) * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                            bufferbuilder.addVertex(
                                    (float) ((double) k1 - camX - d0 + 0.5), (float) ((double) j2 - camY),
                                    (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F + f9, (float) (k2 - j) * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                        }
                    }
                }
            }

            if (i1 >= 0) {
                BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
            }

            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            lightTexture.turnOffLightLayer();
        }
    }
}
