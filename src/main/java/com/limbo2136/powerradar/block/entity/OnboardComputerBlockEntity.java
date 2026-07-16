package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.block.OnboardComputerBlock;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureNames;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeFormatter;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.item.NameCardItem;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class OnboardComputerBlockEntity extends RadarMonitorControllerBlockEntity {
    private static final String NETWORK_TAG = "NetworkId";
    private static final String CARD_TAG = "NameCard";
    private static final String ASSIGNED_STRUCTURE_TAG = "AssignedStructureId";
    @Nullable private UUID networkId;
    @Nullable private UUID assignedStructureUuid;
    private ItemStack nameCard = ItemStack.EMPTY;

    public OnboardComputerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ONBOARD_COMPUTER.get(), pos, state);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, OnboardComputerBlockEntity computer) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (computer.networkId == null) computer.initializeNetwork(RadarNetworkManager.get(serverLevel.getServer()).createNetwork());
        computer.updateDisplayStructure(pos, 1, state.getValue(OnboardComputerBlock.FACING),
                RadarDisplayStructureResolver.StructureStatus.ACTIVE);
        RadarMonitorControllerBlockEntity.tick(serverLevel, pos, state, computer);
    }

    public UUID ensureNetworkId() {
        if (this.networkId == null && this.level instanceof ServerLevel serverLevel) {
            initializeNetwork(RadarNetworkManager.get(serverLevel.getServer()).createNetwork());
        }
        return this.networkId;
    }

    @Nullable public UUID networkId() { return this.networkId; }

    public void initializeNetwork(UUID id) {
        if (id == null) return;
        UUID oldId = this.networkId;
        this.networkId = id;
        RadarNetworkNodeClientCacheBridge.onNetworkChanged(this.level, this.worldPosition, oldId, id);
        setChanged();
        sendData();
    }

    @Override public void onLoad() {
        super.onLoad();
        RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
        refreshStructureName();
    }

    @Override public void clearRemoved() {
        super.clearRemoved();
        RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
    }

    @Override public void remove() {
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.remove();
    }

    @Override public void onChunkUnloaded() {
        RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        super.onChunkUnloaded();
    }

    public ItemStack nameCard() { return this.nameCard; }

    public boolean insertNameCard(ItemStack held) {
        if (!this.nameCard.isEmpty() || !(held.getItem() instanceof NameCardItem)) return false;
        this.nameCard = held.copyWithCount(1);
        held.shrink(1);
        setChanged();
        refreshStructureName();
        sendData();
        return true;
    }

    public ItemStack removeNameCard() {
        ItemStack result = this.nameCard;
        this.nameCard = ItemStack.EMPTY;
        setChanged();
        refreshStructureName();
        sendData();
        return result;
    }

    public void clearStructureName() {
        if (this.assignedStructureUuid != null && this.level instanceof ServerLevel serverLevel) {
            SableStructureNames.remove(serverLevel.getServer(), this.assignedStructureUuid);
            this.assignedStructureUuid = null;
            setChanged();
        }
    }

    private void refreshStructureName() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        String name = NameCardItem.name(this.nameCard).trim();
        UUID desiredStructure = isElectricallyOperational() && !name.isEmpty()
                ? SableRadarIntegration.containingStructureUuid(serverLevel, this.worldPosition).orElse(null)
                : null;
        if (this.assignedStructureUuid != null && !this.assignedStructureUuid.equals(desiredStructure)) {
            SableStructureNames.remove(serverLevel.getServer(), this.assignedStructureUuid);
            this.assignedStructureUuid = null;
        }
        if (desiredStructure != null) {
            SableStructureNames.assign(serverLevel.getServer(), desiredStructure, name);
            this.assignedStructureUuid = desiredStructure;
        }
        setChanged();
    }

    @Override public boolean applyElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        boolean wasPowered = isElectricallyOperational();
        boolean changed = super.applyElectricalSnapshot(snapshot);
        if (wasPowered != isElectricallyOperational()) refreshStructureName();
        return changed;
    }

    @Override public void updateDisplayStructure(@Nullable BlockPos origin, int size,
            net.minecraft.core.Direction facing, RadarDisplayStructureResolver.StructureStatus status) {
        PowerRadarCeeState previousState = electricalState();
        super.updateDisplayStructure(origin, size, facing, status);
        if (previousState != electricalState()) refreshStructureName();
    }

    @Override protected UUID directNetworkId() { return this.networkId; }
    @Override protected boolean usesDisplayStructureResolver() { return false; }

    @Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.onboard_computer")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("power_radar.electrical.state",
                Component.translatable(electricalState().translationKey())));
        tooltip.add(Component.translatable("power_radar.electrical.voltage",
                PowerRadarCeeFormatter.voltageComponent(electricalVoltageVolts())));
        tooltip.add(Component.translatable("power_radar.electrical.power",
                PowerRadarCeeFormatter.powerComponent(electricalPowerWatts())));
        return true;
    }

    @Override protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (this.networkId != null) tag.putUUID(NETWORK_TAG, this.networkId);
        if (this.assignedStructureUuid != null) tag.putUUID(ASSIGNED_STRUCTURE_TAG, this.assignedStructureUuid);
        if (!this.nameCard.isEmpty()) tag.put(CARD_TAG, this.nameCard.save(registries));
    }

    @Override protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        UUID oldNetworkId = this.networkId;
        super.read(tag, registries, clientPacket);
        this.networkId = tag.hasUUID(NETWORK_TAG) ? tag.getUUID(NETWORK_TAG) : null;
        if (clientPacket) {
            RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                    this.level, this.worldPosition, oldNetworkId, this.networkId);
        }
        this.assignedStructureUuid = tag.hasUUID(ASSIGNED_STRUCTURE_TAG)
                ? tag.getUUID(ASSIGNED_STRUCTURE_TAG) : null;
        this.nameCard = tag.contains(CARD_TAG) ? ItemStack.parseOptional(registries, tag.getCompound(CARD_TAG)) : ItemStack.EMPTY;
    }
}
