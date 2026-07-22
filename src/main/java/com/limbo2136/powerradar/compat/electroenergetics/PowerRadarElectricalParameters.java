package com.limbo2136.powerradar.compat.electroenergetics;

import com.limbo2136.powerradar.PowerRadarServerConfig;

/**
 * Single source of truth for the electrical characteristics exposed by Power Radar blocks.
 * Gameplay constants and circuit math intentionally live elsewhere.
 */
public final class PowerRadarElectricalParameters {
    public static final double MIN_SAFE_RESISTANCE_OHMS = 0.001D;
    public static final double OFF_RESISTANCE_OHMS = 200_000_000.0D;

    private PowerRadarElectricalParameters() {
    }

    public static final class Voltages {
        private Voltages() {
        }

        public static LoadVoltageRange radar() {
            return new LoadVoltageRange(
                    PowerRadarServerConfig.radarNominalVoltage(),
                    PowerRadarServerConfig.radarMinVoltage(),
                    PowerRadarServerConfig.radarRestartVoltage(),
                    PowerRadarServerConfig.radarMaxVoltage(),
                    PowerRadarServerConfig.radarOvervoltageRecovery());
        }

        public static double radarFullRange() {
            return PowerRadarServerConfig.radarFullRangeVoltage();
        }

        public static LoadVoltageRange monitor() {
            return new LoadVoltageRange(
                    PowerRadarServerConfig.monitorNominalVoltage(),
                    PowerRadarServerConfig.monitorMinVoltage(),
                    PowerRadarServerConfig.monitorRestartVoltage(),
                    PowerRadarServerConfig.monitorMaxVoltage(),
                    PowerRadarServerConfig.monitorOvervoltageRecovery());
        }

        public static LoadVoltageRange shellAlarm() {
            return new LoadVoltageRange(
                    PowerRadarServerConfig.shellAlarmNominalVoltage(),
                    PowerRadarServerConfig.shellAlarmMinVoltage(),
                    PowerRadarServerConfig.shellAlarmRestartVoltage(),
                    PowerRadarServerConfig.shellAlarmMaxVoltage(),
                    PowerRadarServerConfig.shellAlarmOvervoltageRecovery());
        }

        public static DriveVoltageRange targetController() {
            return new DriveVoltageRange(
                    PowerRadarServerConfig.targetControllerMinVoltage(),
                    PowerRadarServerConfig.targetControllerFullSpeedVoltage(),
                    PowerRadarServerConfig.targetControllerMaxVoltage());
        }

        public static DriveVoltageRange interceptionController() {
            return new DriveVoltageRange(
                    PowerRadarServerConfig.interceptionControllerMinVoltage(),
                    PowerRadarServerConfig.interceptionControllerFullSpeedVoltage(),
                    PowerRadarServerConfig.interceptionControllerMaxVoltage());
        }
    }

    public static final class Resistances {
        private Resistances() {
        }

        public static double targetController() {
            return PowerRadarServerConfig.targetControllerResistanceOhms();
        }

        public static double interceptionController() {
            return PowerRadarServerConfig.interceptionControllerResistanceOhms();
        }
    }

    public static final class Ratings {
        private Ratings() {
        }

        public static double radarControllerPowerWatts() {
            return PowerRadarServerConfig.radarControllerPowerWatts();
        }

        public static double phasedArrayPanelPowerWatts() {
            return PowerRadarServerConfig.basicRadarPanelPowerWatts();
        }

        public static double overviewModulePowerWatts() {
            return PowerRadarServerConfig.overviewModulePowerWatts();
        }

        public static double monitorControllerPowerWatts() {
            return PowerRadarServerConfig.monitorControllerPowerWatts();
        }

        public static double radarDisplayPowerWatts() {
            return PowerRadarServerConfig.radarDisplayPowerWatts();
        }

        public static double computingBlockPowerWatts() {
            return PowerRadarServerConfig.computingBlockPowerWatts();
        }

        public static double onboardComputerPowerWatts() {
            return PowerRadarServerConfig.onboardComputerPowerWatts();
        }

        public static double shellAlarmPowerWatts() {
            return PowerRadarServerConfig.shellAlarmPowerWatts();
        }
    }

    public record LoadVoltageRange(
            double nominal,
            double minimum,
            double restart,
            double maximum,
            double overvoltageRecovery
    ) {
    }

    public record DriveVoltageRange(double minimum, double fullSpeed, double maximum) {
    }
}
