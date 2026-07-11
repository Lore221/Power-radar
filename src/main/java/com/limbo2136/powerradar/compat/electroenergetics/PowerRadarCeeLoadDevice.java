package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public abstract class PowerRadarCeeLoadDevice extends SimpleElectricalDevice {
    private boolean bridgeEnabled;
    private double resistanceOhms = PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
    private double constantPowerWatts;
    private double parallelResistanceOhms = PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
    private double nominalVoltageVolts = 1.0;
    private double voltageVolts;
    private double currentAmps;
    private double powerWatts;
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;

    protected PowerRadarCeeLoadDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    protected final void setLoad(boolean enabled, double constantPowerWatts, double parallelResistanceOhms, double nominalVoltageVolts) {
        double safePower = Double.isFinite(constantPowerWatts) ? Math.max(0.0, constantPowerWatts) : 0.0;
        double safeParallel = PowerRadarCeeConstants.sanitizeResistance(parallelResistanceOhms);
        double safeNominal = Double.isFinite(nominalVoltageVolts) && nominalVoltageVolts > 0.0 ? nominalVoltageVolts : 1.0;
        boolean changed = this.bridgeEnabled != enabled
                || Double.compare(this.constantPowerWatts, safePower) != 0
                || Double.compare(this.parallelResistanceOhms, safeParallel) != 0
                || Double.compare(this.nominalVoltageVolts, safeNominal) != 0;
        this.bridgeEnabled = enabled;
        this.constantPowerWatts = safePower;
        this.parallelResistanceOhms = safeParallel;
        this.nominalVoltageVolts = safeNominal;
        if (!enabled) {
            this.electricalState = PowerRadarCeeState.INVALID_STRUCTURE;
        }
        if (changed) {
            update();
        }
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        if (this.bridgeEnabled) {
            this.resistanceOhms = calculateResistanceOhms();
            bridges.builder(this.pos).resistor(0, 1, this.resistanceOhms);
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        this.voltageVolts = safe(results.getVoltageAt(this.pos, 0, 1));
        this.currentAmps = Math.abs(safe(results.getCurrentThrough(this.pos, 0, 1)));
        this.powerWatts = Math.abs(this.voltageVolts) * this.currentAmps;
        this.electricalState = resolveElectricalState(this.voltageVolts);
        publishSnapshot();
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        tag.putBoolean("BridgeEnabled", this.bridgeEnabled);
        tag.putDouble("ResistanceOhms", this.resistanceOhms);
        tag.putDouble("ConstantPowerWatts", this.constantPowerWatts);
        tag.putDouble("ParallelResistanceOhms", this.parallelResistanceOhms);
        tag.putDouble("NominalVoltageVolts", this.nominalVoltageVolts);
        tag.putDouble("VoltageVolts", this.voltageVolts);
        tag.putDouble("CurrentAmps", this.currentAmps);
        tag.putDouble("PowerWatts", this.powerWatts);
        tag.putString("ElectricalState", this.electricalState.name());
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        this.bridgeEnabled = tag.getBoolean("BridgeEnabled");
        this.resistanceOhms = tag.contains("ResistanceOhms") ? PowerRadarCeeConstants.sanitizeResistance(tag.getDouble("ResistanceOhms")) : PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
        this.constantPowerWatts = safe(tag.getDouble("ConstantPowerWatts"));
        this.parallelResistanceOhms = tag.contains("ParallelResistanceOhms")
                ? PowerRadarCeeConstants.sanitizeResistance(tag.getDouble("ParallelResistanceOhms"))
                : PowerRadarCeeConstants.OFF_RESISTANCE_OHMS;
        this.nominalVoltageVolts = tag.contains("NominalVoltageVolts") && tag.getDouble("NominalVoltageVolts") > 0.0
                ? safe(tag.getDouble("NominalVoltageVolts"))
                : 1.0;
        this.voltageVolts = safe(tag.getDouble("VoltageVolts"));
        this.currentAmps = Math.abs(safe(tag.getDouble("CurrentAmps")));
        this.powerWatts = Math.abs(safe(tag.getDouble("PowerWatts")));
        try {
            this.electricalState = PowerRadarCeeState.valueOf(tag.getString("ElectricalState"));
        } catch (IllegalArgumentException exception) {
            this.electricalState = this.bridgeEnabled ? PowerRadarCeeState.UNDERVOLTAGE : PowerRadarCeeState.INVALID_STRUCTURE;
        }
    }

    public final PowerRadarCeeSnapshot snapshot() {
        return new PowerRadarCeeSnapshot(
                this.bridgeEnabled,
                this.electricalState,
                this.voltageVolts,
                this.currentAmps,
                this.powerWatts,
                this.resistanceOhms);
    }

    protected void publishSnapshot() {
    }

    protected abstract double minVoltage();

    protected abstract double restartVoltage();

    protected abstract double maxVoltage();

    protected abstract double overvoltageRecoveryVoltage();

    private PowerRadarCeeState resolveElectricalState(double voltage) {
        if (!this.bridgeEnabled) {
            return PowerRadarCeeState.INVALID_STRUCTURE;
        }
        if (!Double.isFinite(voltage)) {
            voltage = 0.0;
        }
        if (voltage < -0.001D) {
            return PowerRadarCeeState.REVERSE_POLARITY;
        }
        if (this.electricalState == PowerRadarCeeState.OVERVOLTAGE) {
            return voltage <= overvoltageRecoveryVoltage()
                    ? PowerRadarCeeState.POWERED
                    : PowerRadarCeeState.OVERVOLTAGE;
        }
        if (voltage > maxVoltage()) {
            return PowerRadarCeeState.OVERVOLTAGE;
        }
        if (this.electricalState == PowerRadarCeeState.UNDERVOLTAGE || this.electricalState == PowerRadarCeeState.INVALID_STRUCTURE) {
            return voltage >= restartVoltage()
                    ? PowerRadarCeeState.POWERED
                    : PowerRadarCeeState.UNDERVOLTAGE;
        }
        return voltage >= minVoltage()
                ? PowerRadarCeeState.POWERED
                : PowerRadarCeeState.UNDERVOLTAGE;
    }

    private double calculateResistanceOhms() {
        double constantPowerResistance = PowerRadarCeeConstants.constantPowerResistanceOhms(
                this.voltageVolts,
                this.nominalVoltageVolts,
                this.constantPowerWatts);
        if (this.parallelResistanceOhms >= PowerRadarCeeConstants.OFF_RESISTANCE_OHMS) {
            return constantPowerResistance;
        }
        return PowerRadarCeeConstants.parallelResistanceOhms(constantPowerResistance, this.parallelResistanceOhms);
    }

    private static double safe(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
