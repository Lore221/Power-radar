package com.limbo2136.powerradar.targeting;

import com.limbo2136.powerradar.api.target.TargetClassification;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.api.weapon.WeaponKind;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import net.minecraft.world.phys.Vec3;

public final class TargetLeadSolver {
    private static final int TARGET_LEAD_ITERATIONS = 3;
    private static final int MAX_PROJECTILE_SIMULATION_TICKS = 600;
    private static final int DRAG_AIM_BISECTION_ITERATIONS = 18;
    private static final double MAX_TRACK_PREDICTION_AGE_TICKS = 40.0;
    private static final double DRAG_AIM_BRACKET_STEP_DEGREES = 1.0;
    private static final double DRAG_AIM_ROOT_EPSILON = 1.0E-5;
    private static final double DRAG_AIM_HINT_RANGE_DEGREES = 10.0;
    private static final double BIG_CANNON_MAX_ELEVATION_DEGREES = 60.0;
    private static final double DROP_MORTAR_MIN_ELEVATION_DEGREES = 45.0;
    private static final double DROP_MORTAR_MAX_ELEVATION_DEGREES = 85.0;

    private TargetLeadSolver() {
    }

    public static LeadSolution solve(
            TrackedTargetView track,
            Vec3 origin,
            WeaponBallistics ballistics,
            WeaponKind weaponKind,
            boolean preferHighArc,
            int lockTicks,
            int accelerationLeadWarmupTicks,
            long currentGameTime
    ) {
        // Сначала переносим устаревший радарный снимок к текущему тику, ограничивая возраст прогноза.
        Vec3 velocity = track.hasVelocity() ? track.velocity() : Vec3.ZERO;
        boolean useAcceleration = lockTicks >= accelerationLeadWarmupTicks
                && track.hasAcceleration()
                && usesAccelerationLead(track.classification());
        Vec3 acceleration = useAcceleration ? track.acceleration() : Vec3.ZERO;
        boolean autocannon = weaponKind == WeaponKind.AUTOCANNON;
        double trackAgeTicks = clamp(currentGameTime - track.lastSeenGameTime(), 0.0, MAX_TRACK_PREDICTION_AGE_TICKS);
        Vec3 base = currentTargetPoint(track)
                .add(velocity.scale(trackAgeTicks))
                .add(acceleration.scale(0.5 * trackAgeTicks * trackAgeTicks));
        Vec3 currentVelocity = velocity.add(acceleration.scale(trackAgeTicks));
        double pitchHint = Double.NaN;
        BallisticAim aim = aim(
                base.subtract(origin),
                TargetingMath.horizontalDistance(base.subtract(origin)),
                ballistics,
                autocannon,
                preferHighArc,
                pitchHint);
        if (aim.reachable()) {
            pitchHint = aim.pitchDegrees();
        }
        if (!usesFullVelocityLead(track.classification())
                || base.distanceTo(origin) < PowerRadarCeeConstants.TARGET_CONTROLLER_MIN_LEAD_DISTANCE_BLOCKS) {
            return new LeadSolution(base, aim, 0.0, false);
        }
        // Затем несколько раз согласуем будущую точку цели с рассчитанным временем полёта.
        Vec3 predicted = base;
        double flightTicks = fallbackFlightTicks(base.distanceTo(origin), ballistics);
        for (int i = 0; i < TARGET_LEAD_ITERATIONS; i++) {
            predicted = base
                    .add(currentVelocity.scale(flightTicks))
                    .add(acceleration.scale(0.5 * flightTicks * flightTicks));
            Vec3 delta = predicted.subtract(origin);
            aim = aim(delta, TargetingMath.horizontalDistance(delta), ballistics, autocannon, preferHighArc, pitchHint);
            if (aim.reachable()) {
                pitchHint = aim.pitchDegrees();
            }
            flightTicks = aim.flightTicks() > 0.0
                    ? aim.flightTicks()
                    : fallbackFlightTicks(delta.length(), ballistics);
            flightTicks = clamp(flightTicks, 0.0, PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_LEAD_TICKS);
        }
        return new LeadSolution(predicted, aim, flightTicks, useAcceleration);
    }

