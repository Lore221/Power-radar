package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.network.RadarNetworkKind;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Бортовой радар. Задний блок хранит единственный BlockEntity, а два
 * companion-блока спереди дают установке настоящий размер 3x1 блока.
 *
 * <p>Схема повторяет идею Transformer Core из CEE: установка, вращение и
 * разрушение выполняются как единая конструкция.</p>
 */
public final class AircraftRadarBlock extends RadarControllerBlock {
    public static final MapCodec<AircraftRadarBlock> CODEC = simpleCodec(AircraftRadarBlock::new);
    private static final VoxelShape BLOCK_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final ThreadLocal<Boolean> UPDATING_STRUCTURE = ThreadLocal.withInitial(() -> false);

    public AircraftRadarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RadarScanMode scanMode() {
        return RadarScanMode.AIRCRAFT;
    }

    @Override
    public RadarNetworkKind networkKind() {
        return RadarNetworkKind.AIRCRAFT;
    }

    /** Передняя сторона радара при установке смотрит от игрока. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getPlayer() != null && context.getPlayer().isShiftKeyDown()
                ? context.getHorizontalDirection().getOpposite()
                : context.getHorizontalDirection();
        BlockPos rear = context.getClickedPos();
        if (!canOccupyParts(context.getLevel(), rear, facing, null)) {
            return null;
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            placeParts(level, pos, state.getValue(FACING));
        }
    }

    @Override
    protected void onRemove(
            BlockState oldState,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean movedByPiston
    ) {
        if (!oldState.is(newState.getBlock()) && !isUpdatingStructure()) {
            removeStructure(level, pos, oldState.getValue(FACING), pos);
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PowerRadarCeeBlockLifecycle.removeCreativeConnections(level, pos, player);
        removeStructure(level, pos, state.getValue(FACING), pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return rotateStructure(context.getLevel(), context.getClickedPos());
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return removeWithWrench(context.getLevel(), context.getClickedPos(), context);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return BLOCK_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return BLOCK_SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        // The core is a non-occluding electronics block even though its
        // collision footprint is a full cube. This keeps Sable lighting from
        // treating the rear block as an opaque shadow caster without emitting
        // artificial light.
        return true;
    }

    /**
     * The radar is represented by a three-block model, but the destroy
     * particle event must use a one-block state at the position being broken.
     * Reuse the visible companion state so the particles use the radar texture
     * instead of the invisible controller state.
     */
    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        level.levelEvent(
                player,
                2001,
                pos,
                Block.getId(AircraftRadarPartBlock.particleState(state.getValue(FACING))));
    }

    /** Возвращает задний блок для клика по core или по companion-блоку. */
    @Nullable
    public static BlockPos findCore(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AircraftRadarBlock) {
            return pos;
        }
        if (!(state.getBlock() instanceof AircraftRadarPartBlock)) {
            return null;
        }
        return findCore(level, pos, state.getValue(AircraftRadarPartBlock.FACING));
    }

    /** Возвращает BlockEntity радара даже если клик пришёлся по его краю. */
    @Nullable
    public static BlockEntity findController(Level level, BlockPos pos) {
        BlockPos core = findCore(level, pos);
        return core == null ? null : level.getBlockEntity(core);
    }

    static void removeStructureFromPart(Level level, BlockPos partPos) {
        BlockPos core = findCore(level, partPos);
        if (core != null) {
            removeStructure(level, core, level.getBlockState(core).getValue(FACING), partPos);
        }
    }

    static void removeStructureFromPart(Level level, BlockPos partPos, Direction partFacing) {
        BlockPos core = findCore(level, partPos, partFacing);
        if (core != null) {
            removeStructure(level, core, level.getBlockState(core).getValue(FACING), partPos);
        }
    }

    static InteractionResult rotateStructure(Level level, BlockPos clickedPos) {
        BlockPos core = findCore(level, clickedPos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        BlockState coreState = level.getBlockState(core);
        Direction oldFacing = coreState.getValue(FACING);
        Direction newFacing = oldFacing.getClockWise(Direction.Axis.Y);
        if (!canOccupyParts(level, core, newFacing, core)) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            updateStructureFacing(level, core, oldFacing, newFacing);
            IWrenchable.playRotateSound(level, core);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    static InteractionResult removeWithWrench(Level level, BlockPos clickedPos, UseOnContext context) {
        BlockPos core = findCore(level, clickedPos);
        if (core == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        BlockState coreState = level.getBlockState(core);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, core, coreState, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return InteractionResult.SUCCESS;
        }

        if (player != null && !player.isCreative()) {
            Block.getDrops(
                    coreState,
                    serverLevel,
                    core,
                    level.getBlockEntity(core),
                    player,
                    context.getItemInHand())
                    .forEach(player.getInventory()::placeItemBackInInventory);
        }
        PowerRadarCeeBlockLifecycle.removeCreativeConnections(level, core, player);
        coreState.spawnAfterBreak(serverLevel, core, ItemStack.EMPTY, true);
        removeStructure(level, core, coreState.getValue(FACING), null);
        IWrenchable.playRemoveSound(level, core);
        return InteractionResult.SUCCESS;
    }

    private static void placeParts(Level level, BlockPos core, Direction facing) {
        BlockState partState = ModBlocks.AIRCRAFT_RADAR_PART.get().defaultBlockState()
                .setValue(AircraftRadarPartBlock.FACING, facing);
        // The middle block carries the existing three-block model. The frontmost
        // companion remains invisible and only supplies collision/structure space.
        level.setBlock(core.relative(facing), partState
                .setValue(AircraftRadarPartBlock.MODEL_VISIBLE, true), Block.UPDATE_ALL);
        level.setBlock(core.relative(facing, 2), partState
                .setValue(AircraftRadarPartBlock.MODEL_VISIBLE, false), Block.UPDATE_ALL);
    }

    private static void updateStructureFacing(
            Level level,
            BlockPos core,
            Direction oldFacing,
            Direction newFacing
    ) {
        removeStructure(level, core, oldFacing, core);
        level.setBlock(core, ModBlocks.AIRCRAFT_RADAR.get().defaultBlockState().setValue(FACING, newFacing), Block.UPDATE_ALL);
        placeParts(level, core, newFacing);
    }

    private static boolean canOccupyParts(
            Level level,
            BlockPos core,
            Direction facing,
            @Nullable BlockPos allowedExistingCore
    ) {
        return canOccupy(level, core.relative(facing), allowedExistingCore)
                && canOccupy(level, core.relative(facing, 2), allowedExistingCore);
    }

    private static boolean canOccupy(Level level, BlockPos pos, @Nullable BlockPos allowedExistingCore) {
        if (allowedExistingCore != null
                && level.getBlockState(pos).getBlock() instanceof AircraftRadarPartBlock
                && allowedExistingCore.equals(AircraftRadarBlock.findCore(level, pos))) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced();
    }

    private static boolean isPartPosition(BlockPos core, BlockPos part, Direction facing) {
        return part.equals(core.relative(facing)) || part.equals(core.relative(facing, 2));
    }

    @Nullable
    private static BlockPos findCore(Level level, BlockPos partPos, Direction partFacing) {
        for (int distance = 1; distance <= 2; distance++) {
            BlockPos candidate = partPos.relative(partFacing.getOpposite(), distance);
            BlockState candidateState = level.getBlockState(candidate);
            if (candidateState.getBlock() instanceof AircraftRadarBlock
                    && candidateState.getValue(FACING) == partFacing
                    && isPartPosition(candidate, partPos, partFacing)) {
                return candidate;
            }
        }
        return null;
    }

    private static void removeStructure(
            Level level,
            BlockPos core,
            Direction facing,
            @Nullable BlockPos keep
    ) {
        if (isUpdatingStructure()) {
            return;
        }
        UPDATING_STRUCTURE.set(true);
        try {
            for (BlockPos pos : List.of(
                    core,
                    core.relative(facing),
                    core.relative(facing, 2))) {
                if (keep != null && keep.equals(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof AircraftRadarBlock
                        || state.getBlock() instanceof AircraftRadarPartBlock) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        } finally {
            UPDATING_STRUCTURE.remove();
        }
    }

    private static boolean isUpdatingStructure() {
        return Boolean.TRUE.equals(UPDATING_STRUCTURE.get());
    }
}
