package com.limbo2136.powerradar;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class PowerRadarServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MAX_RADAR_PANELS;
    private static final ModConfigSpec.IntValue RADAR_BASE_RANGE_BLOCKS;
    private static final ModConfigSpec.IntValue BASIC_PANEL_RANGE_BONUS_BLOCKS;
    private static final ModConfigSpec.DoubleValue AIR_RANGE_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue AIR_FOV_DEGREES;
    private static final ModConfigSpec.IntValue GROUND_UP_BLOCKS;
    private static final ModConfigSpec.IntValue GROUND_DOWN_BLOCKS;
    private static final ModConfigSpec.IntValue AIR_MIN_Y_OFFSET;
    private static final ModConfigSpec.IntValue AIR_MAX_Y_OFFSET;

    private static final ModConfigSpec.DoubleValue RADAR_CONTROLLER_POWER_WATTS;
    private static final ModConfigSpec.DoubleValue BASIC_RADAR_PANEL_POWER_WATTS;
    private static final ModConfigSpec.DoubleValue OVERVIEW_MODULE_POWER_WATTS;
    private static final ModConfigSpec.DoubleValue RADAR_NOMINAL_VOLTAGE;
    private static final ModConfigSpec.DoubleValue RADAR_MIN_VOLTAGE;
    private static final ModConfigSpec.DoubleValue RADAR_RESTART_VOLTAGE;
    private static final ModConfigSpec.DoubleValue RADAR_FULL_RANGE_VOLTAGE;
    private static final ModConfigSpec.DoubleValue RADAR_MAX_VOLTAGE;
    private static final ModConfigSpec.DoubleValue RADAR_OVERVOLTAGE_RECOVERY;

    private static final ModConfigSpec.DoubleValue MONITOR_CONTROLLER_POWER_WATTS;
    private static final ModConfigSpec.DoubleValue RADAR_DISPLAY_POWER_WATTS;
    private static final ModConfigSpec.DoubleValue MONITOR_NOMINAL_VOLTAGE;
    private static final ModConfigSpec.DoubleValue MONITOR_MIN_VOLTAGE;
    private static final ModConfigSpec.DoubleValue MONITOR_RESTART_VOLTAGE;
    private static final ModConfigSpec.DoubleValue MONITOR_MAX_VOLTAGE;
    private static final ModConfigSpec.DoubleValue MONITOR_OVERVOLTAGE_RECOVERY;

    private static final ModConfigSpec.IntValue RADAR_SCAN_UPDATE_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue STRUCTURE_VALIDATION_INTERVAL_TICKS;
    private static final ModConfigSpec.IntValue STALE_TRACK_EXPIRATION_TICKS;
    private static final ModConfigSpec.IntValue ENTITY_QUERY_SLICE_SIZE;
    private static final ModConfigSpec.BooleanValue DETECT_PASSIVE_MOBS_BY_DEFAULT;
    private static final ModConfigSpec.IntValue RADAR_LINK_MAX_CONNECTION_DISTANCE_BLOCKS;
    private static final ModConfigSpec.BooleanValue RADAR_LINK_FORCELOAD_ENABLED;
    private static final ModConfigSpec.IntValue RADAR_LINK_FORCELOAD_RADIUS_CHUNKS;
    private static final ModConfigSpec.DoubleValue AUTOCANNON_MIN_FIRING_DISTANCE_BLOCKS;
    private static final ModConfigSpec.DoubleValue BIG_CANNON_MIN_FIRING_DISTANCE_BLOCKS;
    private static final ModConfigSpec.DoubleValue INTERCEPTION_SHELL_DESTRUCTION_PROBABILITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("radar_range");
        MAX_RADAR_PANELS = builder.defineInRange("max_radar_panels", 20, 1, 512);
        RADAR_BASE_RANGE_BLOCKS = builder.defineInRange("base_range_blocks", 80, 0, 100_000);
        BASIC_PANEL_RANGE_BONUS_BLOCKS = builder.defineInRange("basic_panel_range_bonus_blocks", 20, 0, 100_000);
        AIR_RANGE_MULTIPLIER = builder.defineInRange("air_range_multiplier", 1.5D, 0.0D, 100.0D);
        AIR_FOV_DEGREES = builder.defineInRange("air_fov_degrees", 90.0D, 1.0D, 360.0D);
        GROUND_UP_BLOCKS = builder.defineInRange("ground_up_blocks", 128, 0, 4096);
        GROUND_DOWN_BLOCKS = builder.defineInRange("ground_down_blocks", 20, 0, 4096);
        AIR_MIN_Y_OFFSET = builder.defineInRange("air_min_y_offset", 40, -4096, 4096);
        AIR_MAX_Y_OFFSET = builder.defineInRange("air_max_y_offset", 1500, 0, 32_000);
        builder.pop();

        builder.push("radar_electricity");
        RADAR_CONTROLLER_POWER_WATTS = builder.defineInRange("controller_power_watts", 1000.0D, 0.0D, 1.0E9D);
        BASIC_RADAR_PANEL_POWER_WATTS = builder.defineInRange("basic_panel_power_watts", 700.0D, 0.0D, 1.0E9D);
        OVERVIEW_MODULE_POWER_WATTS = builder.defineInRange("overview_module_power_watts", 700.0D, 0.0D, 1.0E9D);
        RADAR_NOMINAL_VOLTAGE = builder.defineInRange("nominal_voltage", 600.0D, 1.0D, 1.0E6D);
        RADAR_MIN_VOLTAGE = builder.defineInRange("min_voltage", 400.0D, 0.0D, 1.0E6D);
        RADAR_RESTART_VOLTAGE = builder.defineInRange("restart_voltage", 430.0D, 0.0D, 1.0E6D);
        RADAR_FULL_RANGE_VOLTAGE = builder.defineInRange("full_range_voltage", 600.0D, 0.001D, 1.0E6D);
        RADAR_MAX_VOLTAGE = builder.defineInRange("max_voltage", 700.0D, 0.001D, 1.0E6D);
        RADAR_OVERVOLTAGE_RECOVERY = builder.defineInRange("overvoltage_recovery", 650.0D, 0.0D, 1.0E6D);
        builder.pop();

        builder.push("monitor_electricity");
        MONITOR_CONTROLLER_POWER_WATTS = builder.defineInRange("controller_power_watts", 45.0D, 0.0D, 1.0E9D);
        RADAR_DISPLAY_POWER_WATTS = builder.defineInRange("display_power_watts", 5.0D, 0.0D, 1.0E9D);
        MONITOR_NOMINAL_VOLTAGE = builder.defineInRange("nominal_voltage", 24.0D, 1.0D, 1.0E6D);
        MONITOR_MIN_VOLTAGE = builder.defineInRange("min_voltage", 18.0D, 0.0D, 1.0E6D);
        MONITOR_RESTART_VOLTAGE = builder.defineInRange("restart_voltage", 20.0D, 0.0D, 1.0E6D);
        MONITOR_MAX_VOLTAGE = builder.defineInRange("max_voltage", 30.0D, 0.001D, 1.0E6D);
        MONITOR_OVERVOLTAGE_RECOVERY = builder.defineInRange("overvoltage_recovery", 28.0D, 0.0D, 1.0E6D);
        builder.pop();

        builder.push("performance");
        RADAR_SCAN_UPDATE_INTERVAL_TICKS = builder.defineInRange("radar_scan_update_interval_ticks", 5, 1, 1200);
        STRUCTURE_VALIDATION_INTERVAL_TICKS = builder.defineInRange("structure_validation_interval_ticks", 20, 1, 1200);
        STALE_TRACK_EXPIRATION_TICKS = builder.defineInRange("stale_track_expiration_ticks", 100, 1, 12000);
        ENTITY_QUERY_SLICE_SIZE = builder.defineInRange("entity_query_slice_size", 256, 16, 4096);
        DETECT_PASSIVE_MOBS_BY_DEFAULT = builder.define("detect_passive_mobs_by_default", true);
        builder.pop();

        builder.push("radar_link");
        RADAR_LINK_MAX_CONNECTION_DISTANCE_BLOCKS = builder.defineInRange("max_connection_distance_blocks", 128, 1, 100_000);
        RADAR_LINK_FORCELOAD_ENABLED = builder.define("force_load_enabled", true);
        RADAR_LINK_FORCELOAD_RADIUS_CHUNKS = builder.defineInRange("force_load_radius_chunks", 1, 0, 32);
        builder.pop();

        builder.push("target_controller");
        AUTOCANNON_MIN_FIRING_DISTANCE_BLOCKS =
                builder.defineInRange("autocannon_min_firing_distance_blocks", 20.0D, 0.0D, 100_000.0D);
        BIG_CANNON_MIN_FIRING_DISTANCE_BLOCKS =
                builder.defineInRange("big_cannon_min_firing_distance_blocks", 20.0D, 0.0D, 100_000.0D);
        builder.pop();

        builder.push("interception_controller");
        INTERCEPTION_SHELL_DESTRUCTION_PROBABILITY = builder
                .comment(
                        "Starting chance that a valid interception fuze detonation destroys the targeted CBC shell.",
                        "The chance increases by the same amount after every failure, up to guaranteed success.",
                        "Example: 0.1 produces 0.1, 0.2, 0.3, ... 1.0 for repeated attempts against one shell.")
                .defineInRange("shell_destruction_probability", 0.1D, 0.001D, 1.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private PowerRadarServerConfig() {
    }

    public static int maxRadarPanels() { return value(MAX_RADAR_PANELS); }
    public static int radarBaseRangeBlocks() { return value(RADAR_BASE_RANGE_BLOCKS); }
    public static int basicPanelRangeBonusBlocks() { return value(BASIC_PANEL_RANGE_BONUS_BLOCKS); }
    public static double airRangeMultiplier() { return value(AIR_RANGE_MULTIPLIER); }
    public static double airFovDegrees() { return value(AIR_FOV_DEGREES); }
    public static int groundUpBlocks() { return value(GROUND_UP_BLOCKS); }
    public static int groundDownBlocks() { return value(GROUND_DOWN_BLOCKS); }
    public static int airMinYOffset() { return value(AIR_MIN_Y_OFFSET); }
    public static int airMaxYOffset() { return Math.max(value(AIR_MIN_Y_OFFSET), value(AIR_MAX_Y_OFFSET)); }
    public static double radarControllerPowerWatts() { return value(RADAR_CONTROLLER_POWER_WATTS); }
    public static double basicRadarPanelPowerWatts() { return value(BASIC_RADAR_PANEL_POWER_WATTS); }
    public static double overviewModulePowerWatts() { return value(OVERVIEW_MODULE_POWER_WATTS); }
    public static double radarNominalVoltage() { return value(RADAR_NOMINAL_VOLTAGE); }
    public static double radarMinVoltage() { return value(RADAR_MIN_VOLTAGE); }
    public static double radarRestartVoltage() { return value(RADAR_RESTART_VOLTAGE); }
    public static double radarFullRangeVoltage() { return value(RADAR_FULL_RANGE_VOLTAGE); }
    public static double radarMaxVoltage() { return value(RADAR_MAX_VOLTAGE); }
    public static double radarOvervoltageRecovery() { return value(RADAR_OVERVOLTAGE_RECOVERY); }
    public static double monitorControllerPowerWatts() { return value(MONITOR_CONTROLLER_POWER_WATTS); }
    public static double radarDisplayPowerWatts() { return value(RADAR_DISPLAY_POWER_WATTS); }
    public static double monitorNominalVoltage() { return value(MONITOR_NOMINAL_VOLTAGE); }
    public static double monitorMinVoltage() { return value(MONITOR_MIN_VOLTAGE); }
    public static double monitorRestartVoltage() { return value(MONITOR_RESTART_VOLTAGE); }
    public static double monitorMaxVoltage() { return value(MONITOR_MAX_VOLTAGE); }
    public static double monitorOvervoltageRecovery() { return value(MONITOR_OVERVOLTAGE_RECOVERY); }
    public static int radarScanUpdateIntervalTicks() { return value(RADAR_SCAN_UPDATE_INTERVAL_TICKS); }
    public static int structureValidationIntervalTicks() { return value(STRUCTURE_VALIDATION_INTERVAL_TICKS); }
    public static int staleTrackExpirationTicks() { return value(STALE_TRACK_EXPIRATION_TICKS); }
    public static int entityQuerySliceSize() { return value(ENTITY_QUERY_SLICE_SIZE); }
    public static boolean detectPassiveMobsByDefault() { return value(DETECT_PASSIVE_MOBS_BY_DEFAULT); }
    public static int radarLinkMaxConnectionDistanceBlocks() { return value(RADAR_LINK_MAX_CONNECTION_DISTANCE_BLOCKS); }
    public static boolean radarLinkForceLoadEnabled() { return value(RADAR_LINK_FORCELOAD_ENABLED); }
    public static int radarLinkForceLoadRadiusChunks() { return value(RADAR_LINK_FORCELOAD_RADIUS_CHUNKS); }
    public static double autocannonMinFiringDistanceBlocks() { return value(AUTOCANNON_MIN_FIRING_DISTANCE_BLOCKS); }
    public static double bigCannonMinFiringDistanceBlocks() { return value(BIG_CANNON_MIN_FIRING_DISTANCE_BLOCKS); }
    public static double interceptionShellDestructionProbability() {
        return value(INTERCEPTION_SHELL_DESTRUCTION_PROBABILITY);
    }

    private static <T> T value(ModConfigSpec.ConfigValue<T> configValue) {
        return SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
    }
}
