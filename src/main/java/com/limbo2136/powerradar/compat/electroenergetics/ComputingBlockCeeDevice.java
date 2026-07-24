package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.block.entity.ComputingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ComputingBlockCeeDevice extends PowerRadarCeeLoadDevice {
    private ComputingBlockEntity blockEntity;

    public ComputingBlockCeeDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    public void configureLoad() {
        setLoad(true, PowerRadarElectricalParameters.Ratings.computingBlockPowerWatts(),
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                PowerRadarElectricalParameters.Voltages.monitor().nominal());
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.monitor();
    }

    @Override
    protected void publishSnapshot() {
        ComputingBlockEntity computer = loadedBlockEntity();
        if (computer != null) {
            computer.applyElectricalSnapshot(snapshot());
        }
    }

    private ComputingBlockEntity loadedBlockEntity() {
        if (this.blockEntity != null && !this.blockEntity.isRemoved()) {
            return this.blockEntity;
        }
        this.blockEntity = null;
        if (!this.level.isLoaded(this.pos)) {
            return null;
        }
        BlockEntity loaded = this.level.getBlockEntity(this.pos);
        if (loaded instanceof ComputingBlockEntity computer) {
            this.blockEntity = computer;
        }
        return this.blockEntity;
    }
}
