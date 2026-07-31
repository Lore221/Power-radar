package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerRadarRadarParametersTest {
    @Test
    void unloadedConfigUsesRegisteredSpecDefaults() {
        assertEquals(defaultInt("radar_range.max_radar_panels"),
                PowerRadarRadarParameters.maxPhasedArrayPanels());
        assertEquals(defaultInt("radar_range.max_overview_modules"),
                PowerRadarRadarParameters.maxOverviewModules());
        assertEquals(defaultInt("radar_range.base_range_blocks"),
                PowerRadarRadarParameters.baseRangeBlocks());
        assertEquals(defaultInt("radar_range.basic_panel_range_bonus_blocks"),
                PowerRadarRadarParameters.phasedArrayPanelRangeBlocks());
        assertEquals(defaultInt("radar_range.overview_module_range_bonus_blocks"),
                PowerRadarRadarParameters.overviewModuleRangeBlocks());
        assertEquals(defaultDouble("radar_range.air_range_multiplier"),
                PowerRadarRadarParameters.airRangeMultiplier());
        assertEquals(defaultDouble("radar_range.air_fov_degrees"),
                PowerRadarRadarParameters.airFovDegrees());
        assertEquals(defaultInt("radar_range.ground_up_blocks"),
                PowerRadarRadarParameters.groundUpBlocks());
        assertEquals(defaultInt("radar_range.ground_down_blocks"),
                PowerRadarRadarParameters.groundDownBlocks());
        assertEquals(defaultInt("radar_range.surface_down_blocks"),
                PowerRadarRadarParameters.surfaceDownBlocks());
        assertEquals(defaultInt("radar_range.air_min_y_offset"),
                PowerRadarRadarParameters.airMinYOffset());
        assertEquals(defaultInt("radar_range.air_max_y_offset"),
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
        assertTrue(PowerRadarRadarParameters.airFovDegrees() >= 1.0D);
        assertTrue(PowerRadarRadarParameters.airFovDegrees() <= 360.0D);
        assertTrue(PowerRadarRadarParameters.groundUpBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.groundDownBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.surfaceDownBlocks() >= 0);
        assertTrue(PowerRadarRadarParameters.airMaxYOffset()
                >= PowerRadarRadarParameters.airMinYOffset());
    }

    private static int defaultInt(String path) {
        return ((Number) configDefault(path)).intValue();
    }

    private static double defaultDouble(String path) {
        return ((Number) configDefault(path)).doubleValue();
    }

    private static Object configDefault(String path) {
        ModConfigSpec.ConfigValue<?> value = PowerRadarServerConfig.SPEC.getValues().get(path);
        assertTrue(value != null, () -> "Missing server config path: " + path);
        return value.getDefault();
    }
}
