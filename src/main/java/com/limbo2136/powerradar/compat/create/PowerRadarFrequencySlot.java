package com.limbo2136.powerradar.compat.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class PowerRadarFrequencySlot extends ValueBoxTransform.Dual {
    private static final double SLOT_OFFSET = 2.5D / 16.0D;
    private static final double FACE_OFFSET = 7.99D / 16.0D;
    private final Face face;

    public PowerRadarFrequencySlot(boolean first, Face face) {
        super(first);
        this.face = face;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = slotFace(state);
        Direction right = facing.getAxis().isVertical() ? Direction.EAST : facing.getClockWise();
        double offset = isFirst() ? -SLOT_OFFSET : SLOT_OFFSET;
        return new Vec3(0.5D, 0.5D, 0.5D)
                .add(facing.getStepX() * FACE_OFFSET, facing.getStepY() * FACE_OFFSET,
                        facing.getStepZ() * FACE_OFFSET)
                .add(right.getStepX() * offset, right.getStepY() * offset, right.getStepZ() * offset);
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack poseStack) {
        Direction facing = slotFace(state);
        float yaw = facing.getAxis().isVertical() ? 0.0F : AngleHelper.horizontalAngle(facing) + 180.0F;
        float pitch = facing == Direction.UP ? 90.0F : facing == Direction.DOWN ? 270.0F : 0.0F;
        TransformStack.of(poseStack).rotateYDegrees(yaw).rotateXDegrees(pitch);
    }

    @Override
    public float getScale() {
        return 0.4975F;
    }

    private static Direction facing(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    private Direction slotFace(BlockState state) {
        Direction front = facing(state);
        if (this.face == Face.SIDE) {
            return front.getAxis().isVertical() ? Direction.EAST : front.getClockWise();
        }
        return front;
    }

    public enum Face {
        FRONT,
        SIDE
    }
}
