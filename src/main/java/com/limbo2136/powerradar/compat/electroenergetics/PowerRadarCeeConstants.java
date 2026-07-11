package com.limbo2136.powerradar.compat.electroenergetics;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import com.limbo2136.powerradar.radar.RadarModuleConstants;
import com.limbo2136.powerradar.radar.RadarStructureType;

public final class PowerRadarCeeConstants {
    public static final int MAX_RADAR_PANELS = 20;

    public static final double RADAR_CONTROLLER_POWER_WATTS = 1000.0;
    public static final double BASIC_RADAR_PANEL_POWER_WATTS = 700.0;

    public static final double RADAR_NOMINAL_VOLTAGE = 600.0;
    public static final double RADAR_MIN_VOLTAGE = 400.0;
    public static final double RADAR_RESTART_VOLTAGE = 430.0;
    public static final double RADAR_FULL_RANGE_VOLTAGE = 600.0;
    public static final double RADAR_MAX_VOLTAGE = 700.0;
    public static final double RADAR_OVERVOLTAGE_RECOVERY = 650.0;

    public static final double MONITOR_CONTROLLER_POWER_WATTS = 45.0;
    public static final double RADAR_DISPLAY_POWER_WATTS = 5.0;

    public static final double MONITOR_NOMINAL_VOLTAGE = 24.0;
    public static final double MONITOR_MIN_VOLTAGE = 18.0;
    public static final double MONITOR_RESTART_VOLTAGE = 20.0;
    public static final double MONITOR_MAX_VOLTAGE = 30.0;
    public static final double MONITOR_OVERVOLTAGE_RECOVERY = 28.0;
    public static final double SHELL_ALARM_POWER_WATTS = 45.0;
    public static final double SHELL_ALARM_NOMINAL_VOLTAGE = 400.0;
    public static final double SHELL_ALARM_MIN_VOLTAGE = 300.0;
    public static final double SHELL_ALARM_RESTART_VOLTAGE = 320.0;
    public static final double SHELL_ALARM_MAX_VOLTAGE = 400.0;
    public static final double SHELL_ALARM_OVERVOLTAGE_RECOVERY = 380.0;

    public static final int SHELL_ALARM_DEFAULT_SIDE_BLOCKS = 64;
    public static final int SHELL_ALARM_MIN_SIDE_BLOCKS = 16;
    public static final int SHELL_ALARM_MAX_SIDE_BLOCKS = 512;
    public static final int SHELL_ALARM_SIDE_STEP_BLOCKS = 16;
    public static final int SHELL_ALARM_VERTICAL_MARGIN_BLOCKS = 20;
    public static final int SHELL_ALARM_MAX_SIMULATION_TICKS = 1200;

    public static final double TARGET_CONTROLLER_POWER_RESISTANCE_OHMS = 30.0;
    public static final double TARGET_CONTROLLER_MIN_POWER_VOLTAGE = 200.0;
    public static final double TARGET_CONTROLLER_FULL_SPEED_VOLTAGE = 300.0;
    public static final double TARGET_CONTROLLER_MAX_POWER_VOLTAGE = 360.0;
    public static final double TARGET_CONTROLLER_MIN_RPM = 16.0;
    public static final double TARGET_CONTROLLER_MAX_RPM = 64.0;
    public static final double TARGET_CONTROLLER_ACCELERATION_RPM_PER_SECOND = 32.0;
    public static final double INTERCEPTION_CONTROLLER_SPEED_MULTIPLIER = 2.0;
    public static final double TARGET_CONTROLLER_AIM_RESPONSE_PER_TICK = 0.35;
    public static final double TARGET_CONTROLLER_READY_MAX_SPEED_DEGREES_PER_TICK = 1.0;
    public static final int TARGET_CONTROLLER_VISIBILITY_CHECK_INTERVAL_TICKS = 5;
    public static final double TARGET_CONTROLLER_AIM_TOLERANCE_DEGREES = 2.0;
    public static final double TARGET_CONTROLLER_LEAD_SPEED_BLOCKS_PER_TICK = 3.0;
    public static final double TARGET_CONTROLLER_MAX_LEAD_TICKS = 80.0;
    public static final double TARGET_CONTROLLER_MIN_LEAD_DISTANCE_BLOCKS = 16.0;
    public static final double TARGET_CONTROLLER_TARGET_HEIGHT_FACTOR = 0.5;

    public static final int RADAR_BASE_RANGE_BLOCKS = 80;
    public static final int BASIC_PANEL_RANGE_BONUS_BLOCKS = 20;

