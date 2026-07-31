package com.limbo2136.powerradar.radar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limbo2136.powerradar.api.target.TargetClassification;
import com.limbo2136.powerradar.api.target.TargetSourceType;
import com.limbo2136.powerradar.radar.RadarId;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class RadarNetworkRuntimeSelectedTargetTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void selectionMovesFromWaitingToSharedLiveSnapshotAndBackToEmpty() {
        RadarNetworkRuntime runtime = new RadarNetworkRuntime();
        UUID targetUuid = UUID.randomUUID();
        RadarId radarId = new RadarId(OVERWORLD, new BlockPos(1, 64, 2));
        SelectedTargetRuntimeSnapshot.TargetView measured = measuredTarget(targetUuid);

        runtime.setSelectedTargetUuid(targetUuid);
        assertEquals(SelectedTargetRuntimeSnapshot.Status.WAITING_FOR_TRACK,
                runtime.selectedTargetSnapshot().status());

        runtime.putSelectedTargetTrack(31L, targetUuid, measured, Set.of(radarId), 100L);
        assertEquals(SelectedTargetRuntimeSnapshot.Status.TRACK_CONFIRMED,
                runtime.selectedTargetSnapshot().status());
        assertTrue(runtime.selectedTargetTrack().confirmed());

        SelectedTargetRuntimeSnapshot.TargetView live =
                SelectedTargetRuntimeSnapshot.TargetView.liveEntity(
                        measured,
                        42,
                        OVERWORLD,
                        new Vec3(12.0D, 70.0D, -4.0D),
                        new Vec3(0.5D, 0.0D, -0.25D),
                        101L,
                        1.8D,
                        1.8D);
        runtime.putLiveSelectedTarget(
                SelectedTargetRuntimeSnapshot.Status.LIVE,
                targetUuid,
                Set.of(radarId),
                101L,
                live);

        assertTrue(runtime.selectedTargetSnapshot().alive());
        assertEquals(live, runtime.selectedTargetSnapshot().target());
        assertTrue(runtime.selectedTargetUpdatedAt(101L));

        runtime.setSelectedTargetUuid(null);
        assertEquals(SelectedTargetRuntimeSnapshot.Status.NO_SELECTION,
                runtime.selectedTargetSnapshot().status());
        assertFalse(runtime.selectedTargetTrack().confirmed());
    }

    @Test
    void missedScanKeepsUuidButRemovesConfirmedTarget() {
        RadarNetworkRuntime runtime = new RadarNetworkRuntime();
        UUID targetUuid = UUID.randomUUID();
        runtime.setSelectedTargetUuid(targetUuid);

        runtime.putSelectedTargetTrack(62L, targetUuid, null, Set.of(), 105L);

        assertEquals(targetUuid, runtime.selectedTargetSnapshot().selectedTargetUuid());
        assertEquals(SelectedTargetRuntimeSnapshot.Status.WAITING_FOR_TRACK,
                runtime.selectedTargetSnapshot().status());
        assertFalse(runtime.selectedTargetSnapshot().confirmedByLatestScan());
        assertFalse(runtime.selectedTargetSnapshot().alive());
    }

    @Test
    void scanPublishedAfterLiveLookupKeepsSameTickKinematics() {
        RadarNetworkRuntime runtime = new RadarNetworkRuntime();
        UUID targetUuid = UUID.randomUUID();
        RadarId firstRadar = new RadarId(OVERWORLD, new BlockPos(1, 64, 2));
        RadarId secondRadar = new RadarId(OVERWORLD, new BlockPos(4, 64, 5));
        SelectedTargetRuntimeSnapshot.TargetView firstMeasurement = measuredTarget(targetUuid);
        runtime.setSelectedTargetUuid(targetUuid);
        runtime.putSelectedTargetTrack(
                31L, targetUuid, firstMeasurement, Set.of(firstRadar), 100L);

        Vec3 livePosition = new Vec3(12.0D, 70.0D, -4.0D);
        Vec3 liveVelocity = new Vec3(0.5D, 0.0D, -0.25D);
        runtime.putLiveSelectedTarget(
                SelectedTargetRuntimeSnapshot.Status.LIVE,
                targetUuid,
                Set.of(firstRadar),
                101L,
                SelectedTargetRuntimeSnapshot.TargetView.liveEntity(
                        firstMeasurement,
                        42,
                        OVERWORLD,
                        livePosition,
                        liveVelocity,
                        101L,
                        1.8D,
                        1.8D));

        runtime.putSelectedTargetTrack(
                62L, targetUuid, measuredTarget(targetUuid), Set.of(secondRadar), 101L);

        assertTrue(runtime.selectedTargetUpdatedAt(101L));
        assertTrue(runtime.selectedTargetSnapshot().alive());
        assertEquals(livePosition, runtime.selectedTargetSnapshot().target().position());
        assertEquals(liveVelocity, runtime.selectedTargetSnapshot().target().velocity());
        assertEquals(Set.of(secondRadar), runtime.selectedTargetSnapshot().confirmingRadars());
    }

    @Test
    void scanPublishedAfterMissingEntityDoesNotRequestAnotherSameTickRefresh() {
        RadarNetworkRuntime runtime = new RadarNetworkRuntime();
        UUID targetUuid = UUID.randomUUID();
        RadarId radarId = new RadarId(OVERWORLD, new BlockPos(1, 64, 2));
        SelectedTargetRuntimeSnapshot.TargetView measured = measuredTarget(targetUuid);
        runtime.setSelectedTargetUuid(targetUuid);
        runtime.putSelectedTargetTrack(31L, targetUuid, measured, Set.of(radarId), 100L);
        runtime.putLiveSelectedTarget(
                SelectedTargetRuntimeSnapshot.Status.ENTITY_UNAVAILABLE,
                targetUuid,
                Set.of(radarId),
                101L,
                null);

        runtime.putSelectedTargetTrack(62L, targetUuid, measured, Set.of(radarId), 101L);

        assertTrue(runtime.selectedTargetUpdatedAt(101L));
        assertEquals(
                SelectedTargetRuntimeSnapshot.Status.ENTITY_UNAVAILABLE,
                runtime.selectedTargetSnapshot().status());
        assertFalse(runtime.selectedTargetSnapshot().alive());
    }

    private static SelectedTargetRuntimeSnapshot.TargetView measuredTarget(UUID targetUuid) {
        return new SelectedTargetRuntimeSnapshot.TargetView(
                targetUuid,
                7,
                ResourceLocation.fromNamespaceAndPath("minecraft", "player"),
                TargetSourceType.ENTITY,
                "Pilot",
                TargetClassification.PLAYER,
                OVERWORLD,
                new Vec3(10.0D, 70.0D, -5.0D),
                new Vec3(0.25D, 0.0D, 0.0D),
                true,
                Vec3.ZERO,
                false,
                90L,
                100L,
                100L,
                1.8D,
                1.8D);
    }
}
