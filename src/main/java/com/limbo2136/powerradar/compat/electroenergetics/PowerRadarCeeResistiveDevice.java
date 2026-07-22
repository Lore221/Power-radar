package com.limbo2136.powerradar.compat.electroenergetics;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Shared CEE implementation for two-terminal blocks with a fixed internal resistance. */
public abstract class PowerRadarCeeResistiveDevice extends SimpleElectricalDevice {
    private double voltageVolts;
    private double currentAmps;
    private double powerWatts;

    protected PowerRadarCeeResistiveDevice(
            Level level,
            BlockPos pos,
            DevicesSavedData devicesSavedData,
            SimulatedDeviceType<?> type
    ) {
        super(level, pos, devicesSavedData, type);
    }

    @Override
    public final void preTick(BridgeCollector bridges) {
        bridges.builder(this.pos).resistor(
                PowerRadarCeeTerminalPair.POSITIVE,
                PowerRadarCeeTerminalPair.NEGATIVE,
                PowerRadarCeeConstants.sanitizeResistance(resistanceOhms()));
    }

    @Override
    public final void postTick(SimulationResults results) {
        this.voltageVolts = safe(results.getVoltageAt(
                this.pos, PowerRadarCeeTerminalPair.POSITIVE, PowerRadarCeeTerminalPair.NEGATIVE));
        this.currentAmps = Math.abs(safe(results.getCurrentThrough(
                this.pos, PowerRadarCeeTerminalPair.POSITIVE, PowerRadarCeeTerminalPair.NEGATIVE)));
        this.powerWatts = Math.abs(this.voltageVolts) * this.currentAmps;
        publishSnapshot();
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        tag.putDouble("PowerVoltage", this.voltageVolts);
        tag.putDouble("CurrentAmps", this.currentAmps);
        tag.putDouble("PowerWatts", this.powerWatts);
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        this.voltageVolts = safe(tag.getDouble("PowerVoltage"));
        this.currentAmps = Math.abs(safe(tag.getDouble("CurrentAmps")));
        this.powerWatts = Math.abs(safe(tag.getDouble("PowerWatts")));
    }

    protected final double voltageVolts() {
        return this.voltageVolts;
    }

    protected final double currentAmps() {
        return this.currentAmps;
    }

    protected final double powerWatts() {
        return this.powerWatts;
    }

    protected abstract double resistanceOhms();

    protected abstract void publishSnapshot();

    private static double safe(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
