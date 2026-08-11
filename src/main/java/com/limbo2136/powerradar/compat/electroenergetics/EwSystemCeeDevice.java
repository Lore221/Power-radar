package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.limbo2136.powerradar.block.entity.EwSystemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EwSystemCeeDevice extends PowerRadarCeeLoadDevice {
    private EwSystemBlockEntity blockEntity;

    public EwSystemCeeDevice(Level level, BlockPos pos, DevicesSavedData data, SimulatedDeviceType<?> type) {
        super(level, pos, data, type);
    }

    public void configureLoad() {
        setLoad(true, PowerRadarElectricalParameters.Ratings.ewSystemPowerWatts(),
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                PowerRadarElectricalParameters.Voltages.ewSystem().nominal());
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        configureLoad();
        super.preTick(bridges);
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.ewSystem();
    }

    @Override
    protected void publishSnapshot() {
        EwSystemBlockEntity system = loadedBlockEntity();
        if (system != null) {
            system.applyElectricalSnapshot(snapshot());
        }
    }

    private EwSystemBlockEntity loadedBlockEntity() {
        if (this.blockEntity != null && !this.blockEntity.isRemoved()) {
            return this.blockEntity;
        }
        this.blockEntity = null;
        if (!this.level.isLoaded(this.pos)) {
            return null;
        }
        BlockEntity loaded = this.level.getBlockEntity(this.pos);
        if (loaded instanceof EwSystemBlockEntity system) {
            this.blockEntity = system;
        }
        return this.blockEntity;
    }
}
