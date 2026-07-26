package com.inf.farlands.mixin.worldOverflowFix;

import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.*;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.Optional;

@Mixin(JigsawPlacement.class)
public class JigsawPlacementMixin {

    @Shadow
    @Final
    private static Logger LOGGER;

    @SuppressWarnings("null")
    @Overwrite
    public static Optional<Structure.GenerationStub> addPieces(
            Structure.GenerationContext context,
            Holder<StructureTemplatePool> startPool,
            Optional<ResourceLocation> startJigsawName,
            int maxDepth,
            BlockPos pos,
            boolean useExpansionHack,
            Optional<Heightmap.Types> projectStartToHeightmap,
            int maxDistanceFromCenter,
            PoolAliasLookup aliasLookup,
            DimensionPadding dimensionPadding,
            LiquidSettings liquidSettings) {

        RegistryAccess registryaccess = context.registryAccess();
        ChunkGenerator chunkgenerator = context.chunkGenerator();
        StructureTemplateManager structuretemplatemanager = context.structureTemplateManager();
        LevelHeightAccessor levelheightaccessor = context.heightAccessor();
        WorldgenRandom worldgenrandom = context.random();
        Registry<StructureTemplatePool> registry = registryaccess.registryOrThrow(Registries.TEMPLATE_POOL);
        Rotation rotation = Rotation.getRandom(worldgenrandom);
        StructureTemplatePool structuretemplatepool = startPool.unwrapKey()
                .flatMap(key -> registry.getOptional(aliasLookup.lookup((ResourceKey<StructureTemplatePool>) key)))
                .orElse(startPool.value());

        StructurePoolElement structurepoolelement = structuretemplatepool.getRandomTemplate(worldgenrandom);
        if (structurepoolelement == EmptyPoolElement.INSTANCE) {
            return Optional.empty();
        }

        BlockPos blockpos;
        if (startJigsawName.isPresent()) {
            ResourceLocation resourcelocation = startJigsawName.get();
            Optional<BlockPos> optional = getRandomNamedJigsaw(
                    structurepoolelement, resourcelocation, pos, rotation, structuretemplatemanager,
                    worldgenrandom);
            if (optional.isEmpty()) {
                LOGGER.error(
                        "No starting jigsaw {} found in start pool {}",
                        resourcelocation,
                        startPool.unwrapKey()
                                .map(errkey -> errkey.location().toString())
                                .orElse("<unregistered>"));
                return Optional.empty();
            }
            blockpos = optional.get();
        } else {
            blockpos = pos;
        }

        Vec3i vec3i = blockpos.subtract(pos);
        BlockPos blockpos1 = pos.subtract(vec3i);
        PoolElementStructurePiece poolelementstructurepiece = new PoolElementStructurePiece(
                structuretemplatemanager,
                structurepoolelement,
                blockpos1,
                structurepoolelement.getGroundLevelDelta(),
                rotation,
                structurepoolelement.getBoundingBox(structuretemplatemanager, blockpos1, rotation),
                liquidSettings);

        BoundingBox boundingbox = poolelementstructurepiece.getBoundingBox();
        int x = (int) (((long) boundingbox.maxX() + (long) boundingbox.minX()) / 2L);
        int z = (int) (((long) boundingbox.maxZ() + (long) boundingbox.minZ()) / 2L);
        int y1;

        if (projectStartToHeightmap.isPresent()) {
            y1 = pos.getY() + chunkgenerator.getFirstFreeHeight(x, z, projectStartToHeightmap.get(),
                    levelheightaccessor, context.randomState());
        } else {
            y1 = blockpos1.getY();
        }

        int y2 = boundingbox.minY() + poolelementstructurepiece.getGroundLevelDelta();
        poolelementstructurepiece.move(0, y1 - y2, 0);

        int y = y1 + vec3i.getY();
        Structure.GenerationStub stub = new Structure.GenerationStub(
                new BlockPos(x, y, z),
                key -> {
                    List<PoolElementStructurePiece> list = Lists.newArrayList();
                    list.add(poolelementstructurepiece);
                    if (maxDepth > 0) {
                        AABB aabb = new AABB(
                                (double) ((long) x - (long) maxDistanceFromCenter),
                                (double) Math.max(y - maxDistanceFromCenter,
                                        levelheightaccessor.getMinBuildHeight() + dimensionPadding.bottom()),
                                (double) ((long) z - (long) maxDistanceFromCenter),
                                (double) ((long) x + (long) maxDistanceFromCenter + 1L),
                                (double) Math.min(y + maxDistanceFromCenter + 1,
                                        levelheightaccessor.getMaxBuildHeight() - dimensionPadding.top()),
                                (double) ((long) z + (long) maxDistanceFromCenter + 1L));
                        VoxelShape voxelshape = Shapes.join(Shapes.create(aabb),
                                Shapes.create(AABB.of(boundingbox)), BooleanOp.ONLY_FIRST);

                        addPieces(
                                context.randomState(),
                                maxDepth,
                                useExpansionHack,
                                chunkgenerator,
                                structuretemplatemanager,
                                levelheightaccessor,
                                worldgenrandom,
                                registry,
                                poolelementstructurepiece,
                                list,
                                voxelshape,
                                aliasLookup,
                                liquidSettings);
                        list.forEach(key::addPiece);
                    }
                });
        return Optional.of(stub);
    }

    @Shadow
    private static Optional<BlockPos> getRandomNamedJigsaw(
            StructurePoolElement element,
            ResourceLocation name,
            BlockPos pos,
            Rotation rotation,
            StructureTemplateManager structureTemplateManager,
            WorldgenRandom random) {
        throw new AssertionError();
    }

    @Shadow
    private static void addPieces(
            RandomState randomState,
            int maxDepth,
            boolean useExpansionHack,
            ChunkGenerator chunkGenerator,
            StructureTemplateManager structureTemplateManager,
            LevelHeightAccessor levelHeightAccessor,
            RandomSource random,
            Registry<StructureTemplatePool> pools,
            PoolElementStructurePiece startPiece,
            List<PoolElementStructurePiece> pieces,
            VoxelShape free,
            PoolAliasLookup aliasLookup,
            LiquidSettings liquidSettings) {
        throw new AssertionError();
    }
}
