package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.compat.createbigcannons.RadarCbcProjectileCompat;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.limbo2136.powerradar.entity.RadarStructureEntity;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RadarScanner {
    private RadarScanner() {
    }

    public static RadarScanResult scan(RadarScanProfile profile, RadarScanContext context, RadarTargetCache targetCache) {
        return scanBucket(profile, context, targetCache, 1, 0, true);
    }

    public static RadarScanResult scanBucket(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            int scanWindowTicks,
            int scanBucket,
            boolean validateStale
    ) {
        return scanBucket(profile, context, targetCache, scanWindowTicks, scanBucket, validateStale, new HashSet<>());
    }

    public static RadarScanResult scanBucket(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            int scanWindowTicks,
            int scanBucket,
            boolean validateStale,
            Set<TargetKey> seenScratch
    ) {
        return scanBucket(
                profile,
                context,
                targetCache,
                scanWindowTicks,
                scanBucket,
                validateStale,
                seenScratch,
                buildSlicePlan(profile, context));
    }

    public static RadarScanResult scanBucket(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            int scanWindowTicks,
            int scanBucket,
            boolean validateStale,
            Set<TargetKey> seenScratch,
            RadarScanSlicePlan slicePlan
    ) {
        boolean measurePerf = PowerRadarDebugOptions.scanOptimizationLogging();
        long totalStart = measurePerf ? System.nanoTime() : 0L;
        if (profile.range() <= 0) {
            return validateStale ? housekeeping(context, targetCache, totalStart) : emptyScanResult(context, targetCache, totalStart);
        }

        AABB searchBox = slicePlan.searchBox();

        CandidateScanResult candidateResult = scanCandidates(
                profile,
                context,
                targetCache,
                slicePlan,
                measurePerf,
                Math.max(1, scanWindowTicks),
                Math.floorMod(scanBucket, Math.max(1, scanWindowTicks)),
                seenScratch
        );
        seenScratch.clear();

        long staleValidationStart = measurePerf ? System.nanoTime() : 0L;
        RadarStaleValidationResult staleValidationResult = validateStale
                ? targetCache.validateStaleTracks(context.level(), context.gameTime())
                : new RadarStaleValidationResult(0, 0, 0);
        long staleValidationDuration = measurePerf ? System.nanoTime() - staleValidationStart : 0L;

        long totalDuration = measurePerf ? System.nanoTime() - totalStart : 0L;
        RadarScanResult result = new RadarScanResult(
                true,
                0,
                profile.range(),
                context.assemblyFacing(),
                candidateResult.candidateCount(),
                candidateResult.acceptedCount(),
                candidateResult.updatedTrackCount(),
                candidateResult.addedTrackCount(),
                candidateResult.ignoredItemCount(),
                candidateResult.ignoredCategoryCount(),
                staleValidationResult.staleValidatedCount(),
                staleValidationResult.removedDeadOrMissingCount(),
                staleValidationResult.removedExpiredCount(),
                searchBox.getXsize(),
                searchBox.getYsize(),
                searchBox.getZsize(),
                candidateResult.sliceCount(),
                candidateResult.broadQueryCount(),
                candidateResult.typedQueryCount(),
                targetCache.size(),
                candidateResult.getEntitiesDurationNanos(),
                candidateResult.filteringDurationNanos(),
                staleValidationDuration,
                totalDuration
        );
        logOptimizationDebug(context, result);
        return result;
    }

    public static RadarScanSlicePlan buildSlicePlan(RadarScanProfile profile, RadarScanContext context) {
        int scanRange = profile.range();
        AABB searchBox = new AABB(
                context.radarOriginX() - scanRange,
                context.radarOriginY() + profile.verticalMinOffset(),
                context.radarOriginZ() - scanRange,
                context.radarOriginX() + scanRange,
                context.radarOriginY() + profile.verticalMaxOffset(),
                context.radarOriginZ() + scanRange
        );
        double sliceSize = RadarConstants.entityQuerySliceSize();
        List<AABB> slices = new ArrayList<>();
        for (double minX = searchBox.minX; minX < searchBox.maxX; minX += sliceSize) {
            double maxX = Math.min(minX + sliceSize, searchBox.maxX);
            for (double minZ = searchBox.minZ; minZ < searchBox.maxZ; minZ += sliceSize) {
                double maxZ = Math.min(minZ + sliceSize, searchBox.maxZ);
                slices.add(new AABB(minX, searchBox.minY, minZ, maxX, searchBox.maxY, maxZ));
            }
        }
        return new RadarScanSlicePlan(searchBox, List.copyOf(slices));
    }

    public static RadarScanResult housekeeping(RadarScanContext context, RadarTargetCache targetCache) {
        return housekeeping(context, targetCache, PowerRadarDebugOptions.scanOptimizationLogging() ? System.nanoTime() : 0L);
    }

    /** Refreshes acquired tracks directly, without another spatial entity query. */
    public static int refreshTrackedEntities(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache
    ) {
        int[] refreshed = { 0 };
        targetCache.forEachTrack(track -> {
            Entity entity = resolveTrackedEntity(context, track);
            if (entity == null || !entity.isAlive()) {
                return;
            }
            RadarTargetCategory category = RadarTargetClassifier.classify(entity, profile);
            if (category == null || !RadarCoverageFilter.isEntityInCoverage(profile, context, entity)) {
                return;
            }
            updateTrack(context, targetCache, track.key(), entity, category, track);
            refreshed[0]++;
        });
        return refreshed[0];
    }

    private static Entity resolveTrackedEntity(RadarScanContext context, RadarTargetTrack track) {
        if (!context.dimensionId().equals(track.dimensionId())) {
            return null;
        }
        return track.targetUuid() != null
                ? context.level().getEntity(track.targetUuid())
                : context.level().getEntity(track.targetId());
    }

    private static RadarScanResult housekeeping(RadarScanContext context, RadarTargetCache targetCache, long totalStart) {
        boolean measurePerf = PowerRadarDebugOptions.scanOptimizationLogging();
        long staleValidationStart = measurePerf ? System.nanoTime() : 0L;
        RadarStaleValidationResult staleValidationResult = targetCache.validateStaleTracks(context.level(), context.gameTime());
        long staleValidationDuration = measurePerf ? System.nanoTime() - staleValidationStart : 0L;
        long totalDuration = measurePerf ? System.nanoTime() - totalStart : 0L;
        return new RadarScanResult(
                false,
                0,
                0,
                context.assemblyFacing(),
                0,
                0,
                0,
                0,
                0,
                0,
                staleValidationResult.staleValidatedCount(),
                staleValidationResult.removedDeadOrMissingCount(),
                staleValidationResult.removedExpiredCount(),
                0.0,
                0.0,
                0.0,
                0,
                0,
                0,
                targetCache.size(),
                0L,
                0L,
                staleValidationDuration,
                totalDuration
        );
    }

    private static RadarScanResult emptyScanResult(RadarScanContext context, RadarTargetCache targetCache, long totalStart) {
        boolean measurePerf = PowerRadarDebugOptions.scanOptimizationLogging();
        long totalDuration = measurePerf ? System.nanoTime() - totalStart : 0L;
        return new RadarScanResult(
                false,
                0,
                0,
                context.assemblyFacing(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0.0,
                0.0,
                0.0,
                0,
                0,
                0,
                targetCache.size(),
                0L,
                0L,
                0L,
                totalDuration
        );
    }

    private static CandidateScanResult scanCandidates(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            RadarScanSlicePlan slicePlan,
            boolean measurePerf,
            int scanWindowTicks,
            int scanBucket,
            Set<TargetKey> seen
    ) {
        CandidateScanStats stats = new CandidateScanStats();
        seen.clear();
        boolean broadQuery = shouldUseBroadQuery(profile);
        Optional<Class<? extends Entity>> cbcProjectileClass = RadarCbcProjectileCompat.projectileClass();

        int sliceIndex = 0;
        for (AABB slice : slicePlan.slices()) {
            int currentSliceIndex = sliceIndex++;
            stats.sliceCount++;
            if (scanWindowTicks > 1 && currentSliceIndex % scanWindowTicks != scanBucket) {
                continue;
            }
            if (broadQuery) {
                stats.broadQueryCount++;
                long queryStart = measurePerf ? System.nanoTime() : 0L;
                List<Entity> entities = context.level().getEntities((Entity) null, slice, Entity::isAlive);
                stats.getEntitiesDurationNanos += measurePerf ? System.nanoTime() - queryStart : 0L;
                addDeduplicatedCandidates(profile, context, targetCache, seen, stats, entities, measurePerf);
                continue;
            }

            stats.typedQueryCount += collectTypedCandidates(
                    profile,
                    context,
                    targetCache,
                    slice,
                    seen,
                    stats,
                    measurePerf,
                    cbcProjectileClass
            );
        }

        return new CandidateScanResult(
                stats.candidateCount,
                stats.acceptedCount,
                stats.updatedTrackCount,
                stats.addedTrackCount,
                stats.ignoredItemCount,
                stats.ignoredCategoryCount,
                stats.sliceCount,
                stats.broadQueryCount,
                stats.typedQueryCount,
                stats.getEntitiesDurationNanos,
                stats.filteringDurationNanos
        );
    }

    private static boolean shouldUseBroadQuery(RadarScanProfile profile) {
        int typedQueries = 0;
        if (profile.detectPlayers()) {
            typedQueries++;
        }
        if (profile.detectProjectiles()) {
            typedQueries++;
        }
        if (profile.detectPassiveMobs() || profile.detectHostileMobs()) {
            typedQueries++;
        }
        if (profile.detectUnknown()) {
            typedQueries++;
        }
        return typedQueries >= 3;
    }

    private static void logOptimizationDebug(RadarScanContext context, RadarScanResult result) {
        if (!optimizationDebugEnabled()) {
            return;
        }
        PowerRadar.LOGGER.info(
                "[PowerRadar BugReport][RadarScan] radarId={} tick={} range={} box={}x{}x{} slices={} broadQueries={} typedQueries={} candidates={} accepted={} cache={} getEntitiesUs={} filteringUs={} staleValidationUs={}",
                context.radarId(),
                context.gameTime(),
                result.range(),
                formatOneDecimal(result.aabbSizeX()),
                formatOneDecimal(result.aabbSizeY()),
                formatOneDecimal(result.aabbSizeZ()),
                result.aabbSliceCount(),
                result.broadQueryCount(),
                result.typedQueryCount(),
                result.candidateCount(),
                result.acceptedCount(),
                result.cacheSizeAfter(),
                result.getEntitiesDurationNanos() / 1_000L,
                result.filteringDurationNanos() / 1_000L,
                result.staleValidationDurationNanos() / 1_000L
        );
    }

    private static boolean optimizationDebugEnabled() {
        return PowerRadarDebugOptions.scanOptimizationLogging();
    }

    private static String formatOneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static int collectTypedCandidates(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            AABB slice,
            Set<TargetKey> seen,
            CandidateScanStats stats,
            boolean measurePerf,
            Optional<Class<? extends Entity>> cbcProjectileClass
    ) {
        int queryCount = 0;
        if (profile.detectPlayers()) {
            queryCount++;
            long queryStart = measurePerf ? System.nanoTime() : 0L;
            List<Player> entities = context.level().getEntities(EntityTypeTest.forClass(Player.class), slice, Entity::isAlive);
            stats.getEntitiesDurationNanos += measurePerf ? System.nanoTime() - queryStart : 0L;
            addDeduplicatedCandidates(profile, context, targetCache, seen, stats, entities, measurePerf);
        }

        if (profile.detectProjectiles()) {
            queryCount++;
            long vanillaProjectileQueryStart = measurePerf ? System.nanoTime() : 0L;
            List<Projectile> entities = context.level().getEntities(EntityTypeTest.forClass(Projectile.class), slice, Entity::isAlive);
            stats.getEntitiesDurationNanos += measurePerf ? System.nanoTime() - vanillaProjectileQueryStart : 0L;
            addDeduplicatedCandidates(profile, context, targetCache, seen, stats, entities, measurePerf);

            if (cbcProjectileClass.isPresent()) {
                queryCount++;
                long cbcProjectileQueryStart = measurePerf ? System.nanoTime() : 0L;
                List<? extends Entity> cbcProjectiles = context.level().getEntities(
                        EntityTypeTest.forClass(cbcProjectileClass.get()),
                        slice,
                        Entity::isAlive);
                stats.getEntitiesDurationNanos += measurePerf ? System.nanoTime() - cbcProjectileQueryStart : 0L;
                addDeduplicatedCandidates(profile, context, targetCache, seen, stats, cbcProjectiles, measurePerf);
            }
        }

        if (profile.detectPassiveMobs() || profile.detectHostileMobs()) {
            queryCount++;
            long queryStart = measurePerf ? System.nanoTime() : 0L;
            List<Mob> entities = context.level().getEntities(EntityTypeTest.forClass(Mob.class), slice,
                    entity -> entity.isAlive() && isDetectedMobCategory(RadarTargetClassifier.classify(entity, profile)));
            stats.getEntitiesDurationNanos += measurePerf ? System.nanoTime() - queryStart : 0L;
            addDeduplicatedCandidates(profile, context, targetCache, seen, stats, entities, measurePerf);
        }
        if (profile.detectUnknown()) {
            queryCount++;
            long queryStart = measurePerf ? System.nanoTime() : 0L;
            List<AbstractContraptionEntity> entities = context.level().getEntities(
                    EntityTypeTest.forClass(AbstractContraptionEntity.class), slice, Entity::isAlive);
            stats.getEntitiesDurationNanos += measurePerf ? System.nanoTime() - queryStart : 0L;
            addDeduplicatedCandidates(profile, context, targetCache, seen, stats, entities, measurePerf);

            queryCount++;
            queryStart = measurePerf ? System.nanoTime() : 0L;
            List<RadarStructureEntity> radarStructures = context.level().getEntities(
                    EntityTypeTest.forClass(RadarStructureEntity.class), slice, Entity::isAlive);
            stats.getEntitiesDurationNanos += measurePerf ? System.nanoTime() - queryStart : 0L;
            addDeduplicatedCandidates(profile, context, targetCache, seen, stats, radarStructures, measurePerf);
        }
        return queryCount;
    }

    private static boolean isDetectedMobCategory(RadarTargetCategory category) {
        return category == RadarTargetCategory.PASSIVE_MOB || category == RadarTargetCategory.HOSTILE_MOB;
    }

    private static void addDeduplicatedCandidates(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            Set<TargetKey> seen,
            CandidateScanStats stats,
            Iterable<? extends Entity> queriedEntities,
            boolean measurePerf
    ) {
        for (Entity entity : queriedEntities) {
            TargetKey key = TargetKey.entity(context.dimensionId(), entity.getUUID(), entity.getId());
            if (seen.add(key)) {
                stats.candidateCount++;
                long filteringStart = measurePerf ? System.nanoTime() : 0L;
                processCandidate(profile, context, targetCache, stats, entity, key);
                stats.filteringDurationNanos += measurePerf ? System.nanoTime() - filteringStart : 0L;
            }
        }
    }

    private static void processCandidate(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            CandidateScanStats stats,
            Entity entity,
            TargetKey key
    ) {
        if (entity == null || !entity.isAlive()) {
            return;
        }
        if (entity instanceof ItemEntity) {
            stats.ignoredItemCount++;
            return;
        }

        RadarTargetCategory category = RadarTargetClassifier.classify(entity, profile);
        if (category == null) {
            stats.ignoredCategoryCount++;
            return;
        }
        if (!RadarCoverageFilter.isEntityInCoverage(profile, context, entity)) {
            return;
        }

        RadarTargetTrack existingTrack = targetCache.get(key);
        updateTrack(context, targetCache, key, entity, category, existingTrack);
        stats.acceptedCount++;
        if (existingTrack != null) {
            stats.updatedTrackCount++;
        } else {
            stats.addedTrackCount++;
        }
    }

    public static RadarStructure validateStructure(ServerLevel level, BlockPos controllerPos) {
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
        PanelCounts panels = countConnectedBasicPanels(level, processorPos, facing);
        return new RadarStructure(panels.total() > 0, controllerPos, controllerPos, processorPos, facing,
                panels.basicPanelCount(), 0);
    }

    private static PanelCounts countConnectedBasicPanels(ServerLevel level, BlockPos firstPanelPos, Direction facing) {
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
                    current.relative(horizontalStep.getOpposite())
            )) {
                if (visited.size() >= maxPanels) {
                    break;
                }
                if (!visited.contains(next) && isInDirectPanelPlane(firstPanelPos, next, facing)
                        && isCompatibleBasicPanel(level, next, facing)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        return new PanelCounts(visited.size());
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

    public static int calculateRange(RadarScanMode mode, int phasedArrayPanelCount) {
        int groundRange = Math.min(
                PowerRadarCeeConstants.radarBaseRangeBlocks(phasedArrayPanelCount),
                Integer.MAX_VALUE
        );
        if (mode == RadarScanMode.GROUND) {
            return groundRange;
        }
        if (mode == RadarScanMode.SKY) {
            return (int) Math.floor(groundRange * PowerRadarCeeConstants.airRangeMultiplier());
        }
        return groundRange;
    }

    public static int calculateOverviewRange(RadarScanMode mode, int overviewModuleCount) {
        int groundRange = Math.min(
                PowerRadarCeeConstants.overviewRadarBaseRangeBlocks(overviewModuleCount),
                Integer.MAX_VALUE
        );
        if (mode == RadarScanMode.SKY) {
            return (int) Math.floor(groundRange * PowerRadarCeeConstants.airRangeMultiplier());
        }
        return groundRange;
    }

    private record PanelCounts(int basicPanelCount) {

        private int total() {
            return this.basicPanelCount;
        }
    }

    private static void updateTrack(
            RadarScanContext context,
            RadarTargetCache targetCache,
            TargetKey key,
            Entity entity,
            RadarTargetCategory category,
            @Nullable RadarTargetTrack track
    ) {
        Vec3 pos = entity.position();
        Vec3 velocity = displayVelocity(entity, category);
        double width = entity.getBbWidth();
        double height = entity.getBbHeight();
        String displayName = displayName(entity);
        if (isStationaryProjectile(category, track, pos)) {
            velocity = Vec3.ZERO;
        }
        boolean hasVelocity = velocity != null;
        if (track == null) {
            targetCache.put(key, new RadarTargetTrack(
                    key,
                    entity.getUUID(),
                    entity.getId(),
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()),
                    sourceKind(entity, category),
                    displayName,
                    category,
                    context.dimensionId(),
                    pos.x,
                    pos.y,
                    pos.z,
                    hasVelocity ? velocity.x : 0.0,
                    hasVelocity ? velocity.y : 0.0,
                    hasVelocity ? velocity.z : 0.0,
                    hasVelocity,
                    height,
                    width,
                    context.gameTime()
            ));
            return;
        }
        track.update(
                category,
                displayName,
                context.dimensionId(),
                pos.x,
                pos.y,
                pos.z,
                hasVelocity ? velocity.x : 0.0,
                hasVelocity ? velocity.y : 0.0,
                hasVelocity ? velocity.z : 0.0,
                hasVelocity,
                height,
                width,
                context.gameTime()
        );
    }

    private static RadarTargetSourceKind sourceKind(Entity entity, RadarTargetCategory category) {
        if (category != RadarTargetCategory.PROJECTILE) {
            return RadarTargetSourceKind.ENTITY;
        }
        if (RadarCbcProjectileCompat.isAutocannonProjectile(entity)) {
            return RadarTargetSourceKind.CBC_AUTOCANNON_PROJECTILE;
        }
        if (RadarCbcProjectileCompat.isBigCannonProjectile(entity)) {
            return RadarTargetSourceKind.CBC_BIG_CANNON_PROJECTILE;
        }
        return RadarTargetSourceKind.PROJECTILE;
    }

    private static Vec3 displayVelocity(Entity entity, RadarTargetCategory category) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity == null) {
            return Vec3.ZERO;
        }
        if (category != RadarTargetCategory.PROJECTILE) {
            if (entity.onGround() && velocity.y < 0.0D && Math.abs(velocity.y) <= 0.1D) {
                return new Vec3(velocity.x, 0.0, velocity.z);
            }
            return velocity;
        }
        if (entity.onGround() && velocity.lengthSqr() < 0.01D) {
            return Vec3.ZERO;
        }
        return velocity;
    }

    private static boolean isStationaryProjectile(RadarTargetCategory category, @Nullable RadarTargetTrack track, Vec3 pos) {
        if (category != RadarTargetCategory.PROJECTILE || track == null) {
            return false;
        }
        double dx = pos.x - track.x();
        double dy = pos.y - track.y();
        double dz = pos.z - track.z();
        return dx * dx + dy * dy + dz * dz < 0.0001D;
    }

    @Nullable
    private static String displayName(Entity entity) {
        if (entity instanceof Player || entity.hasCustomName()) {
            return entity.getName().getString();
        }
        return null;
    }

    private static final class CandidateScanStats {
        private int candidateCount;
        private int acceptedCount;
        private int updatedTrackCount;
        private int addedTrackCount;
        private int ignoredItemCount;
        private int ignoredCategoryCount;
        private int sliceCount;
        private int broadQueryCount;
        private int typedQueryCount;
        private long getEntitiesDurationNanos;
        private long filteringDurationNanos;
    }

    private record CandidateScanResult(
            int candidateCount,
            int acceptedCount,
            int updatedTrackCount,
            int addedTrackCount,
            int ignoredItemCount,
            int ignoredCategoryCount,
            int sliceCount,
            int broadQueryCount,
            int typedQueryCount,
            long getEntitiesDurationNanos,
            long filteringDurationNanos
    ) {
    }
}
