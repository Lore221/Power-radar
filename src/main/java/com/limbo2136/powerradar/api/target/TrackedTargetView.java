package com.limbo2136.powerradar.api.target;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

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
}
