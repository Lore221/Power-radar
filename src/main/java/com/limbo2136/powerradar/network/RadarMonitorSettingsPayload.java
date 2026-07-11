package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.TargetTrajectoryMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorSettingsPayload(
        BlockPos monitorPos,
        RadarScanMode mode,
        int detectionFilterMask,
        int autotargetFilterMask,
        TargetTrajectoryMode targetTrajectoryMode
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarMonitorSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorSettingsPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorSettingsPayload::write, RadarMonitorSettingsPayload::new);

    public RadarMonitorSettingsPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), RadarScanMode.byName(buffer.readUtf()), buffer.readVarInt(),
                buffer.readVarInt(),
                TargetTrajectoryMode.byName(buffer.readUtf()));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeUtf((this.mode == null ? RadarScanMode.GROUND : this.mode).name());
        buffer.writeVarInt(this.detectionFilterMask);
        buffer.writeVarInt(this.autotargetFilterMask);
        buffer.writeUtf((this.targetTrajectoryMode == null ? TargetTrajectoryMode.FLAT : this.targetTrajectoryMode).name());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
