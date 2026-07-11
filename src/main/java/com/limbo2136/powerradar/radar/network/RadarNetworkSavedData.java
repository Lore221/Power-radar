package com.limbo2136.powerradar.radar.network;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class RadarNetworkSavedData extends SavedData {
    public static final String NAME = "power_radar_networks";
    private static final String NETWORKS_KEY = "PowerRadarNetworks";

    private final Map<UUID, RadarNetworkRecord> networks = new LinkedHashMap<>();

    public static RadarNetworkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RadarNetworkSavedData::new, RadarNetworkSavedData::load),
                NAME
        );
    }

    public static RadarNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RadarNetworkSavedData data = new RadarNetworkSavedData();
        ListTag networksTag = tag.getList(NETWORKS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < networksTag.size(); i++) {
            CompoundTag networkTag = networksTag.getCompound(i);
            if (!networkTag.hasUUID("Id")) {
                continue;
            }
            UUID id = networkTag.getUUID("Id");
            RadarNetworkRecord record = new RadarNetworkRecord(id, networkTag.getInt("SchemaVersion"));
            ListTag linksTag = networkTag.getList("LinkNodes", Tag.TAG_COMPOUND);
            for (int linkIndex = 0; linkIndex < linksTag.size(); linkIndex++) {
                readGlobalPos(linksTag.getCompound(linkIndex)).ifPresent(record.linkNodes()::add);
            }
            ListTag controllerBindingsTag = networkTag.getList("ControllerBindings", Tag.TAG_COMPOUND);
            for (int bindingIndex = 0; bindingIndex < controllerBindingsTag.size(); bindingIndex++) {
                readControllerBinding(controllerBindingsTag.getCompound(bindingIndex))
                        .ifPresent(record.controllerBindings()::add);
            }
            readStringSet(networkTag, "WhitelistedPlayers", record.whitelistedPlayerNames());
            readStringSet(networkTag, "WhitelistedSable", record.whitelistedSableNames());
            if (networkTag.hasUUID("SelectedTargetUuid")) {
                record.setSelectedTargetUuid(networkTag.getUUID("SelectedTargetUuid"));
            }
            record.setAutotargetFilterMask(networkTag.getInt("AutotargetFilterMask"));
            data.networks.put(id, record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag networksTag = new ListTag();
        for (RadarNetworkRecord record : this.networks.values()) {
            CompoundTag networkTag = new CompoundTag();
            networkTag.putUUID("Id", record.id());
            networkTag.putInt("SchemaVersion", record.schemaVersion());
            ListTag linksTag = new ListTag();
            for (GlobalPos linkPos : record.linkNodes()) {
                linksTag.add(writeGlobalPos(linkPos));
            }
            networkTag.put("LinkNodes", linksTag);
            ListTag controllerBindingsTag = new ListTag();
            for (RadarControllerEndpointBinding binding : record.controllerBindings()) {
                controllerBindingsTag.add(writeControllerBinding(binding));
            }
            networkTag.put("ControllerBindings", controllerBindingsTag);
            networkTag.put("WhitelistedPlayers", writeStringSet(record.whitelistedPlayerNames()));
            networkTag.put("WhitelistedSable", writeStringSet(record.whitelistedSableNames()));
            if (record.selectedTargetUuid() != null) {
                networkTag.putUUID("SelectedTargetUuid", record.selectedTargetUuid());
            }
            networkTag.putInt("AutotargetFilterMask", record.autotargetFilterMask());
            networksTag.add(networkTag);
        }
        tag.put(NETWORKS_KEY, networksTag);
        return tag;
    }

    public Collection<RadarNetworkRecord> records() {
        return this.networks.values();
    }

    public Optional<RadarNetworkRecord> get(UUID id) {
        return Optional.ofNullable(this.networks.get(id));
    }

    public RadarNetworkRecord ensure(UUID id) {
        RadarNetworkRecord existing = this.networks.get(id);
        if (existing != null) {
            return existing;
        }
        RadarNetworkRecord record = new RadarNetworkRecord(id);
        this.networks.put(id, record);
        this.setDirty();
        return record;
    }

    public void remove(UUID id) {
        if (this.networks.remove(id) != null) {
            this.setDirty();
        }
    }

    private static CompoundTag writeGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        tag.putInt("X", pos.pos().getX());
        tag.putInt("Y", pos.pos().getY());
        tag.putInt("Z", pos.pos().getZ());
        return tag;
    }

    private static Optional<GlobalPos> readGlobalPos(CompoundTag tag) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimensionId == null) {
            return Optional.empty();
        }
        ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId);
        return Optional.of(GlobalPos.of(dimension, new BlockPos(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"))));
    }

    private static CompoundTag writeControllerBinding(RadarControllerEndpointBinding binding) {
        CompoundTag tag = new CompoundTag();
        tag.put("RadarLinkPos", writeGlobalPos(binding.radarLinkPos()));
        tag.put("ControllerPos", writeGlobalPos(binding.controllerPos()));
        return tag;
    }

    private static Optional<RadarControllerEndpointBinding> readControllerBinding(CompoundTag tag) {
        Optional<GlobalPos> radarLinkPos = readGlobalPos(tag.getCompound("RadarLinkPos"));
        Optional<GlobalPos> controllerPos = readGlobalPos(tag.getCompound("ControllerPos"));
        if (radarLinkPos.isEmpty() || controllerPos.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RadarControllerEndpointBinding(radarLinkPos.get(), controllerPos.get()));
    }

    private static ListTag writeStringSet(Collection<String> values) {
        ListTag tag = new ListTag();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                tag.add(StringTag.valueOf(value));
            }
        }
        return tag;
    }

    private static void readStringSet(CompoundTag source, String key, Collection<String> destination) {
        ListTag tag = source.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < tag.size(); i++) {
            String value = tag.getString(i).trim();
            if (!value.isEmpty()) {
                destination.add(value);
            }
        }
    }
}
