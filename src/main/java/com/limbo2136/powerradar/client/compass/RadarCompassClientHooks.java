package com.limbo2136.powerradar.client.compass;

import com.limbo2136.powerradar.network.RadarCompassTargetPayload;

public final class RadarCompassClientHooks {
    private RadarCompassClientHooks() {
    }

    public static void handleTarget(RadarCompassTargetPayload payload) {
        RadarCompassClientCache.apply(payload);
    }
}
