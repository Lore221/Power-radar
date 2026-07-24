package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ComputingBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final int TARGETING_CARD_SLOT = 0;
    private static final int DISPLAY_CARD_SLOT = 1;
    private static final int ALLOWLIST_CARD_SLOT = 2;

    private final ItemStack[] cards = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private PowerRadarCeeState electricalState = PowerRadarCeeState.INVALID_STRUCTURE;

    public ComputingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPUTING_BLOCK.get(), pos, state);
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
            ComputingBlockEntity computer
    ) {
        if (level instanceof ServerLevel serverLevel) {
            PowerRadarCeeIntegration.configureComputingLoad(serverLevel, pos);
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
        // Порядок Kind является частью раскладки слотов и сохранённых NBT-ключей Card0..Card2.
        int slot = kind.ordinal();
        if (!this.cards[slot].isEmpty()) {
            return false;
        }
        this.cards[slot] = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        cardsChanged();
        return true;
    }

    public void extractCard(Player player, int requestedSlot) {
        int slot = requestedSlot;
        if (slot < 0 || slot >= this.cards.length || this.cards[slot].isEmpty()) {
            slot = firstOccupiedSlot();
        }
        if (slot < 0) {
            return;
        }
        ItemStack extracted = this.cards[slot];
        this.cards[slot] = ItemStack.EMPTY;
        if (!player.addItem(extracted)) {
            player.drop(extracted, false);
        }
        cardsChanged();
    }

    public int targetingMask() {
        if (this.cards[TARGETING_CARD_SLOT].isEmpty()) {
            return 0;
        }
        int selected = RadarFilterCardItem.filterMask(this.cards[TARGETING_CARD_SLOT], 0);
        return RadarFilterCardItem.cardOption(this.cards[TARGETING_CARD_SLOT], 1) == 0
                ? RadarDetectionFilters.DEFAULT_MASK & ~selected
                : selected;
    }

    public int displayMask() {
        if (this.cards[DISPLAY_CARD_SLOT].isEmpty()) {
            return RadarDetectionFilters.DEFAULT_MASK;
        }
        int selected = RadarFilterCardItem.filterMask(this.cards[DISPLAY_CARD_SLOT], 0);
        return RadarFilterCardItem.cardOption(this.cards[DISPLAY_CARD_SLOT], 0) == 0
                ? RadarDetectionFilters.DEFAULT_MASK & ~selected
                : selected;
    }

    public List<String> allowlistedPlayers() {
        if (this.cards[ALLOWLIST_CARD_SLOT].isEmpty() || !allowlistIsWhitelist()) {
            return List.of();
        }
        return allowlistData().playerNames();
    }

    public boolean allowlistIsWhitelist() {
        return this.cards[ALLOWLIST_CARD_SLOT].isEmpty()
                || RadarFilterCardItem.cardOption(this.cards[ALLOWLIST_CARD_SLOT], 1) == 1;
    }

    public List<String> allowlistPlayerNames() {
        return allowlistData().playerNames();
    }

    public List<String> allowlistSableNames() {
        return allowlistData().sableNames();
    }
    public List<String> allowlistedSableNames() {
        return this.cards[ALLOWLIST_CARD_SLOT].isEmpty() || !allowlistIsWhitelist()
                ? List.of()
                : allowlistSableNames();
    }

    private RadarFilterCardItem.AllowlistData allowlistData() {
        return this.cards[ALLOWLIST_CARD_SLOT].isEmpty()
                ? new RadarFilterCardItem.AllowlistData(List.of(), List.of())
                : RadarFilterCardItem.allowlistData(this.cards[ALLOWLIST_CARD_SLOT]);
    }

    public void dropCards() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int i = 0; i < this.cards.length; i++) {
            if (!this.cards[i].isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D, this.cards[i]);
                this.cards[i] = ItemStack.EMPTY;
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        for (PowerRadarTooltipSettings.Line line : PowerRadarTooltipSettings.goggles(Target.COMPUTING_BLOCK)) {
            if (PowerRadarTooltipSettings.appendText(tooltip, line)) {
                continue;
            }
            PowerRadarTooltipSettings.GoggleField field = (PowerRadarTooltipSettings.GoggleField) line.field();
            switch (field) {
                case TITLE -> tooltip.add(Component.translatable("goggles.power_radar.computing_block")
                        .withStyle(ChatFormatting.GOLD));
                case CARD_SLOTS -> {
                    for (int i = 0; i < this.cards.length; i++) {
                        tooltip.add(Component.translatable("goggles.power_radar.computing_block.slot." + i,
                                this.cards[i].isEmpty()
                                        ? Component.translatable("goggles.power_radar.computing_block.empty")
                                        : this.cards[i].getHoverName()));
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
            tooltip.add(Component.translatable("goggles.power_radar.computing_block.disconnected"));
            return;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
        if (!manager.controlConsumersAllowed(resolution.link().networkId())) {
            tooltip.add(Component.translatable("goggles.power_radar.computing_block.onboard_network"));
            return;
        }
        RadarNetworkManager.ComputingResolution computing = manager
                .resolveComputingBlock(resolution.link().networkId());
        tooltip.add(Component.translatable(computing.conflict()
                ? "goggles.power_radar.computing_block.conflict"
                : "goggles.power_radar.computing_block.connected"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < this.cards.length; i++) {
            if (!this.cards[i].isEmpty()) {
                tag.put("Card" + i, this.cards[i].save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < this.cards.length; i++) {
            this.cards[i] = tag.contains("Card" + i)
                    ? ItemStack.parseOptional(registries, tag.getCompound("Card" + i))
                    : ItemStack.EMPTY;
        }
    }

    private int firstOccupiedSlot() {
        for (int i = 0; i < this.cards.length; i++) {
            if (!this.cards[i].isEmpty()) {
                return i;
            }
        }
        return -1;
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
            RadarNetworkManager.get(serverLevel.getServer()).invalidateComputingCache(resolution.link().networkId());
        }
    }
}