    public static Vec3 currentTargetPoint(TrackedTargetView track) {
        Vec3 position = track.position();
        return new Vec3(
                position.x,
                position.y + track.boundingHeight() * PowerRadarCeeConstants.TARGET_CONTROLLER_TARGET_HEIGHT_FACTOR,
                position.z);
    }

    public static boolean withinLifetimeLimit(
            double horizontalDistance,
            double directDistance,
            WeaponBallistics ballistics
    ) {
        if (ballistics == null || !ballistics.available() || !ballistics.hasLifetimeLimit()) {
            return true;
        }
        double maxDistance = ballistics.speedBlocksPerTick() * ballistics.lifetimeTicks();
        return directDistance <= maxDistance || horizontalDistance <= maxDistance;
    }

    private static BallisticAim aim(
            Vec3 delta,
            double horizontalDistance,
            WeaponBallistics ballistics,
            boolean autocannon,
            boolean preferHighArc,
            double pitchHint
    ) {
        // Выбор ветки: прямое наведение, аналитика без сопротивления либо решатель сопротивления.
        float directPitch = (float) Math.toDegrees(Math.atan2(delta.y, Math.max(0.001, horizontalDistance)));
        if (ballistics == null || !ballistics.available() || ballistics.speedBlocksPerTick() <= 0.001) {
            return new BallisticAim(directPitch, true, "direct", 0.0);
        }
        double gravity = ballistics.gravityBlocksPerTickSquared();
        if (gravity <= 0.000001 || horizontalDistance <= 0.001) {
            double ticks = horizontalDistance / Math.max(0.001, ballistics.speedBlocksPerTick());
            return new BallisticAim(directPitch, true, ballistics.mode() + ":direct", ticks);
        }
        if (ballistics.drag() > 0.000001) {
            BallisticAim simulated = simulatedDragAim(
                    delta,
                    horizontalDistance,
                    ballistics,
                    autocannon,
                    directPitch,
                    preferHighArc,
                    pitchHint);
            return simulated;
        }
        double speed = ballistics.speedBlocksPerTick();
        double speedSquared = speed * speed;
        double x = horizontalDistance;
        double y = delta.y;
        double discriminant = speedSquared * speedSquared - gravity * (gravity * x * x + 2.0 * y * speedSquared);
        if (discriminant < 0.0) {
            return new BallisticAim(directPitch, false, ballistics.mode() + ":no-solution", 0.0);
        }
        double sqrt = Math.sqrt(discriminant);
        double low = Math.atan((speedSquared - sqrt) / (gravity * x));
        double high = Math.atan((speedSquared + sqrt) / (gravity * x));
        double minElevation = minimumElevationDegrees(ballistics);
        double highDegrees = Math.toDegrees(high);
        double maxElevation = maximumElevationDegrees(ballistics);
        boolean highArcReachable = highDegrees >= minElevation && highDegrees <= maxElevation;
        boolean useHighArc = preferHighArc && highArcReachable;
        double selected = useHighArc ? high : low;
        double selectedDegrees = Math.toDegrees(selected);
        if (selectedDegrees < minElevation || selectedDegrees > maxElevation) {
            return new BallisticAim(directPitch, false, ballistics.mode() + ":angle-out-of-limits", 0.0);
        }
        String modeSuffix = useHighArc ? ":high" : (preferHighArc ? ":flat/high-over-limit" : ":flat");
        double flightTicks = x / Math.max(0.001, Math.cos(selected) * speed);
        return new BallisticAim((float) Math.toDegrees(selected), true,
                ballistics.mode() + modeSuffix, flightTicks);
    }

