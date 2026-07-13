package com.limbo2136.powerradar.targeting;

import com.limbo2136.powerradar.api.weapon.WeaponBallistics;

/**
 * Solves the two pitch branches of the discrete linear-drag trajectory used by CBC.
 * The height, its pitch derivatives, and the horizontal hit time are evaluated from
 * the same geometric-series equations as the tick simulation in {@link TargetLeadSolver}.
 */
public final class LinearDragAimSolver {
    private static final int MAX_PROJECTILE_TICKS = 600;
    private static final int MAX_NEWTON_ITERATIONS = 8;
    private static final int MAX_FALLBACK_ITERATIONS = 24;
    private static final double HEIGHT_TOLERANCE_BLOCKS = 1.0E-5;
    private static final double ANGLE_TOLERANCE_RADIANS = Math.toRadians(1.0E-4);
    private static final double DERIVATIVE_EPSILON = 1.0E-9;

    private LinearDragAimSolver() {
    }

    public static Roots solve(
            double minimumPitchDegrees,
            double maximumPitchDegrees,
            double horizontalDistance,
            double targetHeight,
            WeaponBallistics ballistics
    ) {
        double drag = ballistics.drag();
        double speed = ballistics.speedBlocksPerTick();
        if (drag <= 1.0E-6 || drag >= 1.0 || speed <= 1.0E-6 || horizontalDistance <= 0.0) {
            return null;
        }

        int maximumTicks = ballistics.hasLifetimeLimit()
                ? Math.min(MAX_PROJECTILE_TICKS, Math.max(1, ballistics.lifetimeTicks() + 1))
                : MAX_PROJECTILE_TICKS;
        double retention = LinearDragTrajectory.retention(drag);
        double stepScale = LinearDragTrajectory.stepScale(drag);
        double horizontalScale = speed * stepScale * LinearDragTrajectory.geometricSum(maximumTicks, drag);
        double reachRatio = horizontalDistance / horizontalScale;
        if (!Double.isFinite(reachRatio) || reachRatio > 1.0) {
            return Roots.EMPTY;
        }

        double lifetimeLimit = Math.acos(clamp(reachRatio, -1.0, 1.0));
        double minimumPitch = Math.max(Math.toRadians(minimumPitchDegrees), -lifetimeLimit);
        double maximumPitch = Math.min(Math.toRadians(maximumPitchDegrees), lifetimeLimit);
        if (!(minimumPitch <= maximumPitch)) {
            return Roots.EMPTY;
        }

        Context context = new Context(
                horizontalDistance,
                targetHeight,
                speed,
                ballistics.gravityBlocksPerTickSquared(),
                drag,
                retention,
                stepScale,
                maximumTicks);
        Evaluation minimum = evaluate(minimumPitch, context);
        Evaluation maximum = evaluate(maximumPitch, context);
        if (!minimum.reachable() || !maximum.reachable()) {
            return null;
        }

        Evaluation peak;
        if (minimum.firstDerivative() <= 0.0) {
            peak = minimum;
        } else if (maximum.firstDerivative() >= 0.0) {
            peak = maximum;
        } else {
            peak = solvePeak(minimum, maximum, context);
            if (peak == null) {
                return null;
            }
        }

        Root low = solveBranch(minimum, peak, context);
        Root high = solveBranch(peak, maximum, context);
        if (low != null && high != null
                && Math.abs(low.pitchRadians() - high.pitchRadians()) <= ANGLE_TOLERANCE_RADIANS) {
            high = low;
        }
        return new Roots(low, high);
    }

