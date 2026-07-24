package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.block.entity.ComputingBlockEntity;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.compat.electroenergetics.ComputingBlockCeeDevice;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeTerminalPair;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

public class ComputingBlock extends BaseEntityBlock implements ElectricalDeviceBlock<ComputingBlockCeeDevice> {
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            net.minecraft.world.InteractionHand hand,
            BlockHitResult hitResult
    ) {
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
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
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
        // Три визуальные секции соответствуют ordinal Kind и устойчивым NBT-ключам Card0..Card2.
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide()
                ? null
                : createTickerHelper(
                        type,
                        ModBlockEntities.COMPUTING_BLOCK.get(),
                        ComputingBlockEntity::serverTick);
    }

    @Override
    public SimulatedDeviceType<ComputingBlockCeeDevice> getDevice() {
        return PowerRadarCeeDeviceTypes.COMPUTING_BLOCK.get();
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
        Vec3 center = new Vec3(0.5, 5.0 / 16.0, 0.5)
                .add(rear.getStepX() * 9.0 / 16.0, 0, rear.getStepZ() * 9.0 / 16.0);
        return new PowerRadarCeeTerminalPair(
                center.add(right.getStepX() * -2.0 / 16.0, 0, right.getStepZ() * -2.0 / 16.0),
                center.add(right.getStepX() * 4.0 / 16.0, 0, right.getStepZ() * 4.0 / 16.0));
    }
}
