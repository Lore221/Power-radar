package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerRadarRadarParametersTest {
    @Test
    void unloadedConfigUsesRadarCatalogDefaults() {
        assertEquals(20, PowerRadarRadarParameters.maxPhasedArrayPanels());
        assertEquals(5, PowerRadarRadarParameters.maxOverviewModules());
        assertEquals(80, PowerRadarRadarParameters.baseRangeBlocks());
        assertEquals(20, PowerRadarRadarParameters.phasedArrayPanelRangeBlocks());
        assertEquals(60, PowerRadarRadarParameters.overviewModuleRangeBlocks());
        assertEquals(1.5D, PowerRadarRadarParameters.airRangeMultiplier());
        assertEquals(90.0D, PowerRadarRadarParameters.airFovDegrees());
    }

    @Test
    void existingRadarPathsRemainAndOverviewGetsItsOwnPath() {
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("radar_range.max_radar_panels"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("radar_range.base_range_blocks"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains(
                "radar_range.basic_panel_range_bonus_blocks"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains(
                "radar_range.overview_module_range_bonus_blocks"));
        assertTrue(PowerRadarServerConfig.SPEC.getValues().contains("radar_range.air_fov_degrees"));
    }
}
