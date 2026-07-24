package com.limbo2136.powerradar.radar.network;

/** Имена статусов передаются в сетевом снимке монитора. */
public enum RadarNetworkConnectionStatus {
    CONNECTED,
    NO_LINK,
    NO_RADAR,
    CONTROLLER_OFFLINE,
    OUT_OF_RANGE,
    CROSS_DIMENSION_BLOCKED,
    AMBIGUOUS_LINKS
}
