package com.limbo2136.powerradar.compat.aeronautics;

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
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

final class SableStructureScanner {
    private static final int REBUILD_CYCLE_TICKS = 200;
    private static final int CACHE_TTL_TICKS = 6_000;
    private static final int MIN_REBUILD_WORK_PER_TICK = 4_096;
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

    static Optional<UUID> containingStructureUuid(ServerLevel level, net.minecraft.core.BlockPos pos) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        return subLevel == null || subLevel.isRemoved() ? Optional.empty() : Optional.of(subLevel.getUniqueId());
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
        Vec3 worldOrigin = serverSubLevel.logicalPose().transformPosition(localCenter);
        Vector3d worldForward = serverSubLevel.logicalPose().transformNormal(new Vector3d(0.0D, 0.0D, 1.0D));
        float heading = (float) Math.toDegrees(Math.atan2(-worldForward.x(), worldForward.z()));
        Vec3 velocity = Sable.HELPER
                .getVelocity(serverSubLevel.getLevel(), serverSubLevel, localCenter)
                .scale(0.05D);
        String name = serverSubLevel.getName();
        if (name == null || name.isBlank()) {
            name = "Sable Structure";
        }
        name = SableStructureNames.resolve(serverSubLevel.getLevel().getServer(), serverSubLevel.getUniqueId(), name);
        return new SableStructureObservation(
                serverSubLevel.getUniqueId(),
                name,
                worldOrigin,
                velocity,
                heading,
                serverSubLevel.boundingBox().toMojang());
    }

    static void markDetected(ServerLevel level, UUID structureUuid, long gameTime) {
        cache(level.getServer()).markDetected(level, structureUuid, gameTime);
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

    private record StructureKey(ResourceLocation dimensionId, UUID structureUuid) {
    }

    private static final class SilhouetteCache {
        private final Map<StructureKey, CacheEntry> entries = new HashMap<>();
        private Set<StructureKey> currentUpdateSet = Set.of();
        private Set<StructureKey> nextUpdateSet = new LinkedHashSet<>();
        private final Deque<BuildTask> rebuildQueue = new ArrayDeque<>();
        private long cycleStart = Long.MIN_VALUE;
        private int rebuildWorkPerTick = MIN_REBUILD_WORK_PER_TICK;

        private void markDetected(ServerLevel level, UUID structureUuid, long gameTime) {
            StructureKey key = new StructureKey(level.dimension().location(), structureUuid);
            CacheEntry entry = this.entries.get(key);
            if (entry == null) {
                entry = new CacheEntry(gameTime);
                this.entries.put(key, entry);
            }
            if (entry.snapshot == null) {
                BuildTask immediate = createTask(level, key);
                if (immediate != null) {
                    immediate.finishImmediately();
                    applyFinished(immediate, gameTime);
                }
            }
            entry.lastSeenGameTime = gameTime;
            this.nextUpdateSet.add(key);
        }

        private void tick(MinecraftServer server) {
            long gameTime = server.overworld().getGameTime();
            if (this.cycleStart == Long.MIN_VALUE) {
                this.cycleStart = gameTime - Math.floorMod(gameTime, REBUILD_CYCLE_TICKS);
            }
            if (gameTime - this.cycleStart >= REBUILD_CYCLE_TICKS) {
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
                            && !this.nextUpdateSet.contains(entry.getKey()));
        }

        private void beginNextCycle(MinecraftServer server, long gameTime) {
            this.cycleStart = gameTime;
            this.currentUpdateSet = Set.copyOf(this.nextUpdateSet);
            this.nextUpdateSet = new LinkedHashSet<>();
            this.rebuildQueue.clear();
            long totalWork = 0L;
            for (StructureKey key : this.currentUpdateSet) {
                ServerLevel level = level(server, key.dimensionId());
                BuildTask task = level == null ? null : createTask(level, key);
                if (task != null) {
                    this.rebuildQueue.addLast(task);
                    totalWork += task.estimatedWork();
                }
            }
            this.rebuildWorkPerTick = (int) Math.min(Integer.MAX_VALUE, Math.max(
                    MIN_REBUILD_WORK_PER_TICK,
                    (totalWork + REBUILD_CYCLE_TICKS - 1L) / REBUILD_CYCLE_TICKS));
        }

        private Optional<SableSilhouetteSnapshot> snapshot(StructureKey key) {
            CacheEntry entry = this.entries.get(key);
            return entry == null ? Optional.empty() : Optional.ofNullable(entry.snapshot);
        }

        private void applyFinished(BuildTask task, long gameTime) {
            SableSilhouetteBuilder.Result result = task.result();
            CacheEntry entry = this.entries.computeIfAbsent(task.key, ignored -> new CacheEntry(gameTime));
            if (entry.snapshot != null && entry.geometryHash == result.geometryHash()) {
                entry.snapshot = new SableSilhouetteSnapshot(
                        task.key.dimensionId(), task.key.structureUuid(), entry.snapshot.version(), gameTime,
                        result.lines(), result.fills());
                return;
            }
            int version = entry.snapshot == null ? 1 : entry.snapshot.version() + 1;
            entry.geometryHash = result.geometryHash();
            entry.snapshot = new SableSilhouetteSnapshot(
                    task.key.dimensionId(), task.key.structureUuid(), version, gameTime,
                    result.lines(), result.fills());
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
        private int sectionIndex;
        private boolean complete;
        private SableSilhouetteBuilder.Result result;

        private BuildTask(StructureKey key, double anchorX, double anchorZ, List<SectionCursor> sections) {
            this.key = key;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
            this.sections = sections;
            this.complete = sections.isEmpty();
            if (this.complete) {
                finish();
            }
        }

        private static BuildTask create(StructureKey key, ServerSubLevel subLevel) {
            BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
            if (!valid(bounds)) {
                return new BuildTask(key, 0.0D, 0.0D, List.of());
            }
            List<SectionCursor> sections = new ArrayList<>();
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
            return new BuildTask(
                    key,
                    (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D,
                    (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D,
                    List.copyOf(sections));
        }

        private int estimatedWork() {
            return this.sections.size() * 4_096;
        }

        private int process(int budget) {
            if (this.complete) {
                return 0;
            }
            int used = 0;
            while (used < budget && this.sectionIndex < this.sections.size()) {
                SectionCursor section = this.sections.get(this.sectionIndex);
                used += section.process(budget - used, this.occupiedColumns);
                if (section.complete()) {
                    this.sectionIndex++;
                }
            }
            if (this.sectionIndex >= this.sections.size()) {
                finish();
            }
            return used;
        }

        private void finishImmediately() {
            while (!this.complete) {
                process(Integer.MAX_VALUE);
            }
        }

        private void finish() {
            this.result = SableSilhouetteBuilder.build(this.occupiedColumns, this.anchorX, this.anchorZ);
            this.complete = true;
        }

        private boolean complete() {
            return this.complete;
        }

        private SableSilhouetteBuilder.Result result() {
            return this.result;
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
