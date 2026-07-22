package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.block.entity.TargetControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TargetControllerCeeDevice extends PowerRadarCeeResistiveDevice {
    private TargetControllerBlockEntity blockEntity;

    public TargetControllerCeeDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    @Override
    protected double resistanceOhms() {
        return PowerRadarElectricalParameters.Resistances.targetController();
    }

    @Override
    protected void publishSnapshot() {
        TargetControllerBlockEntity controller = loadedBlockEntity();
        if (controller != null) {
            controller.applyElectricalSnapshot(snapshot());
        }
    }

    public TargetControllerCeeSnapshot snapshot() {
        return new TargetControllerCeeSnapshot(
                voltageVolts(),
                currentAmps(),
                powerWatts());
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
}
