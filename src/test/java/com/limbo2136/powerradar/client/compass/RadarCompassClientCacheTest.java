package com.limbo2136.powerradar.client.compass;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RadarCompassClientCacheTest {
    @Test
    void extrapolationUsesVelocityAndCapsMissingPackets() {
        Vec3 position = new Vec3(10.0D, 20.0D, 30.0D);
        Vec3 velocity = new Vec3(0.5D, -0.25D, 1.0D);

        assertEquals(
                new Vec3(10.75D, 19.625D, 31.5D),
                RadarCompassClientCache.extrapolate(position, velocity, 100L, 101.5D));
        assertEquals(
                new Vec3(11.5D, 19.25D, 33.0D),
                RadarCompassClientCache.extrapolate(position, velocity, 100L, 110.0D));
        assertEquals(
                position,
                RadarCompassClientCache.extrapolate(position, velocity, 100L, 99.0D));
    }
}
