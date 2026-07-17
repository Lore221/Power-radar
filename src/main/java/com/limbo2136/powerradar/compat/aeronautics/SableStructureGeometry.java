package com.limbo2136.powerradar.compat.aeronautics;

import java.util.function.UnaryOperator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Geometry-only Sable sample; deliberately performs no velocity query. */
public record SableStructureGeometry(
        AABB localBounds,
        Vec3 localCenter,
        Vec3 worldOrigin,
        AABB worldBounds
) {
    static AABB enclosingWorldBounds(AABB localBounds, UnaryOperator<Vec3> transform) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (int xIndex = 0; xIndex < 2; xIndex++) {
            double x = xIndex == 0 ? localBounds.minX : localBounds.maxX;
            for (int yIndex = 0; yIndex < 2; yIndex++) {
                double y = yIndex == 0 ? localBounds.minY : localBounds.maxY;
                for (int zIndex = 0; zIndex < 2; zIndex++) {
                    double z = zIndex == 0 ? localBounds.minZ : localBounds.maxZ;
                    Vec3 world = transform.apply(new Vec3(x, y, z));
                    minX = Math.min(minX, world.x);
                    minY = Math.min(minY, world.y);
                    minZ = Math.min(minZ, world.z);
                    maxX = Math.max(maxX, world.x);
                    maxY = Math.max(maxY, world.y);
                    maxZ = Math.max(maxZ, world.z);
                }
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
