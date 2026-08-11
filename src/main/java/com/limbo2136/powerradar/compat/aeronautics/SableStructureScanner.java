package com.limbo2136.powerradar.compat.aeronautics;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.radar.SableStructureName;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;

final class SableStructureScanner {
    private static final int CACHE_TTL_TICKS = 6_000;
    private static final int MIN_REBUILD_WORK_PER_TICK = 4_096;
    private static final int DIRTY_REBUILD_DEBOUNCE_TICKS = 40;
    private static final int FALLBACK_RETRY_TICKS = 200;
    // Кэш принадлежит экземпляру сервера; слабый ключ не удерживает завершённую локальную сессию.
    private static final Map<MinecraftServer, SilhouetteCache> CACHES = new WeakHashMap<>();

    private SableStructureScanner() {
    }

    static List<SableStructureObservation> loadedStructures(ServerLevel level) {
        Map<UUID, SableStructureObservation> observations = new HashMap<>();
        for (SubLevel subLevel : SubLevelContainer.getContainer(level).getAllSubLevels()) {
            if (!(subLevel instanceof ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
                continue;
            }
            SableStructureObservation observation = observe(serverSubLevel);
            if (observation != null) {
                observations.putIfAbsent(serverSubLevel.getUniqueId(), observation);
            }
        }
        return List.copyOf(observations.values());
    }

    static Optional<SableStructureObservation> loadedStructure(ServerLevel level, UUID structureUuid) {
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(structureUuid);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
            return Optional.empty();
        }
        return Optional.ofNullable(observe(serverSubLevel));
    }

