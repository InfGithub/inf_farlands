package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntSectionPos;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.CsvOutput;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends net.minecraft.world.level.entity.EntityAccess> {

    @Shadow private EntitySectionStorage<T> sectionStorage;
    @Shadow private it.unimi.dsi.fastutil.longs.Long2ObjectMap<Object> chunkLoadStatuses;

    // === method3: section-level key → IntSectionPos ===
    private static IntSectionPos getSectionPos(long key) {
        IntSectionPos sp = HashUtil.getSection(key);
        return sp != null ? sp
            : new IntSectionPos(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
    }

    @Overwrite
    public void dumpSections(Writer writer) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder()
                .addColumn("x").addColumn("y").addColumn("z")
                .addColumn("visibility").addColumn("load_status").addColumn("entity_count")
                .build(writer);
        this.sectionStorage.getAllChunksWithExistingSections().forEach(chunkKey -> {
            Object status = this.chunkLoadStatuses.get(chunkKey);
            this.sectionStorage.getExistingSectionPositionsInChunk(chunkKey).forEach(secKey -> {
                EntitySection<T> section = this.sectionStorage.getSection(secKey);
                if (section != null) {
                    try {
                        IntSectionPos sp = getSectionPos(secKey);
                        csvoutput.writeRow(sp.x, sp.y, sp.z, section.getStatus(), status, section.size());
                    } catch (IOException e) { throw new UncheckedIOException(e); }
                }
            });
        });
    }
}
