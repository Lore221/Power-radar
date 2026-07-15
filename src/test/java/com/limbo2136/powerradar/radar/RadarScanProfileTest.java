package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadarScanProfileTest {
    @Test
    void surfaceScannerUsesFixedVerticalDepth() {
        RadarScanProfile shortRange = RadarScanProfile.sectorController(RadarScanMode.SURFACE_SCANNER, 80);
        RadarScanProfile longRange = RadarScanProfile.sectorController(RadarScanMode.SURFACE_SCANNER, 400);

        assertEquals(-PowerRadarCeeConstants.SURFACE_DOWN_BLOCKS, shortRange.verticalMinOffset());
        assertEquals(-PowerRadarCeeConstants.SURFACE_DOWN_BLOCKS, longRange.verticalMinOffset());
        assertEquals(0, shortRange.verticalMaxOffset());
        assertEquals(0, longRange.verticalMaxOffset());
    }
}
