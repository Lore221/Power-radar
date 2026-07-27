package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPose;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPoseResolver;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarStructureType;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Формирует одинаковую динамическую позу для блочного и панельного мониторов. */
public final class RadarMonitorPosePayloadFactory {
    private RadarMonitorPosePayloadFactory() {
    }

    @Nullable
    public static RadarMonitorBlockPosePayload create(
            ServerLevel level,
            BlockPos monitorPos,
            Direction monitorFacing,
            List<RadarControllerBlockEntity> controllers
    ) {
        long gameTime = level.getGameTime();
        RadarWorldPose monitorWorldPose = RadarWorldPoseResolver.resolve(
                level,
                monitorPos,
                Vec3.atCenterOf(monitorPos),
                RadarGeometry.yawDegrees(monitorFacing.getOpposite()));
        RadarMonitorBlockPosePayload.MonitorPose monitorPose = monitorWorldPose.onSableStructure()
                ? new RadarMonitorBlockPosePayload.MonitorPose(
                        monitorWorldPose.origin().x,
                        monitorWorldPose.origin().y,
                        monitorWorldPose.origin().z,
                        monitorWorldPose.yawDegrees())
                : null;

        ArrayList<RadarMonitorBlockPosePayload.RadarPose> poses = new ArrayList<>();
        for (RadarControllerBlockEntity controller : controllers) {
            RadarWorldPose pose = controller.worldPoseAt(gameTime);
            if (!pose.onSableStructure()) {
                continue;
            }
            poses.add(new RadarMonitorBlockPosePayload.RadarPose(
                    controller.radarId(),
                    pose.origin().x,
                    pose.origin().y,
                    pose.origin().z,
                    controller.orientationState().structureType() == RadarStructureType.OVERVIEW
                            ? 0.0F
                            : pose.yawDegrees()));
        }
        if (monitorPose == null && poses.isEmpty()) {
            return null;
        }
        return new RadarMonitorBlockPosePayload(
                monitorPos, gameTime, monitorPose, List.copyOf(poses));
    }
}
