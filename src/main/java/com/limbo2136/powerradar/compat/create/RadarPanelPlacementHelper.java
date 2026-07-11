package com.limbo2136.powerradar.compat.create;

import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.List;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RadarPanelPlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.is(ModBlocks.RADAR_PANEL.get().asItem());
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.is(ModBlocks.RADAR_PANEL.get())
                && state.hasProperty(RadarPanelBlock.FACING);
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hitResult) {
        if (!state.hasProperty(RadarPanelBlock.FACING)) {
            return PlacementOffset.fail();
        }

        Direction clickedFacing = state.getValue(RadarPanelBlock.FACING);
        Direction.Axis excludedAxis = clickedFacing.getAxis();
        List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(
                pos,
                hitResult.getLocation(),
                excludedAxis,
                direction -> level.getBlockState(pos.relative(direction)).canBeReplaced()
        );
        if (directions.isEmpty()) {
            return PlacementOffset.fail();
        }

        BlockPos targetPos = pos.relative(directions.getFirst());
        return PlacementOffset.success(targetPos, newState -> newState.hasProperty(RadarPanelBlock.FACING)
                ? newState.setValue(RadarPanelBlock.FACING, clickedFacing)
                : newState);
    }
}
