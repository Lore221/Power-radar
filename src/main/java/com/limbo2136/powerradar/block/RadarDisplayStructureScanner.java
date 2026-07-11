package com.limbo2136.powerradar.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

final class RadarDisplayStructureScanner {
    private RadarDisplayStructureScanner() {
    }

    static List<RadarDisplayStructure> findStandaloneAround(Level level, BlockPos changedPos) {
        Map<StandaloneCandidateKey, RadarDisplayStructure> candidates = new HashMap<>();
        for (BlockPos pos : nearbyDisplayPositions(level, changedPos)) {
            BlockState state = level.getBlockState(pos);
            if (!state.hasProperty(RadarDisplayBlock.FACING)) {
                continue;
            }
            Direction facing = state.getValue(RadarDisplayBlock.FACING);
            for (RadarDisplayStructure candidate : enumerateCandidates(level, pos, facing)) {
                candidates.putIfAbsent(
                        new StandaloneCandidateKey(candidate.origin(), candidate.size(), candidate.facing()),
                        candidate);
            }
        }

        ArrayList<RadarDisplayStructure> ordered = sortByAssemblyPriority(candidates.values());
        ArrayList<RadarDisplayStructure> persisted = new ArrayList<>();
        for (RadarDisplayStructure candidate : ordered) {
            if (candidate.size() > 1 && matchesStoredShape(level, candidate)) {
                persisted.add(candidate);
            }
        }

        ArrayList<RadarDisplayStructure> selected = new ArrayList<>();
        HashSet<RadarDisplayStructure> consumedPersisted = new HashSet<>();
        for (RadarDisplayStructure current : persisted) {
            if (consumedPersisted.contains(current)) {
                continue;
            }
            RadarDisplayStructure selectedStructure = bestExpansion(current, ordered, persisted);
            selected.add(selectedStructure);
            for (RadarDisplayStructure existing : persisted) {
                if (selectedStructure.positions().containsAll(existing.positions())) {
                    consumedPersisted.add(existing);
                }
            }
        }

        HashSet<BlockPos> claimed = new HashSet<>();
        for (RadarDisplayStructure structure : selected) {
            claimed.addAll(structure.positions());
        }
        for (RadarDisplayStructure candidate : ordered) {
            if (overlaps(candidate.positions(), claimed)) {
                continue;
            }
            selected.add(candidate);
            claimed.addAll(candidate.positions());
        }
        return List.copyOf(selected);
    }

    private static ArrayList<RadarDisplayStructure> sortByAssemblyPriority(java.util.Collection<RadarDisplayStructure> candidates) {
        ArrayList<RadarDisplayStructure> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator
                .comparingInt(RadarDisplayStructure::size)
                .reversed()
                .thenComparing(RadarDisplayStructure::origin, RadarDisplayStructureScanner::compareBlockPos)
                .thenComparing(candidate -> candidate.facing().ordinal()));
        return ordered;
    }

    private static RadarDisplayStructure bestExpansion(
            RadarDisplayStructure current,
            List<RadarDisplayStructure> candidates,
            List<RadarDisplayStructure> persisted
    ) {
        for (RadarDisplayStructure candidate : candidates) {
            if (candidate.size() <= current.size() || !candidate.positions().containsAll(current.positions())) {
                continue;
            }
            if (partiallyConsumesPersistedStructure(candidate, persisted)) {
                continue;
            }
            return candidate;
        }
        return current;
    }

    private static boolean partiallyConsumesPersistedStructure(
            RadarDisplayStructure candidate,
            List<RadarDisplayStructure> persisted
    ) {
        for (RadarDisplayStructure existing : persisted) {
            if (!overlaps(candidate.positions(), existing.positions())) {
                continue;
            }
            if (!candidate.positions().containsAll(existing.positions())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStoredShape(Level level, RadarDisplayStructure structure) {
        for (BlockPos pos : structure.positions()) {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!RadarDisplayStructure.isMatchingDisplay(state, structure.facing())
                    || !state.hasProperty(RadarDisplayBlock.FRAME_SHAPE)
                    || state.getValue(RadarDisplayBlock.FRAME_SHAPE) != structure.frameShape(pos)) {
                return false;
            }
        }
        return true;
    }

    static List<BlockPos> nearbyDisplayPositions(Level level, BlockPos changedPos) {
        ArrayList<BlockPos> positions = new ArrayList<>();
        addNearbyDisplays(level, changedPos, positions);
        return List.copyOf(positions);
    }

    static void addNearbyDisplays(Level level, BlockPos changedPos, java.util.Collection<BlockPos> output) {
        for (int x = -RadarDisplayStructureResolver.CONTROLLER_SCAN_RADIUS; x <= RadarDisplayStructureResolver.CONTROLLER_SCAN_RADIUS; x++) {
            for (int y = -RadarDisplayStructureResolver.MAX_SIZE; y <= RadarDisplayStructureResolver.MAX_SIZE; y++) {
                for (int z = -RadarDisplayStructureResolver.CONTROLLER_SCAN_RADIUS; z <= RadarDisplayStructureResolver.CONTROLLER_SCAN_RADIUS; z++) {
                    BlockPos pos = changedPos.offset(x, y, z);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.is(com.limbo2136.powerradar.registry.ModBlocks.RADAR_DISPLAY.get())) {
                        output.add(pos);
                    }
                }
            }
        }
    }

    static boolean overlaps(Set<BlockPos> first, Set<BlockPos> second) {
        for (BlockPos pos : first) {
            if (second.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private static List<RadarDisplayStructure> enumerateCandidates(Level level, BlockPos anchorPos, Direction facing) {
        Direction right = RadarDisplayStructureResolver.right(facing);
        ArrayList<RadarDisplayStructure> candidates = new ArrayList<>();
        for (int size = RadarDisplayStructureResolver.MIN_SIZE; size <= RadarDisplayStructureResolver.MAX_SIZE; size++) {
            for (int anchorU = 0; anchorU < size; anchorU++) {
                for (int anchorV = 0; anchorV < size; anchorV++) {
                    BlockPos origin = anchorPos.relative(right.getOpposite(), anchorU).below(anchorV);
                    Optional<RadarDisplayStructure> candidate = candidate(level, origin, facing, size);
                    candidate.ifPresent(candidates::add);
                }
            }
        }
        return candidates;
    }

    private static Optional<RadarDisplayStructure> candidate(Level level, BlockPos origin, Direction facing, int size) {
        Direction right = RadarDisplayStructureResolver.right(facing);
        HashSet<BlockPos> positions = new HashSet<>(size * size);
        for (int u = 0; u < size; u++) {
            for (int v = 0; v < size; v++) {
                BlockPos pos = RadarDisplayStructureResolver.localOffset(origin, right, u, v);
                if (!level.isLoaded(pos) || !RadarDisplayStructure.isMatchingDisplay(level.getBlockState(pos), facing)) {
                    return Optional.empty();
                }
                positions.add(pos);
            }
        }
        RadarDisplayStructure structure = new RadarDisplayStructure(origin, size, facing, Set.copyOf(positions));
        return structure.assembled() ? Optional.of(structure) : Optional.empty();
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

    private record StandaloneCandidateKey(BlockPos origin, int size, Direction facing) {
    }
}
