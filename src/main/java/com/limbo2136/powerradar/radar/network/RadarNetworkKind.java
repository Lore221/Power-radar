package com.limbo2136.powerradar.radar.network;

/**
 * Класс сигнала, который распространяет источник сети.
 *
 * <p>Воздушный радар использует отдельный канал: источники разных классов нельзя
 * случайно объединить одной настройкой сети, но обычные потребители по-прежнему
 * могут подключаться к обоим классам.</p>
 */
public enum RadarNetworkKind {
    STANDARD,
    AIRCRAFT;

    /** Бортовой радар работает как отдельный источник и не образует кластер. */
    public boolean isSingleSource() {
        return this == AIRCRAFT;
    }

    public static RadarNetworkKind byName(String name) {
        for (RadarNetworkKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return STANDARD;
    }
}
