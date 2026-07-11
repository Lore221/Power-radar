package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorWhitelistPayload(
        BlockPos monitorPos,
        Action action,
        String value
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarMonitorWhitelistPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_whitelist"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorWhitelistPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorWhitelistPayload::write, RadarMonitorWhitelistPayload::new);

    public RadarMonitorWhitelistPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), Action.valueOf(buffer.readUtf()), buffer.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeUtf(this.action.name());
        buffer.writeUtf(this.value == null ? "" : this.value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        ADD_PLAYER,
        REMOVE_PLAYER
    }
}
