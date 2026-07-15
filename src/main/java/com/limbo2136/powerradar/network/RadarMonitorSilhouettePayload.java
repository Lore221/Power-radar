package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadarMonitorSilhouettePayload(
        ResourceLocation dimensionId,
        UUID structureUuid,
        int version,
        List<Line> lines,
        List<Fill> fills
) implements CustomPacketPayload {
    public static final Type<RadarMonitorSilhouettePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_silhouette"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorSilhouettePayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorSilhouettePayload::write, RadarMonitorSilhouettePayload::new);

    public RadarMonitorSilhouettePayload {
        lines = List.copyOf(lines);
        fills = List.copyOf(fills);
    }

    public RadarMonitorSilhouettePayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readResourceLocation(),
                buffer.readUUID(),
                buffer.readVarInt(),
                readLines(buffer),
                readFills(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeUUID(this.structureUuid);
        buffer.writeVarInt(this.version);
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
        ArrayList<Line> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new Line(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
        }
        return List.copyOf(lines);
    }

    private static List<Fill> readFills(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        ArrayList<Fill> fills = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            fills.add(new Fill(buffer.readFloat(), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
        }
        return List.copyOf(fills);
    }

    public record Line(float x1, float z1, float x2, float z2) {
    }

    public record Fill(float minX, float minZ, float maxX, float maxZ) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
