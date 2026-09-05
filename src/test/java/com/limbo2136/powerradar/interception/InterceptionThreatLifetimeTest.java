package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.*;

import com.limbo2136.powerradar.compat.createbigcannons.ShellAlarmCbcCompat;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class InterceptionThreatLifetimeTest {
    private static final AABB BOUNDS = new AABB(-1, -1, -1, 1, 1, 1);
    private static final ShellAlarmCbcCompat.Ballistics STRAIGHT =
            new ShellAlarmCbcCompat.Ballistics(0, 0, false);

    private static MovingProtectedZone ground() {
        return new MovingProtectedZone(BOUNDS, Vec3.ZERO, Vec3.ZERO, 0, null, 0);
    }

    private static void sample(InterceptionThreatLifetime threat, long tick, double x) {
        threat.observe(tick, ground(), new Vec3(x, 0, 0), new Vec3(1, 0, 0), 0, false, STRAIGHT);
    }

    @Test
    void firstEntryIsTheNearBoundaryNotTheCenterOrExit() {
        assertEquals(10, MovingAabbThreatEvaluator.firstEntryTicks(BOUNDS, Vec3.ZERO, Vec3.ZERO,
                new Vec3(-11, 0, 0), new Vec3(1, 0, 0), STRAIGHT, 100), 1e-6);
    }

    @Test
    void keepsKnownProjectileWithoutAnyNewRadarPublication() {
        var threat = new InterceptionThreatLifetime();
        sample(threat, 0, -101);
        for (int tick = 1; tick <= 60; tick++) sample(threat, tick, -101 + tick);
        assertTrue(threat.active());
        assertEquals(40, threat.remainingTicks(60), 1e-6);
    }

    @Test
    void closesAfterSweepingAcrossTheEntireZoneInOneTick() {
        var threat = new InterceptionThreatLifetime();
        sample(threat, 0, -11);
        sample(threat, 1, 11);
        assertTrue(threat.closed());
        sample(threat, 2, -11);
        assertFalse(threat.active());
    }

    @Test
    void anExpiredPredictionAloneDoesNotCloseAMovingShipsThreat() {
        var threat = new InterceptionThreatLifetime();
        UUID ship = UUID.randomUUID();
        var original = new MovingProtectedZone(BOUNDS, Vec3.ZERO, Vec3.ZERO, 0, ship, 0);
        threat.observe(0, original, new Vec3(-11, 0, 0), new Vec3(1, 0, 0), 0, false, STRAIGHT);
        var moved = new MovingProtectedZone(BOUNDS.move(100, 0, 0), Vec3.ZERO, Vec3.ZERO, 11, ship, 0);
        threat.observe(11, moved, Vec3.ZERO, new Vec3(1, 0, 0), 0, false, STRAIGHT);
        assertFalse(threat.closed());
        assertTrue(threat.active());
        assertEquals(99, threat.remainingTicks(11), 1e-6);
    }

    @Test
    void unavailableEntitySuspendsFireAndHasBoundedGrace() {
        var threat = new InterceptionThreatLifetime();
        sample(threat, 0, -101);
        assertFalse(threat.missing(1));
        assertFalse(threat.active());
        assertFalse(threat.missing(21));
        assertTrue(threat.missing(22));
    }

    @Test
    void recoveredEntityDoesNotInventASweptImpactDuringUnloading() {
        var threat = new InterceptionThreatLifetime();
        sample(threat, 0, -11);
        threat.missing(1);
        threat.observe(2, ground(), new Vec3(11, 0, 0), new Vec3(-1, 0, 0), 0, false, STRAIGHT);
        assertFalse(threat.closed());
        assertTrue(threat.active());
    }

    @Test
    void inGroundIsTerminalEvenOutsideTheProtectedZone() {
        var threat = new InterceptionThreatLifetime();
        sample(threat, 0, -11);
        threat.observe(1, ground(), new Vec3(-10, 0, 0), Vec3.ZERO, 0, true, STRAIGHT);
        assertTrue(threat.closed());
        assertFalse(threat.active());
    }

    @Test
    void aDeflectionOutsideTheZoneRecomputesRatherThanCloses() {
        var threat = new InterceptionThreatLifetime();
        sample(threat, 0, -11);
        threat.observe(1, ground(), new Vec3(-10, 0, 0), new Vec3(-1, 0, 0), 0, false, STRAIGHT);
        assertFalse(threat.closed());
        assertFalse(threat.active());
        sample(threat, 2, -11);
        assertTrue(threat.active());
    }

    @Test
    void quadraticFallbackReturnsFractionalEntryTime() {
        var quadratic = new ShellAlarmCbcCompat.Ballistics(0, 0, true);
        assertEquals(2.5, MovingAabbThreatEvaluator.firstEntryTicks(BOUNDS, Vec3.ZERO, Vec3.ZERO,
                new Vec3(-6, 0, 0), new Vec3(2, 0, 0), quadratic, 10), 1e-6);
    }
}
