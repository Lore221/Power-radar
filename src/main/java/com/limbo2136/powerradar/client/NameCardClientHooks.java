package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.NameCardOpenPayload;
import net.minecraft.client.Minecraft;

public final class NameCardClientHooks {
    private NameCardClientHooks() { }
    public static void open(NameCardOpenPayload payload) {
        Minecraft.getInstance().setScreen(new NameCardScreen(payload));
    }
}
