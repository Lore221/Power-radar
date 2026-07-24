package com.limbo2136.powerradar.api.weapon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Серверный контракт установленного оружия. Точка дула дана в текущем пространстве наведения,
 * углы — в градусах; адаптер отвечает за согласованность этого пространства с командами привода.
 */
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
