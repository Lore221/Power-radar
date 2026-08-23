package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureObservation;
import com.limbo2136.powerradar.compat.aeronautics.SableWarningManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Объединяет дорогие запросы кандидатов в мире, сохраняя отдельный авторитетный кэш каждого радара.
 * Порядок заявок внутри одного серверного тика остаётся порядком их поступления.
 */
public final class RadarScanCoordinator {
    private static final double MAX_SHARED_UNION_RATIO = 0.90D;
    private static final Map<ServerLevel, List<RadarScanRequest>> PENDING = new WeakHashMap<>();

    private RadarScanCoordinator() {
    }

    public static void submit(ServerLevel level, RadarScanRequest request) {
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(request);
    }

    public static void tickServer(MinecraftServer server) {
        List<Map.Entry<ServerLevel, List<RadarScanRequest>>> ready = new ArrayList<>();
        PENDING.entrySet().removeIf(entry -> {
            if (entry.getKey().getServer() != server) {
                return false;
            }
            // Список уже исключается из PENDING и больше никем не изменяется, поэтому копия здесь не нужна.
            ready.add(Map.entry(entry.getKey(), entry.getValue()));
            return true;
        });
        for (Map.Entry<ServerLevel, List<RadarScanRequest>> entry : ready) {
            processLevel(entry.getKey(), entry.getValue());
        }
        SableRadarIntegration.tickSilhouetteCache(server);
    }

    public static void stopServer(MinecraftServer server) {
        PENDING.entrySet().removeIf(entry -> entry.getKey().getServer() == server);
        SableRadarIntegration.clearSilhouetteCache(server);
    }

