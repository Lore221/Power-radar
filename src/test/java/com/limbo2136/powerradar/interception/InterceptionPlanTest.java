package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InterceptionPlanTest {
    @Test
    void earlyAimWaitsForLaunchWindowInsteadOfDiscardingPlan() {
        double error = InterceptionPlan.signedTimingError(80.0, 10.0, 40.0);

        assertFalse(InterceptionPlan.deadlineMissed(error, 3.0));
    }

    @Test
    void planIsDiscardedOnlyAfterTheLaunchDeadlineIsMissed() {
        double error = InterceptionPlan.signedTimingError(40.0, 8.0, 36.0);

        assertTrue(InterceptionPlan.deadlineMissed(error, 3.0));
    }

    @Test
    void sableChoosesTheLargestReserveLeftForCorrections() {
        double candidate = InterceptionPlan.correctionReserveTicks(60.0, 10.0, 35.0);
        double current = InterceptionPlan.correctionReserveTicks(60.0, 12.0, 36.0);

        assertTrue(InterceptionPlan.hasMeaningfullyLargerCorrectionReserve(candidate, current, 0.25));
        assertFalse(InterceptionPlan.hasMeaningfullyLargerCorrectionReserve(12.1, 12.0, 0.25));
    }
}
