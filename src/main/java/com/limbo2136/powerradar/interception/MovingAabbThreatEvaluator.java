package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import com.limbo2136.powerradar.targeting.LinearDragTrajectory;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Three-stage projectile threat test for a translating protected AABB. */
public final class MovingAabbThreatEvaluator {
    private static final double COARSE_STEP_TICKS = 4.0D;
    private static final double REFINEMENT_STEP_TICKS = 0.25D;
    private static final double MOTION_EPSILON_SQR = 1.0E-8D;

    private MovingAabbThreatEvaluator() {
    }

    public static boolean threatens(
            AABB shipBounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            double projectileRadius,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double maximumTicks
    ) {
        return threatens(
                shipBounds,
                shipVelocity,
                shipAcceleration,
                projectilePosition,
                projectileVelocity,
                projectileRadius,
                0.05D,
                ballistics,
                maximumTicks);
    }

    public static boolean threatens(
            AABB shipBounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            double projectileRadius,
            double safetyMarginPerSide,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double maximumTicks
    ) {
        if (maximumTicks <= 0.0D) {
            return false;
        }
        AABB protectedBounds = protectedBounds(shipBounds, projectileRadius, safetyMarginPerSide);
        if (protectedBounds.contains(projectilePosition)) {
            return true;
        }
        if (projectileVelocity.lengthSqr() < MOTION_EPSILON_SQR) {
            return false;
        }
        Vec3 center = protectedBounds.getCenter();
        double sphereRadius = Math.sqrt(
                square(protectedBounds.getXsize())
                        + square(protectedBounds.getYsize())
                        + square(protectedBounds.getZsize())) * 0.5D;
        if (!passesBroadPhase(
                center,
                sphereRadius,
                shipVelocity,
                shipAcceleration,
                projectilePosition,
                projectileVelocity,
                ballistics,
                maximumTicks)) {
            return false;
        }
        if (!ballistics.quadraticDrag()
                && (ballistics.drag() <= 1.0E-6D || LinearDragTrajectory.supported(ballistics.drag()))) {
            return evaluateAnalytic(
                    protectedBounds,
                    center,
                    sphereRadius,
                    shipVelocity,
                    shipAcceleration,
                    projectilePosition,
                    projectileVelocity,
                    ballistics,
                    maximumTicks);
        }
        return evaluateSimulated(
                protectedBounds,
                center,
                sphereRadius,
                shipVelocity,
                shipAcceleration,
                projectilePosition,
                projectileVelocity,
                ballistics,
                maximumTicks);
    }

    static AABB protectedBounds(AABB bounds, double projectileRadius) {
        return protectedBounds(bounds, projectileRadius, 0.05D);
    }

    static AABB protectedBounds(AABB bounds, double projectileRadius, double safetyMarginPerSide) {
        double radius = Double.isFinite(projectileRadius) ? Math.max(0.0D, projectileRadius) : 0.0D;
        double margin = Double.isFinite(safetyMarginPerSide)
                ? Math.max(0.0D, safetyMarginPerSide)
                : 0.0D;
        return bounds.inflate(
                bounds.getXsize() * margin + radius,
                bounds.getYsize() * margin + radius,
                bounds.getZsize() * margin + radius);
    }

    private static boolean passesBroadPhase(
            Vec3 center,
            double radius,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double maximumTicks
    ) {
        boolean accelerating = horizontalLengthSqr(shipAcceleration) > MOTION_EPSILON_SQR;
        boolean curvedRelativePath = ballistics.quadraticDrag()
                || (ballistics.drag() > 1.0E-6D && horizontalLengthSqr(shipVelocity) > MOTION_EPSILON_SQR);
        if (accelerating || curvedRelativePath) {
            return true;
        }
        double relativeX = projectilePosition.x - center.x;
        double relativeZ = projectilePosition.z - center.z;
        double velocityX = projectileVelocity.x - shipVelocity.x;
        double velocityZ = projectileVelocity.z - shipVelocity.z;
        double speedSqr = velocityX * velocityX + velocityZ * velocityZ;
        if (speedSqr < MOTION_EPSILON_SQR) {
            return relativeX * relativeX + relativeZ * relativeZ <= radius * radius;
        }
        double closestTicks = Math.clamp(
                -(relativeX * velocityX + relativeZ * velocityZ) / speedSqr,
                0.0D,
                maximumTicks);
        double closestX = relativeX + velocityX * closestTicks;
        double closestZ = relativeZ + velocityZ * closestTicks;
        return closestX * closestX + closestZ * closestZ <= radius * radius;
    }

