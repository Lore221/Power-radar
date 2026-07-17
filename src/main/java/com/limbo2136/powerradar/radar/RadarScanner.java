package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.compat.createbigcannons.RadarCbcProjectileCompat;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureObservation;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.limbo2136.powerradar.entity.RadarStructureEntity;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    public static void housekeeping(RadarScanContext context, RadarTargetCache targetCache) {
        targetCache.validateStaleTracks(context.level(), context.gameTime());
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

    static List<Entity> queryCandidates(
            ServerLevel level,
            AABB searchBox,
            Collection<RadarScanProfile> profiles
    ) {
        boolean players = profiles.stream().anyMatch(RadarScanProfile::detectPlayers);
        boolean projectiles = profiles.stream().anyMatch(RadarScanProfile::detectProjectiles);
        boolean mobs = profiles.stream().anyMatch(profile -> profile.detectPassiveMobs() || profile.detectHostileMobs());
        boolean unknown = profiles.stream().anyMatch(RadarScanProfile::detectUnknown);
        int typedQueries = (players ? 1 : 0) + (projectiles ? 1 : 0) + (mobs ? 1 : 0) + (unknown ? 1 : 0);
        if (typedQueries >= 3) {
            return level.getEntities((Entity) null, searchBox, Entity::isAlive);
        }

        Map<UUID, Entity> candidates = new LinkedHashMap<>();
        if (players) {
            level.getEntities(EntityTypeTest.forClass(Player.class), searchBox, Entity::isAlive)
                    .forEach(entity -> candidates.putIfAbsent(entity.getUUID(), entity));
        }
        if (projectiles) {
            level.getEntities(EntityTypeTest.forClass(Projectile.class), searchBox, Entity::isAlive)
                    .forEach(entity -> candidates.putIfAbsent(entity.getUUID(), entity));
            RadarCbcProjectileCompat.projectileClass().ifPresent(type ->
                    level.getEntities(EntityTypeTest.forClass(type), searchBox, Entity::isAlive)
                            .forEach(entity -> candidates.putIfAbsent(entity.getUUID(), entity)));
        }
        if (mobs) {
            level.getEntities(EntityTypeTest.forClass(Mob.class), searchBox, Entity::isAlive)
                    .forEach(entity -> candidates.putIfAbsent(entity.getUUID(), entity));
        }
        if (unknown) {
            level.getEntities(EntityTypeTest.forClass(AbstractContraptionEntity.class), searchBox, Entity::isAlive)
                    .forEach(entity -> candidates.putIfAbsent(entity.getUUID(), entity));
            level.getEntities(EntityTypeTest.forClass(RadarStructureEntity.class), searchBox, Entity::isAlive)
                    .forEach(entity -> candidates.putIfAbsent(entity.getUUID(), entity));
        }
        return List.copyOf(candidates.values());
    }

    static boolean processSharedCandidate(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            Entity entity,
            Set<TargetKey> seen
    ) {
        TargetKey key = TargetKey.entity(context.dimensionId(), entity.getUUID(), entity.getId());
        if (!seen.add(key) || entity instanceof ItemEntity) {
            return false;
        }
        RadarTargetCategory category = RadarTargetClassifier.classify(entity, profile);
        if (category == null || !RadarCoverageFilter.isEntityInCoverage(profile, context, entity)) {
            return false;
        }
        updateTrack(context, targetCache, key, entity, category, targetCache.get(key));
        return true;
    }

    static boolean processSableCandidate(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            SableStructureObservation structure,
            Set<TargetKey> seen
    ) {
        TargetKey key = TargetKey.entity(context.dimensionId(), structure.structureUuid(), -1);
        Vec3 coveragePoint = closestPoint(structure.worldBounds(), context);
        if (!seen.add(key)) {
            return false;
        }
        if (!RadarCoverageFilter.isPointInCoverage(profile, context, coveragePoint)) {
            return false;
        }
        RadarTargetTrack track = targetCache.get(key);
        Vec3 velocity = structure.velocity();
        double height = structure.worldBounds().getYsize();
        double size = Math.max(structure.worldBounds().getXsize(), structure.worldBounds().getZsize());
        if (track == null) {
            targetCache.put(key, new RadarTargetTrack(
                    key,
                    structure.structureUuid(),
                    -1,
                    ResourceLocation.fromNamespaceAndPath("sable", "sublevel"),
                    RadarTargetSourceKind.FUTURE_SABLE_STRUCTURE,
                    structure.displayName(),
                    RadarTargetCategory.SABLE_STRUCTURE,
                    context.dimensionId(),
                    structure.worldOrigin().x,
                    structure.worldOrigin().y,
                    structure.worldOrigin().z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    true,
                    height,
                    size,
                    context.gameTime()));
        } else {
            track.update(
                    RadarTargetCategory.SABLE_STRUCTURE,
                    structure.displayName(),
                    context.dimensionId(),
                    structure.worldOrigin().x,
                    structure.worldOrigin().y,
                    structure.worldOrigin().z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    true,
                    height,
                    size,
                    context.gameTime());
        }
        SableRadarIntegration.markDetected(context.level(), structure, context.gameTime());
        int silhouetteVersion = SableRadarIntegration.silhouetteSnapshot(
                        context.level().getServer(), context.dimensionId(), structure.structureUuid())
                .map(snapshot -> snapshot.version())
                .orElse(0);
        targetCache.get(key).updateSablePresentation(structure.headingDegrees(), silhouetteVersion);
        return true;
    }

    private static Vec3 closestPoint(AABB bounds, RadarScanContext context) {
        return new Vec3(
                clamp(context.radarOriginX(), bounds.minX, bounds.maxX),
                clamp(context.radarOriginY(), bounds.minY, bounds.maxY),
                clamp(context.radarOriginZ(), bounds.minZ, bounds.maxZ));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
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

}
