package com.limbo2136.powerradar.compat.aeronautics;

import com.limbo2136.powerradar.radar.RadarGeometry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

final class SableRadarWorldPose {
    private SableRadarWorldPose() {
    }

    static RadarWorldPose resolve(
            ServerLevel level,
            BlockPos controllerPos,
            Vec3 localOrigin,
            float localYawDegrees
    ) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, controllerPos);
        if (subLevel == null) {
            return new RadarWorldPose(localOrigin, localYawDegrees, false);
        }

        Vec3 worldOrigin = subLevel.logicalPose().transformPosition(localOrigin);
        double yawRadians = Math.toRadians(localYawDegrees);
        Vector3d worldForward = subLevel.logicalPose().transformNormal(new Vector3d(
                -Math.sin(yawRadians),
                0.0D,
                Math.cos(yawRadians)
        ));
        float worldYaw = RadarGeometry.normalizeDegrees((float) Math.toDegrees(
                Math.atan2(-worldForward.x(), worldForward.z())
        ));
        return new RadarWorldPose(worldOrigin, worldYaw, true);
    }

    static Vec3 worldPosition(ServerLevel level, BlockPos containingPos, Vec3 localPosition) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, containingPos);
        return subLevel == null ? localPosition : subLevel.logicalPose().transformPosition(localPosition);
    }

    static Vec3 localDirection(ServerLevel level, BlockPos containingPos, Vec3 worldDirection) {
        SubLevel subLevel = Sable.HELPER.getContaining(level, containingPos);
        return subLevel == null ? worldDirection : subLevel.logicalPose().transformNormalInverse(worldDirection);
    }
}
