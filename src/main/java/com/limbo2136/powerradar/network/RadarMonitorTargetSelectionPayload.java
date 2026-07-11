package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorTargetSelectionPayload(
        BlockPos monitorPos,
        @Nullable UUID targetUuid
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarMonitorTargetSelectionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_target_selection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorTargetSelectionPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorTargetSelectionPayload::write, RadarMonitorTargetSelectionPayload::new);

    public RadarMonitorTargetSelectionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), readNullableUuid(buffer));
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeBoolean(this.targetUuid != null);
        if (this.targetUuid != null) {
            buffer.writeUUID(this.targetUuid);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Nullable
    private static UUID readNullableUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }
}
