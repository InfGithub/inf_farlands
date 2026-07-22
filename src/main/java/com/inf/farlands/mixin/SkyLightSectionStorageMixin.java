package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SkyLightSectionStorage.class)
public abstract class SkyLightSectionStorageMixin {

    private static final Field F_TOP_SECTIONS, F_CURRENT_LOWEST_Y, F_VISIBLE, F_UPDATING, F_QUEUED;
    private static final Method M_GET_DATA_LAYER, M_LIGHT_ON_IN_SECTION, M_STORING, M_HAS_BELOW, M_GET_DL_BOOL;
    static {
        try {
            Class<?> skyMapCls = Class.forName("net.minecraft.world.level.lighting.SkyLightSectionStorage$SkyDataLayerStorageMap");
            Class<?> lls = LayerLightSectionStorage.class;
            F_TOP_SECTIONS = skyMapCls.getDeclaredField("topSections"); F_TOP_SECTIONS.setAccessible(true);
            F_CURRENT_LOWEST_Y = skyMapCls.getDeclaredField("currentLowestY"); F_CURRENT_LOWEST_Y.setAccessible(true);
            F_VISIBLE = lls.getDeclaredField("visibleSectionData"); F_VISIBLE.setAccessible(true);
            F_UPDATING = lls.getDeclaredField("updatingSectionData"); F_UPDATING.setAccessible(true);
            F_QUEUED = lls.getDeclaredField("queuedSections"); F_QUEUED.setAccessible(true);
            M_GET_DATA_LAYER = lls.getDeclaredMethod("getDataLayer", DataLayerStorageMap.class, Long.TYPE); M_GET_DATA_LAYER.setAccessible(true);
            M_LIGHT_ON_IN_SECTION = lls.getDeclaredMethod("lightOnInSection", Long.TYPE); M_LIGHT_ON_IN_SECTION.setAccessible(true);
            M_STORING = lls.getDeclaredMethod("storingLightForSection", Long.TYPE); M_STORING.setAccessible(true);
            M_HAS_BELOW = SkyLightSectionStorage.class.getDeclaredMethod("hasLightDataAtOrBelow", Integer.TYPE); M_HAS_BELOW.setAccessible(true);
            M_GET_DL_BOOL = lls.getDeclaredMethod("getDataLayer", Long.TYPE, Boolean.TYPE); M_GET_DL_BOOL.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private Object refVisible() { try { return F_VISIBLE.get(this); } catch (Exception e) { throw new RuntimeException(e); } }
    private Object refUpdating() { try { return F_UPDATING.get(this); } catch (Exception e) { throw new RuntimeException(e); } }
    private Map<Long, DataLayer> refQueued() { try { return (Map<Long, DataLayer>) F_QUEUED.get(this); } catch (Exception e) { throw new RuntimeException(e); } }
    private Long2IntOpenHashMap topSections(Object m) { try { return (Long2IntOpenHashMap) F_TOP_SECTIONS.get(m); } catch (Exception e) { throw new RuntimeException(e); } }
    private int currentLowestY(Object m)       { try { return F_CURRENT_LOWEST_Y.getInt(m); } catch (Exception e) { throw new RuntimeException(e); } }
    private void setCurrentLowestY(Object m, int v) { try { F_CURRENT_LOWEST_Y.setInt(m, v); } catch (Exception e) { throw new RuntimeException(e); } }
    private DataLayer callGetDataLayer(Object m, long s)  { try { return (DataLayer) M_GET_DATA_LAYER.invoke(this, m, s); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callLightOnInSection(long s)          { try { return (boolean) M_LIGHT_ON_IN_SECTION.invoke(this, s); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callStoring(long s)                   { try { return (boolean) M_STORING.invoke(this, s); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callHasBelow(int y)                   { try { return (boolean) M_HAS_BELOW.invoke(this, y); } catch (Exception e) { throw new RuntimeException(e); } }
    private DataLayer callGetDLBool(long s, boolean b)    { try { return (DataLayer) M_GET_DL_BOOL.invoke(this, s, b); } catch (Exception e) { throw new RuntimeException(e); } }

    private static IntBlockPos getBlockPos(long k) { IntBlockPos bp = HashUtil.getBlock(k); return bp != null ? bp : new IntBlockPos(BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k)); }
    private static IntSectionPos getSectionPos(long k) { IntSectionPos sp = HashUtil.getSection(k); return sp != null ? sp : new IntSectionPos(SectionPos.x(k), SectionPos.y(k), SectionPos.z(k)); }

    @Overwrite
    protected int getLightValue(long packedPos, boolean updateAll) {
        long sec = SectionPos.blockToSection(packedPos);
        int sy = SectionPos.y(sec);
        Object map = updateAll ? refUpdating() : refVisible();
        Long2IntOpenHashMap ts = topSections(map);
        int k = ts.get(SectionPos.getZeroNode(sec));
        if (k != currentLowestY(map) && sy < k) {
            DataLayer dl = callGetDataLayer(map, sec);
            if (dl == null) { for (long flatIdx = BlockPos.getFlatIndex(packedPos); dl == null; dl = callGetDataLayer(map, sec)) { if (++sy >= k) return 15; sec = SectionPos.offset(sec, Direction.UP); } }
            IntBlockPos bp = getBlockPos(packedPos);
            return dl.get(SectionPos.sectionRelative(bp.x), SectionPos.sectionRelative(bp.y), SectionPos.sectionRelative(bp.z));
        }
        return updateAll && !callLightOnInSection(sec) ? 0 : 15;
    }

    @Overwrite
    protected void onNodeAdded(long sectionPos) {
        IntSectionPos sp = getSectionPos(sectionPos);
        Object up = refUpdating();
        if (currentLowestY(up) > sp.y) { setCurrentLowestY(up, sp.y); topSections(up).defaultReturnValue(sp.y); }
        long zk = SectionPos.getZeroNode(sectionPos);
        int k = topSections(up).get(zk);
        if (k < sp.y + 1) topSections(up).put(zk, sp.y + 1);
    }

    @Overwrite
    protected void onNodeRemoved(long sectionPos) {
        IntSectionPos sp = getSectionPos(sectionPos);
        Object up = refUpdating();
        long zk = SectionPos.getZeroNode(sectionPos);
        if (topSections(up).get(zk) == sp.y + 1) {
            long k; int j = sp.y;
            for (k = sectionPos; !callStoring(k) && callHasBelow(j); k = SectionPos.offset(k, Direction.DOWN)) j--;
            if (callStoring(k)) topSections(up).put(zk, j + 1); else topSections(up).remove(zk);
        }
    }

    @Overwrite
    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer dl = refQueued().get(sectionPos);
        if (dl != null) return dl;
        IntSectionPos sp = getSectionPos(sectionPos);
        Object up = refUpdating();
        long zk = SectionPos.getZeroNode(sectionPos);
        int i = topSections(up).get(zk);
        if (i != currentLowestY(up) && sp.y < i) {
            long j = SectionPos.offset(sectionPos, Direction.UP);
            DataLayer dl1;
            while ((dl1 = callGetDLBool(j, true)) == null) j = SectionPos.offset(j, Direction.UP);
            return repeatFirstLayer(dl1);
        }
        return callLightOnInSection(sectionPos) ? new DataLayer(15) : new DataLayer();
    }

    private static DataLayer repeatFirstLayer(DataLayer dl) { if (dl.isDefinitelyHomogenous()) return dl.copy(); byte[] s = dl.getData(), d = new byte[2048]; for (int i = 0; i < 16; i++) System.arraycopy(s, 0, d, i * 128, 128); return new DataLayer(d); }

    @Overwrite
    protected boolean isAboveData(long sectionPos) {
        IntSectionPos sp = getSectionPos(sectionPos);
        Object up = refUpdating();
        long zk = SectionPos.getZeroNode(sectionPos);
        int j = topSections(up).get(zk);
        return j == currentLowestY(up) || sp.y >= j;
    }
}
