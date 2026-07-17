package com.limbo2136.powerradar;

public final class RadarConstants {
    public static final int SECTOR_RADAR_GROUND_ANGLE_DEGREES = 90;
    public static final int SECTOR_RADAR_ENTITY_QUERY_SLICE_SIZE = 256;

    public static final int RADAR_SCAN_UPDATE_INTERVAL_TICKS = 5;
    public static final int SECTOR_RADAR_STRUCTURE_VALIDATION_INTERVAL_TICKS = 20;
    public static final boolean SECTOR_RADAR_DETECT_PASSIVE_MOBS = true;

    public static final int SECTOR_RADAR_STALE_TRACK_EXPIRATION_TICKS = 100;
    public static final int RADAR_BLIP_FADE_DELAY_TICKS = 21;
    public static final int RADAR_MONITOR_BLIP_FADE_DELAY_TICKS = 20;
    public static final int RADAR_MONITOR_BLIP_FADE_TICKS = 40;

    public static final int RADAR_MONITOR_UPDATE_INTERVAL_TICKS = 5;
    public static final int RADAR_MONITOR_BLOCK_UPDATE_INTERVAL_TICKS = 5;
    public static final int RADAR_MONITOR_STRUCTURE_RECONCILE_INTERVAL_TICKS = 100;
    public static final int RADAR_MONITOR_BLOCK_SYNC_RANGE_BLOCKS = 64;
    public static final int RADAR_LINK_MAX_CONNECTION_DISTANCE_BLOCKS = 128;
    public static final int RADAR_LINK_FORCELOAD_RADIUS_CHUNKS = 1;
    public static final int RADAR_LINK_FORCELOAD_RECONCILE_INTERVAL_TICKS = 100;
    public static final boolean RADAR_LINK_FORCELOAD_ENABLED = true;
    public static final int RADAR_LINK_OUTLINE_RANGE_BLOCKS = 64;
    public static final int RADAR_LINK_OUTLINE_PULSE_PERIOD_TICKS = 16;
    public static final int RADAR_LINK_OUTLINE_PULSE_HALF_PERIOD_TICKS = 8;
    public static final int RADAR_LINK_OUTLINE_COLOR_A = 0x58BDEB;
    public static final int RADAR_LINK_OUTLINE_COLOR_B = 0x8ADFFF;
    public static final int INTERCEPTION_NETWORK_OUTLINE_COLOR_A = 0xF28C28;
    public static final int INTERCEPTION_NETWORK_OUTLINE_COLOR_B = 0xFFB347;
    public static final int RADAR_DISPLAY_LINK_REFRESH_INTERVAL_TICKS = 5;

    public static final float RADAR_DISPLAY_CONTENT_SCALE = 0.88F;
    public static final float RADAR_BLIP_RENDER_SCALE = 0.5F;
    public static final float RADAR_BLIP_CELL_TEXTURE_RATIO = 11.0F / 128.0F * RADAR_BLIP_RENDER_SCALE;

    private RadarConstants() {
    }

    public static float computeInWorldBlipSize(float displaySurfaceSize) {
        return displaySurfaceSize * RADAR_BLIP_CELL_TEXTURE_RATIO;
    }

    public static int sectorRadarGroundAngleDegrees() {
        return SECTOR_RADAR_GROUND_ANGLE_DEGREES;
    }

    public static int entityQuerySliceSize() {
        return PowerRadarServerConfig.entityQuerySliceSize();
    }

    public static int radarScanUpdateIntervalTicks() {
        return PowerRadarServerConfig.radarScanUpdateIntervalTicks();
    }

    public static int structureValidationIntervalTicks() {
        return PowerRadarServerConfig.structureValidationIntervalTicks();
    }

    public static boolean detectPassiveMobsByDefault() {
        return PowerRadarServerConfig.detectPassiveMobsByDefault();
    }

    public static int staleTrackExpirationTicks() {
        return PowerRadarServerConfig.staleTrackExpirationTicks();
    }

    public static int radarLinkMaxConnectionDistanceBlocks() {
        return PowerRadarServerConfig.radarLinkMaxConnectionDistanceBlocks();
    }

    public static boolean radarLinkForceLoadEnabled() {
        return PowerRadarServerConfig.radarLinkForceLoadEnabled();
    }

    public static int radarLinkForceLoadRadiusChunks() {
        return PowerRadarServerConfig.radarLinkForceLoadRadiusChunks();
    }
}
