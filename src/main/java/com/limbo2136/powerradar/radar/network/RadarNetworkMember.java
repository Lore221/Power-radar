package com.limbo2136.powerradar.radar.network;

import java.util.UUID;
import javax.annotation.Nullable;

/** Блок, который может принадлежать логической радарной сети. */
public interface RadarNetworkMember {
    @Nullable
    UUID radarNetworkId();

    void setRadarNetworkId(@Nullable UUID networkId);

    /** Только источник создаёт новую сеть при установке предмета без настройки. */
    default boolean createsRadarNetworkWhenUntuned() {
        return false;
    }
}
