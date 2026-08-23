package com.limbo2136.powerradar.block;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.block.entity.RadarDisplayBlockEntity;
import com.limbo2136.powerradar.block.entity.AbstractRadarMonitorBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.RadarDisplayCeeDevice;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeContactGeometry;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeTerminalPair;
import com.limbo2136.powerradar.compat.create.RadarDisplayPlacementHelper;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import javax.annotation.Nullable;

public class RadarDisplayBlock extends BaseEntityBlock
        implements IWrenchable, ElectricalDeviceBlock<RadarDisplayCeeDevice> {
    public static final MapCodec<RadarDisplayBlock> CODEC = simpleCodec(RadarDisplayBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final EnumProperty<RadarDisplayFrameShape> FRAME_SHAPE =
            EnumProperty.create("frame_shape", RadarDisplayFrameShape.class);
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new RadarDisplayPlacementHelper());

    private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 3.0, 16.0, 16.0, 14.0);
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 2.0, 16.0, 16.0, 13.0);
    private static final VoxelShape WEST_SHAPE = Block.box(3.0, 0.0, 0.0, 14.0, 16.0, 16.0);
    private static final VoxelShape EAST_SHAPE = Block.box(2.0, 0.0, 0.0, 13.0, 16.0, 16.0);
    private static final VoxelShape NORTH_ROOT_SHAPE = Block.box(0.0, 0.0, 3.0, 16.0, 16.0, 17.0);
    private static final VoxelShape SOUTH_ROOT_SHAPE = Block.box(0.0, 0.0, -1.0, 16.0, 16.0, 13.0);
    private static final VoxelShape WEST_ROOT_SHAPE = Block.box(3.0, 0.0, 0.0, 17.0, 16.0, 16.0);
    private static final VoxelShape EAST_ROOT_SHAPE = Block.box(-1.0, 0.0, 0.0, 13.0, 16.0, 16.0);

    public RadarDisplayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false)
                .setValue(FRAME_SHAPE, RadarDisplayFrameShape.SINGLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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
        scheduleNodeRefresh(level, pos);
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide()
                && !oldState.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity display) {
            display.prepareForDisplayRemoval();
            display.prepareForBlockRemoval();
        }
        super.onRemove(oldState, level, pos, newState, moving);
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
        // Соседские уведомления от массовой смены ACTIVE/FRAME_SHAPE не запускают новую сверку.
        // Состав дисплея пересчитывают только явные placement/removal lifecycle callbacks выше.
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING), isRootDisplay(level, pos));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING), isRootDisplay(level, pos));
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
            RadarDisplayPlacementHelper displayHelper = (RadarDisplayPlacementHelper) helper;
            BlockPos targetPos = displayHelper.findTargetPos(level, state, pos, hitResult).orElse(null);
            if (targetPos == null
                    || !(level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity member)
                    || !member.canJoinAt(targetPos)) {
                return ItemInteractionResult.FAIL;
            }
            PlacementOffset offset = helper.getOffset(player, level, state, pos, hitResult);
            offset.placeInWorld(level, blockItem, player, hand, hitResult);
            if (!level.isClientSide()
                    && level.getBlockEntity(targetPos) instanceof RadarDisplayBlockEntity placed) {
                placed.joinDisplay(member);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (hitResult.getDirection() != state.getValue(FACING)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return handleDisplayInteraction(level, pos, player) == InteractionResult.CONSUME
                ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (hitResult.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        return handleDisplayInteraction(level, pos, player);
    }

    private static InteractionResult handleDisplayInteraction(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            if (level instanceof ServerLevel serverLevel
                    && player instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity display
                    && level.getBlockEntity(display.rootPos()) instanceof RadarDisplayBlockEntity root
                    && root.structureStatus() == RadarDisplayStructureResolver.StructureStatus.ACTIVE) {
                PacketDistributor.sendToPlayer(
                        serverPlayer,
                        AbstractRadarMonitorBlockEntity.createSnapshotPayload(
                                serverLevel, root.getBlockPos())
                );
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.power_radar.monitor_display.inactive"), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    private static VoxelShape shapeFor(Direction facing, boolean root) {
        if (root) {
            return switch (facing) {
                case SOUTH -> SOUTH_ROOT_SHAPE;
                case WEST -> WEST_ROOT_SHAPE;
                case EAST -> EAST_ROOT_SHAPE;
                case NORTH, UP, DOWN -> NORTH_ROOT_SHAPE;
            };
        }
        return switch (facing) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case NORTH, UP, DOWN -> NORTH_SHAPE;
        };
    }

    private static boolean isRootDisplay(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity display && display.isRoot();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE, FRAME_SHAPE);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadarDisplayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> blockEntityType
    ) {
        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.RADAR_DISPLAY.get(),
                RadarDisplayBlockEntity::tick);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ensureNodesExist(level, pos, state);
        if (level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity display) {
            display.markGroupDirty();
        }
        super.tick(state, level, pos, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PowerRadarCeeBlockLifecycle.removeCreativeConnections(level, pos, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public SimulatedDeviceType<RadarDisplayCeeDevice> getDevice() {
        return PowerRadarCeeDeviceTypes.RADAR_DISPLAY.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity display) || !display.isRoot()) {
            return Map.of();
        }
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
        return PowerRadarCeeContactGeometry.radarDisplay(state.getValue(FACING));
    }

    private void scheduleNodeRefresh(Level level, BlockPos pos) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }
}