    private static Evaluation solvePeak(Evaluation low, Evaluation high, Context context) {
        Evaluation current = evaluate((low.pitchRadians() + high.pitchRadians()) * 0.5, context);
        for (int iteration = 0; iteration < MAX_NEWTON_ITERATIONS; iteration++) {
            if (!current.reachable()) {
                return null;
            }
            if (Math.abs(current.firstDerivative()) <= DERIVATIVE_EPSILON
                    || high.pitchRadians() - low.pitchRadians() <= ANGLE_TOLERANCE_RADIANS) {
                return current;
            }
            if (current.firstDerivative() > 0.0) {
                low = current;
            } else {
                high = current;
            }
            double candidate = current.pitchRadians()
                    - current.firstDerivative() / current.secondDerivative();
            if (!Double.isFinite(candidate)
                    || Math.abs(current.secondDerivative()) <= DERIVATIVE_EPSILON
                    || candidate <= low.pitchRadians()
                    || candidate >= high.pitchRadians()) {
                candidate = (low.pitchRadians() + high.pitchRadians()) * 0.5;
            }
            current = evaluate(candidate, context);
        }
        for (int iteration = MAX_NEWTON_ITERATIONS; iteration < MAX_FALLBACK_ITERATIONS; iteration++) {
            if (current.firstDerivative() > 0.0) {
                low = current;
            } else {
                high = current;
            }
            current = evaluate((low.pitchRadians() + high.pitchRadians()) * 0.5, context);
            if (!current.reachable()) {
                return null;
            }
            if (high.pitchRadians() - low.pitchRadians() <= ANGLE_TOLERANCE_RADIANS) {
                break;
            }
        }
        return current;
    }

    private static Root solveBranch(Evaluation low, Evaluation high, Context context) {
        if (Math.abs(low.heightError()) <= HEIGHT_TOLERANCE_BLOCKS) {
            return low.asRoot();
        }
        if (Math.abs(high.heightError()) <= HEIGHT_TOLERANCE_BLOCKS) {
            return high.asRoot();
        }
        if (Math.signum(low.heightError()) == Math.signum(high.heightError())) {
            return null;
        }

        Evaluation current = evaluate(falsePosition(low, high), context);
        for (int iteration = 0; iteration < MAX_NEWTON_ITERATIONS; iteration++) {
            if (!current.reachable()) {
                return null;
            }
            if (Math.abs(current.heightError()) <= HEIGHT_TOLERANCE_BLOCKS) {
                return current.asRoot();
            }
            if (Math.signum(current.heightError()) == Math.signum(low.heightError())) {
                low = current;
            } else {
                high = current;
            }
            if (high.pitchRadians() - low.pitchRadians() <= ANGLE_TOLERANCE_RADIANS) {
                return evaluate((low.pitchRadians() + high.pitchRadians()) * 0.5, context).asRoot();
            }
            double candidate = current.pitchRadians() - current.heightError() / current.firstDerivative();
            if (!Double.isFinite(candidate)
                    || Math.abs(current.firstDerivative()) <= DERIVATIVE_EPSILON
                    || candidate <= low.pitchRadians()
                    || candidate >= high.pitchRadians()) {
                candidate = (low.pitchRadians() + high.pitchRadians()) * 0.5;
            }
            current = evaluate(candidate, context);
        }
        for (int iteration = MAX_NEWTON_ITERATIONS; iteration < MAX_FALLBACK_ITERATIONS; iteration++) {
            if (Math.signum(current.heightError()) == Math.signum(low.heightError())) {
                low = current;
            } else {
                high = current;
            }
            current = evaluate((low.pitchRadians() + high.pitchRadians()) * 0.5, context);
            if (!current.reachable()) {
                return null;
            }
            if (Math.abs(current.heightError()) <= HEIGHT_TOLERANCE_BLOCKS
                    || high.pitchRadians() - low.pitchRadians() <= ANGLE_TOLERANCE_RADIANS) {
                return current.asRoot();
            }
        }
        return Math.abs(low.heightError()) <= Math.abs(high.heightError()) ? low.asRoot() : high.asRoot();
    }

