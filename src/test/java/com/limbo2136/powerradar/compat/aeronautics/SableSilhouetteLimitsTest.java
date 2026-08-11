package com.limbo2136.powerradar.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SableSilhouetteLimitsTest {
    @Test
    void acceptsGeometryWithinOneMiB() {
        assertTrue(SableSilhouetteLimits.accepts(32_768, 32_768));
    }

    @Test
    void rejectsExcessiveElementCounts() {
        assertFalse(SableSilhouetteLimits.accepts(32_769, 0));
        assertFalse(SableSilhouetteLimits.accepts(0, 32_769));
    }
}
