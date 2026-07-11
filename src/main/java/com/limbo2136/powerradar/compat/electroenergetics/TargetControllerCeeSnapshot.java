package com.limbo2136.powerradar.compat.electroenergetics;

public record TargetControllerCeeSnapshot(
        double powerVoltageVolts,
        double currentAmps,
        double powerWatts
) {
    public static final TargetControllerCeeSnapshot EMPTY =
            new TargetControllerCeeSnapshot(0.0, 0.0, 0.0);
}
