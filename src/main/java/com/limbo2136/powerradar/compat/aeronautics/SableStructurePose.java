package com.limbo2136.powerradar.compat.aeronautics;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Лёгкая проекция закэшированной локальной геометрии в текущую позу Sable. */
public record SableStructurePose(Vec3 worldOrigin, AABB worldBounds) {
}
