package com.limbo2136.powerradar.compat.electroenergetics;

import com.limbo2136.powerradar.PowerRadarServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Единый каталог электрических характеристик блоков Power Radar.
 * Значения по умолчанию меняются здесь, а серверный TOML может переопределить
 * их без пересборки мода.
 */
public final class PowerRadarElectricalParameters {
        // Служебные пределы защищают расчёты цепи. Это не игровые настройки блоков.
        public static final double MIN_SAFE_RESISTANCE_OHMS = 0.001D;
        public static final double OFF_RESISTANCE_OHMS = 200_000_000.0D;
        private static final double MAX_VOLTAGE_VOLTS = 1_000_000.0D;
        private static final double MAX_RESISTANCE_OHMS = 1_000_000_000.0D;
        private static final double MAX_POWER_WATTS = 1_000_000_000.0D;

        // Базовые значения напряжения. Порядок: номинал, минимум, максимум.
        private static final LoadVoltageRange DEFAULT_RADAR_VOLTAGE = new LoadVoltageRange(380.0D, 120.0D, 400.0D);
        private static final LoadVoltageRange DEFAULT_RADAR_DISPLAY_VOLTAGE = new LoadVoltageRange(220.0D, 180.0D,
                        250.0D);
        private static final LoadVoltageRange DEFAULT_SHELL_ALARM_VOLTAGE = new LoadVoltageRange(220.0D, 180.0D,
                        250.0D);
        private static final LoadVoltageRange DEFAULT_LOGIC_DOCK_VOLTAGE = new LoadVoltageRange(24.0D, 18.0D, 26.0D);
        private static final LoadVoltageRange DEFAULT_ONBOARD_COMPUTER_VOLTAGE = new LoadVoltageRange(220.0D, 180.0D,
                        250.0D);
        private static final LoadVoltageRange DEFAULT_EW_SYSTEM_VOLTAGE = new LoadVoltageRange(24.0D, 18.0D, 26.0D);

        // Контроллеры наведения являются резистивной нагрузкой; скорость растёт до
        // номинального напряжения.
        private static final DriveVoltageRange DEFAULT_TARGET_CONTROLLER_VOLTAGE = new DriveVoltageRange(200.0D, 380.0D,
                        400.0D);
        private static final DriveVoltageRange DEFAULT_INTERCEPTION_CONTROLLER_VOLTAGE = new DriveVoltageRange(200.0D,
                        380.0D, 400.0D);
        private static final double DEFAULT_TARGET_CONTROLLER_RESISTANCE_OHMS = 20.0D;
        private static final double DEFAULT_INTERCEPTION_CONTROLLER_RESISTANCE_OHMS = 20.0D;

        // Номинальная мощность задаётся при номинальном напряжении и определяет
        // постоянное сопротивление.
        private static final double DEFAULT_RADAR_CONTROLLER_POWER_WATTS = 1_000.0D;
        private static final double DEFAULT_AIRCRAFT_RADAR_POWER_WATTS = 5_000.0D;
        private static final double DEFAULT_PHASED_ARRAY_PANEL_POWER_WATTS = 250.0D;
        private static final double DEFAULT_OVERVIEW_MODULE_POWER_WATTS = 1500.0D;
        private static final double DEFAULT_RADAR_DISPLAY_BASE_POWER_WATTS = 250.0D;
        private static final double DEFAULT_RADAR_DISPLAY_POWER_WATTS = 25.0D;
        private static final double DEFAULT_PANEL_RADAR_DISPLAY_POWER_WATTS = 25.0D;
        private static final double DEFAULT_LOGIC_DOCK_POWER_WATTS = 50.0D;
        private static final double DEFAULT_ONBOARD_COMPUTER_POWER_WATTS = 1000.0D;
        private static final double DEFAULT_SHELL_ALARM_POWER_WATTS = 750.0D;
        private static final double DEFAULT_EW_SYSTEM_POWER_WATTS = 600.0D;

