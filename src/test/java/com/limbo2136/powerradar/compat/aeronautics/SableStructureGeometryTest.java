package com.limbo2136.powerradar.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SableStructureGeometryTest {
    @Test
    void enclosingWorldBoundsFollowsCurrentRollInsteadOfOnlyTranslatingOldAabb() {
        AABB local = new AABB(-5.0D, -1.0D, -2.0D, 5.0D, 1.0D, 2.0D);

        AABB rolled = SableStructureGeometry.enclosingWorldBounds(
                local,
                point -> new Vec3(100.0D - point.y, 50.0D + point.x, -3.0D + point.z));

        assertEquals(new AABB(99.0D, 45.0D, -5.0D, 101.0D, 55.0D, -1.0D), rolled);
        assertEquals(2.0D, rolled.getXsize(), 1.0E-9D);
        assertEquals(10.0D, rolled.getYsize(), 1.0E-9D);
    }
}
