package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record RadarDisplayStructure(
        BlockPos origin,
        int width,
        int height,
        Direction facing,
        Set<BlockPos> positions
) {
    public RadarDisplayStructure(BlockPos origin, int size, Direction facing, Set<BlockPos> positions) {
        this(origin, size, size, facing, positions);
    }

    public int size() {
        return Math.min(this.width, this.height);
    }

    public boolean assembled() {
        return this.origin != null
                && this.width >= RadarDisplayStructureResolver.MIN_SIZE
                && this.width <= RadarDisplayStructureResolver.MAX_SIZE
                && this.height >= RadarDisplayStructureResolver.MIN_SIZE
                && this.height <= RadarDisplayStructureResolver.MAX_SIZE
                && this.positions.size() == this.width * this.height;
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
        if (this.width <= 1 && this.height <= 1) {
            return RadarDisplayFrameShape.SINGLE;
        }
        Direction rightAxis = RadarDisplayStructureResolver.right(this.facing);
        int u = coordinate(this.origin, pos, rightAxis);
        int v = pos.getY() - this.origin.getY();

        boolean left = u == 0;
        boolean right = u == this.width - 1;
        boolean bottom = v == 0;
        boolean top = v == this.height - 1;

        if (this.width == 1) {
            if (top) {
                return RadarDisplayFrameShape.VERTICAL_TOP;
            }
            if (bottom) {
                return RadarDisplayFrameShape.VERTICAL_BOTTOM;
            }
            return RadarDisplayFrameShape.VERTICAL;
        }
        if (this.height == 1) {
            if (left) {
                return RadarDisplayFrameShape.HORIZONTAL_LEFT;
            }
            if (right) {
                return RadarDisplayFrameShape.HORIZONTAL_RIGHT;
            }
            return RadarDisplayFrameShape.HORIZONTAL;
        }

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
        return rectanglePositions(origin, facing, size, size);
    }

    public static List<BlockPos> rectanglePositions(
            BlockPos origin,
            Direction facing,
            int width,
            int height
    ) {
        Direction right = RadarDisplayStructureResolver.right(facing);
        ArrayList<BlockPos> positions = new ArrayList<>(width * height);
        for (int u = 0; u < width; u++) {
            for (int v = 0; v < height; v++) {
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
