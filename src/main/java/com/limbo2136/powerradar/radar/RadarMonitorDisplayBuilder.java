package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.RadarWorldPose;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

public final class RadarMonitorDisplayBuilder {
    private RadarMonitorDisplayBuilder() {
    }

    public static RadarMonitorDisplayData noLink(BlockPos monitorPos, Direction monitorFacing, long serverGameTime) {
        return noLink(monitorPos, monitorFacing, serverGameTime, RadarNetworkConnectionStatus.NO_LINK);
    }

    public static RadarMonitorDisplayData noLink(
            BlockPos monitorPos,
            Direction monitorFacing,
            long serverGameTime,
            PowerRadarCeeState monitorElectricalState
    ) {
        return noLink(monitorPos, monitorFacing, serverGameTime, RadarNetworkConnectionStatus.NO_LINK,
                monitorElectricalState, 0.0, PowerRadarCeeConstants.OFF_RESISTANCE_OHMS, 0, 0, false);
    }

    public static RadarMonitorDisplayData noLink(
            BlockPos monitorPos,
            Direction monitorFacing,
            long serverGameTime,
            RadarNetworkConnectionStatus connectionStatus
    ) {
        return noLink(monitorPos, monitorFacing, serverGameTime, connectionStatus,
                PowerRadarCeeState.INVALID_STRUCTURE, 0.0, PowerRadarCeeConstants.OFF_RESISTANCE_OHMS, 0, 0, false);
    }

