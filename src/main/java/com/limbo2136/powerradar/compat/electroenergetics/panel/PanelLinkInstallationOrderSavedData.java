package com.limbo2136.powerradar.compat.electroenergetics.panel;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Хранит общий возрастающий номер установки панельных Link.
 * Счётчик отделён от радарных сетей: возраст принадлежит установленному модулю.
 */
public final class PanelLinkInstallationOrderSavedData extends SavedData {
    private static final String NAME = "power_radar_panel_link_installation_order";
    private static final String NEXT_ORDER_KEY = "NextOrder";
    private static final long FIRST_ORDER = 1L;

    private long nextOrder = FIRST_ORDER;

    public static PanelLinkInstallationOrderSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(
                        PanelLinkInstallationOrderSavedData::new,
                        PanelLinkInstallationOrderSavedData::load),
                NAME);
    }

    private static PanelLinkInstallationOrderSavedData load(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        PanelLinkInstallationOrderSavedData data = new PanelLinkInstallationOrderSavedData();
        long stored = tag.getLong(NEXT_ORDER_KEY);
        data.nextOrder = stored >= FIRST_ORDER ? stored : FIRST_ORDER;
        return data;
    }

    /** Выдаёт номер только установленному экземпляру; предмет этот номер не получает. */
    public long allocate() {
        long allocated = this.nextOrder;
        if (this.nextOrder < Long.MAX_VALUE) {
            this.nextOrder++;
        }
        setDirty();
        return allocated;
    }

    /** Не позволяет счётчику отстать от уже сохранённых вложений после обновления мира. */
    public void reserveAfter(long existingOrder) {
        if (existingOrder < FIRST_ORDER || existingOrder == Long.MAX_VALUE) {
            return;
        }
        long requiredNext = existingOrder + 1L;
        if (this.nextOrder < requiredNext) {
            this.nextOrder = requiredNext;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(NEXT_ORDER_KEY, this.nextOrder);
        return tag;
    }
}
