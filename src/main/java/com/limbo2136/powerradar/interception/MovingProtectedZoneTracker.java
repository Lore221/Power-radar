package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureGeometry;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureMotion;
import com.limbo2136.powerradar.compat.aeronautics.SableStructurePose;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Один раз определяет принадлежность блока Sable, затем измеряет движение только этой структуры.
 * Новый экземпляр блочной сущности выполняет новое определение контейнера.
 */
public final class MovingProtectedZoneTracker {
    private boolean placementInitialized;
    @Nullable private UUID structureUuid;
    private Vec3 latestVelocity = Vec3.ZERO;
    private boolean latestVelocityInitialized;
    private Vec3 previousVelocity = Vec3.ZERO;
    private Vec3 acceleration = Vec3.ZERO;
    private long previousGameTime = Long.MIN_VALUE;
    private boolean velocityInitialized;
    private boolean accelerationInitialized;
    @Nullable private AABB cachedLocalBounds;
    @Nullable private AABB cachedStructureBounds;
    @Nullable private Vec3 cachedLocalCenter;
    @Nullable private Vec3 cachedGeometryOrigin;
    private long nextGeometryRefreshGameTime = Long.MIN_VALUE;

    @Nullable
    public MovingProtectedZone broadPhaseZone(
            ServerLevel level,
            BlockPos blockPos,
            AABB stationaryBounds,
            double sableTotalExpansionPercent
    ) {
        // Быстрый этап использует кэшированную геометрию либо временную малую зону вокруг блока.
        initializePlacement(level, blockPos);
        if (this.structureUuid == null) {
            return new MovingProtectedZone(
                    stationaryBounds,
                    Vec3.ZERO,
                    Vec3.ZERO,
                    level.getGameTime(),
                    null,
                    0.0D);
        }
        long gameTime = level.getGameTime();
        double safetyMargin = MovingProtectedZone.safetyMarginPerSide(sableTotalExpansionPercent);
        if (this.cachedLocalBounds == null
                || this.cachedStructureBounds == null
                || this.cachedLocalCenter == null
                || this.cachedGeometryOrigin == null) {
            Vec3 blockWorldPosition = RadarWorldPoseResolver.worldPosition(
                    level, blockPos, Vec3.atCenterOf(blockPos));
            return new MovingProtectedZone(
                    new AABB(blockWorldPosition, blockWorldPosition).inflate(0.5D),
                    this.latestVelocityInitialized ? this.latestVelocity : Vec3.ZERO,
                    this.accelerationInitialized ? this.acceleration : Vec3.ZERO,
                    gameTime,
                    this.structureUuid,
                    safetyMargin);
        }
        Vec3 worldOrigin = SableRadarIntegration
                .loadedStructureOrigin(level, this.structureUuid, this.cachedLocalCenter)
                .orElse(null);
        if (worldOrigin == null) {
            resetMotion();
            return null;
        }
        AABB worldBounds = this.cachedStructureBounds.move(worldOrigin.subtract(this.cachedGeometryOrigin));
        return new MovingProtectedZone(
                worldBounds,
                this.latestVelocityInitialized ? this.latestVelocity : Vec3.ZERO,
                this.accelerationInitialized ? this.acceleration : Vec3.ZERO,
                gameTime,
                this.structureUuid,
                safetyMargin);
    }

    @Nullable
    public MovingProtectedZone refreshGeometryIfDue(
            ServerLevel level,
            MovingProtectedZone broadPhaseZone,
            double sableTotalExpansionPercent
    ) {
        // Блоки перечитываются не чаще интервала, но пространственная поза обновляется для каждого снимка.
        if (!broadPhaseZone.onSable() || this.structureUuid == null) {
            return broadPhaseZone;
        }
        long gameTime = level.getGameTime();
        if (this.cachedLocalBounds == null
                || this.cachedStructureBounds == null
                || this.cachedLocalCenter == null
                || this.cachedGeometryOrigin == null
                || gameTime >= this.nextGeometryRefreshGameTime) {
            SableStructureGeometry geometry = SableRadarIntegration
                    .loadedStructureGeometry(level, this.structureUuid)
                    .orElse(null);
            if (geometry == null) {
                resetMotion();
                return null;
            }
            this.cachedLocalBounds = geometry.localBounds();
            this.cachedStructureBounds = geometry.worldBounds();
            this.cachedLocalCenter = geometry.localCenter();
            this.cachedGeometryOrigin = geometry.worldOrigin();
            this.nextGeometryRefreshGameTime = gameTime
                    + SableRadarIntegration.geometryRefreshIntervalTicks();
        }
        SableStructurePose pose = SableRadarIntegration
                .loadedStructurePose(
                        level,
                        this.structureUuid,
                        this.cachedLocalBounds,
                        this.cachedLocalCenter)
                .orElse(null);
        if (pose == null) {
            resetMotion();
            return null;
        }
        this.cachedStructureBounds = pose.worldBounds();
        this.cachedGeometryOrigin = pose.worldOrigin();
        return new MovingProtectedZone(
                pose.worldBounds(),
                broadPhaseZone.velocity(),
                this.accelerationInitialized ? this.acceleration : Vec3.ZERO,
                gameTime,
                this.structureUuid,
                MovingProtectedZone.safetyMarginPerSide(sableTotalExpansionPercent));
    }

