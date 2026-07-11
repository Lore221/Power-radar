package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record RadarDisplayStructure(BlockPos origin, int size, Direction facing, Set<BlockPos> positions) {
    public boolean assembled() {
        return this.origin != null
                && this.size >= RadarDisplayStructureResolver.MIN_SIZE
                && this.size <= RadarDisplayStructureResolver.MAX_SIZE
                && this.positions.size() == this.size * this.size;
    }

    public boolean contains(BlockPos pos) {
        return this.positions.contains(pos);
    }

    public boolean isValid(Level level) {
        if (!assembled()) {
            return false;
        }
        for (BlockPos pos : this.positions) {
            if (!level.isLoaded(pos) || !isMatchingDisplay(level.getBlockState(pos), this.facing)) {
                return false;
            }
        }
        return true;
    }

    public RadarDisplayFrameShape frameShape(BlockPos pos) {
        if (this.size <= 1) {
            return RadarDisplayFrameShape.SINGLE;
        }
        Direction rightAxis = RadarDisplayStructureResolver.right(this.facing);
        int u = coordinate(this.origin, pos, rightAxis);
        int v = pos.getY() - this.origin.getY();

        boolean left = u == 0;
        boolean right = u == this.size - 1;
        boolean bottom = v == 0;
        boolean top = v == this.size - 1;

        if (top && left) {
            return RadarDisplayFrameShape.TOP_LEFT;
        }
        if (top && right) {
            return RadarDisplayFrameShape.TOP_RIGHT;
        }
        if (bottom && left) {
            return RadarDisplayFrameShape.BOTTOM_LEFT;
        }
        if (bottom && right) {
            return RadarDisplayFrameShape.BOTTOM_RIGHT;
        }
        if (top) {
            return RadarDisplayFrameShape.TOP;
        }
        if (bottom) {
            return RadarDisplayFrameShape.BOTTOM;
        }
        if (left) {
            return RadarDisplayFrameShape.LEFT;
        }
        if (right) {
            return RadarDisplayFrameShape.RIGHT;
        }
        return RadarDisplayFrameShape.CENTER;
    }

    public static List<BlockPos> squarePositions(BlockPos origin, Direction facing, int size) {
        Direction right = RadarDisplayStructureResolver.right(facing);
        ArrayList<BlockPos> positions = new ArrayList<>(size * size);
        for (int u = 0; u < size; u++) {
            for (int v = 0; v < size; v++) {
                positions.add(RadarDisplayStructureResolver.localOffset(origin, right, u, v));
            }
        }
        return List.copyOf(positions);
    }

    static boolean isMatchingDisplay(BlockState state, Direction facing) {
        return state.is(ModBlocks.RADAR_DISPLAY.get())
                && state.hasProperty(RadarDisplayBlock.FACING)
                && state.getValue(RadarDisplayBlock.FACING) == facing;
    }

    private static int coordinate(BlockPos origin, BlockPos pos, Direction axis) {
        int dx = pos.getX() - origin.getX();
        int dz = pos.getZ() - origin.getZ();
        return dx * axis.getStepX() + dz * axis.getStepZ();
    }
}
