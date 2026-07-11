package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.block.entity.MechanicalSirenBlockEntity;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MechanicalSirenBlock extends HorizontalKineticBlock implements EntityBlock {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0),
            box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0),
            box(3.0, 3.0, 0.5, 13.0, 13.0, 2.0));
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0),
            box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0),
            box(3.0, 3.0, 14.0, 13.0, 13.0, 15.5));
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0),
            box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0),
            box(14.0, 3.0, 3.0, 15.5, 13.0, 13.0));
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0),
            box(2.0, 2.0, 2.0, 14.0, 14.0, 14.0),
            box(0.5, 3.0, 3.0, 2.0, 13.0, 13.0));

    public MechanicalSirenBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(POWER, 0));
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
        super.createBlockStateDefinition(builder);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block block,
            BlockPos fromPos,
            boolean moving
    ) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        updateRedstoneSignal(state, level, pos);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        updateRedstoneSignal(state, level, pos);
    }

    @Override
    public boolean canConnectRedstone(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            @Nullable Direction direction
    ) {
        return true;
    }

    private static void updateRedstoneSignal(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        int signal = level.getBestNeighborSignal(pos);
        if (state.getValue(POWER) != signal) {
            level.setBlock(pos, state.setValue(POWER, signal), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(HORIZONTAL_FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MechanicalSirenBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return (tickerLevel, tickerPos, tickerState, blockEntity) -> {
            if (blockEntity instanceof MechanicalSirenBlockEntity siren) {
                siren.tick();
            }
        };
    }
}
