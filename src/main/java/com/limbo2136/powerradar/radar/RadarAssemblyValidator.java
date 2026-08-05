package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.ArrayDeque;
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
        BlockPos processorPos = controllerPos.above();
        BlockState processorState = level.getBlockState(processorPos);

        if (processorState.is(ModBlocks.OVERVIEW_MODULE.get())) {
            int overviewModuleCount = countOverviewModules(level, processorPos);
            return new RadarStructure(
                    true,
                    controllerPos,
                    controllerPos,
                    processorPos,
                    Direction.NORTH,
                    0,
                    overviewModuleCount,
                    RadarStructureType.OVERVIEW,
                    RadarOrientationState.fixed(
                            RadarStructureType.OVERVIEW,
                            RadarGeometry.yawDegrees(Direction.NORTH),
                            level.getGameTime()));
        }

        if (!isBasicRadarPanel(processorState)) {
            return RadarStructure.invalid(controllerPos);
        }

        Direction facing = processorState.getValue(RadarPanelBlock.FACING);
        int panelCount = countConnectedBasicPanels(level, processorPos, facing);
        return new RadarStructure(
                panelCount > 0, controllerPos, controllerPos, processorPos, facing, panelCount, 0);
    }

    public static int calculateRange(RadarScanMode mode, int phasedArrayPanelCount) {
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

    private static int countConnectedBasicPanels(ServerLevel level, BlockPos firstPanelPos, Direction facing) {
        Direction horizontalStep = facing.getClockWise();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(firstPanelPos);
        visited.add(firstPanelPos);

        int maxPanels = PowerRadarCeeConstants.maxRadarPanels();
        while (!queue.isEmpty() && visited.size() < maxPanels) {
            BlockPos current = queue.removeFirst();
            for (BlockPos next : List.of(
                    current.above(),
                    current.below(),
                    current.relative(horizontalStep),
                    current.relative(horizontalStep.getOpposite()))) {
                if (visited.size() >= maxPanels) {
                    break;
                }
                if (!visited.contains(next)
                        && isInDirectPanelPlane(firstPanelPos, next, facing)
                        && isCompatibleBasicPanel(level, next, facing)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return visited.size();
    }

    private static int countOverviewModules(ServerLevel level, BlockPos firstModulePos) {
        int count = 0;
        int maxModules = RadarModuleConstants.maxOverviewModules();
        while (count < maxModules
                && level.getBlockState(firstModulePos.above(count)).is(ModBlocks.OVERVIEW_MODULE.get())) {
            count++;
        }
        return count;
    }

    private static boolean isInDirectPanelPlane(BlockPos firstPanelPos, BlockPos panelPos, Direction facing) {
        int depth = (panelPos.getX() - firstPanelPos.getX()) * facing.getStepX()
                + (panelPos.getY() - firstPanelPos.getY()) * facing.getStepY()
                + (panelPos.getZ() - firstPanelPos.getZ()) * facing.getStepZ();
        return depth == 0;
    }

    private static boolean isCompatibleBasicPanel(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return isBasicRadarPanel(state) && state.getValue(RadarPanelBlock.FACING) == facing;
    }

    private static boolean isBasicRadarPanel(BlockState state) {
        return state.is(ModBlocks.RADAR_PANEL.get()) && state.hasProperty(RadarPanelBlock.FACING);
    }
}
