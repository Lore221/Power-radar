package com.limbo2136.powerradar.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.block.entity.TargetControllerBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.TargetControllerCeeDevice;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.Vec3;

public class TargetControllerBlock extends BaseEntityBlock implements ElectricalDeviceBlock<TargetControllerCeeDevice> {
    public static final MapCodec<TargetControllerBlock> CODEC = simpleCodec(TargetControllerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public TargetControllerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        scheduleNodeRefresh(level, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ensureNodesExist(level, pos, state);
        super.tick(state, level, pos, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PowerRadarCeeBlockLifecycle.removeCreativeConnections(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireSignal(level, pos);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return fireSignal(level, pos);
    }

    private static int fireSignal(BlockGetter level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof TargetControllerBlockEntity controller && controller.readyToFire() ? 15 : 0;
    }

    @Override
    public SimulatedDeviceType<TargetControllerCeeDevice> getDevice() {
        return PowerRadarCeeDeviceTypes.TARGET_CONTROLLER.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(
                0, getNodePosition(level, pos, state, 0),
                1, getNodePosition(level, pos, state, 1)
        );
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int index) {
        Direction facing = state.getValue(FACING);
        Direction rear = facing.getOpposite();
        Direction right = rightOf(facing);
        Direction up = upOf(facing);
        return switch (index) {
            case 0 -> facePoint(rear, right, up, -0.24, -0.22);
            case 1 -> facePoint(rear, right, up, 0.24, -0.22);
            default -> Vec3.atCenterOf(pos);
        };
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable(switch (node) {
            case 0 -> "power_radar.cee.node.power_positive";
            default -> "power_radar.cee.node.power_negative";
        });
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TargetControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, ModBlockEntities.TARGET_CONTROLLER.get(), TargetControllerBlockEntity::serverTick);
    }

    private static Vec3 facePoint(Direction face, Direction horizontal, Direction vertical, double horizontalOffset, double verticalOffset) {
        return new Vec3(0.5, 0.5, 0.5)
                .add(face.getStepX() * 0.56, face.getStepY() * 0.56, face.getStepZ() * 0.56)
                .add(horizontal.getStepX() * horizontalOffset, horizontal.getStepY() * horizontalOffset, horizontal.getStepZ() * horizontalOffset)
                .add(vertical.getStepX() * verticalOffset, vertical.getStepY() * verticalOffset, vertical.getStepZ() * verticalOffset);
    }

    private static Direction rightOf(Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return Direction.EAST;
        }
        return facing.getClockWise();
    }

    private static Direction upOf(Direction facing) {
        return switch (facing) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
    }

    private void scheduleNodeRefresh(Level level, BlockPos pos) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }
}
