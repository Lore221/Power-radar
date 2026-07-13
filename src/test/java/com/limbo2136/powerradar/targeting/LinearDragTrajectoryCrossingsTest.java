package com.limbo2136.powerradar.targeting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class LinearDragTrajectoryCrossingsTest {
    @Test
    void combinedStateMatchesSeparatePositionAndVelocityEvaluation() {
        Vec3 position = new Vec3(4.0, 70.0, -3.0);
        Vec3 velocity = new Vec3(2.5, 1.25, -0.75);
        double gravity = 0.05;
        double drag = 0.01;
        double ticks = 37.25;

        LinearDragTrajectory.TrajectoryState state = LinearDragTrajectory.stateAfterTicks(
                position, velocity, gravity, drag, ticks);
        assertVecEquals(
                LinearDragTrajectory.positionAfterTicks(position, velocity, gravity, drag, ticks),
                state.position());
        assertVecEquals(
                LinearDragTrajectory.velocityAfterTicks(velocity, gravity, drag, ticks),
                state.velocity());
    }

    @Test
    void bundledCrossingsMatchIndependentPlaneSolutions() {
        Vec3 position = new Vec3(12.0, 100.0, -8.0);
        Vec3 velocity = new Vec3(3.0, 2.0, -1.25);
        double gravity = 0.05;
        double drag = 0.01;
        double upperPlane = 20.0;
        double lowerPlane = -20.0;
        double maximumTicks = 600.0;

        LinearDragTrajectory.DescendingPlaneCrossings crossings =
                LinearDragTrajectory.descendingPlaneCrossings(
                        position, velocity, gravity, drag,
                        upperPlane, lowerPlane, maximumTicks);
        assertNotNull(crossings);

        Double upperTicks = LinearDragTrajectory.descendingPlaneCrossingTicks(
                position.y, velocity.y, gravity, drag, upperPlane, maximumTicks);
        Double lowerTicks = LinearDragTrajectory.descendingPlaneCrossingTicks(
                position.y, velocity.y, gravity, drag, lowerPlane, maximumTicks);
        assertNotNull(upperTicks);
        assertNotNull(lowerTicks);
        assertEquals(upperTicks, crossings.upper().ticks(), 1.0E-5);
        assertEquals(lowerTicks, crossings.lower().ticks(), 1.0E-5);
        assertEquals(upperPlane, crossings.upper().position().y, 1.0E-5);
        assertEquals(lowerPlane, crossings.lower().position().y, 1.0E-5);

        Vec3 expectedUpperPosition = LinearDragTrajectory.positionAfterTicks(
                position, velocity, gravity, drag, upperTicks);
        Vec3 expectedLowerPosition = LinearDragTrajectory.positionAfterTicks(
                position, velocity, gravity, drag, lowerTicks);
        Vec3 expectedLowerVelocity = LinearDragTrajectory.velocityAfterTicks(
                velocity, gravity, drag, lowerTicks);
        assertVecEquals(expectedUpperPosition, crossings.upper().position());
        assertVecEquals(expectedLowerPosition, crossings.lower().position());
        assertVecEquals(expectedLowerVelocity, crossings.lower().velocity());
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0E-5);
        assertEquals(expected.y, actual.y, 1.0E-5);
        assertEquals(expected.z, actual.z, 1.0E-5);
    }
}
