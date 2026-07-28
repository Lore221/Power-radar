package com.limbo2136.powerradar.logic;

import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Хранит три карты Logic Dock и единожды определяет их сетевую политику. */
public final class LogicDockCardInventory {
    public static final int SLOT_COUNT = 3;
    public static final int TARGETING_SLOT = 0;
    public static final int DISPLAY_SLOT = 1;
    public static final int ALLOWLIST_SLOT = 2;

    private static final String CARD_PRESENCE_MASK_TAG = "CardPresenceMask";
    private final ItemStack[] cards = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    public boolean insert(RadarFilterCardItem.Kind kind, ItemStack held, Player player) {
        // Порядок Kind является частью раскладки слотов и сохранённых ключей Card0..Card2.
        int slot = kind.ordinal();
        if (!this.cards[slot].isEmpty()) {
            return false;
        }
        this.cards[slot] = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        return true;
    }

    public ItemStack extract(int requestedSlot) {
        int slot = requestedSlot;
        if (!validSlot(slot) || this.cards[slot].isEmpty()) {
            slot = firstOccupiedSlot();
        }
        return slot < 0 ? ItemStack.EMPTY : removeExact(slot);
    }

    public ItemStack removeExact(int slot) {
        if (!validSlot(slot) || this.cards[slot].isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = this.cards[slot];
        this.cards[slot] = ItemStack.EMPTY;
        return extracted;
    }

    public boolean hasCard(int slot) {
        return validSlot(slot) && !this.cards[slot].isEmpty();
    }

    public ItemStack card(int slot) {
        return validSlot(slot) ? this.cards[slot] : ItemStack.EMPTY;
    }

    public int targetingMask() {
        if (!hasCard(TARGETING_SLOT)) {
            return 0;
        }
        int selected = RadarFilterCardItem.filterMask(this.cards[TARGETING_SLOT], 0);
        return RadarFilterCardItem.cardOption(this.cards[TARGETING_SLOT], 1) == 0
                ? RadarDetectionFilters.DEFAULT_MASK & ~selected
                : selected;
    }

    public int displayMask() {
        if (!hasCard(DISPLAY_SLOT)) {
            return RadarDetectionFilters.DEFAULT_MASK;
        }
        int selected = RadarFilterCardItem.filterMask(this.cards[DISPLAY_SLOT], 0);
        return RadarFilterCardItem.cardOption(this.cards[DISPLAY_SLOT], 0) == 0
                ? RadarDetectionFilters.DEFAULT_MASK & ~selected
                : selected;
    }

    public boolean allowlistIsWhitelist() {
        return !hasCard(ALLOWLIST_SLOT)
                || RadarFilterCardItem.cardOption(this.cards[ALLOWLIST_SLOT], 1) == 1;
    }

    public List<String> allowlistPlayerNames() {
        return allowlistData().playerNames();
    }

    public List<String> allowlistSableNames() {
        return allowlistData().sableNames();
    }

    public List<String> allowlistedPlayers() {
        return !hasCard(ALLOWLIST_SLOT) || !allowlistIsWhitelist()
                ? List.of()
                : allowlistPlayerNames();
    }

    public List<String> allowlistedSableNames() {
        return !hasCard(ALLOWLIST_SLOT) || !allowlistIsWhitelist()
                ? List.of()
                : allowlistSableNames();
    }

    public void read(CompoundTag tag, HolderLookup.Provider registries) {
        for (int slot = 0; slot < this.cards.length; slot++) {
            this.cards[slot] = tag.contains("Card" + slot)
                    ? ItemStack.parseOptional(registries, tag.getCompound("Card" + slot))
                    : ItemStack.EMPTY;
        }
    }

    public void write(CompoundTag tag, HolderLookup.Provider registries) {
        int presenceMask = 0;
        for (int slot = 0; slot < this.cards.length; slot++) {
            if (!this.cards[slot].isEmpty()) {
                presenceMask |= 1 << slot;
                tag.put("Card" + slot, this.cards[slot].save(registries));
            }
        }
        // Нулевая маска явно синхронизирует удаление последней карты на клиент.
        tag.putByte(CARD_PRESENCE_MASK_TAG, (byte) presenceMask);
    }

    private RadarFilterCardItem.AllowlistData allowlistData() {
        return !hasCard(ALLOWLIST_SLOT)
                ? new RadarFilterCardItem.AllowlistData(List.of(), List.of())
                : RadarFilterCardItem.allowlistData(this.cards[ALLOWLIST_SLOT]);
    }

    private int firstOccupiedSlot() {
        for (int slot = 0; slot < this.cards.length; slot++) {
            if (!this.cards[slot].isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }
}
