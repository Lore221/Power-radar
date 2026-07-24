package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureObservation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
            ready.add(Map.entry(entry.getKey(), List.copyOf(entry.getValue())));
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
        boolean sableFilterEnabled = batches.stream()
                .flatMap(batch -> batch.profiles().stream())
                .anyMatch(RadarScanProfile::detectSableStructures);
        List<SableStructureObservation> loadedSableStructures = sableFilterEnabled
                ? SableRadarIntegration.loadedStructures(level)
                : List.of();
        Map<RadarScanRequest, Set<TargetKey>> seenByRequest = new IdentityHashMap<>();
        int entityCandidates = 0;
        int sableCandidates = 0;

        // Этап 2: общий запрос даёт кандидатов, но точное покрытие и запись трека выполняются отдельно.
        for (SharedBatch batch : batches) {
            List<RadarScanProfile> profiles = batch.profiles();
            List<Entity> entities = RadarScanner.queryCandidates(level, batch.queryBox(), profiles);
            entityCandidates += entities.size();
            for (SliceWork member : batch.members) {
                Set<TargetKey> seen = seenByRequest.computeIfAbsent(member.request, ignored -> new HashSet<>());
                for (Entity entity : entities) {
                    if (!entity.getBoundingBox().intersects(member.slice)) {
                        continue;
                    }
                    RadarScanner.processSharedCandidate(
                            member.request.discoveryProfile(), member.request.context(),
                            member.request.targetCache(), entity, seen);
                }
            }

            if (profiles.stream().anyMatch(RadarScanProfile::detectSableStructures)) {
                List<SableStructureObservation> structures = loadedSableStructures.stream()
                        .filter(structure -> intersectsHorizontally(structure.worldBounds(), batch.queryBox()))
                        .toList();
                sableCandidates += structures.size();
                for (SliceWork member : batch.members) {
                    if (!member.request.discoveryProfile().detectSableStructures()) {
                        continue;
                    }
                    Set<TargetKey> seen = seenByRequest.computeIfAbsent(member.request, ignored -> new HashSet<>());
                    for (SableStructureObservation structure : structures) {
                        if (!intersectsHorizontally(structure.worldBounds(), member.slice)) {
                            continue;
                        }
                        RadarScanner.processSableCandidate(
                                member.request.discoveryProfile(), member.request.context(),
                                member.request.targetCache(), structure, seen);
                    }
                }
            }
        }

        // Этап 3: публикация обновляет уже захваченные сущности и выполняет очистку каждого кэша.
        for (RadarScanRequest request : requests) {
            if (!request.publish()) {
                continue;
            }
            if (request.refreshProfile() != null) {
                RadarScanner.refreshTrackedEntities(request.refreshProfile(), request.context(), request.targetCache());
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

    private static List<SharedBatch> groupWork(List<SliceWork> work) {
        if (work.isEmpty()) {
            return List.of();
        }
        double cellSize = Math.max(1.0D, RadarConstants.entityQuerySliceSize());
        Map<CellKey, List<SharedBatch>> buckets = new HashMap<>();
        List<SharedBatch> batches = new ArrayList<>();
        for (SliceWork item : work) {
            CellKey key = CellKey.forBox(item.slice, cellSize);
            SharedBatch best = null;
            double bestRatio = MAX_SHARED_UNION_RATIO;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (SharedBatch candidate : buckets.getOrDefault(new CellKey(key.x + dx, key.z + dz), List.of())) {
                        if (candidate.containsRadar(item.request.radarId())) {
                            continue;
                        }
                        double ratio = mergeRatio(candidate.queryBox, candidate.sourceVolume, item.slice);
                        if (isBeneficialMerge(candidate.queryBox, candidate.sourceVolume, item.slice)
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
                buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(batch);
            } else {
                best.add(item);
            }
        }
        return List.copyOf(batches);
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

    static boolean isBeneficialMerge(AABB existingUnion, double existingSourceVolume, AABB next) {
        return mergeRatio(existingUnion, existingSourceVolume, next) < MAX_SHARED_UNION_RATIO;
    }

    private static double mergeRatio(AABB existingUnion, double existingSourceVolume, AABB next) {
        return volume(union(existingUnion, next))
                / Math.max(1.0D, existingSourceVolume + volume(next));
    }

    private record SliceWork(RadarScanRequest request, AABB slice) {
    }

    private record CellKey(int x, int z) {
        private static CellKey forBox(AABB box, double cellSize) {
            return new CellKey((int) Math.floor(((box.minX + box.maxX) * 0.5D) / cellSize),
                    (int) Math.floor(((box.minZ + box.maxZ) * 0.5D) / cellSize));
        }
    }

    private static final class SharedBatch {
        private final List<SliceWork> members = new ArrayList<>();
        private final Set<RadarId> radarIds = new HashSet<>();
        private AABB queryBox;
        private double sourceVolume;

        private SharedBatch(SliceWork first) {
            add(first);
        }

        private void add(SliceWork item) {
            this.members.add(item);
            this.radarIds.add(item.request.radarId());
            this.queryBox = this.queryBox == null ? item.slice : union(this.queryBox, item.slice);
            this.sourceVolume += volume(item.slice);
        }

        private boolean containsRadar(RadarId radarId) {
            return this.radarIds.contains(radarId);
        }

        private AABB queryBox() {
            return this.queryBox;
        }

        private List<RadarScanProfile> profiles() {
            Map<RadarScanProfile, RadarScanProfile> unique = new LinkedHashMap<>();
            for (SliceWork member : this.members) {
                unique.put(member.request.discoveryProfile(), member.request.discoveryProfile());
            }
            return List.copyOf(unique.values());
        }
    }
}
