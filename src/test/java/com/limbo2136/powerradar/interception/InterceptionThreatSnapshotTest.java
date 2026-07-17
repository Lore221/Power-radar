package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class InterceptionThreatSnapshotTest {
    @Test
    void protectedReferenceProjectsVelocityAndAccelerationFromItsOwnSampleTime() {
        InterceptionCoordinator.ThreatSnapshot snapshot = new InterceptionCoordinator.ThreatSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                Level.OVERWORLD,
                Vec3.ZERO,
                Vec3.ZERO,
                95L,
                0.05D,
                0.99D,
                false,
                new Vec3(10.0D, 20.0D, 30.0D),
                new Vec3(2.0D, 0.0D, -1.0D),
                new Vec3(0.5D, 0.0D, 0.25D),
                100L,
                null,
                null);

        Vec3 projected = snapshot.referencePositionAt(104L);

        assertEquals(22.0D, projected.x, 1.0E-9D);
        assertEquals(20.0D, projected.y, 1.0E-9D);
        assertEquals(28.0D, projected.z, 1.0E-9D);
    }

    @Test
    void protectedReferenceDoesNotProjectBackwards() {
        InterceptionCoordinator.ThreatSnapshot snapshot = new InterceptionCoordinator.ThreatSnapshot(
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                Level.OVERWORLD,
                Vec3.ZERO,
                Vec3.ZERO,
                100L,
                0.05D,
                0.99D,
                false,
                new Vec3(4.0D, 5.0D, 6.0D),
                new Vec3(1.0D, 1.0D, 1.0D),
                new Vec3(1.0D, 1.0D, 1.0D),
                100L,
                null,
                null);

        assertEquals(new Vec3(4.0D, 5.0D, 6.0D), snapshot.referencePositionAt(90L));
    }
}
