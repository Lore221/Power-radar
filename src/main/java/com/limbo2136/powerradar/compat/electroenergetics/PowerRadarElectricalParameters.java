package com.limbo2136.powerradar.compat.electroenergetics;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Единый каталог электрических характеристик блоков Power Radar.
 * Значения по умолчанию меняются здесь, а серверный TOML может переопределить их без пересборки мода.
 */
public final class PowerRadarElectricalParameters {
    // Служебные пределы защищают расчёты цепи. Это не игровые настройки блоков.
    public static final double MIN_SAFE_RESISTANCE_OHMS = 0.001D;
    public static final double OFF_RESISTANCE_OHMS = 200_000_000.0D;
    private static final double MAX_VOLTAGE_VOLTS = 1_000_000.0D;
    private static final double MAX_RESISTANCE_OHMS = 1_000_000_000.0D;
    private static final double MAX_POWER_WATTS = 1_000_000_000.0D;

    // Базовые значения напряжения. Порядок: номинал, минимум, перезапуск, максимум, сброс перенапряжения.
    private static final LoadVoltageRange DEFAULT_RADAR_VOLTAGE =
            new LoadVoltageRange(600.0D, 400.0D, 430.0D, 700.0D, 650.0D);
    private static final double DEFAULT_RADAR_FULL_RANGE_VOLTAGE = 600.0D;
    private static final LoadVoltageRange DEFAULT_MONITOR_VOLTAGE =
            new LoadVoltageRange(24.0D, 18.0D, 20.0D, 30.0D, 28.0D);
    private static final LoadVoltageRange DEFAULT_SHELL_ALARM_VOLTAGE =
            new LoadVoltageRange(400.0D, 300.0D, 320.0D, 400.0D, 380.0D);

    // Контроллеры наведения являются резистивной нагрузкой; скорость растёт до fullSpeed.
    private static final DriveVoltageRange DEFAULT_TARGET_CONTROLLER_VOLTAGE =
            new DriveVoltageRange(200.0D, 300.0D, 360.0D);
    private static final DriveVoltageRange DEFAULT_INTERCEPTION_CONTROLLER_VOLTAGE =
            new DriveVoltageRange(200.0D, 300.0D, 360.0D);
    private static final double DEFAULT_TARGET_CONTROLLER_RESISTANCE_OHMS = 30.0D;
    private static final double DEFAULT_INTERCEPTION_CONTROLLER_RESISTANCE_OHMS = 30.0D;

    // Постоянная потребляемая мощность отдельных блоков и модулей в ваттах.
    private static final double DEFAULT_RADAR_CONTROLLER_POWER_WATTS = 1_000.0D;
    private static final double DEFAULT_PHASED_ARRAY_PANEL_POWER_WATTS = 700.0D;
    private static final double DEFAULT_OVERVIEW_MODULE_POWER_WATTS = 700.0D;
    private static final double DEFAULT_MONITOR_CONTROLLER_POWER_WATTS = 45.0D;
    private static final double DEFAULT_RADAR_DISPLAY_POWER_WATTS = 5.0D;
    private static final double DEFAULT_COMPUTING_BLOCK_POWER_WATTS = 45.0D;
    private static final double DEFAULT_ONBOARD_COMPUTER_POWER_WATTS = 50.0D;
    private static final double DEFAULT_SHELL_ALARM_POWER_WATTS = 45.0D;

    private static ModConfigSpec.DoubleValue radarNominalVoltage;
    private static ModConfigSpec.DoubleValue radarMinimumVoltage;
    private static ModConfigSpec.DoubleValue radarRestartVoltage;
    private static ModConfigSpec.DoubleValue radarFullRangeVoltage;
    private static ModConfigSpec.DoubleValue radarMaximumVoltage;
    private static ModConfigSpec.DoubleValue radarOvervoltageRecovery;
    private static ModConfigSpec.DoubleValue monitorNominalVoltage;
    private static ModConfigSpec.DoubleValue monitorMinimumVoltage;
    private static ModConfigSpec.DoubleValue monitorRestartVoltage;
    private static ModConfigSpec.DoubleValue monitorMaximumVoltage;
    private static ModConfigSpec.DoubleValue monitorOvervoltageRecovery;
    private static ModConfigSpec.DoubleValue shellAlarmNominalVoltage;
    private static ModConfigSpec.DoubleValue shellAlarmMinimumVoltage;
    private static ModConfigSpec.DoubleValue shellAlarmRestartVoltage;
    private static ModConfigSpec.DoubleValue shellAlarmMaximumVoltage;
    private static ModConfigSpec.DoubleValue shellAlarmOvervoltageRecovery;
    private static ModConfigSpec.DoubleValue targetControllerMinimumVoltage;
    private static ModConfigSpec.DoubleValue targetControllerFullSpeedVoltage;
    private static ModConfigSpec.DoubleValue targetControllerMaximumVoltage;
    private static ModConfigSpec.DoubleValue interceptionControllerMinimumVoltage;
    private static ModConfigSpec.DoubleValue interceptionControllerFullSpeedVoltage;
    private static ModConfigSpec.DoubleValue interceptionControllerMaximumVoltage;
    private static ModConfigSpec.DoubleValue targetControllerResistanceOhms;
    private static ModConfigSpec.DoubleValue interceptionControllerResistanceOhms;
    private static ModConfigSpec.DoubleValue radarControllerPowerWatts;
    private static ModConfigSpec.DoubleValue phasedArrayPanelPowerWatts;
    private static ModConfigSpec.DoubleValue overviewModulePowerWatts;
    private static ModConfigSpec.DoubleValue monitorControllerPowerWatts;
    private static ModConfigSpec.DoubleValue radarDisplayPowerWatts;
    private static ModConfigSpec.DoubleValue computingBlockPowerWatts;
    private static ModConfigSpec.DoubleValue onboardComputerPowerWatts;
    private static ModConfigSpec.DoubleValue shellAlarmPowerWatts;

