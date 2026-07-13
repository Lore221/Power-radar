package com.limbo2136.powerradar.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import org.junit.jupiter.api.Test;

class LinearDragAimSolverAutocannonTest {
    private static final WeaponBallistics AUTOCANNON = new WeaponBallistics(
            true, "autocannon", 5.0, 0.05, 0.01, false,
            60, 4, "test", false);

    @Test
    void monotonicLifetimePathMatchesFullSolverLowRoot() {
        for (double distance : new double[] { 50.0, 100.0, 180.0 }) {
            LinearDragAimSolver.Roots fast = LinearDragAimSolver.solveAutocannonLowArc(
                    -45.0, 60.0, distance, 0.0, AUTOCANNON);
            LinearDragAimSolver.Roots full = LinearDragAimSolver.solve(
                    -45.0, 60.0, distance, 0.0, AUTOCANNON);

            assertNotNull(fast);
            assertNotNull(fast.low());
            assertNotNull(full);
            assertNotNull(full.low());
            assertEquals(full.low().pitchRadians(), fast.low().pitchRadians(), 1.0E-8);
            assertEquals(full.low().flightTicks(), fast.low().flightTicks(), 1.0E-8);
            assertNull(fast.high());
        }
    }

    @Test
    void fallsBackWhenLifetimeDoesNotRemoveTheTrajectoryPeak() {
        WeaponBallistics longLived = new WeaponBallistics(
                true, "autocannon", 5.0, 0.05, 0.01, false,
                200, 4, "test", false);

        assertNull(LinearDragAimSolver.solveAutocannonLowArc(
                -45.0, 85.0, 100.0, 0.0, longLived));
    }
}
