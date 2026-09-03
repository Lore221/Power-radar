package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.RadarDisplayBlock;
import com.limbo2136.powerradar.block.RadarDisplayFrameShape;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.block.entity.RadarDisplayBlockEntity;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class RadarDisplayCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final CTSpriteShiftEntry FRONT = shift(
            "radar_display/radar_display_front", "radar_display/radar_display_front_connected");
    private static final CTSpriteShiftEntry BACK = shift(
            "radar_display/radar_display_back", "radar_display/radar_display_back_connectted");
    private static final CTSpriteShiftEntry SIDE_HORIZONTAL =
            shift("radar_display/radar_display_side_horizontal", "radar_display/radar_display_side_horizontal_connected");
    private static final CTSpriteShiftEntry SIDE_VERTICAL =
            shift("radar_display/radar_display_side_vertical", "radar_display/radar_display_side_vertical_connected");

    @Override
    @Nullable
    public CTSpriteShiftEntry getShift(
            BlockState state,
            Direction direction,
            @Nullable TextureAtlasSprite sprite
    ) {
        if (!state.hasProperty(RadarDisplayBlock.FACING)) {
            return null;
        }

        Direction facing = state.getValue(RadarDisplayBlock.FACING);
        CTSpriteShiftEntry shift;
        if (direction == facing) {
            shift = FRONT;
        } else if (direction == facing.getOpposite()) {
            shift = BACK;
        } else if (direction.getAxis().isVertical()) {
            shift = SIDE_HORIZONTAL;
        } else {
            shift = SIDE_VERTICAL;
        }
        return sprite == null || shift.getOriginal() == sprite ? shift : null;
    }

    @Override
    protected Direction getRightDirection(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face
    ) {
        if (face.getAxis().isVertical() && state.hasProperty(RadarDisplayBlock.FACING)) {
            // Горизонтальный атлас боковины меняется вдоль ширины дисплея.
            Direction displayRight = RadarDisplayStructureResolver.right(
                    state.getValue(RadarDisplayBlock.FACING));
            // На верхней грани U направлена противоположно нижней.
            return face == Direction.UP ? displayRight.getOpposite() : displayRight;
        }
        return super.getRightDirection(level, pos, state, face);
    }

    @Override
    protected Direction getUpDirection(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face
    ) {
        if (face.getAxis().isVertical() && state.hasProperty(RadarDisplayBlock.FACING)) {
            // В глубину соседних дисплеев нет, поэтому вертикальная ось CT остаётся
            // несоединённой и выбирается нужная горизонтальная строка атласа.
            return state.getValue(RadarDisplayBlock.FACING);
        }
        return super.getUpDirection(level, pos, state, face);
    }

    @Override
    public boolean connectsTo(
            BlockState state,
            BlockState other,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockPos otherPos,
            Direction face
    ) {
        if (!super.connectsTo(state, other, level, pos, otherPos, face)
                || !state.hasProperty(RadarDisplayBlock.FACING)
                || !other.hasProperty(RadarDisplayBlock.FACING)
                || state.getValue(RadarDisplayBlock.FACING) != other.getValue(RadarDisplayBlock.FACING)
                || state.getValue(RadarDisplayBlock.ACTIVE) != other.getValue(RadarDisplayBlock.ACTIVE)) {
            return false;
        }

        if (!(level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity display)
                || !(level.getBlockEntity(otherPos) instanceof RadarDisplayBlockEntity otherDisplay)
                || !display.displayId().equals(otherDisplay.displayId())) {
            return false;
        }

        Direction connection = connectionDirection(pos, otherPos);
        if (connection == null) {
            return false;
        }
        Direction facing = state.getValue(RadarDisplayBlock.FACING);
        FrameEdge ownEdge = FrameEdge.forDirection(facing, connection);
        FrameEdge otherEdge = FrameEdge.forDirection(facing, connection.getOpposite());
        return ownEdge != null
                && otherEdge != null
                && !ownEdge.isPresent(state.getValue(RadarDisplayBlock.FRAME_SHAPE))
                && !otherEdge.isPresent(other.getValue(RadarDisplayBlock.FRAME_SHAPE));
    }

    @Nullable
    private static Direction connectionDirection(BlockPos pos, BlockPos otherPos) {
        for (Direction direction : Direction.values()) {
            if (pos.relative(direction).equals(otherPos)) {
                return direction;
            }
        }
        return null;
    }

    private static CTSpriteShiftEntry shift(String original, String connected) {
        return CTSpriteShifter.getCT(
                AllCTTypes.RECTANGLE,
                PowerRadar.id("block/" + original),
                PowerRadar.id("block/" + connected));
    }

    private enum FrameEdge {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT;

        @Nullable
        private static FrameEdge forDirection(Direction facing, Direction direction) {
            if (direction == Direction.UP) {
                return TOP;
            }
            if (direction == Direction.DOWN) {
                return BOTTOM;
            }
            Direction right = RadarDisplayStructureResolver.right(facing);
            if (direction == right) {
                return RIGHT;
            }
            if (direction == right.getOpposite()) {
                return LEFT;
            }
            return null;
        }

        private boolean isPresent(RadarDisplayFrameShape shape) {
            return switch (this) {
                case TOP -> shape.hasTopEdge();
                case BOTTOM -> shape.hasBottomEdge();
                case LEFT -> shape.hasLeftEdge();
                case RIGHT -> shape.hasRightEdge();
            };
        }
    }
}