    private static BallisticAim simulatedDragAim(
            Vec3 delta,
            double horizontalDistance,
            WeaponBallistics ballistics,
            boolean autocannon,
            float fallbackPitch,
            boolean preferHighArc,
            double pitchHint
    ) {
        // Линейное сопротивление решается аналитически; для квадратичного остаётся резервная симуляция.
        double minElevation = minimumElevationDegrees(ballistics);
        double maxElevation = maximumElevationDegrees(ballistics);
        if (!ballistics.quadraticDrag()) {
            LinearDragAimSolver.Roots analyticRoots = autocannon
                    ? LinearDragAimSolver.solveAutocannonLowArc(
                            minElevation,
                            maxElevation,
                            horizontalDistance,
                            delta.y,
                            ballistics)
                    : null;
            if (analyticRoots == null) {
                analyticRoots = LinearDragAimSolver.solve(
                        minElevation,
                        maxElevation,
                        horizontalDistance,
                        delta.y,
                        ballistics);
            }
            if (analyticRoots != null) {
                LinearDragAimSolver.Root lowRoot = analyticRoots.low();
                LinearDragAimSolver.Root highRoot = analyticRoots.high();
                if (lowRoot == null && highRoot == null) {
                    return new BallisticAim(fallbackPitch, false, ballistics.mode() + ":linear-drag-no-solution", 0.0);
                }
                boolean highArcSelected = preferHighArc && highRoot != null && !sameAngle(
                        lowRoot == null ? null : lowRoot.pitchRadians(),
                        highRoot.pitchRadians());
                LinearDragAimSolver.Root selected = highArcSelected
                        ? highRoot
                        : (lowRoot != null ? lowRoot : highRoot);
                String suffix = highArcSelected
                        ? ":linear-drag-high"
                        : (preferHighArc ? ":linear-drag-flat/high-unavailable" : ":linear-drag-flat");
                ProjectileSimulation validation = simulateLinearDragProjectileAtHorizontalDistance(
                        selected.pitchRadians(),
                        horizontalDistance,
                        ballistics);
                if (validation != null && !Double.isNaN(validation.height())) {
                    return new BallisticAim(
                            (float) Math.toDegrees(selected.pitchRadians()),
                            true,
                            ballistics.mode() + suffix,
                            validation.flightTicks());
                }
            }
        }
        DragAimRoots roots = DragAimRoots.EMPTY;
        if (Double.isFinite(pitchHint)) {
            roots = dragAimRootsByBrackets(
                    Math.max(minElevation, pitchHint - DRAG_AIM_HINT_RANGE_DEGREES),
                    Math.min(maxElevation, pitchHint + DRAG_AIM_HINT_RANGE_DEGREES),
                    horizontalDistance,
                    delta.y,
                    ballistics);
        }
        if (roots.lowRoot() == null && roots.highRoot() == null) {
            roots = dragAimRootsByBrackets(
                minElevation,
                maxElevation,
                horizontalDistance,
                delta.y,
                ballistics);
        }
        Double lowRoot = roots.lowRoot();
        Double highRoot = roots.highRoot();
        if (lowRoot == null && highRoot == null) {
            return new BallisticAim(fallbackPitch, false, ballistics.mode() + ":drag-no-solution", 0.0);
        }
        boolean highArcSelected = preferHighArc && highRoot != null && !sameAngle(lowRoot, highRoot);
        String suffix = highArcSelected ? ":drag-high" : (preferHighArc ? ":drag-flat/high-unavailable" : ":drag-flat");
        double selected = highArcSelected ? highRoot : (lowRoot != null ? lowRoot : highRoot);
        ProjectileSimulation simulation = simulateProjectileAtHorizontalDistance(selected, horizontalDistance, ballistics);
        return new BallisticAim((float) Math.toDegrees(selected), true, ballistics.mode() + suffix, simulation.flightTicks());
    }

