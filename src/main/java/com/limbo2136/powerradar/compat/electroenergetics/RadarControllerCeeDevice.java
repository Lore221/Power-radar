package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.radar.RadarStructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RadarControllerCeeDevice extends PowerRadarCeeLoadDevice {
    private RadarControllerBlockEntity blockEntity;

    public RadarControllerCeeDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    public void configureLoad(
            boolean validStructure,
            RadarStructureType structureType,
            int phasedArrayPanelCount,
            int overviewModuleCount
    ) {
        int activeModuleCount = structureType == RadarStructureType.OVERVIEW ? overviewModuleCount : phasedArrayPanelCount;
        if (!validStructure || activeModuleCount <= 0) {
            setLoad(false, 0.0, PowerRadarCeeConstants.OFF_RESISTANCE_OHMS, PowerRadarCeeConstants.radarNominalVoltage());
            return;
        }
        setLoad(
                true,
                PowerRadarCeeConstants.radarConstantPowerWatts(structureType, phasedArrayPanelCount, overviewModuleCount),
                PowerRadarCeeConstants.OFF_RESISTANCE_OHMS,
                PowerRadarCeeConstants.radarNominalVoltage());
    }

    @Override
    protected double minVoltage() {
        return PowerRadarCeeConstants.radarMinVoltage();
    }

    @Override
    protected double restartVoltage() {
        return PowerRadarCeeConstants.radarRestartVoltage();
    }

    @Override
    protected double maxVoltage() {
        return PowerRadarCeeConstants.radarMaxVoltage();
    }

    @Override
    protected double overvoltageRecoveryVoltage() {
        return PowerRadarCeeConstants.radarOvervoltageRecovery();
    }

    @Override
    protected void publishSnapshot() {
        RadarControllerBlockEntity controller = loadedBlockEntity();
        if (controller != null) {
            controller.applyElectricalSnapshot(snapshot());
        }
    }

    private RadarControllerBlockEntity loadedBlockEntity() {
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
        if (loaded instanceof RadarControllerBlockEntity controller) {
            this.blockEntity = controller;
            return controller;
        }
        return null;
    }
}
