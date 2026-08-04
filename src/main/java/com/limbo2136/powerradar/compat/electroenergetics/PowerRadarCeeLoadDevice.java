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
    private double resistanceOhms = PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
    private double nominalPowerWatts;
    private double parallelResistanceOhms = PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
    private double nominalVoltageVolts = 1.0;
    private double voltageVolts;
    private double currentAmps;
    private double powerWatts;
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;

    protected PowerRadarCeeLoadDevice(Level level, BlockPos pos, DevicesSavedData devicesSavedData, SimulatedDeviceType<?> type) {
        super(level, pos, devicesSavedData, type);
    }

    protected final void setLoad(
            boolean enabled,
            double nominalPowerWatts,
            double parallelResistanceOhms,
            double nominalVoltageVolts
    ) {
        double safePower = Double.isFinite(nominalPowerWatts) ? Math.max(0.0, nominalPowerWatts) : 0.0;
        double safeParallel = PowerRadarCeeConstants.sanitizeResistance(parallelResistanceOhms);
        double safeNominal = Double.isFinite(nominalVoltageVolts) && nominalVoltageVolts > 0.0 ? nominalVoltageVolts : 1.0;
        boolean changed = this.bridgeEnabled != enabled
                || Double.compare(this.nominalPowerWatts, safePower) != 0
                || Double.compare(this.parallelResistanceOhms, safeParallel) != 0
                || Double.compare(this.nominalVoltageVolts, safeNominal) != 0;
        this.bridgeEnabled = enabled;
        this.nominalPowerWatts = safePower;
        this.parallelResistanceOhms = safeParallel;
        this.nominalVoltageVolts = safeNominal;
        if (!enabled) {
            this.electricalState = PowerRadarCeeState.INVALID_STRUCTURE;
        }
        if (changed) {
            this.resistanceOhms = calculateResistanceOhms();
            update();
        }
    }

    @Override
    public void preTick(BridgeCollector bridges) {
        if (this.bridgeEnabled) {
            // В выключенном состоянии остаётся только высокоомная цепь контроля напряжения.
            double connectedResistance = this.electricalState == PowerRadarCeeState.POWERED
                    ? this.resistanceOhms
                    : PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
            bridges.builder(this.pos).resistor(
                    PowerRadarCeeTerminalPair.POSITIVE,
                    PowerRadarCeeTerminalPair.NEGATIVE,
                    connectedResistance);
        }
    }

    @Override
    public void postTick(SimulationResults results) {
        this.voltageVolts = safe(results.getVoltageAt(
                this.pos, PowerRadarCeeTerminalPair.POSITIVE, PowerRadarCeeTerminalPair.NEGATIVE));
        double measuredCurrent = Math.abs(safe(results.getCurrentThrough(
                this.pos, PowerRadarCeeTerminalPair.POSITIVE, PowerRadarCeeTerminalPair.NEGATIVE)));
        this.electricalState = resolveElectricalState(this.voltageVolts);
        // Защита размыкает рабочую нагрузку со следующего расчёта сети; в снимке отключение видно сразу.
        this.currentAmps = this.electricalState == PowerRadarCeeState.POWERED ? measuredCurrent : 0.0D;
        this.powerWatts = Math.abs(this.voltageVolts) * this.currentAmps;
        publishSnapshot();
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        // Имена полей уже сохраняются в мире через CEE и являются совместимым форматом устройства.
        tag.putBoolean("BridgeEnabled", this.bridgeEnabled);
        tag.putDouble("ResistanceOhms", this.resistanceOhms);
        // Старый ключ сохраняется ради совместимости миров; теперь это паспортная мощность при NominalVoltageVolts.
        tag.putDouble("ConstantPowerWatts", this.nominalPowerWatts);
        tag.putDouble("NominalPowerWatts", this.nominalPowerWatts);
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
        this.nominalPowerWatts = tag.contains("NominalPowerWatts")
                ? Math.max(0.0D, safe(tag.getDouble("NominalPowerWatts")))
                : Math.max(0.0D, safe(tag.getDouble("ConstantPowerWatts")));
        this.parallelResistanceOhms = tag.contains("ParallelResistanceOhms")
                ? PowerRadarCeeConstants.sanitizeResistance(tag.getDouble("ParallelResistanceOhms"))
                : PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
        this.nominalVoltageVolts = tag.contains("NominalVoltageVolts") && tag.getDouble("NominalVoltageVolts") > 0.0
                ? safe(tag.getDouble("NominalVoltageVolts"))
                : 1.0;
        // Старые сохранения содержат сопротивление прежней модели постоянной мощности.
        // Пересчёт из паспортных значений сразу переводит их на новую фиксированную нагрузку.
        this.resistanceOhms = calculateResistanceOhms();
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

    protected abstract PowerRadarElectricalParameters.LoadVoltageRange voltageRange();

    private PowerRadarCeeState resolveElectricalState(double voltage) {
        return PowerRadarCeeLoadMath.resolveState(
                this.bridgeEnabled, voltage, voltageRange());
    }

    private double calculateResistanceOhms() {
        double nominalResistance = PowerRadarCeeConstants.nominalResistanceOhms(
                this.nominalVoltageVolts,
                this.nominalPowerWatts);
        if (this.parallelResistanceOhms >= PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS) {
            return nominalResistance;
        }
        return PowerRadarCeeConstants.parallelResistanceOhms(nominalResistance, this.parallelResistanceOhms);
    }

    private static double safe(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