    private static DragAimRoots dragAimRootsByBrackets(
            double minElevationDegrees,
            double maxElevationDegrees,
            double horizontalDistance,
            double targetHeight,
            WeaponBallistics ballistics
    ) {
        Double lowRoot = null;
        Double highRoot = null;
        double previousDegrees = minElevationDegrees;
        double previousAngle = Math.toRadians(previousDegrees);
        double previousError = dragAimError(previousAngle, horizontalDistance, targetHeight, ballistics);
        boolean previousExact = isDragAimRoot(previousError);
        if (previousExact) {
            lowRoot = previousAngle;
            highRoot = previousAngle;
        }
        for (double degrees = minElevationDegrees + DRAG_AIM_BRACKET_STEP_DEGREES;
                degrees <= maxElevationDegrees + 0.0001;
                degrees += DRAG_AIM_BRACKET_STEP_DEGREES) {
            double currentDegrees = Math.min(degrees, maxElevationDegrees);
            if (currentDegrees <= previousDegrees) {
                continue;
            }
            double currentAngle = Math.toRadians(currentDegrees);
            double currentError = dragAimError(currentAngle, horizontalDistance, targetHeight, ballistics);
            boolean currentExact = isDragAimRoot(currentError);
            Double root = null;
            if (currentExact) {
                root = currentAngle;
            } else if (!previousExact
                    && Double.isFinite(previousError)
                    && Double.isFinite(currentError)
                    && Math.signum(previousError) != Math.signum(currentError)) {
                root = dragAimRoot(previousAngle, currentAngle, horizontalDistance, targetHeight, ballistics);
            }
            if (root != null && !sameAngle(highRoot, root)) {
                if (lowRoot == null) {
                    lowRoot = root;
                }
                highRoot = root;
            }
            previousDegrees = currentDegrees;
            previousAngle = currentAngle;
            previousError = currentError;
            previousExact = currentExact;
        }
        return new DragAimRoots(lowRoot, highRoot);
    }

    private static Double dragAimRoot(
            double lowAngle,
            double highAngle,
            double horizontalDistance,
            double targetHeight,
            WeaponBallistics ballistics
    ) {
        double lowError = dragAimError(lowAngle, horizontalDistance, targetHeight, ballistics);
        double highError = dragAimError(highAngle, horizontalDistance, targetHeight, ballistics);
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
            double midError = dragAimError(mid, horizontalDistance, targetHeight, ballistics);
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
            double horizontalDistance,
            double targetHeight,
            WeaponBallistics ballistics
    ) {
        return dragAimHeight(pitchRadians, horizontalDistance, ballistics) - targetHeight;
    }

    private static double dragAimHeight(
            double pitchRadians,
            double horizontalDistance,
            WeaponBallistics ballistics
    ) {
        double height = simulateProjectileAtHorizontalDistance(pitchRadians, horizontalDistance, ballistics).height();
        return Double.isNaN(height) ? Double.NEGATIVE_INFINITY : height;
    }

    private static boolean isDragAimRoot(double error) {
        return Double.isFinite(error) && Math.abs(error) <= DRAG_AIM_ROOT_EPSILON;
    }

    private static boolean sameAngle(Double first, Double second) {
        return first != null && second != null && Math.abs(first - second) <= 1.0E-7;
    }

    private static double maximumElevationDegrees(WeaponBallistics ballistics) {
        return ballistics != null && ballistics.highArcEnabled()
                ? DROP_MORTAR_MAX_ELEVATION_DEGREES
                : BIG_CANNON_MAX_ELEVATION_DEGREES;
    }

    private static double minimumElevationDegrees(WeaponBallistics ballistics) {
        return highArcRequired(ballistics) ? DROP_MORTAR_MIN_ELEVATION_DEGREES : -45.0;
    }

    private static boolean highArcRequired(WeaponBallistics ballistics) {
        return ballistics != null && ballistics.highArcEnabled();
    }

