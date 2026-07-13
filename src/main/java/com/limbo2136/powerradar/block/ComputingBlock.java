package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.block.entity.ComputingBlockEntity;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ComputingBlock extends BaseEntityBlock {
    public static final MapCodec<ComputingBlock> CODEC = simpleCodec(ComputingBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ComputingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, net.minecraft.world.InteractionHand hand,
                                          BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof RadarFilterCardItem card)
                || !(level.getBlockEntity(pos) instanceof ComputingBlockEntity computer)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && computer.insertCard(card.kind(), stack, player)) {
            return ItemInteractionResult.SUCCESS;
        }
        return level.isClientSide() ? ItemInteractionResult.SUCCESS : ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof ComputingBlockEntity computer) {
            if (!level.isClientSide()) {
                computer.extractCard(player, slotFromHit(state, hitResult));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    private static int slotFromHit(BlockState state, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return -1;
        }
        double localY = hit.getLocation().y - hit.getBlockPos().getY();
        return localY >= 2.0D / 3.0D ? 0 : localY >= 1.0D / 3.0D ? 1 : 2;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ComputingBlockEntity computer) {
            computer.dropCards();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputingBlockEntity(pos, state);
    }
}
