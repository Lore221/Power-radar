package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeBlockLifecycle;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Physical companion of the rear Aircraft Radar block. */
public final class AircraftRadarPartBlock extends Block implements IWrenchable {
    public static final MapCodec<AircraftRadarPartBlock> CODEC =
            simpleCodec(AircraftRadarPartBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty MODEL_VISIBLE = BooleanProperty.create("model_visible");
    private static final VoxelShape FULL_BLOCK_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    public AircraftRadarPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MODEL_VISIBLE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Invisible companions still need a model render shape so Minecraft
        // can draw the breaking overlay when that physical block is mined.
        // AircraftRadarBakedModel suppresses their normal quads on the client.
        return RenderShape.MODEL;
    }

    static BlockState particleState(Direction facing) {
        return ModBlocks.AIRCRAFT_RADAR_PART.get().defaultBlockState()
                .setValue(FACING, facing)
                .setValue(MODEL_VISIBLE, true);
    }

    @Override
    protected void spawnDestroyParticles(Level level, Player player, BlockPos pos, BlockState state) {
        level.levelEvent(player, 2001, pos, Block.getId(particleState(state.getValue(FACING))));
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos core = AircraftRadarBlock.findCore(level, pos);
        if (core != null) {
            PowerRadarCeeBlockLifecycle.removeCreativeConnections(level, core, player);
            AircraftRadarBlock.removeStructureFromPart(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(
            BlockState oldState,
            Level level,
            BlockPos pos,
            BlockState newState,
        boolean movedByPiston
    ) {
        if (!oldState.is(newState.getBlock())) {
            AircraftRadarBlock.removeStructureFromPart(level, pos, oldState.getValue(FACING));
        }
        super.onRemove(oldState, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return AircraftRadarBlock.rotateStructure(context.getLevel(), context.getClickedPos());
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return AircraftRadarBlock.removeWithWrench(context.getLevel(), context.getClickedPos(), context);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODEL_VISIBLE);
    }
}
