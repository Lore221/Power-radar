package com.limbo2136.powerradar.onboard;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Side-neutral geometry shared by module interaction and client outlines. */
public final class OnboardPanelGeometry {
    public static final double PANEL_HEIGHT = 11.0D / 16.0D;
    public static final double PANEL_TILT_RADIANS = Math.toRadians(22.5D);
    public static final double MODULE_SCALE = 0.25D;
    public static final double MODULE_SINK_SOURCE_PIXELS = 1.0D;
    public static final double OUTLINE_OFFSET = 1.0D / 256.0D;

    private static final double PIXELS_PER_BLOCK = 16.0D;
    private static final double COS_TILT = Math.cos(PANEL_TILT_RADIANS);
    private static final double SIN_TILT = Math.sin(PANEL_TILT_RADIANS);
    private static final Direction[] HORIZONTAL_FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final VoxelShape[][] SELECTION_SHAPES = createSelectionShapes();

    private OnboardPanelGeometry() {
    }

    @Nullable
    public static OnboardModuleSlot slotAt(BlockPos pos, Direction facing, BlockHitResult hit) {
        Basis basis = basis(pos, facing);
        Vec3 relative = hit.getLocation().subtract(basis.origin());
        double panelX = relative.dot(basis.xAxis()) * PIXELS_PER_BLOCK;
        double panelDepth = relative.dot(basis.depthAxis()) * PIXELS_PER_BLOCK;
        return OnboardModuleSlot.at(panelX, panelDepth);
    }

    @Nullable
    public static OnboardModuleColumn columnAt(BlockPos pos, Direction facing, BlockHitResult hit) {
        Basis basis = basis(pos, facing);
        Vec3 relative = hit.getLocation().subtract(basis.origin());
        double panelX = relative.dot(basis.xAxis()) * PIXELS_PER_BLOCK;
        double panelDepth = relative.dot(basis.depthAxis()) * PIXELS_PER_BLOCK;
        return OnboardModuleColumn.at(panelX, panelDepth);
    }

    public static VoxelShape selectionShape(Direction facing, int installedModuleMask) {
        return SELECTION_SHAPES[horizontalIndex(facing)][installedModuleMask & 0xF];
    }

    public static Vec3[] outline(BlockPos pos, Direction facing, OnboardModuleSlot slot) {
        Basis basis = basis(pos, facing);
        double minX = slot.panelX() / PIXELS_PER_BLOCK;
        double maxX = (slot.panelX() + OnboardModuleSlot.SIDE_PIXELS) / PIXELS_PER_BLOCK;
        double minDepth = slot.panelDepth() / PIXELS_PER_BLOCK;
        double maxDepth = (slot.panelDepth() + OnboardModuleSlot.SIDE_PIXELS) / PIXELS_PER_BLOCK;
        return new Vec3[] {
                point(basis, minX, minDepth, OUTLINE_OFFSET),
                point(basis, maxX, minDepth, OUTLINE_OFFSET),
                point(basis, maxX, maxDepth, OUTLINE_OFFSET),
                point(basis, minX, maxDepth, OUTLINE_OFFSET)
        };
    }

    private static Basis basis(BlockPos pos, Direction facing) {
        Direction modelX = facing.getClockWise();
        Direction rear = facing.getOpposite();
        Vec3 xAxis = new Vec3(modelX.getStepX(), 0.0D, modelX.getStepZ());
        Vec3 depthAxis = new Vec3(rear.getStepX() * COS_TILT, SIN_TILT, rear.getStepZ() * COS_TILT);
        Vec3 normal = new Vec3(facing.getStepX() * SIN_TILT, COS_TILT, facing.getStepZ() * SIN_TILT);
        Vec3 origin = new Vec3(pos.getX() + 0.5D, pos.getY() + PANEL_HEIGHT, pos.getZ() + 0.5D)
                .add(facing.getStepX() * 0.5D, 0.0D, facing.getStepZ() * 0.5D)
                .subtract(xAxis.scale(0.5D));
        return new Basis(origin, xAxis, depthAxis, normal);
    }

