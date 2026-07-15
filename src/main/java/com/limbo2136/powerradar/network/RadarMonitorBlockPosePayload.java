package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.radar.RadarId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorBlockPosePayload(
        BlockPos monitorPos,
        long serverGameTime,
        MonitorPose monitorPose,
        List<RadarPose> poses
) implements CustomPacketPayload {
    public static final Type<RadarMonitorBlockPosePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_block_pose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorBlockPosePayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorBlockPosePayload::write, RadarMonitorBlockPosePayload::new);

    public RadarMonitorBlockPosePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBlockPos(), buffer.readLong(), readMonitorPose(buffer), readPoses(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeLong(this.serverGameTime);
        buffer.writeBoolean(this.monitorPose != null);
        if (this.monitorPose != null) {
            buffer.writeDouble(this.monitorPose.originX);
            buffer.writeDouble(this.monitorPose.originY);
            buffer.writeDouble(this.monitorPose.originZ);
            buffer.writeFloat(this.monitorPose.yawDegrees);
        }
        buffer.writeVarInt(this.poses.size());
        for (RadarPose pose : this.poses) {
            buffer.writeResourceLocation(pose.radarId.dimensionId());
            buffer.writeBlockPos(pose.radarId.controllerPos());
            buffer.writeDouble(pose.originX);
            buffer.writeDouble(pose.originY);
            buffer.writeDouble(pose.originZ);
            buffer.writeFloat(pose.yawDegrees);
        }
    }

    private static MonitorPose readMonitorPose(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean()
                ? new MonitorPose(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat())
                : null;
    }

    private static List<RadarPose> readPoses(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<RadarPose> poses = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            RadarId radarId = new RadarId(buffer.readResourceLocation(), buffer.readBlockPos());
            poses.add(new RadarPose(
                    radarId,
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat()));
        }
        return List.copyOf(poses);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record RadarPose(RadarId radarId, double originX, double originY, double originZ, float yawDegrees) {
    }

    public record MonitorPose(double originX, double originY, double originZ, float yawDegrees) {
    }
}
