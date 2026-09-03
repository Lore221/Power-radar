package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;

/** Имена значений сохраняются в NBT и передаются монитором; переименование требует миграции. */
public enum RadarScanMode {
    GROUND,
    SKY,
    SURFACE_SCANNER,
    AIRCRAFT;

    public RadarScanMode next() {
        if (this == AIRCRAFT) {
            return this;
        }
        return this == GROUND ? SKY : GROUND;
    }

    public String messageKey() {
        return switch (this) {
            case GROUND -> "message.power_radar.mode.ground";
            case SKY -> "message.power_radar.mode.sky";
            case SURFACE_SCANNER -> "message.power_radar.mode.surface_scanner";
            case AIRCRAFT -> "message.power_radar.mode.aircraft";
        };
    }

    public int sectorAngleDegrees() {
        return switch (this) {
            case GROUND -> PowerRadarRadarParameters.groundFovDegrees();
            case SKY -> PowerRadarRadarParameters.airFovDegrees();
            case SURFACE_SCANNER -> PowerRadarRadarParameters.surfaceFovDegrees();
            case AIRCRAFT -> PowerRadarRadarParameters.aircraftFovDegrees();
        };
    }

    public int verticalScanHeight() {
        return switch (this) {
            case GROUND, SURFACE_SCANNER -> PowerRadarCeeConstants.groundUpBlocks();
            case SKY -> PowerRadarCeeConstants.airMaxYOffset();
            case AIRCRAFT -> PowerRadarRadarParameters.aircraftMaxYOffset();
        };
    }

    public static RadarScanMode byName(String name) {
        for (RadarScanMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return GROUND;
    }
}
