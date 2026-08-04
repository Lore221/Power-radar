package com.limbo2136.powerradar.compat.electroenergetics;

/**
 * Общая математика низковольтных нагрузок. Ею пользуются и самостоятельные CEE-устройства,
 * и вложения электрического щитка, поэтому рабочие пороги не расходятся.
 */
public final class PowerRadarCeeLoadMath {
    private PowerRadarCeeLoadMath() {
    }

    public static double nominalResistance(double nominalVoltageVolts, double nominalPowerWatts) {
        return PowerRadarCeeConstants.nominalResistanceOhms(nominalVoltageVolts, nominalPowerWatts);
    }

    public static PowerRadarCeeState resolveState(
            boolean enabled,
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
        if (voltage > voltages.maximum()) {
            return PowerRadarCeeState.OVERVOLTAGE;
        }
        return voltage >= voltages.minimum()
                ? PowerRadarCeeState.POWERED
                : PowerRadarCeeState.UNDERVOLTAGE;
    }
}
