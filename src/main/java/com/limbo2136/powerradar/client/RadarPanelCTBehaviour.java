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
    private static final CTSpriteShiftEntry FRONT = omnidirectional("radar_panel_front");
    private static final CTSpriteShiftEntry BACK = omnidirectional("radar_panel_back");
    private static final CTSpriteShiftEntry SIDE = CTSpriteShifter.getCT(
            AllCTTypes.RECTANGLE,
            PowerRadar.id("block/radar_panel_side"),
            PowerRadar.id("block/radar_panel_side_connected"));

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
        CTSpriteShiftEntry shift = direction == facing
                ? FRONT
                : direction == facing.getOpposite() ? BACK : SIDE;
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
                        != panelState.getValue(RadarPanelBlock.FACING);
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
        return CTSpriteShifter.getCT(
                AllCTTypes.OMNIDIRECTIONAL,
                PowerRadar.id("block/" + texture),
                PowerRadar.id("block/" + texture + "_connected"));
    }
}
