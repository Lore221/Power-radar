package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.RadarPanelBlock;
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

public final class RadarPanelCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final CTSpriteShiftEntry FRONT = omnidirectional("radar_panel/radar_panel_front");
    private static final CTSpriteShiftEntry BACK_SURFACE = omnidirectional(
            "radar_panel/radar_panel_back", "radar_panel/radar_panel_back_connected");
    private static final CTSpriteShiftEntry BACK_BASED = omnidirectional(
            "radar_panel/radar_panel_back", "radar_panel/radar_panel_back_connected_based");
    private static final CTSpriteShiftEntry BACK_AIR = omnidirectional(
            "radar_panel/radar_panel_back", "radar_panel/radar_panel_back_air");
    private static final CTSpriteShiftEntry SIDE_SURFACE = rectangle(
            "radar_panel/radar_panel_side", "radar_panel/radar_panel_side_connected");
    private static final CTSpriteShiftEntry SIDE_BASED = rectangle(
            "radar_panel/radar_panel_side", "radar_panel/radar_panel_side_connected_based");
    private static final CTSpriteShiftEntry SIDE_AIR = rectangle(
            "radar_panel/radar_panel_side", "radar_panel/radar_panel_side_connected_air");

    private static CTSpriteShiftEntry side(RadarPanelBlock.ControllerType controllerType) {
        return switch (controllerType) {
            case BASED -> SIDE_BASED;
            case AIR -> SIDE_AIR;
            case SURFACE -> SIDE_SURFACE;
        };
    }

    private static CTSpriteShiftEntry back(RadarPanelBlock.ControllerType controllerType) {
        return switch (controllerType) {
            case BASED -> BACK_BASED;
            case AIR -> BACK_AIR;
            case SURFACE -> BACK_SURFACE;
        };
    }

    private static CTSpriteShiftEntry rectangle(String original, String connected) {
        return CTSpriteShifter.getCT(
            AllCTTypes.RECTANGLE,
            PowerRadar.id("block/" + original),
            PowerRadar.id("block/" + connected));
    }

    @Override
    @Nullable
    public CTSpriteShiftEntry getShift(
            BlockState state,
            Direction direction,
            @Nullable TextureAtlasSprite sprite
    ) {
        if (!state.hasProperty(RadarPanelBlock.FACING)) {
            return null;
        }

        Direction facing = state.getValue(RadarPanelBlock.FACING);
        RadarPanelBlock.ControllerType controllerType = state.hasProperty(RadarPanelBlock.CONTROLLER_TYPE)
                ? state.getValue(RadarPanelBlock.CONTROLLER_TYPE)
                : RadarPanelBlock.ControllerType.SURFACE;
        CTSpriteShiftEntry shift = direction == facing
                ? FRONT
                : direction == facing.getOpposite() ? back(controllerType) : side(controllerType);
        return sprite == null || shift.getOriginal() == sprite ? shift : null;
    }

    @Override
    protected Direction getUpDirection(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face
    ) {
        if (face.getAxis().isVertical() && state.hasProperty(RadarPanelBlock.FACING)) {
            Direction panelRight = state.getValue(RadarPanelBlock.FACING).getCounterClockWise();
            return panelRight;
        }
        return super.getUpDirection(level, pos, state, face);
    }

    @Override
    protected Direction getRightDirection(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face
    ) {
        if (face.getAxis().isVertical() && state.hasProperty(RadarPanelBlock.FACING)) {
            // Горизонталь CT направляем в глубину панели, где соединений быть не может.
            return state.getValue(RadarPanelBlock.FACING);
        }
        return super.getRightDirection(level, pos, state, face);
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
                || !state.hasProperty(RadarPanelBlock.FACING)
                || !other.hasProperty(RadarPanelBlock.FACING)
                || state.getValue(RadarPanelBlock.FACING) != other.getValue(RadarPanelBlock.FACING)) {
            return false;
        }

        if (state.hasProperty(RadarPanelBlock.CONTROLLER_TYPE)
                && other.hasProperty(RadarPanelBlock.CONTROLLER_TYPE)
                && state.getValue(RadarPanelBlock.CONTROLLER_TYPE)
                        != other.getValue(RadarPanelBlock.CONTROLLER_TYPE)) {
            return false;
        }

        Direction facing = state.getValue(RadarPanelBlock.FACING);
        if (face == facing || face == facing.getOpposite()) {
            return true;
        }

        Direction connection = connectionDirection(pos, otherPos);
        Direction panelRight = facing.getCounterClockWise();
        boolean followsPanelEdge = connection == Direction.UP
                || connection == Direction.DOWN
                || connection == panelRight
                || connection == panelRight.getOpposite();
        return followsPanelEdge
                && isExposedSide(level, pos, state, face)
                && isExposedSide(level, otherPos, other, face);
    }

    private static boolean isExposedSide(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState panelState,
            Direction side
    ) {
        BlockState outwardState = level.getBlockState(pos.relative(side));
        return !outwardState.is(panelState.getBlock())
                || !outwardState.hasProperty(RadarPanelBlock.FACING)
                || outwardState.getValue(RadarPanelBlock.FACING)
                        != panelState.getValue(RadarPanelBlock.FACING)
                || outwardState.hasProperty(RadarPanelBlock.CONTROLLER_TYPE)
                        && panelState.hasProperty(RadarPanelBlock.CONTROLLER_TYPE)
                        && outwardState.getValue(RadarPanelBlock.CONTROLLER_TYPE)
                                != panelState.getValue(RadarPanelBlock.CONTROLLER_TYPE);
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

    private static CTSpriteShiftEntry omnidirectional(String texture) {
        return omnidirectional(texture, texture + "_connected");
    }

    private static CTSpriteShiftEntry omnidirectional(String original, String connected) {
        return CTSpriteShifter.getCT(
                AllCTTypes.OMNIDIRECTIONAL,
                PowerRadar.id("block/" + original),
                PowerRadar.id("block/" + connected));
    }
}