    @Nullable
    public MovingProtectedZone sampleVelocity(ServerLevel level, MovingProtectedZone zone) {
        if (!zone.onSable() || this.structureUuid == null || this.cachedLocalCenter == null) {
            return zone;
        }
        SableStructureMotion motion = SableRadarIntegration
                .loadedStructureMotion(level, this.structureUuid, this.cachedLocalCenter)
                .orElse(null);
        if (motion == null) {
            resetMotion();
            return null;
        }
        long gameTime = level.getGameTime();
        Vec3 velocity = motion.velocity();
        this.latestVelocity = velocity;
        this.latestVelocityInitialized = true;
        return new MovingProtectedZone(
                zone.bounds(),
                velocity,
                Vec3.ZERO,
                gameTime,
                this.structureUuid,
                zone.safetyMarginPerSide());
    }

    public MovingProtectedZone completeMotionSample(MovingProtectedZone zone) {
        if (!zone.onSable()) {
            return zone;
        }
        long gameTime = zone.sampleGameTime();
        Vec3 velocity = zone.velocity();
        // Ускорение выводится из последовательных снимков и сбрасывается после большого разрыва времени.
        boolean reset = this.previousGameTime == Long.MIN_VALUE
                || gameTime <= this.previousGameTime
                || gameTime - this.previousGameTime
                        > SableRadarIntegration.geometryRefreshIntervalTicks();
        if (reset) {
            this.acceleration = Vec3.ZERO;
            this.velocityInitialized = false;
            this.accelerationInitialized = false;
        } else if (this.velocityInitialized) {
            double elapsedTicks = gameTime - this.previousGameTime;
            Vec3 sampledAcceleration = clampAcceleration(
                    velocity.subtract(this.previousVelocity).scale(1.0D / elapsedTicks));
            this.acceleration = this.accelerationInitialized
                    ? lerp(this.acceleration, sampledAcceleration, 0.65D)
                    : sampledAcceleration;
            this.accelerationInitialized = true;
        }
        this.previousVelocity = velocity;
        this.velocityInitialized = true;
        this.previousGameTime = gameTime;
        return new MovingProtectedZone(
                zone.bounds(),
                velocity,
                this.accelerationInitialized ? this.acceleration : Vec3.ZERO,
                gameTime,
                this.structureUuid,
                zone.safetyMarginPerSide());
    }

    public boolean placementInitialized() {
        return this.placementInitialized;
    }

    public boolean onSable() {
        return this.placementInitialized && this.structureUuid != null;
    }

    @Nullable
    public UUID structureUuid() {
        return this.structureUuid;
    }

    private void initializePlacement(ServerLevel level, BlockPos blockPos) {
        if (this.placementInitialized) {
            return;
        }
        this.structureUuid = SableRadarIntegration.containingStructureUuid(level, blockPos).orElse(null);
        this.placementInitialized = true;
    }

    private void resetMotion() {
        this.latestVelocity = Vec3.ZERO;
        this.latestVelocityInitialized = false;
        this.previousVelocity = Vec3.ZERO;
        this.acceleration = Vec3.ZERO;
        this.previousGameTime = Long.MIN_VALUE;
        this.velocityInitialized = false;
        this.accelerationInitialized = false;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double factor) {
        return from.add(to.subtract(from).scale(factor));
    }

    private static Vec3 clampAcceleration(Vec3 acceleration) {
        double limit = 0.25D;
        return new Vec3(
                Math.clamp(acceleration.x, -limit, limit),
                Math.clamp(acceleration.y, -limit, limit),
                Math.clamp(acceleration.z, -limit, limit));
    }
}
