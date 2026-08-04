package com.limbo2136.powerradar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class RadarLinkModelRotationTest {
    @Test
    void horizontalPoseRotationInvertsBakedQuarterTurns() {
        assertEquals(0.0F, RadarLinkModelRotation.horizontalFacingYDegrees(Direction.SOUTH));
        assertEquals(90.0F, RadarLinkModelRotation.horizontalFacingYDegrees(Direction.EAST));
        assertEquals(180.0F, RadarLinkModelRotation.horizontalFacingYDegrees(Direction.NORTH));
        assertEquals(270.0F, RadarLinkModelRotation.horizontalFacingYDegrees(Direction.WEST));
    }

    @Test
    void verticalPoseRotationInvertsBakedQuarterTurns() {
        assertEquals(0.0F, RadarLinkModelRotation.verticalModelYDegrees(Direction.NORTH));
        assertEquals(270.0F, RadarLinkModelRotation.verticalModelYDegrees(Direction.EAST));
        assertEquals(180.0F, RadarLinkModelRotation.verticalModelYDegrees(Direction.SOUTH));
        assertEquals(90.0F, RadarLinkModelRotation.verticalModelYDegrees(Direction.WEST));
    }
}
