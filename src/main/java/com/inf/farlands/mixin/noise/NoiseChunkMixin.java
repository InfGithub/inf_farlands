package com.inf.farlands.mixin.noise;

import com.inf.farlands.terrain.NoiseSystem;
import com.inf.farlands.terrain.system.NoiseSystemRegistry;
import com.inf.farlands.terrain.TerrainSystem;

import java.lang.reflect.Method;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {

    private static final Method M_AQUIFER_CREATE;

    static {
        try {
            M_AQUIFER_CREATE = Aquifer.class.getDeclaredMethod("create",
                    NoiseChunk.class, ChunkPos.class, NoiseRouter.class,
                    PositionalRandomFactory.class, int.class, int.class, Aquifer.FluidPicker.class);
            M_AQUIFER_CREATE.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** finalDensity 由当前地形系统提供，按维度分派（NoiseFillerContext）。
     * 主世界由 Config.overworldTerrainSystem 选择（NoiseSystem 密度链 / BlockSystem 逐块几何
     * 占位 zero）；下界/末地走各自维度系统（vanilla 链）。 */
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/DensityFunction;"))
    private DensityFunction replaceFinalDensity(NoiseRouter router) {
        TerrainSystem sys = switch (com.inf.farlands.terrain.noisefiller.NoiseFillerContext.get()) {
            case NETHER -> com.inf.farlands.terrain.system.NoiseSystemRegistry.getTheNether();
            case END -> com.inf.farlands.terrain.system.NoiseSystemRegistry.getTheEnd();
            case OVERWORLD -> com.inf.farlands.terrain.system.NoiseSystemRegistry.getOverworld();
        };
        return sys instanceof NoiseSystem n ? n.createFinalDensity(router) : DensityFunctions.zero();
    }

    /**
     * aquifer 由当前噪声系统提供，null 表示默认 NoiseBasedAquifer。
     * VOID 返回空气 aquifer：computeSubstance 恒为 AIR 且非 null，MaterialRuleList 短路
     * → 矿脉规则不执行 → 全空气且 O(1)。
     */
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/Aquifer;create(Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/levelgen/NoiseRouter;Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;IILnet/minecraft/world/level/levelgen/Aquifer$FluidPicker;)Lnet/minecraft/world/level/levelgen/Aquifer;"))
    private Aquifer redirectAquiferCreate(NoiseChunk chunk, ChunkPos pos, NoiseRouter router,
            PositionalRandomFactory random, int minY, int height, Aquifer.FluidPicker picker) {
        TerrainSystem sys = switch (com.inf.farlands.terrain.noisefiller.NoiseFillerContext.get()) {
            case NETHER -> com.inf.farlands.terrain.system.NoiseSystemRegistry.getTheNether();
            case END -> com.inf.farlands.terrain.system.NoiseSystemRegistry.getTheEnd();
            case OVERWORLD -> com.inf.farlands.terrain.system.NoiseSystemRegistry.getOverworld();
        };
        Aquifer a = sys.createAquifer(chunk, pos, router, random, minY, height, picker);
        if (a != null) {
            return a;
        }
        try {
            return (Aquifer) M_AQUIFER_CREATE.invoke(null, chunk, pos, router, random, minY, height, picker);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
