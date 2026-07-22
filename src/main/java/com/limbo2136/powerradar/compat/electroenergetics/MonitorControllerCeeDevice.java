package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MonitorControllerCeeDevice extends PowerRadarCeeLoadDevice {
    private RadarMonitorControllerBlockEntity blockEntity;

    public MonitorControllerCeeDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    public void configureLoad(boolean validStructure, int activeDisplayCount) {
        if (!validStructure || activeDisplayCount <= 0) {
            setLoad(false, 0.0, PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                    PowerRadarElectricalParameters.Voltages.monitor().nominal());
            return;
        }
        setLoad(
                true,
                PowerRadarCeeConstants.monitorConstantPowerWatts(activeDisplayCount),
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                PowerRadarElectricalParameters.Voltages.monitor().nominal());
    }

    public void configureFixedLoad(boolean enabled, double powerWatts) {
        setLoad(
                enabled,
                enabled ? powerWatts : 0.0D,
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                PowerRadarElectricalParameters.Voltages.monitor().nominal());
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.monitor();
    }

    @Override
    protected void publishSnapshot() {
        RadarMonitorControllerBlockEntity controller = loadedBlockEntity();
        if (controller != null) {
            controller.applyElectricalSnapshot(snapshot());
        }
    }

    private RadarMonitorControllerBlockEntity loadedBlockEntity() {
        if (this.blockEntity != null) {
            if (this.blockEntity.isRemoved()) {
                this.blockEntity = null;
            } else {
                return this.blockEntity;
            }
        }
        if (!this.level.isLoaded(this.pos)) {
            return null;
        }
        BlockEntity loaded = this.level.getBlockEntity(this.pos);
        if (loaded instanceof RadarMonitorControllerBlockEntity controller) {
            this.blockEntity = controller;
            return controller;
        }
        return null;
    }
}
