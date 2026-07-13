package com.limbo2136.powerradar.radar;

public final class RadarDetectionFilters {
    public static final int HOSTILE_MOBS = 1;
    public static final int PASSIVE_MOBS = 1 << 1;
    public static final int PLAYERS = 1 << 2;
    public static final int SABLE_STRUCTURES = 1 << 3;
    public static final int PROJECTILES = 1 << 4;
    /** The targeting card reuses its fourth stored bit for phantoms; display cards use it for projectiles. */
    public static final int TARGETING_PHANTOMS = PROJECTILES;
    public static final int DEFAULT_MASK = HOSTILE_MOBS | PASSIVE_MOBS | PLAYERS | SABLE_STRUCTURES | PROJECTILES;

    private RadarDetectionFilters() {
    }

    public static int sanitize(int mask) {
        return mask & DEFAULT_MASK;
    }

    public static boolean enabled(int mask, RadarTargetCategory category) {
        return switch (category) {
            case HOSTILE_MOB -> (mask & HOSTILE_MOBS) != 0;
            case PASSIVE_MOB -> (mask & PASSIVE_MOBS) != 0;
            case PLAYER -> (mask & PLAYERS) != 0;
            case SABLE_STRUCTURE -> (mask & SABLE_STRUCTURES) != 0;
            case PROJECTILE -> (mask & PROJECTILES) != 0;
            case UNKNOWN -> true;
        };
    }

    public static int bit(RadarTargetCategory category) {
        return switch (category) {
            case HOSTILE_MOB -> HOSTILE_MOBS;
            case PASSIVE_MOB -> PASSIVE_MOBS;
            case PLAYER -> PLAYERS;
            case SABLE_STRUCTURE -> SABLE_STRUCTURES;
            case PROJECTILE -> PROJECTILES;
            case UNKNOWN -> 0;
        };
    }
}
