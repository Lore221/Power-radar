package com.limbo2136.powerradar.compat.create;

import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class PowerRadarMovementChecks {
    private static boolean registered;

    private PowerRadarMovementChecks() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlockMovementChecks.registerMovementAllowedCheck(PowerRadarMovementChecks::canMoveBlock);
        BlockMovementChecks.registerAttachedCheck(PowerRadarMovementChecks::isAttachedTowards);
    }

    private static BlockMovementChecks.CheckResult canMoveBlock(BlockState state, Level level, BlockPos pos) {
        return BlockMovementChecks.CheckResult.PASS;
    }

    private static BlockMovementChecks.CheckResult isAttachedTowards(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction direction
    ) {
        if (!isRadarPanel(state)) {
            return BlockMovementChecks.CheckResult.PASS;
        }
        Direction facing = state.getValue(RadarPanelBlock.FACING);
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        if (isPanelPlaneDirection(facing, direction) && isRadarPanel(neighbour)
                && neighbour.getValue(RadarPanelBlock.FACING) == facing) {
            return BlockMovementChecks.CheckResult.SUCCESS;
        }
        return BlockMovementChecks.CheckResult.PASS;
    }

    private static boolean isPanelPlaneDirection(Direction facing, Direction direction) {
        return direction == Direction.UP
                || direction == Direction.DOWN
                || direction == facing.getClockWise()
                || direction == facing.getCounterClockWise();
    }

    private static boolean isRadarPanel(BlockState state) {
        return state.is(ModBlocks.RADAR_PANEL.get()) && state.hasProperty(RadarPanelBlock.FACING);
    }
}
