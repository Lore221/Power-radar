package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class InterceptionCoordinatorThreatTtlTest {
    @Test
    void threatTtlCoversExpectedRadarScanIntervalAndSafetyMargin() {
        assertEquals(20L, InterceptionCoordinator.threatTtlTicksForScanInterval(5L));
        assertEquals(20L, InterceptionCoordinator.threatTtlTicksForScanInterval(10L));
        assertEquals(35L, InterceptionCoordinator.threatTtlTicksForScanInterval(25L));
    }

    @Test
    void threatTtlRemainsBoundedWhenScanUpdatesStopOrAreMisconfigured() {
        assertEquals(20L, InterceptionCoordinator.threatTtlTicksForScanInterval(0L));
        assertEquals(1210L, InterceptionCoordinator.threatTtlTicksForScanInterval(10_000L));
    }
}
