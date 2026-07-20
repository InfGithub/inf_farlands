package com.inf.farlands;

/**
 * A lightweight record key for three-dimensional section positions using long coordinates.
 * Replaces the packed long returned by {@code SectionPos.asLong()} throughout the system.
 *
 * <p>Implements {@link Comparable} with ordering X → Z → Y, matching the bit layout of the
 * vanilla packed long (where X occupies the highest bits, followed by Z, then Y). This ordering
 * enables {@code TreeSet.subSet} range queries that are equivalent to the original
 * {@code LongAVLTreeSet.subSet} queries on packed section positions.</p>
 *
 * @param x the X section coordinate
 * @param y the Y section coordinate
 * @param z the Z section coordinate
 */
public record SectionKey(long x, long y, long z) implements Comparable<SectionKey> {

    @Override
    public int compareTo(SectionKey o) {
        int c = Long.compare(this.x, o.x);
        if (c != 0) return c;
        c = Long.compare(this.z, o.z);
        if (c != 0) return c;
        return Long.compare(this.y, o.y);
    }
}
