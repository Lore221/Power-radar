package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
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
}
