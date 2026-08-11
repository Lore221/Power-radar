package com.limbo2136.powerradar.api.target;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Неизменяемое представление измеренного трека, а не ссылка на живую сущность.
 * Положение измеряется в блоках, скорость — в блоках/тик, ускорение — в блоках/тик².
 */
public interface TrackedTargetView {
    @Nullable
    UUID targetUuid();

    int targetId();

    ResourceLocation entityTypeId();

    TargetSourceType sourceType();

    @Nullable
    String displayName();

    TargetClassification classification();

    ResourceLocation dimensionId();

    Vec3 position();

    Vec3 velocity();

    boolean hasVelocity();

    Vec3 acceleration();

    boolean hasAcceleration();

    long firstSeenGameTime();

    long lastSeenGameTime();

    long lastConfirmedAliveGameTime();

    double boundingHeight();

    double approximateSize();

    /** Текущий мировой хитбокс; снимки без точной геометрии используют измеренные габариты. */
    default AABB targetBounds() {
        Vec3 base = position();
        double width = Math.max(0.1D, approximateSize());
        double halfWidth = width * 0.5D;
        return new AABB(
                base.x - halfWidth,
                base.y,
                base.z - halfWidth,
                base.x + halfWidth,
                base.y + Math.max(0.1D, boundingHeight()),
                base.z + halfWidth);
    }
}
