package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorRequestPayload(BlockPos monitorPos, long knownRevision) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarMonitorRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorRequestPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorRequestPayload::write, RadarMonitorRequestPayload::new);

    public RadarMonitorRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeLong(this.knownRevision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
