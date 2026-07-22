package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class InterceptionControllerCeeDevice extends PowerRadarCeeResistiveDevice {
    private InterceptionControllerBlockEntity blockEntity;

    public InterceptionControllerCeeDevice(
            Level level,
            BlockPos pos,
            DevicesSavedData devicesSavedData,
            SimulatedDeviceType<?> type
    ) {
        super(level, pos, devicesSavedData, type);
    }

    @Override
    protected double resistanceOhms() {
        return PowerRadarElectricalParameters.Resistances.interceptionController();
    }

    @Override
    protected void publishSnapshot() {
        InterceptionControllerBlockEntity controller = loadedBlockEntity();
        if (controller != null) {
            controller.applyElectricalSnapshot(snapshot());
        }
        if (PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][Interception][CEE] pos={} voltage={} current={} power={} blockEntityLoaded={}",
                    this.pos,
                    round(voltageVolts()),
                    round(currentAmps()),
                    round(powerWatts()),
                    controller != null);
        }
    }

    public InterceptionControllerCeeSnapshot snapshot() {
        return new InterceptionControllerCeeSnapshot(
                voltageVolts(), currentAmps(), powerWatts());
    }

    private InterceptionControllerBlockEntity loadedBlockEntity() {
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
        if (loaded instanceof InterceptionControllerBlockEntity controller) {
            this.blockEntity = controller;
            return controller;
        }
        return null;
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
