package com.limbo2136.powerradar.compat.aeronautics;

import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record SableStructureObservation(
        UUID structureUuid,
        String displayName,
        Vec3 worldOrigin,
        Vec3 velocity,
        float headingDegrees,
        AABB worldBounds
) {
}
