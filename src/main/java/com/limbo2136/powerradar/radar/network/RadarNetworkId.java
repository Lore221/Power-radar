package com.limbo2136.powerradar.radar.network;

import java.util.UUID;

public record RadarNetworkId(UUID value) {
    public static RadarNetworkId random() {
        return new RadarNetworkId(UUID.randomUUID());
    }

    public static RadarNetworkId of(UUID value) {
        return new RadarNetworkId(value);
    }

    public String shortString() {
        return this.value.toString().substring(0, 8);
    }

    @Override
    public String toString() {
        return "power_radar_network:" + this.value;
    }
}