    private static Evaluation evaluate(double pitch, Context context) {
        double cosine = Math.cos(pitch);
        double sine = Math.sin(pitch);
        double horizontalVelocity = cosine * context.speed();
        if (horizontalVelocity <= 1.0E-9) {
            return Evaluation.UNREACHABLE;
        }
        double normalizedRemaining = 1.0
                - context.horizontalDistance() * context.drag()
                / (horizontalVelocity * context.stepScale());
        if (normalizedRemaining < 0.0) {
            return Evaluation.UNREACHABLE;
        }
        normalizedRemaining = Math.max(Double.MIN_VALUE, normalizedRemaining);
        int hitTick = (int) Math.ceil(Math.log(normalizedRemaining) / Math.log(context.retention()));
        hitTick = Math.max(1, Math.min(context.maximumTicks(), hitTick));

        double previousHorizontalCoefficient = horizontalCoefficient(hitTick - 1, context);
        double horizontalCoefficient = horizontalCoefficient(hitTick, context);
        double horizontalSpanCoefficient = horizontalCoefficient - previousHorizontalCoefficient;
        if (horizontalSpanCoefficient <= 1.0E-12) {
            return Evaluation.UNREACHABLE;
        }
        double fraction = context.horizontalDistance() / (cosine * horizontalSpanCoefficient)
                - previousHorizontalCoefficient / horizontalSpanCoefficient;
        if (fraction < -1.0E-7 || fraction > 1.0 + 1.0E-7) {
            return Evaluation.UNREACHABLE;
        }
        fraction = clamp(fraction, 0.0, 1.0);

        VerticalTerms previous = verticalTerms(hitTick - 1, context);
        VerticalTerms current = verticalTerms(hitTick, context);
        double previousHeight = previous.sineCoefficient() * sine + previous.constant();
        double currentHeight = current.sineCoefficient() * sine + current.constant();
        double heightSpan = currentHeight - previousHeight;
        double height = previousHeight + fraction * heightSpan;

        double fractionScale = context.horizontalDistance() / horizontalSpanCoefficient;
        double fractionFirst = fractionScale * sine / (cosine * cosine);
        double fractionSecond = fractionScale
                * (1.0 / cosine + 2.0 * sine * sine / (cosine * cosine * cosine));
        double previousFirst = previous.sineCoefficient() * cosine;
        double currentFirst = current.sineCoefficient() * cosine;
        double heightSpanFirst = currentFirst - previousFirst;
        double first = previousFirst + fractionFirst * heightSpan + fraction * heightSpanFirst;

        double previousSecond = -previous.sineCoefficient() * sine;
        double currentSecond = -current.sineCoefficient() * sine;
        double heightSpanSecond = currentSecond - previousSecond;
        double second = previousSecond
                + fractionSecond * heightSpan
                + 2.0 * fractionFirst * heightSpanFirst
                + fraction * heightSpanSecond;
        return new Evaluation(
                pitch,
                height - context.targetHeight(),
                first,
                second,
                hitTick + fraction,
                true);
    }

    private static double horizontalCoefficient(int ticks, Context context) {
        return context.speed()
                * context.stepScale()
                * LinearDragTrajectory.geometricSum(ticks, context.drag());
    }

    private static VerticalTerms verticalTerms(int ticks, Context context) {
        if (ticks <= 0) {
            return VerticalTerms.ZERO;
        }
        double sum = LinearDragTrajectory.geometricSum(ticks, context.drag());
        double gravityVelocitySum = context.gravity() / context.drag() * (ticks - sum);
        double sineCoefficient = context.stepScale() * context.speed() * sum;
        double constant = -context.stepScale() * gravityVelocitySum - context.gravity() * ticks * 0.5;
        return new VerticalTerms(sineCoefficient, constant);
    }

    private static double falsePosition(Evaluation low, Evaluation high) {
        double denominator = high.heightError() - low.heightError();
        if (Math.abs(denominator) <= 1.0E-12) {
            return (low.pitchRadians() + high.pitchRadians()) * 0.5;
        }
        return clamp(
                low.pitchRadians()
                        - low.heightError() * (high.pitchRadians() - low.pitchRadians()) / denominator,
                low.pitchRadians(),
                high.pitchRadians());
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Root(double pitchRadians, double flightTicks) {
    }

    public record Roots(Root low, Root high) {
        private static final Roots EMPTY = new Roots(null, null);
    }

    private record Context(
            double horizontalDistance,
            double targetHeight,
            double speed,
            double gravity,
            double drag,
            double retention,
            double stepScale,
            int maximumTicks
    ) {
    }

    private record Evaluation(
            double pitchRadians,
            double heightError,
            double firstDerivative,
            double secondDerivative,
            double flightTicks,
            boolean reachable
    ) {
        private static final Evaluation UNREACHABLE = new Evaluation(
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0, false);

        private Root asRoot() {
            return new Root(this.pitchRadians, this.flightTicks);
        }
    }

    private record VerticalTerms(double sineCoefficient, double constant) {
        private static final VerticalTerms ZERO = new VerticalTerms(0.0, 0.0);
    }
}
