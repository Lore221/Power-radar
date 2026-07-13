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
        setLoad(true, PowerRadarCeeConstants.monitorControllerPowerWatts(),
                PowerRadarCeeConstants.OFF_RESISTANCE_OHMS, PowerRadarCeeConstants.monitorNominalVoltage());
    }

    @Override protected double minVoltage() { return PowerRadarCeeConstants.monitorMinVoltage(); }
    @Override protected double restartVoltage() { return PowerRadarCeeConstants.monitorRestartVoltage(); }
    @Override protected double maxVoltage() { return PowerRadarCeeConstants.monitorMaxVoltage(); }
    @Override protected double overvoltageRecoveryVoltage() { return PowerRadarCeeConstants.monitorOvervoltageRecovery(); }

    @Override
    protected void publishSnapshot() {
        ComputingBlockEntity computer = loadedBlockEntity();
        if (computer != null) computer.applyElectricalSnapshot(snapshot());
    }

    private ComputingBlockEntity loadedBlockEntity() {
        if (blockEntity != null && !blockEntity.isRemoved()) return blockEntity;
        blockEntity = null;
        if (!level.isLoaded(pos)) return null;
        BlockEntity loaded = level.getBlockEntity(pos);
        if (loaded instanceof ComputingBlockEntity computer) blockEntity = computer;
        return blockEntity;
    }
}
