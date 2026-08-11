package com.limbo2136.powerradar.client;

/** Плавно интерполирует плоский курс структуры по кратчайшей дуге. */
final class SableHeadingInterpolator {
    private SableHeadingInterpolator() {
    }

    static float interpolate(float fromDegrees, float toDegrees, float amount) {
        float clamped = Math.min(1.0F, Math.max(0.0F, amount));
        return normalize(fromDegrees + shortestDelta(fromDegrees, toDegrees) * clamped);
    }

    static float shortestDelta(float fromDegrees, float toDegrees) {
        float delta = normalize(toDegrees) - normalize(fromDegrees);
        if (delta > 180.0F) {
            delta -= 360.0F;
        } else if (delta < -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    static float normalize(float degrees) {
        float normalized = degrees % 360.0F;
        return normalized < 0.0F ? normalized + 360.0F : normalized;
    }
}
