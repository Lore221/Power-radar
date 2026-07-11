package com.limbo2136.powerradar.api.target;

import com.limbo2136.powerradar.radar.RadarTargetSourceKind;

public enum TargetSourceType {
    ENTITY,
    PROJECTILE,
    CBC_BIG_CANNON_PROJECTILE,
    CBC_AUTOCANNON_PROJECTILE,
    STRUCTURE;

    public static TargetSourceType fromRadarSourceKind(RadarTargetSourceKind sourceKind) {
        return switch (sourceKind) {
            case ENTITY -> ENTITY;
            case PROJECTILE -> PROJECTILE;
            case CBC_BIG_CANNON_PROJECTILE -> CBC_BIG_CANNON_PROJECTILE;
            case CBC_AUTOCANNON_PROJECTILE -> CBC_AUTOCANNON_PROJECTILE;
            case FUTURE_SABLE_STRUCTURE -> STRUCTURE;
        };
    }
}
