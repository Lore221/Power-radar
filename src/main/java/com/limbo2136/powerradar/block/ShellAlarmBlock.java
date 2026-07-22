package com.limbo2136.powerradar.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeTerminalPair;
import com.limbo2136.powerradar.compat.electroenergetics.ShellAlarmCeeDevice;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;

public class ShellAlarmBlock extends BaseEntityBlock implements ElectricalDeviceBlock<ShellAlarmCeeDevice> {
    public static final MapCodec<ShellAlarmBlock> CODEC = simpleCodec(ShellAlarmBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ShellAlarmBlock(Properties properties) {
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
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
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return signal(level, pos);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return signal(level, pos);
    }

    private static int signal(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ShellAlarmBlockEntity alarm && alarm.alarmActive() ? 15 : 0;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ShellAlarmBlockEntity alarm) {
            alarm.deactivateRadarStructureEntity();
            alarm.destroyNetworkMembership();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
    public SimulatedDeviceType<ShellAlarmCeeDevice> getDevice() {
        return PowerRadarCeeDeviceTypes.SHELL_ALARM.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return terminals(state).positions();
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int index) {
        return terminals(state).position(index);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return terminals(state).label(node);
    }

    private static PowerRadarCeeTerminalPair terminals(BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction rear = facing.getOpposite();
        Direction right = facing.getClockWise();
        Vec3 center = new Vec3(0.5, 0.68, 0.5)
                .add(rear.getStepX() * 0.56, 0.0, rear.getStepZ() * 0.56);
        return new PowerRadarCeeTerminalPair(
                center.add(right.getStepX() * -0.24, 0.0, right.getStepZ() * -0.24),
                center.add(right.getStepX() * 0.24, 0.0, right.getStepZ() * 0.24));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShellAlarmBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.SHELL_ALARM.get(), ShellAlarmBlockEntity::serverTick);
    }
}
