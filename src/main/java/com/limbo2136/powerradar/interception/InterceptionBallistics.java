package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.targeting.LinearDragAimSolver;
import com.limbo2136.powerradar.targeting.TargetingMath;
import net.minecraft.world.phys.Vec3;

public final class InterceptionBallistics {
    private static final double MIN_AIM_ELEVATION_DEGREES = -45.0;
    private static final double MAX_AIM_ELEVATION_DEGREES = 85.0;

    private InterceptionBallistics() {
    }

    public static Aim solveLowArc(Vec3 delta, WeaponBallistics ballistics) {
        return solveAutocannonLowArc(delta, ballistics, Double.NaN, 0.0);
    }

    public static Aim solveLowArc(
            Vec3 delta,
            WeaponBallistics ballistics,
            double preferredPitchDegrees,
            double searchHalfRangeDegrees
    ) {
        return solveAutocannonLowArc(
                delta, ballistics, preferredPitchDegrees, searchHalfRangeDegrees);
    }

    public static Aim solveAutocannonLowArc(
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
            LinearDragAimSolver.Roots roots = null;
            if (Double.isFinite(preferredPitchDegrees) && searchHalfRangeDegrees > 0.0) {
                double minDegrees = Math.max(
                        MIN_AIM_ELEVATION_DEGREES,
                        preferredPitchDegrees - searchHalfRangeDegrees);
                double maxDegrees = Math.min(
                        MAX_AIM_ELEVATION_DEGREES,
                        preferredPitchDegrees + searchHalfRangeDegrees);
                roots = autocannonRoots(
                        minDegrees, maxDegrees, horizontal, delta.y, ballistics);
            }
            LinearDragAimSolver.Root root = lowestRoot(roots);
            if (root == null) {
                roots = autocannonRoots(
                        MIN_AIM_ELEVATION_DEGREES,
                        MAX_AIM_ELEVATION_DEGREES,
                        horizontal,
                        delta.y,
                        ballistics);
                root = lowestRoot(roots);
            }
        return root == null
                    ? Aim.UNREACHABLE
                    : new Aim(true, (float) Math.toDegrees(root.pitchRadians()), root.flightTicks());
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

    private static LinearDragAimSolver.Roots autocannonRoots(
            double minimumPitchDegrees,
            double maximumPitchDegrees,
            double horizontalDistance,
            double targetHeight,
            WeaponBallistics ballistics
    ) {
        LinearDragAimSolver.Roots roots = LinearDragAimSolver.solveAutocannonLowArc(
                minimumPitchDegrees,
                maximumPitchDegrees,
                horizontalDistance,
                targetHeight,
                ballistics);
        return roots != null
                ? roots
                : LinearDragAimSolver.solve(
                        minimumPitchDegrees,
                        maximumPitchDegrees,
                        horizontalDistance,
                        targetHeight,
                        ballistics);
    }

    public static Vec3 applyBallistics(Vec3 velocity, double gravity, double drag, boolean quadraticDrag) {
        double speed = velocity.length();
        double dragAmount = quadraticDrag ? drag * speed : drag;
        double factor = Math.max(0.0, 1.0 - dragAmount);
        return velocity.scale(factor).add(0.0, -gravity, 0.0);
    }

    private static LinearDragAimSolver.Root lowestRoot(LinearDragAimSolver.Roots roots) {
        if (roots == null) {
            return null;
        }
        return roots.low() != null ? roots.low() : roots.high();
    }

    public record Aim(boolean reachable, float pitchDegrees, double flightTicks) {
        public static final Aim UNREACHABLE = new Aim(false, 0.0F, 0.0);
    }
}
