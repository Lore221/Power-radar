package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.targeting.TargetingMath;
import net.minecraft.world.phys.Vec3;

public final class InterceptionBallistics {
    private static final int DRAG_AIM_BISECTION_ITERATIONS = 18;
    private static final double DRAG_AIM_BRACKET_STEP_DEGREES = 1.0;
    private static final double DRAG_AIM_ROOT_EPSILON = 1.0E-5;
    private static final double MIN_AIM_ELEVATION_DEGREES = -45.0;
    private static final double MAX_AIM_ELEVATION_DEGREES = 85.0;
    private static final int MAX_PROJECTILE_SIMULATION_TICKS = 600;

    private InterceptionBallistics() {
    }

    public static Aim solveLowArc(Vec3 delta, WeaponBallistics ballistics) {
        return solveLowArc(delta, ballistics, Double.NaN, 0.0);
    }

    public static Aim solveLowArc(
            Vec3 delta,
            WeaponBallistics ballistics,
            double preferredPitchDegrees,
            double searchHalfRangeDegrees
    ) {
        double horizontal = TargetingMath.horizontalDistance(delta);
        double speed = ballistics.speedBlocksPerTick();
        if (horizontal <= 0.001 || speed <= 0.001) {
            return Aim.UNREACHABLE;
        }
        double gravity = ballistics.gravityBlocksPerTickSquared();
        if (gravity <= 0.000001) {
            return new Aim(true,
                    (float) Math.toDegrees(Math.atan2(delta.y, horizontal)),
                    delta.length() / speed);
        }
        if (ballistics.drag() > 0.000001 && !ballistics.quadraticDrag()) {
            Aim dragAim = Aim.UNREACHABLE;
            if (Double.isFinite(preferredPitchDegrees) && searchHalfRangeDegrees > 0.0) {
                double minDegrees = Math.max(
                        MIN_AIM_ELEVATION_DEGREES,
                        preferredPitchDegrees - searchHalfRangeDegrees);
                double maxDegrees = Math.min(
                        MAX_AIM_ELEVATION_DEGREES,
                        preferredPitchDegrees + searchHalfRangeDegrees);
                dragAim = solveLinearDragLowArc(delta, horizontal, ballistics, minDegrees, maxDegrees);
            }
            if (!dragAim.reachable()) {
                dragAim = solveLinearDragLowArc(
                        delta,
                        horizontal,
                        ballistics,
                        MIN_AIM_ELEVATION_DEGREES,
                        MAX_AIM_ELEVATION_DEGREES);
            }
            if (dragAim.reachable()) {
                return dragAim;
            }
        }
        double speedSquared = speed * speed;
        double discriminant = speedSquared * speedSquared
                - gravity * (gravity * horizontal * horizontal + 2.0 * delta.y * speedSquared);
        if (discriminant < 0.0) {
            return Aim.UNREACHABLE;
        }
        double angle = Math.atan((speedSquared - Math.sqrt(discriminant)) / (gravity * horizontal));
        double horizontalSpeed = Math.cos(angle) * speed;
        if (horizontalSpeed <= 0.001) {
            return Aim.UNREACHABLE;
        }
        return new Aim(true, (float) Math.toDegrees(angle), horizontal / horizontalSpeed);
    }

    public static Vec3 applyBallistics(Vec3 velocity, double gravity, double drag, boolean quadraticDrag) {
        double speed = velocity.length();
        double dragAmount = quadraticDrag ? drag * speed : drag;
        double factor = Math.max(0.0, 1.0 - dragAmount);
        return velocity.scale(factor).add(0.0, -gravity, 0.0);
    }

