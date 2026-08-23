package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Самостоятельная электрическая модель бортового компьютера. */
public final class OnboardComputerCeeDevice extends PowerRadarCeeLoadDevice {
    private OnboardComputerBlockEntity blockEntity;

    public OnboardComputerCeeDevice(
            Level level,
            BlockPos pos,
            DevicesSavedData devicesSavedData,
            SimulatedDeviceType<?> type
    ) {
        super(level, pos, devicesSavedData, type);
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        setLoad(
                true,
                PowerRadarElectricalParameters.Ratings.onboardComputerPowerWatts(),
                PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS,
                voltageRange().nominal());
        super.preTick(bridges);
    }

    @Override
    protected PowerRadarElectricalParameters.LoadVoltageRange voltageRange() {
        return PowerRadarElectricalParameters.Voltages.onboardComputer();
    }

    @Override
    protected void publishSnapshot() {
        OnboardComputerBlockEntity computer = loadedBlockEntity();
        if (computer != null) {
            computer.applyElectricalSnapshot(snapshot());
        }
    }

    private OnboardComputerBlockEntity loadedBlockEntity() {
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
        if (loaded instanceof OnboardComputerBlockEntity computer) {
            this.blockEntity = computer;
            return computer;
        }
        return null;
    }
}
