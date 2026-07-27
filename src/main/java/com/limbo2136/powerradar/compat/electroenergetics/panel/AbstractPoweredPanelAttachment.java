package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeConstants;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeLoadMath;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeTerminalPair;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Общая двухконтактная нагрузка для модулей Power Radar в щитке CEE. */
public abstract class AbstractPoweredPanelAttachment extends PanelAttachment {
    private double voltageVolts;
    private double currentAmps;
    private double resistanceOhms = PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
    private PowerRadarCeeState electricalState = PowerRadarCeeState.UNDERVOLTAGE;

    protected AbstractPoweredPanelAttachment(PanelAttachmentType type) {
        super(type);
    }

    @Override
    public final void preTick(BridgeCollector bridges) {
        if (this.nodes.length < 2) {
            return;
        }
        this.resistanceOhms = PowerRadarCeeLoadMath.constantPowerResistance(
                this.voltageVolts, powerDrawWatts());
        bridges.bridge(
                this.nodes[PowerRadarCeeTerminalPair.POSITIVE],
                this.nodes[PowerRadarCeeTerminalPair.NEGATIVE],
                ElectricalProperties.resistor(this.resistanceOhms));
    }

    @Override
    public final void postTick(SimulationResults results) {
        if (this.nodes.length < 2) {
            return;
        }
        double previousVoltage = this.voltageVolts;
        PowerRadarCeeState previousState = this.electricalState;
        this.voltageVolts = finite(results.getVoltageAt(this.nodes[0], this.nodes[1]));
        this.currentAmps = Math.abs(finite(results.getCurrentThrough(this.nodes[0], this.nodes[1])));
        this.electricalState = PowerRadarCeeLoadMath.resolveState(
                true,
                previousState,
                this.voltageVolts,
                PowerRadarElectricalParameters.Voltages.monitor());
        afterElectricalTick(results);
        if (previousState != this.electricalState || Math.abs(previousVoltage - this.voltageVolts) >= 0.1D) {
            sendData();
        }
    }

    protected abstract double powerDrawWatts();

    protected void afterElectricalTick(SimulationResults results) {
    }

    public final boolean isElectricallyOperational() {
        return this.electricalState == PowerRadarCeeState.POWERED;
    }

    public final PowerRadarCeeState electricalState() {
        return this.electricalState;
    }

    public final double voltageVolts() {
        return this.voltageVolts;
    }

    public final double currentAmps() {
        return this.currentAmps;
    }

    public final double resistanceOhms() {
        return PowerRadarCeeConstants.sanitizeResistance(this.resistanceOhms);
    }

    @Override
    public MutableComponent getNodeLabel(Level level, BlockPos pos, BlockState state, int nodeIndex) {
        return net.minecraft.network.chat.Component.translatable(
                nodeIndex == PowerRadarCeeTerminalPair.POSITIVE
                        ? "power_radar.cee.node.power_positive"
                        : "power_radar.cee.node.power_negative");
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        this.voltageVolts = finite(tag.getDouble("VoltageVolts"));
        this.currentAmps = Math.abs(finite(tag.getDouble("CurrentAmps")));
        this.resistanceOhms = tag.contains("ResistanceOhms")
                ? PowerRadarCeeConstants.sanitizeResistance(tag.getDouble("ResistanceOhms"))
                : PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS;
        try {
            this.electricalState = PowerRadarCeeState.valueOf(tag.getString("ElectricalState"));
        } catch (IllegalArgumentException exception) {
            this.electricalState = PowerRadarCeeState.UNDERVOLTAGE;
        }
    }

    @Override
    public void write(CompoundTag tag, boolean clientPacket, HolderLookup.Provider registries) {
        tag.putDouble("VoltageVolts", this.voltageVolts);
        tag.putDouble("CurrentAmps", this.currentAmps);
        tag.putDouble("ResistanceOhms", this.resistanceOhms);
        tag.putString("ElectricalState", this.electricalState.name());
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
