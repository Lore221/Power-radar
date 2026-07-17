package com.limbo2136.powerradar.targeting;

import net.minecraft.world.phys.Vec3;

/** Shared analytic form of CBC's discrete linear-drag integration contract. */
public final class LinearDragTrajectory {
    private static final int ROOT_NEWTON_ITERATIONS = 8;
    private static final int ROOT_FALLBACK_ITERATIONS = 24;
    private static final double TIME_TOLERANCE_TICKS = 1.0E-5;
    private static final double HEIGHT_TOLERANCE_BLOCKS = 1.0E-5;
    private static final double DERIVATIVE_EPSILON = 1.0E-9;

    private LinearDragTrajectory() {
    }

    public static boolean supported(double drag) {
        return drag > 1.0E-6 && drag < 1.0;
    }

    public static double retention(double drag) {
        return 1.0 - drag;
    }

    public static double stepScale(double drag) {
        return 1.0 - drag * 0.5;
    }

    public static double geometricSum(double ticks, double drag) {
        if (ticks <= 0.0) {
            return 0.0;
        }
        return (1.0 - Math.pow(retention(drag), ticks)) / drag;
    }

    /**
     * Continuous analytic coefficients for one axis of CBC's discrete trajectory.
     * The acceleration is signed in axis units per tick squared (gravity is negative Y).
     */
    public static AxisTrajectory axisTrajectory(
            double initialPosition,
            double initialVelocity,
            double constantAcceleration,
            double drag
    ) {
        if (drag <= 1.0E-6) {
            return new AxisTrajectory(
                    initialPosition,
                    initialPosition,
                    initialVelocity,
                    constantAcceleration * 0.5,
                    0.0,
                    0.0);
        }
        if (!supported(drag)) {
            throw new IllegalArgumentException("Unsupported linear drag: " + drag);
        }
        double inverseDrag = 1.0 / drag;
        double scale = stepScale(drag);
        double transientTerm = initialVelocity * inverseDrag
                - constantAcceleration * inverseDrag * inverseDrag;
        return new AxisTrajectory(
                initialPosition,
                initialPosition + scale * transientTerm,
                scale * constantAcceleration * inverseDrag + constantAcceleration * 0.5,
                0.0,
                -scale * transientTerm,
                Math.log1p(-drag));
    }

    public static Vec3 positionAfterTicks(
            Vec3 initialPosition,
            Vec3 initialVelocity,
            double gravity,
            double drag,
            double ticks
    ) {
        double sum = geometricSum(ticks, drag);
        double scale = stepScale(drag);
        double gravityVelocitySum = gravity / drag * (ticks - sum);
        return new Vec3(
                initialPosition.x + initialVelocity.x * scale * sum,
                initialPosition.y + scale * (initialVelocity.y * sum - gravityVelocitySum)
                        - gravity * ticks * 0.5,
                initialPosition.z + initialVelocity.z * scale * sum);
    }

    public static Vec3 velocityAfterTicks(
            Vec3 initialVelocity,
            double gravity,
            double drag,
            double ticks
    ) {
        double retentionPower = Math.pow(retention(drag), ticks);
        double gravityVelocity = gravity / drag * (1.0 - retentionPower);
        return new Vec3(
                initialVelocity.x * retentionPower,
                initialVelocity.y * retentionPower - gravityVelocity,
                initialVelocity.z * retentionPower);
    }

    public static TrajectoryState stateAfterTicks(
            Vec3 initialPosition,
            Vec3 initialVelocity,
            double gravity,
            double drag,
            double ticks
    ) {
        double retentionPower = Math.pow(retention(drag), ticks);
        double sum = (1.0 - retentionPower) / drag;
        double scale = stepScale(drag);
        double gravityVelocitySum = gravity / drag * (ticks - sum);
        Vec3 position = new Vec3(
                initialPosition.x + initialVelocity.x * scale * sum,
                initialPosition.y + scale * (initialVelocity.y * sum - gravityVelocitySum)
                        - gravity * ticks * 0.5,
                initialPosition.z + initialVelocity.z * scale * sum);
        double gravityVelocity = gravity / drag * (1.0 - retentionPower);
        Vec3 velocity = new Vec3(
                initialVelocity.x * retentionPower,
                initialVelocity.y * retentionPower - gravityVelocity,
                initialVelocity.z * retentionPower);
        return new TrajectoryState(position, velocity);
    }

