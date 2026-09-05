package com.limbo2136.powerradar.targeting;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Removes the small downward velocity left by ground collision resolution.
 * Real airborne motion, including gliding and falling, is preserved.
 */
public final class TargetVelocityNormalizer {
    private static final double GROUND_VERTICAL_VELOCITY_EPSILON = 0.1D;

    private TargetVelocityNormalizer() {
    }

    public static Vec3 normalize(Entity entity) {
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity == null) {
            return Vec3.ZERO;
        }
        if (entity.onGround()
                && velocity.y < 0.0D
                && Math.abs(velocity.y) <= GROUND_VERTICAL_VELOCITY_EPSILON) {
            return new Vec3(velocity.x, 0.0D, velocity.z);
        }
        return velocity;
    }
}