    private static Aim solveLinearDragLowArc(
            Vec3 delta,
            double horizontal,
            WeaponBallistics ballistics,
            double minDegrees,
            double maxDegrees
    ) {
        if (maxDegrees <= minDegrees) {
            return Aim.UNREACHABLE;
        }
        double previousDegrees = minDegrees;
        double previousAngle = Math.toRadians(previousDegrees);
        double previousError = dragAimError(previousAngle, horizontal, delta.y, ballistics);
        if (isDragAimRoot(previousError)) {
            return dragAim(previousAngle, horizontal, ballistics);
        }
        for (double degrees = minDegrees + DRAG_AIM_BRACKET_STEP_DEGREES;
                degrees <= maxDegrees + 0.0001;
                degrees += DRAG_AIM_BRACKET_STEP_DEGREES) {
            double currentDegrees = Math.min(degrees, maxDegrees);
            if (currentDegrees <= previousDegrees) {
                continue;
            }
            double currentAngle = Math.toRadians(currentDegrees);
            double currentError = dragAimError(currentAngle, horizontal, delta.y, ballistics);
            if (isDragAimRoot(currentError)) {
                return dragAim(currentAngle, horizontal, ballistics);
            }
            if (Double.isFinite(previousError)
                    && Double.isFinite(currentError)
                    && Math.signum(previousError) != Math.signum(currentError)) {
                Double root = dragAimRoot(previousAngle, currentAngle, horizontal, delta.y, ballistics);
                if (root != null) {
                    return dragAim(root, horizontal, ballistics);
                }
            }
            previousDegrees = currentDegrees;
            previousAngle = currentAngle;
            previousError = currentError;
        }
        return Aim.UNREACHABLE;
    }

    private static Aim dragAim(double pitchRadians, double horizontal, WeaponBallistics ballistics) {
        ProjectileSample sample = sampleLinearDragProjectileAtHorizontalDistance(pitchRadians, horizontal, ballistics);
        if (sample == null || !sample.reachable()) {
            return Aim.UNREACHABLE;
        }
        return new Aim(true, (float) Math.toDegrees(pitchRadians), sample.flightTicks());
    }

    private static Double dragAimRoot(
            double lowAngle,
            double highAngle,
            double horizontal,
            double targetHeight,
            WeaponBallistics ballistics
    ) {
        double lowError = dragAimError(lowAngle, horizontal, targetHeight, ballistics);
        double highError = dragAimError(highAngle, horizontal, targetHeight, ballistics);
        if (lowError == 0.0) {
            return lowAngle;
        }
        if (highError == 0.0) {
            return highAngle;
        }
        if (!Double.isFinite(lowError) || !Double.isFinite(highError)
                || Math.signum(lowError) == Math.signum(highError)) {
            return null;
        }
        double low = lowAngle;
        double high = highAngle;
        for (int i = 0; i < DRAG_AIM_BISECTION_ITERATIONS; i++) {
            double mid = (low + high) * 0.5;
            double midError = dragAimError(mid, horizontal, targetHeight, ballistics);
            if (!Double.isFinite(midError)) {
                high = mid;
                continue;
            }
            if (midError == 0.0 || Math.signum(midError) == Math.signum(lowError)) {
                low = mid;
                lowError = midError;
            } else {
                high = mid;
            }
        }
        return (low + high) * 0.5;
    }

    private static double dragAimError(
            double pitchRadians,
            double horizontal,
            double targetHeight,
            WeaponBallistics ballistics
    ) {
        ProjectileSample sample = sampleLinearDragProjectileAtHorizontalDistance(pitchRadians, horizontal, ballistics);
        return sample != null && sample.reachable()
                ? sample.height() - targetHeight
                : Double.NEGATIVE_INFINITY;
    }

    private static boolean isDragAimRoot(double error) {
        return Double.isFinite(error) && Math.abs(error) <= DRAG_AIM_ROOT_EPSILON;
    }