    private static ProjectileSimulation simulateProjectileAtHorizontalDistance(
            double pitchRadians,
            double targetHorizontalDistance,
            WeaponBallistics ballistics
    ) {
        // Порядок шага совпадает с CBC: половинный вклад ускорения в позицию, затем скорость.
        if (!ballistics.quadraticDrag()) {
            ProjectileSimulation linear = simulateLinearDragProjectileAtHorizontalDistance(
                    pitchRadians,
                    targetHorizontalDistance,
                    ballistics);
            if (linear != null) {
                return linear;
            }
        }
        double speed = ballistics.speedBlocksPerTick();
        double horizontalVelocity = Math.cos(pitchRadians) * speed;
        double verticalVelocity = Math.sin(pitchRadians) * speed;
        double horizontal = 0.0;
        double height = 0.0;
        double previousHorizontal = 0.0;
        double previousHeight = 0.0;
        int maxTicks = ballistics.hasLifetimeLimit()
                ? Math.min(MAX_PROJECTILE_SIMULATION_TICKS, Math.max(1, ballistics.lifetimeTicks() + 1))
                : MAX_PROJECTILE_SIMULATION_TICKS;
        for (int tick = 0; tick < maxTicks; tick++) {
            previousHorizontal = horizontal;
            previousHeight = height;
            double currentSpeed = Math.sqrt(horizontalVelocity * horizontalVelocity + verticalVelocity * verticalVelocity);
            if (currentSpeed <= 0.000001) {
                return ProjectileSimulation.UNREACHABLE;
            }
            double dragForce = ballistics.drag() * currentSpeed;
            if (ballistics.quadraticDrag()) {
                dragForce *= currentSpeed;
            }
            dragForce = Math.min(dragForce, currentSpeed);
            double horizontalAcceleration = -horizontalVelocity / currentSpeed * dragForce;
            double verticalAcceleration = -verticalVelocity / currentSpeed * dragForce - ballistics.gravityBlocksPerTickSquared();
            horizontal += horizontalVelocity + horizontalAcceleration * 0.5;
            height += verticalVelocity + verticalAcceleration * 0.5;
            if (horizontal >= targetHorizontalDistance) {
                double span = Math.max(0.000001, horizontal - previousHorizontal);
                double fraction = (targetHorizontalDistance - previousHorizontal) / span;
                double hitHeight = previousHeight + (height - previousHeight) * fraction;
                return new ProjectileSimulation(hitHeight, tick + fraction + 1.0);
            }
            horizontalVelocity += horizontalAcceleration;
            verticalVelocity += verticalAcceleration;
        }
        return ProjectileSimulation.UNREACHABLE;
    }

    private static ProjectileSimulation simulateLinearDragProjectileAtHorizontalDistance(
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
            return ProjectileSimulation.UNREACHABLE;
        }
        double maxTicks = ballistics.hasLifetimeLimit()
                ? Math.min(MAX_PROJECTILE_SIMULATION_TICKS, Math.max(1, ballistics.lifetimeTicks() + 1))
                : MAX_PROJECTILE_SIMULATION_TICKS;
        double horizontalStepScale = 1.0 - drag * 0.5;
        if (horizontalStepScale <= 0.0) {
            return null;
        }
        double retention = 1.0 - drag;
        double maxHorizontal = horizontalAfterTicks(maxTicks, horizontalVelocity, horizontalStepScale, retention, drag);
        if (maxHorizontal < targetHorizontalDistance) {
            return ProjectileSimulation.UNREACHABLE;
        }

