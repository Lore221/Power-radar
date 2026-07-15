package com.limbo2136.powerradar.compat.aeronautics;

import net.minecraft.world.phys.Vec3;

public record RadarWorldPose(Vec3 origin, float yawDegrees, boolean onSableStructure) {
}
