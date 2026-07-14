package com.limbo2136.powerradar.radar;

import java.util.List;
import java.util.UUID;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record RadarMonitorDisplayData(
        BlockPos monitorPos,
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
        RadarOrientationState orientationState,
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
) {
    public RadarMonitorDisplayData withTargets(List<RadarDisplayTarget> targets) {
        return withTargets(targets, this.lastScanGameTime, this.serverGameTime);
    }

    public RadarMonitorDisplayData withTargets(List<RadarDisplayTarget> targets, long lastScanGameTime, long serverGameTime) {
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
                this.radarFacing,
                this.monitorViewYawDegrees,
                this.orientationState,
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
                lastScanGameTime,
                serverGameTime,
                this.coverages,
                this.shellAlarmZones,
                List.copyOf(targets)
        );
    }

    public RadarMonitorDisplayData withMonitorContext(
            BlockPos monitorPos,
            Direction monitorFacing,
            PowerRadarCeeState monitorElectricalState,
            double monitorVoltageVolts,
            double monitorResistanceOhms,
            int monitorDisplayCount,
            int monitorScreenSize,
            boolean monitorRendererEnabled
    ) {
        return new RadarMonitorDisplayData(
                monitorPos,
                this.connectionStatus,
                this.linked,
                this.radarId,
                this.controllerPos,
                this.radarDimensionId,
                this.radarOriginX,
                this.radarOriginY,
                this.radarOriginZ,
                this.radarFacing,
                RadarGeometry.yawDegrees(Direction.NORTH),
                this.orientationState,
                this.structureValid,
                this.active,
                monitorElectricalState,
                monitorVoltageVolts,
                monitorResistanceOhms,
                monitorDisplayCount,
                monitorScreenSize,
                monitorRendererEnabled,
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

    public RadarMonitorDisplayData withShellAlarmZones(List<ShellAlarmDisplayZone> zones) {
        return new RadarMonitorDisplayData(
                this.monitorPos, this.connectionStatus, this.linked, this.radarId, this.controllerPos,
                this.radarDimensionId, this.radarOriginX, this.radarOriginY, this.radarOriginZ,
                this.radarFacing, this.monitorViewYawDegrees, this.orientationState, this.structureValid,
                this.active, this.monitorElectricalState, this.monitorVoltageVolts, this.monitorResistanceOhms,
                this.monitorDisplayCount, this.monitorScreenSize, this.monitorRendererEnabled, this.mode,
                this.detectionFilterMask, this.autotargetFilterMask, this.manualTargetUuid,
                this.onlinePlayerNames, this.whitelistedPlayerNames, this.whitelistedSableNames,
                this.validPanelCount, this.currentRange, this.maxRange, this.sectorAngle,
                this.verticalScanHeight, this.displayedTargetCount, this.trackUpdateIntervalTicks,
                this.lastScanGameTime, this.serverGameTime, this.coverages, List.copyOf(zones), this.targets);
    }
}
