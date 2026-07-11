package com.limbo2136.powerradar.radar;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public record TargetKey(ResourceLocation dimensionId, @Nullable UUID entityUuid, int fallbackEntityId) {
    public static TargetKey entity(ResourceLocation dimensionId, @Nullable UUID entityUuid, int entityId) {
        return new TargetKey(dimensionId, entityUuid, entityId);
    }

    @Override
    public String toString() {
        return this.dimensionId + ":" + (this.entityUuid == null ? Integer.toString(this.fallbackEntityId) : this.entityUuid.toString());
    }
}
