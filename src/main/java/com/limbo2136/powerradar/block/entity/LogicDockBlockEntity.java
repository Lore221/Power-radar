package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.logic.LogicDockCardInventory;
import com.limbo2136.powerradar.logic.LogicDockPolicySource;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeSnapshot;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import java.util.List;
import net.minecraft.ChatFormatting;
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
        implements IHaveGoggleInformation, LogicDockPolicySource {
    private final LogicDockCardInventory cards = new LogicDockCardInventory();
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;

    public LogicDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOGIC_DOCK.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
        }
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
        if (this.electricalState != snapshot.electricalState()) {
            this.electricalState = snapshot.electricalState();
            invalidateNetworkPolicyCache();
        }
    }

    public boolean isElectricallyOperational() {
        return this.electricalState == PowerRadarCeeState.POWERED;
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
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        for (PowerRadarTooltipSettings.Line line : PowerRadarTooltipSettings.goggles(Target.LOGIC_DOCK)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            PowerRadarTooltipSettings.GoggleField field = (PowerRadarTooltipSettings.GoggleField) line.field();
            switch (field) {
                case TITLE -> tooltip.add(Component.translatable("goggles.power_radar.logic_dock")
                        .withStyle(ChatFormatting.GOLD));
                case CARD_SLOTS -> {
                    for (int i = 0; i < LogicDockCardInventory.SLOT_COUNT; i++) {
                        ItemStack card = this.cards.card(i);
                        tooltip.add(Component.translatable("goggles.power_radar.logic_dock.slot." + i,
                                card.isEmpty()
                                        ? Component.translatable("goggles.power_radar.logic_dock.empty")
                                        : card.getHoverName()));
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
        return true;
    }

    // Вычисляет сетевой статус только если соответствующая строка включена в раскладке очков.
    private void appendNetworkStatus(List<Component> tooltip, ServerLevel serverLevel) {
        RadarLinkConnectionResolver.Resolution resolution =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(serverLevel, worldPosition);
        if (resolution.status() != RadarLinkConnectionResolver.Status.SINGLE || resolution.link().networkId() == null) {
            tooltip.add(Component.translatable("goggles.power_radar.logic_dock.disconnected"));
            return;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
        if (!manager.controlConsumersAllowed(resolution.link().networkId())) {
            tooltip.add(Component.translatable("goggles.power_radar.logic_dock.onboard_network"));
            return;
        }
        RadarNetworkManager.LogicDockResolution dock = manager
                .resolveLogicDock(resolution.link().networkId());
        tooltip.add(Component.translatable(dock.conflict()
                ? "goggles.power_radar.logic_dock.conflict"
                : "goggles.power_radar.logic_dock.connected"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        this.cards.write(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.cards.read(tag, registries);
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
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        RadarLinkConnectionResolver.Resolution resolution =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(serverLevel, this.worldPosition);
        if (resolution.status() == RadarLinkConnectionResolver.Status.SINGLE
                && resolution.link().networkId() != null) {
            RadarNetworkManager.get(serverLevel.getServer()).invalidateLogicDockCache(resolution.link().networkId());
        }
    }
}
