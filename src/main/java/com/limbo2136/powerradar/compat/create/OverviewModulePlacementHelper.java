package com.limbo2136.powerradar.compat.create;

import com.limbo2136.powerradar.radar.RadarModuleConstants;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class OverviewModulePlacementHelper implements IPlacementHelper {
    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.is(ModBlocks.OVERVIEW_MODULE.get().asItem());
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.is(ModBlocks.OVERVIEW_MODULE.get());
    }

    @Override
    public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hitResult) {
        BlockPos base = bottomModule(level, pos);
        int count = 0;
        while (count < RadarModuleConstants.maxOverviewModules()
                && level.getBlockState(base.above(count)).is(ModBlocks.OVERVIEW_MODULE.get())) {
            count++;
        }
        if (count <= 0 || count >= RadarModuleConstants.maxOverviewModules()) {
            return PlacementOffset.fail();
        }

        BlockPos targetPos = base.above(count);
        if (!level.getBlockState(targetPos).canBeReplaced()) {
            return PlacementOffset.fail();
        }
        return PlacementOffset.success(targetPos, newState -> newState);
    }

    private static BlockPos bottomModule(Level level, BlockPos pos) {
        BlockPos current = pos;
        while (level.getBlockState(current.below()).is(ModBlocks.OVERVIEW_MODULE.get())) {
            current = current.below();
        }
        return current;
    }
}
