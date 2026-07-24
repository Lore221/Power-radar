package com.limbo2136.powerradar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RadarCardScreenThemeTest {
    @Test
    void smallButtonStatesKeepTheirAuthoredAtlasCells() {
        assertEquals(192, RadarCardScreenTheme.smallButtonSourceX(false, true, true, true));
        assertEquals(156, RadarCardScreenTheme.smallButtonSourceX(true, true, true, true));
        assertEquals(138, RadarCardScreenTheme.smallButtonSourceX(true, true, true, false));
        assertEquals(174, RadarCardScreenTheme.smallButtonSourceX(true, true, false, false));
        assertEquals(120, RadarCardScreenTheme.smallButtonSourceX(true, false, false, false));
    }
}
