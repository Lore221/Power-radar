package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorBlockTargetsPayload(
        BlockPos monitorPos,
        long revision,
        long lastScanGameTime,
        long serverGameTime,
        List<RadarDisplayTarget> targets
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarMonitorBlockTargetsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_block_targets"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorBlockTargetsPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorBlockTargetsPayload::write, RadarMonitorBlockTargetsPayload::new);

    public RadarMonitorBlockTargetsPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readBlockPos(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong(),
                RadarMonitorSnapshotPayload.readTargets(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeLong(this.revision);
        buffer.writeLong(this.lastScanGameTime);
        buffer.writeLong(this.serverGameTime);
        buffer.writeVarInt(this.targets.size());
        for (RadarDisplayTarget target : this.targets) {
            RadarMonitorSnapshotPayload.writeTarget(buffer, target);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
