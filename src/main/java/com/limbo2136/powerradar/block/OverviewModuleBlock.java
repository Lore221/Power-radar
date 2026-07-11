package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.block.entity.OverviewModuleBlockEntity;
import com.limbo2136.powerradar.compat.create.OverviewModulePlacementHelper;
import com.mojang.serialization.MapCodec;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class OverviewModuleBlock extends BaseEntityBlock {
    public static final MapCodec<OverviewModuleBlock> CODEC = simpleCodec(OverviewModuleBlock::new);
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new OverviewModulePlacementHelper());
    private static final VoxelShape FULL_BLOCK_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public OverviewModuleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!player.isShiftKeyDown()
                && player.mayBuild()
                && helper.matchesState(state)
                && helper.matchesItem(stack)
                && stack.getItem() instanceof BlockItem blockItem) {
            PlacementOffset offset = helper.getOffset(player, level, state, pos, hitResult);
            offset.placeInWorld(level, blockItem, player, hand, hitResult);
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OverviewModuleBlockEntity(pos, state);
    }
}