    private PowerRadarElectricalParameters() {
    }

    /** Подключает электрические секции к общему серверному конфигу, не меняя существующие TOML-пути. */
    public static void defineConfig(ModConfigSpec.Builder builder) {
        defineVoltages(builder);
        defineResistances(builder);
        definePowerRatings(builder);
    }

    // Регистрирует пороги напряжения. restart/recovery создают гистерезис и не дают состоянию мигать на границе.
    private static void defineVoltages(ModConfigSpec.Builder builder) {
        builder.push("voltages");

        builder.comment("Power supply for radar controllers and their modules, in volts.").push("radar");
        radarNominalVoltage = voltage(builder, "nominal", DEFAULT_RADAR_VOLTAGE.nominal(), 1.0D,
                "Nominal voltage used to calculate the constant-power load.");
        radarMinimumVoltage = voltage(builder, "minimum", DEFAULT_RADAR_VOLTAGE.minimum(), 0.0D,
                "The radar shuts down below this voltage.");
        radarRestartVoltage = voltage(builder, "restart", DEFAULT_RADAR_VOLTAGE.restart(), 0.0D,
                "After an undervoltage shutdown, the radar restarts only above this voltage.");
        radarFullRangeVoltage = voltage(builder, "full_range", DEFAULT_RADAR_FULL_RANGE_VOLTAGE,
                MIN_SAFE_RESISTANCE_OHMS, "Voltage at which the radar reaches its full range.");
        radarMaximumVoltage = voltage(builder, "maximum", DEFAULT_RADAR_VOLTAGE.maximum(),
                MIN_SAFE_RESISTANCE_OHMS, "Overvoltage protection trips above this voltage.");
        radarOvervoltageRecovery = voltage(builder, "overvoltage_recovery",
                DEFAULT_RADAR_VOLTAGE.overvoltageRecovery(), 0.0D,
                "After an overvoltage shutdown, the radar recovers only below this voltage.");
        builder.pop();

        builder.comment("Shared low-voltage supply for monitors, the Computing Block, and OnBoard, in volts.")
                .push("monitor");
        monitorNominalVoltage = voltage(builder, "nominal", DEFAULT_MONITOR_VOLTAGE.nominal(), 1.0D,
                "Nominal voltage used to calculate the constant-power load.");
        monitorMinimumVoltage = voltage(builder, "minimum", DEFAULT_MONITOR_VOLTAGE.minimum(), 0.0D,
                "The device shuts down below this voltage.");
        monitorRestartVoltage = voltage(builder, "restart", DEFAULT_MONITOR_VOLTAGE.restart(), 0.0D,
                "After an undervoltage shutdown, the device restarts only above this voltage.");
        monitorMaximumVoltage = voltage(builder, "maximum", DEFAULT_MONITOR_VOLTAGE.maximum(),
                MIN_SAFE_RESISTANCE_OHMS, "Overvoltage protection trips above this voltage.");
        monitorOvervoltageRecovery = voltage(builder, "overvoltage_recovery",
                DEFAULT_MONITOR_VOLTAGE.overvoltageRecovery(), 0.0D,
                "After an overvoltage shutdown, the device recovers only below this voltage.");
        builder.pop();

        builder.comment("Shell Alarm power supply, in volts.").push("shell_alarm");
        shellAlarmNominalVoltage = voltage(builder, "nominal", DEFAULT_SHELL_ALARM_VOLTAGE.nominal(), 1.0D,
                "Nominal voltage used to calculate the constant-power load.");
        shellAlarmMinimumVoltage = voltage(builder, "minimum", DEFAULT_SHELL_ALARM_VOLTAGE.minimum(), 0.0D,
                "The alarm shuts down below this voltage.");
        shellAlarmRestartVoltage = voltage(builder, "restart", DEFAULT_SHELL_ALARM_VOLTAGE.restart(), 0.0D,
                "After an undervoltage shutdown, the alarm restarts only above this voltage.");
        shellAlarmMaximumVoltage = voltage(builder, "maximum", DEFAULT_SHELL_ALARM_VOLTAGE.maximum(),
                MIN_SAFE_RESISTANCE_OHMS, "Overvoltage protection trips above this voltage.");
        shellAlarmOvervoltageRecovery = voltage(builder, "overvoltage_recovery",
                DEFAULT_SHELL_ALARM_VOLTAGE.overvoltageRecovery(), 0.0D,
                "After an overvoltage shutdown, the alarm recovers only below this voltage.");
        builder.pop();

        defineDriveVoltages(builder, "target_controller", DEFAULT_TARGET_CONTROLLER_VOLTAGE, true);
        defineDriveVoltages(builder, "interception_controller", DEFAULT_INTERCEPTION_CONTROLLER_VOLTAGE, false);
        builder.pop();
    }

