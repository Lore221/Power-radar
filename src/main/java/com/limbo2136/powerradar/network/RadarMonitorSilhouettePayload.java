package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.compat.aeronautics.SableSilhouetteStatus;
import com.limbo2136.powerradar.compat.aeronautics.SableSilhouetteLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorSilhouettePayload(
        ResourceLocation dimensionId,
        UUID structureUuid,
        int version,
        SableSilhouetteStatus status,
        List<Line> lines,
        List<Fill> fills
) implements CustomPacketPayload {
    public static final Type<RadarMonitorSilhouettePayload> TYPE = new Type<>(
            PowerRadar.id("radar_monitor_silhouette"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorSilhouettePayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorSilhouettePayload::write, RadarMonitorSilhouettePayload::new);

    public RadarMonitorSilhouettePayload {
        status = Objects.requireNonNull(status, "status");
        lines = List.copyOf(lines);
        fills = List.copyOf(fills);
        if (!SableSilhouetteLimits.accepts(lines.size(), fills.size())) {
            throw new IllegalArgumentException(
                    "Sable silhouette payload exceeds geometry limits: lines=" + lines.size()
                            + ", fills=" + fills.size());
        }
        if (lines.stream().anyMatch(line -> !line.finite())
                || fills.stream().anyMatch(fill -> !fill.valid())) {
            throw new IllegalArgumentException("Sable silhouette payload contains invalid geometry");
        }
    }

    public RadarMonitorSilhouettePayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readResourceLocation(),
                buffer.readUUID(),
                buffer.readVarInt(),
                readStatus(buffer),
                readLines(buffer),
                readFills(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeUUID(this.structureUuid);
        buffer.writeVarInt(this.version);
        buffer.writeByte(this.status.ordinal());
        buffer.writeVarInt(this.lines.size());
        for (Line line : this.lines) {
            buffer.writeFloat(line.x1());
            buffer.writeFloat(line.z1());
            buffer.writeFloat(line.x2());
            buffer.writeFloat(line.z2());
        }
        buffer.writeVarInt(this.fills.size());
        for (Fill fill : this.fills) {
            buffer.writeFloat(fill.minX());
            buffer.writeFloat(fill.minZ());
            buffer.writeFloat(fill.maxX());
            buffer.writeFloat(fill.maxZ());
        }
    }

    private static List<Line> readLines(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > SableSilhouetteLimits.MAX_LINES) {
            throw new IllegalArgumentException("Invalid Sable silhouette line count: " + count);
        }
        ArrayList<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Line line = new Line(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            if (!line.finite()) {
                throw new IllegalArgumentException("Non-finite Sable silhouette line");
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    private static List<Fill> readFills(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > SableSilhouetteLimits.MAX_FILLS) {
            throw new IllegalArgumentException("Invalid Sable silhouette fill count: " + count);
        }
        ArrayList<Fill> fills = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Fill fill = new Fill(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            if (!fill.valid()) {
                throw new IllegalArgumentException("Invalid Sable silhouette fill");
            }
            fills.add(fill);
        }
        return List.copyOf(fills);
    }

    private static SableSilhouetteStatus readStatus(RegistryFriendlyByteBuf buffer) {
        int ordinal = buffer.readUnsignedByte();
        SableSilhouetteStatus[] values = SableSilhouetteStatus.values();
        if (ordinal >= values.length) {
            throw new IllegalArgumentException("Invalid Sable silhouette status: " + ordinal);
        }
        return values[ordinal];
    }


    public record Line(float x1, float z1, float x2, float z2) {
        private boolean finite() {
            return Float.isFinite(this.x1) && Float.isFinite(this.z1)
                    && Float.isFinite(this.x2) && Float.isFinite(this.z2);
        }
    }

    public record Fill(float minX, float minZ, float maxX, float maxZ) {
        private boolean valid() {
            return Float.isFinite(this.minX) && Float.isFinite(this.minZ)
                    && Float.isFinite(this.maxX) && Float.isFinite(this.maxZ)
                    && this.maxX > this.minX && this.maxZ > this.minZ;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
