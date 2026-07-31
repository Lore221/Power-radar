package com.limbo2136.powerradar.compat.electroenergetics;

/**
 * Общая математика низковольтных нагрузок. Ею пользуются и самостоятельные CEE-устройства,
 * и вложения электрического щитка, поэтому пороги и гистерезис не расходятся.
 */
public final class PowerRadarCeeLoadMath {
    private PowerRadarCeeLoadMath() {
    }

    public static double nominalResistance(double nominalVoltageVolts, double nominalPowerWatts) {
        return PowerRadarCeeConstants.nominalResistanceOhms(nominalVoltageVolts, nominalPowerWatts);
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
            if (voltage > voltages.overvoltageRecovery()) {
                return PowerRadarCeeState.OVERVOLTAGE;
            }
            return voltage >= voltages.restart()
                    ? PowerRadarCeeState.POWERED
                    : PowerRadarCeeState.UNDERVOLTAGE;
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
