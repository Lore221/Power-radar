package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadarServerConfig;

public final class RadarModuleConstants {
    public static final int MAX_MODULES = 20;
    public static final int BASE_RANGE_BLOCKS = 80;
    public static final int PHASED_ARRAY_RANGE_BONUS_BLOCKS = 20;
    public static final int OVERVIEW_RANGE_PANEL_EQUIVALENT = 3;
    public static final int MAX_OVERVIEW_MODULES = 5;
    public static final int OVERVIEW_TRACK_UPDATE_INTERVAL_TICKS = 10;
    public static final double PHASED_ARRAY_POWER_WATTS = 700.0;
    public static final double OVERVIEW_MODULE_POWER_WATTS = 700.0;

    private RadarModuleConstants() {
    }

    public static int maxModules() {
        return PowerRadarServerConfig.maxRadarPanels();
    }

    public static int baseRangeBlocks() {
        return PowerRadarServerConfig.radarBaseRangeBlocks();
    }

    public static int phasedArrayRangeBonusBlocks() {
        return PowerRadarServerConfig.basicPanelRangeBonusBlocks();
    }

    public static int overviewRangeBonusBlocks() {
        return phasedArrayRangeBonusBlocks() * OVERVIEW_RANGE_PANEL_EQUIVALENT;
    }

    public static int maxOverviewModules() {
        return MAX_OVERVIEW_MODULES;
    }

    public static double phasedArrayPowerWatts() {
        return PowerRadarServerConfig.basicRadarPanelPowerWatts();
    }

    public static double overviewModulePowerWatts() {
        return PowerRadarServerConfig.overviewModulePowerWatts();
    }

    public static int overviewTrackUpdateIntervalTicks() {
        return OVERVIEW_TRACK_UPDATE_INTERVAL_TICKS;
    }
}
