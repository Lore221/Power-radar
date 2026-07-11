package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.limbo2136.powerradar.radar.network.RadarLinkConnectionResolver;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
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
    private ModNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Snapshot payload remains a lightweight full monitor view. TODO: add a v2 delta payload if full snapshots become measurable.
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RadarMonitorSnapshotPayload.TYPE, RadarMonitorSnapshotPayload.STREAM_CODEC, ModNetwork::handleSnapshot);
        registrar.playToClient(RadarMonitorBlockSnapshotPayload.TYPE, RadarMonitorBlockSnapshotPayload.STREAM_CODEC, ModNetwork::handleBlockSnapshot);
        registrar.playToClient(RadarMonitorBlockStaticPayload.TYPE, RadarMonitorBlockStaticPayload.STREAM_CODEC, ModNetwork::handleBlockStatic);
        registrar.playToClient(RadarMonitorBlockTargetsPayload.TYPE, RadarMonitorBlockTargetsPayload.STREAM_CODEC, ModNetwork::handleBlockTargets);
        registrar.playToClient(RadarControllerSnapshotPayload.TYPE, RadarControllerSnapshotPayload.STREAM_CODEC, ModNetwork::handleControllerSnapshot);
        registrar.playToServer(RadarMonitorRequestPayload.TYPE, RadarMonitorRequestPayload.STREAM_CODEC, ModNetwork::handleRequest);
        registrar.playToServer(RadarMonitorSettingsPayload.TYPE, RadarMonitorSettingsPayload.STREAM_CODEC, ModNetwork::handleSettings);
        registrar.playToServer(RadarMonitorTargetSelectionPayload.TYPE, RadarMonitorTargetSelectionPayload.STREAM_CODEC, ModNetwork::handleTargetSelection);
        registrar.playToServer(RadarMonitorWhitelistPayload.TYPE, RadarMonitorWhitelistPayload.STREAM_CODEC, ModNetwork::handleWhitelist);
        registrar.playToServer(RadarControllerSettingsPayload.TYPE, RadarControllerSettingsPayload.STREAM_CODEC, ModNetwork::handleControllerSettings);
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

    private static void handleControllerSnapshot(RadarControllerSnapshotPayload payload, IPayloadContext context) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        context.enqueueWork(() -> invokeClientControllerSnapshotHandler(payload));
    }

    private static void invokeClientControllerSnapshotHandler(RadarControllerSnapshotPayload payload) {
        try {
            Class<?> hooks = Class.forName("com.limbo2136.powerradar.client.RadarControllerClientHooks");
            hooks.getMethod("handleSnapshot", RadarControllerSnapshotPayload.class).invoke(null, payload);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            PowerRadar.LOGGER.error("[PowerRadar] Failed to open radar controller screen", exception);
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

    private static void handleSettings(RadarMonitorSettingsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RadarMonitorSnapshotPayload snapshot = RadarMonitorControllerBlockEntity.applySettingsFromMonitor(
                    player,
                    payload.monitorPos(),
                    payload.mode(),
                    payload.detectionFilterMask(),
                    payload.autotargetFilterMask(),
                    payload.targetTrajectoryMode());
            PacketDistributor.sendToPlayer(player, snapshot);
        }
    }

    private static void handleControllerSettings(RadarControllerSettingsPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.serverLevel().getBlockEntity(payload.controllerPos()) instanceof com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity controller) {
            controller.applyMonitorSettings(payload.mode(), payload.detectionFilterMask(), payload.targetTrajectoryMode());
            PacketDistributor.sendToPlayer(player, RadarControllerSnapshotPayload.fromController(controller));
        }
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
            RadarNetworkManager.ControllersResolution controllerResolution = manager.resolveControllersForConsumer(
                    linkResolution.link().networkId(),
                    GlobalPos.of(player.serverLevel().dimension(), linkResolution.link().getBlockPos()));
            if (payload.targetUuid() != null
                    && controllerResolution.controllers().stream()
                    .noneMatch(controller -> controller.findTargetTrack(payload.targetUuid()) != null)) {
                return;
            }
            manager.setSelectedTargetUuid(linkResolution.link().networkId(), payload.targetUuid());
            PowerRadar.LOGGER.info("[PowerRadar] Target selection changed from monitor network={} target={}",
                    linkResolution.link().networkId(), payload.targetUuid());
        });
    }

    private static void handleWhitelist(RadarMonitorWhitelistPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        context.enqueueWork(() -> {
            RadarLinkConnectionResolver.Resolution linkResolution =
                    RadarLinkConnectionResolver.findSingleLinkFacingEndpoint(player.serverLevel(), payload.monitorPos());
            if (linkResolution.status() != RadarLinkConnectionResolver.Status.SINGLE
                    || linkResolution.link().networkId() == null
                    || payload.value() == null
                    || payload.value().isBlank()) {
                return;
            }
            RadarNetworkManager manager = RadarNetworkManager.get(player.server);
            switch (payload.action()) {
                case ADD_PLAYER -> manager.addWhitelistedPlayerName(linkResolution.link().networkId(), payload.value());
                case REMOVE_PLAYER -> manager.removeWhitelistedPlayerName(linkResolution.link().networkId(), payload.value());
            }
            PacketDistributor.sendToPlayer(player,
                    RadarMonitorControllerBlockEntity.getOrCreateSnapshotPayload(player.serverLevel(), payload.monitorPos()));
        });
    }
}
