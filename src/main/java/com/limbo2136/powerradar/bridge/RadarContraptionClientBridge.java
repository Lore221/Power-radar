package com.limbo2136.powerradar.bridge;

import com.limbo2136.powerradar.network.RadarContraptionAnglePayload;
import java.util.Objects;
import java.util.function.Consumer;

/** Common/client boundary for the client-only Create contraption animation. */
public final class RadarContraptionClientBridge {
    private static Consumer<RadarContraptionAnglePayload> angleHandler = ignored -> { };

    private RadarContraptionClientBridge() {
    }

    public static void configure(Consumer<RadarContraptionAnglePayload> handler) {
        angleHandler = Objects.requireNonNull(handler);
    }

    public static void handle(RadarContraptionAnglePayload payload) {
        angleHandler.accept(payload);
    }
}
