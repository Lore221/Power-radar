package com.limbo2136.powerradar.interception;

import java.util.function.DoubleFunction;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** Ранняя встреча с достаточным запасом вместо максимизации времени ожидания. */
public final class SableInterceptSearch {
    public static final double CORRECTION_RESERVE_TICKS = 5.0D;
    private static final double COARSE_STEP = 5.0D;
    private static final double FINE_STEP = 0.5D;
    private static final double TRANSLATION_EPSILON_SQR = 0.01D * 0.01D;
    private static final double ROTATION_COSINE = Math.cos(Math.toRadians(0.05D));

    private SableInterceptSearch() { }

    @Nullable
    public static <T> T find(double minimumTicks, double maximumTicks, DoubleFunction<T> evaluate,
            Predicate<T> usable, ToDoubleFunction<T> reserve) {
        if (maximumTicks < minimumTicks) return null;
        T fallback = null;
        double previousTick = minimumTicks;
        int steps = (int) Math.ceil((maximumTicks - minimumTicks) / COARSE_STEP);
        for (int index = 0; index <= steps; index++) {
            double tick = Math.min(maximumTicks, minimumTicks + index * COARSE_STEP);
            T candidate = evaluate.apply(tick);
            if (usable.test(candidate) && reserve.applyAsDouble(candidate) >= 0.0D) {
                // Уточняем начало подходящего окна, а не весь остаток траектории.
                for (double fineTick = previousTick; fineTick < tick; fineTick += FINE_STEP) {
                    T refined = evaluate.apply(fineTick);
                    if (!usable.test(refined) || reserve.applyAsDouble(refined) < 0.0D) continue;
                    if (fallback == null) fallback = refined;
                    if (reserve.applyAsDouble(refined) >= CORRECTION_RESERVE_TICKS) return refined;
                }
                if (fallback == null) fallback = candidate;
                if (reserve.applyAsDouble(candidate) >= CORRECTION_RESERVE_TICKS) return candidate;
            }
            previousTick = tick;
        }
        return fallback;
    }

    public static boolean earlierReplacement(double candidateTicks, double currentTicks) {
        return candidateTicks + 1.0D < currentTicks;
    }

    public record Pose(Vec3 origin, Vec3 forward, Vec3 up) {
        public boolean differsFrom(@Nullable Pose previous) {
            return previous == null || this.origin.distanceToSqr(previous.origin) > TRANSLATION_EPSILON_SQR
                    || this.forward.normalize().dot(previous.forward.normalize()) < ROTATION_COSINE
                    || this.up.normalize().dot(previous.up.normalize()) < ROTATION_COSINE;
        }
    }
}
