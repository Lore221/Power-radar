package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.api.target.TargetClassification;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.api.target.TrackedTargetView;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class RadarTargetTrack implements TrackedTargetView {
    private static final double ACCELERATION_SMOOTHING = 0.35;
    private static final double MAX_TRACK_ACCELERATION_BLOCKS_PER_TICK_SQUARED = 0.25;

    private final TargetKey key;
    @Nullable
    private final UUID targetUuid;
    private final int targetId;
    private final ResourceLocation entityTypeId;
    private final RadarTargetSourceKind sourceKind;
    @Nullable
    private String displayName;
    private RadarTargetCategory category;
    private ResourceLocation dimensionId;
    private double x;
    private double y;
    private double z;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private boolean hasVelocity;
    private double accelerationX;
    private double accelerationY;
    private double accelerationZ;
    private boolean hasAcceleration;
    private final long firstSeenGameTime;
    private long lastSeenGameTime;
    private long lastConfirmedAliveGameTime;
    private double boundingHeight;
    private double approximateSize;

    public RadarTargetTrack(
            TargetKey key,
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
            double boundingHeight,
            double approximateSize,
            long gameTime
    ) {
        this.key = key;
        this.targetUuid = targetUuid;
        this.targetId = targetId;
        this.entityTypeId = entityTypeId;
        this.sourceKind = sourceKind;
        this.displayName = displayName;
        this.firstSeenGameTime = gameTime;
        update(category, displayName, dimensionId, x, y, z, velocityX, velocityY, velocityZ, hasVelocity, boundingHeight, approximateSize, gameTime);
    }

    public RadarTargetTrack copy() {
        RadarTargetTrack copy = new RadarTargetTrack(
                this.key,
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
                this.boundingHeight,
                this.approximateSize,
                this.firstSeenGameTime
        );
        copy.accelerationX = this.accelerationX;
        copy.accelerationY = this.accelerationY;
        copy.accelerationZ = this.accelerationZ;
        copy.hasAcceleration = this.hasAcceleration;
        copy.lastSeenGameTime = this.lastSeenGameTime;
        copy.lastConfirmedAliveGameTime = this.lastConfirmedAliveGameTime;
        return copy;
    }

    public void update(
            RadarTargetCategory category,
            @Nullable String displayName,
            ResourceLocation dimensionId,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            boolean hasVelocity,
            double boundingHeight,
            double approximateSize,
            long gameTime
    ) {
        this.category = category;
        this.displayName = displayName;
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        updateAcceleration(category, velocityX, velocityY, velocityZ, hasVelocity, gameTime);
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.hasVelocity = hasVelocity;
        this.boundingHeight = boundingHeight;
        this.approximateSize = approximateSize;
        this.lastSeenGameTime = gameTime;
        this.lastConfirmedAliveGameTime = gameTime;
    }

    public void confirmAlive(long gameTime) {
        this.lastConfirmedAliveGameTime = gameTime;
    }

    public TargetKey key() {
        return this.key;
    }

    @Nullable
    public UUID targetUuid() {
        return this.targetUuid;
    }

    public int targetId() {
        return this.targetId;
    }

    public ResourceLocation entityTypeId() {
        return this.entityTypeId;
    }

    public RadarTargetSourceKind sourceKind() {
        return this.sourceKind;
    }

    @Override
    public TargetSourceType sourceType() {
        return TargetSourceType.fromRadarSourceKind(this.sourceKind);
    }

    @Nullable
    public String displayName() {
        return this.displayName;
    }

    public RadarTargetCategory category() {
        return this.category;
    }

    @Override
    public TargetClassification classification() {
        return TargetClassification.fromRadarCategory(this.category);
    }

    public ResourceLocation dimensionId() {
        return this.dimensionId;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    @Override
    public Vec3 position() {
        return new Vec3(this.x, this.y, this.z);
    }

    public double velocityX() {
        return this.velocityX;
    }

    public double velocityY() {
        return this.velocityY;
    }

    public double velocityZ() {
        return this.velocityZ;
    }

    @Override
    public Vec3 velocity() {
        return new Vec3(this.velocityX, this.velocityY, this.velocityZ);
    }

    public boolean hasVelocity() {
        return this.hasVelocity;
    }

    public double accelerationX() {
        return this.accelerationX;
    }

    public double accelerationY() {
        return this.accelerationY;
    }

    public double accelerationZ() {
        return this.accelerationZ;
    }

    public boolean hasAcceleration() {
        return this.hasAcceleration;
    }

    @Override
    public Vec3 acceleration() {
        return new Vec3(this.accelerationX, this.accelerationY, this.accelerationZ);
    }

    public long firstSeenGameTime() {
        return this.firstSeenGameTime;
    }

    public long lastSeenGameTime() {
        return this.lastSeenGameTime;
    }

    public long lastConfirmedAliveGameTime() {
        return this.lastConfirmedAliveGameTime;
    }

    public double boundingHeight() {
        return this.boundingHeight;
    }

    public double approximateSize() {
        return this.approximateSize;
    }

    private void updateAcceleration(
            RadarTargetCategory category,
            double nextVelocityX,
            double nextVelocityY,
            double nextVelocityZ,
            boolean nextHasVelocity,
            long gameTime
    ) {
        if (!supportsAccelerationLead(category) || !this.hasVelocity || !nextHasVelocity || gameTime <= this.lastSeenGameTime) {
            resetAcceleration();
            return;
        }
        double elapsedTicks = Math.max(1.0, gameTime - this.lastSeenGameTime);
        double rawX = clampAcceleration((nextVelocityX - this.velocityX) / elapsedTicks);
        double rawY = clampAcceleration((nextVelocityY - this.velocityY) / elapsedTicks);
        double rawZ = clampAcceleration((nextVelocityZ - this.velocityZ) / elapsedTicks);
        if (!this.hasAcceleration) {
            this.accelerationX = rawX;
            this.accelerationY = rawY;
            this.accelerationZ = rawZ;
        } else {
            this.accelerationX = lerp(this.accelerationX, rawX, ACCELERATION_SMOOTHING);
            this.accelerationY = lerp(this.accelerationY, rawY, ACCELERATION_SMOOTHING);
            this.accelerationZ = lerp(this.accelerationZ, rawZ, ACCELERATION_SMOOTHING);
        }
        this.hasAcceleration = true;
    }

    private void resetAcceleration() {
        this.accelerationX = 0.0;
        this.accelerationY = 0.0;
        this.accelerationZ = 0.0;
        this.hasAcceleration = false;
    }

    private static boolean supportsAccelerationLead(RadarTargetCategory category) {
        return true;
    }

    private static double clampAcceleration(double value) {
        return Math.max(-MAX_TRACK_ACCELERATION_BLOCKS_PER_TICK_SQUARED,
                Math.min(MAX_TRACK_ACCELERATION_BLOCKS_PER_TICK_SQUARED, value));
    }

    private static double lerp(double from, double to, double factor) {
        return from + (to - from) * factor;
    }
}
