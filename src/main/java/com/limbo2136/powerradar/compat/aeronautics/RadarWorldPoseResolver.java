package com.limbo2136.powerradar.compat.aeronautics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Выбирает обычные мировые координаты или Sable-преобразование без утечки Sable-типов наружу. */
public final class RadarWorldPoseResolver {
    private RadarWorldPoseResolver() {
    }

    public static RadarWorldPose resolve(
            ServerLevel level,
            BlockPos controllerPos,
            Vec3 localOrigin,
            float localYawDegrees
    ) {
        if (!SableRadarIntegration.isSableLoaded()) {
            return new RadarWorldPose(localOrigin, localYawDegrees, false);
        }
        return SableRadarWorldPose.resolve(level, controllerPos, localOrigin, localYawDegrees);
    }

    public static Vec3 worldPosition(ServerLevel level, BlockPos localPos) {
        return resolve(level, localPos, Vec3.atCenterOf(localPos), 0.0F).origin();
    }

    public static Vec3 worldPosition(ServerLevel level, BlockPos containingPos, Vec3 localPosition) {
        if (!SableRadarIntegration.isSableLoaded()) {
            return localPosition;
        }
        return SableRadarWorldPose.worldPosition(level, containingPos, localPosition);
    }

    public static Vec3 localDirection(ServerLevel level, BlockPos containingPos, Vec3 worldDirection) {
        if (!SableRadarIntegration.isSableLoaded()) {
            return worldDirection;
        }
        return SableRadarWorldPose.localDirection(level, containingPos, worldDirection);
    }

    public static Vec3 worldDirection(ServerLevel level, BlockPos containingPos, Vec3 localDirection) {
        if (!SableRadarIntegration.isSableLoaded()) {
            return localDirection;
        }
        return SableRadarWorldPose.worldDirection(level, containingPos, localDirection);
    }

    /** Возвращает скорость указанной точки конструкции в мировых блоках за серверный тик. */
    public static Vec3 worldPointVelocity(ServerLevel level, BlockPos containingPos, Vec3 localPosition) {
        if (!SableRadarIntegration.isSableLoaded()) {
            return Vec3.ZERO;
        }
        return SableRadarWorldPose.worldPointVelocity(level, containingPos, localPosition);
    }

    public static boolean isOnSableStructure(ServerLevel level, BlockPos localPos) {
        return resolve(level, localPos, Vec3.atCenterOf(localPos), 0.0F).onSableStructure();
    }
}
