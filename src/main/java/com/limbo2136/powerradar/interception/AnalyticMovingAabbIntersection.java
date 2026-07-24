package com.limbo2136.powerradar.interception;

import com.limbo2136.powerradar.targeting.LinearDragTrajectory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Точное пересечение временных интервалов аналитической траектории CBC и движущегося AABB. */
final class AnalyticMovingAabbIntersection {
    private static final double TIME_TOLERANCE = 1.0E-7D;
    private static final double ROOT_VALUE_TOLERANCE = 1.0E-8D;
    private static final double POSITION_TOLERANCE = 1.0E-7D;
    private static final int NEWTON_ITERATIONS = 8;
    private static final int ROOT_ITERATIONS = 56;
    private static final int MAX_ROOT_RECURSION = 16;

    private AnalyticMovingAabbIntersection() {
    }

    static Result evaluate(
            AABB bounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            double gravity,
            double drag,
            double maximumTicks
    ) {
        if (!(maximumTicks > 0.0D) || !finite(
                bounds,
                shipVelocity,
                shipAcceleration,
                projectilePosition,
                projectileVelocity,
                gravity,
                drag,
                maximumTicks)) {
            return Result.INDETERMINATE;
        }
        Vec3 center = bounds.getCenter();
        RelativeTrajectory trajectory;
        try {
            trajectory = new RelativeTrajectory(
                    relativeAxis(
                            projectilePosition.x,
                            projectileVelocity.x,
                            0.0D,
                            drag,
                            center.x,
                            shipVelocity.x,
                            shipAcceleration.x),
                    relativeAxis(
                            projectilePosition.y,
                            projectileVelocity.y,
                            -gravity,
                            drag,
                            center.y,
                            shipVelocity.y,
                            shipAcceleration.y),
                    relativeAxis(
                            projectilePosition.z,
                            projectileVelocity.z,
                            0.0D,
                            drag,
                            center.z,
                            shipVelocity.z,
                            shipAcceleration.z));
        } catch (IllegalArgumentException ignored) {
            return Result.INDETERMINATE;
        }

        // Сфера только дёшево отвергает далёкие траектории; окончательное решение дают интервалы трёх осей.
        double sphereRadius = Math.sqrt(
                square(bounds.getXsize())
                        + square(bounds.getYsize())
                        + square(bounds.getZsize())) * 0.5D;
        ClosestApproachResult closest = closestApproachWithinSphere(
                trajectory, sphereRadius, maximumTicks);
        if (closest == ClosestApproachResult.OUTSIDE) {
            return Result.MISS;
        }

        List<TimeInterval> intervals = List.of(new TimeInterval(0.0D, maximumTicks));
        intervals = intersect(intervals, axisIntervals(
                trajectory.x(), -bounds.getXsize() * 0.5D, bounds.getXsize() * 0.5D, maximumTicks));
        if (intervals == null) {
            return Result.INDETERMINATE;
        }
        if (intervals.isEmpty()) {
            return Result.MISS;
        }
        intervals = intersect(intervals, axisIntervals(
                trajectory.y(), -bounds.getYsize() * 0.5D, bounds.getYsize() * 0.5D, maximumTicks));
        if (intervals == null) {
            return Result.INDETERMINATE;
        }
        if (intervals.isEmpty()) {
            return Result.MISS;
        }
        intervals = intersect(intervals, axisIntervals(
                trajectory.z(), -bounds.getZsize() * 0.5D, bounds.getZsize() * 0.5D, maximumTicks));
        if (intervals == null) {
            return Result.INDETERMINATE;
        }
        return intervals.isEmpty() ? Result.MISS : Result.HIT;
    }

    private static LinearDragTrajectory.AxisTrajectory relativeAxis(
            double projectilePosition,
            double projectileVelocity,
            double projectileAcceleration,
            double drag,
            double center,
            double shipVelocity,
            double shipAcceleration
    ) {
        return LinearDragTrajectory.axisTrajectory(
                        projectilePosition,
                        projectileVelocity,
                        projectileAcceleration,
                        drag)
                .relativeTo(center, shipVelocity, shipAcceleration);
    }

