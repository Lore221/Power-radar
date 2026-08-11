package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.compat.aeronautics.EwSystemManager;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EwSystemBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final int RECONCILE_INTERVAL_TICKS = 200;

    private final UUID registrationToken = UUID.randomUUID();
    private PowerRadarCeeSnapshot electrical = PowerRadarCeeSnapshot.EMPTY;
    private boolean hasFreshElectricalSnapshot;
    private volatile boolean registrationDirty = true;
    @Nullable
    private ResourceLocation registeredDimensionId;
    @Nullable
    private UUID registeredStructureUuid;

    public EwSystemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EW_SYSTEM.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.registrationDirty = true;
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
    }

    public static void serverTick(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            BlockState state,
            EwSystemBlockEntity system
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PowerRadarCeeIntegration.configureEwSystemLoad(serverLevel, pos);
        long offset = Math.floorMod(pos.asLong(), RECONCILE_INTERVAL_TICKS);
        if (system.registrationDirty
                || Math.floorMod(serverLevel.getGameTime() + offset, RECONCILE_INTERVAL_TICKS) == 0L) {
            system.reconcileRegistration(serverLevel);
        }
    }

    public void applyElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        PowerRadarCeeSnapshot next = snapshot == null ? PowerRadarCeeSnapshot.EMPTY : snapshot;
        PowerRadarCeeSnapshot previous = this.electrical;
        this.electrical = next;
        this.hasFreshElectricalSnapshot = true;
        boolean stateChanged = previous.electricalState() != next.electricalState();
        boolean displayChanged = stateChanged
                || Math.abs(previous.voltageVolts() - next.voltageVolts()) > 0.01D
                || Math.abs(previous.currentAmps() - next.currentAmps()) > 0.001D
                || Math.abs(previous.powerWatts() - next.powerWatts()) > 0.1D;
        if (stateChanged) {
            this.registrationDirty = true;
        }
        if (displayChanged) {
            setChanged();
            if (this.level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("ElectricalBridgeEnabled", this.electrical.bridgeEnabled());
        tag.putString("ElectricalState", this.electrical.electricalState().name());
        tag.putDouble("ElectricalVoltageVolts", this.electrical.voltageVolts());
        tag.putDouble("ElectricalCurrentAmps", this.electrical.currentAmps());
        tag.putDouble("ElectricalPowerWatts", this.electrical.powerWatts());
        tag.putDouble("ElectricalResistanceOhms", this.electrical.resistanceOhms());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        PowerRadarCeeState state;
        try {
            state = PowerRadarCeeState.valueOf(tag.getString("ElectricalState"));
        } catch (IllegalArgumentException exception) {
            state = PowerRadarCeeState.INVALID_STRUCTURE;
        }
        this.electrical = new PowerRadarCeeSnapshot(
                tag.getBoolean("ElectricalBridgeEnabled"),
                state,
                finite(tag.getDouble("ElectricalVoltageVolts")),
                Math.abs(finite(tag.getDouble("ElectricalCurrentAmps"))),
                Math.max(0.0D, finite(tag.getDouble("ElectricalPowerWatts"))),
                finite(tag.getDouble("ElectricalResistanceOhms")));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        this.registrationDirty = true;
    }

    @Override
    public void setRemoved() {
        unregisterCurrent();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unregisterCurrent();
        super.onChunkUnloaded();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        int firstNewLine = tooltip.size();
        for (PowerRadarTooltipSettings.Line line : PowerRadarTooltipSettings.goggles(Target.EW_SYSTEM)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            PowerRadarTooltipSettings.GoggleField field = (PowerRadarTooltipSettings.GoggleField) line.field();
            switch (field) {
                case TITLE -> PowerRadarTooltipSettings.appendElectricalStatisticsTitle(tooltip);
                case ELECTRICAL_STATE -> tooltip.add(Component.translatable(
                        "power_radar.electrical.state",
                        Component.translatable(this.electrical.electricalState().translationKey())));
                case VOLTAGE -> tooltip.add(Component.translatable(
                        "power_radar.electrical.voltage",
                        PowerRadarCeeFormatter.voltageComponent(this.electrical.voltageVolts())));
                case POWER -> tooltip.add(Component.translatable(
                        "power_radar.electrical.power",
                        PowerRadarCeeFormatter.powerComponent(this.electrical.powerWatts())));
                default -> { }
            }
        }
        return PowerRadarTooltipSettings.finishGoggleTooltip(tooltip, firstNewLine);
    }

    private void reconcileRegistration(ServerLevel level) {
        this.registrationDirty = false;
        UUID structureUuid = this.hasFreshElectricalSnapshot
                && this.electrical.electricalState() == PowerRadarCeeState.POWERED
                ? SableRadarIntegration.containingStructureUuid(level, this.worldPosition).orElse(null)
                : null;
        ResourceLocation dimensionId = structureUuid == null ? null : level.dimension().location();
        if (java.util.Objects.equals(structureUuid, this.registeredStructureUuid)
                && java.util.Objects.equals(dimensionId, this.registeredDimensionId)) {
            return;
        }
        unregisterCurrent();
        if (structureUuid != null) {
            EwSystemManager.register(level.getServer(), dimensionId, structureUuid, this.registrationToken);
            this.registeredDimensionId = dimensionId;
            this.registeredStructureUuid = structureUuid;
        }
    }

    private void unregisterCurrent() {
        if (this.registeredDimensionId == null || this.registeredStructureUuid == null
                || !(this.level instanceof ServerLevel serverLevel)) {
            this.registeredDimensionId = null;
            this.registeredStructureUuid = null;
            return;
        }
        EwSystemManager.unregister(
                serverLevel.getServer(),
                this.registeredDimensionId,
                this.registeredStructureUuid,
                this.registrationToken);
        this.registeredDimensionId = null;
        this.registeredStructureUuid = null;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
