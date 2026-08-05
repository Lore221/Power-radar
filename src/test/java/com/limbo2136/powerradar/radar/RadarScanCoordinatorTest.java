package com.limbo2136.powerradar.radar;

import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarScanCoordinatorTest {
    @Test
    void identicalSlicesAreWorthSharing() {
        AABB slice = new AABB(0.0D, 0.0D, 0.0D, 256.0D, 128.0D, 256.0D);

        assertTrue(RadarScanCoordinator.isBeneficialMerge(slice, slice));
    }

    @Test
    void barelyOverlappingSlicesStayIndependent() {
        AABB first = new AABB(0.0D, 0.0D, 0.0D, 256.0D, 128.0D, 256.0D);
        AABB second = new AABB(240.0D, 0.0D, 0.0D, 496.0D, 128.0D, 256.0D);

        assertFalse(RadarScanCoordinator.isBeneficialMerge(first, second));
    }

    @Test
    void stronglyOverlappingSlicesShareOneQuery() {
        AABB first = new AABB(0.0D, 0.0D, 0.0D, 256.0D, 128.0D, 256.0D);
        AABB second = new AABB(64.0D, 0.0D, 0.0D, 320.0D, 128.0D, 256.0D);

        assertTrue(RadarScanCoordinator.isBeneficialMerge(first, second));
    }
}
