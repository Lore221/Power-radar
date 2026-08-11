package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;

/**
 * Неизменяемый профиль одного прохода сканирования. Дальность и смещения выражены в блоках,
 * сектор — в градусах; фильтр обнаружения применяется до появления трека в кэше.
 */
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
        boolean detectRadars,
        boolean detectUnknown
) {
    public boolean detects(RadarTargetCategory category) {
        return switch (category) {
            case PLAYER -> this.detectPlayers;
            case HOSTILE_MOB -> this.detectHostileMobs;
            case PASSIVE_MOB -> this.detectPassiveMobs;
            case PROJECTILE -> this.detectProjectiles;
            case SABLE_STRUCTURE -> this.detectSableStructures;
            case RADAR -> this.detectRadars;
            case UNKNOWN -> this.detectUnknown;
        };
    }

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
            case SURFACE_SCANNER -> PowerRadarCeeConstants.surfaceMinYOffset();
        };
        int maxOffset = switch (mode) {
            case SKY -> PowerRadarCeeConstants.airMaxYOffset();
            case GROUND -> PowerRadarCeeConstants.groundUpBlocks();
            case SURFACE_SCANNER -> PowerRadarCeeConstants.surfaceMaxYOffset();
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
                true,
                true,
                true,
                true,
                true,
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
                this.detectRadars,
                this.detectUnknown
        );
    }

    public RadarScanProfile frequentDiscoveryOnly() {
        return discoveryOnly(false, false);
    }

    public RadarScanProfile regularDiscoveryOnly() {
        return discoveryOnly(true, false);
    }

    public RadarScanProfile frequentAndUnknownDiscoveryOnly() {
        return discoveryOnly(false, true);
    }

    public boolean queriesSableStructures() {
        return this.detectSableStructures || this.detectUnknown;
    }

    private RadarScanProfile discoveryOnly(boolean includeRegularTargets, boolean includeUnknownTargets) {
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
                this.detectPlayers,
                includeRegularTargets && this.detectHostileMobs,
                includeRegularTargets && this.detectPassiveMobs,
                this.detectProjectiles,
                this.detectSableStructures,
                includeRegularTargets && this.detectRadars,
                includeUnknownTargets && this.detectUnknown
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
                this.detectRadars,
                this.detectUnknown
        );
    }
}
