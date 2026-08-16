package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.ElectricalDeviceBlock;
import com.limbo2136.powerradar.compat.electroenergetics.LogicDockCeeDevice;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeContactGeometry;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeDeviceTypes;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeTerminalPair;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;

public class LogicDockBlock extends BaseEntityBlock
        implements IWrenchable, ElectricalDeviceBlock<LogicDockCeeDevice> {
    public static final MapCodec<LogicDockBlock> CODEC = simpleCodec(LogicDockBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    // Кубы повторяют корпус из models/block/logic_dock/model.json.
    // Боковые электрические контакты остаются визуальными и в физическую форму не входят.
    private static final ModelBox[] MODEL_BOXES = {
            new ModelBox(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
            new ModelBox(0.0, 4.0, 13.0, 16.0, 16.0, 16.0),
            new ModelBox(0.0, 4.0, 3.0, 3.0, 6.0, 10.0),
            new ModelBox(13.0, 4.0, 3.0, 16.0, 6.0, 10.0),
            new ModelBox(8.67, 4.0, 3.0, 11.67, 6.0, 10.0),
            new ModelBox(4.34, 4.0, 3.0, 7.34, 6.0, 10.0),
            new ModelBox(0.0, 4.0, 10.0, 16.0, 6.0, 13.0),
            new ModelBox(0.0, 4.0, 0.0, 16.0, 6.0, 3.0)
    };
    private static final VoxelShape NORTH_SHAPE = buildShape(Direction.NORTH);
    private static final VoxelShape EAST_SHAPE = buildShape(Direction.EAST);
    private static final VoxelShape SOUTH_SHAPE = buildShape(Direction.SOUTH);
    private static final VoxelShape WEST_SHAPE = buildShape(Direction.WEST);

    public LogicDockBlock(Properties properties) {
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
                || !(level.getBlockEntity(pos) instanceof LogicDockBlockEntity dock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide() && dock.insertCard(card.kind(), stack, player)) {
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
        if (level.getBlockEntity(pos) instanceof LogicDockBlockEntity dock) {
            if (!level.isClientSide()) {
                dock.extractCard(player, slotFromHit(state, hitResult));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    private static int slotFromHit(BlockState state, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return -1;
        }
        // Модельные позиции справа налево соответствуют ordinal Kind и NBT-ключам Card0..Card2.
        Direction right = state.getValue(FACING).getClockWise();
        double offsetX = hit.getLocation().x - (hit.getBlockPos().getX() + 0.5D);
        double offsetZ = hit.getLocation().z - (hit.getBlockPos().getZ() + 0.5D);
        double localX = 0.5D + offsetX * right.getStepX() + offsetZ * right.getStepZ();
        return localX >= 2.0D / 3.0D ? 0 : localX >= 1.0D / 3.0D ? 1 : 2;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LogicDockBlockEntity dock) {
            dock.invalidateConnectedNetworks();
            dock.dropCards();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LogicDockBlockEntity(pos, state);
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
                        ModBlockEntities.LOGIC_DOCK.get(),
                        LogicDockBlockEntity::serverTick);
    }

    @Override
    public SimulatedDeviceType<LogicDockCeeDevice> getDevice() {
        return PowerRadarCeeDeviceTypes.LOGIC_DOCK.get();
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
        return PowerRadarCeeContactGeometry.logicDock(state.getValue(FACING));
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    // Поворачивает модельные координаты вокруг центра блока так же, как blockstate.
    private static VoxelShape buildShape(Direction facing) {
        VoxelShape shape = Shapes.empty();
        for (ModelBox box : MODEL_BOXES) {
            ModelBox rotated = box.rotateTo(facing);
            shape = Shapes.or(shape, Block.box(
                    rotated.minX,
                    rotated.minY,
                    rotated.minZ,
                    rotated.maxX,
                    rotated.maxY,
                    rotated.maxZ));
        }
        return shape.optimize();
    }

    private record ModelBox(
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ
    ) {
        private ModelBox rotateTo(Direction facing) {
            return switch (facing) {
                case EAST -> new ModelBox(
                        16.0 - this.maxZ, this.minY, this.minX,
                        16.0 - this.minZ, this.maxY, this.maxX);
                case SOUTH -> new ModelBox(
                        16.0 - this.maxX, this.minY, 16.0 - this.maxZ,
                        16.0 - this.minX, this.maxY, 16.0 - this.minZ);
                case WEST -> new ModelBox(
                        this.minZ, this.minY, 16.0 - this.maxX,
                        this.maxZ, this.maxY, 16.0 - this.minX);
                default -> this;
            };
        }
    }
}
