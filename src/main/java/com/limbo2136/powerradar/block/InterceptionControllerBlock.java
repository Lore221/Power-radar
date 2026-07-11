package com.limbo2136.powerradar.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.InterceptionControllerCeeDevice;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
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

public class InterceptionControllerBlock extends BaseEntityBlock
        implements ElectricalDeviceBlock<InterceptionControllerCeeDevice> {
    public static final MapCodec<InterceptionControllerBlock> CODEC =
            simpleCodec(InterceptionControllerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public InterceptionControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
        if (!level.isClientSide()) {
            for (Direction direction : Direction.values()) {
                if (level.getBlockEntity(pos.relative(direction)) instanceof RadarLinkBlockEntity link) {
                    link.reconcileFacingEndpoint(null);
                }
            }
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
        return level.getBlockEntity(pos) instanceof InterceptionControllerBlockEntity controller
                && controller.readyToFire() ? 15 : 0;
    }

    @Override
    public SimulatedDeviceType<InterceptionControllerCeeDevice> getDevice() {
        return PowerRadarCeeDeviceTypes.INTERCEPTION_CONTROLLER.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(
                0, getNodePosition(level, pos, state, 0),
                1, getNodePosition(level, pos, state, 1));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int index) {
        Direction facing = state.getValue(FACING);
        Direction rear = facing.getOpposite();
        Direction right = facing.getAxis() == Direction.Axis.Y ? Direction.EAST : facing.getClockWise();
        Direction up = switch (facing) {
            case UP -> Direction.SOUTH;
            case DOWN -> Direction.NORTH;
            default -> Direction.UP;
        };
        double horizontalOffset = index == 0 ? -0.24 : 0.24;
        return new Vec3(0.5, 0.5, 0.5)
                .add(rear.getStepX() * 0.56, rear.getStepY() * 0.56, rear.getStepZ() * 0.56)
                .add(right.getStepX() * horizontalOffset, right.getStepY() * horizontalOffset,
                        right.getStepZ() * horizontalOffset)
                .add(up.getStepX() * 0.22, up.getStepY() * 0.22, up.getStepZ() * 0.22);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable(node == 0
                ? "power_radar.cee.node.power_positive"
                : "power_radar.cee.node.power_negative");
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InterceptionControllerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.INTERCEPTION_CONTROLLER.get(),
                        InterceptionControllerBlockEntity::serverTick);
    }
}
