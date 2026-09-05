package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.targeting.LinearDragTrajectory;
import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Сопровождение одной уже подтверждённой угрозы, независимо от снимков радара. */
final class InterceptionThreatLifetime {
    private static final long MISSING_GRACE_TICKS = 20L;
    private static final long MOVING_PREDICTION_INTERVAL = 5L;
    private long missingSince = Long.MIN_VALUE;
    private long predictedAt = Long.MIN_VALUE;
    private double deadline = Double.POSITIVE_INFINITY;
    private boolean closed;
    private boolean active;
    private String stateReason = "unobserved";
    private String lastLoggedReason = "";
    private long lastLoggedTick = Long.MIN_VALUE;
    @Nullable private DebugObservation debugObservation;
    @Nullable private Vec3 previousRelativePosition;
    @Nullable private Vec3 predictionPosition;
    @Nullable private Vec3 predictionVelocity;
    @Nullable private AABB predictionBounds;
    @Nullable private ShellAlarmCbcCompat.Ballistics predictionBallistics;

    boolean active() { return this.active; }
    boolean closed() { return this.closed; }
    String stateReason() { return this.stateReason; }
    double predictedDeadline() { return this.deadline; }
    long predictionTick() { return this.predictedAt; }
    @Nullable DebugObservation debugObservation() { return this.debugObservation; }

    boolean shouldLog(long gameTime, String reason) {
        boolean changed = !reason.equals(this.lastLoggedReason);
        if (!changed && (this.closed || gameTime - this.lastLoggedTick < 5L)) return false;
        this.lastLoggedReason = reason;
        this.lastLoggedTick = gameTime;
        return true;
    }
    double remainingTicks(long gameTime) { return this.active ? Math.max(0.0D, this.deadline - gameTime) : 0.0D; }

    /** Отсутствие в загруженном мире не равно смерти; огонь при этом запрещён. */
    boolean missing(long gameTime) {
        this.stateReason = "entity-unavailable";
        this.active = false;
        this.previousRelativePosition = null;
        this.predictedAt = Long.MIN_VALUE;
        if (this.missingSince == Long.MIN_VALUE) this.missingSince = gameTime;
        return gameTime - this.missingSince > MISSING_GRACE_TICKS;
    }

    void observe(long gameTime, MovingProtectedZone zone, Vec3 position, Vec3 velocity,
            double radius, boolean inGround, ShellAlarmCbcCompat.Ballistics ballistics) {
        this.missingSince = Long.MIN_VALUE;
        if (this.closed) return;
        AABB bounds = MovingAabbThreatEvaluator.protectedBounds(
                zone.bounds(), radius, zone.safetyMarginPerSide());
        double elapsed = Math.max(0L, gameTime - zone.sampleGameTime());
        Vec3 displacement = zone.velocity().scale(elapsed)
                .add(zone.acceleration().scale(0.5D * elapsed * elapsed));
        AABB currentBounds = bounds.move(displacement);
        Vec3 relativePosition = position.subtract(currentBounds.getCenter());
        AABB centered = currentBounds.move(currentBounds.getCenter().scale(-1.0D));
        boolean boundaryTested = !zone.onSable() || elapsed == 0.0D;
        boolean inside = centered.contains(relativePosition);
        boolean crossed = boundaryTested && this.previousRelativePosition != null
                && centered.clip(this.previousRelativePosition, relativePosition).isPresent();
        if (zone.onSable() && PowerRadarDebugOptions.sableInterceptionDebugLogging()) {
            this.debugObservation = new DebugObservation(gameTime, currentBounds, position, velocity,
                    radius, this.previousRelativePosition, inside, crossed, boundaryTested);
        }
        // Закрываем по наблюдаемому положению, не по истечению старого прогноза.
        // Для Sable граница проверяется только с актуальной геометрией носителя.
        if (boundaryTested) {
            if (inGround || inside || crossed) {
                this.stateReason = inGround ? "closed-in-ground"
                        : inside ? "closed-inside-zone" : "closed-crossed-zone";
                this.closed = true;
                this.active = false;
                return;
            }
            this.previousRelativePosition = relativePosition;
        } else if (inGround) {
            this.stateReason = "closed-in-ground";
            this.closed = true;
            this.active = false;
            return;
        }
        boolean refresh = this.predictedAt == Long.MIN_VALUE
                || !ballistics.equals(this.predictionBallistics)
                || (zone.onSable() && gameTime - this.predictedAt >= MOVING_PREDICTION_INTERVAL)
                || (!zone.onSable() && !bounds.equals(this.predictionBounds))
                || this.deadline <= gameTime
                || !followsPrediction(position, velocity, ballistics, gameTime);
        if (refresh) {
            Vec3 currentZoneVelocity = zone.velocity().add(zone.acceleration().scale(elapsed));
            double entry = MovingAabbThreatEvaluator.firstEntryTicks(currentBounds, currentZoneVelocity,
                    zone.acceleration(), position, velocity, ballistics,
                    PowerRadarCeeConstants.SHELL_ALARM_MAX_SIMULATION_TICKS);
            this.deadline = gameTime + entry;
            this.predictedAt = gameTime;
            this.predictionPosition = position;
            this.predictionVelocity = velocity;
            this.predictionBounds = bounds;
            this.predictionBallistics = ballistics;
        }
        this.active = Double.isFinite(this.deadline) && this.deadline > gameTime;
        this.stateReason = this.active ? "active"
                : Double.isFinite(this.deadline) ? "predicted-entry-now" : "no-future-entry";
    }

    /** Именно использованные границы и результаты проверки, без повторного прогноза для лога. */
    record DebugObservation(long sampleTick, AABB effectiveWorldBounds, Vec3 projectilePosition,
            Vec3 projectileVelocity, double projectileRadius, @Nullable Vec3 previousRelativePosition,
            boolean inside, boolean sweptCrossing, boolean boundaryTested) {
    }

    private boolean followsPrediction(Vec3 position, Vec3 velocity,
            ShellAlarmCbcCompat.Ballistics ballistics, long gameTime) {
        if (this.predictionPosition == null || this.predictionVelocity == null) return false;
        long ticks = gameTime - this.predictedAt;
        if (ballistics.quadraticDrag()) return ticks < MOVING_PREDICTION_INTERVAL;
        if (ballistics.drag() > 1.0E-6D && !LinearDragTrajectory.supported(ballistics.drag())) return false;
        Vec3 expectedPosition;
        Vec3 expectedVelocity;
        if (ballistics.drag() <= 1.0E-6D) {
            expectedPosition = this.predictionPosition.add(this.predictionVelocity.scale(ticks))
                    .add(0.0D, -0.5D * ballistics.gravity() * ticks * ticks, 0.0D);
            expectedVelocity = this.predictionVelocity.add(0.0D, -ballistics.gravity() * ticks, 0.0D);
        } else {
            expectedPosition = LinearDragTrajectory.positionAfterTicks(this.predictionPosition,
                    this.predictionVelocity, ballistics.gravity(), ballistics.drag(), ticks);
            expectedVelocity = LinearDragTrajectory.velocityAfterTicks(this.predictionVelocity,
                    ballistics.gravity(), ballistics.drag(), ticks);
        }
        return expectedPosition.distanceToSqr(position) < 0.01D
                && expectedVelocity.distanceToSqr(velocity) < 1.0E-4D;
    }
}
