package com.inf.farlands.mixin.threeInt;

import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Mixin(SkyLightSectionStorage.class)
public abstract class SkyLightSectionStorageMixin {
    private static final Field F_TOP_SECTIONS;
    private static final Field F_CURRENT_LOWEST_Y;
    private static final Field F_UPDATING;
    private static final Field F_VISIBLE;
    private static final Field F_QUEUED;
    private static final Method M_GET_DATA_LAYER;
    private static final Method M_LIGHT_ON_IN_SECTION;
    private static final Method M_STORING;
    private static final Method M_GET_DL_BOOL;
    private static final Method M_HAS_BELOW;

    static {
        try {
            Class<?> skyMapCls = Class
                    .forName("net.minecraft.world.level.lighting.SkyLightSectionStorage$SkyDataLayerStorageMap");
            F_TOP_SECTIONS = skyMapCls.getDeclaredField("topSections");
            F_TOP_SECTIONS.setAccessible(true);
            F_CURRENT_LOWEST_Y = skyMapCls.getDeclaredField("currentLowestY");
            F_CURRENT_LOWEST_Y.setAccessible(true);
            // -------------------------------------------------------
            Class<?> lls = LayerLightSectionStorage.class;
            F_UPDATING = lls.getDeclaredField("updatingSectionData");
            F_UPDATING.setAccessible(true);
            F_VISIBLE = lls.getDeclaredField("visibleSectionData");
            F_VISIBLE.setAccessible(true);
            F_QUEUED = lls.getDeclaredField("queuedSections");
            F_QUEUED.setAccessible(true);
            M_GET_DATA_LAYER = lls.getDeclaredMethod("getDataLayer", DataLayerStorageMap.class, Long.TYPE);
            M_GET_DATA_LAYER.setAccessible(true);
            M_LIGHT_ON_IN_SECTION = lls.getDeclaredMethod("lightOnInSection", Long.TYPE);
            M_LIGHT_ON_IN_SECTION.setAccessible(true);
            M_STORING = lls.getDeclaredMethod("storingLightForSection", Long.TYPE);
            M_STORING.setAccessible(true);
            M_GET_DL_BOOL = lls.getDeclaredMethod("getDataLayer", Long.TYPE, Boolean.TYPE);
            M_GET_DL_BOOL.setAccessible(true);
            // -------------------------------------------------------
            M_HAS_BELOW = SkyLightSectionStorage.class.getDeclaredMethod("hasLightDataAtOrBelow", Integer.TYPE);
            M_HAS_BELOW.setAccessible(true);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private Object refUpdating() {
        try {
            return F_UPDATING.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object refVisible() {
        try {
            return F_VISIBLE.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long, DataLayer> refQueued() {
        try {
            return (Map<Long, DataLayer>) F_QUEUED.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long2IntOpenHashMap topSections(Object m) {
        try {
            return (Long2IntOpenHashMap) F_TOP_SECTIONS.get(m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int currentLowestY(Object m) {
        try {
            return F_CURRENT_LOWEST_Y.getInt(m);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setCurrentLowestY(Object m, int v) {
        try {
            F_CURRENT_LOWEST_Y.setInt(m, v);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DataLayer callGetDataLayer(Object m, long s) {
        try {
            return (DataLayer) M_GET_DATA_LAYER.invoke(this, m, s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean callLightOnInSection(long s) {
        try {
            return (boolean) M_LIGHT_ON_IN_SECTION.invoke(this, s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean callStoring(long s) {
        try {
            return (boolean) M_STORING.invoke(this, s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean callHasBelow(int y) {
        try {
            return (boolean) M_HAS_BELOW.invoke(this, y);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DataLayer callGetDLBool(long s, boolean b) {
        try {
            return (DataLayer) M_GET_DL_BOOL.invoke(this, s, b);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    // -------------------------------------------------------

    @Overwrite
    protected int getLightValue(long packedPos, boolean updateAll) {
        long sec = SectionPos.blockToSection(packedPos);
        int y = SectionPos.y(sec);
        Object map = updateAll ? refUpdating() : refVisible();
        Long2IntOpenHashMap topSec = topSections(map);

        int k = topSec.get(SectionPos.getZeroNode(sec));
        if (k != currentLowestY(map) && y < k) {
            DataLayer dataLayer = callGetDataLayer(map, sec);
            while (dataLayer == null) {
                if (++y >= k) {
                    return 15;
                }
                sec = SectionPos.offset(sec, Direction.UP);
                dataLayer = callGetDataLayer(map, sec);
            }

            IntBlockPos pos = IntBlockPos.getBlockPos(packedPos);
            return dataLayer.get(SectionPos.sectionRelative(pos.x), SectionPos.sectionRelative(pos.y),
                    SectionPos.sectionRelative(pos.z));
        }
        return updateAll && !callLightOnInSection(sec) ? 0 : 15;
    }

    @Overwrite
    protected void onNodeAdded(long sectionPos) {
        IntSectionPos pos = IntSectionPos.getSectionPos(sectionPos);
        Object up = refUpdating();
        if (currentLowestY(up) > pos.y) {
            setCurrentLowestY(up, pos.y);
            topSections(up).defaultReturnValue(pos.y);
        }
        long zeroNode = SectionPos.getZeroNode(sectionPos);
        int k = topSections(up).get(zeroNode);
        if (k < pos.y + 1) {
            topSections(up).put(zeroNode, pos.y + 1);
        }
    }

    @Overwrite
    protected void onNodeRemoved(long sectionPos) {
        IntSectionPos pos = IntSectionPos.getSectionPos(sectionPos);
        Object map = refUpdating();
        long columnKey = SectionPos.getZeroNode(sectionPos);

        if (topSections(map).get(columnKey) != pos.y + 1)
            return;

        long sec = sectionPos;
        int y = pos.y;
        while (!callStoring(sec) && callHasBelow(y)) {
            sec = SectionPos.offset(sec, Direction.DOWN);
            y--;
        }
        if (callStoring(sec)) {
            topSections(map).put(columnKey, y + 1);
        } else {
            topSections(map).remove(columnKey);
        }
    }

    @Overwrite
    protected DataLayer createDataLayer(long sectionPos) {
        DataLayer dataLayer = refQueued().get(sectionPos);
        if (dataLayer != null) {
            return dataLayer;
        }

        IntSectionPos secPos = IntSectionPos.getSectionPos(sectionPos);
        Object up = refUpdating();
        long zeroNode = SectionPos.getZeroNode(sectionPos);
        int topZeroNode = topSections(up).get(zeroNode);

        if (topZeroNode != currentLowestY(up) && secPos.y < topZeroNode) {
            long pos = SectionPos.offset(sectionPos, Direction.UP);
            DataLayer resDataLayer;
            while ((resDataLayer = callGetDLBool(pos, true)) == null) {
                pos = SectionPos.offset(pos, Direction.UP);
            }
            return repeatFirstLayer(resDataLayer);
        }
        return callLightOnInSection(sectionPos) ? new DataLayer(15) : new DataLayer();
    }

    private static DataLayer repeatFirstLayer(DataLayer DataLayer) {
        if (DataLayer.isDefinitelyHomogenous())
            return DataLayer.copy();

        byte[] data = DataLayer.getData();
        byte[] newData = new byte[2048];
        for (int i = 0; i < 16; i++)
            System.arraycopy(data, 0, newData, i * 128, 128);

        return new DataLayer(newData);
    }

    @Overwrite
    protected boolean isAboveData(long sectionPos) {
        IntSectionPos pos = IntSectionPos.getSectionPos(sectionPos);
        Object up = refUpdating();
        long zeroNode = SectionPos.getZeroNode(sectionPos);
        int topSecZeroNode = topSections(up).get(zeroNode);
        return topSecZeroNode == currentLowestY(up) || pos.y >= topSecZeroNode;
    }
}
