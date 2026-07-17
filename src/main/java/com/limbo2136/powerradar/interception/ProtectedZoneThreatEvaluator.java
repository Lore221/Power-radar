package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared staged threat evaluation used by both compact protection roots. */
public final class ProtectedZoneThreatEvaluator {
    private ProtectedZoneThreatEvaluator() {
    }

    public static boolean passesInitialBroadPhase(
            ServerLevel level,
            MovingProtectedZone zone,
            TrackedTargetView track,
            double maximumTicks
    ) {
        ProjectileSample sample = sample(level, track);
        return MovingAabbThreatEvaluator.passesInitialBroadPhase(
                zone,
                sample.position(),
                sample.velocity(),
                sample.radius(),
                maximumTicks);
    }

    public static Evaluation evaluate(
            ServerLevel level,
            MovingProtectedZone zone,
            TrackedTargetView track,
            double maximumTicks
    ) {
        ProjectileSample sample = sample(level, track);
        ShellAlarmCbcCompat.Ballistics ballistics = ShellAlarmCbcCompat.ballistics(sample.entity());
        boolean dangerous = MovingAabbThreatEvaluator.threatens(
                zone.bounds(),
                zone.velocity(),
                zone.acceleration(),
                sample.position(),
                sample.velocity(),
                sample.radius(),
                zone.safetyMarginPerSide(),
                ballistics,
                maximumTicks);
        return new Evaluation(dangerous, sample.position(), sample.velocity(), ballistics);
    }

    private static ProjectileSample sample(ServerLevel level, TrackedTargetView track) {
        Entity entity = track.targetUuid() == null ? null : level.getEntity(track.targetUuid());
        Entity liveEntity = entity != null && entity.isAlive() ? entity : null;
        Vec3 position = liveEntity == null ? track.position() : liveEntity.position();
        Vec3 velocity = liveEntity == null ? track.velocity() : liveEntity.getDeltaMovement();
        return new ProjectileSample(position, velocity, projectileRadius(track, liveEntity), liveEntity);
    }

    private static double projectileRadius(TrackedTargetView track, @Nullable Entity projectile) {
        if (projectile != null) {
            AABB bounds = projectile.getBoundingBox();
            return Math.max(0.05D,
                    Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * 0.5D);
        }
        double size = track.approximateSize();
        return Double.isFinite(size) ? Math.max(0.05D, size * 0.5D) : 0.05D;
    }

    public record Evaluation(
            boolean dangerous,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics
    ) {
    }

    private record ProjectileSample(
            Vec3 position,
            Vec3 velocity,
            double radius,
            @Nullable Entity entity
    ) {
    }
}