    private static Vec3 point(Basis basis, double x, double depth, double normalOffset) {
        return basis.origin()
                .add(basis.xAxis().scale(x))
                .add(basis.depthAxis().scale(depth))
                .add(basis.normal().scale(normalOffset));
    }

    private static VoxelShape[][] createSelectionShapes() {
        VoxelShape[][] shapes = new VoxelShape[HORIZONTAL_FACINGS.length][16];
        for (int facingIndex = 0; facingIndex < HORIZONTAL_FACINGS.length; facingIndex++) {
            Direction facing = HORIZONTAL_FACINGS[facingIndex];
            for (int moduleMask = 0; moduleMask < 16; moduleMask++) {
                VoxelShape shape = Shapes.empty();
                shape = addBox(shape, facing, 0.0D, 0.0D, 0.0D, 16.0D, 11.0D, 16.0D);
                shape = addBox(shape, facing, 0.0D, 11.0D, 10.0D, 16.0D, 15.21D, 16.0D);

                // Eleven narrow steps closely follow the model's -22.5-degree panel.
                for (int depthPixel = 0; depthPixel < 11; depthPixel++) {
                    double minDepth = depthPixel * COS_TILT;
                    double maxDepth = (depthPixel + 1.0D) * COS_TILT;
                    double maxHeight = 11.0D + (depthPixel + 1.0D) * SIN_TILT;
                    shape = addBox(shape, facing,
                            0.0D, 11.0D, minDepth,
                            16.0D, maxHeight, maxDepth);
                }

                for (OnboardModuleSlot slot : OnboardModuleSlot.values()) {
                    if ((moduleMask & (1 << slot.index())) == 0) {
                        continue;
                    }
                    double minDepth = slot.panelDepth() * COS_TILT - SIN_TILT;
                    double maxDepth = (slot.panelDepth() + OnboardModuleSlot.SIDE_PIXELS) * COS_TILT;
                    double minHeight = 11.0D + slot.panelDepth() * SIN_TILT;
                    double maxHeight = 11.0D
                            + (slot.panelDepth() + OnboardModuleSlot.SIDE_PIXELS) * SIN_TILT
                            + COS_TILT;
                    shape = addBox(shape, facing,
                            slot.panelX(), minHeight, minDepth,
                            slot.panelX() + OnboardModuleSlot.SIDE_PIXELS, maxHeight, maxDepth);
                }
                shapes[facingIndex][moduleMask] = shape.optimize();
            }
        }
        return shapes;
    }

    private static VoxelShape addBox(
            VoxelShape shape,
            Direction facing,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        double rotatedMinX = minX;
        double rotatedMinZ = minZ;
        double rotatedMaxX = maxX;
        double rotatedMaxZ = maxZ;
        switch (facing) {
            case EAST -> {
                rotatedMinX = 16.0D - maxZ;
                rotatedMaxX = 16.0D - minZ;
                rotatedMinZ = minX;
                rotatedMaxZ = maxX;
            }
            case SOUTH -> {
                rotatedMinX = 16.0D - maxX;
                rotatedMaxX = 16.0D - minX;
                rotatedMinZ = 16.0D - maxZ;
                rotatedMaxZ = 16.0D - minZ;
            }
            case WEST -> {
                rotatedMinX = minZ;
                rotatedMaxX = maxZ;
                rotatedMinZ = 16.0D - maxX;
                rotatedMaxZ = 16.0D - minX;
            }
            case NORTH, UP, DOWN -> { }
        }
        return Shapes.or(shape, Block.box(
                rotatedMinX, minY, rotatedMinZ,
                rotatedMaxX, maxY, rotatedMaxZ));
    }

    private static int horizontalIndex(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            case UP, DOWN -> 0;
        };
    }

    private record Basis(Vec3 origin, Vec3 xAxis, Vec3 depthAxis, Vec3 normal) {
    }
}
