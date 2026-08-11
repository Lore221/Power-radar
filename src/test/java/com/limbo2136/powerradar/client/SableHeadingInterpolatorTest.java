package com.limbo2136.powerradar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SableHeadingInterpolatorTest {
    private static final float EPSILON = 0.0001F;

    @Test
    void crossesNorthByShortestArc() {
        assertEquals(0.0F, SableHeadingInterpolator.interpolate(350.0F, 10.0F, 0.5F), EPSILON);
        assertEquals(0.0F, SableHeadingInterpolator.interpolate(10.0F, 350.0F, 0.5F), EPSILON);
    }

    @Test
    void clampsInterpolationAmount() {
        assertEquals(30.0F, SableHeadingInterpolator.interpolate(30.0F, 90.0F, -1.0F), EPSILON);
        assertEquals(90.0F, SableHeadingInterpolator.interpolate(30.0F, 90.0F, 2.0F), EPSILON);
    }
}
