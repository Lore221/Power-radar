package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import com.limbo2136.powerradar.targeting.LinearDragTrajectory;
import java.util.Random;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MovingAabbThreatEvaluatorTest {
    private static final ShellAlarmCbcCompat.Ballistics VACUUM =
            new ShellAlarmCbcCompat.Ballistics(0.0D, 0.0D, false);
    private static final AABB SHIP = new AABB(-2.0D, -1.0D, -2.0D, 2.0D, 1.0D, 2.0D);

    @Test
    void protectedBoundsAddsTenPercentToEveryDimension() {
        AABB protectedBounds = MovingAabbThreatEvaluator.protectedBounds(
                new AABB(0.0D, 0.0D, 0.0D, 10.0D, 20.0D, 30.0D),
                0.0D);

        assertEquals(-0.5D, protectedBounds.minX, 1.0E-9D);
        assertEquals(10.5D, protectedBounds.maxX, 1.0E-9D);
        assertEquals(-1.0D, protectedBounds.minY, 1.0E-9D);
        assertEquals(21.0D, protectedBounds.maxY, 1.0E-9D);
        assertEquals(-1.5D, protectedBounds.minZ, 1.0E-9D);
        assertEquals(31.5D, protectedBounds.maxZ, 1.0E-9D);
    }

    @Test
    void detectsProjectileCrossingStationaryShip() {
        assertTrue(threatens(
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(30.0D, 0.0D, 0.0D),
                new Vec3(-2.0D, 0.0D, 0.0D)));
    }

    @Test
    void detectsProjectileThatCrossesEntireZoneBetweenTicks() {
        assertTrue(threatens(
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(-20.0D, 0.0D, 0.0D)));
    }

    @Test
    void detectsStationaryProjectileAlreadyInsideZone() {
        assertTrue(threatens(
                Vec3.ZERO,
                Vec3.ZERO,
                Vec3.ZERO,
                Vec3.ZERO));
    }

    @Test
    void exactZoneModeDoesNotAddShipSafetyMargin() {
        AABB exact = MovingAabbThreatEvaluator.protectedBounds(SHIP, 0.0D, 0.0D);

        assertEquals(SHIP, exact);
    }

    @Test
    void sableExpansionPercentIsTotalDimensionGrowth() {
        assertEquals(0.0D, MovingProtectedZone.safetyMarginPerSide(0.0D), 1.0E-9D);
        assertEquals(0.05D, MovingProtectedZone.safetyMarginPerSide(10.0D), 1.0E-9D);
        assertEquals(0.5D, MovingProtectedZone.safetyMarginPerSide(100.0D), 1.0E-9D);

        AABB doubled = MovingAabbThreatEvaluator.protectedBounds(
                new AABB(0.0D, 0.0D, 0.0D, 10.0D, 20.0D, 30.0D),
                0.0D,
                MovingProtectedZone.safetyMarginPerSide(100.0D));
        assertEquals(20.0D, doubled.getXsize(), 1.0E-9D);
        assertEquals(40.0D, doubled.getYsize(), 1.0E-9D);
        assertEquals(60.0D, doubled.getZsize(), 1.0E-9D);
    }

    @Test
    void initialBroadPhaseRejectsProjectileMovingAway() {
        MovingProtectedZone zone = new MovingProtectedZone(
                SHIP, Vec3.ZERO, Vec3.ZERO, 0L, null, 0.0D);

        assertFalse(MovingAabbThreatEvaluator.passesInitialBroadPhase(
                zone,
                new Vec3(30.0D, 0.0D, 0.0D),
                new Vec3(2.0D, 0.0D, 0.0D),
                0.1D,
                100.0D));
        assertTrue(MovingAabbThreatEvaluator.passesInitialBroadPhase(
                zone,
                new Vec3(30.0D, 0.0D, 0.0D),
                new Vec3(-2.0D, 0.0D, 0.0D),
                0.1D,
                100.0D));
    }

    @Test
    void rejectsProjectilePassingOutsideShip() {
        assertFalse(threatens(
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(30.0D, 0.0D, 8.0D),
                new Vec3(-2.0D, 0.0D, 0.0D)));
    }

    @Test
    void accountsForShipTranslation() {
        assertTrue(threatens(
                new Vec3(1.0D, 0.0D, 0.0D),
                Vec3.ZERO,
                new Vec3(20.0D, 0.0D, 0.0D),
                new Vec3(0.5D, 0.0D, 0.0D)));
    }

    @Test
    void rejectsProjectilePassingAboveShip() {
        assertFalse(threatens(
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(30.0D, 6.0D, 0.0D),
                new Vec3(-2.0D, 0.0D, 0.0D)));
    }

    @Test
    void detectsExactVacuumTangencyBetweenFormerSubsteps() {
        AABB bounds = new AABB(-1.0D, 0.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        ShellAlarmCbcCompat.Ballistics ballistics =
                new ShellAlarmCbcCompat.Ballistics(1.0D / 16.0D, 0.0D, false);

        assertTrue(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.0D, -81.0D / 2048.0D, 0.0D),
                new Vec3(0.0D, 9.0D / 128.0D, 0.0D),
                ballistics,
                2.0D));
        assertFalse(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.0D, -81.0D / 2048.0D - 1.0E-4D, 0.0D),
                new Vec3(0.0D, 9.0D / 128.0D, 0.0D),
                ballistics,
                2.0D));
    }

    @Test
    void rejectsFormerChordFalsePositive() {
        AABB bounds = new AABB(
                1.124D, -0.641625D, -1.0D,
                1.126D, -0.639625D, 1.0D);

        assertFalse(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(1.0D, 0.0D, 0.0D),
                new ShellAlarmCbcCompat.Ballistics(1.0D, 0.0D, false),
                2.0D));
    }

    @Test
    void detectsLinearDragTangencyBetweenFormerSubsteps() {
        AABB bounds = new AABB(-1.0D, 0.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        ShellAlarmCbcCompat.Ballistics ballistics =
                new ShellAlarmCbcCompat.Ballistics(0.05D, 0.01D, false);

        assertEquals(AnalyticMovingAabbIntersection.Result.HIT, analyticResult(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.0D, -2.665430403081649D, 0.0D),
                new Vec3(0.0D, 0.535540128343639D, 0.0D),
                ballistics,
                20.0D));
        assertTrue(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.0D, -2.665430403081649D, 0.0D),
                new Vec3(0.0D, 0.535540128343639D, 0.0D),
                ballistics,
                20.0D));
        assertFalse(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.0D, -2.665530403081649D, 0.0D),
                new Vec3(0.0D, 0.535540128343639D, 0.0D),
                ballistics,
                20.0D));
    }

    @Test
    void findsLaterHitAfterEarlierClosestApproach() {
        AABB bounds = new AABB(-3.0D, -2.3D, -1.45D, 3.0D, 2.3D, 1.45D);

        assertEquals(AnalyticMovingAabbIntersection.Result.HIT, analyticResult(
                bounds,
                Vec3.ZERO,
                new Vec3(0.0D, -0.4D, 0.0D),
                new Vec3(-10.0D, 16.0D, -4.0D),
                new Vec3(1.0D, -4.0D, 0.2D),
                VACUUM,
                15.0D));
        assertTrue(threatensExact(
                bounds,
                Vec3.ZERO,
                new Vec3(0.0D, -0.4D, 0.0D),
                new Vec3(-10.0D, 16.0D, -4.0D),
                new Vec3(1.0D, -4.0D, 0.2D),
                VACUUM,
                15.0D));
    }

    @Test
    void rejectsPathInsideDescribedSphereButOutsideLongAabb() {
        AABB bounds = new AABB(-50.0D, -1.0D, -2.0D, 50.0D, 1.0D, 2.0D);

        assertFalse(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(-100.0D, 40.0D, 0.0D),
                new Vec3(5.0D, 0.0D, 0.0D),
                VACUUM,
                30.0D));
    }

    @Test
    void accountsForLateralSableAcceleration() {
        AABB bounds = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);

        assertTrue(threatensExact(
                bounds,
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 0.1D),
                new Vec3(30.0D, 0.0D, 5.0D),
                new Vec3(-3.0D, 0.0D, 0.0D),
                VACUUM,
                12.0D));
        assertFalse(threatensExact(
                bounds,
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -0.1D),
                new Vec3(30.0D, 0.0D, 5.0D),
                new Vec3(-3.0D, 0.0D, 0.0D),
                VACUUM,
                12.0D));
    }

    @Test
    void keepsSeparateAscendingAndDescendingHighArcIntervals() {
        ShellAlarmCbcCompat.Ballistics ballistics =
                new ShellAlarmCbcCompat.Ballistics(0.05D, 0.0D, false);
        Vec3 velocity = new Vec3(2.0D, 2.0D, 0.0D);

        assertTrue(threatensExact(
                new AABB(119.0D, 29.0D, -1.0D, 121.0D, 31.0D, 1.0D),
                Vec3.ZERO,
                Vec3.ZERO,
                Vec3.ZERO,
                velocity,
                ballistics,
                80.0D));
        assertFalse(threatensExact(
                new AABB(119.0D, 30.51D, -1.0D, 121.0D, 31.49D, 1.0D),
                Vec3.ZERO,
                Vec3.ZERO,
                Vec3.ZERO,
                velocity,
                ballistics,
                80.0D));
    }

    @Test
    void includesAabbFacesAndSimulationHorizon() {
        AABB bounds = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);

        assertTrue(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(3.0D, 0.0D, 0.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                VACUUM,
                2.0D));
        assertFalse(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(3.0D, 0.0D, 0.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                VACUUM,
                1.99D));
    }

    @Test
    void handlesConstantCoordinateOnAabbFaceWithLinearDrag() {
        AABB bounds = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        ShellAlarmCbcCompat.Ballistics ballistics =
                new ShellAlarmCbcCompat.Ballistics(0.0D, 0.01D, false);

        assertTrue(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(10.0D, 1.0D, 0.0D),
                new Vec3(-2.0D, 0.0D, 0.0D),
                ballistics,
                20.0D));
        assertFalse(threatensExact(
                bounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(10.0D, 1.0001D, 0.0D),
                new Vec3(-2.0D, 0.0D, 0.0D),
                ballistics,
                20.0D));
    }

    @Test
    void remainsStableNearSupportedDragLimits() {
        AABB ordinaryBounds = new AABB(-1.0D, -1.0D, -1.0D, 1.0D, 1.0D, 1.0D);
        assertTrue(threatensExact(
                ordinaryBounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(10.0D, 0.0D, 0.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                new ShellAlarmCbcCompat.Ballistics(0.0D, 1.0001E-6D, false),
                20.0D));

        AABB narrowBounds = new AABB(-0.01D, -1.0D, -1.0D, 0.01D, 1.0D, 1.0D);
        ShellAlarmCbcCompat.Ballistics nearTotalDrag =
                new ShellAlarmCbcCompat.Ballistics(0.0D, 0.999999D, false);
        assertTrue(threatensExact(
                narrowBounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.4D, 0.0D, 0.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                nearTotalDrag,
                10.0D));
        assertFalse(threatensExact(
                narrowBounds,
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.6D, 0.0D, 0.0D),
                new Vec3(-1.0D, 0.0D, 0.0D),
                nearTotalDrag,
                10.0D));
    }

    @Test
    void analyticIntervalsAgreeWithFineIndependentReferenceAwayFromBoundary() {
        Random random = new Random(0x51A8_1E5DL);
        int verified = 0;
        for (int scenario = 0; scenario < 160; scenario++) {
            double halfX = randomBetween(random, 1.0D, 5.0D);
            double halfY = randomBetween(random, 1.0D, 3.0D);
            double halfZ = randomBetween(random, 1.0D, 5.0D);
            AABB bounds = new AABB(-halfX, -halfY, -halfZ, halfX, halfY, halfZ);
            Vec3 shipVelocity = randomVec(random, -0.5D, 0.5D);
            Vec3 shipAcceleration = randomVec(random, -0.02D, 0.02D);
            Vec3 projectilePosition = randomVec(random, -20.0D, 20.0D);
            Vec3 projectileVelocity = randomVec(random, -3.0D, 3.0D);
            double gravity = randomBetween(random, 0.0D, 0.08D);
            double drag = switch (scenario % 3) {
                case 0 -> 0.0D;
                case 1 -> 0.01D;
                default -> 0.05D;
            };
            double maximumTicks = 20.0D;

            FineReference reference = fineReference(
                    bounds,
                    shipVelocity,
                    shipAcceleration,
                    projectilePosition,
                    projectileVelocity,
                    gravity,
                    drag,
                    maximumTicks);
            if (reference == FineReference.AMBIGUOUS) {
                continue;
            }
            AnalyticMovingAabbIntersection.Result actual = AnalyticMovingAabbIntersection.evaluate(
                    bounds,
                    shipVelocity,
                    shipAcceleration,
                    projectilePosition,
                    projectileVelocity,
                    gravity,
                    drag,
                    maximumTicks);
            assertEquals(
                    reference == FineReference.HIT
                            ? AnalyticMovingAabbIntersection.Result.HIT
                            : AnalyticMovingAabbIntersection.Result.MISS,
                    actual,
                    "scenario=" + scenario);
            verified++;
        }
        assertTrue(verified >= 100, "too many random scenarios were numerically ambiguous");
    }

    private static boolean threatens(
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity
    ) {
        return MovingAabbThreatEvaluator.threatens(
                SHIP,
                shipVelocity,
                shipAcceleration,
                projectilePosition,
                projectileVelocity,
                0.1D,
                VACUUM,
                100.0D);
    }

    private static boolean threatensExact(
            AABB bounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double maximumTicks
    ) {
        return MovingAabbThreatEvaluator.threatens(
                bounds,
                shipVelocity,
                shipAcceleration,
                projectilePosition,
                projectileVelocity,
                0.0D,
                0.0D,
                ballistics,
                maximumTicks);
    }

    private static AnalyticMovingAabbIntersection.Result analyticResult(
            AABB bounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            ShellAlarmCbcCompat.Ballistics ballistics,
            double maximumTicks
    ) {
        return AnalyticMovingAabbIntersection.evaluate(
                bounds,
                shipVelocity,
                shipAcceleration,
                projectilePosition,
                projectileVelocity,
                ballistics.gravity(),
                ballistics.drag(),
                maximumTicks);
    }

    private static FineReference fineReference(
            AABB bounds,
            Vec3 shipVelocity,
            Vec3 shipAcceleration,
            Vec3 projectilePosition,
            Vec3 projectileVelocity,
            double gravity,
            double drag,
            double maximumTicks
    ) {
        Vec3 center = bounds.getCenter();
        LinearDragTrajectory.AxisTrajectory x = LinearDragTrajectory.axisTrajectory(
                        projectilePosition.x, projectileVelocity.x, 0.0D, drag)
                .relativeTo(center.x, shipVelocity.x, shipAcceleration.x);
        LinearDragTrajectory.AxisTrajectory y = LinearDragTrajectory.axisTrajectory(
                        projectilePosition.y, projectileVelocity.y, -gravity, drag)
                .relativeTo(center.y, shipVelocity.y, shipAcceleration.y);
        LinearDragTrajectory.AxisTrajectory z = LinearDragTrajectory.axisTrajectory(
                        projectilePosition.z, projectileVelocity.z, 0.0D, drag)
                .relativeTo(center.z, shipVelocity.z, shipAcceleration.z);
        AABB centeredBounds = new AABB(
                -bounds.getXsize() * 0.5D,
                -bounds.getYsize() * 0.5D,
                -bounds.getZsize() * 0.5D,
                bounds.getXsize() * 0.5D,
                bounds.getYsize() * 0.5D,
                bounds.getZsize() * 0.5D);
        AABB inner = centeredBounds.inflate(-0.002D);
        AABB outer = centeredBounds.inflate(0.002D);
        boolean innerHit = false;
        boolean outerHit = false;
        double step = 0.02D;
        Vec3 start = relativePosition(x, y, z, 0.0D);
        for (double ticks = step; ticks <= maximumTicks + 1.0E-9D; ticks += step) {
            double time = Math.min(ticks, maximumTicks);
            Vec3 end = relativePosition(x, y, z, time);
            innerHit |= intersects(inner, start, end);
            outerHit |= intersects(outer, start, end);
            start = end;
        }
        if (innerHit) {
            return FineReference.HIT;
        }
        return outerHit ? FineReference.AMBIGUOUS : FineReference.MISS;
    }

    private static Vec3 relativePosition(
            LinearDragTrajectory.AxisTrajectory x,
            LinearDragTrajectory.AxisTrajectory y,
            LinearDragTrajectory.AxisTrajectory z,
            double ticks
    ) {
        return new Vec3(x.position(ticks), y.position(ticks), z.position(ticks));
    }

    private static boolean intersects(AABB bounds, Vec3 start, Vec3 end) {
        return bounds.contains(start) || bounds.contains(end) || bounds.clip(start, end).isPresent();
    }

    private static Vec3 randomVec(Random random, double minimum, double maximum) {
        return new Vec3(
                randomBetween(random, minimum, maximum),
                randomBetween(random, minimum, maximum),
                randomBetween(random, minimum, maximum));
    }

    private static double randomBetween(Random random, double minimum, double maximum) {
        return minimum + random.nextDouble() * (maximum - minimum);
    }

    private enum FineReference {
        HIT,
        MISS,
        AMBIGUOUS
    }
}
