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
        setLoad(true, PowerRadarCeeConstants.SHELL_ALARM_POWER_WATTS,
                PowerRadarCeeConstants.OFF_RESISTANCE_OHMS,
                PowerRadarCeeConstants.SHELL_ALARM_NOMINAL_VOLTAGE);
        super.preTick(bridges);
    }

    @Override
    protected double minVoltage() {
        return PowerRadarCeeConstants.SHELL_ALARM_MIN_VOLTAGE;
    }

    @Override
    protected double restartVoltage() {
        return PowerRadarCeeConstants.SHELL_ALARM_RESTART_VOLTAGE;
    }

    @Override
    protected double maxVoltage() {
        return PowerRadarCeeConstants.SHELL_ALARM_MAX_VOLTAGE;
    }

    @Override
    protected double overvoltageRecoveryVoltage() {
        return PowerRadarCeeConstants.SHELL_ALARM_OVERVOLTAGE_RECOVERY;
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
