package com.limbo2136.powerradar.radar;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

/** Компактный серверный снимок цели для отображения; возраст выражен в тиках. */
public record RadarDisplayTarget(
        @Nullable UUID targetUuid,
        int targetId,
        ResourceLocation entityTypeId,
        RadarTargetSourceKind sourceKind,
        @Nullable String displayName,
        RadarTargetCategory category,
        ResourceLocation dimensionId,
        double x,
        double y,
        double z,
        double velocityX,
        double velocityY,
        double velocityZ,
        boolean hasVelocity,
        float structureHeadingDegrees,
        float structureRotationPointOffsetX,
        float structureRotationPointOffsetZ,
        int silhouetteVersion,
        int displayAgeTicks
) {
    public static RadarDisplayTarget fromTrack(RadarTargetTrack target, long serverGameTime, int trackUpdateIntervalTicks) {
        long scanAgeTicks = Math.max(0L, serverGameTime - target.lastSeenGameTime());
        long freshnessGraceTicks = target.category() == RadarTargetCategory.UNKNOWN
                ? 0L
                : Math.max(1, trackUpdateIntervalTicks);
        int displayAgeTicks = (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, scanAgeTicks - freshnessGraceTicks));
        return new RadarDisplayTarget(
                target.targetUuid(),
                target.targetId(),
                target.entityTypeId(),
                target.sourceKind(),
                target.displayName(),
                target.category(),
                target.dimensionId(),
                target.x(),
                target.y(),
                target.z(),
                target.velocityX(),
                target.velocityY(),
                target.velocityZ(),
                target.hasVelocity(),
                target.structureHeadingDegrees(),
                target.structureRotationPointOffsetX(),
                target.structureRotationPointOffsetZ(),
                target.silhouetteVersion(),
                displayAgeTicks
        );
    }

    public RadarDisplayTarget withDisplayAgeTicks(int displayAgeTicks) {
        return new RadarDisplayTarget(
                this.targetUuid,
                this.targetId,
                this.entityTypeId,
                this.sourceKind,
                this.displayName,
                this.category,
                this.dimensionId,
                this.x,
                this.y,
                this.z,
                this.velocityX,
                this.velocityY,
                this.velocityZ,
                this.hasVelocity,
                this.structureHeadingDegrees,
                this.structureRotationPointOffsetX,
                this.structureRotationPointOffsetZ,
                this.silhouetteVersion,
                displayAgeTicks
        );
    }

    public RadarDisplayTarget withPosition(double x, double y, double z) {
        return new RadarDisplayTarget(
                this.targetUuid, this.targetId, this.entityTypeId, this.sourceKind, this.displayName,
                this.category, this.dimensionId, x, y, z,
                this.velocityX, this.velocityY, this.velocityZ, this.hasVelocity,
                this.structureHeadingDegrees, this.structureRotationPointOffsetX, this.structureRotationPointOffsetZ,
                this.silhouetteVersion, this.displayAgeTicks);
    }

    public RadarDisplayTarget(
            @Nullable UUID targetUuid,
            int targetId,
            ResourceLocation entityTypeId,
            RadarTargetSourceKind sourceKind,
            @Nullable String displayName,
            RadarTargetCategory category,
            ResourceLocation dimensionId,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            boolean hasVelocity,
            float structureHeadingDegrees,
            int silhouetteVersion,
            int displayAgeTicks
    ) {
        this(targetUuid, targetId, entityTypeId, sourceKind, displayName, category, dimensionId,
                x, y, z, velocityX, velocityY, velocityZ, hasVelocity, structureHeadingDegrees,
                0.0F, 0.0F, silhouetteVersion, displayAgeTicks);
    }

    public String stableSelectionKey() {
        // UUID — основная личность; составной ключ нужен лишь объектам без стабильного UUID.
        if (this.targetUuid != null) {
            return "uuid:" + this.targetUuid;
        }
        return "entity:" + this.dimensionId + ":" + this.targetId + ":" + this.entityTypeId;
    }
}