    // Оба привода используют одинаковую модель: минимум запуска, полная скорость и предел перенапряжения.
    private static void defineDriveVoltages(
            ModConfigSpec.Builder builder,
            String path,
            DriveVoltageRange defaults,
            boolean targetController
    ) {
        builder.comment(targetController
                        ? "Target Controller power supply, in volts."
                        : "Interception Controller power supply, in volts.")
                .push(path);
        ModConfigSpec.DoubleValue minimum = voltage(builder, "minimum", defaults.minimum(), 0.0D,
                "Minimum voltage at which the drive starts operating.");
        ModConfigSpec.DoubleValue fullSpeed = voltage(builder, "full_speed", defaults.fullSpeed(),
                MIN_SAFE_RESISTANCE_OHMS, "Voltage corresponding to full drive speed.");
        ModConfigSpec.DoubleValue maximum = voltage(builder, "maximum", defaults.maximum(),
                MIN_SAFE_RESISTANCE_OHMS, "Maximum operating voltage.");
        if (targetController) {
            targetControllerMinimumVoltage = minimum;
            targetControllerFullSpeedVoltage = fullSpeed;
            targetControllerMaximumVoltage = maximum;
        } else {
            interceptionControllerMinimumVoltage = minimum;
            interceptionControllerFullSpeedVoltage = fullSpeed;
            interceptionControllerMaximumVoltage = maximum;
        }
        builder.pop();
    }

    // Резистивные контроллеры потребляют мощность по закону P = U² / R.
    private static void defineResistances(ModConfigSpec.Builder builder) {
        builder.comment("Fixed resistance of resistive devices, in ohms.").push("resistances");
        targetControllerResistanceOhms = builder.comment("Target Controller resistance.")
                .defineInRange("target_controller_ohms", DEFAULT_TARGET_CONTROLLER_RESISTANCE_OHMS,
                        MIN_SAFE_RESISTANCE_OHMS, MAX_RESISTANCE_OHMS);
        interceptionControllerResistanceOhms = builder.comment("Interception Controller resistance.")
                .defineInRange("interception_controller_ohms", DEFAULT_INTERCEPTION_CONTROLLER_RESISTANCE_OHMS,
                        MIN_SAFE_RESISTANCE_OHMS, MAX_RESISTANCE_OHMS);
        builder.pop();
    }

    // Для постоянной нагрузки CEE пересчитывает эквивалентное сопротивление по текущему напряжению.
    private static void definePowerRatings(ModConfigSpec.Builder builder) {
        builder.comment("Nominal constant power draw of blocks, in watts.").push("ratings");
        radarControllerPowerWatts = power(builder, "radar_controller_power_watts",
                DEFAULT_RADAR_CONTROLLER_POWER_WATTS, "Base power draw of one radar controller.");
        phasedArrayPanelPowerWatts = power(builder, "phased_array_panel_power_watts",
                DEFAULT_PHASED_ARRAY_PANEL_POWER_WATTS, "Additional power draw of each phased-array panel.");
        overviewModulePowerWatts = power(builder, "overview_module_power_watts",
                DEFAULT_OVERVIEW_MODULE_POWER_WATTS, "Additional power draw of each overview module.");
        monitorControllerPowerWatts = power(builder, "monitor_controller_power_watts",
                DEFAULT_MONITOR_CONTROLLER_POWER_WATTS, "Base power draw of the monitor controller.");
        radarDisplayPowerWatts = power(builder, "radar_display_power_watts",
                DEFAULT_RADAR_DISPLAY_POWER_WATTS, "Additional power draw of each radar display block.");
        computingBlockPowerWatts = power(builder, "computing_block_power_watts",
                DEFAULT_COMPUTING_BLOCK_POWER_WATTS, "Computing Block power draw.");
        onboardComputerPowerWatts = power(builder, "onboard_computer_power_watts",
                DEFAULT_ONBOARD_COMPUTER_POWER_WATTS, "OnBoard Computer power draw.");
        shellAlarmPowerWatts = power(builder, "shell_alarm_power_watts",
                DEFAULT_SHELL_ALARM_POWER_WATTS, "Shell Alarm power draw.");
        builder.pop();
    }

