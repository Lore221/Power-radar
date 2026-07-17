package com.limbo2136.powerradar.compat.aeronautics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lightweight current-pose projection of cached local Sable geometry. */
public record SableStructurePose(Vec3 worldOrigin, AABB worldBounds) {
}