    private static ProjectileSample sampleLinearDragProjectileAtHorizontalDistance(
            double pitchRadians,
            double targetHorizontalDistance,
            WeaponBallistics ballistics
    ) {
        double drag = ballistics.drag();
        if (drag <= 0.000001 || drag >= 1.0) {
            return null;
        }
        double speed = ballistics.speedBlocksPerTick();
        double horizontalVelocity = Math.cos(pitchRadians) * speed;
        if (horizontalVelocity <= 0.000001) {
            return ProjectileSample.UNREACHABLE;
        }
        double maxTicks = ballistics.hasLifetimeLimit()
                ? Math.min(MAX_PROJECTILE_SIMULATION_TICKS, Math.max(1, ballistics.lifetimeTicks() + 1))
                : MAX_PROJECTILE_SIMULATION_TICKS;
        double stepScale = 1.0 - drag * 0.5;
        if (stepScale <= 0.0) {
            return null;
        }
        double retention = 1.0 - drag;
        double maxHorizontal = horizontalAfterTicks(maxTicks, horizontalVelocity, stepScale, retention, drag);
        if (maxHorizontal < targetHorizontalDistance) {
            return ProjectileSample.UNREACHABLE;
        }
        double normalizedRemaining = 1.0 - targetHorizontalDistance * drag / (horizontalVelocity * stepScale);
        if (normalizedRemaining <= 0.0) {
            normalizedRemaining = Double.MIN_VALUE;
        }
        int hitTick = (int) Math.ceil(Math.log(normalizedRemaining) / Math.log(retention));
        hitTick = Math.max(1, Math.min((int) Math.ceil(maxTicks), hitTick));
        while (hitTick > 1
                && horizontalAfterTicks(hitTick - 1, horizontalVelocity, stepScale, retention, drag)
                >= targetHorizontalDistance) {
            hitTick--;
        }
        while (hitTick < maxTicks
                && horizontalAfterTicks(hitTick, horizontalVelocity, stepScale, retention, drag)
                < targetHorizontalDistance) {
            hitTick++;
        }
        double previousHorizontal = horizontalAfterTicks(hitTick - 1, horizontalVelocity, stepScale, retention, drag);
        double currentHorizontal = horizontalAfterTicks(hitTick, horizontalVelocity, stepScale, retention, drag);
        double fraction = (targetHorizontalDistance - previousHorizontal)
                / Math.max(0.000001, currentHorizontal - previousHorizontal);

        double verticalVelocity = Math.sin(pitchRadians) * speed;
        double previousHeight = linearDragHeightAfterTicks(
                hitTick - 1, verticalVelocity, retention, drag, stepScale, ballistics.gravityBlocksPerTickSquared());
        double currentHeight = linearDragHeightAfterTicks(
                hitTick, verticalVelocity, retention, drag, stepScale, ballistics.gravityBlocksPerTickSquared());
        double hitHeight = previousHeight + (currentHeight - previousHeight) * fraction;
        return new ProjectileSample(true, hitHeight, hitTick - 1 + fraction + 1.0);
    }

    private static double horizontalAfterTicks(
            double ticks,
            double initialHorizontalVelocity,
            double stepScale,
            double retention,
            double drag
    ) {
        return initialHorizontalVelocity * stepScale * (1.0 - Math.pow(retention, ticks)) / drag;
    }

    private static double linearDragHeightAfterTicks(
            int ticks,
            double initialVerticalVelocity,
            double retention,
            double drag,
            double stepScale,
            double gravity
    ) {
        if (ticks <= 0) {
            return 0.0;
        }
        double velocityGeometricSum = (1.0 - Math.pow(retention, ticks)) / drag;
        double gravityVelocitySum = gravity / drag * (ticks - velocityGeometricSum);
        return stepScale * (initialVerticalVelocity * velocityGeometricSum - gravityVelocitySum)
                - gravity * ticks * 0.5;
    }

    public record Aim(boolean reachable, float pitchDegrees, double flightTicks) {
        public static final Aim UNREACHABLE = new Aim(false, 0.0F, 0.0);
    }

    private record ProjectileSample(boolean reachable, double height, double flightTicks) {
        private static final ProjectileSample UNREACHABLE = new ProjectileSample(false, Double.NaN, 0.0);
    }
}
