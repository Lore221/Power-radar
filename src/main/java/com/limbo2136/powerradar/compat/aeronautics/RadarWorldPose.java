package com.limbo2136.powerradar.compat.aeronautics;

import net.minecraft.world.phys.Vec3;

public record RadarWorldPose(
        Vec3 origin,
        float yawDegrees,
        boolean onSableStructure,
        Vec3 forward
) {
    public RadarWorldPose(Vec3 origin, float yawDegrees, boolean onSableStructure) {
        this(origin, yawDegrees, onSableStructure, forwardFromYaw(yawDegrees));
    }

    private static Vec3 forwardFromYaw(float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        return new Vec3(Math.sin(radians), 0.0D, -Math.cos(radians));
    }
}
