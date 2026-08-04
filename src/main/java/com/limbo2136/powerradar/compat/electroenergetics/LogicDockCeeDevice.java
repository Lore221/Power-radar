package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.limbo2136.powerradar.block.entity.LogicDockBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class LogicDockCeeDevice extends PowerRadarCeeLoadDevice {
    private LogicDockBlockEntity blockEntity;

    public LogicDockCeeDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    public void configureLoad() {
        setLoad(true, PowerRadarElectricalParameters.Ratings.logicDockPowerWatts(),
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                PowerRadarElectricalParameters.Voltages.logicDock().nominal());
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        // Повторное применение дешёво и подхватывает новый конфиг у уже установленного блока.
        configureLoad();
        super.preTick(bridges);
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.logicDock();
    }

    @Override
    protected void publishSnapshot() {
        LogicDockBlockEntity dock = loadedBlockEntity();
        if (dock != null) {
            dock.applyElectricalSnapshot(snapshot());
        }
    }

    private LogicDockBlockEntity loadedBlockEntity() {
        if (this.blockEntity != null && !this.blockEntity.isRemoved()) {
            return this.blockEntity;
        }
        this.blockEntity = null;
        if (!this.level.isLoaded(this.pos)) {
            return null;
        }
        BlockEntity loaded = this.level.getBlockEntity(this.pos);
        if (loaded instanceof LogicDockBlockEntity dock) {
            this.blockEntity = dock;
        }
        return this.blockEntity;
    }
}
