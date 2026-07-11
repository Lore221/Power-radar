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
}
