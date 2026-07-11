package com.limbo2136.powerradar.api.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public interface WeaponMount {
    BlockPos mountPos();

    Vec3 muzzleOrigin();

    float currentYawDegrees();

    float currentPitchDegrees();

    float physicalPitchDegrees();

    default float worldToLogicalPitchMultiplier() {
        return 1.0F;
    }

    WeaponKind kind();

    boolean fireCapable();

    WeaponBallistics ballistics();
}
