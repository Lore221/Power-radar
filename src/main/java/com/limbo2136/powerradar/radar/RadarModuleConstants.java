package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;

public final class RadarModuleConstants {
    // Фиксированная эталонная шкала рендера 200x200, а не игровые значения дальности.
    public static final int BASE_RANGE_BLOCKS = 80;
    public static final int PHASED_ARRAY_RANGE_BONUS_BLOCKS = 20;
    public static final int OVERVIEW_TRACK_UPDATE_INTERVAL_TICKS = 10;
    private RadarModuleConstants() {
    }

    public static int maxModules() {
        return PowerRadarRadarParameters.maxPhasedArrayPanels();
    }

    public static int baseRangeBlocks() {
        return PowerRadarRadarParameters.baseRangeBlocks();
    }

    public static int phasedArrayRangeBonusBlocks() {
        return PowerRadarRadarParameters.phasedArrayPanelRangeBlocks();
    }

    public static int overviewRangeBonusBlocks() {
        return PowerRadarRadarParameters.overviewModuleRangeBlocks();
    }

    public static int maxOverviewModules() {
        return PowerRadarRadarParameters.maxOverviewModules();
    }

    public static double phasedArrayPowerWatts() {
        return PowerRadarElectricalParameters.Ratings.phasedArrayPanelPowerWatts();
    }

    public static double overviewModulePowerWatts() {
        return PowerRadarElectricalParameters.Ratings.overviewModulePowerWatts();
    }

    public static int overviewTrackUpdateIntervalTicks() {
        return OVERVIEW_TRACK_UPDATE_INTERVAL_TICKS;
    }
}
