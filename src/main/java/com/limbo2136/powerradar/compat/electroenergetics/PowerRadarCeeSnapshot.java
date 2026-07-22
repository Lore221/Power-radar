package com.limbo2136.powerradar.compat.electroenergetics;

public record PowerRadarCeeSnapshot(
        boolean bridgeEnabled,
        PowerRadarCeeState electricalState,
        double voltageVolts,
        double currentAmps,
        double powerWatts,
        double resistanceOhms
) {
    public static final PowerRadarCeeSnapshot EMPTY =
            new PowerRadarCeeSnapshot(false, PowerRadarCeeState.INVALID_STRUCTURE,
                    0.0, 0.0, 0.0, PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS);
}