    public static DescendingPlaneCrossings descendingPlaneCrossings(
            Vec3 initialPosition,
            Vec3 initialVelocity,
            double gravity,
            double drag,
            double upperPlaneHeight,
            double lowerPlaneHeight,
            double maximumTicks
    ) {
        if (!supported(drag) || maximumTicks <= 0.0 || lowerPlaneHeight > upperPlaneHeight) {
            return null;
        }
        VerticalContext context = verticalContext(
                initialPosition.y, initialVelocity.y, gravity, drag);
        Double peakTicks = descendingPeakTicks(context, maximumTicks);
        if (peakTicks == null) {
            return null;
        }
        VerticalEvaluation upper = solveDescendingPlane(
                context, upperPlaneHeight, peakTicks, maximumTicks);
        if (upper == null) {
            return null;
        }
        VerticalEvaluation lower = solveDescendingPlane(
                context, lowerPlaneHeight, upper.ticks(), maximumTicks);
        if (lower == null) {
            return null;
        }
        return new DescendingPlaneCrossings(
                planeCrossing(initialPosition, initialVelocity, context, upper),
                planeCrossing(initialPosition, initialVelocity, context, lower));
    }

    public static Double descendingPlaneCrossingTicks(
            double initialHeight,
            double initialVerticalVelocity,
            double gravity,
            double drag,
            double planeHeight,
            double maximumTicks
    ) {
        if (!supported(drag) || maximumTicks <= 0.0) {
            return null;
        }
        VerticalContext context = verticalContext(initialHeight, initialVerticalVelocity, gravity, drag);
        Double peakTicks = descendingPeakTicks(context, maximumTicks);
        if (peakTicks == null) {
            return null;
        }
        VerticalEvaluation crossing = solveDescendingPlane(
                context, planeHeight, peakTicks, maximumTicks);
        return crossing == null ? null : crossing.ticks();
    }

    private static Double descendingPeakTicks(VerticalContext context, double maximumTicks) {
        VerticalEvaluation start = evaluateVertical(0.0, context, 0.0);
        VerticalEvaluation end = evaluateVertical(maximumTicks, context, 0.0);
        if (start.firstDerivative() <= 0.0) {
            return 0.0;
        }
        if (end.firstDerivative() >= 0.0) {
            return null;
        }
        double numerator = context.gravity()
                + context.gravity() * context.drag() / (2.0 * context.scale());
        double ratio = numerator / (context.velocityTerm() * -context.logarithm());
        if (!(ratio > 0.0 && ratio < 1.0)) {
            return null;
        }
        return clamp(Math.log(ratio) / context.logarithm(), 0.0, maximumTicks);
    }

    private static VerticalEvaluation solveDescendingPlane(
            VerticalContext context,
            double planeHeight,
            double minimumTicks,
            double maximumTicks
    ) {
        VerticalEvaluation low = evaluateVertical(minimumTicks, context, planeHeight);
        VerticalEvaluation high = evaluateVertical(maximumTicks, context, planeHeight);
        if (Math.abs(low.error()) <= HEIGHT_TOLERANCE_BLOCKS) {
            return low;
        }
        if (low.error() < 0.0 || high.error() > 0.0) {
            return null;
        }
        if (Math.abs(high.error()) <= HEIGHT_TOLERANCE_BLOCKS) {
            return high;
        }

        VerticalEvaluation current = evaluateVertical(falsePosition(low, high), context, planeHeight);
        for (int iteration = 0; iteration < ROOT_FALLBACK_ITERATIONS; iteration++) {
            if (Math.abs(current.error()) <= HEIGHT_TOLERANCE_BLOCKS
                    || high.ticks() - low.ticks() <= TIME_TOLERANCE_TICKS) {
                return current;
            }
            if (current.error() > 0.0) {
                low = current;
            } else {
                high = current;
            }
            double candidate = Double.NaN;
            if (iteration < ROOT_NEWTON_ITERATIONS
                    && Math.abs(current.firstDerivative()) > DERIVATIVE_EPSILON) {
                candidate = current.ticks() - current.error() / current.firstDerivative();
            }
            if (!Double.isFinite(candidate) || candidate <= low.ticks() || candidate >= high.ticks()) {
                candidate = (low.ticks() + high.ticks()) * 0.5;
            }
            current = evaluateVertical(candidate, context, planeHeight);
        }
        return current;
    }

