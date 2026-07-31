package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Короткий keepalive подписки: один UUID сети вместо запроса от каждого компаса. */
public record RadarCompassSubscriptionPayload(UUID networkId) implements CustomPacketPayload {
    public static final Type<RadarCompassSubscriptionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_compass_subscription"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarCompassSubscriptionPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarCompassSubscriptionPayload::write, RadarCompassSubscriptionPayload::new);

    public RadarCompassSubscriptionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.networkId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
