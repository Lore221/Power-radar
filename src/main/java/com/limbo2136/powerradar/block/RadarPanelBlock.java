package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.compat.create.RadarPanelPlacementHelper;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RadarPanelBlock extends HorizontalDirectionalBlock implements IWrenchable {
    public static final MapCodec<RadarPanelBlock> CODEC = simpleCodec(RadarPanelBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Визуальный профиль задней и боковой сторон, унаследованный от контроллера радара. */
    public static final EnumProperty<ControllerType> CONTROLLER_TYPE =
            EnumProperty.create("controller_type", ControllerType.class);
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new RadarPanelPlacementHelper());

    private static final VoxelShape NORTH_SHAPE =
            Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 13.0);
    private static final VoxelShape SOUTH_SHAPE =
            Block.box(0.0, 0.0, 3.0, 16.0, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE =
            Block.box(0.0, 0.0, 0.0, 13.0, 16.0, 16.0);
    private static final VoxelShape EAST_SHAPE =
            Block.box(3.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    public RadarPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONTROLLER_TYPE, ControllerType.SURFACE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
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
        return this.defaultBlockState()
                .setValue(FACING, facing)
                // Panels are neutral until the controller explicitly assembles
                // the radar.  The assembly action applies the controller
                // profile to the complete cached structure.
                .setValue(CONTROLLER_TYPE, ControllerType.SURFACE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
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
            PlacementOffset offset = helper.getOffset(player, level, state, pos, hitResult);
            offset.placeInWorld(level, blockItem, player, hand, hitResult);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static VoxelShape shapeFor(Direction facing) {
        return switch (facing) {
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            case NORTH, UP, DOWN -> NORTH_SHAPE;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONTROLLER_TYPE);
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        return originalState.setValue(FACING, originalState.getValue(FACING).getClockWise());
    }

    /** Преобразует режим источника в профиль задней/боковой текстуры панели. */
    public static ControllerType fromScanMode(RadarScanMode scanMode) {
        return switch (scanMode) {
            case GROUND -> ControllerType.BASED;
            case SKY -> ControllerType.AIR;
            case SURFACE_SCANNER, AIRCRAFT -> ControllerType.SURFACE;
        };
    }

    /** Обновляет профиль всей панели после установки/загрузки её контроллера. */
    public static void refreshControllerType(Level level, BlockPos controllerPos, ControllerType controllerType) {
        BlockPos firstPanelPos = controllerPos.above();
        if (!level.hasChunkAt(firstPanelPos)) {
            return;
        }
        BlockState firstPanelState = level.getBlockState(firstPanelPos);
        if (!isPanel(firstPanelState)) {
            return;
        }

        Direction facing = firstPanelState.getValue(FACING);
        Direction horizontalStep = facing.getClockWise();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(firstPanelPos);
        visited.add(firstPanelPos);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            BlockState state = level.getBlockState(current);
            if (!isPanel(state) || state.getValue(FACING) != facing) {
                continue;
            }
            if (state.getValue(CONTROLLER_TYPE) != controllerType) {
                level.setBlock(
                        current,
                        state.setValue(CONTROLLER_TYPE, controllerType),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }

            for (BlockPos next : List.of(
                    current.above(),
                    current.below(),
                    current.relative(horizontalStep),
                    current.relative(horizontalStep.getOpposite()))) {
                if (!level.hasChunkAt(next) || visited.contains(next)) {
                    continue;
                }
                BlockState nextState = level.getBlockState(next);
                // Traverse only panels in this radar plane.  Air or a
                // differently-facing block is a gap, not a visited panel, so
                // a connected array can be arbitrarily wide and still gets a
                // complete controller texture profile.
                if (!isInPanelPlane(firstPanelPos, next, facing)
                        || !isPanel(nextState)
                        || nextState.getValue(FACING) != facing) {
                    continue;
                }
                visited.add(next);
                queue.addLast(next);
            }
        }
    }

    /** Применяет профиль к уже найденным панелям без повторного обхода сети. */
    public static void applyControllerType(Level level, Iterable<BlockPos> panelPositions, ControllerType controllerType) {
        for (BlockPos position : panelPositions) {
            if (!level.hasChunkAt(position)) {
                continue;
            }
            BlockState state = level.getBlockState(position);
            if (!isPanel(state) || state.getValue(CONTROLLER_TYPE) == controllerType) {
                continue;
            }
            level.setBlock(
                    position,
                    state.setValue(CONTROLLER_TYPE, controllerType),
                    Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }

    /** Returns whether a position lies in the plane defined by the first panel. */
    public static boolean isInPanelPlane(BlockPos firstPanelPos, BlockPos panelPos, Direction facing) {
        int depth = (panelPos.getX() - firstPanelPos.getX()) * facing.getStepX()
                + (panelPos.getY() - firstPanelPos.getY()) * facing.getStepY()
                + (panelPos.getZ() - firstPanelPos.getZ()) * facing.getStepZ();
        return depth == 0;
    }

    private static boolean isPanel(BlockState state) {
        return state.is(ModBlocks.RADAR_PANEL.get()) && state.hasProperty(FACING);
    }

    public enum ControllerType implements StringRepresentable {
        SURFACE("surface"),
        BASED("based"),
        AIR("air");

        private final String serializedName;

        ControllerType(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }
}
