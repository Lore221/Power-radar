package com.limbo2136.powerradar.radar;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

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
        int silhouetteVersion,
        int displayAgeTicks
) {
    public static RadarDisplayTarget fromTrack(RadarTargetTrack target, long serverGameTime, int trackUpdateIntervalTicks) {
        long scanAgeTicks = Math.max(0L, serverGameTime - target.lastSeenGameTime());
        int displayAgeTicks = (int) Math.max(0L, scanAgeTicks - Math.max(1, trackUpdateIntervalTicks));
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
                this.silhouetteVersion,
                displayAgeTicks
        );
    }

    public RadarDisplayTarget withPosition(double x, double y, double z) {
        return new RadarDisplayTarget(
                this.targetUuid, this.targetId, this.entityTypeId, this.sourceKind, this.displayName,
                this.category, this.dimensionId, x, y, z,
                this.velocityX, this.velocityY, this.velocityZ, this.hasVelocity,
                this.structureHeadingDegrees, this.silhouetteVersion, this.displayAgeTicks);
    }

    public String stableSelectionKey() {
        if (this.targetUuid != null) {
            return "uuid:" + this.targetUuid;
        }
        return "entity:" + this.dimensionId + ":" + this.targetId + ":" + this.entityTypeId;
    }
}
