package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.PowerRadar;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final String ID_KEY = "Id";
    private static final String SCHEMA_VERSION_KEY = "SchemaVersion";
    private static final String LINK_NODES_KEY = "LinkNodes";
    private static final String CONTROLLER_BINDINGS_KEY = "ControllerBindings";
    private static final String WHITELISTED_PLAYERS_KEY = "WhitelistedPlayers";
    private static final String WHITELISTED_SABLE_KEY = "WhitelistedSable";
    private static final String SELECTED_TARGET_UUID_KEY = "SelectedTargetUuid";
    private static final String AUTOTARGET_FILTER_MASK_KEY = "AutotargetFilterMask";
    private static final String CONTROL_CONSUMERS_ALLOWED_KEY = "ControlConsumersAllowed";

    private final Map<UUID, RadarNetworkRecord> networks = new LinkedHashMap<>();
    private final List<CompoundTag> preservedFutureNetworkTags = new ArrayList<>();

    public static RadarNetworkSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RadarNetworkSavedData::new, RadarNetworkSavedData::load),
                NAME
        );
    }

    public static RadarNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RadarNetworkSavedData data = new RadarNetworkSavedData();
        ListTag networksTag = tag.getList(NETWORKS_KEY, Tag.TAG_COMPOUND);
        boolean needsResave = false;
        for (int i = 0; i < networksTag.size(); i++) {
            MigrationResult migration = migrateNetworkTag(networksTag.getCompound(i));
            if (migration.status() == MigrationStatus.FUTURE) {
                data.preservedFutureNetworkTags.add(migration.tag());
                PowerRadar.LOGGER.warn("[PowerRadar] Preserving unsupported future radar network schema {}",
                        migration.schemaVersion());
                continue;
            }
            if (migration.status() == MigrationStatus.INVALID) {
                needsResave = true;
                PowerRadar.LOGGER.warn("[PowerRadar] Skipping invalid radar network record: {}", migration.reason());
                continue;
            }

            CompoundTag networkTag = migration.tag();
            UUID id = networkTag.getUUID(ID_KEY);
            RadarNetworkRecord record = new RadarNetworkRecord(id);
            ListTag linksTag = networkTag.getList(LINK_NODES_KEY, Tag.TAG_COMPOUND);
            for (int linkIndex = 0; linkIndex < linksTag.size(); linkIndex++) {
                readGlobalPos(linksTag.getCompound(linkIndex)).ifPresent(record.linkNodes()::add);
            }
            ListTag controllerBindingsTag = networkTag.getList(CONTROLLER_BINDINGS_KEY, Tag.TAG_COMPOUND);
            for (int bindingIndex = 0; bindingIndex < controllerBindingsTag.size(); bindingIndex++) {
                readControllerBinding(controllerBindingsTag.getCompound(bindingIndex))
                        .ifPresent(record.controllerBindings()::add);
            }
            readStringSet(networkTag, WHITELISTED_PLAYERS_KEY, record.whitelistedPlayerNames());
            readStringSet(networkTag, WHITELISTED_SABLE_KEY, record.whitelistedSableNames());
            if (networkTag.hasUUID(SELECTED_TARGET_UUID_KEY)) {
                record.setSelectedTargetUuid(networkTag.getUUID(SELECTED_TARGET_UUID_KEY));
            }
            record.setAutotargetFilterMask(networkTag.getInt(AUTOTARGET_FILTER_MASK_KEY));
            record.setControlConsumersAllowed(networkTag.getBoolean(CONTROL_CONSUMERS_ALLOWED_KEY));
            if (data.networks.putIfAbsent(id, record) != null) {
                needsResave = true;
                PowerRadar.LOGGER.warn("[PowerRadar] Skipping duplicate radar network record {}", id);
                continue;
            }
            needsResave |= migration.changed();
        }
        if (needsResave) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag networksTag = new ListTag();
        for (RadarNetworkRecord record : this.networks.values()) {
            CompoundTag networkTag = new CompoundTag();
            networkTag.putUUID(ID_KEY, record.id());
            networkTag.putInt(SCHEMA_VERSION_KEY, RadarNetworkRecord.SCHEMA_VERSION);
            ListTag linksTag = new ListTag();
            for (GlobalPos linkPos : record.linkNodes()) {
                linksTag.add(writeGlobalPos(linkPos));
            }
            networkTag.put(LINK_NODES_KEY, linksTag);
            ListTag controllerBindingsTag = new ListTag();
            for (RadarControllerEndpointBinding binding : record.controllerBindings()) {
                controllerBindingsTag.add(writeControllerBinding(binding));
            }
            networkTag.put(CONTROLLER_BINDINGS_KEY, controllerBindingsTag);
            networkTag.put(WHITELISTED_PLAYERS_KEY, writeStringSet(record.whitelistedPlayerNames()));
            networkTag.put(WHITELISTED_SABLE_KEY, writeStringSet(record.whitelistedSableNames()));
            if (record.selectedTargetUuid() != null) {
                networkTag.putUUID(SELECTED_TARGET_UUID_KEY, record.selectedTargetUuid());
            }
            networkTag.putInt(AUTOTARGET_FILTER_MASK_KEY, record.autotargetFilterMask());
            networkTag.putBoolean(CONTROL_CONSUMERS_ALLOWED_KEY, record.controlConsumersAllowed());
            networksTag.add(networkTag);
        }
        for (CompoundTag futureNetworkTag : this.preservedFutureNetworkTags) {
            networksTag.add(futureNetworkTag.copy());
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

    boolean removeIfNoLinks(UUID id) {
        RadarNetworkRecord record = this.networks.get(id);
        if (record == null || !record.linkNodes().isEmpty()) {
            return false;
        }
        this.networks.remove(id);
        this.setDirty();
        return true;
    }

    private static MigrationResult migrateNetworkTag(CompoundTag source) {
        CompoundTag tag = source.copy();
        if (!tag.hasUUID(ID_KEY)) {
            return MigrationResult.invalid("missing network UUID");
        }

        int schemaVersion = tag.contains(SCHEMA_VERSION_KEY, Tag.TAG_INT)
                ? tag.getInt(SCHEMA_VERSION_KEY)
                : 0;
        if (schemaVersion > RadarNetworkRecord.SCHEMA_VERSION) {
            return MigrationResult.future(tag, schemaVersion);
        }
        if (schemaVersion < 0) {
            return MigrationResult.invalid("negative schema version " + schemaVersion);
        }

        boolean changed = false;
        while (schemaVersion < RadarNetworkRecord.SCHEMA_VERSION) {
            switch (schemaVersion) {
                case 0 -> migrateV0ToV1(tag);
                case 1 -> migrateV1ToV2(tag);
                case 2 -> migrateV2ToV3(tag);
                case 3 -> migrateV3ToV4(tag);
                default -> throw new IllegalStateException("Unsupported radar network schema " + schemaVersion);
            }
            schemaVersion++;
            tag.putInt(SCHEMA_VERSION_KEY, schemaVersion);
            changed = true;
        }
        changed |= normalizeCurrentSchema(tag);
        return MigrationResult.current(tag, changed);
    }

    private static void migrateV0ToV1(CompoundTag tag) {
        ensureCompoundList(tag, LINK_NODES_KEY);
        ensureCompoundList(tag, CONTROLLER_BINDINGS_KEY);
    }

    private static void migrateV1ToV2(CompoundTag tag) {
        ensureStringList(tag, WHITELISTED_PLAYERS_KEY);
        ensureStringList(tag, WHITELISTED_SABLE_KEY);
    }

    private static void migrateV2ToV3(CompoundTag tag) {
        if (!tag.contains(AUTOTARGET_FILTER_MASK_KEY, Tag.TAG_INT)) {
            tag.putInt(AUTOTARGET_FILTER_MASK_KEY, 0);
        }
    }

    private static void migrateV3ToV4(CompoundTag tag) {
        if (!tag.contains(CONTROL_CONSUMERS_ALLOWED_KEY, Tag.TAG_BYTE)) {
            tag.putBoolean(CONTROL_CONSUMERS_ALLOWED_KEY, true);
        }
    }

    private static boolean normalizeCurrentSchema(CompoundTag tag) {
        boolean changed = false;
        changed |= ensureCompoundList(tag, LINK_NODES_KEY);
        changed |= ensureCompoundList(tag, CONTROLLER_BINDINGS_KEY);
        changed |= ensureStringList(tag, WHITELISTED_PLAYERS_KEY);
        changed |= ensureStringList(tag, WHITELISTED_SABLE_KEY);
        if (!tag.contains(AUTOTARGET_FILTER_MASK_KEY, Tag.TAG_INT)) {
            tag.putInt(AUTOTARGET_FILTER_MASK_KEY, 0);
            changed = true;
        }
        if (!tag.contains(CONTROL_CONSUMERS_ALLOWED_KEY, Tag.TAG_BYTE)) {
            tag.putBoolean(CONTROL_CONSUMERS_ALLOWED_KEY, true);
            changed = true;
        }
        return changed;
    }

    private static boolean ensureCompoundList(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_LIST)) {
            return false;
        }
        tag.put(key, new ListTag());
        return true;
    }

    private static boolean ensureStringList(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_LIST)) {
            return false;
        }
        tag.put(key, new ListTag());
        return true;
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

    private enum MigrationStatus {
        CURRENT,
        FUTURE,
        INVALID
    }

    private record MigrationResult(MigrationStatus status, CompoundTag tag, int schemaVersion, boolean changed, String reason) {
        private static MigrationResult current(CompoundTag tag, boolean changed) {
            return new MigrationResult(MigrationStatus.CURRENT, tag, RadarNetworkRecord.SCHEMA_VERSION, changed, "");
        }

        private static MigrationResult future(CompoundTag tag, int schemaVersion) {
            return new MigrationResult(MigrationStatus.FUTURE, tag, schemaVersion, false, "");
        }

        private static MigrationResult invalid(String reason) {
            return new MigrationResult(MigrationStatus.INVALID, new CompoundTag(), -1, false, reason);
        }
    }
}
