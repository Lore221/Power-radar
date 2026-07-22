package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ShellAlarmCeeDevice extends PowerRadarCeeLoadDevice {
    private ShellAlarmBlockEntity blockEntity;

    public ShellAlarmCeeDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        setLoad(true, PowerRadarElectricalParameters.Ratings.shellAlarmPowerWatts(),
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                PowerRadarElectricalParameters.Voltages.shellAlarm().nominal());
        super.preTick(bridges);
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.shellAlarm();
    }

    @Override
    protected void publishSnapshot() {
        ShellAlarmBlockEntity alarm = loadedBlockEntity();
        if (alarm != null) {
            alarm.applyElectricalSnapshot(snapshot());
        }
    }

    private ShellAlarmBlockEntity loadedBlockEntity() {
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
        if (loaded instanceof ShellAlarmBlockEntity alarm) {
            this.blockEntity = alarm;
            return alarm;
        }
        return null;
    }
}
