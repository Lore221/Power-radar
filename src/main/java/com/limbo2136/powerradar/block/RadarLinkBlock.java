package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RadarLinkBlock extends BaseEntityBlock {
    public static final MapCodec<RadarLinkBlock> CODEC = simpleCodec(RadarLinkBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape NORTH_SHAPE = Block.box(1.0, 1.0, 0.0, 15.0, 16.0, 5.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(1.0, 1.0, 11.0, 15.0, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.box(0.0, 1.0, 1.0, 5.0, 16.0, 15.0);
    private static final VoxelShape EAST_SHAPE = Block.box(11.0, 1.0, 1.0, 16.0, 16.0, 15.0);
    private static final VoxelShape UP_SHAPE = Block.box(1.0, 11.0, 1.0, 15.0, 16.0, 15.0);
    private static final VoxelShape DOWN_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 5.0, 15.0);

    public RadarLinkBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (!level.isClientSide() && fromPos.equals(pos.relative(state.getValue(FACING)))
                && level.getBlockEntity(pos) instanceof RadarLinkBlockEntity link) {
            link.reconcileFacingEndpoint(null);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide()
                && oldState.is(state.getBlock())
                && oldState.hasProperty(FACING)
                && oldState.getValue(FACING) != state.getValue(FACING)
                && level.getBlockEntity(pos) instanceof RadarLinkBlockEntity link) {
            link.reconcileFacingEndpoint(null);
        }
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!oldState.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RadarLinkBlockEntity link) {
            link.destroyNetworkMembership();
        }
        super.onRemove(oldState, level, pos, newState, moving);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadarLinkBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide()
                ? null
                : createTickerHelper(blockEntityType, ModBlockEntities.RADAR_LINK.get(), RadarLinkBlockEntity::serverTick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
            case NORTH -> NORTH_SHAPE;
        };
    }
}
