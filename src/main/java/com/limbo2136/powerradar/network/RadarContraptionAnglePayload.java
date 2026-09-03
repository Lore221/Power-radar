package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Synchronizes the short activation tilt of a radar Create contraption. */
public record RadarContraptionAnglePayload(int entityId, float angle) implements CustomPacketPayload {
    public static final Type<RadarContraptionAnglePayload> TYPE = new Type<>(
            PowerRadar.id("radar_contraption_angle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarContraptionAnglePayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarContraptionAnglePayload::write, RadarContraptionAnglePayload::new);

    public RadarContraptionAnglePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readFloat());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.entityId);
        buffer.writeFloat(this.angle);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
