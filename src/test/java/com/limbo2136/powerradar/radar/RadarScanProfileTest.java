package com.limbo2136.powerradar.radar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadarScanProfileTest {
    @Test
    void surfaceScannerUsesFixedVerticalDepth() {
        RadarScanProfile shortRange = RadarScanProfile.sectorController(RadarScanMode.SURFACE_SCANNER, 80);
        RadarScanProfile longRange = RadarScanProfile.sectorController(RadarScanMode.SURFACE_SCANNER, 400);

        assertEquals(-PowerRadarRadarParameters.surfaceDownBlocks(), shortRange.verticalMinOffset());
        assertEquals(-PowerRadarRadarParameters.surfaceDownBlocks(), longRange.verticalMinOffset());
        assertEquals(0, shortRange.verticalMaxOffset());
        assertEquals(0, longRange.verticalMaxOffset());
    }

    @Test
    void fullHorizontalCoverageKeepsVerticalAndDetectionSettings() {
        RadarScanProfile sector = RadarScanProfile.sectorController(RadarScanMode.GROUND, 400)
                .withDetectionFilter(RadarDetectionFilters.PROJECTILES);

        RadarScanProfile full = sector.withFullHorizontalCoverage();

        assertEquals(RadarCoverageShape.CIRCLE_360, full.coverageShape());
        assertEquals(360, full.sectorAngle());
        assertFalse(full.useFovCheck());
        assertEquals(sector.verticalMinOffset(), full.verticalMinOffset());
        assertEquals(sector.verticalMaxOffset(), full.verticalMaxOffset());
        assertEquals(sector.detectProjectiles(), full.detectProjectiles());
        assertEquals(sector.detectPlayers(), full.detectPlayers());
    }

    @Test
    void overviewFootprintMatchesRenderedOctagon() {
        assertTrue(RadarCoverageFilter.isInsideOverviewFootprint(100.0D, 50.0D, 100.0D));
        assertFalse(RadarCoverageFilter.isInsideOverviewFootprint(100.0D, 60.0D, 100.0D));
        assertTrue(RadarCoverageFilter.isInsideOverviewFootprint(0.0D, 100.0D, 100.0D));
        assertFalse(RadarCoverageFilter.isInsideOverviewFootprint(101.0D, 0.0D, 100.0D));
    }

    @Test
    void frequentDiscoveryKeepsProjectilesAndSableOnly() {
        RadarScanProfile frequent = RadarScanProfile.sectorController(RadarScanMode.SKY, 400)
                .frequentDiscoveryOnly();

        assertTrue(frequent.detectProjectiles());
        assertTrue(frequent.detectSableStructures());
        assertFalse(frequent.detectPlayers());
        assertFalse(frequent.detectHostileMobs());
        assertFalse(frequent.detectPassiveMobs());
        assertFalse(frequent.detectUnknown());
    }

    @Test
    void everyControllerModeCanDetectSableStructures() {
        for (RadarScanMode mode : RadarScanMode.values()) {
            assertTrue(RadarScanProfile.sectorController(mode, 400).detectSableStructures(), mode.name());
            assertTrue(RadarScanProfile.overviewController(mode, 400).detectSableStructures(), mode.name());
        }
    }
}
