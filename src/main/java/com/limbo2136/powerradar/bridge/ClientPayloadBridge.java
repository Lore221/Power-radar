package com.limbo2136.powerradar.bridge;

import com.limbo2136.powerradar.network.AllowlistCardOpenPayload;
import com.limbo2136.powerradar.network.RadarCompassTargetPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockPosePayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockSnapshotPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockStaticPayload;
import com.limbo2136.powerradar.network.RadarMonitorBlockTargetsPayload;
import com.limbo2136.powerradar.network.RadarMonitorSilhouettePayload;
import com.limbo2136.powerradar.network.RadarMonitorSnapshotPayload;
import com.limbo2136.powerradar.network.TargetingCardOpenPayload;
import java.util.Objects;
import java.util.function.Consumer;

/** Типизированная common/client-граница для входящих клиентских payload без reflection в горячем пути. */
public final class ClientPayloadBridge {
    private static Consumer<RadarMonitorSnapshotPayload> monitorSnapshot = ignored -> { };
    private static Consumer<RadarMonitorBlockSnapshotPayload> blockSnapshot = ignored -> { };
    private static Consumer<RadarMonitorBlockStaticPayload> blockStatic = ignored -> { };
    private static Consumer<RadarMonitorBlockTargetsPayload> blockTargets = ignored -> { };
    private static Consumer<RadarMonitorBlockPosePayload> blockPose = ignored -> { };
    private static Consumer<RadarMonitorSilhouettePayload> silhouette = ignored -> { };
    private static Consumer<TargetingCardOpenPayload> targetingCardOpen = ignored -> { };
    private static Consumer<AllowlistCardOpenPayload> allowlistCardOpen = ignored -> { };
    private static Consumer<RadarCompassTargetPayload> compassTarget = ignored -> { };

    private ClientPayloadBridge() {
    }

    public static void configure(
            Consumer<RadarMonitorSnapshotPayload> monitorSnapshotHandler,
            Consumer<RadarMonitorBlockSnapshotPayload> blockSnapshotHandler,
            Consumer<RadarMonitorBlockStaticPayload> blockStaticHandler,
            Consumer<RadarMonitorBlockTargetsPayload> blockTargetsHandler,
            Consumer<RadarMonitorBlockPosePayload> blockPoseHandler,
            Consumer<RadarMonitorSilhouettePayload> silhouetteHandler,
            Consumer<TargetingCardOpenPayload> targetingCardOpenHandler,
            Consumer<AllowlistCardOpenPayload> allowlistCardOpenHandler,
            Consumer<RadarCompassTargetPayload> compassTargetHandler
    ) {
        monitorSnapshot = Objects.requireNonNull(monitorSnapshotHandler);
        blockSnapshot = Objects.requireNonNull(blockSnapshotHandler);
        blockStatic = Objects.requireNonNull(blockStaticHandler);
        blockTargets = Objects.requireNonNull(blockTargetsHandler);
        blockPose = Objects.requireNonNull(blockPoseHandler);
        silhouette = Objects.requireNonNull(silhouetteHandler);
        targetingCardOpen = Objects.requireNonNull(targetingCardOpenHandler);
        allowlistCardOpen = Objects.requireNonNull(allowlistCardOpenHandler);
        compassTarget = Objects.requireNonNull(compassTargetHandler);
    }

    public static void handle(RadarMonitorSnapshotPayload payload) {
        monitorSnapshot.accept(payload);
    }

    public static void handle(RadarMonitorBlockSnapshotPayload payload) {
        blockSnapshot.accept(payload);
    }

    public static void handle(RadarMonitorBlockStaticPayload payload) {
        blockStatic.accept(payload);
    }

    public static void handle(RadarMonitorBlockTargetsPayload payload) {
        blockTargets.accept(payload);
    }

    public static void handle(RadarMonitorBlockPosePayload payload) {
        blockPose.accept(payload);
    }

    public static void handle(RadarMonitorSilhouettePayload payload) {
        silhouette.accept(payload);
    }

    public static void handle(TargetingCardOpenPayload payload) {
        targetingCardOpen.accept(payload);
    }

    public static void handle(AllowlistCardOpenPayload payload) {
        allowlistCardOpen.accept(payload);
    }

    public static void handle(RadarCompassTargetPayload payload) {
        compassTarget.accept(payload);
    }
}
