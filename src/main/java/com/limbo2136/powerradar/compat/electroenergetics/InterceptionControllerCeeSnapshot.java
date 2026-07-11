package com.limbo2136.powerradar.compat.electroenergetics;

public record InterceptionControllerCeeSnapshot(
        double powerVoltageVolts,
        double currentAmps,
        double powerWatts
) {
    public static final InterceptionControllerCeeSnapshot EMPTY =
            new InterceptionControllerCeeSnapshot(0.0, 0.0, 0.0);
}