    private static ClosestApproachResult closestApproachWithinSphere(
            RelativeTrajectory trajectory,
            double sphereRadius,
            double maximumTicks
    ) {
        RootResult stationary = roots(distanceDerivative(trajectory), 0.0D, maximumTicks, 0);
        if (!stationary.reliable() || stationary.roots().size() > 7) {
            return ClosestApproachResult.UNKNOWN;
        }
        double minimumDistanceSqr = Math.min(
                trajectory.distanceSqr(0.0D),
                trajectory.distanceSqr(maximumTicks));
        for (double ticks : stationary.roots()) {
            minimumDistanceSqr = Math.min(minimumDistanceSqr, trajectory.distanceSqr(ticks));
        }
        if (!Double.isFinite(minimumDistanceSqr)) {
            return ClosestApproachResult.UNKNOWN;
        }
        double tolerance = POSITION_TOLERANCE * Math.max(1.0D, sphereRadius);
        return minimumDistanceSqr > square(sphereRadius + tolerance)
                ? ClosestApproachResult.OUTSIDE
                : ClosestApproachResult.INSIDE;
    }

    /** Половина d(distance²)/dt как экспоненциальный полином максимум с семью корнями. */
    private static ExponentialPolynomial distanceDerivative(RelativeTrajectory trajectory) {
        Vec3 constant = trajectory.coefficients(Coefficient.CONSTANT);
        Vec3 linear = trajectory.coefficients(Coefficient.LINEAR);
        Vec3 quadratic = trajectory.coefficients(Coefficient.QUADRATIC);
        Vec3 exponential = trajectory.coefficients(Coefficient.EXPONENTIAL);
        double logarithm = trajectory.logarithm();

        ArrayList<ExponentialTerm> terms = new ArrayList<>();
        terms.add(new ExponentialTerm(0.0D, new double[] {
                constant.dot(linear),
                linear.lengthSqr() + 2.0D * constant.dot(quadratic),
                3.0D * linear.dot(quadratic),
                2.0D * quadratic.lengthSqr()
        }));
        if (exponential.lengthSqr() > 0.0D) {
            terms.add(new ExponentialTerm(logarithm, new double[] {
                    exponential.dot(linear) + logarithm * exponential.dot(constant),
                    2.0D * exponential.dot(quadratic) + logarithm * exponential.dot(linear),
                    logarithm * exponential.dot(quadratic)
            }));
            terms.add(new ExponentialTerm(
                    logarithm * 2.0D,
                    new double[] {logarithm * exponential.lengthSqr()}));
        }
        return new ExponentialPolynomial(terms).canonical();
    }

    private static List<TimeInterval> axisIntervals(
            LinearDragTrajectory.AxisTrajectory axis,
            double minimum,
            double maximum,
            double maximumTicks
    ) {
        RootResult lowerRoots = roots(axisBoundary(axis, minimum), 0.0D, maximumTicks, 0);
        RootResult upperRoots = roots(axisBoundary(axis, maximum), 0.0D, maximumTicks, 0);
        if (!lowerRoots.reliable() || !upperRoots.reliable()) {
            return null;
        }

        ArrayList<Double> cuts = new ArrayList<>();
        cuts.add(0.0D);
        cuts.add(maximumTicks);
        cuts.addAll(lowerRoots.roots());
        cuts.addAll(upperRoots.roots());
        List<Double> sortedCuts = sortedUnique(cuts, 0.0D, maximumTicks);
        ArrayList<TimeInterval> inside = new ArrayList<>();
        for (double ticks : sortedCuts) {
            double value = axis.position(ticks);
            if (!Double.isFinite(value)) {
                return null;
            }
            if (inside(value, minimum, maximum)) {
                inside.add(new TimeInterval(ticks, ticks));
            }
        }
        for (int index = 0; index + 1 < sortedCuts.size(); index++) {
            double start = sortedCuts.get(index);
            double end = sortedCuts.get(index + 1);
            if (end - start <= TIME_TOLERANCE) {
                continue;
            }
            double value = axis.position((start + end) * 0.5D);
            if (!Double.isFinite(value)) {
                return null;
            }
            if (inside(value, minimum, maximum)) {
                inside.add(new TimeInterval(start, end));
            }
        }
        return merge(inside);
    }

    private static ExponentialPolynomial axisBoundary(
            LinearDragTrajectory.AxisTrajectory axis,
            double boundary
    ) {
        ArrayList<ExponentialTerm> terms = new ArrayList<>();
        terms.add(new ExponentialTerm(0.0D, new double[] {
                axis.constant() - boundary,
                axis.linear(),
                axis.quadratic()
        }));
        if (axis.exponential() != 0.0D) {
            terms.add(new ExponentialTerm(
                    axis.logarithm(),
                    new double[] {axis.exponential()}));
        }
        return new ExponentialPolynomial(terms).canonical();
    }

