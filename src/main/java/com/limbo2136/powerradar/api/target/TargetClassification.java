package com.limbo2136.powerradar.api.target;

import com.limbo2136.powerradar.radar.RadarTargetCategory;

/** Потребительская классификация цели без зависимости от конкретной реализации трекера. */
public enum TargetClassification {
    PLAYER,
    PASSIVE_MOB,
    HOSTILE_MOB,
    PROJECTILE,
    STRUCTURE,
    UNKNOWN;

    public static TargetClassification fromRadarCategory(RadarTargetCategory category) {
        return switch (category) {
            case PLAYER -> PLAYER;
            case PASSIVE_MOB -> PASSIVE_MOB;
            case HOSTILE_MOB -> HOSTILE_MOB;
            case PROJECTILE -> PROJECTILE;
            case SABLE_STRUCTURE -> STRUCTURE;
            case UNKNOWN -> UNKNOWN;
        };
    }
}
