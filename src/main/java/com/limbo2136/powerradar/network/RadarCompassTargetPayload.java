package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record RadarCompassTargetPayload(
        UUID networkId,
        long revision,
        @Nullable UUID targetUuid,
        boolean confirmedByLatestScan,
        boolean alive,
        @Nullable ResourceLocation dimensionId,
        @Nullable Vec3 position,
        Vec3 velocity,
        long serverGameTime
) implements CustomPacketPayload {
    public static final Type<RadarCompassTargetPayload> TYPE = new Type<>(
            PowerRadar.id("radar_compass_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarCompassTargetPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> payload.write(buffer),
                    RadarCompassTargetPayload::read);

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(this.networkId);
        buffer.writeVarLong(this.revision);
        buffer.writeBoolean(this.targetUuid != null);
        if (this.targetUuid != null) {
            buffer.writeUUID(this.targetUuid);
        }
        buffer.writeBoolean(this.confirmedByLatestScan);
        buffer.writeBoolean(this.alive);
        boolean hasLiveTarget = this.alive && this.dimensionId != null && this.position != null;
        buffer.writeBoolean(hasLiveTarget);
        if (hasLiveTarget) {
            buffer.writeResourceLocation(this.dimensionId);
            buffer.writeDouble(this.position.x);
            buffer.writeDouble(this.position.y);
            buffer.writeDouble(this.position.z);
            buffer.writeDouble(this.velocity.x);
            buffer.writeDouble(this.velocity.y);
            buffer.writeDouble(this.velocity.z);
        }
        buffer.writeVarLong(this.serverGameTime);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static RadarCompassTargetPayload read(RegistryFriendlyByteBuf buffer) {
        UUID networkId = buffer.readUUID();
        long revision = buffer.readVarLong();
        UUID targetUuid = buffer.readBoolean() ? buffer.readUUID() : null;
        boolean confirmedByLatestScan = buffer.readBoolean();
        boolean alive = buffer.readBoolean();
        ResourceLocation dimensionId = null;
        Vec3 position = null;
        Vec3 velocity = Vec3.ZERO;
        if (buffer.readBoolean()) {
            dimensionId = buffer.readResourceLocation();
            position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            velocity = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        }
        return new RadarCompassTargetPayload(
                networkId,
                revision,
                targetUuid,
                confirmedByLatestScan,
                alive,
                dimensionId,
                position,
                velocity,
                buffer.readVarLong());
    }

}
