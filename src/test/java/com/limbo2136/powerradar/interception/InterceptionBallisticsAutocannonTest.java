package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.targeting.LinearDragAimSolver;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class InterceptionBallisticsAutocannonTest {
    private static final WeaponBallistics AUTOCANNON = new WeaponBallistics(
            true, "autocannon", 5.0, 0.05, 0.01, false,
            60, 4, "test", false);

    @Test
    void interceptionFastPathMatchesFullLowArcSolver() {
        for (Vec3 delta : new Vec3[] {
                new Vec3(50.0, 0.0, 0.0),
                new Vec3(80.0, 12.0, 60.0),
                new Vec3(140.0, -8.0, 40.0)
        }) {
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            LinearDragAimSolver.Roots full = LinearDragAimSolver.solve(
                    -45.0, 85.0, horizontal, delta.y, AUTOCANNON);
            assertNotNull(full);
            LinearDragAimSolver.Root expected = full.low() != null ? full.low() : full.high();
            assertNotNull(expected);

            InterceptionBallistics.Aim actual =
                    InterceptionBallistics.solveAutocannonLowArc(
                            delta, AUTOCANNON, Double.NaN, 0.0);
            assertTrue(actual.reachable());
            assertEquals(Math.toDegrees(expected.pitchRadians()), actual.pitchDegrees(), 1.0E-5);
            assertEquals(expected.flightTicks(), actual.flightTicks(), 1.0E-8);
        }
    }
}
