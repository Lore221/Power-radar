package com.limbo2136.powerradar.targeting;

import com.limbo2136.powerradar.api.target.TargetClassification;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureObservation;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Обновляет устойчивый радарный снимок текущей позицией загруженной цели. */
public final class LiveTrackedTargetResolver {
    private LiveTrackedTargetResolver() {
    }

    public static TrackedTargetView resolve(ServerLevel level, TrackedTargetView track, long gameTime) {
        if (!level.dimension().location().equals(track.dimensionId()) || track.targetUuid() == null) {
            return track;
        }
        if (track.sourceType() == TargetSourceType.STRUCTURE) {
            return SableRadarIntegration.loadedStructure(level, track.targetUuid())
                    .<TrackedTargetView>map(observation -> fromSable(track, observation, gameTime))
                    .orElse(track);
        }
        Entity entity = level.getEntity(track.targetUuid());
        return entity == null || !entity.isAlive()
                ? track
                : new EntityTargetView(track, entity, gameTime);
    }

    private static TrackedTargetView fromSable(
            TrackedTargetView track,
            SableStructureObservation observation,
            long gameTime
    ) {
        double height = Math.max(0.1D, observation.worldBounds().getYsize());
        Vec3 targetingBase = observation.worldOrigin().subtract(0.0D, height * 0.5D, 0.0D);
        return new AdjustedTargetView(
                track,
                targetingBase,
                observation.velocity(),
                true,
                track.acceleration(),
                track.hasAcceleration(),
                gameTime,
                gameTime,
                height);
    }

    private record AdjustedTargetView(
            TrackedTargetView fallback,
            Vec3 position,
            Vec3 velocity,
            boolean hasVelocity,
            Vec3 acceleration,
            boolean hasAcceleration,
            long lastSeenGameTime,
            long lastConfirmedAliveGameTime,
            double boundingHeight
    ) implements TrackedTargetView {
        @Override
        public UUID targetUuid() {
            return this.fallback.targetUuid();
        }

        @Override
        public int targetId() {
            return this.fallback.targetId();
        }

        @Override
        public ResourceLocation entityTypeId() {
            return this.fallback.entityTypeId();
        }

        @Override
        public TargetSourceType sourceType() {
            return this.fallback.sourceType();
        }

        @Override
        public String displayName() {
            return this.fallback.displayName();
        }

        @Override
        public TargetClassification classification() {
            return this.fallback.classification();
        }

        @Override
        public ResourceLocation dimensionId() {
            return this.fallback.dimensionId();
        }

        @Override
        public long firstSeenGameTime() {
            return this.fallback.firstSeenGameTime();
        }

        @Override
        public double approximateSize() {
            return this.fallback.approximateSize();
        }
    }

    private record EntityTargetView(
            TrackedTargetView fallback,
            Entity entity,
            long gameTime
    ) implements TrackedTargetView {
        @Override
        public UUID targetUuid() {
            return this.fallback.targetUuid();
        }

        @Override
        public int targetId() {
            return this.entity.getId();
        }

        @Override
        public ResourceLocation entityTypeId() {
            return this.fallback.entityTypeId();
        }

        @Override
        public TargetSourceType sourceType() {
            return this.fallback.sourceType();
        }

        @Override
        public String displayName() {
            return this.fallback.displayName();
        }

        @Override
        public TargetClassification classification() {
            return this.fallback.classification();
        }

        @Override
        public ResourceLocation dimensionId() {
            return this.entity.level().dimension().location();
        }

        @Override
        public Vec3 position() {
            return this.entity.position();
        }

        @Override
        public Vec3 velocity() {
            return this.entity.getDeltaMovement();
        }

        @Override
        public boolean hasVelocity() {
            return true;
        }

        @Override
        public Vec3 acceleration() {
            return this.fallback.acceleration();
        }

        @Override
        public boolean hasAcceleration() {
            return this.fallback.hasAcceleration();
        }

        @Override
        public long firstSeenGameTime() {
            return this.fallback.firstSeenGameTime();
        }

        @Override
        public long lastSeenGameTime() {
            return this.gameTime;
        }

        @Override
        public long lastConfirmedAliveGameTime() {
            return this.gameTime;
        }

        @Override
        public double boundingHeight() {
            return Math.max(0.1D, this.entity.getBbHeight());
        }

        @Override
        public double approximateSize() {
            return Math.max(
                    this.fallback.approximateSize(),
                    Math.max(this.entity.getBbWidth(), this.entity.getBbHeight()));
        }
    }
}
