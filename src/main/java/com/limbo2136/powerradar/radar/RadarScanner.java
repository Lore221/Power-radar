package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.compat.createbigcannons.RadarCbcProjectileCompat;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureObservation;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.EwSystemManager;
import com.limbo2136.powerradar.entity.RadarStructureEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RadarScanner {
    private RadarScanner() {
    }

    public static void housekeeping(RadarScanContext context, RadarTargetCache targetCache) {
        targetCache.validateStaleTracks(context.level(), context.gameTime());
    }

    /** Обновляет уже захваченные треки напрямую, без повторного пространственного запроса сущностей. */
    public static int refreshTrackedEntities(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            RadarCoverageFilter.PreparedCoverage coverage,
            RadarSurfaceHeightCache surfaceHeights
    ) {
        int refreshed = 0;
        for (RadarTargetTrack track : targetCache.tracks()) {
            Entity entity = resolveTrackedEntity(context, track);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            RadarTargetCategory category = RadarTargetClassifier.classify(entity);
            // UNKNOWN подтверждается только отдельным редким проходом. Здесь проверяем
            // уже известную сущность лишь для быстрого возврата в обычную категорию.
            if (category == null || category == RadarTargetCategory.UNKNOWN || !profile.detects(category)
                    || !coverage.isEntityInCoverage(entity, surfaceHeights)) {
                continue;
            }
            updateTrack(context, targetCache, track.key(), entity, category, track);
            refreshed++;
        }
        return refreshed;
    }

    private static Entity resolveTrackedEntity(RadarScanContext context, RadarTargetTrack track) {
        if (!context.dimensionId().equals(track.dimensionId())) {
            return null;
        }
        return track.targetUuid() != null
                ? context.level().getEntity(track.targetUuid())
                : context.level().getEntity(track.targetId());
    }

    static List<RadarCandidate> queryCandidates(
            ServerLevel level,
            AABB searchBox,
            Collection<RadarScanProfile> profiles
    ) {
        boolean players = false;
        boolean projectiles = false;
        boolean mobs = false;
        boolean radars = false;
        for (RadarScanProfile profile : profiles) {
            // Невидимые игроки и мобы образуют UNKNOWN независимо от маски их исходной категории.
            players |= profile.detectPlayers() || profile.detectUnknown();
            projectiles |= profile.detectProjectiles();
            mobs |= profile.detectPassiveMobs() || profile.detectHostileMobs() || profile.detectUnknown();
            radars |= profile.detectRadars();
        }
        int typedQueries = (players ? 1 : 0) + (projectiles ? 1 : 0) + (mobs ? 1 : 0) + (radars ? 1 : 0);
        // При трёх и более группах один широкий запрос дешевле нескольких почти одинаковых выборок.
        if (typedQueries >= 3) {
            return classifyCandidates(level.getEntities((Entity) null, searchBox, Entity::isAlive));
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
        if (radars) {
            level.getEntities(EntityTypeTest.forClass(RadarStructureEntity.class), searchBox, Entity::isAlive)
                    .forEach(entity -> candidates.putIfAbsent(entity.getUUID(), entity));
        }
        return classifyCandidates(candidates.values());
    }

    private static List<RadarCandidate> classifyCandidates(Collection<Entity> entities) {
        List<RadarCandidate> candidates = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            if (!(entity instanceof RadarStructureEntity)
                    && SableRadarIntegration.isEntityOnStructure(entity)) {
                continue;
            }
            RadarTargetCategory category = RadarTargetClassifier.classify(entity);
            if (category != null) {
                candidates.add(new RadarCandidate(entity, category));
            }
        }
        return candidates;
    }

    static boolean processSharedCandidate(
            RadarScanProfile profile,
            RadarScanContext context,
            RadarTargetCache targetCache,
            RadarCandidate candidate,
            Set<TargetKey> seen,
            RadarCoverageFilter.PreparedCoverage coverage,
            RadarSurfaceHeightCache surfaceHeights
    ) {
        Entity entity = candidate.entity();
        RadarTargetCategory category = candidate.category();
        if (!profile.detects(category)) {
            return false;
        }
        TargetKey key = TargetKey.entity(context.dimensionId(), entity.getUUID(), entity.getId());
        if (!seen.add(key)) {
            return false;
        }
        if (!coverage.isEntityInCoverage(entity, surfaceHeights)) {
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
            Set<TargetKey> seen,
            RadarCoverageFilter.PreparedCoverage coverage,
            RadarSurfaceHeightCache surfaceHeights
    ) {
        TargetKey key = TargetKey.entity(context.dimensionId(), structure.structureUuid(), -1);
        Vec3 coveragePoint = closestPoint(structure.worldBounds(), context);
        boolean silhouetteSuppressed = EwSystemManager.suppressesSilhouette(
                context.level().getServer(), context.dimensionId(), structure.structureUuid());
        RadarTargetCategory category = silhouetteSuppressed
                ? RadarTargetCategory.UNKNOWN
                : RadarTargetCategory.SABLE_STRUCTURE;
        if (!profile.detects(category)) {
            return false;
        }
        if (!seen.add(key)) {
            return false;
        }
        if (!coverage.isPointInCoverage(coveragePoint, surfaceHeights)) {
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
                    silhouetteSuppressed ? null : structure.displayName(),
                    category,
                    context.dimensionId(),
                    structure.geometricCenter().x,
                    structure.geometricCenter().y,
                    structure.geometricCenter().z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    true,
                    height,
                    size,
                    context.gameTime()));
        } else {
            track.update(
                    category,
                    silhouetteSuppressed ? null : structure.displayName(),
                    context.dimensionId(),
                    structure.geometricCenter().x,
                    structure.geometricCenter().y,
                    structure.geometricCenter().z,
                    velocity.x,
                    velocity.y,
                    velocity.z,
                    true,
                    height,
                    size,
                    context.gameTime());
        }
        int silhouetteVersion = 0;
        if (!silhouetteSuppressed) {
            SableRadarIntegration.markDetected(context.level(), structure, context.gameTime());
            silhouetteVersion = SableRadarIntegration.silhouetteSnapshot(
                            context.level().getServer(), context.dimensionId(), structure.structureUuid())
                    .map(snapshot -> snapshot.version())
                    .orElse(0);
        }
        // Pose3dc.position() — точное мировое положение rotation point. Передаём его
        // относительно наблюдаемого центра, чтобы клиент не восстанавливал ось по yaw.
        float rotationPointOffsetX = (float) (structure.worldRotationPointX() - structure.geometricCenter().x);
        float rotationPointOffsetZ = (float) (structure.worldRotationPointZ() - structure.geometricCenter().z);
        if (PowerRadarDebugOptions.sablePoseLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][SablePose][Server] tick={} structure={} center_x={} center_z={} heading={} local_pivot_x={} local_pivot_z={} world_pivot_x={} world_pivot_z={} sent_offset_x={} sent_offset_z={}",
                    context.gameTime(), structure.structureUuid(),
                    structure.geometricCenter().x, structure.geometricCenter().z, structure.headingDegrees(),
                    structure.localRotationPointX(), structure.localRotationPointZ(),
                    structure.worldRotationPointX(), structure.worldRotationPointZ(),
                    rotationPointOffsetX, rotationPointOffsetZ);
        }
        targetCache.get(key).updateSablePresentation(
                silhouetteSuppressed ? 0.0F : structure.headingDegrees(),
                silhouetteSuppressed ? 0.0F : rotationPointOffsetX,
                silhouetteSuppressed ? 0.0F : rotationPointOffsetZ,
                silhouetteVersion);
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

    record RadarCandidate(Entity entity, RadarTargetCategory category) {
    }

    private static void updateTrack(
            RadarScanContext context,
            RadarTargetCache targetCache,
            TargetKey key,
            Entity entity,
            RadarTargetCategory category,
            @Nullable RadarTargetTrack track
    ) {
        // Все кинематические величины снимка остаются в блоках, блоках/тик и блоках/тик².
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
