package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.block.RadarControllerBlock;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Проверяет физическую сборку радара и вычисляет её базовую дальность. */
public final class RadarAssemblyValidator {
    private RadarAssemblyValidator() {
    }

    public static RadarStructure validate(ServerLevel level, BlockPos controllerPos) {
        return discover(level, controllerPos).structure();
    }

    /**
     * Finds the complete connected assembly once and returns both its
     * metadata and the exact block positions that can be captured.  Keeping
     * the positions beside the structure prevents assembly from repeating the
     * same plane traversal for texture profiles and the Create contraption.
     */
    public static Discovery discover(ServerLevel level, BlockPos controllerPos) {
        BlockState controllerState = level.getBlockState(controllerPos);
        if (SableRadarIntegration.isAeronauticsLoaded()
                && controllerState.is(ModBlocks.AIRCRAFT_RADAR.get())) {
            Direction facing = controllerState.getValue(RadarControllerBlock.FACING);
            return new Discovery(RadarStructure.aircraft(controllerPos, facing), List.of());
        }

        BlockPos processorPos = controllerPos.above();
        BlockState processorState = level.getBlockState(processorPos);

        if (processorState.is(ModBlocks.OVERVIEW_MODULE.get())) {
            List<BlockPos> positions = findOverviewModules(level, processorPos);
            RadarStructure structure = new RadarStructure(
                    !positions.isEmpty(),
                    controllerPos,
                    controllerPos,
                    processorPos,
                    Direction.NORTH,
                    0,
                    positions.size(),
                    RadarStructureType.OVERVIEW,
                    RadarOrientationState.fixed(
                            RadarStructureType.OVERVIEW,
                            RadarGeometry.yawDegrees(Direction.NORTH),
                            level.getGameTime()));
            return new Discovery(structure, positions);
        }

        if (!isBasicRadarPanel(processorState)) {
            return new Discovery(RadarStructure.invalid(controllerPos), List.of());
        }

        Direction facing = processorState.getValue(RadarPanelBlock.FACING);
        List<BlockPos> positions = findConnectedBasicPanels(level, processorPos, facing);
        RadarStructure structure = new RadarStructure(
                !positions.isEmpty(), controllerPos, controllerPos, processorPos, facing, positions.size(), 0);
        return new Discovery(structure, positions);
    }

    public static int calculateRange(RadarScanMode mode, int phasedArrayPanelCount) {
        if (mode == RadarScanMode.AIRCRAFT) {
            return PowerRadarRadarParameters.aircraftRangeBlocks();
        }
        int groundRange = PowerRadarCeeConstants.radarBaseRangeBlocks(phasedArrayPanelCount);
        return mode == RadarScanMode.SKY
                ? (int) Math.floor(groundRange * PowerRadarCeeConstants.airRangeMultiplier())
                : groundRange;
    }

    public static int calculateOverviewRange(RadarScanMode mode, int overviewModuleCount) {
        int groundRange = PowerRadarCeeConstants.overviewRadarBaseRangeBlocks(overviewModuleCount);
        return mode == RadarScanMode.SKY
                ? (int) Math.floor(groundRange * PowerRadarCeeConstants.airRangeMultiplier())
                : groundRange;
    }

    private static List<BlockPos> findConnectedBasicPanels(
            ServerLevel level,
            BlockPos firstPanelPos,
            Direction facing
    ) {
        Direction horizontalStep = facing.getClockWise();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(firstPanelPos);
        visited.add(firstPanelPos);

        // Обходим только связанную компоненту в плоскости первой панели.
        // Произвольного лимита количества панелей и полного перебора AABB нет.
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (BlockPos next : List.of(
                    current.above(),
                    current.below(),
                    current.relative(horizontalStep),
                    current.relative(horizontalStep.getOpposite()))) {
                if (!visited.contains(next)
                        && level.hasChunkAt(next)
                        && RadarPanelBlock.isInPanelPlane(firstPanelPos, next, facing)
                        && isCompatibleBasicPanel(level, next, facing)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return new ArrayList<>(visited);
    }

    private static List<BlockPos> findOverviewModules(ServerLevel level, BlockPos firstModulePos) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        int maxModules = RadarModuleConstants.maxOverviewModules();
        while (positions.size() < maxModules) {
            BlockPos position = firstModulePos.above(positions.size());
            if (!level.getBlockState(position).is(ModBlocks.OVERVIEW_MODULE.get())) {
                break;
            }
            positions.add(position);
        }
        return List.copyOf(positions);
    }

    private static boolean isCompatibleBasicPanel(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return isBasicRadarPanel(state) && state.getValue(RadarPanelBlock.FACING) == facing;
    }

    private static boolean isBasicRadarPanel(BlockState state) {
        return state.is(ModBlocks.RADAR_PANEL.get()) && state.hasProperty(RadarPanelBlock.FACING);
    }

    public record Discovery(RadarStructure structure, List<BlockPos> capturedPositions) {
        public Discovery {
            capturedPositions = List.copyOf(capturedPositions);
        }
    }
}