        private static LoadVoltageConfig radarVoltageConfig;
        private static LoadVoltageConfig radarDisplayVoltageConfig;
        private static LoadVoltageConfig shellAlarmVoltageConfig;
        private static LoadVoltageConfig logicDockVoltageConfig;
        private static LoadVoltageConfig onboardComputerVoltageConfig;
        private static LoadVoltageConfig ewSystemVoltageConfig;
        private static ModConfigSpec.DoubleValue targetControllerMinimumVoltage;
        private static ModConfigSpec.DoubleValue targetControllerNominalVoltage;
        private static ModConfigSpec.DoubleValue targetControllerMaximumVoltage;
        private static ModConfigSpec.DoubleValue interceptionControllerMinimumVoltage;
        private static ModConfigSpec.DoubleValue interceptionControllerNominalVoltage;
        private static ModConfigSpec.DoubleValue interceptionControllerMaximumVoltage;
        private static ModConfigSpec.DoubleValue targetControllerResistanceOhms;
        private static ModConfigSpec.DoubleValue interceptionControllerResistanceOhms;
        private static ModConfigSpec.DoubleValue radarControllerPowerWatts;
        private static ModConfigSpec.DoubleValue aircraftRadarPowerWatts;
        private static ModConfigSpec.DoubleValue phasedArrayPanelPowerWatts;
        private static ModConfigSpec.DoubleValue overviewModulePowerWatts;
        private static ModConfigSpec.DoubleValue radarDisplayBasePowerWatts;
        private static ModConfigSpec.DoubleValue radarDisplayPowerWatts;
        private static ModConfigSpec.DoubleValue panelRadarDisplayPowerWatts;
        private static ModConfigSpec.DoubleValue logicDockPowerWatts;
        private static ModConfigSpec.DoubleValue onboardComputerPowerWatts;
        private static ModConfigSpec.DoubleValue shellAlarmPowerWatts;
        private static ModConfigSpec.DoubleValue ewSystemPowerWatts;

        private PowerRadarElectricalParameters() {
        }

        /**
         * Подключает независимые электрические секции устройств к общему серверному
         * конфигу.
         */
        public static void defineConfig(ModConfigSpec.Builder builder) {
                defineVoltages(builder);
                defineResistances(builder);
                definePowerRatings(builder);
        }

        // Регистрирует независимые паспортные диапазоны напряжения без гистерезиса.
        private static void defineVoltages(ModConfigSpec.Builder builder) {
                builder.push("voltages");

                radarVoltageConfig = defineLoadVoltages(builder, "radar", DEFAULT_RADAR_VOLTAGE,
                                "Power supply for radar controllers and their modules, in volts.");
                radarDisplayVoltageConfig = defineLoadVoltages(builder, "radar_display",
                                DEFAULT_RADAR_DISPLAY_VOLTAGE,
                                "Radar Display power supply in the world and electrical panel, in volts.");
                shellAlarmVoltageConfig = defineLoadVoltages(builder, "shell_alarm", DEFAULT_SHELL_ALARM_VOLTAGE,
                                "Shell Alarm power supply, in volts.");
                logicDockVoltageConfig = defineLoadVoltages(builder, "logic_dock", DEFAULT_LOGIC_DOCK_VOLTAGE,
                                "Logic Dock power supply for both the world block and electrical panel attachment, in volts.");
                onboardComputerVoltageConfig = defineLoadVoltages(builder, "onboard_computer",
                                DEFAULT_ONBOARD_COMPUTER_VOLTAGE, "OnBoard Computer power supply, in volts.");
                ewSystemVoltageConfig = defineLoadVoltages(builder, "ew_system", DEFAULT_EW_SYSTEM_VOLTAGE,
                                "Electronic Warfare System power supply, in volts.");

                defineDriveVoltages(builder, "target_controller", DEFAULT_TARGET_CONTROLLER_VOLTAGE, true);
                defineDriveVoltages(builder, "interception_controller", DEFAULT_INTERCEPTION_CONTROLLER_VOLTAGE, false);
                builder.pop();
        }

        private static LoadVoltageConfig defineLoadVoltages(
                        ModConfigSpec.Builder builder,
                        String path,
                        LoadVoltageRange defaults,
                        String comment) {
                builder.comment(comment).push(path);
                ModConfigSpec.DoubleValue nominal = voltage(builder, "nominal", defaults.nominal(), 1.0D,
                                "Rated voltage used for nominal load calculations; scalable devices reach full performance at this voltage.");
                ModConfigSpec.DoubleValue minimum = voltage(builder, "minimum", defaults.minimum(), 0.0D,
                                "Minimum voltage at which the device operates.");
                ModConfigSpec.DoubleValue maximum = voltage(builder, "maximum", defaults.maximum(),
                                MIN_SAFE_RESISTANCE_OHMS, "Maximum operating voltage.");
                builder.pop();
                return new LoadVoltageConfig(nominal, minimum, maximum);
        }

