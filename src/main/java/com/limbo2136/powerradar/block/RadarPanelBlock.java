package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.compat.create.RadarPanelPlacementHelper;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RadarPanelBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final MapCodec<RadarPanelBlock> CODEC = simpleCodec(RadarPanelBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<PanelShape> SHAPE = EnumProperty.create("shape", PanelShape.class);
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
    private static boolean applyingConnectionStates;

    public RadarPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, PanelShape.SOLO));
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        reconcileAround(level, pos, state.getValue(FACING));
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        super.onRemove(oldState, level, pos, newState, moving);
        if (!oldState.is(newState.getBlock())) {
            reconcileAround(level, pos, oldState.getValue(FACING));
        }
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
        builder.add(FACING, SHAPE);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState
                .setValue(FACING, originalState.getValue(FACING).getClockWise())
                .setValue(SHAPE, PanelShape.SOLO);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Direction oldFacing = state.getValue(FACING);
        InteractionResult result = IWrenchable.super.onWrenched(state, context);
        if (!context.getLevel().isClientSide() && result.consumesAction()) {
            BlockPos pos = context.getClickedPos();
            reconcileAround(context.getLevel(), pos, oldFacing);
            BlockState rotatedState = context.getLevel().getBlockState(pos);
            if (rotatedState.is(this)) {
                reconcileAround(context.getLevel(), pos, rotatedState.getValue(FACING));
            }
        }
        return result;
    }

    private BlockState connectedState(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction left = facing.getClockWise();
        Direction right = facing.getCounterClockWise();
        int connections = 0;
        if (isConnectedPanel(level, pos.above(), facing)) {
            connections |= 1;
        }
        if (isConnectedPanel(level, pos.below(), facing)) {
            connections |= 2;
        }
        if (isConnectedPanel(level, pos.relative(left), facing)) {
            connections |= 4;
        }
        if (isConnectedPanel(level, pos.relative(right), facing)) {
            connections |= 8;
        }
        return state.setValue(SHAPE, PanelShape.fromConnections(connections));
    }

    private boolean isConnectedPanel(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState neighbor = level.getBlockState(pos);
        return neighbor.is(this)
                && neighbor.hasProperty(FACING)
                && neighbor.getValue(FACING) == facing;
    }

    private void reconcileAround(Level level, BlockPos changedPos, Direction facing) {
        if (level.isClientSide() || applyingConnectionStates) {
            return;
        }

        applyingConnectionStates = true;
        try {
            updateConnectionState(level, changedPos, facing);
            updateConnectionState(level, changedPos.above(), facing);
            updateConnectionState(level, changedPos.below(), facing);
            updateConnectionState(level, changedPos.relative(facing.getClockWise()), facing);
            updateConnectionState(level, changedPos.relative(facing.getCounterClockWise()), facing);
        } finally {
            applyingConnectionStates = false;
        }
    }

    private void updateConnectionState(Level level, BlockPos pos, Direction facing) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(this) || state.getValue(FACING) != facing) {
            return;
        }
        BlockState target = connectedState(state, level, pos);
        if (!target.equals(state)) {
            level.setBlock(pos, target, Block.UPDATE_ALL);
        }
    }

    public enum PanelShape implements StringRepresentable {
        SOLO("solo"),
        END_TOP("end_top"),
        END_BOTTOM("end_bottom"),
        END_LEFT("end_left"),
        END_RIGHT("end_right"),
        TOP_LEFT("top_left"),
        TOP_RIGHT("top_right"),
        BOTTOM_LEFT("bottom_left"),
        BOTTOM_RIGHT("bottom_right"),
        VERTICAL("vertical"),
        HORIZONTAL("horizontal"),
        TOP("top"),
        BOTTOM("bottom"),
        LEFT("left"),
        RIGHT("right"),
        CENTER("center");

        private final String serializedName;

        PanelShape(String serializedName) {
            this.serializedName = serializedName;
        }

        public static PanelShape fromConnections(int connections) {
            return switch (connections) {
                case 0 -> SOLO;
                case 1 -> END_BOTTOM;
                case 2 -> END_TOP;
                case 4 -> END_RIGHT;
                case 8 -> END_LEFT;
                case 3 -> VERTICAL;
                case 5 -> BOTTOM_RIGHT;
                case 6 -> TOP_RIGHT;
                case 9 -> BOTTOM_LEFT;
                case 10 -> TOP_LEFT;
                case 12 -> HORIZONTAL;
                case 7 -> RIGHT;
                case 11 -> LEFT;
                case 13 -> BOTTOM;
                case 14 -> TOP;
                case 15 -> CENTER;
                default -> throw new IllegalArgumentException("Unknown radar panel connection mask: " + connections);
            };
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }
}