    static Optional<SableStructureGeometry> loadedStructureGeometry(ServerLevel level, UUID structureUuid) {
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(structureUuid);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
            return Optional.empty();
        }
        BoundingBox3ic localBounds = serverSubLevel.getPlot().getBoundingBox();
        if (!valid(localBounds)) {
            return Optional.empty();
        }
        AABB localAabb = localAabb(localBounds);
        Vec3 localCenter = localCenter(localBounds);
        // localBounds остаётся в пространстве plot, worldBounds строится по всем восьми углам текущей позы.
        return Optional.of(new SableStructureGeometry(
                localAabb,
                localCenter,
                serverSubLevel.logicalPose().transformPosition(localCenter),
                worldBounds(serverSubLevel, localAabb)));
    }

    static Optional<SableStructurePose> loadedStructurePose(
            ServerLevel level,
            UUID structureUuid,
            AABB localBounds,
            Vec3 localCenter
    ) {
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(structureUuid);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(new SableStructurePose(
                serverSubLevel.logicalPose().transformPosition(localCenter),
                worldBounds(serverSubLevel, localBounds)));
    }

    static Optional<SableStructureMotion> loadedStructureMotion(
            ServerLevel level,
            UUID structureUuid,
            Vec3 localCenter
    ) {
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(structureUuid);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
            return Optional.empty();
        }
        Vec3 worldOrigin = serverSubLevel.logicalPose().transformPosition(localCenter);
        // Множитель 0,05 переводит скорость Sable в используемые радаром блоки/tick.
        Vec3 velocity = Sable.HELPER
                .getVelocity(serverSubLevel.getLevel(), serverSubLevel, localCenter)
                .scale(0.05D);
        return Optional.of(new SableStructureMotion(worldOrigin, velocity));
    }

    static Optional<Vec3> loadedStructureOrigin(
            ServerLevel level,
            UUID structureUuid,
            Vec3 localCenter
    ) {
        SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(structureUuid);
        if (!(subLevel instanceof ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
            return Optional.empty();
        }
        return Optional.of(serverSubLevel.logicalPose().transformPosition(localCenter));
    }

    static Optional<UUID> containingStructureUuid(ServerLevel level, net.minecraft.core.BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel == null || subLevel.isRemoved() ? Optional.empty() : Optional.of(subLevel.getUniqueId());
    }

    static boolean isEntityOnStructure(Entity entity) {
        return Sable.HELPER.getTrackingSubLevel(entity) != null;
    }

    static boolean isInsideStructure(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel != null && !subLevel.isRemoved();
    }

    private static SableStructureObservation observe(ServerSubLevel serverSubLevel) {
        BoundingBox3ic localBounds = serverSubLevel.getPlot().getBoundingBox();
        if (!valid(localBounds)) {
            return null;
        }
        Vec3 localCenter = localCenter(localBounds);
        Vec3 geometricCenter = serverSubLevel.logicalPose().transformPosition(localCenter);
        Vector3d worldForward = serverSubLevel.logicalPose().transformNormal(new Vector3d(0.0D, 0.0D, 1.0D));
        float heading = (float) Math.toDegrees(Math.atan2(-worldForward.x(), worldForward.z()));
        Vec3 velocity = Sable.HELPER
                .getVelocity(serverSubLevel.getLevel(), serverSubLevel, localCenter)
                .scale(0.05D);
        var logicalPose = serverSubLevel.logicalPose();
        return new SableStructureObservation(
                serverSubLevel.getUniqueId(),
                SableStructureName.normalize(serverSubLevel.getName()),
                geometricCenter,
                velocity,
                heading,
                logicalPose.rotationPoint().x(),
                logicalPose.rotationPoint().z(),
                logicalPose.position().x(),
                logicalPose.position().z(),
                serverSubLevel.boundingBox().toMojang());
    }

    static void markDetected(ServerLevel level, UUID structureUuid, long gameTime) {
        cache(level.getServer()).markDetected(level, structureUuid, gameTime);
    }

    static void markSilhouetteDirty(ServerLevel level, net.minecraft.core.BlockPos changedPos, long gameTime) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, changedPos);
        if (subLevel != null && !subLevel.isRemoved()) {
            cache(level.getServer()).markDirty(level, subLevel.getUniqueId(), gameTime);
        }
    }

    static void tickSilhouetteCache(MinecraftServer server) {
        cache(server).tick(server);
    }

    static void clearSilhouetteCache(MinecraftServer server) {
        CACHES.remove(server);
    }

    static Optional<SableSilhouetteSnapshot> silhouetteSnapshot(
            MinecraftServer server,
            ResourceLocation dimensionId,
            UUID structureUuid
    ) {
        return cache(server).snapshot(new StructureKey(dimensionId, structureUuid));
    }

    private static SilhouetteCache cache(MinecraftServer server) {
        return CACHES.computeIfAbsent(server, ignored -> new SilhouetteCache());
    }

    private static boolean valid(BoundingBox3ic bounds) {
        return bounds.maxX() >= bounds.minX()
                && bounds.maxY() >= bounds.minY()
                && bounds.maxZ() >= bounds.minZ();
    }

    private static Vec3 localCenter(BoundingBox3ic bounds) {
        return new Vec3(
                (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1.0D) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D);
    }

    private static AABB localAabb(BoundingBox3ic bounds) {
        return new AABB(
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX() + 1.0D,
                bounds.maxY() + 1.0D,
                bounds.maxZ() + 1.0D);
    }

    private static AABB worldBounds(ServerSubLevel subLevel, AABB localBounds) {
        return SableStructureGeometry.enclosingWorldBounds(
                localBounds,
                subLevel.logicalPose()::transformPosition);
    }

    private record StructureKey(ResourceLocation dimensionId, UUID structureUuid) {
    }

    private static final class SilhouetteCache {
        private final Map<StructureKey, CacheEntry> entries = new HashMap<>();
        private Set<StructureKey> currentUpdateSet = Set.of();
        private Set<StructureKey> nextUpdateSet = new LinkedHashSet<>();
        private final Map<StructureKey, Long> dirtySince = new HashMap<>();
        private final Deque<BuildTask> rebuildQueue = new ArrayDeque<>();
        private long cycleStart = Long.MIN_VALUE;
        private final int rebuildWorkPerTick = MIN_REBUILD_WORK_PER_TICK;

        private void markDetected(ServerLevel level, UUID structureUuid, long gameTime) {
            StructureKey key = new StructureKey(level.dimension().location(), structureUuid);
            CacheEntry entry = this.entries.get(key);
            if (entry == null) {
                entry = new CacheEntry(gameTime);
                this.entries.put(key, entry);
            }
            if (entry.snapshot == null) {
                // Первый запрос строится сразу, чтобы монитор не ждал начала следующего фонового цикла.
                BuildTask immediate = createTask(level, key);
                if (immediate != null) {
                    entry.snapshot = snapshotOf(
                            immediate.fallbackResult(), immediate, 1, gameTime, SableSilhouetteStatus.BUILDING);
                    this.rebuildQueue.addLast(immediate);
                }
            }
            entry.lastSeenGameTime = gameTime;
            this.nextUpdateSet.add(key);
        }

        private void markDirty(ServerLevel level, UUID structureUuid, long gameTime) {
            StructureKey key = new StructureKey(level.dimension().location(), structureUuid);
            CacheEntry entry = this.entries.computeIfAbsent(key, ignored -> new CacheEntry(gameTime));
            entry.lastSeenGameTime = gameTime;
            this.dirtySince.put(key, gameTime);
            this.nextUpdateSet.add(key);
        }

        private void tick(MinecraftServer server) {
            long gameTime = server.overworld().getGameTime();
            List<StructureKey> dirtyReady = this.dirtySince.entrySet().stream()
                    .filter(entry -> gameTime - entry.getValue() >= DIRTY_REBUILD_DEBOUNCE_TICKS)
                    .map(Map.Entry::getKey)
                    .toList();
            for (StructureKey key : dirtyReady) {
                ServerLevel level = level(server, key.dimensionId());
                CacheEntry entry = this.entries.get(key);
                if (level != null && entry != null && gameTime >= entry.nextBuildAllowedGameTime
                        && this.rebuildQueue.stream().noneMatch(task -> task.key.equals(key))) {
                    BuildTask task = createTask(level, key);
                    if (task != null) {
                        this.rebuildQueue.addLast(task);
                        this.dirtySince.remove(key);
                    }
                }
            }
            enqueueFallbackRetries(server, gameTime);
            if (this.cycleStart == Long.MIN_VALUE) {
                this.cycleStart = gameTime - Math.floorMod(
                        gameTime, SableRadarIntegration.GEOMETRY_REFRESH_INTERVAL_TICKS);
            }
            if (gameTime - this.cycleStart >= SableRadarIntegration.GEOMETRY_REFRESH_INTERVAL_TICKS) {
                beginNextCycle(server, gameTime);
            }
            int remaining = this.rebuildWorkPerTick;
            while (remaining > 0 && !this.rebuildQueue.isEmpty()) {
                BuildTask task = this.rebuildQueue.peekFirst();
                int used = task.process(remaining);
                remaining -= Math.max(1, used);
                if (task.complete()) {
                    this.rebuildQueue.removeFirst();
                    applyFinished(task, gameTime);
                } else if (used <= 0) {
                    break;
                }
            }
            this.entries.entrySet().removeIf(entry ->
                    gameTime - entry.getValue().lastSeenGameTime > CACHE_TTL_TICKS
                            && !this.currentUpdateSet.contains(entry.getKey())
                            && !this.nextUpdateSet.contains(entry.getKey())
                            && !this.dirtySince.containsKey(entry.getKey()));
        }

        private void enqueueFallbackRetries(MinecraftServer server, long gameTime) {
            for (Map.Entry<StructureKey, CacheEntry> cached : this.entries.entrySet()) {
                StructureKey key = cached.getKey();
                CacheEntry entry = cached.getValue();
                boolean stillDetected = this.currentUpdateSet.contains(key) || this.nextUpdateSet.contains(key);
                if (!stillDetected
                        || entry.snapshot == null
                        || entry.snapshot.status() != SableSilhouetteStatus.FALLBACK_BOUNDS
                        || gameTime < entry.nextBuildAllowedGameTime
                        || this.rebuildQueue.stream().anyMatch(task -> task.key.equals(key))) {
                    continue;
                }
                ServerLevel level = level(server, key.dimensionId());
                BuildTask task = level == null ? null : createTask(level, key);
                if (task != null) {
                    this.rebuildQueue.addLast(task);
                    entry.nextBuildAllowedGameTime = gameTime + FALLBACK_RETRY_TICKS;
                }
            }
        }

        private void beginNextCycle(MinecraftServer server, long gameTime) {
            // Набор текущего цикла фиксируется снимком, а новые обнаружения переходят в следующий.
            this.cycleStart = gameTime;
            this.currentUpdateSet = Set.copyOf(this.nextUpdateSet);
            this.nextUpdateSet = new LinkedHashSet<>();
            for (StructureKey key : this.currentUpdateSet) {
                ServerLevel level = level(server, key.dimensionId());
                BuildTask task = level == null ? null : createTask(level, key);
                CacheEntry entry = this.entries.get(key);
                if (task != null && entry != null && gameTime >= entry.nextBuildAllowedGameTime
                        && this.rebuildQueue.stream().noneMatch(queued -> queued.key.equals(key))) {
                    this.rebuildQueue.addLast(task);
                }
            }
        }

        private Optional<SableSilhouetteSnapshot> snapshot(StructureKey key) {
            CacheEntry entry = this.entries.get(key);
            return entry == null ? Optional.empty() : Optional.ofNullable(entry.snapshot);
        }

        private void applyFinished(BuildTask task, long gameTime) {
            SableSilhouetteBuilder.Result result = task.result();
            CacheEntry entry = this.entries.computeIfAbsent(task.key, ignored -> new CacheEntry(gameTime));
            if (entry.snapshot != null
                    && entry.geometryHash == result.geometryHash()
                    && entry.snapshot.status() == task.status()) {
                entry.snapshot = snapshotOf(result, task, entry.snapshot.version(), gameTime, task.status());
                entry.nextBuildAllowedGameTime = task.status() == SableSilhouetteStatus.DETAILED
                        ? gameTime : gameTime + FALLBACK_RETRY_TICKS;
                logBuild(task, result);
                return;
            }
            int version = entry.snapshot == null ? 1 : entry.snapshot.version() + 1;
            entry.geometryHash = result.geometryHash();
            entry.snapshot = snapshotOf(result, task, version, gameTime, task.status());
            entry.nextBuildAllowedGameTime = task.status() == SableSilhouetteStatus.DETAILED
                    ? gameTime : gameTime + FALLBACK_RETRY_TICKS;
            logBuild(task, result);
        }

        private static SableSilhouetteSnapshot snapshotOf(
                SableSilhouetteBuilder.Result result,
                BuildTask task,
                int version,
                long gameTime,
                SableSilhouetteStatus status
        ) {
            return new SableSilhouetteSnapshot(
                    task.key.dimensionId(), task.key.structureUuid(), version, gameTime, status,
                    task.anchorX, task.anchorZ,
                    result.lines(), result.fills());
        }

        private static void logBuild(BuildTask task, SableSilhouetteBuilder.Result result) {
            if (!PowerRadarDebugOptions.sableSilhouetteBuildLogging()) {
                return;
            }
            PowerRadar.LOGGER.info(
                    "Sable silhouette structure={} dimension={} status={} duration_ms={} scanned_blocks={} columns={} lines={} fills={} geometry_bytes={} reason={}",
                    task.key.structureUuid(), task.key.dimensionId(), task.status(), task.durationMillis(),
                    task.scannedBlocks(), task.occupiedColumns(), result.lines().size(), result.fills().size(),
                    SableSilhouetteLimits.estimatedGeometryBytes(result.lines().size(), result.fills().size()),
                    task.fallbackReason() == null ? "none" : task.fallbackReason());
        }

        private static BuildTask createTask(ServerLevel level, StructureKey key) {
            SubLevel subLevel = SubLevelContainer.getContainer(level).getSubLevel(key.structureUuid());
            if (!(subLevel instanceof ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
                return null;
            }
            return BuildTask.create(key, serverSubLevel);
        }

        private static ServerLevel level(MinecraftServer server, ResourceLocation dimensionId) {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        }
    }

    private static final class CacheEntry {
        private long lastSeenGameTime;
        private int geometryHash;
        private SableSilhouetteSnapshot snapshot;
        private long nextBuildAllowedGameTime;

        private CacheEntry(long lastSeenGameTime) {
            this.lastSeenGameTime = lastSeenGameTime;
        }
    }

    private static final class BuildTask {
        private final StructureKey key;
        private final double anchorX;
        private final double anchorZ;
        private final List<SectionCursor> sections;
        private final Set<Long> occupiedColumns = new HashSet<>();
        private final SableSilhouetteBuilder.Result fallbackResult;
        private final long startedNanos = System.nanoTime();
        private int sectionIndex;
        private int scannedBlocks;
        private boolean complete;
        private SableSilhouetteBuilder.Result result;
        private SableSilhouetteStatus status = SableSilhouetteStatus.DETAILED;
        private String fallbackReason;

        private BuildTask(
                StructureKey key,
                double anchorX,
                double anchorZ,
                List<SectionCursor> sections,
                SableSilhouetteBuilder.Result fallbackResult
        ) {
            this.key = key;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
            this.sections = sections;
            this.fallbackResult = fallbackResult;
            this.complete = sections.isEmpty();
            if (this.complete) {
                finish();
            }
        }

        private static BuildTask create(StructureKey key, ServerSubLevel subLevel) {
            BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (!valid(bounds)) {
                SableSilhouetteBuilder.Result empty = SableSilhouetteBuilder.build(Set.of(), 0.0D, 0.0D);
                return new BuildTask(key, 0.0D, 0.0D, List.of(), empty);
            }
            List<SectionCursor> sections = new ArrayList<>();
            // Секции сортируются по локальным координатам plot для воспроизводимого результата.
            for (PlotChunkHolder holder : subLevel.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                LevelChunkSection[] chunkSections = chunk.getSections();
                for (int index = 0; index < chunkSections.length; index++) {
                    LevelChunkSection section = chunkSections[index];
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    int minX = chunk.getPos().getMinBlockX();
                    int minZ = chunk.getPos().getMinBlockZ();
                    int minY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
                    if (minX + 15 < bounds.minX() || minX > bounds.maxX()
                            || minY + 15 < bounds.minY() || minY > bounds.maxY()
                            || minZ + 15 < bounds.minZ() || minZ > bounds.maxZ()) {
                        continue;
                    }
                    sections.add(new SectionCursor(minX, minY, minZ, section, bounds));
                }
            }
            sections.sort(Comparator.comparingInt((SectionCursor section) -> section.minX)
                    .thenComparingInt(section -> section.minZ)
                    .thenComparingInt(section -> section.minY));
            double anchorX = (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D;
            double anchorZ = (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D;
            SableSilhouetteBuilder.Result fallback = rectangle(
                    bounds.minX(), bounds.minZ(), bounds.maxX() + 1, bounds.maxZ() + 1, anchorX, anchorZ);
            BuildTask task = new BuildTask(
                    key,
                    anchorX,
                    anchorZ,
                    List.copyOf(sections),
                    fallback);
            if (task.estimatedWork() > SableSilhouetteLimits.MAX_SCANNED_BLOCKS) {
                task.useFallback("scan_limit");
            }
            return task;
        }

        private long estimatedWork() {
            return (long) this.sections.size() * 4_096L;
        }

        private int process(int budget) {
            if (this.complete) {
                return 0;
            }
            int used = 0;
            while (used < budget && this.sectionIndex < this.sections.size()) {
                SectionCursor section = this.sections.get(this.sectionIndex);
                int processed = section.process(budget - used, this.occupiedColumns);
                used += processed;
                this.scannedBlocks += processed;
                if (section.complete()) {
                    this.sectionIndex++;
                }
            }
            if (this.sectionIndex >= this.sections.size()) {
                finish();
            }
            return used;
        }

        private void finish() {
            this.result = SableSilhouetteBuilder.build(this.occupiedColumns, this.anchorX, this.anchorZ);
            if (!SableSilhouetteLimits.accepts(this.result.lines().size(), this.result.fills().size())) {
                useFallback("output_limit");
                return;
            }
            this.complete = true;
        }

        private void useFallback(String reason) {
            this.result = this.fallbackResult;
            this.status = SableSilhouetteStatus.FALLBACK_BOUNDS;
            this.fallbackReason = reason;
            this.complete = true;
        }

        private boolean complete() {
            return this.complete;
        }

        private SableSilhouetteBuilder.Result result() {
            return this.result;
        }

        private SableSilhouetteBuilder.Result fallbackResult() {
            return this.fallbackResult;
        }

        private SableSilhouetteStatus status() {
            return this.status;
        }

        private String fallbackReason() {
            return this.fallbackReason;
        }

        private int scannedBlocks() {
            return this.scannedBlocks;
        }

        private int occupiedColumns() {
            return this.occupiedColumns.size();
        }

        private long durationMillis() {
            return (System.nanoTime() - this.startedNanos) / 1_000_000L;
        }

        private static SableSilhouetteBuilder.Result rectangle(
                int minX,
                int minZ,
                int maxX,
                int maxZ,
                double anchorX,
                double anchorZ
        ) {
            float left = (float) (minX - anchorX);
            float top = (float) (minZ - anchorZ);
            float right = (float) (maxX - anchorX);
            float bottom = (float) (maxZ - anchorZ);
            List<SableSilhouetteLine> lines = List.of(
                    new SableSilhouetteLine(left, top, right, top),
                    new SableSilhouetteLine(right, top, right, bottom),
                    new SableSilhouetteLine(right, bottom, left, bottom),
                    new SableSilhouetteLine(left, bottom, left, top));
            List<SableSilhouetteFill> fills = List.of(new SableSilhouetteFill(left, top, right, bottom));
            return new SableSilhouetteBuilder.Result(lines, fills, 31 * lines.hashCode() + fills.hashCode());
        }
    }

    private static final class SectionCursor {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final LevelChunkSection section;
        private final BoundingBox3ic bounds;
        private int index;

        private SectionCursor(int minX, int minY, int minZ, LevelChunkSection section, BoundingBox3ic bounds) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.section = section;
            this.bounds = bounds;
        }

        private int process(int budget, Set<Long> occupiedColumns) {
            int start = this.index;
            int end = Math.min(4_096, this.index + budget);
            for (; this.index < end; this.index++) {
                // Индекс секции разворачивается в локальный Minecraft-порядок X, Z, Y.
                int localX = this.index & 15;
                int localZ = (this.index >>> 4) & 15;
                int localY = (this.index >>> 8) & 15;
                int x = this.minX + localX;
                int y = this.minY + localY;
                int z = this.minZ + localZ;
                if (x < this.bounds.minX() || x > this.bounds.maxX()
                        || y < this.bounds.minY() || y > this.bounds.maxY()
                        || z < this.bounds.minZ() || z > this.bounds.maxZ()) {
                    continue;
                }
                if (!this.section.getBlockState(localX, localY, localZ).isAir()) {
                    occupiedColumns.add(SableSilhouetteBuilder.pack(x, z));
                }
            }
            return this.index - start;
        }

        private boolean complete() {
            return this.index >= 4_096;
        }
    }

}
