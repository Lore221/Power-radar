package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.block.RadarMonitorControllerBlock;
import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.radar.RadarGeometry;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class RadarMonitorViewOrientation {
    private RadarMonitorViewOrientation() {
    }

    public static float viewYawDegrees(RadarMonitorDisplayData displayData) {
        // Fixed-north использует серверный yaw вида; block-aligned привязывает верх карты к блоку.
        if (!PowerRadarClientConfig.monitorBlockAlignedView()) {
            return displayData.monitorViewYawDegrees();
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return displayData.monitorViewYawDegrees();
        }
        BlockState state = minecraft.level.getBlockState(displayData.monitorPos());
        Direction facing = state.hasProperty(RadarMonitorControllerBlock.FACING)
                ? state.getValue(RadarMonitorControllerBlock.FACING)
                : Direction.NORTH;
        return RadarGeometry.yawDegrees(facing.getOpposite());
    }

    public static float viewYawDegrees(
            RadarMonitorDisplayData displayData,
            RadarMonitorBlockPosePayload.MonitorPose monitorPose
    ) {
        // На движущемся Sable block-aligned следует интерполированной позе, а fixed-north её игнорирует.
        if (PowerRadarClientConfig.monitorBlockAlignedView() && monitorPose != null) {
            return monitorPose.yawDegrees();
        }
        return viewYawDegrees(displayData);
    }
}
