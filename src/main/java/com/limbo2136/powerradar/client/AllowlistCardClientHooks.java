package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.AllowlistCardOpenPayload;
import net.minecraft.client.Minecraft;

public final class AllowlistCardClientHooks {
    private AllowlistCardClientHooks() {
    }

    public static void open(AllowlistCardOpenPayload payload) {
        Minecraft.getInstance().setScreen(new AllowlistCardScreen(payload));
    }
}