    private static VerticalEvaluation evaluateVertical(
            double ticks,
            VerticalContext context,
            double planeHeight
    ) {
        double retentionPower = Math.pow(context.retention(), ticks);
        double sum = (1.0 - retentionPower) / context.drag();
        double sumFirst = -context.logarithm() * retentionPower / context.drag();
        double sumSecond = -context.logarithm() * context.logarithm()
                * retentionPower / context.drag();
        double height = context.initialHeight()
                + context.scale() * (context.velocityTerm() * sum
                        - context.gravity() * ticks / context.drag())
                - context.gravity() * ticks * 0.5;
        double first = context.scale() * (context.velocityTerm() * sumFirst
                - context.gravity() / context.drag()) - context.gravity() * 0.5;
        double second = context.scale() * context.velocityTerm() * sumSecond;
        return new VerticalEvaluation(
                ticks, height, height - planeHeight, first, second, retentionPower, sum);
    }

    private static VerticalContext verticalContext(
            double initialHeight,
            double initialVerticalVelocity,
            double gravity,
            double drag
    ) {
        double retention = retention(drag);
        return new VerticalContext(
                initialHeight,
                initialVerticalVelocity,
                gravity,
                drag,
                retention,
                Math.log(retention),
                stepScale(drag),
                initialVerticalVelocity + gravity / drag);
    }

    private static PlaneCrossing planeCrossing(
            Vec3 initialPosition,
            Vec3 initialVelocity,
            VerticalContext context,
            VerticalEvaluation evaluation
    ) {
        double horizontalScale = context.scale() * evaluation.geometricSum();
        Vec3 position = new Vec3(
                initialPosition.x + initialVelocity.x * horizontalScale,
                evaluation.height(),
                initialPosition.z + initialVelocity.z * horizontalScale);
        double gravityVelocity = context.gravity() / context.drag()
                * (1.0 - evaluation.retentionPower());
        Vec3 velocity = new Vec3(
                initialVelocity.x * evaluation.retentionPower(),
                initialVelocity.y * evaluation.retentionPower() - gravityVelocity,
                initialVelocity.z * evaluation.retentionPower());
        return new PlaneCrossing(evaluation.ticks(), position, velocity);
    }

    private static double falsePosition(VerticalEvaluation low, VerticalEvaluation high) {
        double denominator = high.error() - low.error();
        if (Math.abs(denominator) <= 1.0E-12) {
            return (low.ticks() + high.ticks()) * 0.5;
        }
        return clamp(
                low.ticks() - low.error() * (high.ticks() - low.ticks()) / denominator,
                low.ticks(),
                high.ticks());
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record PlaneCrossing(double ticks, Vec3 position, Vec3 velocity) {
    }

    public record TrajectoryState(Vec3 position, Vec3 velocity) {
    }

    /** Position = constant + linear*t + quadratic*t^2 + exponential*exp(logarithm*t). */
    public record AxisTrajectory(
            double initial,
            double constant,
            double linear,
            double quadratic,
            double exponential,
            double logarithm
    ) {
        public double position(double ticks) {
            return this.initial
                    + this.linear * ticks
                    + this.quadratic * ticks * ticks
                    + exponentialDelta(ticks);
        }

        public double firstDerivative(double ticks) {
            return this.linear
                    + 2.0 * this.quadratic * ticks
                    + this.logarithm * exponentialValue(ticks);
        }

        public double secondDerivative(double ticks) {
            return 2.0 * this.quadratic
                    + this.logarithm * this.logarithm * exponentialValue(ticks);
        }

        public AxisTrajectory relativeTo(
                double referencePosition,
                double referenceVelocity,
                double referenceAcceleration
        ) {
            return new AxisTrajectory(
                    this.initial - referencePosition,
                    this.constant - referencePosition,
                    this.linear - referenceVelocity,
                    this.quadratic - referenceAcceleration * 0.5,
                    this.exponential,
                    this.logarithm);
        }

        private double exponentialValue(double ticks) {
            return this.exponential == 0.0
                    ? 0.0
                    : this.exponential * Math.exp(this.logarithm * ticks);
        }

        private double exponentialDelta(double ticks) {
            return this.exponential == 0.0
                    ? 0.0
                    : this.exponential * Math.expm1(this.logarithm * ticks);
        }
    }

    public record DescendingPlaneCrossings(PlaneCrossing upper, PlaneCrossing lower) {
    }

    private record VerticalContext(
            double initialHeight,
            double initialVerticalVelocity,
            double gravity,
            double drag,
            double retention,
            double logarithm,
            double scale,
            double velocityTerm
    ) {
    }

    private record VerticalEvaluation(
            double ticks,
            double height,
            double error,
            double firstDerivative,
            double secondDerivative,
            double retentionPower,
            double geometricSum
    ) {
    }
}