        double normalizedRemaining = 1.0 - targetHorizontalDistance * drag / (horizontalVelocity * horizontalStepScale);
        if (normalizedRemaining <= 0.0) {
            normalizedRemaining = Double.MIN_VALUE;
        }
        int hitTick = (int) Math.ceil(Math.log(normalizedRemaining) / Math.log(retention));
        hitTick = Math.max(1, Math.min((int) Math.ceil(maxTicks), hitTick));
        while (hitTick > 1
                && horizontalAfterTicks(hitTick - 1, horizontalVelocity, horizontalStepScale, retention, drag)
                >= targetHorizontalDistance) {
            hitTick--;
        }
        while (hitTick < maxTicks
                && horizontalAfterTicks(hitTick, horizontalVelocity, horizontalStepScale, retention, drag)
                < targetHorizontalDistance) {
            hitTick++;
        }
        double previousHorizontal = horizontalAfterTicks(hitTick - 1, horizontalVelocity, horizontalStepScale, retention, drag);
        double horizontal = horizontalAfterTicks(hitTick, horizontalVelocity, horizontalStepScale, retention, drag);
        double span = Math.max(0.000001, horizontal - previousHorizontal);
        double fraction = (targetHorizontalDistance - previousHorizontal) / span;

        double verticalVelocity = Math.sin(pitchRadians) * speed;
        double previousHeight = linearDragHeightAfterTicks(
                hitTick - 1,
                verticalVelocity,
                retention,
                drag,
                horizontalStepScale,
                ballistics.gravityBlocksPerTickSquared());
        double height = linearDragHeightAfterTicks(
                hitTick,
                verticalVelocity,
                retention,
                drag,
                horizontalStepScale,
                ballistics.gravityBlocksPerTickSquared());
        double hitHeight = previousHeight + (height - previousHeight) * fraction;
        return new ProjectileSimulation(hitHeight, hitTick - 1 + fraction + 1.0);
    }

    private static double horizontalAfterTicks(
            double ticks,
            double initialHorizontalVelocity,
            double horizontalStepScale,
            double retention,
            double drag
    ) {
        return initialHorizontalVelocity * horizontalStepScale * (1.0 - Math.pow(retention, ticks)) / drag;
    }

    private static double linearDragHeightAfterTicks(
            int ticks,
            double initialVerticalVelocity,
            double retention,
            double drag,
            double verticalStepScale,
            double gravity
    ) {
        if (ticks <= 0) {
            return 0.0;
        }
        double velocityGeometricSum = (1.0 - Math.pow(retention, ticks)) / drag;
        double gravityVelocitySum = gravity / drag * (ticks - velocityGeometricSum);
        return verticalStepScale * (initialVerticalVelocity * velocityGeometricSum - gravityVelocitySum)
                - gravity * ticks * 0.5;
    }

    private static boolean usesFullVelocityLead(TargetClassification classification) {
        return switch (classification) {
            case PASSIVE_MOB, HOSTILE_MOB, PLAYER, STRUCTURE -> true;
            default -> false;
        };
    }

    private static boolean usesAccelerationLead(TargetClassification classification) {
        return classification == TargetClassification.PLAYER
                || classification == TargetClassification.HOSTILE_MOB
                || classification == TargetClassification.STRUCTURE;
    }

    private static double fallbackFlightTicks(double distance, WeaponBallistics ballistics) {
        double speed = ballistics != null && ballistics.available() && ballistics.speedBlocksPerTick() > 0.001
                ? ballistics.speedBlocksPerTick()
                : PowerRadarCeeConstants.TARGET_CONTROLLER_LEAD_SPEED_BLOCKS_PER_TICK;
        return clamp(distance / Math.max(0.001, speed), 0.0, PowerRadarCeeConstants.TARGET_CONTROLLER_MAX_LEAD_TICKS);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record LeadSolution(Vec3 aimPoint, BallisticAim ballisticAim, double flightTicks, boolean usesAcceleration) {
    }

    public record BallisticAim(float pitchDegrees, boolean reachable, String mode, double flightTicks) {
    }

    private record ProjectileSimulation(double height, double flightTicks) {
        private static final ProjectileSimulation UNREACHABLE = new ProjectileSimulation(Double.NaN, 0.0);
    }

    private record DragAimRoots(Double lowRoot, Double highRoot) {
        private static final DragAimRoots EMPTY = new DragAimRoots(null, null);
    }

}
