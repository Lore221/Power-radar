package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.TargetingCardOpenPayload;
import net.minecraft.client.Minecraft;

public final class TargetingCardClientHooks {
    private TargetingCardClientHooks() {
    }

    public static void open(TargetingCardOpenPayload payload) {
        Minecraft.getInstance().setScreen(new TargetingCardScreen(payload));
    }
}
