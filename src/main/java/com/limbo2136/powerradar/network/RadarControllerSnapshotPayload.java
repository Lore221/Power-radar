package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.TargetTrajectoryMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarControllerSnapshotPayload(
        BlockPos controllerPos,
        RadarScanMode mode,
        int detectionFilterMask,
        boolean assembled,
        PowerRadarCeeState electricalState,
        int validPanelCount,
        int basicPanelCount,
        int currentRange,
        int maxRange,
        TargetTrajectoryMode targetTrajectoryMode
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RadarControllerSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_controller_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarControllerSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarControllerSnapshotPayload::write, RadarControllerSnapshotPayload::new);

    public RadarControllerSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readBlockPos(),
                RadarScanMode.byName(buffer.readUtf()),
                buffer.readVarInt(),
                buffer.readBoolean(),
                PowerRadarCeeState.valueOf(buffer.readUtf()),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                TargetTrajectoryMode.byName(buffer.readUtf())
        );
    }

    public static RadarControllerSnapshotPayload fromController(RadarControllerBlockEntity controller) {
        return new RadarControllerSnapshotPayload(
                controller.getBlockPos(),
                controller.scanMode(),
                controller.detectionFilterMask(),
                controller.assembled(),
                controller.electricalState(),
                controller.validPanelCount(),
                controller.basicPanelCount(),
                controller.displayCurrentRange(),
                controller.maxRange(),
                controller.targetTrajectoryMode()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.controllerPos);
        buffer.writeUtf((this.mode == null ? RadarScanMode.GROUND : this.mode).name());
        buffer.writeVarInt(this.detectionFilterMask);
        buffer.writeBoolean(this.assembled);
        buffer.writeUtf((this.electricalState == null ? PowerRadarCeeState.INVALID_STRUCTURE : this.electricalState).name());
        buffer.writeVarInt(this.validPanelCount);
        buffer.writeVarInt(this.basicPanelCount);
        buffer.writeVarInt(this.currentRange);
        buffer.writeVarInt(this.maxRange);
        buffer.writeUtf((this.targetTrajectoryMode == null ? TargetTrajectoryMode.FLAT : this.targetTrajectoryMode).name());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
