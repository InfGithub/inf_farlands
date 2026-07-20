package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PerlinNoise.class)
public class PerlinNoiseMixin {

    @Shadow
    @Final
    private ImprovedNoise[] noiseLevels;

    @Shadow
    @Final
    private DoubleList amplitudes;

    @Shadow
    private double lowestFreqInputFactor;

    @Shadow
    private double lowestFreqValueFactor;

    /**
     * 3-parameter getValue: no wrapping, configurable octave acceleration.
     */
    @Overwrite
    public double getValue(double x, double y, double z) {
        double sum = 0.0;
        if (!Config.enableFarLands) {
            // Vanilla behaviour with wrapping
            double fi = this.lowestFreqInputFactor;
            double fv = this.lowestFreqValueFactor;
            for (int i = 0; i < this.noiseLevels.length; i++) {
                ImprovedNoise n = this.noiseLevels[i];
                if (n != null) {
                    sum +=
                        this.amplitudes.getDouble(i) *
                        fv *
                        n.noise(
                            PerlinNoise.wrap(x * fi),
                            PerlinNoise.wrap(y * fi),
                            PerlinNoise.wrap(z * fi),
                            0.0,
                            0.0
                        );
                }
                fi *= 2.0;
                fv /= 2.0;
            }
            return sum;
        }

        double inputFactor = this.lowestFreqInputFactor;
        double valueFactor = this.lowestFreqValueFactor;
        int threshold = Config.octaveAccelThreshold;
        double multiplier = Config.accelMultiplier;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise noise = this.noiseLevels[i];
            if (noise != null) {
                sum +=
                    this.amplitudes.getDouble(i) *
                    valueFactor *
                    noise.noise(
                        x * inputFactor,
                        y * inputFactor,
                        z * inputFactor,
                        0.0,
                        0.0
                    );
            }
            inputFactor *= i < threshold ? 2.0 : multiplier;
            valueFactor /= 2.0;
        }
        return sum;
    }

    /**
     * 6-parameter getValue: no wrapping, configurable octave acceleration.
     */
    @Overwrite
    public double getValue(
        double x,
        double y,
        double z,
        double yScale,
        double yMax,
        boolean useFixedY
    ) {
        if (!Config.enableFarLands) {
            double fi = this.lowestFreqInputFactor;
            double fv = this.lowestFreqValueFactor;
            double sum = 0.0;
            for (int i = 0; i < this.noiseLevels.length; i++) {
                ImprovedNoise n = this.noiseLevels[i];
                if (n != null) {
                    sum +=
                        this.amplitudes.getDouble(i) *
                        fv *
                        n.noise(
                            PerlinNoise.wrap(x * fi),
                            useFixedY ? -n.yo : PerlinNoise.wrap(y * fi),
                            PerlinNoise.wrap(z * fi),
                            yScale * fi,
                            yMax * fi
                        );
                }
                fi *= 2.0;
                fv /= 2.0;
            }
            return sum;
        }

        double inputFactor = this.lowestFreqInputFactor;
        double valueFactor = this.lowestFreqValueFactor;
        int threshold = Config.octaveAccelThreshold;
        double multiplier = Config.accelMultiplier;
        double sum = 0.0;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise noise = this.noiseLevels[i];
            if (noise != null) {
                sum +=
                    this.amplitudes.getDouble(i) *
                    valueFactor *
                    noise.noise(
                        x * inputFactor,
                        useFixedY ? -noise.yo : y * inputFactor,
                        z * inputFactor,
                        yScale * inputFactor,
                        yMax * inputFactor
                    );
            }
            inputFactor *= i < threshold ? 2.0 : multiplier;
            valueFactor /= 2.0;
        }
        return sum;
    }

    @Inject(method = "wrap", at = @At("TAIL"), cancellable = true)
    private static void undoFarlandsPatch(
        double value,
        CallbackInfoReturnable<Double> cir
    ) {
        if (Config.enableFarLands) {
            cir.setReturnValue(value);
        }
    }
}
