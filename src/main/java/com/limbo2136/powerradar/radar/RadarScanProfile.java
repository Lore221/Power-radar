package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;

public record RadarScanProfile(
        RadarProfileType radarType,
        RadarStructureType structureType,
        RadarScanMode scanMode,
        int range,
        int verticalScanHeight,
        int verticalMinOffset,
        int verticalMaxOffset,
        RadarCoverageShape coverageShape,
        boolean useFovCheck,
        int sectorAngle,
        boolean ignoreItems,
        boolean detectPlayers,
        boolean detectHostileMobs,
        boolean detectPassiveMobs,
        boolean detectProjectiles,
        boolean detectSableStructures,
        boolean detectUnknown
) {
    public static RadarScanProfile sectorController(RadarScanMode mode, int range) {
        return controller(mode, range, RadarStructureType.PHASED_ARRAY);
    }

    public static RadarScanProfile overviewController(RadarScanMode mode, int range) {
        return controller(mode, range, RadarStructureType.OVERVIEW);
    }

    private static RadarScanProfile controller(RadarScanMode mode, int range, RadarStructureType structureType) {
        boolean overview = structureType == RadarStructureType.OVERVIEW;
        int minOffset = switch (mode) {
            case SKY -> PowerRadarCeeConstants.airMinYOffset();
            case GROUND, SURFACE_SCANNER -> -PowerRadarCeeConstants.groundDownBlocks();
        };
        int maxOffset = switch (mode) {
            case SKY -> PowerRadarCeeConstants.airMaxYOffset();
            case GROUND, SURFACE_SCANNER -> PowerRadarCeeConstants.groundUpBlocks();
        };
        return new RadarScanProfile(
                overview ? RadarProfileType.OVERVIEW_CONTROLLER : RadarProfileType.SECTOR_CONTROLLER,
                structureType,
                mode,
                range,
                maxOffset,
                minOffset,
                maxOffset,
                overview ? RadarCoverageShape.CIRCLE_360 : RadarCoverageShape.SECTOR,
                !overview,
                mode.sectorAngleDegrees(),
                true,
                overview || mode != RadarScanMode.SKY,
                overview || mode == RadarScanMode.GROUND,
                overview || mode == RadarScanMode.GROUND && RadarConstants.detectPassiveMobsByDefault(),
                overview || mode == RadarScanMode.SKY || mode == RadarScanMode.GROUND,
                overview || mode == RadarScanMode.SKY || mode == RadarScanMode.SURFACE_SCANNER,
                false
        );
    }

    public RadarScanProfile withDetectionFilter(int detectionFilterMask) {
        return new RadarScanProfile(
                this.radarType,
                this.structureType,
                this.scanMode,
                this.range,
                this.verticalScanHeight,
                this.verticalMinOffset,
                this.verticalMaxOffset,
                this.coverageShape,
                this.useFovCheck,
                this.sectorAngle,
                this.ignoreItems,
                this.detectPlayers && RadarDetectionFilters.enabled(detectionFilterMask, RadarTargetCategory.PLAYER),
                this.detectHostileMobs && RadarDetectionFilters.enabled(detectionFilterMask, RadarTargetCategory.HOSTILE_MOB),
                this.detectPassiveMobs && RadarDetectionFilters.enabled(detectionFilterMask, RadarTargetCategory.PASSIVE_MOB),
                this.detectProjectiles && RadarDetectionFilters.enabled(detectionFilterMask, RadarTargetCategory.PROJECTILE),
                this.detectSableStructures && RadarDetectionFilters.enabled(detectionFilterMask, RadarTargetCategory.SABLE_STRUCTURE),
                this.detectUnknown
        );
    }
}
