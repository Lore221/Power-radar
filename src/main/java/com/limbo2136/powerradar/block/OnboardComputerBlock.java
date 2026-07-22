package com.limbo2136.powerradar.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.MonitorControllerCeeDevice;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeTerminalPair;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.item.NameCardItem;
import com.limbo2136.powerradar.onboard.OnboardModuleColumn;
import com.limbo2136.powerradar.onboard.OnboardModuleSlot;
import com.limbo2136.powerradar.onboard.OnboardModuleType;
import com.limbo2136.powerradar.onboard.OnboardPanelGeometry;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OnboardComputerBlock extends BaseEntityBlock
        implements IWrenchable, ElectricalDeviceBlock<MonitorControllerCeeDevice> {
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
        PowerRadarCeeIntegration.configureOnboardComputerLoad(level, pos);
        super.tick(state, level, pos, random);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (OnboardModuleType.accepts(stack)) {
            if (!(level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer)) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            OnboardModuleSlot slot = OnboardPanelGeometry.slotAt(pos, state.getValue(FACING), hit);
            if (slot == null || !computer.module(slot).isEmpty()) {
                return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide()
                    && computer.insertModule(slot, stack, !player.getAbilities().instabuild)) {
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.75F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
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

    @Override public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer)) {
            return IWrenchable.super.onWrenched(state, context);
        }
        BlockHitResult hit = new BlockHitResult(
                context.getClickLocation(), context.getClickedFace(), pos, context.isInside());
        OnboardModuleColumn column = OnboardPanelGeometry.columnAt(pos, state.getValue(FACING), hit);
        if (column == null
                || (!computer.hasAssembledModule(column) && !computer.canAssembleModule(column))) {
            return IWrenchable.super.onWrenched(state, context);
        }
        if (!level.isClientSide() && computer.toggleCombinedModule(column)) {
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer)) {
            return IWrenchable.super.onSneakWrenched(state, context);
        }
        BlockHitResult hit = new BlockHitResult(
                context.getClickLocation(), context.getClickedFace(), pos, context.isInside());
        OnboardModuleColumn column = OnboardPanelGeometry.columnAt(pos, state.getValue(FACING), hit);
        if (column != null && computer.hasAssembledModule(column)) {
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        OnboardModuleSlot slot = OnboardPanelGeometry.slotAt(pos, state.getValue(FACING), hit);
        if (slot == null || computer.module(slot).isEmpty()) {
            return IWrenchable.super.onSneakWrenched(state, context);
        }
        if (!level.isClientSide()) {
            ItemStack removed = computer.removeModule(slot);
            if (!removed.isEmpty() && !player.addItem(removed)) {
                player.drop(removed, false);
            }
            IWrenchable.playRemoveSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PowerRadarCeeBlockLifecycle.removeCreativeConnections(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer) {
            computer.destroyOwnedNetworks();
            computer.clearStructureName();
            if (!computer.nameCard().isEmpty()) Block.popResource(level, pos, computer.removeNameCard());
            for (ItemStack module : computer.removeAllModules()) {
                Block.popResource(level, pos, module);
            }
        }
        super.onRemove(state, level, pos, next, moved);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        int moduleMask = level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer
                ? computer.installedModuleMask()
                : 0;
        return OnboardPanelGeometry.selectionShape(state.getValue(FACING), moduleMask);
    }
    @Override protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return Shapes.block();
    }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return alarmSignal(level, pos);
    }
    @Override protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return alarmSignal(level, pos);
    }
    private static int alarmSignal(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer && computer.alarmActive()
                ? 15
                : 0;
    }
    @Override public SimulatedDeviceType<MonitorControllerCeeDevice> getDevice() { return PowerRadarCeeDeviceTypes.RADAR_MONITOR_CONTROLLER.get(); }
    @Override public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        return terminals(state).positions();
    }
    @Override public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int index) {
        return terminals(state).position(index);
    }
    private static PowerRadarCeeTerminalPair terminals(BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction right = RadarDisplayStructureResolver.right(facing);
        Direction rear = facing.getOpposite();
        Vec3 center = new Vec3(0.5, 0.5, 0.5)
                .add(rear.getStepX() * 0.25, 0, rear.getStepZ() * 0.25);
        return new PowerRadarCeeTerminalPair(
                center.add(right.getStepX() * 0.56, 0, right.getStepZ() * 0.56),
                center.add(right.getStepX() * -0.56, 0, right.getStepZ() * -0.56));
    }
    @Override public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int node) {
        return terminals(state).label(node);
    }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new OnboardComputerBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.ONBOARD_COMPUTER.get(), OnboardComputerBlockEntity::serverTick);
    }
}
