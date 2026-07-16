package com.limbo2136.powerradar.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.MonitorControllerCeeDevice;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.item.NameCardItem;
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

public final class OnboardComputerBlock extends BaseEntityBlock implements ElectricalDeviceBlock<MonitorControllerCeeDevice> {
    public static final MapCodec<OnboardComputerBlock> CODEC = simpleCodec(OnboardComputerBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public OnboardComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!SableRadarIntegration.canPlaceOnStructure(context.getLevel(), context.getClickedPos())) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, 1);
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ensureNodesExist(level, pos, state);
        PowerRadarCeeIntegration.configureMonitorLoad(level, pos, true, 1);
        super.tick(state, level, pos, random);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(stack.getItem() instanceof NameCardItem)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        if (!(level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide()) {
            if (computer.nameCard().isEmpty()) {
                computer.insertNameCard(stack);
            } else {
                giveNameCardToPlayer(computer, player);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer))
            return InteractionResult.PASS;
        if (!level.isClientSide()) {
            if (computer.nameCard().isEmpty()) return InteractionResult.PASS;
            giveNameCardToPlayer(computer, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    private static void giveNameCardToPlayer(OnboardComputerBlockEntity computer, Player player) {
        ItemStack card = computer.removeNameCard();
        if (!player.addItem(card)) player.drop(card, false);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PowerRadarCeeBlockLifecycle.removeCreativeConnections(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer) {
            computer.clearStructureName();
            if (!computer.nameCard().isEmpty()) Block.popResource(level, pos, computer.removeNameCard());
        }
        super.onRemove(state, level, pos, next, moved);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public SimulatedDeviceType<MonitorControllerCeeDevice> getDevice() { return PowerRadarCeeDeviceTypes.RADAR_MONITOR_CONTROLLER.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return Map.of(0, getNodePosition(level, pos, state, 0), 1, getNodePosition(level, pos, state, 1));
    }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int index) {
        Direction facing = state.getValue(FACING);
        Direction right = RadarDisplayStructureResolver.right(facing);
        Direction rear = facing.getOpposite();
        double side = index == 0 ? 0.56 : -0.56;
        return new Vec3(0.5, 0.5, 0.5).add(right.getStepX() * side, 0, right.getStepZ() * side)
                .add(rear.getStepX() * 0.25, 0, rear.getStepZ() * 0.25);
    }
    @Override public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return Component.translatable(node == 0
                ? "power_radar.cee.node.power_positive"
                : "power_radar.cee.node.power_negative");
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OnboardComputerBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ONBOARD_COMPUTER.get(), OnboardComputerBlockEntity::serverTick);
    }
}
