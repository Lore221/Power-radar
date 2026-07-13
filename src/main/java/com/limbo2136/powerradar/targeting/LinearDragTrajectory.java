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
        VerticalEvaluation start = evaluateVertical(
                0.0, initialHeight, initialVerticalVelocity, gravity, drag, planeHeight);
        VerticalEvaluation end = evaluateVertical(
                maximumTicks, initialHeight, initialVerticalVelocity, gravity, drag, planeHeight);
        double peakTicks;
        if (start.firstDerivative() <= 0.0) {
            peakTicks = 0.0;
        } else if (end.firstDerivative() >= 0.0) {
            return null;
        } else {
            double scale = stepScale(drag);
            double logarithm = Math.log(retention(drag));
            double velocityTerm = initialVerticalVelocity + gravity / drag;
            double numerator = gravity + gravity * drag / (2.0 * scale);
            double ratio = numerator / (velocityTerm * -logarithm);
            if (!(ratio > 0.0 && ratio < 1.0)) {
                return null;
            }
            peakTicks = clamp(Math.log(ratio) / logarithm, 0.0, maximumTicks);
        }

        VerticalEvaluation peak = evaluateVertical(
                peakTicks, initialHeight, initialVerticalVelocity, gravity, drag, planeHeight);
        if (Math.abs(peak.error()) <= HEIGHT_TOLERANCE_BLOCKS) {
            return peak.ticks();
        }
        if (peak.error() < 0.0 || end.error() > 0.0) {
            return null;
        }
        if (Math.abs(end.error()) <= HEIGHT_TOLERANCE_BLOCKS) {
            return end.ticks();
        }

        VerticalEvaluation low = peak;
        VerticalEvaluation high = end;
        VerticalEvaluation current = evaluateVertical(
                falsePosition(low, high),
                initialHeight, initialVerticalVelocity, gravity, drag, planeHeight);
        for (int iteration = 0; iteration < ROOT_FALLBACK_ITERATIONS; iteration++) {
            if (Math.abs(current.error()) <= HEIGHT_TOLERANCE_BLOCKS
                    || high.ticks() - low.ticks() <= TIME_TOLERANCE_TICKS) {
                return current.ticks();
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
            current = evaluateVertical(
                    candidate, initialHeight, initialVerticalVelocity, gravity, drag, planeHeight);
        }
        return current.ticks();
    }

    private static VerticalEvaluation evaluateVertical(
            double ticks,
            double initialHeight,
            double initialVerticalVelocity,
            double gravity,
            double drag,
            double planeHeight
    ) {
        double retention = retention(drag);
        double retentionPower = Math.pow(retention, ticks);
        double logarithm = Math.log(retention);
        double scale = stepScale(drag);
        double sum = (1.0 - retentionPower) / drag;
        double sumFirst = -logarithm * retentionPower / drag;
        double sumSecond = -logarithm * logarithm * retentionPower / drag;
        double velocityTerm = initialVerticalVelocity + gravity / drag;
        double height = initialHeight
                + scale * (velocityTerm * sum - gravity * ticks / drag)
                - gravity * ticks * 0.5;
        double first = scale * (velocityTerm * sumFirst - gravity / drag) - gravity * 0.5;
        double second = scale * velocityTerm * sumSecond;
        return new VerticalEvaluation(ticks, height - planeHeight, first, second);
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

    private record VerticalEvaluation(
            double ticks,
            double error,
            double firstDerivative,
            double secondDerivative
    ) {
    }
}
