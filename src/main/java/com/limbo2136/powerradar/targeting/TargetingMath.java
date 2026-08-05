package com.limbo2136.powerradar.targeting;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class TargetingMath {
    private TargetingMath() {
    }

    public static double horizontalDistance(Vec3 delta) {
        return Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    }

    public static float yawTo(Vec3 delta) {
        return normalize360((float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) + 270.0));
    }

    public static float normalize360(float degrees) {
        float wrapped = Mth.wrapDegrees(degrees);
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    public static Vec3 directionFromAngles(float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vec3(
                -Math.sin(yaw) * horizontal,
                Math.sin(pitch),
                Math.cos(yaw) * horizontal);
    }

    public static double approach(double current, double target, double maxDelta) {
        if (current < target) {
            return Math.min(target, current + maxDelta);
        }
        if (current > target) {
            return Math.max(target, current - maxDelta);
        }
        return current;
    }
}
