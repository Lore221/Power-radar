package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;

/** Проецирует направление движения цели на плоскость карты монитора. */
final class RadarBlipOrientation {
    private static final double MIN_HORIZONTAL_SPEED_SQUARED = 1.0E-8D;

    private RadarBlipOrientation() {
    }

    static float rotationDegrees(RadarDisplayTarget target, float viewYawDegrees) {
        if (target.category() != RadarTargetCategory.PROJECTILE || !target.hasVelocity()) {
            return 0.0F;
        }
        return rotationDegrees(target.velocityX(), target.velocityZ(), viewYawDegrees);
    }

    static float rotationDegrees(double velocityX, double velocityZ, float viewYawDegrees) {
        if (velocityX * velocityX + velocityZ * velocityZ < MIN_HORIZONTAL_SPEED_SQUARED) {
            return 0.0F;
        }
        double viewRadians = Math.toRadians(viewYawDegrees);
        double screenRight = velocityX * Math.cos(viewRadians) + velocityZ * Math.sin(viewRadians);
        double screenUp = velocityX * Math.sin(viewRadians) - velocityZ * Math.cos(viewRadians);
        return RadarGeometry.normalizeDegrees((float) Math.toDegrees(Math.atan2(screenRight, screenUp)));
    }
}
