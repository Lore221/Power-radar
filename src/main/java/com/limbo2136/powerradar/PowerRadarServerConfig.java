package com.limbo2136.powerradar;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.limbo2136.powerradar.radar.PowerRadarRadarParameters;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class PowerRadarServerConfig {
    public static final ModConfigSpec SPEC;

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
    private static final ModConfigSpec.DoubleValue MANUAL_INTERCEPTION_FUZE_DISTANCE_BLOCKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        PowerRadarRadarParameters.defineConfig(builder);
        PowerRadarElectricalParameters.defineConfig(builder);

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
        MANUAL_INTERCEPTION_FUZE_DISTANCE_BLOCKS = builder
                .comment(
                        "Distance travelled before an unassigned interception fuze performs a manual airburst.",
                        "Automatically assigned interceptor shells continue to detonate from their tracked target.")
                .defineInRange("manual_fuze_distance_blocks", 50.0D, 1.0D, 100_000.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private PowerRadarServerConfig() {
    }

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
    public static double manualInterceptionFuzeDistanceBlocks() {
        return value(MANUAL_INTERCEPTION_FUZE_DISTANCE_BLOCKS);
    }

    private static <T> T value(ModConfigSpec.ConfigValue<T> configValue) {
        return SPEC.isLoaded() ? configValue.get() : configValue.getDefault();
    }
}