        // Оба привода используют одинаковую модель: минимум запуска, номинал полной
        // скорости и предел перенапряжения.
        private static void defineDriveVoltages(
                        ModConfigSpec.Builder builder,
                        String path,
                        DriveVoltageRange defaults,
                        boolean targetController) {
                builder.comment(targetController
                                ? "Target Controller power supply, in volts."
                                : "Interception Controller power supply, in volts.")
                                .push(path);
                ModConfigSpec.DoubleValue minimum = voltage(builder, "minimum", defaults.minimum(), 0.0D,
                                "Minimum voltage at which the drive starts operating.");
                ModConfigSpec.DoubleValue nominal = voltage(builder, "nominal", defaults.nominal(),
                                MIN_SAFE_RESISTANCE_OHMS, "Rated voltage corresponding to full drive speed.");
                ModConfigSpec.DoubleValue maximum = voltage(builder, "maximum", defaults.maximum(),
                                MIN_SAFE_RESISTANCE_OHMS, "Maximum operating voltage.");
                if (targetController) {
                        targetControllerMinimumVoltage = minimum;
                        targetControllerNominalVoltage = nominal;
                        targetControllerMaximumVoltage = maximum;
                } else {
                        interceptionControllerMinimumVoltage = minimum;
                        interceptionControllerNominalVoltage = nominal;
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
                                .defineInRange("interception_controller_ohms",
                                                DEFAULT_INTERCEPTION_CONTROLLER_RESISTANCE_OHMS,
                                                MIN_SAFE_RESISTANCE_OHMS, MAX_RESISTANCE_OHMS);
                builder.pop();
        }

        // Эти мощности относятся к номинальному напряжению; CEE получает рассчитанное
        // из них постоянное сопротивление.
        private static void definePowerRatings(ModConfigSpec.Builder builder) {
                builder.comment("Power consumed at the device's nominal voltage, in watts.").push("ratings");
                radarControllerPowerWatts = power(builder, "radar_controller_power_watts",
                                DEFAULT_RADAR_CONTROLLER_POWER_WATTS, "Nominal base power of one radar controller.");
                aircraftRadarPowerWatts = power(builder, "aircraft_radar_power_watts",
                                DEFAULT_AIRCRAFT_RADAR_POWER_WATTS,
                                "Nominal power of the integrated Aircraft Radar.");
                phasedArrayPanelPowerWatts = power(builder, "phased_array_panel_power_watts",
                                DEFAULT_PHASED_ARRAY_PANEL_POWER_WATTS,
                                "Nominal power added by each phased-array panel.");
                overviewModulePowerWatts = power(builder, "overview_module_power_watts",
                                DEFAULT_OVERVIEW_MODULE_POWER_WATTS, "Nominal power added by each overview module.");
                radarDisplayBasePowerWatts = power(builder, "radar_display_base_power_watts",
                                DEFAULT_RADAR_DISPLAY_BASE_POWER_WATTS,
                                "Nominal power of a single root Radar Display.");
                radarDisplayPowerWatts = power(builder, "radar_display_power_watts",
                                DEFAULT_RADAR_DISPLAY_POWER_WATTS,
                                "Nominal power added by each display block in a large monitor.");
                panelRadarDisplayPowerWatts = power(builder, "panel_radar_display_power_watts",
                                DEFAULT_PANEL_RADAR_DISPLAY_POWER_WATTS,
                                "Nominal power of a standalone Radar Display installed in an electrical panel.");
                logicDockPowerWatts = power(builder, "logic_dock_power_watts",
                                DEFAULT_LOGIC_DOCK_POWER_WATTS, "Logic Dock nominal power.");
                onboardComputerPowerWatts = power(builder, "onboard_computer_power_watts",
                                DEFAULT_ONBOARD_COMPUTER_POWER_WATTS, "OnBoard Computer nominal power.");
                shellAlarmPowerWatts = power(builder, "shell_alarm_power_watts",
                                DEFAULT_SHELL_ALARM_POWER_WATTS, "Shell Alarm nominal power.");
                ewSystemPowerWatts = power(builder, "ew_system_power_watts",
                                DEFAULT_EW_SYSTEM_POWER_WATTS, "Electronic Warfare System nominal power.");
                builder.pop();
        }

        private static ModConfigSpec.DoubleValue voltage(
                        ModConfigSpec.Builder builder,
                        String path,
                        double defaultValue,
                        double minimum,
                        String comment) {
                return builder.comment(comment).defineInRange(path, defaultValue, minimum, MAX_VOLTAGE_VOLTS);
        }

        private static ModConfigSpec.DoubleValue power(
                        ModConfigSpec.Builder builder,
                        String path,
                        double defaultValue,
                        String comment) {
                return builder.comment(comment).defineInRange(path, defaultValue, 0.0D, MAX_POWER_WATTS);
        }

        public static final class Voltages {
                private Voltages() {
                }

                public static LoadVoltageRange radar() {
                        ensureConfigDefined();
                        return value(radarVoltageConfig);
                }

                public static LoadVoltageRange radarDisplay() {
                        ensureConfigDefined();
                        return value(radarDisplayVoltageConfig);
                }

                public static LoadVoltageRange shellAlarm() {
                        ensureConfigDefined();
                        return value(shellAlarmVoltageConfig);
                }

                public static LoadVoltageRange logicDock() {
                        ensureConfigDefined();
                        return value(logicDockVoltageConfig);
                }

                public static LoadVoltageRange onboardComputer() {
                        ensureConfigDefined();
                        return value(onboardComputerVoltageConfig);
                }

                public static LoadVoltageRange ewSystem() {
                        ensureConfigDefined();
                        return value(ewSystemVoltageConfig);
                }

                public static DriveVoltageRange targetController() {
                        ensureConfigDefined();
                        return new DriveVoltageRange(value(targetControllerMinimumVoltage),
                                        value(targetControllerNominalVoltage), value(targetControllerMaximumVoltage));
                }

                public static DriveVoltageRange interceptionController() {
                        ensureConfigDefined();
                        return new DriveVoltageRange(value(interceptionControllerMinimumVoltage),
                                        value(interceptionControllerNominalVoltage),
                                        value(interceptionControllerMaximumVoltage));
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

                public static double aircraftRadarPowerWatts() {
                        ensureConfigDefined();
                        return value(aircraftRadarPowerWatts);
                }

                public static double phasedArrayPanelPowerWatts() {
                        ensureConfigDefined();
                        return value(phasedArrayPanelPowerWatts);
                }

                public static double overviewModulePowerWatts() {
                        ensureConfigDefined();
                        return value(overviewModulePowerWatts);
                }

                public static double radarDisplayBasePowerWatts() {
                        ensureConfigDefined();
                        return value(radarDisplayBasePowerWatts);
                }

                public static double radarDisplayPowerWatts() {
                        ensureConfigDefined();
                        return value(radarDisplayPowerWatts);
                }

                public static double panelRadarDisplayPowerWatts() {
                        ensureConfigDefined();
                        return value(panelRadarDisplayPowerWatts);
                }

                public static double logicDockPowerWatts() {
                        ensureConfigDefined();
                        return value(logicDockPowerWatts);
                }

                public static double onboardComputerPowerWatts() {
                        ensureConfigDefined();
                        return value(onboardComputerPowerWatts);
                }

                public static double shellAlarmPowerWatts() {
                        ensureConfigDefined();
                        return value(shellAlarmPowerWatts);
                }

                public static double ewSystemPowerWatts() {
                        ensureConfigDefined();
                        return value(ewSystemPowerWatts);
                }
        }

        // При раннем обращении электрического блока принудительно завершаем инициализацию общего конфига.
        private static void ensureConfigDefined() {
                if (radarVoltageConfig == null) {
                        PowerRadarServerConfig.ensureInitialized();
                }
        }

        private static LoadVoltageRange value(LoadVoltageConfig config) {
                return new LoadVoltageRange(value(config.nominal()), value(config.minimum()), value(config.maximum()));
        }

        private static double value(ModConfigSpec.DoubleValue configValue) {
                return PowerRadarServerConfig.SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
        }

        private record LoadVoltageConfig(
                        ModConfigSpec.DoubleValue nominal,
                        ModConfigSpec.DoubleValue minimum,
                        ModConfigSpec.DoubleValue maximum) {
        }

        public record LoadVoltageRange(double nominal, double minimum, double maximum) {
        }

        public record DriveVoltageRange(double minimum, double nominal, double maximum) {
        }
}
