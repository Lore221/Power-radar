package com.limbo2136.powerradar.radar;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarTargetTrackTest {
    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void sableAccelerationFollowsVelocityChangesFasterThanEntityAcceleration() {
        RadarTargetTrack entity = track(RadarTargetCategory.PLAYER, RadarTargetSourceKind.ENTITY);
        RadarTargetTrack sable = track(
                RadarTargetCategory.SABLE_STRUCTURE,
                RadarTargetSourceKind.FUTURE_SABLE_STRUCTURE);

        updateVelocity(entity, RadarTargetCategory.PLAYER, 1.0D, 5L);
        updateVelocity(sable, RadarTargetCategory.SABLE_STRUCTURE, 1.0D, 5L);
        updateVelocity(entity, RadarTargetCategory.PLAYER, 1.5D, 10L);
        updateVelocity(sable, RadarTargetCategory.SABLE_STRUCTURE, 1.5D, 10L);

        assertTrue(entity.hasAcceleration());
        assertTrue(sable.hasAcceleration());
        assertEquals(0.165D, entity.accelerationX(), 1.0E-9D);
        assertEquals(0.135D, sable.accelerationX(), 1.0E-9D);
    }

    private static RadarTargetTrack track(RadarTargetCategory category, RadarTargetSourceKind sourceKind) {
        UUID uuid = UUID.randomUUID();
        return new RadarTargetTrack(
                TargetKey.entity(DIMENSION, uuid, -1),
                uuid,
                -1,
                ResourceLocation.fromNamespaceAndPath("test", "target"),
                sourceKind,
                null,
                category,
                DIMENSION,
                0.0D, 0.0D, 0.0D,
                0.0D, 0.0D, 0.0D,
                true,
                1.0D,
                1.0D,
                0L);
    }

    private static void updateVelocity(
            RadarTargetTrack track,
            RadarTargetCategory category,
            double velocityX,
            long gameTime
    ) {
        track.update(
                category,
                null,
                DIMENSION,
                0.0D, 0.0D, 0.0D,
                velocityX, 0.0D, 0.0D,
                true,
                1.0D,
                1.0D,
                gameTime);
    }
}