    private static boolean evaluateAnalytic(
            AABB protectedBounds,
            Vec3 center,
            double sphereRadius,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 initialPosition,
            Vec3 initialVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double maximumTicks
    ) {
        double startTicks = 0.0D;
        Vec3 start = relativePosition(
                analyticPosition(initialPosition, initialVelocity, ballistics, startTicks),
                shipVelocity,
                shipAcceleration,
                startTicks);
        if (protectedBounds.contains(start)) {
            return true;
        }
        while (startTicks < maximumTicks) {
            double endTicks = Math.min(maximumTicks, startTicks + COARSE_STEP_TICKS);
            Vec3 end = relativePosition(
                    analyticPosition(initialPosition, initialVelocity, ballistics, endTicks),
                    shipVelocity,
                    shipAcceleration,
                    endTicks);
            double curveMargin = (ballistics.gravity() + shipAcceleration.length())
                    * (endTicks - startTicks) * (endTicks - startTicks) * 0.125D;
            if (segmentDistanceToCenterSqr(start, end, center)
                    <= square(sphereRadius + curveMargin)
                    && refineAnalyticInterval(
                            protectedBounds,
                            shipVelocity,
                            shipAcceleration,
                            initialPosition,
                            initialVelocity,
                            ballistics,
                            startTicks,
                            endTicks)) {
                return true;
            }
            startTicks = endTicks;
            start = end;
        }
        return false;
    }

    private static boolean refineAnalyticInterval(
            AABB protectedBounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 initialPosition,
            Vec3 initialVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double startTicks,
            double endTicks
    ) {
        double time = startTicks;
        Vec3 start = relativePosition(
                analyticPosition(initialPosition, initialVelocity, ballistics, time),
                shipVelocity,
                shipAcceleration,
                time);
        while (time < endTicks) {
            time = Math.min(endTicks, time + REFINEMENT_STEP_TICKS);
            Vec3 end = relativePosition(
                    analyticPosition(initialPosition, initialVelocity, ballistics, time),
                    shipVelocity,
                    shipAcceleration,
                    time);
            if (intersects(protectedBounds, start, end)) {
                return true;
            }
            start = end;
        }
        return false;
    }

    private static Vec3 analyticPosition(
            Vec3 initialPosition,
            Vec3 initialVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double ticks
    ) {
        if (LinearDragTrajectory.supported(ballistics.drag())) {
            return LinearDragTrajectory.positionAfterTicks(
                    initialPosition,
                    initialVelocity,
                    ballistics.gravity(),
                    ballistics.drag(),
                    ticks);
        }
        return initialPosition.add(
                initialVelocity.x * ticks,
                initialVelocity.y * ticks - 0.5D * ballistics.gravity() * ticks * ticks,
                initialVelocity.z * ticks);
    }

    private static boolean evaluateSimulated(
            AABB protectedBounds,
            Vec3 center,
            double sphereRadius,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 initialPosition,
            Vec3 initialVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double maximumTicks
    ) {
        Vec3 position = initialPosition;
        Vec3 velocity = initialVelocity;
        double time = 0.0D;
        Vec3 relativeStart = relativePosition(position, shipVelocity, shipAcceleration, time);
        if (protectedBounds.contains(relativeStart)) {
            return true;
        }
        while (time < maximumTicks) {
            int substeps = Mth.clamp((int) Math.ceil(velocity.length() / 4.0D), 1, 16);
            double dt = Math.min(1.0D / substeps, maximumTicks - time);
            for (int step = 0; step < substeps && time < maximumTicks; step++) {
                double speed = velocity.length();
                position = position.add(velocity.scale(dt));
                time += dt;
                Vec3 relativeEnd = relativePosition(position, shipVelocity, shipAcceleration, time);
                if (segmentDistanceToCenterSqr(relativeStart, relativeEnd, center) <= square(sphereRadius)
                        && intersects(protectedBounds, relativeStart, relativeEnd)) {
                    return true;
                }
                double drag = ballistics.quadraticDrag()
                        ? ballistics.drag() * speed
                        : ballistics.drag();
                velocity = velocity.scale(Math.max(0.0D, 1.0D - drag * dt))
                        .add(0.0D, -ballistics.gravity() * dt, 0.0D);
                relativeStart = relativeEnd;
                dt = Math.min(dt, maximumTicks - time);
            }
        }
        return false;
    }

    private static Vec3 relativePosition(
            Vec3 projectilePosition,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            double ticks
    ) {
        Vec3 displacement = shipVelocity.scale(ticks)
                .add(shipAcceleration.scale(0.5D * ticks * ticks));
        return projectilePosition.subtract(displacement);
    }

    private static boolean intersects(AABB bounds, Vec3 start, Vec3 end) {
        return bounds.contains(start) || bounds.contains(end) || bounds.clip(start, end).isPresent();
    }

    private static double segmentDistanceToCenterSqr(Vec3 start, Vec3 end, Vec3 center) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < MOTION_EPSILON_SQR) {
            return start.distanceToSqr(center);
        }
        double interpolation = Math.clamp(center.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return start.add(segment.scale(interpolation)).distanceToSqr(center);
    }

    private static double horizontalLengthSqr(Vec3 vector) {
        return vector.x * vector.x + vector.z * vector.z;
    }

    private static double square(double value) {
        return value * value;
    }
}
