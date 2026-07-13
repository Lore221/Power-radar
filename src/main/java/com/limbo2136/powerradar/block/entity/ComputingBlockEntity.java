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
    private final ItemStack[] cards = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
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

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, ComputingBlockEntity computer) {
        if (level instanceof ServerLevel serverLevel) PowerRadarCeeIntegration.configureComputingLoad(serverLevel, pos);
    }

    public void applyElectricalSnapshot(PowerRadarCeeSnapshot snapshot) {
        if (this.electricalState != snapshot.electricalState()) {
            this.electricalState = snapshot.electricalState();
            invalidateNetworkPolicyCache();
        }
    }

    public boolean isElectricallyOperational() { return this.electricalState == PowerRadarCeeState.POWERED; }

    public boolean insertCard(RadarFilterCardItem.Kind kind, ItemStack held, Player player) {
        int slot = kind.ordinal();
        if (!cards[slot].isEmpty()) {
            return false;
        }
        cards[slot] = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        changed();
        return true;
    }

    public void extractCard(Player player, int requestedSlot) {
        int slot = requestedSlot;
        if (slot < 0 || slot >= cards.length || cards[slot].isEmpty()) {
            slot = firstOccupiedSlot();
        }
        if (slot < 0) {
            return;
        }
        ItemStack extracted = cards[slot];
        cards[slot] = ItemStack.EMPTY;
        if (!player.addItem(extracted)) {
            player.drop(extracted, false);
        }
        changed();
    }

    public int targetingMask() {
        if (cards[0].isEmpty()) {
            return 0;
        }
        int selected = RadarFilterCardItem.filterMask(cards[0], 0);
        return RadarFilterCardItem.cardOption(cards[0], 1) == 0
                ? RadarDetectionFilters.DEFAULT_MASK & ~selected
                : selected;
    }

    public int displayMask() {
        if (cards[1].isEmpty()) {
            return RadarDetectionFilters.DEFAULT_MASK;
        }
        int selected = RadarFilterCardItem.filterMask(cards[1], 0);
        return RadarFilterCardItem.cardOption(cards[1], 0) == 0
                ? RadarDetectionFilters.DEFAULT_MASK & ~selected
                : selected;
    }

    public List<String> allowlistedPlayers() {
        if (cards[2].isEmpty() || !allowlistIsWhitelist() || allowlistTargetsSable()) {
            return List.of();
        }
        return allowlistNames();
    }

    public boolean allowlistIsWhitelist() { return cards[2].isEmpty() || RadarFilterCardItem.cardOption(cards[2], 1) == 1; }
    public boolean allowlistTargetsSable() { return !cards[2].isEmpty() && RadarFilterCardItem.allowlistSableMode(cards[2]); }
    public List<String> allowlistNames() { return cards[2].isEmpty() ? List.of() : RadarFilterCardItem.allowlistNames(cards[2]); }

    public void dropCards() {
        if (level == null || level.isClientSide()) {
            return;
        }
        for (int i = 0; i < cards.length; i++) {
            if (!cards[i].isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D, cards[i]);
                cards[i] = ItemStack.EMPTY;
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        tooltip.add(Component.translatable("goggles.power_radar.computing_block").withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < cards.length; i++) {
            tooltip.add(Component.translatable("goggles.power_radar.computing_block.slot." + i,
                    cards[i].isEmpty() ? Component.translatable("goggles.power_radar.computing_block.empty")
                            : cards[i].getHoverName()));
        }
        if (level instanceof ServerLevel serverLevel) {
            RadarLinkConnectionResolver.Resolution resolution =
                    RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(serverLevel, worldPosition);
            if (resolution.status() == RadarLinkConnectionResolver.Status.SINGLE && resolution.link().networkId() != null) {
                RadarNetworkManager.ComputingResolution computing = RadarNetworkManager.get(serverLevel.getServer())
                        .resolveComputingBlock(resolution.link().networkId());
                tooltip.add(Component.translatable(computing.conflict()
                        ? "goggles.power_radar.computing_block.conflict"
                        : "goggles.power_radar.computing_block.connected"));
            } else {
                tooltip.add(Component.translatable("goggles.power_radar.computing_block.disconnected"));
            }
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < cards.length; i++) {
            if (!cards[i].isEmpty()) {
                tag.put("Card" + i, cards[i].save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < cards.length; i++) {
            cards[i] = tag.contains("Card" + i)
                    ? ItemStack.parseOptional(registries, tag.getCompound("Card" + i))
                    : ItemStack.EMPTY;
        }
    }

    private int firstOccupiedSlot() {
        for (int i = 0; i < cards.length; i++) {
            if (!cards[i].isEmpty()) return i;
        }
        return -1;
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            invalidateNetworkPolicyCache();
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void invalidateNetworkPolicyCache() {
        if (!(this.level instanceof ServerLevel serverLevel)) return;
        RadarLinkConnectionResolver.Resolution resolution =
                RadarLinkConnectionResolver.findSingleLinkFacingEndpointCached(serverLevel, this.worldPosition);
        if (resolution.status() == RadarLinkConnectionResolver.Status.SINGLE
                && resolution.link().networkId() != null) {
            RadarNetworkManager.get(serverLevel.getServer()).invalidateComputingCache(resolution.link().networkId());
        }
    }
}