    public static final int GROUND_UP_BLOCKS = 128;
    public static final int GROUND_DOWN_BLOCKS = 20;

    public static final double AIR_RANGE_MULTIPLIER = 1.5;
    public static final double AIR_FOV_DEGREES = 90.0;
    public static final int AIR_MIN_Y_OFFSET = 40;
    public static final int AIR_MAX_Y_OFFSET = 1500;

    public static final double MIN_SAFE_RESISTANCE_OHMS = 0.001;
    public static final double OFF_RESISTANCE_OHMS = 200_000_000.0;

    private PowerRadarCeeConstants() {
    }

    public static int clampedRadarPanelCount(int activePanelCount) {
        if (activePanelCount <= 0) {
            return 0;
        }
        return Math.min(activePanelCount, maxRadarPanels());
    }

    public static double radarConstantPowerWatts(RadarStructureType structureType, int phasedArrayPanelCount, int overviewModuleCount) {
        int panels = clampedRadarPanelCount(phasedArrayPanelCount);
        int overviewModules = Math.max(0, Math.min(overviewModuleCount, RadarModuleConstants.maxOverviewModules()));
        if (structureType == RadarStructureType.OVERVIEW) {
            return overviewModules <= 0
                    ? 0.0
                    : radarControllerPowerWatts() + RadarModuleConstants.overviewModulePowerWatts() * overviewModules;
        }
        return panels <= 0 ? 0.0 : radarControllerPowerWatts() + RadarModuleConstants.phasedArrayPowerWatts() * panels;
    }

    public static double radarConstantPowerWatts(int activePanelCount) {
        return radarConstantPowerWatts(RadarStructureType.PHASED_ARRAY, activePanelCount, 0);
    }

    public static double monitorConstantPowerWatts(int activeDisplayCount) {
        return activeDisplayCount <= 0 ? 0.0 : monitorControllerPowerWatts() + radarDisplayPowerWatts() * activeDisplayCount;
    }

    public static int radarBaseRangeBlocks(int phasedArrayPanelCount) {
        int panels = clampedRadarPanelCount(phasedArrayPanelCount);
        return panels <= 0
                ? 0
                : RadarModuleConstants.baseRangeBlocks()
                + RadarModuleConstants.phasedArrayRangeBonusBlocks() * panels;
    }

    public static int overviewRadarBaseRangeBlocks(int overviewModuleCount) {
        int modules = Math.max(0, Math.min(overviewModuleCount, RadarModuleConstants.maxOverviewModules()));
        return modules <= 0
                ? 0
                : RadarModuleConstants.baseRangeBlocks() + RadarModuleConstants.overviewRangeBonusBlocks() * modules;
    }

    public static int basicRadarBaseRangeBlocks(int activePanelCount) {
        return radarBaseRangeBlocks(activePanelCount);
    }

    public static int radarModeRangeBlocks(com.limbo2136.powerradar.radar.RadarScanMode mode, int phasedArrayPanelCount) {
        int range = radarBaseRangeBlocks(phasedArrayPanelCount);
        if (mode == com.limbo2136.powerradar.radar.RadarScanMode.SKY) {
            return (int) Math.floor(range * airRangeMultiplier());
        }
        return range;
    }

    public static int basicRadarModeRangeBlocks(com.limbo2136.powerradar.radar.RadarScanMode mode, int activePanelCount) {
        return radarModeRangeBlocks(mode, activePanelCount);
    }

    public static double radarRangeMultiplier(double voltageVolts) {
        if (!Double.isFinite(voltageVolts) || voltageVolts < radarMinVoltage() || voltageVolts > radarMaxVoltage()) {
            return 0.0;
        }
        if (voltageVolts >= radarFullRangeVoltage()) {
            return 1.0;
        }
        double fraction = (voltageVolts - radarMinVoltage()) / Math.max(0.001D, radarFullRangeVoltage() - radarMinVoltage());
        return clamp(0.5 + 0.5 * fraction, 0.5, 1.0);
    }

    public static double constantPowerResistanceOhms(double measuredVoltageVolts, double nominalVoltageVolts, double powerWatts) {
        if (!Double.isFinite(powerWatts) || powerWatts <= 0.0) {
            return OFF_RESISTANCE_OHMS;
        }
        double voltage = sanitizeCalculationVoltage(measuredVoltageVolts, nominalVoltageVolts);
        return Math.max(MIN_SAFE_RESISTANCE_OHMS, voltage * voltage / powerWatts);
    }

