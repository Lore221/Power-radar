package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.radar.RadarDisplayCoverage;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarId;
import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import com.limbo2136.powerradar.radar.RadarOrientationState;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.RadarTargetSourceKind;
import com.limbo2136.powerradar.radar.ShellAlarmDisplayZone;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RadarMonitorSnapshotPayload(
        BlockPos monitorPos,
        long revision,
        RadarNetworkConnectionStatus connectionStatus,
        boolean linked,
        @Nullable RadarId radarId,
        @Nullable BlockPos controllerPos,
        ResourceLocation radarDimensionId,
        double radarOriginX,
        double radarOriginY,
        double radarOriginZ,
        Direction radarFacing,
        float monitorViewYawDegrees,
        RadarStructureType structureType,
        float radarYawDegrees,
        float rotationSpeedDegreesPerTick,
        long rotationReferenceGameTime,
        boolean structureValid,
        boolean active,
        PowerRadarCeeState monitorElectricalState,
        double monitorVoltageVolts,
        double monitorResistanceOhms,
        int monitorDisplayCount,
        int monitorScreenSize,
        boolean monitorRendererEnabled,
        RadarScanMode mode,
        int detectionFilterMask,
        int autotargetFilterMask,
        @Nullable UUID manualTargetUuid,
        List<String> onlinePlayerNames,
        List<String> whitelistedPlayerNames,
        List<String> whitelistedSableNames,
        int validPanelCount,
        int currentRange,
        int maxRange,
        int sectorAngle,
        int verticalScanHeight,
        int displayedTargetCount,
        int trackUpdateIntervalTicks,
        long lastScanGameTime,
        long serverGameTime,
        List<RadarDisplayCoverage> coverages,
        List<ShellAlarmDisplayZone> shellAlarmZones,
        List<RadarDisplayTarget> targets
) implements CustomPacketPayload {
    /**
     * Increment when the encoded snapshot bytes change intentionally. The value also versions the
     * NeoForge payload registrar, while the codec test locks the exact bytes of each schema.
     */
    public static final int WIRE_SCHEMA_VERSION = 4;
    public static final CustomPacketPayload.Type<RadarMonitorSnapshotPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_monitor_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RadarMonitorSnapshotPayload> STREAM_CODEC =
            StreamCodec.ofMember(RadarMonitorSnapshotPayload::write, RadarMonitorSnapshotPayload::new);

    public RadarMonitorSnapshotPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readBlockPos(),
                buffer.readLong(),
                RadarNetworkConnectionStatus.valueOf(buffer.readUtf()),
                buffer.readBoolean(),
                readNullableRadarId(buffer),
                readNullableBlockPos(buffer),
                buffer.readResourceLocation(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                Direction.byName(buffer.readUtf()),
                buffer.readFloat(),
                RadarStructureType.valueOf(buffer.readUtf()),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                PowerRadarCeeState.valueOf(buffer.readUtf()),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                RadarScanMode.byName(buffer.readUtf()),
                buffer.readVarInt(),
                buffer.readVarInt(),
                readNullableUuid(buffer),
                readStringList(buffer),
                readStringList(buffer),
                readStringList(buffer),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readLong(),
                readCoverages(buffer),
                readShellAlarmZones(buffer),
                readTargets(buffer)
        );
    }

    public static RadarMonitorSnapshotPayload fromDisplayData(RadarMonitorDisplayData data) {
        return fromDisplayData(data, 0L);
    }

    public static RadarMonitorSnapshotPayload fromDisplayData(RadarMonitorDisplayData data, long revision) {
        return new RadarMonitorSnapshotPayload(
                data.monitorPos(),
                revision,
                data.connectionStatus(),
                data.linked(),
                data.radarId(),
                data.controllerPos(),
                data.radarDimensionId(),
                data.radarOriginX(),
                data.radarOriginY(),
                data.radarOriginZ(),
                data.radarFacing(),
                data.monitorViewYawDegrees(),
                data.orientationState().structureType(),
                data.orientationState().referenceYawDegrees(),
                data.orientationState().rotationSpeedDegreesPerTick(),
                data.orientationState().referenceGameTime(),
                data.structureValid(),
                data.active(),
                data.monitorElectricalState(),
                data.monitorVoltageVolts(),
                data.monitorResistanceOhms(),
                data.monitorDisplayCount(),
                data.monitorScreenSize(),
                data.monitorRendererEnabled(),
                data.mode(),
                data.detectionFilterMask(),
                data.autotargetFilterMask(),
                data.manualTargetUuid(),
                data.onlinePlayerNames(),
                data.whitelistedPlayerNames(),
                data.whitelistedSableNames(),
                data.validPanelCount(),
                data.currentRange(),
                data.maxRange(),
                data.sectorAngle(),
                data.verticalScanHeight(),
                data.displayedTargetCount(),
                data.trackUpdateIntervalTicks(),
                data.lastScanGameTime(),
                data.serverGameTime(),
                data.coverages(),
                data.shellAlarmZones(),
                data.targets()
        );
    }

    public RadarMonitorSnapshotPayload withTargets(List<RadarDisplayTarget> targets) {
        return new RadarMonitorSnapshotPayload(
                this.monitorPos,
                this.revision,
                this.connectionStatus,
                this.linked,
                this.radarId,
                this.controllerPos,
                this.radarDimensionId,
                this.radarOriginX,
                this.radarOriginY,
                this.radarOriginZ,
                this.radarFacing,
                this.monitorViewYawDegrees,
                this.structureType,
                this.radarYawDegrees,
                this.rotationSpeedDegreesPerTick,
                this.rotationReferenceGameTime,
                this.structureValid,
                this.active,
                this.monitorElectricalState,
                this.monitorVoltageVolts,
                this.monitorResistanceOhms,
                this.monitorDisplayCount,
                this.monitorScreenSize,
                this.monitorRendererEnabled,
                this.mode,
                this.detectionFilterMask,
                this.autotargetFilterMask,
                this.manualTargetUuid,
                this.onlinePlayerNames,
                this.whitelistedPlayerNames,
                this.whitelistedSableNames,
                this.validPanelCount,
                this.currentRange,
                this.maxRange,
                this.sectorAngle,
                this.verticalScanHeight,
                targets.size(),
                this.trackUpdateIntervalTicks,
                this.lastScanGameTime,
                this.serverGameTime,
                this.coverages,
                this.shellAlarmZones,
                List.copyOf(targets)
        );
    }

    void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.monitorPos);
        buffer.writeLong(this.revision);
        buffer.writeUtf(this.connectionStatus.name());
        buffer.writeBoolean(this.linked);
        writeNullableRadarId(buffer, this.radarId);
        writeNullableBlockPos(buffer, this.controllerPos);
        buffer.writeResourceLocation(this.radarDimensionId);
        buffer.writeDouble(this.radarOriginX);
        buffer.writeDouble(this.radarOriginY);
        buffer.writeDouble(this.radarOriginZ);
        buffer.writeUtf((this.radarFacing == null ? Direction.NORTH : this.radarFacing).getName());
        buffer.writeFloat(this.monitorViewYawDegrees);
        buffer.writeUtf((this.structureType == null ? RadarStructureType.PHASED_ARRAY : this.structureType).name());
        buffer.writeFloat(this.radarYawDegrees);
        buffer.writeFloat(this.rotationSpeedDegreesPerTick);
        buffer.writeLong(this.rotationReferenceGameTime);
        buffer.writeBoolean(this.structureValid);
        buffer.writeBoolean(this.active);
        buffer.writeUtf((this.monitorElectricalState == null ? PowerRadarCeeState.INVALID_STRUCTURE : this.monitorElectricalState).name());
        buffer.writeDouble(this.monitorVoltageVolts);
        buffer.writeDouble(this.monitorResistanceOhms);
        buffer.writeVarInt(this.monitorDisplayCount);
        buffer.writeVarInt(this.monitorScreenSize);
        buffer.writeBoolean(this.monitorRendererEnabled);
        buffer.writeUtf(this.mode.name());
        buffer.writeVarInt(this.detectionFilterMask);
        buffer.writeVarInt(this.autotargetFilterMask);
        writeNullableUuid(buffer, this.manualTargetUuid);
        writeStringList(buffer, this.onlinePlayerNames);
        writeStringList(buffer, this.whitelistedPlayerNames);
        writeStringList(buffer, this.whitelistedSableNames);
        buffer.writeVarInt(this.validPanelCount);
        buffer.writeVarInt(this.currentRange);
        buffer.writeVarInt(this.maxRange);
        buffer.writeVarInt(this.sectorAngle);
        buffer.writeVarInt(this.verticalScanHeight);
        buffer.writeVarInt(this.displayedTargetCount);
        buffer.writeVarInt(this.trackUpdateIntervalTicks);
        buffer.writeLong(this.lastScanGameTime);
        buffer.writeLong(this.serverGameTime);
        buffer.writeVarInt(this.coverages.size());
        for (RadarDisplayCoverage coverage : this.coverages) {
            writeCoverage(buffer, coverage);
        }
        buffer.writeVarInt(this.shellAlarmZones.size());
        for (ShellAlarmDisplayZone zone : this.shellAlarmZones) {
            buffer.writeResourceLocation(zone.dimensionId());
            buffer.writeDouble(zone.centerX());
            buffer.writeDouble(zone.centerY());
            buffer.writeDouble(zone.centerZ());
            buffer.writeVarInt(zone.sideBlocks());
        }
        buffer.writeVarInt(this.targets.size());
        for (RadarDisplayTarget target : this.targets) {
            writeTarget(buffer, target);
        }
    }

    private static List<RadarDisplayCoverage> readCoverages(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<RadarDisplayCoverage> coverages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            RadarId radarId = new RadarId(buffer.readResourceLocation(), buffer.readBlockPos());
            BlockPos controllerPos = buffer.readBlockPos();
            ResourceLocation dimensionId = buffer.readResourceLocation();
            double originX = buffer.readDouble();
            double originY = buffer.readDouble();
            double originZ = buffer.readDouble();
            RadarOrientationState orientationState = new RadarOrientationState(
                    RadarStructureType.valueOf(buffer.readUtf()),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readLong());
            int currentRange = buffer.readVarInt();
            int sectorAngle = buffer.readVarInt();
            coverages.add(new RadarDisplayCoverage(
                    radarId,
                    controllerPos,
                    dimensionId,
                    originX,
                    originY,
                    originZ,
                    orientationState,
                    currentRange,
                    sectorAngle));
        }
        return List.copyOf(coverages);
    }

    private static List<ShellAlarmDisplayZone> readShellAlarmZones(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        ArrayList<ShellAlarmDisplayZone> zones = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            zones.add(new ShellAlarmDisplayZone(buffer.readResourceLocation(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readVarInt()));
        }
        return List.copyOf(zones);
    }

    private static void writeCoverage(RegistryFriendlyByteBuf buffer, RadarDisplayCoverage coverage) {
        buffer.writeResourceLocation(coverage.radarId().dimensionId());
        buffer.writeBlockPos(coverage.radarId().controllerPos());
        buffer.writeBlockPos(coverage.controllerPos());
        buffer.writeResourceLocation(coverage.dimensionId());
        buffer.writeDouble(coverage.originX());
        buffer.writeDouble(coverage.originY());
        buffer.writeDouble(coverage.originZ());
        buffer.writeUtf(coverage.orientationState().structureType().name());
        buffer.writeFloat(coverage.orientationState().referenceYawDegrees());
        buffer.writeFloat(coverage.orientationState().rotationSpeedDegreesPerTick());
        buffer.writeLong(coverage.orientationState().referenceGameTime());
        buffer.writeVarInt(coverage.currentRange());
        buffer.writeVarInt(coverage.sectorAngle());
    }

    private static void writeNullableRadarId(RegistryFriendlyByteBuf buffer, @Nullable RadarId radarId) {
        buffer.writeBoolean(radarId != null);
        if (radarId != null) {
            buffer.writeResourceLocation(radarId.dimensionId());
            buffer.writeBlockPos(radarId.controllerPos());
        }
    }

    @Nullable
    private static RadarId readNullableRadarId(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new RadarId(buffer.readResourceLocation(), buffer.readBlockPos()) : null;
    }

    private static void writeNullableBlockPos(RegistryFriendlyByteBuf buffer, @Nullable BlockPos pos) {
        buffer.writeBoolean(pos != null);
        if (pos != null) {
            buffer.writeBlockPos(pos);
        }
    }

    @Nullable
    private static BlockPos readNullableBlockPos(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readBlockPos() : null;
    }

    static List<RadarDisplayTarget> readTargets(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<RadarDisplayTarget> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(readTarget(buffer));
        }
        return List.copyOf(targets);
    }

    private static RadarDisplayTarget readTarget(RegistryFriendlyByteBuf buffer) {
        return new RadarDisplayTarget(
                readNullableUuid(buffer),
                buffer.readVarInt(),
                buffer.readResourceLocation(),
                RadarTargetSourceKind.valueOf(buffer.readUtf()),
                readNullableString(buffer),
                RadarTargetCategory.valueOf(buffer.readUtf()),
                buffer.readResourceLocation(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readBoolean(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readVarInt()
        );
    }

    static void writeTarget(RegistryFriendlyByteBuf buffer, RadarDisplayTarget target) {
        writeNullableUuid(buffer, target.targetUuid());
        buffer.writeVarInt(target.targetId());
        buffer.writeResourceLocation(target.entityTypeId());
        buffer.writeUtf(target.sourceKind().name());
        writeNullableString(buffer, target.displayName());
        buffer.writeUtf(target.category().name());
        buffer.writeResourceLocation(target.dimensionId());
        buffer.writeDouble(target.x());
        buffer.writeDouble(target.y());
        buffer.writeDouble(target.z());
        buffer.writeDouble(target.velocityX());
        buffer.writeDouble(target.velocityY());
        buffer.writeDouble(target.velocityZ());
        buffer.writeBoolean(target.hasVelocity());
        buffer.writeFloat(target.structureHeadingDegrees());
        buffer.writeVarInt(target.silhouetteVersion());
        buffer.writeVarInt(target.displayAgeTicks());
    }

    private static void writeNullableUuid(RegistryFriendlyByteBuf buffer, @Nullable UUID uuid) {
        buffer.writeBoolean(uuid != null);
        if (uuid != null) {
            buffer.writeUUID(uuid);
        }
    }

    @Nullable
    private static UUID readNullableUuid(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    private static void writeNullableString(RegistryFriendlyByteBuf buffer, @Nullable String value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            buffer.writeUtf(value);
        }
    }

    @Nullable
    private static String readNullableString(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUtf() : null;
    }

    private static List<String> readStringList(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<String> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(buffer.readUtf());
        }
        return List.copyOf(values);
    }

    private static void writeStringList(RegistryFriendlyByteBuf buffer, List<String> values) {
        buffer.writeVarInt(values.size());
        for (String value : values) {
            buffer.writeUtf(value);
        }
    }

    public RadarMonitorDisplayData displayData() {
        return new RadarMonitorDisplayData(
                this.monitorPos,
                this.connectionStatus,
                this.linked,
                this.radarId,
                this.controllerPos,
                this.radarDimensionId,
                this.radarOriginX,
                this.radarOriginY,
                this.radarOriginZ,
                this.radarFacing == null ? Direction.NORTH : this.radarFacing,
                this.monitorViewYawDegrees,
                new RadarOrientationState(
                        this.structureType == null ? RadarStructureType.PHASED_ARRAY : this.structureType,
                        this.radarYawDegrees,
                        this.rotationSpeedDegreesPerTick,
                        this.rotationReferenceGameTime
                ),
                this.structureValid,
                this.active,
                this.monitorElectricalState == null ? PowerRadarCeeState.INVALID_STRUCTURE : this.monitorElectricalState,
                this.monitorVoltageVolts,
                this.monitorResistanceOhms,
                this.monitorDisplayCount,
                this.monitorScreenSize,
                this.monitorRendererEnabled,
                this.mode,
                this.detectionFilterMask,
                this.autotargetFilterMask,
                this.manualTargetUuid,
                this.onlinePlayerNames,
                this.whitelistedPlayerNames,
                this.whitelistedSableNames,
                this.validPanelCount,
                this.currentRange,
                this.maxRange,
                this.sectorAngle,
                this.verticalScanHeight,
                this.displayedTargetCount,
                this.trackUpdateIntervalTicks,
                this.lastScanGameTime,
                this.serverGameTime,
                this.coverages,
                this.shellAlarmZones,
                this.targets
        );
    }

    public int approximatePayloadSizeBytes() {
        int size = blockPosBytes();
        size += utfBytes(this.connectionStatus.name());
        size += 2; // linked + nullable radarId marker
        if (this.radarId != null) {
            size += resourceLocationBytes(this.radarId.dimensionId()) + blockPosBytes();
        }
        size += 1; // nullable controller marker
        if (this.controllerPos != null) {
        size += blockPosBytes();
        size += 8; // revision
        }
        size += resourceLocationBytes(this.radarDimensionId);
        size += 24; // origin doubles
        size += utfBytes((this.radarFacing == null ? Direction.NORTH : this.radarFacing).getName());
        size += 4; // monitorViewYawDegrees
        size += utfBytes((this.structureType == null ? RadarStructureType.PHASED_ARRAY : this.structureType).name());
        size += 16; // radarYawDegrees + rotationSpeedDegreesPerTick + rotationReferenceGameTime
        size += 2; // structureValid + active
        size += utfBytes((this.monitorElectricalState == null ? PowerRadarCeeState.INVALID_STRUCTURE : this.monitorElectricalState).name());
        size += 16; // monitor voltage + resistance
        size += varIntBytes(this.monitorDisplayCount);
        size += varIntBytes(this.monitorScreenSize);
        size += 1; // renderer enabled
        size += utfBytes(this.mode.name());
        size += varIntBytes(this.detectionFilterMask);
        size += varIntBytes(this.autotargetFilterMask);
        size += 1 + (this.manualTargetUuid == null ? 0 : 16);
        size += stringListBytes(this.onlinePlayerNames);
        size += stringListBytes(this.whitelistedPlayerNames);
        size += stringListBytes(this.whitelistedSableNames);
        size += varIntBytes(this.validPanelCount);
        size += varIntBytes(this.currentRange);
        size += varIntBytes(this.maxRange);
        size += varIntBytes(this.sectorAngle);
        size += varIntBytes(this.verticalScanHeight);
        size += varIntBytes(this.displayedTargetCount);
        size += varIntBytes(this.trackUpdateIntervalTicks);
        size += 16; // lastScanGameTime + serverGameTime
        size += varIntBytes(this.coverages.size());
        for (RadarDisplayCoverage coverage : this.coverages) {
            size += coverageBytes(coverage);
        }
        size += varIntBytes(this.shellAlarmZones.size());
        for (ShellAlarmDisplayZone zone : this.shellAlarmZones) {
            size += resourceLocationBytes(zone.dimensionId()) + 24 + varIntBytes(zone.sideBlocks());
        }
        size += varIntBytes(this.targets.size());
        for (RadarDisplayTarget target : this.targets) {
            size += targetBytes(target);
        }
        return size;
    }

    private static int coverageBytes(RadarDisplayCoverage coverage) {
        return resourceLocationBytes(coverage.radarId().dimensionId())
                + blockPosBytes()
                + blockPosBytes()
                + resourceLocationBytes(coverage.dimensionId())
                + 24
                + utfBytes(coverage.orientationState().structureType().name())
                + 16
                + varIntBytes(coverage.currentRange())
                + varIntBytes(coverage.sectorAngle());
    }

    private static int targetBytes(RadarDisplayTarget target) {
        int size = 1; // nullable uuid marker
        if (target.targetUuid() != null) {
            size += 16;
        }
        size += varIntBytes(target.targetId())
                + resourceLocationBytes(target.entityTypeId())
                + utfBytes(target.sourceKind().name())
                + 1; // nullable displayName marker
        if (target.displayName() != null) {
            size += utfBytes(target.displayName());
        }
        size += utfBytes(target.category().name())
                + resourceLocationBytes(target.dimensionId())
                + 48
                + 1
                + 4
                + varIntBytes(target.silhouetteVersion())
                + varIntBytes(target.displayAgeTicks());
        return size;
    }

    private static int resourceLocationBytes(ResourceLocation location) {
        return utfBytes(location.toString());
    }

    private static int utfBytes(String value) {
        return varIntBytes(value.length()) + value.length();
    }

    private static int stringListBytes(List<String> values) {
        int size = varIntBytes(values.size());
        for (String value : values) {
            size += utfBytes(value);
        }
        return size;
    }

    private static int blockPosBytes() {
        return 8;
    }

    private static int varIntBytes(int value) {
        int bytes = 1;
        while ((value & -128) != 0) {
            bytes++;
            value >>>= 7;
        }
        return bytes;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}
