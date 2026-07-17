package com.limbo2136.powerradar.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.limbo2136.powerradar.radar.RadarDisplayCoverage;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarId;
import com.limbo2136.powerradar.radar.RadarOrientationState;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarStructureType;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.RadarTargetSourceKind;
import com.limbo2136.powerradar.radar.ShellAlarmDisplayZone;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import io.netty.buffer.Unpooled;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class RadarMonitorPayloadCodecTest {
    private static final String V5_FIXTURE_SHA_256 =
            "a509099f2ae4d4094723b165c447c5bd423d1bd4366c908e90b6b80bf3f96969";

    @Test
    void fullSnapshotRoundTripsWithoutUnreadBytes() {
        RadarMonitorSnapshotPayload expected = fixture();

        RadarMonitorSnapshotPayload actual = roundTrip(RadarMonitorSnapshotPayload.STREAM_CODEC, expected);

        assertEquals(expected, actual);
    }

    @Test
    void snapshotWithNullOptionalsAndEmptyListsRoundTrips() {
        RadarMonitorSnapshotPayload source = fixture();
        RadarMonitorSnapshotPayload expected = new RadarMonitorSnapshotPayload(
                source.monitorPos(),
                0L,
                RadarNetworkConnectionStatus.NO_RADAR,
                false,
                null,
                null,
                source.radarDimensionId(),
                0.0,
                0.0,
                0.0,
                Direction.NORTH,
                0.0F,
                RadarStructureType.PHASED_ARRAY,
                0.0F,
                0.0F,
                0L,
                false,
                false,
                PowerRadarCeeState.INVALID_STRUCTURE,
                0.0,
                0.0,
                0,
                1,
                false,
                RadarScanMode.GROUND,
                0,
                0,
                null,
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                0L,
                0L,
                List.of(),
                List.of(),
                List.of());

        RadarMonitorSnapshotPayload actual = roundTrip(RadarMonitorSnapshotPayload.STREAM_CODEC, expected);

        assertEquals(expected, actual);
    }

    @Test
    void blockStaticSnapshotRoundTrips() {
        RadarMonitorBlockStaticPayload expected = new RadarMonitorBlockStaticPayload(fixture().withTargets(List.of()));

        RadarMonitorBlockStaticPayload actual = roundTrip(RadarMonitorBlockStaticPayload.STREAM_CODEC, expected);

        assertEquals(expected, actual);
        assertTrue(actual.snapshot().targets().isEmpty());
        assertEquals(0, actual.snapshot().displayedTargetCount());
    }

    @Test
    void blockTargetDeltaRoundTrips() {
        RadarMonitorSnapshotPayload snapshot = fixture();
        RadarMonitorBlockTargetsPayload expected = new RadarMonitorBlockTargetsPayload(
                snapshot.monitorPos(),
                snapshot.revision(),
                snapshot.lastScanGameTime(),
                snapshot.serverGameTime(),
                snapshot.targets());

        RadarMonitorBlockTargetsPayload actual = roundTrip(RadarMonitorBlockTargetsPayload.STREAM_CODEC, expected);

        assertEquals(expected, actual);
    }

    @Test
    void blockPoseDeltaRoundTrips() {
        ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("power_radar", "moving_plot");
        RadarMonitorBlockPosePayload expected = new RadarMonitorBlockPosePayload(
                new BlockPos(4, 70, -9),
                12_345L,
                new RadarMonitorBlockPosePayload.MonitorPose(4.5D, 70.5D, -8.5D, 91.0F),
                List.of(new RadarMonitorBlockPosePayload.RadarPose(
                        new RadarId(dimension, new BlockPos(2, 3, 4)),
                        10.25D, 65.5D, -31.75D, 359.5F)));

        RadarMonitorBlockPosePayload actual = roundTrip(RadarMonitorBlockPosePayload.STREAM_CODEC, expected);

        assertEquals(expected, actual);
    }

    @Test
    void sableSilhouettePayloadsRoundTrip() {
        UUID structureUuid = UUID.fromString("156afdf2-8e42-4fac-a98c-17196ec33a17");
        RadarMonitorSilhouetteRequestPayload request = new RadarMonitorSilhouetteRequestPayload(
                new BlockPos(3, 70, -8), structureUuid, 4);
        assertEquals(request, roundTrip(RadarMonitorSilhouetteRequestPayload.STREAM_CODEC, request));

        RadarMonitorSilhouettePayload silhouette = new RadarMonitorSilhouettePayload(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                structureUuid,
                5,
                List.of(new RadarMonitorSilhouettePayload.Line(-2.5F, -1.0F, 3.5F, -1.0F)),
                List.of(new RadarMonitorSilhouettePayload.Fill(-2.5F, -1.0F, 3.5F, 2.0F)));
        assertEquals(silhouette, roundTrip(RadarMonitorSilhouettePayload.STREAM_CODEC, silhouette));

        RadarDisplayTarget sableTarget = new RadarDisplayTarget(
                structureUuid,
                -1,
                ResourceLocation.fromNamespaceAndPath("sable", "sublevel"),
                RadarTargetSourceKind.FUTURE_SABLE_STRUCTURE,
                "Codec Airship",
                RadarTargetCategory.SABLE_STRUCTURE,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                100.5D, 72.0D, -35.25D,
                0.25D, 0.0D, -0.5D,
                true,
                -73.5F,
                5,
                0);
        RadarMonitorBlockTargetsPayload targets = new RadarMonitorBlockTargetsPayload(
                new BlockPos(3, 70, -8), 12L, 100L, 101L, List.of(sableTarget));
        assertEquals(targets, roundTrip(RadarMonitorBlockTargetsPayload.STREAM_CODEC, targets));
    }

    @Test
    void v5WireBytesRemainStable() throws NoSuchAlgorithmException {
        assertEquals("5", ModNetwork.PROTOCOL_VERSION);
        assertEquals(5, RadarMonitorSnapshotPayload.WIRE_SCHEMA_VERSION);

        RegistryFriendlyByteBuf buffer = newBuffer();
        try {
            RadarMonitorSnapshotPayload.STREAM_CODEC.encode(buffer, fixture());
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));

            assertEquals(V5_FIXTURE_SHA_256, digest);
        } finally {
            buffer.release();
        }
    }

    private static RadarMonitorSnapshotPayload fixture() {
        ResourceLocation overworld = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
        ResourceLocation testDimension = ResourceLocation.fromNamespaceAndPath("power_radar", "codec_test");
        RadarId radarId = new RadarId(testDimension, new BlockPos(-91, 72, 143));
        UUID manualTargetUuid = UUID.fromString("39f160a0-8fa8-4c22-bb45-5c894dfb78c1");

        List<RadarDisplayCoverage> coverages = List.of(
                new RadarDisplayCoverage(
                        radarId,
                        new BlockPos(-88, 70, 140),
                        testDimension,
                        -87.25,
                        70.5,
                        141.75,
                        new RadarOrientationState(RadarStructureType.OVERVIEW, 271.5F, 2.25F, 98_765L),
                        1_024,
                        360),
                new RadarDisplayCoverage(
                        new RadarId(overworld, new BlockPos(5, 64, -5)),
                        new BlockPos(6, 65, -6),
                        overworld,
                        6.125,
                        65.25,
                        -6.5,
                        RadarOrientationState.fixed(RadarStructureType.PHASED_ARRAY, 45.0F, 12_345L),
                        256,
                        60));

        List<RadarDisplayTarget> targets = List.of(
                new RadarDisplayTarget(
                        manualTargetUuid,
                        17,
                        ResourceLocation.fromNamespaceAndPath("minecraft", "player"),
                        RadarTargetSourceKind.ENTITY,
                        "Codec Pilot",
                        RadarTargetCategory.PLAYER,
                        overworld,
                        12.5,
                        80.25,
                        -48.75,
                        0.125,
                        -0.0625,
                        1.5,
                        true,
                        0.0F,
                        0,
                        7),
                new RadarDisplayTarget(
                        null,
                        2_147,
                        ResourceLocation.fromNamespaceAndPath("createbigcannons", "ap_shell"),
                        RadarTargetSourceKind.CBC_BIG_CANNON_PROJECTILE,
                        null,
                        RadarTargetCategory.PROJECTILE,
                        testDimension,
                        -1_000.5,
                        255.75,
                        2_048.125,
                        0.0,
                        0.0,
                        0.0,
                        false,
                        0.0F,
                        0,
                        127));

        return new RadarMonitorSnapshotPayload(
                new BlockPos(-12_345, 2_047, 54_321),
                9_876_543_210L,
                RadarNetworkConnectionStatus.CONNECTED,
                true,
                radarId,
                new BlockPos(-88, 70, 140),
                testDimension,
                -87.25,
                70.5,
                141.75,
                Direction.UP,
                182.75F,
                RadarStructureType.OVERVIEW,
                271.5F,
                2.25F,
                98_765L,
                true,
                true,
                PowerRadarCeeState.OVERVOLTAGE,
                -230.5,
                0.03125,
                25,
                5,
                true,
                RadarScanMode.SURFACE_SCANNER,
                0x55,
                0x0A,
                manualTargetUuid,
                List.of("Alice", "Bob Builder"),
                List.of("Alice"),
                List.of("Sable Alpha", "Sable Beta"),
                127,
                1_024,
                4_096,
                360,
                512,
                targets.size(),
                20,
                123_456_789L,
                123_456_799L,
                coverages,
                List.of(new ShellAlarmDisplayZone(
                        testDimension, 17.5D, 71.5D, -22.5D, 128, 72, 96)),
                targets);
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T expected) {
        RegistryFriendlyByteBuf buffer = newBuffer();
        try {
            codec.encode(buffer, expected);
            assertTrue(buffer.isReadable());

            T actual = codec.decode(buffer);

            assertFalse(buffer.isReadable(), "Codec left unread bytes in the buffer");
            return actual;
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf newBuffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
    }
}
