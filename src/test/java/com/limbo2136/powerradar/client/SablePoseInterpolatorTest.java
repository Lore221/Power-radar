package com.limbo2136.powerradar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SablePoseInterpolatorTest {
    private static final double EPSILON = 0.0001D;

    @Test
    void offCenterStructureRotatesAroundFixedDynamicAxis() {
        SablePoseInterpolator.Pose start = SablePoseInterpolator.fromCenter(
                -1.0D, 0.0D, 0.0F, 1.0F, 0.0F);
        SablePoseInterpolator.Pose target = SablePoseInterpolator.fromCenter(
                0.0D, -1.0D, 90.0F, 0.0F, 1.0F);

        SablePoseInterpolator.Pose halfway = SablePoseInterpolator.interpolate(start, target, 0.5F);

        assertEquals(0.0D, halfway.worldRotationPointX(), EPSILON);
        assertEquals(0.0D, halfway.worldRotationPointZ(), EPSILON);
        assertEquals(-Math.sqrt(0.5D), halfway.centerX(), EPSILON);
        assertEquals(-Math.sqrt(0.5D), halfway.centerZ(), EPSILON);
        assertEquals(45.0D, halfway.headingDegrees(), EPSILON);
    }

    @Test
    void movingAxisAndOffsetAreInterpolatedAsOnePose() {
        SablePoseInterpolator.Pose start = SablePoseInterpolator.fromCenter(
                8.0D, 20.0D, 0.0F, 2.0F, 0.0F);
        SablePoseInterpolator.Pose target = SablePoseInterpolator.fromCenter(
                14.0D, 18.0D, 90.0F, 0.0F, 4.0F);

        SablePoseInterpolator.Pose halfway = SablePoseInterpolator.interpolate(start, target, 0.5F);

        assertEquals(12.0D, halfway.worldRotationPointX(), EPSILON);
        assertEquals(21.0D, halfway.worldRotationPointZ(), EPSILON);
        assertEquals(3.0D, halfway.localRotationPointOffsetX(), EPSILON);
        assertEquals(45.0D, halfway.headingDegrees(), EPSILON);
        assertEquals(12.0D - 3.0D * Math.sqrt(0.5D), halfway.centerX(), EPSILON);
        assertEquals(21.0D - 3.0D * Math.sqrt(0.5D), halfway.centerZ(), EPSILON);
    }
}