    /**
     * Изолирует каждый вещественный корень через рекурсивный поиск критических точек нормализованного
     * экспоненциального полинома. Нормализация умножает функцию на положительную экспоненту,
     * сохраняя корни и уменьшая степень старшего экспоненциального члена после дифференцирования.
     */
    private static RootResult roots(
            ExponentialPolynomial source,
            double minimum,
            double maximum,
            int recursion
    ) {
        if (recursion > MAX_ROOT_RECURSION || !(maximum >= minimum)) {
            return RootResult.unreliable();
        }
        ExponentialPolynomial function = source.canonical().normalized();
        if (!function.finite()) {
            return RootResult.unreliable();
        }
        if (function.zero() || function.order() <= 1) {
            return RootResult.reliable(List.of());
        }

        ExponentialPolynomial derivative = function.derivative().canonical();
        RootResult critical = roots(derivative, minimum, maximum, recursion + 1);
        if (!critical.reliable()) {
            return critical;
        }
        ArrayList<Double> cuts = new ArrayList<>();
        cuts.add(minimum);
        cuts.add(maximum);
        cuts.addAll(critical.roots());
        List<Double> sortedCuts = sortedUnique(cuts, minimum, maximum);
        ArrayList<Double> result = new ArrayList<>();
        for (double ticks : sortedCuts) {
            Evaluation evaluation = function.evaluate(ticks);
            if (!evaluation.finite()) {
                return RootResult.unreliable();
            }
            if (evaluation.zero()) {
                result.add(ticks);
            }
        }
        for (int index = 0; index + 1 < sortedCuts.size(); index++) {
            double start = sortedCuts.get(index);
            double end = sortedCuts.get(index + 1);
            if (end - start <= TIME_TOLERANCE) {
                continue;
            }
            Evaluation startEvaluation = function.evaluate(start);
            Evaluation endEvaluation = function.evaluate(end);
            if (!startEvaluation.finite() || !endEvaluation.finite()) {
                return RootResult.unreliable();
            }
            if (startEvaluation.zero() || endEvaluation.zero()
                    || Math.copySign(1.0D, startEvaluation.value())
                            == Math.copySign(1.0D, endEvaluation.value())) {
                continue;
            }
            Double root = solveBracketed(
                    function,
                    derivative,
                    start,
                    end,
                    startEvaluation.value(),
                    endEvaluation.value());
            if (root == null) {
                return RootResult.unreliable();
            }
            result.add(root);
        }
        return RootResult.reliable(sortedUnique(result, minimum, maximum));
    }

    private static Double solveBracketed(
            ExponentialPolynomial function,
            ExponentialPolynomial derivative,
            double minimum,
            double maximum,
            double minimumValue,
            double maximumValue
    ) {
        double low = minimum;
        double high = maximum;
        double lowValue = minimumValue;
        double current = falsePosition(low, high, lowValue, maximumValue);
        for (int iteration = 0; iteration < ROOT_ITERATIONS; iteration++) {
            Evaluation evaluation = function.evaluate(current);
            if (!evaluation.finite()) {
                return null;
            }
            if (evaluation.zero() || high - low <= TIME_TOLERANCE) {
                return Math.clamp(current, minimum, maximum);
            }
            if (Math.copySign(1.0D, lowValue) == Math.copySign(1.0D, evaluation.value())) {
                low = current;
                lowValue = evaluation.value();
            } else {
                high = current;
            }

            double candidate = Double.NaN;
            if (iteration < NEWTON_ITERATIONS) {
                Evaluation derivativeEvaluation = derivative.evaluate(current);
                if (derivativeEvaluation.finite()
                        && Math.abs(derivativeEvaluation.value()) > 1.0E-14D) {
                    candidate = current - evaluation.value() / derivativeEvaluation.value();
                }
            }
            if (!(candidate > low && candidate < high) || !Double.isFinite(candidate)) {
                candidate = (low + high) * 0.5D;
            }
            current = candidate;
        }
        return high - low <= TIME_TOLERANCE ? (low + high) * 0.5D : null;
    }