    private static void processLevel(ServerLevel level, List<RadarScanRequest> requests) {
        long start = PowerRadarDebugOptions.scanOptimizationLogging() ? System.nanoTime() : 0L;

        // Этап 1: разворачиваем заявки в пространственные срезы и объединяем только выгодные пересечения.
        List<SliceWork> work = new ArrayList<>();
        for (RadarScanRequest request : requests) {
            if (request.discoveryProfile() == null) {
                continue;
            }
            for (AABB slice : request.slices()) {
                work.add(new SliceWork(request, slice));
            }
        }

        List<SharedBatch> batches = groupWork(work);
        boolean sableFilterEnabled = false;
        for (SharedBatch batch : batches) {
            if (batch.queriesSableStructures()) {
                sableFilterEnabled = true;
                break;
            }
        }
        List<SableStructureObservation> loadedSableStructures = sableFilterEnabled
                ? SableRadarIntegration.loadedStructures(level)
                : List.of();
        Map<RadarScanRequest, Set<TargetKey>> seenByRequest = new IdentityHashMap<>();
        Map<RadarScanRequest, RadarCoverageFilter.PreparedCoverage> coverageByRequest = new IdentityHashMap<>();
        RadarSurfaceHeightCache surfaceHeights = new RadarSurfaceHeightCache(level);
        int entityCandidates = 0;
        int sableCandidates = 0;

        // Этап 2: общий запрос даёт кандидатов, но точное покрытие и запись трека выполняются отдельно.
        for (SharedBatch batch : batches) {
            Collection<RadarScanProfile> profiles = batch.profiles();
            List<RadarScanner.RadarCandidate> candidates = RadarScanner.queryCandidates(
                    level, batch.queryBox(), profiles);
            entityCandidates += candidates.size();
            for (SliceWork member : batch.members) {
                Set<TargetKey> seen = seenByRequest.computeIfAbsent(member.request, ignored -> new HashSet<>());
                RadarCoverageFilter.PreparedCoverage coverage = coverageFor(
                        coverageByRequest, member.request, member.request.discoveryProfile());
                for (RadarScanner.RadarCandidate candidate : candidates) {
                    if (!candidate.entity().getBoundingBox().intersects(member.slice)) {
                        continue;
                    }
                    RadarScanner.processSharedCandidate(
                            member.request.discoveryProfile(), member.request.context(),
                            member.request.targetCache(), candidate, seen, coverage, surfaceHeights);
                }
            }

            if (batch.queriesSableStructures()) {
                List<SableStructureObservation> structures = new ArrayList<>();
                for (SableStructureObservation structure : loadedSableStructures) {
                    if (intersectsHorizontally(structure.worldBounds(), batch.queryBox())) {
                        structures.add(structure);
                    }
                }
                sableCandidates += structures.size();
                for (SliceWork member : batch.members) {
                    if (!member.request.discoveryProfile().queriesSableStructures()) {
                        continue;
                    }
                    Set<TargetKey> seen = seenByRequest.computeIfAbsent(member.request, ignored -> new HashSet<>());
                    RadarCoverageFilter.PreparedCoverage coverage = coverageFor(
                            coverageByRequest, member.request, member.request.discoveryProfile());
                    for (SableStructureObservation structure : structures) {
                        if (!intersectsHorizontally(structure.worldBounds(), member.slice)) {
                            continue;
                        }
                        if (RadarScanner.processSableCandidate(
                                member.request.discoveryProfile(), member.request.context(),
                                member.request.targetCache(), structure, seen, coverage, surfaceHeights)) {
                            member.request.sableCoverage().record(structure.structureUuid());
                        }
                    }
                }
            }
        }

        // Этап 3: публикация обновляет уже захваченные сущности и выполняет очистку каждого кэша.
        for (RadarScanRequest request : requests) {
            if (!request.publish()) {
                continue;
            }
            SableWarningManager.replaceRadarCoverage(
                    level,
                    request.radarId(),
                    request.sableCoverage().snapshot(),
                    request.context().gameTime());
            if (request.refreshProfile() != null) {
                RadarScanner.refreshTrackedEntities(
                        request.refreshProfile(), request.context(), request.targetCache(),
                        coverageFor(coverageByRequest, request, request.refreshProfile()), surfaceHeights);
            }
            RadarScanner.housekeeping(request.context(), request.targetCache());
            if (request.publishCompletion() != null) {
                request.publishCompletion().run();
            }
        }

        if (PowerRadarDebugOptions.scanOptimizationLogging() && !requests.isEmpty()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][SharedRadarScan] dimension={} requests={} slices={} batches={} entityCandidates={} sableCandidates={} durationUs={}",
                    level.dimension().location(), requests.size(), work.size(), batches.size(), entityCandidates,
                    sableCandidates, (System.nanoTime() - start) / 1_000L);
        }
    }

    private static RadarCoverageFilter.PreparedCoverage coverageFor(
            Map<RadarScanRequest, RadarCoverageFilter.PreparedCoverage> coverages,
            RadarScanRequest request,
            RadarScanProfile profile
    ) {
        RadarCoverageFilter.PreparedCoverage coverage = coverages.get(request);
        if (coverage == null) {
            coverage = RadarCoverageFilter.prepare(profile, request.context());
            coverages.put(request, coverage);
        }
        return coverage;
    }

    private static List<SharedBatch> groupWork(List<SliceWork> work) {
        if (work.isEmpty()) {
            return List.of();
        }
        double cellSize = Math.max(1.0D, RadarConstants.entityQuerySliceSize());
        Long2ObjectOpenHashMap<List<SharedBatch>> buckets = new Long2ObjectOpenHashMap<>();
        List<SharedBatch> batches = new ArrayList<>();
        for (SliceWork item : work) {
            int cellX = cellCoordinate((item.slice.minX + item.slice.maxX) * 0.5D, cellSize);
            int cellZ = cellCoordinate((item.slice.minZ + item.slice.maxZ) * 0.5D, cellSize);
            SharedBatch best = null;
            double bestRatio = MAX_SHARED_UNION_RATIO;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<SharedBatch> nearby = buckets.get(cellKey(cellX + dx, cellZ + dz));
                    if (nearby == null) {
                        continue;
                    }
                    for (SharedBatch candidate : nearby) {
                        if (candidate.containsRadar(item.request.radarId())) {
                            continue;
                        }
                        double ratio = mergeRatio(candidate.queryBox, item.slice);
                        if (isBeneficialMerge(candidate.queryBox, item.slice)
                                && ratio < bestRatio) {
                            bestRatio = ratio;
                            best = candidate;
                        }
                    }
                }
            }
            if (best == null) {
                SharedBatch batch = new SharedBatch(item);
                batches.add(batch);
                buckets.computeIfAbsent(cellKey(cellX, cellZ), ignored -> new ArrayList<>()).add(batch);
            } else {
                best.add(item);
            }
        }
        return batches;
    }

    private static AABB union(AABB first, AABB second) {
        return new AABB(
                Math.min(first.minX, second.minX), Math.min(first.minY, second.minY), Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX), Math.max(first.maxY, second.maxY), Math.max(first.maxZ, second.maxZ));
    }

    private static boolean intersectsHorizontally(AABB first, AABB second) {
        return first.minX < second.maxX && first.maxX > second.minX
                && first.minZ < second.maxZ && first.maxZ > second.minZ;
    }

    private static double volume(AABB box) {
        return Math.max(0.0D, box.getXsize()) * Math.max(0.0D, box.getYsize()) * Math.max(0.0D, box.getZsize());
    }

    static boolean isBeneficialMerge(AABB existingUnion, AABB next) {
        return mergeRatio(existingUnion, next) < MAX_SHARED_UNION_RATIO;
    }

    private static double mergeRatio(AABB existingUnion, AABB next) {
        return volume(union(existingUnion, next))
                / Math.max(1.0D, volume(existingUnion) + volume(next));
    }

    private record SliceWork(RadarScanRequest request, AABB slice) {
    }

    private static int cellCoordinate(double coordinate, double cellSize) {
        return (int) Math.floor(coordinate / cellSize);
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }

    private static final class SharedBatch {
        private final List<SliceWork> members = new ArrayList<>();
        private final Set<RadarId> radarIds = new HashSet<>();
        private final Set<RadarScanProfile> profiles = new LinkedHashSet<>();
        private AABB queryBox;
        private boolean queriesSableStructures;

        private SharedBatch(SliceWork first) {
            add(first);
        }

        private void add(SliceWork item) {
            this.members.add(item);
            this.radarIds.add(item.request.radarId());
            RadarScanProfile profile = item.request.discoveryProfile();
            this.profiles.add(profile);
            this.queriesSableStructures |= profile.queriesSableStructures();
            this.queryBox = this.queryBox == null ? item.slice : union(this.queryBox, item.slice);
        }

        private boolean containsRadar(RadarId radarId) {
            return this.radarIds.contains(radarId);
        }

        private AABB queryBox() {
            return this.queryBox;
        }

        private Collection<RadarScanProfile> profiles() {
            return this.profiles;
        }

        private boolean queriesSableStructures() {
            return this.queriesSableStructures;
        }
    }
}
