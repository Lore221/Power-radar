package com.limbo2136.powerradar.compat.aeronautics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class SableStructureNames extends SavedData {
    private static final String DATA_NAME = "power_radar_sable_names";
    private static final String ENTRIES = "Entries";
    private final Map<UUID, String> names = new LinkedHashMap<>();

    private SableStructureNames() { }

    private static SableStructureNames get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SableStructureNames::new, SableStructureNames::load), DATA_NAME);
    }

    public static void assign(MinecraftServer server, UUID structureUuid, String name) {
        if (name == null || name.isBlank()) return;
        SableStructureNames data = get(server);
        if (!name.equals(data.names.put(structureUuid, name))) data.setDirty();
    }

    public static void remove(MinecraftServer server, UUID structureUuid) {
        SableStructureNames data = get(server);
        if (data.names.remove(structureUuid) != null) data.setDirty();
    }

    static String resolve(MinecraftServer server, UUID structureUuid, String fallback) {
        return get(server).names.getOrDefault(structureUuid, fallback);
    }

    public static Optional<String> name(MinecraftServer server, UUID structureUuid) {
        return Optional.ofNullable(get(server).names.get(structureUuid));
    }

    public static Map<UUID, String> matchingName(MinecraftServer server, String requestedName) {
        if (requestedName == null || requestedName.isBlank()) return Map.of();
        LinkedHashMap<UUID, String> matches = new LinkedHashMap<>();
        get(server).names.forEach((uuid, name) -> {
            if (name.equalsIgnoreCase(requestedName.trim())) matches.put(uuid, name);
        });
        return Map.copyOf(matches);
    }

    private static SableStructureNames load(CompoundTag tag, HolderLookup.Provider registries) {
        SableStructureNames data = new SableStructureNames();
        ListTag entries = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Id")) data.names.put(entry.getUUID("Id"), entry.getString("Name"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        this.names.forEach((id, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Name", name);
            entries.add(entry);
        });
        tag.put(ENTRIES, entries);
        return tag;
    }
}
