package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableSilhouetteSnapshot;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModDataComponents;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static final String PROTOCOL_VERSION = Integer.toString(RadarMonitorSnapshotPayload.WIRE_SCHEMA_VERSION);

    private ModNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(RadarMonitorSnapshotPayload.TYPE, RadarMonitorSnapshotPayload.STREAM_CODEC, ModNetwork::handleSnapshot);
        registrar.playToClient(RadarMonitorBlockSnapshotPayload.TYPE, RadarMonitorBlockSnapshotPayload.STREAM_CODEC, ModNetwork::handleBlockSnapshot);
        registrar.playToClient(RadarMonitorBlockStaticPayload.TYPE, RadarMonitorBlockStaticPayload.STREAM_CODEC, ModNetwork::handleBlockStatic);
        registrar.playToClient(RadarMonitorBlockTargetsPayload.TYPE, RadarMonitorBlockTargetsPayload.STREAM_CODEC, ModNetwork::handleBlockTargets);
        registrar.playToClient(RadarMonitorBlockPosePayload.TYPE, RadarMonitorBlockPosePayload.STREAM_CODEC, ModNetwork::handleBlockPose);
        registrar.playToClient(RadarMonitorSilhouettePayload.TYPE, RadarMonitorSilhouettePayload.STREAM_CODEC, ModNetwork::handleSilhouette);
        registrar.playToServer(RadarMonitorRequestPayload.TYPE, RadarMonitorRequestPayload.STREAM_CODEC, ModNetwork::handleRequest);
        registrar.playToServer(RadarMonitorSilhouetteRequestPayload.TYPE, RadarMonitorSilhouetteRequestPayload.STREAM_CODEC, ModNetwork::handleSilhouetteRequest);
        registrar.playToServer(RadarMonitorTargetSelectionPayload.TYPE, RadarMonitorTargetSelectionPayload.STREAM_CODEC, ModNetwork::handleTargetSelection);
        registrar.playToClient(TargetingCardOpenPayload.TYPE, TargetingCardOpenPayload.STREAM_CODEC, ModNetwork::handleTargetingCardOpen);
        registrar.playToServer(TargetingCardSavePayload.TYPE, TargetingCardSavePayload.STREAM_CODEC, ModNetwork::handleTargetingCardSave);
        registrar.playToClient(AllowlistCardOpenPayload.TYPE, AllowlistCardOpenPayload.STREAM_CODEC, ModNetwork::handleAllowlistCardOpen);
        registrar.playToServer(AllowlistCardSavePayload.TYPE, AllowlistCardSavePayload.STREAM_CODEC, ModNetwork::handleAllowlistCardSave);
    }

    private static void handleSnapshot(RadarMonitorSnapshotPayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, RadarMonitorSnapshotPayload.class,
                "com.limbo2136.powerradar.client.RadarMonitorClientHooks", "handleSnapshot",
                "[PowerRadar] Failed to open radar monitor screen");
    }

    private static void handleBlockSnapshot(RadarMonitorBlockSnapshotPayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, RadarMonitorBlockSnapshotPayload.class,
                "com.limbo2136.powerradar.client.RadarMonitorClientHooks", "handleBlockSnapshot",
                "[PowerRadar] Failed to update radar monitor block snapshot");
    }

    private static void handleBlockStatic(RadarMonitorBlockStaticPayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, RadarMonitorBlockStaticPayload.class,
                "com.limbo2136.powerradar.client.RadarMonitorClientHooks", "handleBlockStatic",
                "[PowerRadar] Failed to update radar monitor block static data");
    }

    private static void handleBlockTargets(RadarMonitorBlockTargetsPayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, RadarMonitorBlockTargetsPayload.class,
                "com.limbo2136.powerradar.client.RadarMonitorClientHooks", "handleBlockTargets",
                "[PowerRadar] Failed to update radar monitor block target data");
    }

    private static void handleBlockPose(RadarMonitorBlockPosePayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, RadarMonitorBlockPosePayload.class,
                "com.limbo2136.powerradar.client.RadarMonitorClientHooks", "handleBlockPose",
                "[PowerRadar] Failed to update moving radar monitor pose");
    }

    private static void handleSilhouette(RadarMonitorSilhouettePayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, RadarMonitorSilhouettePayload.class,
                "com.limbo2136.powerradar.client.RadarMonitorClientHooks", "handleSilhouette",
                "[PowerRadar] Failed to update Sable silhouette cache");
    }

    private static void handleTargetingCardOpen(TargetingCardOpenPayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, TargetingCardOpenPayload.class,
                "com.limbo2136.powerradar.client.TargetingCardClientHooks", "open",
                "[PowerRadar] Failed to open targeting card screen");
    }

    private static void handleTargetingCardSave(TargetingCardSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            var stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof RadarFilterCardItem card)
                    || payload.cardKind() < 0 || payload.cardKind() > 1
                    || card.kind() != (payload.cardKind() == 1
                            ? RadarFilterCardItem.Kind.DISPLAY
                            : RadarFilterCardItem.Kind.TARGETING)) {
                return;
            }
            stack.set(ModDataComponents.RADAR_FILTER_MASK.get(),
                    RadarDetectionFilters.sanitize(payload.filterMask()));
            stack.set(ModDataComponents.TARGETING_CARD_OPTION.get(), payload.option() == 0 ? 0 : 1);
        });
    }

    private static void handleAllowlistCardOpen(AllowlistCardOpenPayload payload, IPayloadContext context) {
        enqueueClientHandler(
                payload, context, AllowlistCardOpenPayload.class,
                "com.limbo2136.powerradar.client.AllowlistCardClientHooks", "open",
                "[PowerRadar] Failed to open allowlist card screen");
    }

    private static <P> void enqueueClientHandler(
            P payload,
            IPayloadContext context,
            Class<P> payloadType,
            String hooksClassName,
            String methodName,
            String errorMessage
    ) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        // Рефлексия не даёт общему классу получить прямую ссылку на клиентский пакет.
        context.enqueueWork(() -> invokeClientHandler(
                payload, payloadType, hooksClassName, methodName, errorMessage));
    }

    private static <P> void invokeClientHandler(
            P payload,
            Class<P> payloadType,
            String hooksClassName,
            String methodName,
            String errorMessage
    ) {
        try {
            Class<?> hooks = Class.forName(hooksClassName);
            hooks.getMethod(methodName, payloadType).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                | InvocationTargetException exception) {
            PowerRadar.LOGGER.error(errorMessage, exception);
        }
    }

    private static void handleAllowlistCardSave(AllowlistCardSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            var stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof RadarFilterCardItem card)
                    || card.kind() != RadarFilterCardItem.Kind.ALLOWLIST) {
                return;
            }
            RadarFilterCardItem.AllowlistData submitted = RadarFilterCardItem.decodeAllowlistLines(
                    payload.storedNames(), payload.sableMode());
            stack.set(ModDataComponents.RADAR_ALLOWLIST.get(), String.join("\n", submitted.encodedLines()));
            stack.set(ModDataComponents.ALLOWLIST_SABLE_MODE.get(), payload.sableMode());
            stack.set(ModDataComponents.TARGETING_CARD_OPTION.get(), payload.option() == 0 ? 0 : 1);
        });
    }

    private static void handleRequest(RadarMonitorRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RadarMonitorSnapshotPayload snapshot = RadarMonitorControllerBlockEntity.getOrCreateSnapshotPayload(
                    player.serverLevel(),
                    payload.monitorPos());
            if (snapshot.revision() != payload.knownRevision()) {
                PacketDistributor.sendToPlayer(player, snapshot);
            }
        }
    }

    private static void handleSilhouetteRequest(
            RadarMonitorSilhouetteRequestPayload payload,
            IPayloadContext context
    ) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            RadarMonitorSnapshotPayload monitor = RadarMonitorControllerBlockEntity.getOrCreateSnapshotPayload(
                    player.serverLevel(), payload.monitorPos());
            RadarDisplayTarget target = monitor.targets().stream()
                    .filter(candidate -> candidate.category() == RadarTargetCategory.SABLE_STRUCTURE)
                    .filter(candidate -> payload.structureUuid().equals(candidate.targetUuid()))
                    .findFirst()
                    .orElse(null);
            if (target == null) {
                return;
            }
            SableSilhouetteSnapshot snapshot = SableRadarIntegration.silhouetteSnapshot(
                            player.server, target.dimensionId(), payload.structureUuid())
                    .orElse(null);
            if (snapshot == null || snapshot.version() <= payload.knownVersion()) {
                return;
            }
            PacketDistributor.sendToPlayer(player, new RadarMonitorSilhouettePayload(
                    snapshot.dimensionId(),
                    snapshot.structureUuid(),
                    snapshot.version(),
                    snapshot.lines().stream()
                            .map(line -> new RadarMonitorSilhouettePayload.Line(
                                    line.x1(), line.z1(), line.x2(), line.z2()))
                            .toList(),
                    snapshot.fills().stream()
                            .map(fill -> new RadarMonitorSilhouettePayload.Fill(
                                    fill.minX(), fill.minZ(), fill.maxX(), fill.maxZ()))
                            .toList()));
        });
    }

    private static void handleTargetSelection(RadarMonitorTargetSelectionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            RadarLinkConnectionResolver.Resolution linkResolution =
                    RadarLinkConnectionResolver.findSingleLinkFacingEndpoint(player.serverLevel(), payload.monitorPos());
            if (linkResolution.status() != RadarLinkConnectionResolver.Status.SINGLE
                    || linkResolution.link().networkId() == null) {
                return;
            }
            RadarNetworkManager manager = RadarNetworkManager.get(player.server);
            if (!manager.controlConsumersAllowed(linkResolution.link().networkId())) {
                return;
            }
            RadarNetworkManager.ControllersResolution controllerResolution = manager.resolveControllersForConsumer(
                    linkResolution.link().networkId(),
                    GlobalPos.of(player.serverLevel().dimension(), linkResolution.link().getBlockPos()));
            if (payload.targetUuid() != null
                    && controllerResolution.controllers().stream()
                    .noneMatch(controller -> controller.findTargetTrack(payload.targetUuid()) != null)) {
                return;
            }
            manager.setSelectedTargetUuid(linkResolution.link().networkId(), payload.targetUuid());
        });
    }

}
