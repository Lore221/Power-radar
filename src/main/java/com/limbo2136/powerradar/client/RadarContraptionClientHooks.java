package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.RadarContraptionAnglePayload;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class RadarContraptionClientHooks {
    private RadarContraptionClientHooks() {
    }

    public static void handleAngle(RadarContraptionAnglePayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null
                && minecraft.level.getEntity(payload.entityId()) instanceof ControlledContraptionEntity contraption) {
            contraption.setAngle(payload.angle());
        }
    }
}
