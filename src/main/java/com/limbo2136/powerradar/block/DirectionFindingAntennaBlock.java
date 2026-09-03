package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.compat.create.DirectionFindingAntennaPlacementHelper;
import com.mojang.serialization.MapCodec;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DirectionFindingAntennaBlock extends Block {
    public static final MapCodec<DirectionFindingAntennaBlock> CODEC =
            simpleCodec(DirectionFindingAntennaBlock::new);
    public static final BooleanProperty CONNECTED_UP = BooleanProperty.create("connected_up");

    private static final int PLACEMENT_HELPER_ID =
            PlacementHelpers.register(new DirectionFindingAntennaPlacementHelper());
    private static final VoxelShape HEAD_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape MAST_SHAPE = Block.box(6.5, 0.0, 6.5, 9.5, 16.0, 9.5);
    private static boolean applyingConnectionState;

    public DirectionFindingAntennaBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(CONNECTED_UP, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        reconcileAround(level, pos);
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(oldState, level, pos, newState, moving);
        if (!oldState.is(newState.getBlock())) {
            reconcileAround(level, pos);
        }
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state);
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTED_UP);
    }

    private void reconcileAround(Level level, BlockPos changedPos) {
        if (level.isClientSide() || applyingConnectionState) {
            return;
        }

        applyingConnectionState = true;
        try {
            updateConnectionState(level, changedPos.below());
            updateConnectionState(level, changedPos);
            updateConnectionState(level, changedPos.above());
        } finally {
            applyingConnectionState = false;
        }
    }

    private void updateConnectionState(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) {
            return;
        }
        BlockState target = state.setValue(CONNECTED_UP, level.getBlockState(pos.above()).is(this));
        if (!target.equals(state)) {
            level.setBlock(pos, target, Block.UPDATE_ALL);
        }
    }

    private static VoxelShape shapeFor(BlockState state) {
        return state.getValue(CONNECTED_UP) ? MAST_SHAPE : HEAD_SHAPE;
    }
}
