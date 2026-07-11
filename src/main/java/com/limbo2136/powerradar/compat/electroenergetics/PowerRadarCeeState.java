package com.limbo2136.powerradar.compat.electroenergetics;

public enum PowerRadarCeeState {
    INVALID_STRUCTURE("power_radar.electrical.state.invalid_structure"),
    UNDERVOLTAGE("power_radar.electrical.state.undervoltage"),
    POWERED("power_radar.electrical.state.powered"),
    OVERVOLTAGE("power_radar.electrical.state.overvoltage_shutdown"),
    REVERSE_POLARITY("power_radar.electrical.state.reverse_polarity");

    private final String translationKey;

    PowerRadarCeeState(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }
}