    public static double parallelResistanceOhms(double firstOhms, double secondOhms) {
        if (!Double.isFinite(firstOhms) || firstOhms < MIN_SAFE_RESISTANCE_OHMS) {
            return sanitizeResistance(secondOhms);
        }
        if (!Double.isFinite(secondOhms) || secondOhms < MIN_SAFE_RESISTANCE_OHMS) {
            return sanitizeResistance(firstOhms);
        }
        return sanitizeResistance(1.0 / (1.0 / firstOhms + 1.0 / secondOhms));
    }

    public static double currentAmps(double voltageVolts, double resistanceOhms) {
        if (!Double.isFinite(voltageVolts) || !Double.isFinite(resistanceOhms) || resistanceOhms < MIN_SAFE_RESISTANCE_OHMS) {
            return 0.0;
        }
        return Math.max(0.0, Math.abs(voltageVolts) / resistanceOhms);
    }

    public static double powerWatts(double voltageVolts, double resistanceOhms) {
        if (!Double.isFinite(voltageVolts) || !Double.isFinite(resistanceOhms) || resistanceOhms < MIN_SAFE_RESISTANCE_OHMS) {
            return 0.0;
        }
        return Math.max(0.0, voltageVolts * voltageVolts / resistanceOhms);
    }

    public static double sanitizeResistance(double resistanceOhms) {
        if (!Double.isFinite(resistanceOhms) || resistanceOhms <= 0.0) {
            return OFF_RESISTANCE_OHMS;
        }
        return Math.max(MIN_SAFE_RESISTANCE_OHMS, resistanceOhms);
    }

    private static double sanitizeCalculationVoltage(double measuredVoltageVolts, double nominalVoltageVolts) {
        double fallback = Double.isFinite(nominalVoltageVolts) && nominalVoltageVolts > 0.0 ? nominalVoltageVolts : 1.0;
        if (!Double.isFinite(measuredVoltageVolts) || measuredVoltageVolts <= 1.0) {
            return fallback;
        }
        return clamp(Math.abs(measuredVoltageVolts), 1.0, Math.max(fallback * 4.0, 1.0));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int maxRadarPanels() { return PowerRadarServerConfig.maxRadarPanels(); }
    public static double radarControllerPowerWatts() { return PowerRadarServerConfig.radarControllerPowerWatts(); }
    public static double basicRadarPanelPowerWatts() { return PowerRadarServerConfig.basicRadarPanelPowerWatts(); }
    public static double radarNominalVoltage() { return PowerRadarServerConfig.radarNominalVoltage(); }
    public static double radarMinVoltage() { return PowerRadarServerConfig.radarMinVoltage(); }
    public static double radarRestartVoltage() { return PowerRadarServerConfig.radarRestartVoltage(); }
    public static double radarFullRangeVoltage() { return PowerRadarServerConfig.radarFullRangeVoltage(); }
    public static double radarMaxVoltage() { return PowerRadarServerConfig.radarMaxVoltage(); }
    public static double radarOvervoltageRecovery() { return PowerRadarServerConfig.radarOvervoltageRecovery(); }
    public static double monitorControllerPowerWatts() { return PowerRadarServerConfig.monitorControllerPowerWatts(); }
    public static double radarDisplayPowerWatts() { return PowerRadarServerConfig.radarDisplayPowerWatts(); }
    public static double monitorNominalVoltage() { return PowerRadarServerConfig.monitorNominalVoltage(); }
    public static double monitorMinVoltage() { return PowerRadarServerConfig.monitorMinVoltage(); }
    public static double monitorRestartVoltage() { return PowerRadarServerConfig.monitorRestartVoltage(); }
    public static double monitorMaxVoltage() { return PowerRadarServerConfig.monitorMaxVoltage(); }
    public static double monitorOvervoltageRecovery() { return PowerRadarServerConfig.monitorOvervoltageRecovery(); }
    public static int radarBaseRangeBlocks() { return PowerRadarServerConfig.radarBaseRangeBlocks(); }
    public static int basicPanelRangeBonusBlocks() { return PowerRadarServerConfig.basicPanelRangeBonusBlocks(); }
    public static int groundUpBlocks() { return PowerRadarServerConfig.groundUpBlocks(); }
    public static int groundDownBlocks() { return PowerRadarServerConfig.groundDownBlocks(); }
    public static double airRangeMultiplier() { return PowerRadarServerConfig.airRangeMultiplier(); }
    public static double airFovDegrees() { return PowerRadarServerConfig.airFovDegrees(); }
    public static int airMinYOffset() { return PowerRadarServerConfig.airMinYOffset(); }
    public static int airMaxYOffset() { return PowerRadarServerConfig.airMaxYOffset(); }

}
