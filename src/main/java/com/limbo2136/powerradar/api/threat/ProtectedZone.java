package com.limbo2136.powerradar.api.threat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Неподвижная квадратная зона защиты; линейные размеры заданы в блоках. */
public record ProtectedZone(BlockPos origin, double sideBlocks, double verticalMarginBlocks) {
    public AABB horizontalBounds() {
        Vec3 center = Vec3.atCenterOf(this.origin);
        double halfSide = this.sideBlocks * 0.5;
        return new AABB(
                center.x - halfSide,
                0.0,
                center.z - halfSide,
                center.x + halfSide,
                1.0,
                center.z + halfSide);
    }

    public double lowerPlaneY() {
        return Vec3.atCenterOf(this.origin).y - this.verticalMarginBlocks;
    }

    public double upperPlaneY() {
        return Vec3.atCenterOf(this.origin).y + this.verticalMarginBlocks;
    }
}
