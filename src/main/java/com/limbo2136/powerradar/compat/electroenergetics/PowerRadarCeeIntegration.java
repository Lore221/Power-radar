package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.limbo2136.powerradar.radar.RadarStructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class PowerRadarCeeIntegration {
    private PowerRadarCeeIntegration() {
    }

    public static void configureRadarLoad(
            ServerLevel level,
            BlockPos pos,
            boolean validStructure,
            RadarStructureType structureType,
            int phasedArrayPanelCount,
            int overviewModuleCount
    ) {
        SimulatedDevice device = DevicesSavedData.load(level).getDevice(pos);
        if (device instanceof RadarControllerCeeDevice radarDevice) {
            radarDevice.configureLoad(validStructure, structureType, phasedArrayPanelCount, overviewModuleCount);
        }
    }

    public static void configureMonitorLoad(ServerLevel level, BlockPos pos, boolean validStructure, int activeDisplayCount) {
        SimulatedDevice device = DevicesSavedData.load(level).getDevice(pos);
        if (device instanceof MonitorControllerCeeDevice monitorDevice) {
            monitorDevice.configureLoad(validStructure, activeDisplayCount);
        }
    }

    public static void configureComputingLoad(ServerLevel level, BlockPos pos) {
        SimulatedDevice device = DevicesSavedData.load(level).getDevice(pos);
        if (device instanceof ComputingBlockCeeDevice computingDevice) {
            computingDevice.configureLoad();
        }
    }

    public static void configureOnboardComputerLoad(ServerLevel level, BlockPos pos) {
        SimulatedDevice device = DevicesSavedData.load(level).getDevice(pos);
        if (device instanceof MonitorControllerCeeDevice monitorDevice) {
            monitorDevice.configureFixedLoad(true, PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts());
        }
    }
}
