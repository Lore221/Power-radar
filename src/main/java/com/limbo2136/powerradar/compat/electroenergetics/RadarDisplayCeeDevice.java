package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.block.entity.RadarDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RadarDisplayCeeDevice extends PowerRadarCeeLoadDevice {
    private RadarDisplayBlockEntity blockEntity;

    public RadarDisplayCeeDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    public void configureLoad(boolean validStructure, int activeDisplayCount) {
        if (!validStructure || activeDisplayCount <= 0) {
            setLoad(false, 0.0, PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                    PowerRadarElectricalParameters.Voltages.radarDisplay().nominal());
            return;
        }
        setLoad(
                true,
                PowerRadarCeeConstants.monitorNominalPowerWatts(activeDisplayCount),
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                PowerRadarElectricalParameters.Voltages.radarDisplay().nominal());
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.radarDisplay();
    }

    @Override
    protected void publishSnapshot() {
        RadarDisplayBlockEntity display = loadedBlockEntity();
        if (display != null) {
            display.applyElectricalSnapshot(snapshot());
        }
    }

    private RadarDisplayBlockEntity loadedBlockEntity() {
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
        if (loaded instanceof RadarDisplayBlockEntity display) {
            this.blockEntity = display;
            return display;
        }
        return null;
    }
}
