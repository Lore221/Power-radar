package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.aeronautics.SableSilhouetteSnapshot;
import com.limbo2136.powerradar.compat.aeronautics.SableStructureNames;
import com.limbo2136.powerradar.radar.RadarDisplayTarget;
import com.limbo2136.powerradar.radar.RadarTargetCategory;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.item.RadarFilterCardItem;
import com.limbo2136.powerradar.item.NameCardItem;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
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
        registrar.playToClient(NameCardOpenPayload.TYPE, NameCardOpenPayload.STREAM_CODEC, ModNetwork::handleNameCardOpen);
        registrar.playToServer(NameCardSavePayload.TYPE, NameCardSavePayload.STREAM_CODEC, ModNetwork::handleNameCardSave);
    }

    private static void handleNameCardOpen(NameCardOpenPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) return;
        context.enqueueWork(() -> {
            try {
                Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.NameCardClientHooks");
                hooks.getMethod("open", NameCardOpenPayload.class).invoke(null, payload);
            } catch (ReflectiveOperationException exception) {
                PowerRadar.LOGGER.error("[PowerRadar] Failed to open name card screen", exception);
            }
        });
    }

    private static void handleNameCardSave(NameCardSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            var stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof NameCardItem)) return;
            String name = payload.name().trim();
            if (name.length() > 64) name = name.substring(0, 64);
            if (name.isEmpty()) stack.remove(ModDataComponents.NAME_CARD_NAME.get());
            else stack.set(ModDataComponents.NAME_CARD_NAME.get(), name);
        });
    }

    private static void handleSnapshot(RadarMonitorSnapshotPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> invokeClientSnapshotHandler(payload));
    }

    private static void invokeClientSnapshotHandler(RadarMonitorSnapshotPayload payload) {
        try {
            Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.RadarMonitorClientHooks");
            hooks.getMethod("handleSnapshot", RadarMonitorSnapshotPayload.class).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            PowerRadar.LOGGER.error("[PowerRadar] Failed to open radar monitor screen", exception);
        }
    }

    private static void handleBlockSnapshot(RadarMonitorBlockSnapshotPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> invokeClientBlockSnapshotHandler(payload));
    }

    private static void handleBlockStatic(RadarMonitorBlockStaticPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> invokeClientBlockStaticHandler(payload));
    }

    private static void handleBlockTargets(RadarMonitorBlockTargetsPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> invokeClientBlockTargetsHandler(payload));
    }

    private static void handleBlockPose(RadarMonitorBlockPosePayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> invokeClientBlockPoseHandler(payload));
    }

    private static void handleSilhouette(RadarMonitorSilhouettePayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> invokeClientSilhouetteHandler(payload));
    }

    private static void invokeClientSilhouetteHandler(RadarMonitorSilhouettePayload payload) {
        try {
            Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.RadarMonitorClientHooks");
            hooks.getMethod("handleSilhouette", RadarMonitorSilhouettePayload.class).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            PowerRadar.LOGGER.error("[PowerRadar] Failed to update Sable silhouette cache", exception);
        }
    }

    private static void invokeClientBlockPoseHandler(RadarMonitorBlockPosePayload payload) {
        try {
            Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.RadarMonitorClientHooks");
            hooks.getMethod("handleBlockPose", RadarMonitorBlockPosePayload.class).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            PowerRadar.LOGGER.error("[PowerRadar] Failed to update moving radar monitor pose", exception);
        }
    }

    private static void invokeClientBlockSnapshotHandler(RadarMonitorBlockSnapshotPayload payload) {
        try {
            Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.RadarMonitorClientHooks");
            hooks.getMethod("handleBlockSnapshot", RadarMonitorBlockSnapshotPayload.class).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            PowerRadar.LOGGER.error("[PowerRadar] Failed to update radar monitor block snapshot", exception);
        }
    }

    private static void invokeClientBlockStaticHandler(RadarMonitorBlockStaticPayload payload) {
        try {
            Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.RadarMonitorClientHooks");
            hooks.getMethod("handleBlockStatic", RadarMonitorBlockStaticPayload.class).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            PowerRadar.LOGGER.error("[PowerRadar] Failed to update radar monitor block static data", exception);
        }
    }

    private static void invokeClientBlockTargetsHandler(RadarMonitorBlockTargetsPayload payload) {
        try {
            Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.RadarMonitorClientHooks");
            hooks.getMethod("handleBlockTargets", RadarMonitorBlockTargetsPayload.class).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            PowerRadar.LOGGER.error("[PowerRadar] Failed to update radar monitor block target data", exception);
        }
    }

    private static void handleTargetingCardOpen(TargetingCardOpenPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> {
            try {
                Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.TargetingCardClientHooks");
                hooks.getMethod("open", TargetingCardOpenPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
                PowerRadar.LOGGER.error("[PowerRadar] Failed to open targeting card screen", exception);
            }
        });
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
        if (!FMLEnvironment.dist.isClient()) return;
        context.enqueueWork(() -> {
            try {
                Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.AllowlistCardClientHooks");
                hooks.getMethod("open", AllowlistCardOpenPayload.class).invoke(null, payload);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
                PowerRadar.LOGGER.error("[PowerRadar] Failed to open allowlist card screen", exception);
            }
        });
    }

    private static void handleAllowlistCardSave(AllowlistCardSavePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            var stack = player.getItemInHand(payload.hand());
            if (!(stack.getItem() instanceof RadarFilterCardItem card)
                    || card.kind() != RadarFilterCardItem.Kind.ALLOWLIST) {
                return;
            }
            RadarFilterCardItem.AllowlistData submitted = RadarFilterCardItem.decodeAllowlistLines(
                    payload.storedNames(), payload.sableMode());
            java.util.LinkedHashMap<java.util.UUID, RadarFilterCardItem.SableAllowlistEntry> sables =
                    new java.util.LinkedHashMap<>();
            for (RadarFilterCardItem.SableAllowlistEntry entry : submitted.sableEntries()) {
                String displayName = SableStructureNames.name(player.getServer(), entry.structureUuid())
                        .orElse(entry.displayName());
                sables.putIfAbsent(entry.structureUuid(),
                        new RadarFilterCardItem.SableAllowlistEntry(entry.structureUuid(), displayName));
            }
            for (String requestedName : submitted.unresolvedSableNames()) {
                SableStructureNames.matchingName(player.getServer(), requestedName).forEach((uuid, displayName) ->
                        sables.putIfAbsent(uuid, new RadarFilterCardItem.SableAllowlistEntry(uuid, displayName)));
                if (sables.size() >= 1024) break;
            }
            RadarFilterCardItem.AllowlistData resolved = new RadarFilterCardItem.AllowlistData(
                    submitted.playerNames(),
                    sables.values().stream().limit(1024).toList(),
                    java.util.List.of());
            stack.set(ModDataComponents.RADAR_ALLOWLIST.get(), String.join("\n", resolved.encodedLines()));
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
