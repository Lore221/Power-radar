package com.limbo2136.powerradar.compat.aeronautics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class RadarWorldPoseResolver {
    private static final boolean SABLE_LOADED = ModList.get().isLoaded("sable");

    private RadarWorldPoseResolver() {
    }

    public static RadarWorldPose resolve(
            ServerLevel level,
            BlockPos controllerPos,
            Vec3 localOrigin,
            float localYawDegrees
    ) {
        if (!SABLE_LOADED) {
            return new RadarWorldPose(localOrigin, localYawDegrees, false);
        }
        return SableRadarWorldPose.resolve(level, controllerPos, localOrigin, localYawDegrees);
    }

    public static Vec3 worldPosition(ServerLevel level, BlockPos localPos) {
        return resolve(level, localPos, Vec3.atCenterOf(localPos), 0.0F).origin();
    }

    public static Vec3 worldPosition(ServerLevel level, BlockPos containingPos, Vec3 localPosition) {
        if (!SABLE_LOADED) {
            return localPosition;
        }
        return SableRadarWorldPose.worldPosition(level, containingPos, localPosition);
    }

    public static Vec3 localDirection(ServerLevel level, BlockPos containingPos, Vec3 worldDirection) {
        if (!SABLE_LOADED) {
            return worldDirection;
        }
        return SableRadarWorldPose.localDirection(level, containingPos, worldDirection);
    }

    public static boolean isOnSableStructure(ServerLevel level, BlockPos localPos) {
        return resolve(level, localPos, Vec3.atCenterOf(localPos), 0.0F).onSableStructure();
    }
}