    private static double falsePosition(
            double minimum,
            double maximum,
            double minimumValue,
            double maximumValue
    ) {
        double denominator = maximumValue - minimumValue;
        if (!Double.isFinite(denominator) || Math.abs(denominator) <= 1.0E-14D) {
            return (minimum + maximum) * 0.5D;
        }
        double candidate = minimum
                - minimumValue * (maximum - minimum) / denominator;
        return candidate > minimum && candidate < maximum
                ? candidate
                : (minimum + maximum) * 0.5D;
    }

    private static List<TimeInterval> intersect(
            List<TimeInterval> first,
            List<TimeInterval> second
    ) {
        if (first == null || second == null) {
            return null;
        }
        ArrayList<TimeInterval> result = new ArrayList<>();
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.size() && secondIndex < second.size()) {
            TimeInterval left = first.get(firstIndex);
            TimeInterval right = second.get(secondIndex);
            double start = Math.max(left.start(), right.start());
            double end = Math.min(left.end(), right.end());
            if (start <= end + TIME_TOLERANCE) {
                result.add(new TimeInterval(start, Math.max(start, end)));
            }
            if (left.end() < right.end()) {
                firstIndex++;
            } else {
                secondIndex++;
            }
        }
        return merge(result);
    }

    private static List<TimeInterval> merge(List<TimeInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        ArrayList<TimeInterval> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingDouble(TimeInterval::start));
        ArrayList<TimeInterval> merged = new ArrayList<>();
        TimeInterval current = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            TimeInterval next = sorted.get(index);
            if (next.start() <= current.end() + TIME_TOLERANCE) {
                current = new TimeInterval(current.start(), Math.max(current.end(), next.end()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    private static List<Double> sortedUnique(
            List<Double> values,
            double minimum,
            double maximum
    ) {
        ArrayList<Double> sorted = new ArrayList<>();
        for (double value : values) {
            if (Double.isFinite(value)
                    && value >= minimum - TIME_TOLERANCE
                    && value <= maximum + TIME_TOLERANCE) {
                sorted.add(Math.clamp(value, minimum, maximum));
            }
        }
        sorted.sort(Double::compare);
        ArrayList<Double> unique = new ArrayList<>();
        for (double value : sorted) {
            if (unique.isEmpty()
                    || Math.abs(value - unique.getLast()) > TIME_TOLERANCE) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    private static boolean inside(double value, double minimum, double maximum) {
        double tolerance = POSITION_TOLERANCE
                * Math.max(1.0D, Math.max(Math.abs(minimum), Math.abs(maximum)));
        return value >= minimum - tolerance && value <= maximum + tolerance;
    }

    private static boolean finite(
            AABB bounds,
            Vec3 first,
            Vec3 second,
            Vec3 third,
            Vec3 fourth,
            double... values
    ) {
        return finite(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ)
                && finite(first) && finite(second) && finite(third) && finite(fourth)
                && finite(values);
    }

    private static boolean finite(Vec3 value) {
        return finite(value.x, value.y, value.z);
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private static double square(double value) {
        return value * value;
    }

    enum Result {
        HIT,
        MISS,
        INDETERMINATE
    }

    private enum ClosestApproachResult {
        INSIDE,
        OUTSIDE,
        UNKNOWN
    }

    private enum Coefficient {
        CONSTANT,
        LINEAR,
        QUADRATIC,
        EXPONENTIAL
    }

    private record RelativeTrajectory(
            LinearDragTrajectory.AxisTrajectory x,
            LinearDragTrajectory.AxisTrajectory y,
            LinearDragTrajectory.AxisTrajectory z
    ) {
        private double distanceSqr(double ticks) {
            return square(this.x.position(ticks))
                    + square(this.y.position(ticks))
                    + square(this.z.position(ticks));
        }

        private Vec3 coefficients(Coefficient coefficient) {
            return switch (coefficient) {
                case CONSTANT -> new Vec3(this.x.constant(), this.y.constant(), this.z.constant());
                case LINEAR -> new Vec3(this.x.linear(), this.y.linear(), this.z.linear());
                case QUADRATIC -> new Vec3(this.x.quadratic(), this.y.quadratic(), this.z.quadratic());
                case EXPONENTIAL -> new Vec3(
                        this.x.exponential(), this.y.exponential(), this.z.exponential());
            };
        }

        private double logarithm() {
            return this.x.logarithm();
        }
    }

    private record TimeInterval(double start, double end) {
    }

    private record Evaluation(double value, double scale) {
        private boolean finite() {
            return Double.isFinite(this.value) && Double.isFinite(this.scale);
        }

        private boolean zero() {
            return Math.abs(this.value) <= ROOT_VALUE_TOLERANCE;
        }
    }

    private record RootResult(List<Double> roots, boolean reliable) {
        private static RootResult reliable(List<Double> roots) {
            return new RootResult(roots, true);
        }

        private static RootResult unreliable() {
            return new RootResult(List.of(), false);
        }
    }

    private record ExponentialTerm(double exponent, double[] coefficients) {
        private ExponentialTerm {
            coefficients = coefficients.clone();
        }

        private ExponentialTerm normalized(double highestExponent) {
            return new ExponentialTerm(this.exponent - highestExponent, this.coefficients);
        }

        private ExponentialTerm derivative() {
            double[] derivative = new double[Math.max(1, this.coefficients.length)];
            for (int index = 0; index < this.coefficients.length; index++) {
                derivative[index] += this.exponent * this.coefficients[index];
                if (index > 0) {
                    derivative[index - 1] += index * this.coefficients[index];
                }
            }
            return new ExponentialTerm(this.exponent, derivative);
        }
    }

    private record ExponentialPolynomial(List<ExponentialTerm> terms) {
        private ExponentialPolynomial {
            terms = List.copyOf(terms);
        }

        private ExponentialPolynomial canonical() {
            ArrayList<ExponentialTerm> canonical = new ArrayList<>();
            for (ExponentialTerm term : this.terms) {
                int length = term.coefficients().length;
                while (length > 0 && term.coefficients()[length - 1] == 0.0D) {
                    length--;
                }
                if (length == 0) {
                    continue;
                }
                double[] coefficients = new double[length];
                System.arraycopy(term.coefficients(), 0, coefficients, 0, length);
                canonical.add(new ExponentialTerm(term.exponent(), coefficients));
            }
            canonical.sort(Comparator.comparingDouble(ExponentialTerm::exponent).reversed());
            return new ExponentialPolynomial(canonical);
        }

        private ExponentialPolynomial normalized() {
            if (this.terms.isEmpty()) {
                return this;
            }
            double highestExponent = this.terms.getFirst().exponent();
            ArrayList<ExponentialTerm> normalized = new ArrayList<>();
            for (ExponentialTerm term : this.terms) {
                normalized.add(term.normalized(highestExponent));
            }
            return new ExponentialPolynomial(normalized).canonical();
        }

        private ExponentialPolynomial derivative() {
            ArrayList<ExponentialTerm> derivative = new ArrayList<>();
            for (ExponentialTerm term : this.terms) {
                derivative.add(term.derivative());
            }
            return new ExponentialPolynomial(derivative);
        }

        private Evaluation evaluate(double ticks) {
            double value = 0.0D;
            double compensation = 0.0D;
            double scale = 0.0D;
            for (ExponentialTerm term : this.terms) {
                double polynomial = 0.0D;
                for (int index = term.coefficients().length - 1; index >= 0; index--) {
                    polynomial = polynomial * ticks + term.coefficients()[index];
                }
                double exponent = term.exponent() * ticks;
                double contribution = polynomial * Math.exp(exponent);
                if (term.exponent() != 0.0D && Math.abs(exponent) < 0.5D) {
                    double adjusted = polynomial - compensation;
                    double sum = value + adjusted;
                    compensation = (sum - value) - adjusted;
                    value = sum;
                    adjusted = polynomial * Math.expm1(exponent) - compensation;
                    sum = value + adjusted;
                    compensation = (sum - value) - adjusted;
                    value = sum;
                } else {
                    double adjusted = contribution - compensation;
                    double sum = value + adjusted;
                    compensation = (sum - value) - adjusted;
                    value = sum;
                }
                scale += Math.abs(contribution);
            }
            return new Evaluation(value, scale);
        }

        private int order() {
            int order = 0;
            for (ExponentialTerm term : this.terms) {
                order += term.coefficients().length;
            }
            return order;
        }

        private boolean zero() {
            return this.terms.isEmpty();
        }

        private boolean finite() {
            for (ExponentialTerm term : this.terms) {
                if (!Double.isFinite(term.exponent())
                        || !AnalyticMovingAabbIntersection.finite(term.coefficients())) {
                    return false;
                }
            }
            return true;
        }
    }
}
