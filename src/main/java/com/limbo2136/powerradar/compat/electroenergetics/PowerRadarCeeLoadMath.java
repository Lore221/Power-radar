package com.limbo2136.powerradar.compat.electroenergetics;

/**
 * Общая математика низковольтных нагрузок. Ею пользуются и самостоятельные CEE-устройства,
 * и вложения электрического щитка, поэтому пороги и гистерезис не расходятся.
 */
public final class PowerRadarCeeLoadMath {
    private PowerRadarCeeLoadMath() {
    }

    public static double constantPowerResistance(double voltageVolts, double powerWatts) {
        return PowerRadarCeeConstants.constantPowerResistanceOhms(
                voltageVolts,
                PowerRadarElectricalParameters.Voltages.monitor().nominal(),
                powerWatts);
    }

    public static PowerRadarCeeState resolveState(
            boolean enabled,
            PowerRadarCeeState previousState,
            double voltageVolts,
            PowerRadarElectricalParameters.LoadVoltageRange voltages
    ) {
        if (!enabled) {
            return PowerRadarCeeState.INVALID_STRUCTURE;
        }
        double voltage = Double.isFinite(voltageVolts) ? voltageVolts : 0.0D;
        if (voltage < -0.001D) {
            return PowerRadarCeeState.REVERSE_POLARITY;
        }
        if (previousState == PowerRadarCeeState.OVERVOLTAGE) {
            return voltage <= voltages.overvoltageRecovery()
                    ? PowerRadarCeeState.POWERED
                    : PowerRadarCeeState.OVERVOLTAGE;
        }
        if (voltage > voltages.maximum()) {
            return PowerRadarCeeState.OVERVOLTAGE;
        }
        if (previousState == PowerRadarCeeState.UNDERVOLTAGE
                || previousState == PowerRadarCeeState.INVALID_STRUCTURE) {
            return voltage >= voltages.restart()
                    ? PowerRadarCeeState.POWERED
                    : PowerRadarCeeState.UNDERVOLTAGE;
        }
        return voltage >= voltages.minimum()
                ? PowerRadarCeeState.POWERED
                : PowerRadarCeeState.UNDERVOLTAGE;
    }
}
