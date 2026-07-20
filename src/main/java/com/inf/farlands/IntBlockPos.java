package com.inf.farlands;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class IntBlockPos {
    public final int x, y, z;
    public IntBlockPos(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public IntBlockPos(BlockPos pos) { this(pos.getX(), pos.getY(), pos.getZ()); }
    public IntBlockPos offset(Direction d) { return new IntBlockPos(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()); }
    public IntBlockPos offset(int dx, int dy, int dz) { return new IntBlockPos(x + dx, y + dy, z + dz); }
}
