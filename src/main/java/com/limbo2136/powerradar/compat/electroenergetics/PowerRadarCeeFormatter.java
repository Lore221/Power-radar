package com.limbo2136.powerradar.compat.electroenergetics;

import java.util.Locale;
import net.minecraft.network.chat.Component;

public final class PowerRadarCeeFormatter {
    private PowerRadarCeeFormatter() {
    }

    public static String voltage(double volts) {
        double safeVolts = safeSigned(volts);
        return decimal(safeVolts, Math.abs(safeVolts) >= 10.0 ? 0 : 1) + " V";
    }

    public static Component voltageComponent(double volts) {
        double safeVolts = safeSigned(volts);
        return Component.translatable("power_radar.unit.volt", decimal(safeVolts, Math.abs(safeVolts) >= 10.0 ? 0 : 1));
    }

    public static String current(double amps) {
        return decimal(safe(amps), safe(amps) >= 10.0 ? 1 : 2) + " A";
    }

    public static Component currentComponent(double amps) {
        double safeAmps = safe(amps);
        return Component.translatable("power_radar.unit.amp", decimal(safeAmps, safeAmps >= 10.0 ? 1 : 2));
    }

    public static String power(double watts) {
        double safeWatts = safe(watts);
        if (safeWatts >= 1000.0) {
            return decimal(safeWatts / 1000.0, 2) + " kW";
        }
        return decimal(safeWatts, safeWatts >= 10.0 ? 0 : 1) + " W";
    }

    public static Component powerComponent(double watts) {
        double safeWatts = safe(watts);
        if (safeWatts >= 1000.0) {
            return Component.translatable("power_radar.unit.kilowatt", decimal(safeWatts / 1000.0, 2));
        }
        return Component.translatable("power_radar.unit.watt", decimal(safeWatts, safeWatts >= 10.0 ? 0 : 1));
    }

    public static String resistance(double ohms) {
        double safeOhms = safe(ohms);
        return decimal(safeOhms, safeOhms >= 10.0 ? 2 : 1) + " Ohm";
    }

    public static Component resistanceComponent(double ohms) {
        double safeOhms = safe(ohms);
        if (safeOhms > 0.0 && safeOhms < 1.0) {
            return Component.translatable("power_radar.unit.milliohm", decimal(safeOhms * 1000.0, 1));
        }
        if (safeOhms >= 1000.0) {
            return Component.translatable("power_radar.unit.kiloohm", decimal(safeOhms / 1000.0, 2));
        }
        return Component.translatable("power_radar.unit.ohm", decimal(safeOhms, safeOhms >= 10.0 ? 2 : 1));
    }

    public static String percent(double multiplier) {
        return decimal(safe(multiplier) * 100.0, 0) + "%";
    }

    public static String voltageRange(double minVolts, double maxVolts) {
        return decimal(safe(minVolts), 0) + "-" + decimal(safe(maxVolts), 0) + " V";
    }

    private static double safe(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double safeSigned(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static String decimal(double value, int digits) {
        String pattern = "%." + Math.max(0, digits) + "f";
        return String.format(Locale.ROOT, pattern, value);
    }
}
