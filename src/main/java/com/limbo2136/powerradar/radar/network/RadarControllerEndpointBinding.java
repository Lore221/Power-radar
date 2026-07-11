package com.limbo2136.powerradar.radar.network;

import net.minecraft.core.GlobalPos;

public record RadarControllerEndpointBinding(GlobalPos radarLinkPos, GlobalPos controllerPos) {
}
