package com.inf.farlands;

/**
 * A lightweight record key for three-dimensional block positions using long coordinates.
 * Replaces the packed long returned by {@code BlockPos.asLong()} throughout the system.
 *
 * <p>This key stores the full precision of each coordinate without the 26+12+26 bit truncation
 * inherent in the vanilla packed long format.</p>
 *
 * @param x the X coordinate
 * @param y the Y coordinate
 * @param z the Z coordinate
 */
public record PosKey(long x, long y, long z) {
}
