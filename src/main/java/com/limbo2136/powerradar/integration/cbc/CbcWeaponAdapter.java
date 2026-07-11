package com.limbo2136.powerradar.integration.cbc;

import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import com.limbo2136.powerradar.api.weapon.WeaponKind;
import com.limbo2136.powerradar.api.weapon.WeaponMount;
import com.limbo2136.powerradar.compat.createbigcannons.TargetControllerCbcCompat;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public final class CbcWeaponAdapter {
    private CbcWeaponAdapter() {
    }

    public static Optional<WeaponMount> inspect(ServerLevel level, BlockPos mountPos) {
        return TargetControllerCbcCompat.inspect(level, mountPos).map(CbcWeaponMount::from);
    }

    public static Optional<WeaponMount> inspectForTargeting(
            ServerLevel level,
            BlockPos mountPos,
            WeaponBallistics cachedBigCannonBallistics
    ) {
        return inspectForTargeting(level, mountPos, cachedBigCannonBallistics, null);
    }

    public static Optional<WeaponMount> inspectForTargeting(
            ServerLevel level,
            BlockPos mountPos,
            WeaponBallistics cachedBigCannonBallistics,
            WeaponKind cachedKind
    ) {
        return TargetControllerCbcCompat.inspectForTargeting(
                level,
                mountPos,
                cachedBigCannonBallistics == null ? null : mapProfile(cachedBigCannonBallistics),
                cachedKind == null ? null : mapCachedKind(cachedKind)).map(CbcWeaponMount::from);
    }

    public static Optional<WeaponMount> inspectForPreciseTargeting(
            ServerLevel level,
            BlockPos mountPos,
            WeaponBallistics cachedBallistics,
            WeaponKind cachedKind
    ) {
        return TargetControllerCbcCompat.inspectForPreciseTargeting(
                level,
                mountPos,
                cachedBallistics == null ? null : mapProfile(cachedBallistics),
                cachedKind == null ? null : mapCachedKind(cachedKind)).map(CbcWeaponMount::from);
    }

    public static boolean applyAdjustableMountAngles(
            ServerLevel level,
            WeaponMount mount,
            float yawDegrees,
            float logicalPitchDegrees
    ) {
        return TargetControllerCbcCompat.applyAdjustableMountAngles(
                level, mount.mountPos(), yawDegrees, logicalPitchDegrees, mapCachedKind(mount.kind()));
    }

    private static WeaponKind mapKind(TargetControllerCbcCompat.CannonKind kind) {
        return switch (kind) {
            case NONE -> WeaponKind.NONE;
            case BIG_CANNON -> WeaponKind.BIG_CANNON;
            case DROP_MORTAR -> WeaponKind.DROP_MORTAR;
            case AUTOCANNON -> WeaponKind.AUTOCANNON;
            case UNKNOWN_CBC -> WeaponKind.UNKNOWN;
        };
    }

    private static TargetControllerCbcCompat.CannonKind mapCachedKind(WeaponKind kind) {
        return switch (kind) {
            case NONE -> TargetControllerCbcCompat.CannonKind.NONE;
            case BIG_CANNON -> TargetControllerCbcCompat.CannonKind.BIG_CANNON;
            case DROP_MORTAR -> TargetControllerCbcCompat.CannonKind.DROP_MORTAR;
            case AUTOCANNON -> TargetControllerCbcCompat.CannonKind.AUTOCANNON;
            case UNKNOWN -> TargetControllerCbcCompat.CannonKind.UNKNOWN_CBC;
        };
    }

    private static WeaponBallistics mapBallistics(TargetControllerCbcCompat.BallisticProfile profile) {
        return new WeaponBallistics(
                profile.available(),
                profile.mode(),
                profile.speedBlocksPerTick(),
                profile.gravityBlocksPerTickSquared(),
                profile.drag(),
                profile.quadraticDrag(),
                profile.lifetimeTicks(),
                profile.barrelCount(),
                profile.ammunition(),
                profile.highArcEnabled());
    }

    private static TargetControllerCbcCompat.BallisticProfile mapProfile(WeaponBallistics ballistics) {
        return new TargetControllerCbcCompat.BallisticProfile(
                ballistics.available(),
                ballistics.mode(),
                ballistics.speedBlocksPerTick(),
                ballistics.gravityBlocksPerTickSquared(),
                ballistics.drag(),
                ballistics.quadraticDrag(),
                ballistics.lifetimeTicks(),
                ballistics.barrelCount(),
                ballistics.ammunition(),
                ballistics.highArcEnabled());
    }

    private record CbcWeaponMount(
            BlockPos mountPos,
            Vec3 muzzleOrigin,
            float currentYawDegrees,
            float currentPitchDegrees,
            float physicalPitchDegrees,
            float worldToLogicalPitchMultiplier,
            WeaponKind kind,
            boolean fireCapable,
            WeaponBallistics ballistics
    ) implements WeaponMount {
        private static CbcWeaponMount from(TargetControllerCbcCompat.CannonState state) {
            return new CbcWeaponMount(
                    state.mountPos(),
                    state.muzzleOrigin(),
                    state.currentYawDegrees(),
                    state.currentPitchDegrees(),
                    state.physicalPitchDegrees(),
                    state.worldToLogicalPitchMultiplier(),
                    mapKind(state.kind()),
                    state.fireCapableContraptionPresent(),
                    mapBallistics(state.ballistics()));
        }
    }
}
