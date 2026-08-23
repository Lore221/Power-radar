package com.limbo2136.powerradar.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Геометрические операции для составных дисплеев. */
public final class RadarDisplayStructureResolver {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 5;

    private RadarDisplayStructureResolver() {
    }

    public static Direction right(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            case WEST -> Direction.SOUTH;
            case UP, DOWN -> Direction.EAST;
        };
    }

    public static BlockPos localOffset(BlockPos origin, Direction right, int u, int v) {
        return origin.relative(right, u).relative(Direction.UP, v);
    }

    public static boolean rectangleContains(
            BlockPos origin,
            Direction facing,
            int width,
            int height,
            BlockPos pos
    ) {
        if (origin == null || width <= 0 || height <= 0) {
            return false;
        }
        Direction right = right(facing);
        int dx = pos.getX() - origin.getX();
        int dy = pos.getY() - origin.getY();
        int dz = pos.getZ() - origin.getZ();
        int u = dx * right.getStepX() + dz * right.getStepZ();
        int depth = dx * facing.getStepX() + dz * facing.getStepZ();
        return depth == 0 && u >= 0 && u < width && dy >= 0 && dy < height;
    }

    public static int compareBlockPos(BlockPos first, BlockPos second) {
        int y = Integer.compare(second.getY(), first.getY());
        if (y != 0) {
            return y;
        }
        int x = Integer.compare(second.getX(), first.getX());
        if (x != 0) {
            return x;
        }
        return Integer.compare(second.getZ(), first.getZ());
    }

    public enum StructureStatus {
        NO_DISPLAY,
        ACTIVE,
        INVALID_MULTIPLE_CONTROLLERS
    }
}
