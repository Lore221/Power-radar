package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.limbo2136.powerradar.block.entity.TargetControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TargetControllerCeeDevice extends SimpleElectricalDevice {
    private double powerVoltageVolts;
    private double currentAmps;
    private double powerWatts;
    private TargetControllerBlockEntity blockEntity;

    public TargetControllerCeeDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        bridges.builder(this.pos)
                .resistor(0, 1, PowerRadarCeeConstants.TARGET_CONTROLLER_POWER_RESISTANCE_OHMS);
    }

    @Override
    public void postTick(SimulationResults results) {
        this.powerVoltageVolts = safe(results.getVoltageAt(this.pos, 0, 1));
        this.currentAmps = Math.abs(safe(results.getCurrentThrough(this.pos, 0, 1)));
        this.powerWatts = Math.abs(this.powerVoltageVolts) * this.currentAmps;
        TargetControllerBlockEntity controller = loadedBlockEntity();
        if (controller != null) {
            controller.applyElectricalSnapshot(snapshot());
        }
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        tag.putDouble("PowerVoltage", this.powerVoltageVolts);
        tag.putDouble("CurrentAmps", this.currentAmps);
        tag.putDouble("PowerWatts", this.powerWatts);
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        this.powerVoltageVolts = safe(tag.getDouble("PowerVoltage"));
        this.currentAmps = Math.abs(safe(tag.getDouble("CurrentAmps")));
        this.powerWatts = Math.abs(safe(tag.getDouble("PowerWatts")));
    }

    public TargetControllerCeeSnapshot snapshot() {
        return new TargetControllerCeeSnapshot(
                this.powerVoltageVolts,
                this.currentAmps,
                this.powerWatts);
    }

    private TargetControllerBlockEntity loadedBlockEntity() {
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
        if (loaded instanceof TargetControllerBlockEntity controller) {
            this.blockEntity = controller;
            return controller;
        }
        return null;
    }

    private static double safe(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
