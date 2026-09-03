package com.limbo2136.powerradar.compat.create;

import com.limbo2136.powerradar.block.DirectionFindingAntennaBlock;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class DirectionFindingAntennaPlacementHelper implements IPlacementHelper {
    private static final int MAX_STACK_HEIGHT = 8;

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof DirectionFindingAntennaBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return state -> state.getBlock() instanceof DirectionFindingAntennaBlock;
    }

    @Override
    public PlacementOffset getOffset(
            Player player,
            Level level,
            BlockState state,
            BlockPos pos,
            BlockHitResult hitResult
    ) {
        BlockPos base = bottomAntenna(level, pos);
        int count = 0;
        while (count < MAX_STACK_HEIGHT
                && level.getBlockState(base.above(count)).getBlock() instanceof DirectionFindingAntennaBlock) {
            count++;
        }
        if (count >= MAX_STACK_HEIGHT) {
            return PlacementOffset.fail();
        }

        BlockPos targetPos = base.above(count);
        if (!level.getBlockState(targetPos).canBeReplaced()) {
            return PlacementOffset.fail();
        }
        return PlacementOffset.success(targetPos, newState -> newState);
    }

    private static BlockPos bottomAntenna(Level level, BlockPos pos) {
        BlockPos current = pos;
        while (level.getBlockState(current.below()).getBlock() instanceof DirectionFindingAntennaBlock) {
            current = current.below();
        }
        return current;
    }
}
