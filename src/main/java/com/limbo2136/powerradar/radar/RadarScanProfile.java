package com.limbo2136.powerradar.radar;

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
            case GROUND -> -PowerRadarCeeConstants.groundDownBlocks();
            case SURFACE_SCANNER -> -PowerRadarCeeConstants.surfaceDownBlocks();
        };
        int maxOffset = switch (mode) {
            case SKY -> PowerRadarCeeConstants.airMaxYOffset();
            case GROUND -> PowerRadarCeeConstants.groundUpBlocks();
            case SURFACE_SCANNER -> 0;
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
                mode == RadarScanMode.GROUND || mode == RadarScanMode.SURFACE_SCANNER,
                mode == RadarScanMode.GROUND,
                mode == RadarScanMode.GROUND,
                mode == RadarScanMode.SKY || mode == RadarScanMode.GROUND,
                true,
                true
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

    public RadarScanProfile frequentDiscoveryOnly() {
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
                false,
                false,
                false,
                this.detectProjectiles,
                this.detectSableStructures,
                false
        );
    }

    public RadarScanProfile withFullHorizontalCoverage() {
        return new RadarScanProfile(
                this.radarType,
                this.structureType,
                this.scanMode,
                this.range,
                this.verticalScanHeight,
                this.verticalMinOffset,
                this.verticalMaxOffset,
                RadarCoverageShape.CIRCLE_360,
                false,
                360,
                this.ignoreItems,
                this.detectPlayers,
                this.detectHostileMobs,
                this.detectPassiveMobs,
                this.detectProjectiles,
                this.detectSableStructures,
                this.detectUnknown
        );
    }
}