    private static ModConfigSpec.DoubleValue voltage(
            ModConfigSpec.Builder builder,
            String path,
            double defaultValue,
            double minimum,
            String comment
    ) {
        return builder.comment(comment).defineInRange(path, defaultValue, minimum, MAX_VOLTAGE_VOLTS);
    }

    private static ModConfigSpec.DoubleValue power(
            ModConfigSpec.Builder builder,
            String path,
            double defaultValue,
            String comment
    ) {
        return builder.comment(comment).defineInRange(path, defaultValue, 0.0D, MAX_POWER_WATTS);
    }

    public static final class Voltages {
        private Voltages() {
        }

        public static LoadVoltageRange radar() {
            ensureConfigDefined();
            return new LoadVoltageRange(value(radarNominalVoltage), value(radarMinimumVoltage),
                    value(radarRestartVoltage), value(radarMaximumVoltage), value(radarOvervoltageRecovery));
        }

        public static double radarFullRange() {
            ensureConfigDefined();
            return value(radarFullRangeVoltage);
        }

        public static LoadVoltageRange monitor() {
            ensureConfigDefined();
            return new LoadVoltageRange(value(monitorNominalVoltage), value(monitorMinimumVoltage),
                    value(monitorRestartVoltage), value(monitorMaximumVoltage), value(monitorOvervoltageRecovery));
        }

        public static LoadVoltageRange shellAlarm() {
            ensureConfigDefined();
            return new LoadVoltageRange(value(shellAlarmNominalVoltage), value(shellAlarmMinimumVoltage),
                    value(shellAlarmRestartVoltage), value(shellAlarmMaximumVoltage),
                    value(shellAlarmOvervoltageRecovery));
        }

        public static DriveVoltageRange targetController() {
            ensureConfigDefined();
            return new DriveVoltageRange(value(targetControllerMinimumVoltage),
                    value(targetControllerFullSpeedVoltage), value(targetControllerMaximumVoltage));
        }

        public static DriveVoltageRange interceptionController() {
            ensureConfigDefined();
            return new DriveVoltageRange(value(interceptionControllerMinimumVoltage),
                    value(interceptionControllerFullSpeedVoltage), value(interceptionControllerMaximumVoltage));
        }
    }

    public static final class Resistances {
        private Resistances() {
        }

        public static double targetController() {
            ensureConfigDefined();
            return value(targetControllerResistanceOhms);
        }

        public static double interceptionController() {
            ensureConfigDefined();
            return value(interceptionControllerResistanceOhms);
        }
    }

    public static final class Ratings {
        private Ratings() {
        }

        public static double radarControllerPowerWatts() {
            ensureConfigDefined();
            return value(radarControllerPowerWatts);
        }

        public static double phasedArrayPanelPowerWatts() {
            ensureConfigDefined();
            return value(phasedArrayPanelPowerWatts);
        }

        public static double overviewModulePowerWatts() {
            ensureConfigDefined();
            return value(overviewModulePowerWatts);
        }

        public static double monitorControllerPowerWatts() {
            ensureConfigDefined();
            return value(monitorControllerPowerWatts);
        }

        public static double radarDisplayPowerWatts() {
            ensureConfigDefined();
            return value(radarDisplayPowerWatts);
        }

        public static double computingBlockPowerWatts() {
            ensureConfigDefined();
            return value(computingBlockPowerWatts);
        }

        public static double onboardComputerPowerWatts() {
            ensureConfigDefined();
            return value(onboardComputerPowerWatts);
        }

        public static double shellAlarmPowerWatts() {
            ensureConfigDefined();
            return value(shellAlarmPowerWatts);
        }
    }

    // При раннем обращении электрического блока принудительно строим общий SPEC до чтения его полей.
    private static void ensureConfigDefined() {
        if (radarNominalVoltage == null) {
            ModConfigSpec ignored = PowerRadarServerConfig.SPEC;
        }
    }

    private static double value(ModConfigSpec.DoubleValue configValue) {
        return PowerRadarServerConfig.SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
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
