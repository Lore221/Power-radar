package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RadarDisplayStructureResolver {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 5;
    static final int CONTROLLER_SCAN_RADIUS = 6;
    private static boolean applyingDisplayStates;

    private RadarDisplayStructureResolver() {
    }

    public static void reconcileAround(Level level, BlockPos changedPos) {
        reconcileAround(level, changedPos, "unspecified");
    }

    public static void reconcileAround(Level level, BlockPos changedPos, String reason) {
        if (level.isClientSide() || applyingDisplayStates) {
            return;
        }

        List<RadarMonitorControllerBlockEntity> controllers = findNearbyControllers(level, changedPos);
        List<RadarDisplayStructure> standaloneDisplays = RadarDisplayStructureScanner.findStandaloneAround(level, changedPos);
        if (controllers.isEmpty()) {
            applyStandalonePanelStates(level, changedPos, standaloneDisplays);
            return;
        }

        Map<RadarMonitorControllerBlockEntity, RadarDisplayStructure> selections = new HashMap<>();
        for (RadarMonitorControllerBlockEntity controller : controllers) {
            findConnectedControllerStructure(standaloneDisplays, controller)
                    .ifPresent(selected -> selections.put(controller, selected));
        }

        Set<RadarMonitorControllerBlockEntity> conflicted = conflictedControllers(selections);
        Set<BlockPos> claimedPanels = new HashSet<>();
        Set<BlockPos> oldPanels = new HashSet<>();
        for (RadarMonitorControllerBlockEntity controller : controllers) {
            oldPanels.addAll(controller.activePanelPositions());
        }

        for (RadarMonitorControllerBlockEntity controller : controllers) {
            RadarDisplayStructure candidate = selections.get(controller);
            if (candidate == null) {
                controller.updateDisplayStructure(null, 0, controller.facing(), StructureStatus.NO_DISPLAY);
                continue;
            }
            if (conflicted.contains(controller)) {
                controller.updateDisplayStructure(candidate.origin(), candidate.size(), candidate.facing(), StructureStatus.INVALID_MULTIPLE_CONTROLLERS);
                continue;
            }
            controller.updateDisplayStructure(candidate.origin(), candidate.size(), candidate.facing(), StructureStatus.ACTIVE);
            claimedPanels.addAll(candidate.positions());
        }

        applyPanelStates(level, changedPos, oldPanels, selections, conflicted, claimedPanels, standaloneDisplays);
    }

    public static DisplayOwnerResult resolveActiveOwner(Level level, BlockPos displayPos) {
        BlockPos firstOwner = null;
        int ownerCount = 0;
        boolean conflict = false;
        for (RadarMonitorControllerBlockEntity controller : findNearbyControllers(level, displayPos)) {
            if (controller.structureStatus() == StructureStatus.INVALID_MULTIPLE_CONTROLLERS
                    && controller.lastStructureContains(displayPos)) {
                conflict = true;
            }
            if (controller.structureStatus() == StructureStatus.ACTIVE && controller.activeContains(displayPos)) {
                if (firstOwner == null) {
                    firstOwner = controller.getBlockPos();
                }
                ownerCount++;
            }
        }
        if (conflict || ownerCount > 1) {
            return new DisplayOwnerResult(DisplayOwnerStatus.CONFLICT, null);
        }
        if (ownerCount == 1) {
            return new DisplayOwnerResult(DisplayOwnerStatus.ACTIVE, firstOwner);
        }
        return new DisplayOwnerResult(DisplayOwnerStatus.INACTIVE, null);
    }

    public static Direction right(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            case WEST -> Direction.SOUTH;
            case UP, DOWN -> Direction.EAST;
        };
    }

    public static BlockPos localOffset(BlockPos origin, Direction right, int u, int v) {
        return origin.relative(right, u).relative(Direction.UP, v);
    }

    public static List<BlockPos> squarePositions(BlockPos origin, Direction facing, int size) {
        return RadarDisplayStructure.squarePositions(origin, facing, size);
    }

    public static boolean squareContains(BlockPos origin, Direction facing, int size, BlockPos pos) {
        if (size <= 0) {
            return false;
        }
        Direction right = right(facing);
        int dx = pos.getX() - origin.getX();
        int dy = pos.getY() - origin.getY();
        int dz = pos.getZ() - origin.getZ();
        int u = dx * right.getStepX() + dz * right.getStepZ();
        int depth = dx * facing.getStepX() + dz * facing.getStepZ();
        return depth == 0 && u >= 0 && u < size && dy >= 0 && dy < size;
    }

    private static Optional<RadarDisplayStructure> findConnectedControllerStructure(
            List<RadarDisplayStructure> structures,
            RadarMonitorControllerBlockEntity controller
    ) {
        BlockPos anchorPos = controller.anchorPos();
        Direction facing = controller.facing();
        for (RadarDisplayStructure structure : structures) {
            if (structure.facing() == facing && structure.contains(anchorPos)) {
                return Optional.of(structure);
            }
        }
        return Optional.empty();
    }

    private static Set<RadarMonitorControllerBlockEntity> conflictedControllers(
            Map<RadarMonitorControllerBlockEntity, RadarDisplayStructure> selections
    ) {
        Set<RadarMonitorControllerBlockEntity> conflicted = new HashSet<>();
        List<Map.Entry<RadarMonitorControllerBlockEntity, RadarDisplayStructure>> entries = List.copyOf(selections.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                if (RadarDisplayStructureScanner.overlaps(entries.get(i).getValue().positions(), entries.get(j).getValue().positions())) {
                    conflicted.add(entries.get(i).getKey());
                    conflicted.add(entries.get(j).getKey());
                }
            }
        }
        return conflicted;
    }

    @Nullable
    private static RadarDisplayStructure structureContaining(List<RadarDisplayStructure> structures, BlockPos pos) {
        for (RadarDisplayStructure candidate : structures) {
            if (candidate.positions().contains(pos)) {
                return candidate;
            }
        }
        return null;
    }

    private static void applyPanelStates(
            Level level,
            BlockPos changedPos,
            Set<BlockPos> oldPanels,
            Map<RadarMonitorControllerBlockEntity, RadarDisplayStructure> selections,
            Set<RadarMonitorControllerBlockEntity> conflicted,
            Set<BlockPos> claimedPanels,
            List<RadarDisplayStructure> standaloneDisplays
    ) {
        HashSet<BlockPos> touched = new HashSet<>(oldPanels);
        for (RadarDisplayStructure candidate : selections.values()) {
            touched.addAll(candidate.positions());
        }
        for (RadarDisplayStructure candidate : standaloneDisplays) {
            touched.addAll(candidate.positions());
        }
        RadarDisplayStructureScanner.addNearbyDisplays(level, changedPos, touched);
        applyingDisplayStates = true;
        try {
            for (BlockPos pos : touched) {
                if (!isLoaded(level, pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.is(ModBlocks.RADAR_DISPLAY.get())) {
                    continue;
                }
                BlockState target = inactiveState(state);
                if (claimedPanels.contains(pos)) {
                    for (Map.Entry<RadarMonitorControllerBlockEntity, RadarDisplayStructure> entry : selections.entrySet()) {
                        if (conflicted.contains(entry.getKey())) {
                            continue;
                        }
                        RadarDisplayStructure candidate = entry.getValue();
                        if (candidate.positions().contains(pos)) {
                            target = activeState(state, candidate, pos);
                            break;
                        }
                    }
                } else {
                    RadarDisplayStructure standalone = structureContaining(standaloneDisplays, pos);
                    if (standalone != null) {
                        target = shapedInactiveState(state, standalone, pos);
                    }
                }
                setDisplayStateIfChanged(level, pos, target);
            }
        } finally {
            applyingDisplayStates = false;
        }
    }

    private static void applyStandalonePanelStates(Level level, BlockPos changedPos, List<RadarDisplayStructure> standaloneDisplays) {
        HashSet<BlockPos> touched = new HashSet<>();
        for (RadarDisplayStructure candidate : standaloneDisplays) {
            touched.addAll(candidate.positions());
        }
        RadarDisplayStructureScanner.addNearbyDisplays(level, changedPos, touched);
        applyingDisplayStates = true;
        try {
            for (BlockPos pos : touched) {
                if (!isLoaded(level, pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.is(ModBlocks.RADAR_DISPLAY.get())) {
                    continue;
                }
                RadarDisplayStructure candidate = structureContaining(standaloneDisplays, pos);
                BlockState target = candidate == null
                        ? inactiveState(state)
                        : shapedInactiveState(state, candidate, pos);
                setDisplayStateIfChanged(level, pos, target);
            }
        } finally {
            applyingDisplayStates = false;
        }
    }

    private static BlockState inactiveState(BlockState state) {
        return state
                .setValue(RadarDisplayBlock.ACTIVE, false)
                .setValue(RadarDisplayBlock.FRAME_SHAPE, RadarDisplayFrameShape.SINGLE);
    }

    private static BlockState shapedInactiveState(BlockState state, RadarDisplayStructure candidate, BlockPos pos) {
        return state
                .setValue(RadarDisplayBlock.ACTIVE, false)
                .setValue(RadarDisplayBlock.FRAME_SHAPE, candidate.frameShape(pos));
    }

    private static BlockState activeState(BlockState state, RadarDisplayStructure candidate, BlockPos pos) {
        return state
                .setValue(RadarDisplayBlock.ACTIVE, true)
                .setValue(RadarDisplayBlock.FRAME_SHAPE, candidate.frameShape(pos));
    }

    private static List<RadarMonitorControllerBlockEntity> findNearbyControllers(Level level, BlockPos center) {
        ArrayList<RadarMonitorControllerBlockEntity> controllers = new ArrayList<>();
        for (int x = -CONTROLLER_SCAN_RADIUS; x <= CONTROLLER_SCAN_RADIUS; x++) {
            for (int y = -MAX_SIZE; y <= MAX_SIZE; y++) {
                for (int z = -CONTROLLER_SCAN_RADIUS; z <= CONTROLLER_SCAN_RADIUS; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!isLoaded(level, pos)) {
                        continue;
                    }
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity instanceof RadarMonitorControllerBlockEntity controller) {
                        controllers.add(controller);
                    }
                }
            }
        }
        controllers.sort(Comparator.comparing(RadarMonitorControllerBlockEntity::getBlockPos, RadarDisplayStructureResolver::compareBlockPos));
        return controllers;
    }

    private static boolean setDisplayStateIfChanged(Level level, BlockPos pos, BlockState target) {
        if (level.isClientSide() || !isLoaded(level, pos)) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (current.equals(target)) {
            return false;
        }
        return level.setBlock(pos, target, 3);
    }

    private static boolean isLoaded(Level level, BlockPos pos) {
        return level.isLoaded(pos);
    }

    private static int compareBlockPos(BlockPos first, BlockPos second) {
        int y = Integer.compare(second.getY(), first.getY());
        if (y != 0) {
            return y;
        }
        int x = Integer.compare(second.getX(), first.getX());
        if (x != 0) {
            return x;
        }
        return Integer.compare(second.getZ(), first.getZ());
    }

    public enum StructureStatus {
        NO_DISPLAY,
        ACTIVE,
        INVALID_MULTIPLE_CONTROLLERS
    }

    public enum DisplayOwnerStatus {
        ACTIVE,
        INACTIVE,
        CONFLICT
    }

    public record DisplayOwnerResult(DisplayOwnerStatus status, BlockPos controllerPos) {
    }

}
