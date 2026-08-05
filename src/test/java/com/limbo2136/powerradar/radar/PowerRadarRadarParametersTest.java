package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerRadarRadarParametersTest {
    @Test
    void unloadedConfigUsesRegisteredSpecDefaults() {
        assertEquals(defaultInt("panels.max_radar_panels"),
                PowerRadarRadarParameters.maxPhasedArrayPanels());
        assertEquals(defaultInt("panels.max_overview_modules"),
                PowerRadarRadarParameters.maxOverviewModules());
        assertEquals(defaultInt("base_radar.base_range_blocks"),
                PowerRadarRadarParameters.baseRangeBlocks());
        assertEquals(defaultInt("panels.basic_panel_range_bonus_blocks"),
                PowerRadarRadarParameters.phasedArrayPanelRangeBlocks());
        assertEquals(defaultInt("panels.overview_module_range_bonus_blocks"),
                PowerRadarRadarParameters.overviewModuleRangeBlocks());
        assertEquals(defaultDouble("air_radar.range_multiplier"),
                PowerRadarRadarParameters.airRangeMultiplier());
        assertEquals(defaultFovDegrees("base_radar.fov"),
                PowerRadarRadarParameters.groundFovDegrees());
        assertEquals(defaultFovDegrees("air_radar.fov"),
                PowerRadarRadarParameters.airFovDegrees());
        assertEquals(defaultFovDegrees("surface_radar.fov"),
                PowerRadarRadarParameters.surfaceFovDegrees());
        assertEquals(defaultInt("base_radar.up_blocks"),
                PowerRadarRadarParameters.groundUpBlocks());
        assertEquals(defaultInt("base_radar.down_blocks"),
                PowerRadarRadarParameters.groundDownBlocks());
        assertEquals(defaultInt("surface_radar.down_blocks"),
                PowerRadarRadarParameters.surfaceDownBlocks());
        assertEquals(defaultInt("surface_radar.max_y_offset"),
                PowerRadarRadarParameters.surfaceMaxYOffset());
        assertEquals(defaultInt("air_radar.min_y_offset"),
                PowerRadarRadarParameters.airMinYOffset());
        assertEquals(defaultInt("air_radar.max_y_offset"),
                PowerRadarRadarParameters.airMaxYOffset());
    }

    @Test
    void radarCatalogValuesStayValid() {
        assertTrue(PowerRadarRadarParameters.maxPhasedArrayPanels() > 0);
        assertTrue(PowerRadarRadarParameters.maxOverviewModules() > 0);
        assertTrue(PowerRadarRadarParameters.baseRangeBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.phasedArrayPanelRangeBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.overviewModuleRangeBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.airRangeMultiplier() >= 0.0D);
        assertAllowedAngle(PowerRadarRadarParameters.groundFovDegrees());
        assertAllowedAngle(PowerRadarRadarParameters.airFovDegrees());
        assertAllowedAngle(PowerRadarRadarParameters.surfaceFovDegrees());
        assertTrue(PowerRadarRadarParameters.groundUpBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.groundDownBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.surfaceDownBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.surfaceMaxYOffset()
                >= PowerRadarRadarParameters.surfaceMinYOffset());
        assertTrue(PowerRadarRadarParameters.airMaxYOffset()
                >= PowerRadarRadarParameters.airMinYOffset());
    }

    private static void assertAllowedAngle(int angle) {
        assertTrue(angle == 60 || angle == 90 || angle == 120);
    }

    private static int defaultInt(String path) {
        return ((Number) configDefault(path)).intValue();
    }

    private static double defaultDouble(String path) {
        return ((Number) configDefault(path)).doubleValue();
    }

    private static int defaultFovDegrees(String path) {
        return ((PowerRadarRadarParameters.RadarFieldOfView) configDefault(path)).degrees();
    }

    private static Object configDefault(String path) {
        ModConfigSpec.ConfigValue<?> value = PowerRadarServerConfig.SPEC.getValues().get(path);
        assertTrue(value != null, () -> "Missing server config path: " + path);
        return value.getDefault();
    }
}
