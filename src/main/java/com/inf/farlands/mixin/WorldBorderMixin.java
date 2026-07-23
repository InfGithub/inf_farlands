package com.inf.farlands.mixin;

import com.inf.farlands.Config;

import net.minecraft.world.level.border.WorldBorder;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin {

    // 字段： int absoluteMaxSize = 29999984;
    //

    @Accessor("absoluteMaxSize")
    public abstract void setAbsoluteMaxSize(int size);

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        setAbsoluteMaxSize(Config.borderAbsoluteMax - 16);
    
        // 字段： private WorldBorder.BorderExtent extent = new
        // WorldBorder.StaticBorderExtent(5.999997E7F);
    
        try {
            Field extentField = WorldBorder.class.getDeclaredField("extent");
            extentField.setAccessible(true);
            Class<?> staticBorderExtentClass = Class
                    .forName("net.minecraft.world.level.border.WorldBorder$StaticBorderExtent");
            Constructor<?> constructor = staticBorderExtentClass.getDeclaredConstructor(WorldBorder.class,
                    double.class);
            constructor.setAccessible(true);
            Object newExtent = constructor.newInstance(this, Config.borderAbsoluteMax * 2.0 - 30.0);
            extentField.set(this, newExtent);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 字段： public static final double MAX_SIZE = 5.999997E7F;
    @Shadow
    @Final
    @Mutable
    private static double MAX_SIZE;

    private static void set_MAX_SIZE(double size) {
        MAX_SIZE = size;
    }

    // 字段： public static final double MAX_CENTER_COORDINATE = 2.9999984E7;
    @Shadow
    @Final
    @Mutable
    private static double MAX_CENTER_COORDINATE;

    private static void set_MAX_CENTER_COORDINATE(double coord) {
        MAX_CENTER_COORDINATE = coord;
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void onClinit(CallbackInfo ci) {
        set_MAX_SIZE(Config.borderAbsoluteMax * 2.0 - 30.0);
        set_MAX_CENTER_COORDINATE(Config.borderAbsoluteMax - 16.0);
    }

    // 字段： public static final WorldBorder.Settings DEFAULT_SETTINGS = new
    // WorldBorder.Settings(0.0, 0.0, 0.2, 5.0, 5, 15, 5.999997E7F, 0L, 0.0);

    @ModifyConstant(method = "<clinit>", constant = @Constant(doubleValue = 5.999997E7F))
    private static double modifyDefaultBorderSize(double original) {
        return Config.borderAbsoluteMax * 2.0 - 30.0;
    }
}