    public static RadarMonitorDisplayData noLink(
            BlockPos monitorPos,
            Direction monitorFacing,
            long serverGameTime,
            RadarNetworkConnectionStatus connectionStatus,
            PowerRadarCeeState monitorElectricalState,
            double monitorVoltageVolts,
            double monitorResistanceOhms,
            int monitorDisplayCount,
            int monitorScreenSize,
            boolean monitorRendererEnabled
    ) {
        return new RadarMonitorDisplayData(
                monitorPos,
                connectionStatus,
                false,
                null,
                null,
                Level.OVERWORLD.location(),
                0.0,
                0.0,
                0.0,
                Direction.NORTH,
                monitorViewYawDegrees(),
                RadarOrientationState.fixed(RadarGeometry.yawDegrees(Direction.NORTH), serverGameTime),
                false,
                false,
                monitorElectricalState,
                monitorVoltageVolts,
                monitorResistanceOhms,
                monitorDisplayCount,
                monitorScreenSize,
                monitorRendererEnabled,
                RadarScanMode.GROUND,
                RadarDetectionFilters.DEFAULT_MASK,
                0,
                null,
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                0,
                RadarScanMode.GROUND.sectorAngleDegrees(),
                RadarScanMode.GROUND.verticalScanHeight(),
                0,
                5,
                0L,
                serverGameTime,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public static RadarMonitorDisplayData fromController(
            BlockPos monitorPos,
            Direction monitorFacing,
            RadarControllerBlockEntity controller,
            long serverGameTime
    ) {
        return fromController(monitorPos, monitorFacing, controller, serverGameTime,
                PowerRadarCeeState.POWERED, 0.0, 0.0, 0, 0, true, 0, null, List.of(), List.of(), List.of());
    }

    public static RadarMonitorDisplayData fromController(
            BlockPos monitorPos,
            Direction monitorFacing,
            RadarControllerBlockEntity controller,
            long serverGameTime,
            PowerRadarCeeState monitorElectricalState,
            double monitorVoltageVolts,
            double monitorResistanceOhms,
            int monitorDisplayCount,
            int monitorScreenSize,
            boolean monitorRendererEnabled,
            int autotargetFilterMask,
            UUID manualTargetUuid,
            List<String> onlinePlayerNames,
            List<String> whitelistedPlayerNames,
            List<String> whitelistedSableNames
    ) {
        return fromControllers(
                monitorPos,
                monitorFacing,
                List.of(controller),
                serverGameTime,
                monitorElectricalState,
                monitorVoltageVolts,
                monitorResistanceOhms,
                monitorDisplayCount,
                monitorScreenSize,
                monitorRendererEnabled,
                autotargetFilterMask,
                manualTargetUuid,
                onlinePlayerNames,
                whitelistedPlayerNames,
                whitelistedSableNames);
    }

    public static RadarMonitorDisplayData fromControllers(
            BlockPos monitorPos,
            Direction monitorFacing,
            List<RadarControllerBlockEntity> controllers,
            long serverGameTime,
            PowerRadarCeeState monitorElectricalState,
            double monitorVoltageVolts,
            double monitorResistanceOhms,
            int monitorDisplayCount,
            int monitorScreenSize,
            boolean monitorRendererEnabled,
            int autotargetFilterMask,
            UUID manualTargetUuid,
            List<String> onlinePlayerNames,
            List<String> whitelistedPlayerNames,
            List<String> whitelistedSableNames
    ) {
        if (controllers.isEmpty()) {
            return noLink(monitorPos, monitorFacing, serverGameTime, RadarNetworkConnectionStatus.CONTROLLER_OFFLINE,
                    monitorElectricalState, monitorVoltageVolts, monitorResistanceOhms,
                    monitorDisplayCount, monitorScreenSize, monitorRendererEnabled);
        }
        Map<String, RadarDisplayTarget> targetsByKey = new LinkedHashMap<>();
        ArrayList<RadarDisplayCoverage> coverages = new ArrayList<>(controllers.size());
        RadarControllerBlockEntity firstActiveController = null;
        RadarControllerBlockEntity firstValidController = null;
        boolean structureValid = false;
        boolean active = false;
        int trackUpdateIntervalTicks = Integer.MAX_VALUE;
        long lastScanGameTime = 0L;
        int validPanelCount = 0;
        int currentRange = 0;
        int maxRange = 0;
        for (RadarControllerBlockEntity radar : controllers) {
            boolean radarAssembled = radar.assembled();
            int radarValidPanelCount = radar.validPanelCount();
            int radarCurrentRange = radar.displayCurrentRange();
            int radarTrackUpdateIntervalTicks = radar.trackUpdateIntervalTicks();
            long radarLastScanGameTime = radar.lastScanGameTime();
            if (radarAssembled && radarValidPanelCount > 0) {
                structureValid = true;
                if (firstValidController == null) {
                    firstValidController = radar;
                }
            }
            if (radarAssembled && radarCurrentRange > 0) {
                active = true;
                if (firstActiveController == null) {
                    firstActiveController = radar;
                }
            }
            trackUpdateIntervalTicks = Math.min(trackUpdateIntervalTicks, radarTrackUpdateIntervalTicks);
            lastScanGameTime = Math.max(lastScanGameTime, radarLastScanGameTime);
            validPanelCount += radarValidPanelCount;
            currentRange = Math.max(currentRange, radarCurrentRange);
            maxRange = Math.max(maxRange, radar.maxRange());
            int radarSectorAngle = radar.orientationState().structureType() == RadarStructureType.OVERVIEW
                    ? 360
                    : radar.scanMode().sectorAngleDegrees();
            RadarWorldPose radarWorldPose = radar.worldPoseAt(serverGameTime);
            RadarOrientationState radarWorldOrientation = RadarOrientationState.fixed(
                    radar.orientationState().structureType(),
                    radar.orientationState().structureType() == RadarStructureType.OVERVIEW
                            ? 0.0F
                            : radarWorldPose.yawDegrees(),
                    serverGameTime
            );
            coverages.add(new RadarDisplayCoverage(
                    radar.radarId(),
                    radar.worldControllerPos(),
                    radar.dimensionId(),
                    radarWorldPose.origin().x,
                    radarWorldPose.origin().y,
                    radarWorldPose.origin().z,
                    radarWorldOrientation,
                    radarCurrentRange,
                    radarSectorAngle));
            long targetDisplayGameTime = radarLastScanGameTime > 0L
                    ? radarLastScanGameTime
                    : serverGameTime;
            radar.forEachTargetTrack(track -> {
                if (!RadarDetectionFilters.enabled(radar.detectionFilterMask(), track.category())) {
                    return;
                }
                RadarDisplayTarget candidate = RadarDisplayTarget.fromTrack(track, targetDisplayGameTime, radarTrackUpdateIntervalTicks);
                targetsByKey.merge(candidate.stableSelectionKey(), candidate,
                        (previous, next) -> next.displayAgeTicks() < previous.displayAgeTicks() ? next : previous);
            });
        }
        RadarControllerBlockEntity controller = firstActiveController != null
                ? firstActiveController
                : firstValidController != null ? firstValidController : controllers.get(0);
        List<RadarDisplayTarget> targets = List.copyOf(targetsByKey.values());
        int sectorAngle = controller.orientationState().structureType() == RadarStructureType.OVERVIEW
                ? 360
                : controller.scanMode().sectorAngleDegrees();
        RadarWorldPose controllerWorldPose = controller.worldPoseAt(serverGameTime);
        RadarOrientationState controllerWorldOrientation = RadarOrientationState.fixed(
                controller.orientationState().structureType(),
                controller.orientationState().structureType() == RadarStructureType.OVERVIEW
                        ? 0.0F
                        : controllerWorldPose.yawDegrees(),
                serverGameTime
        );
        RadarMonitorDisplayData data = new RadarMonitorDisplayData(
                monitorPos,
                RadarNetworkConnectionStatus.CONNECTED,
                true,
                controller.radarId(),
                controller.worldControllerPos(),
                controller.dimensionId(),
                controllerWorldPose.origin().x,
                controllerWorldPose.origin().y,
                controllerWorldPose.origin().z,
                controller.radarFacing(),
                monitorViewYawDegrees(),
                controllerWorldOrientation,
                structureValid,
                active,
                monitorElectricalState,
                monitorVoltageVolts,
                monitorResistanceOhms,
                monitorDisplayCount,
                monitorScreenSize,
                monitorRendererEnabled,
                controller.scanMode(),
                controller.detectionFilterMask(),
                RadarDetectionFilters.sanitize(autotargetFilterMask),
                manualTargetUuid,
                List.copyOf(onlinePlayerNames),
                List.copyOf(whitelistedPlayerNames),
                List.copyOf(whitelistedSableNames),
                validPanelCount,
                currentRange,
                maxRange,
                sectorAngle,
                controller.scanMode().verticalScanHeight(),
                targets.size(),
                trackUpdateIntervalTicks,
                lastScanGameTime,
                serverGameTime,
                List.copyOf(coverages),
                List.of(),
                List.copyOf(targets)
        );
        if (PowerRadarDebugOptions.scanOptimizationLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][MonitorData] monitor={} controller={} structure={} assembled={} active={} modules={} mode={} range={} maxRange={} targets={} cache={} interval={} lastScan={} serverTime={} renderer={} monitorState={} monitorVoltage={}",
                    monitorPos,
                    controller.getBlockPos(),
                    controller.orientationState().structureType(),
                    controller.assembled(),
                    controller.isElectricallyOperational(),
                    controller.validPanelCount(),
                    controller.scanMode(),
                    data.currentRange(),
                    data.maxRange(),
                    data.targets().size(),
                    controller.cachedTargetCount(),
                    data.trackUpdateIntervalTicks(),
                    data.lastScanGameTime(),
                    serverGameTime,
                    monitorRendererEnabled,
                    monitorElectricalState,
                    String.format(java.util.Locale.ROOT, "%.1f", monitorVoltageVolts)
            );
        }
        return data;
    }

    private static float monitorViewYawDegrees() {
        return RadarGeometry.yawDegrees(Direction.NORTH);
    }
}
