package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorSilhouetteRequestPayload(
        BlockPos monitorPos,
        UUID structureUuid,
        int knownVersion
) implements CustomPacketPayload {
    public static final Type<RadarMonitorSilhouetteRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_silhouette_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorSilhouetteRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorSilhouetteRequestPayload::write, RadarMonitorSilhouetteRequestPayload::new);

    public RadarMonitorSilhouetteRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeUUID(this.structureUuid);
        buffer.writeVarInt(this.knownVersion);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
