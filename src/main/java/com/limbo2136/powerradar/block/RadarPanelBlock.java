package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.compat.create.RadarPanelPlacementHelper;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RadarPanelBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final MapCodec<RadarPanelBlock> CODEC = simpleCodec(RadarPanelBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new RadarPanelPlacementHelper());

    private static final VoxelShape CENTER_CONNECTOR_SHAPE = Block.box(6.0, -2.0, 6.0, 10.0, 0.0, 10.0);
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 13.0),
            CENTER_CONNECTOR_SHAPE);
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 3.0, 16.0, 16.0, 16.0),
            CENTER_CONNECTOR_SHAPE);
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(0.0, 0.0, 0.0, 13.0, 16.0, 16.0),
            CENTER_CONNECTOR_SHAPE);
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(3.0, 0.0, 0.0, 16.0, 16.0, 16.0),
            CENTER_CONNECTOR_SHAPE);

    public RadarPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        Direction facing;
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = context.getHorizontalDirection();
        } else {
            facing = clickedFace.getAxis().isHorizontal()
                    ? clickedFace
                    : context.getHorizontalDirection().getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
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

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case NORTH, UP, DOWN -> NORTH_SHAPE;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }
}
