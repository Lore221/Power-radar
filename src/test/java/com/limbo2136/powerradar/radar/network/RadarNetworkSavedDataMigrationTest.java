package com.limbo2136.powerradar.radar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class RadarNetworkSavedDataMigrationTest {
    private static final String NETWORKS_KEY = "PowerRadarNetworks";

    @Test
    void migratesUnversionedLegacyRecordAndMarksDataDirty() {
        UUID id = UUID.fromString("2639734d-7211-4ad8-b419-2c925cfb0e12");
        CompoundTag legacy = new CompoundTag();
        legacy.putUUID("Id", id);
        legacy.put("LinkNodes", globalPosList(GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, -20))));
        legacy.put("WhitelistedPlayers", stringList("Alice", " Bob ", ""));

        RadarNetworkSavedData data = RadarNetworkSavedData.load(rootWith(legacy), RegistryAccess.EMPTY);

        RadarNetworkRecord record = data.get(id).orElseThrow();
        assertEquals(RadarNetworkRecord.SCHEMA_VERSION, record.schemaVersion());
        assertEquals(1, record.linkNodes().size());
        assertEquals(List.of("Alice", "Bob"), List.copyOf(record.whitelistedPlayerNames()));
        assertTrue(record.whitelistedSableNames().isEmpty());
        assertEquals(0, record.autotargetFilterMask());
        assertTrue(data.isDirty());

        CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        CompoundTag migrated = saved.getList(NETWORKS_KEY, Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(RadarNetworkRecord.SCHEMA_VERSION, migrated.getInt("SchemaVersion"));
        assertTrue(migrated.contains("ControllerBindings", Tag.TAG_LIST));
        assertTrue(migrated.contains("WhitelistedSable", Tag.TAG_LIST));
        assertTrue(migrated.contains("AutotargetFilterMask", Tag.TAG_INT));
    }

    @Test
    void normalizesMissingOptionalFieldsInCurrentSchema() {
        UUID id = UUID.fromString("91d5a91d-cf6b-4fb8-bc0a-14d152ea4d19");
        CompoundTag currentButIncomplete = new CompoundTag();
        currentButIncomplete.putUUID("Id", id);
        currentButIncomplete.putInt("SchemaVersion", RadarNetworkRecord.SCHEMA_VERSION);

        RadarNetworkSavedData data = RadarNetworkSavedData.load(rootWith(currentButIncomplete), RegistryAccess.EMPTY);

        RadarNetworkRecord record = data.get(id).orElseThrow();
        assertTrue(record.linkNodes().isEmpty());
        assertTrue(record.controllerBindings().isEmpty());
        assertNull(record.selectedTargetUuid());
        assertTrue(data.isDirty());
    }

    @Test
    void preservesUnknownFutureRecordWithoutLoadingIt() {
        UUID futureId = UUID.fromString("5b875824-1995-47ca-95b4-36c0598d9ba5");
        CompoundTag future = new CompoundTag();
        future.putUUID("Id", futureId);
        future.putInt("SchemaVersion", RadarNetworkRecord.SCHEMA_VERSION + 1);
        future.putString("FutureOnlyField", "retain me");

        RadarNetworkSavedData data = RadarNetworkSavedData.load(rootWith(future), RegistryAccess.EMPTY);

        assertTrue(data.records().isEmpty());
        CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        CompoundTag preserved = saved.getList(NETWORKS_KEY, Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(RadarNetworkRecord.SCHEMA_VERSION + 1, preserved.getInt("SchemaVersion"));
        assertEquals("retain me", preserved.getString("FutureOnlyField"));
    }

    @Test
    void discardsMalformedRecordButLoadsValidNeighbors() {
        UUID validId = UUID.fromString("cb4f9a14-c31c-4aa8-92b7-a6fbd2cee072");
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("SchemaVersion", 0);
        CompoundTag valid = new CompoundTag();
        valid.putUUID("Id", validId);
        valid.putInt("SchemaVersion", RadarNetworkRecord.SCHEMA_VERSION);

        RadarNetworkSavedData data = RadarNetworkSavedData.load(rootWith(malformed, valid), RegistryAccess.EMPTY);

        assertEquals(1, data.records().size());
        assertTrue(data.get(validId).isPresent());
        assertTrue(data.isDirty());
    }

    @Test
    void currentRecordRoundTripsWithTopologyAndSettings() {
        UUID id = UUID.fromString("f52c18af-9771-48d8-b0cd-31247132d898");
        UUID targetId = UUID.fromString("b3389da0-4c1d-45e4-84b0-7bc0e61091d1");
        RadarNetworkSavedData original = new RadarNetworkSavedData();
        RadarNetworkRecord record = original.ensure(id);
        GlobalPos linkPos = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 65, 2));
        GlobalPos controllerPos = GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 66, 4));
        record.linkNodes().add(linkPos);
        record.controllerBindings().add(new RadarControllerEndpointBinding(linkPos, controllerPos));
        record.whitelistedPlayerNames().add("Alice");
        record.whitelistedSableNames().add("Sable Alpha");
        record.setSelectedTargetUuid(targetId);
        record.setAutotargetFilterMask(0x35);

        RadarNetworkSavedData restored = RadarNetworkSavedData.load(
                original.save(new CompoundTag(), RegistryAccess.EMPTY),
                RegistryAccess.EMPTY);

        RadarNetworkRecord restoredRecord = restored.get(id).orElseThrow();
        assertEquals(RadarNetworkRecord.SCHEMA_VERSION, restoredRecord.schemaVersion());
        assertEquals(record.linkNodes(), restoredRecord.linkNodes());
        assertEquals(record.controllerBindings(), restoredRecord.controllerBindings());
        assertEquals(record.whitelistedPlayerNames(), restoredRecord.whitelistedPlayerNames());
        assertEquals(record.whitelistedSableNames(), restoredRecord.whitelistedSableNames());
        assertEquals(targetId, restoredRecord.selectedTargetUuid());
        assertEquals(0x35, restoredRecord.autotargetFilterMask());
        assertFalse(restored.isDirty());
    }

    private static CompoundTag rootWith(CompoundTag... networks) {
        ListTag list = new ListTag();
        for (CompoundTag network : networks) {
            list.add(network);
        }
        CompoundTag root = new CompoundTag();
        root.put(NETWORKS_KEY, list);
        return root;
    }

    private static ListTag globalPosList(GlobalPos... positions) {
        ListTag list = new ListTag();
        for (GlobalPos position : positions) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", position.dimension().location().toString());
            tag.putInt("X", position.pos().getX());
            tag.putInt("Y", position.pos().getY());
            tag.putInt("Z", position.pos().getZ());
            list.add(tag);
        }
        return list;
    }

    private static ListTag stringList(String... values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }
}
