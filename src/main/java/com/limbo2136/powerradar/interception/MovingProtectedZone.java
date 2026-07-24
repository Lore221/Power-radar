package com.limbo2136.powerradar.interception;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Мировые границы защищаемой зоны и движение носителя, измеренное на серверном тике. */
public record MovingProtectedZone(
        AABB bounds,
        Vec3 velocity,
        Vec3 acceleration,
        long sampleGameTime,
        @Nullable UUID structureUuid,
        double safetyMarginPerSide
) {
    public MovingProtectedZone {
        safetyMarginPerSide = sanitizeMargin(safetyMarginPerSide);
    }

    public Vec3 referencePosition() {
        return this.bounds.getCenter();
    }

    public boolean onSable() {
        return this.structureUuid != null;
    }

    public static double safetyMarginPerSide(double totalExpansionPercent) {
        // Полный процент роста делится поровну между двумя сторонами каждой оси.
        if (!Double.isFinite(totalExpansionPercent)) {
            return 0.0D;
        }
        return Math.clamp(totalExpansionPercent, 0.0D, 100.0D) / 200.0D;
    }

    private static double sanitizeMargin(double margin) {
        return Double.isFinite(margin) ? Math.clamp(margin, 0.0D, 0.5D) : 0.0D;
    }
}
