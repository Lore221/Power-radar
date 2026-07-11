package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorBlockSnapshotPayload(RadarMonitorSnapshotPayload snapshot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarMonitorBlockSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_block_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorBlockSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorBlockSnapshotPayload::write, RadarMonitorBlockSnapshotPayload::new);

    public RadarMonitorBlockSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(new RadarMonitorSnapshotPayload(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        this.snapshot.write(buffer);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
