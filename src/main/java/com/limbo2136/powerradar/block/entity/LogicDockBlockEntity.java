package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.logic.LogicDockCardInventory;
import com.limbo2136.powerradar.logic.LogicDockPolicySource;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.radar.network.RadarNetworkMember;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarElectricalParameters;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LogicDockBlockEntity extends BlockEntity
        implements IHaveGoggleInformation, LogicDockPolicySource, RadarNetworkMember {
    private final LogicDockCardInventory cards = new LogicDockCardInventory();
    private PowerRadarCeeSnapshot electrical = PowerRadarCeeSnapshot.EMPTY;
    @Nullable
    private UUID networkId;

    public LogicDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGIC_DOCK.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            if (this.networkId != null) {
                RadarNetworkManager.get(serverLevel.getServer()).loadLogicDock(
                        this.networkId, net.minecraft.core.GlobalPos.of(serverLevel.dimension(), this.worldPosition));
            }
            serverLevel.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
        RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
    }

    public static void serverTick(
            net.minecraft.world.level.Level level,
            BlockPos pos,
            BlockState state,
            LogicDockBlockEntity dock
    ) {
        if (level instanceof ServerLevel serverLevel) {
            PowerRadarCeeIntegration.configureLogicDockLoad(serverLevel, pos);
        }
    }

    public void applyElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        PowerRadarCeeSnapshot previous = this.electrical;
        this.electrical = snapshot == null ? PowerRadarCeeSnapshot.EMPTY : snapshot;
        boolean stateChanged = previous.electricalState() != this.electrical.electricalState();
        boolean displayChanged = stateChanged
                || Math.abs(previous.voltageVolts() - this.electrical.voltageVolts()) > 0.01D
                || Math.abs(previous.currentAmps() - this.electrical.currentAmps()) > 0.001D
                || Math.abs(previous.powerWatts() - this.electrical.powerWatts()) > 0.1D;
        if (stateChanged) {
            invalidateNetworkPolicyCache();
        }
        if (displayChanged) {
            setChanged();
            if (this.level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    public boolean isElectricallyOperational() {
        return this.electrical.electricalState() == PowerRadarCeeState.POWERED;
    }

    public boolean insertCard(RadarFilterCardItem.Kind kind, ItemStack held, Player player) {
        if (!this.cards.insert(kind, held, player)) {
            return false;
        }
        cardsChanged();
        return true;
    }

    public boolean hasCard(int slot) {
        return this.cards.hasCard(slot);
    }

    public void extractCard(Player player, int requestedSlot) {
        ItemStack extracted = this.cards.extract(requestedSlot);
        if (extracted.isEmpty()) {
            return;
        }
        if (!player.addItem(extracted)) {
            player.drop(extracted, false);
        }
        cardsChanged();
    }

    public int targetingMask() {
        return this.cards.targetingMask();
    }

    public int displayMask() {
        return this.cards.displayMask();
    }

    public List<String> allowlistedPlayers() {
        return this.cards.allowlistedPlayers();
    }

    public boolean allowlistIsWhitelist() {
        return this.cards.allowlistIsWhitelist();
    }

    public List<String> allowlistPlayerNames() {
        return this.cards.allowlistPlayerNames();
    }

    public List<String> allowlistSableNames() {
        return this.cards.allowlistSableNames();
    }

    public List<String> allowlistedSableNames() {
        return this.cards.allowlistedSableNames();
    }

    public void dropCards() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int i = 0; i < LogicDockCardInventory.SLOT_COUNT; i++) {
            ItemStack card = this.cards.removeExact(i);
            if (!card.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D, card);
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        unregisterLogicDock();
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unregisterLogicDock();
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.setRemoved();
    }

    @Override
    @Nullable
    public UUID radarNetworkId() {
        return this.networkId;
    }

    @Override
    public void setRadarNetworkId(@Nullable UUID networkId) {
        if (java.util.Objects.equals(this.networkId, networkId)) {
            return;
        }
        UUID oldNetworkId = this.networkId;
        if (this.level instanceof ServerLevel serverLevel && oldNetworkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).unloadLogicDock(
                    oldNetworkId, net.minecraft.core.GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
        this.networkId = networkId;
        if (this.level instanceof ServerLevel serverLevel && networkId != null) {
            RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
            manager.loadLogicDock(networkId,
                    net.minecraft.core.GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
        RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldNetworkId, networkId);
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void invalidateConnectedNetworks() {
        if (this.level instanceof ServerLevel serverLevel) {
            RadarNetworkManager.get(serverLevel.getServer())
                    .invalidateLogicDockCachesAt(serverLevel, this.worldPosition);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        int firstNewLine = tooltip.size();
        for (PowerRadarTooltipSettings.Line line : PowerRadarTooltipSettings.goggles(Target.LOGIC_DOCK)) {
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
                case CURRENT -> tooltip.add(Component.translatable(
                        "power_radar.electrical.current",
                        PowerRadarCeeFormatter.currentComponent(this.electrical.currentAmps())));
                case POWER -> tooltip.add(Component.translatable(
                        "power_radar.electrical.power",
                        PowerRadarCeeFormatter.powerComponent(this.electrical.powerWatts())));
                case CARD_SLOTS -> {
                    for (int i = 0; i < LogicDockCardInventory.SLOT_COUNT; i++) {
                        ItemStack card = this.cards.card(i);
                        tooltip.add(Component.translatable("goggles.power_radar.logic_dock.slot." + i,
                                card.isEmpty()
                                        ? Component.translatable("goggles.power_radar.logic_dock.empty")
                                        : Component.translatable("goggles.power_radar.logic_dock.inserted")));
                    }
                }
                case NETWORK_STATUS -> {
                    if (level instanceof ServerLevel serverLevel) {
                        appendNetworkStatus(tooltip, serverLevel);
                    }
                }
                default -> { }
            }
        }
        return PowerRadarTooltipSettings.finishGoggleTooltip(tooltip, firstNewLine);
    }

    // Вычисляет сетевой статус только если соответствующая строка включена в раскладке очков.
    private void appendNetworkStatus(List<Component> tooltip, ServerLevel serverLevel) {
        if (this.networkId == null) {
            tooltip.add(Component.translatable("goggles.power_radar.logic_dock.disconnected"));
            return;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
        RadarNetworkManager.LogicDockResolution dock = manager.resolveLogicDock(this.networkId);
        tooltip.add(Component.translatable(dock.conflict()
                ? "goggles.power_radar.logic_dock.conflict"
                : "goggles.power_radar.logic_dock.connected"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.cards.write(tag, registries);
        tag.putBoolean("ElectricalBridgeEnabled", this.electrical.bridgeEnabled());
        tag.putString("ElectricalState", this.electrical.electricalState().name());
        tag.putDouble("ElectricalVoltageVolts", this.electrical.voltageVolts());
        tag.putDouble("ElectricalCurrentAmps", this.electrical.currentAmps());
        tag.putDouble("ElectricalPowerWatts", this.electrical.powerWatts());
        tag.putDouble("ElectricalResistanceOhms", this.electrical.resistanceOhms());
        if (this.networkId != null) {
            tag.putUUID("PowerRadarNetworkId", this.networkId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        UUID oldNetworkId = this.networkId;
        super.loadAdditional(tag, registries);
        this.cards.read(tag, registries);
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
                tag.contains("ElectricalResistanceOhms")
                        ? tag.getDouble("ElectricalResistanceOhms")
                        : PowerRadarElectricalParameters.OFF_RESISTANCE_OHMS);
        this.networkId = tag.hasUUID("PowerRadarNetworkId")
                ? tag.getUUID("PowerRadarNetworkId")
                : null;
        RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldNetworkId, this.networkId);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    private void cardsChanged() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            invalidateNetworkPolicyCache();
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void invalidateNetworkPolicyCache() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.networkId == null) {
            return;
        }
        RadarNetworkManager.get(serverLevel.getServer()).invalidateLogicDockCache(this.networkId);
    }

    private void unregisterLogicDock() {
        if (this.level instanceof ServerLevel serverLevel && this.networkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).unloadLogicDock(
                    this.networkId, net.minecraft.core.GlobalPos.of(serverLevel.dimension(), this.worldPosition));
        }
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0D;
    }
}
