package com.inf.farlands.mixin.threeInt;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(LightEngine.class)
public abstract class LightEngineMixin {
    // @Override
    // public void updateSectionStatus(SectionPos pos, boolean isQueueEmpty) {
    // this.storage.updateSectionStatus(pos.asLong(), isQueueEmpty);
    // }
    @Inject(method = "updateSectionStatus", at = @At("HEAD"))
    private void refreshSectionLastAccess(SectionPos pos, boolean isQueueEmpty, CallbackInfo ci) {
        // 在方法开头刷新 TTL
        SectionPos.x(pos.asLong());
    }

    // @Shadow
    // private LongOpenHashSet blockNodesToCheck;

    // @Override
    // public void checkBlock(BlockPos pos) {
    // this.blockNodesToCheck.add(pos.asLong());
    // }
    // @Overwrite
    // public void checkBlock(BlockPos pos) {
    //     IntBlockPos ibp = new IntBlockPos(pos);
    //     long key = HashUtil.hashPos((long) ibp.x, (long) ibp.y, (long) ibp.z);
    //     HashUtil.putBlock(key, ibp);
    //     this.blockNodesToCheck.add(key);
    // }

    // @Override
    // public int runLightUpdates() {
    // LongIterator longiterator = this.blockNodesToCheck.iterator();

    // while (longiterator.hasNext()) {
    // this.checkNode(longiterator.nextLong());
    // }

    // this.blockNodesToCheck.clear();
    // this.blockNodesToCheck.trim(512);
    // int i = 0;
    // i += this.propagateDecreases();
    // i += this.propagateIncreases();
    // this.clearChunkCache();
    // this.storage.markNewInconsistencies(this);
    // this.storage.swapSectionMap();
    // return i;
    // }
    // @Shadow
    // protected abstract void clearChunkCache();

    // @Shadow
    // protected abstract int propagateIncreases();

    // @Shadow
    // protected abstract int propagateDecreases();

    // @Shadow
    // protected abstract void checkNode(long packedPos);

    // @Shadow
    // private LayerLightSectionStorage<?> storage;

    // @SuppressWarnings("unchecked")
    // @Overwrite
    // public int runLightUpdates() {
    //     LongIterator it = this.blockNodesToCheck.iterator();
    //     while (it.hasNext())
    //         this.checkNode(it.nextLong());
    //     this.blockNodesToCheck.clear();
    //     this.blockNodesToCheck.trim(512);
    //     int i = this.propagateDecreases() + this.propagateIncreases();
    //     this.clearChunkCache();

    //     LayerLightSectionStorageAccessor<M> accessor = (LayerLightSectionStorageAccessor<M>) this.storage;
    //     accessor.invokeMarkNewInconsistencies((LightEngine<M, ?>) (Object) this);
    //     accessor.invokeSwapSectionMap();

    //     return i;
    // }

    // @Overwrite
    // public int getLightValue(BlockPos levelPos) {
    //     IntBlockPos ibp = new IntBlockPos(levelPos);
    //     long key = HashUtil.hashPos((long) ibp.x, (long) ibp.y, (long) ibp.z);
    //     HashUtil.putBlock(key, ibp);
    //     long secKey = SectionPos.asLong(
    //             SectionPos.blockToSectionCoord(ibp.x),
    //             SectionPos.blockToSectionCoord(ibp.y),
    //             SectionPos.blockToSectionCoord(ibp.z));
    //     DataLayer dl = HashUtil.callGetDataLayer(this.storage, secKey, false);
    //     if (dl == null)
    //         return 15;
    //     return dl.get(
    //             SectionPos.sectionRelative(ibp.x),
    //             SectionPos.sectionRelative(ibp.y),
    //             SectionPos.sectionRelative(ibp.z));
    // }
}
