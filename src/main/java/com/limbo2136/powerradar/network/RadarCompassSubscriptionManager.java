package com.limbo2136.powerradar.network;

import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.radar.network.SelectedTargetRuntimeSnapshot;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Хранит короткие подписки игрока на сети видимых компасов. Один поток сети обслуживает
 * любое количество одинаковых компасов в руке, OnBoard Computer и интерфейсах.
 */
public final class RadarCompassSubscriptionManager {
    private static final long SUBSCRIPTION_TIMEOUT_TICKS = 40L;
    private static final Map<MinecraftServer, RadarCompassSubscriptionManager> MANAGERS =
            new WeakHashMap<>();

    private final MinecraftServer server;
    private final Map<SubscriptionKey, Subscription> subscriptions = new HashMap<>();

    private RadarCompassSubscriptionManager(MinecraftServer server) {
        this.server = server;
    }

    public static void subscribe(ServerPlayer player, UUID networkId) {
        RadarCompassSubscriptionManager manager = MANAGERS.computeIfAbsent(
                player.server, RadarCompassSubscriptionManager::new);
        manager.subscribePlayer(player, networkId);
    }

    public static void tickServer(MinecraftServer server) {
        RadarCompassSubscriptionManager manager = MANAGERS.get(server);
        if (manager != null) {
            manager.publish();
        }
    }

    public static void stopServer(MinecraftServer server) {
        MANAGERS.remove(server);
    }

    private void subscribePlayer(ServerPlayer player, UUID networkId) {
        long gameTime = this.server.overworld().getGameTime();
        RadarNetworkManager radarNetworks = RadarNetworkManager.get(this.server);
        if (!radarNetworks.networkExists(networkId)) {
            send(player, networkId, SelectedTargetRuntimeSnapshot.EMPTY, gameTime);
            return;
        }
        SubscriptionKey key = new SubscriptionKey(player.getUUID(), networkId);
        Subscription subscription = this.subscriptions.computeIfAbsent(key, ignored -> new Subscription());
        subscription.lastSeenGameTime = gameTime;
        SelectedTargetRuntimeSnapshot snapshot = radarNetworks.selectedTargetSnapshot(networkId);
        send(player, networkId, snapshot, gameTime);
        subscription.lastSentRevision = snapshot.revision();
    }

    // Активная цель меняет ревизию каждый тик; неподтверждённая отправляется только при смене состояния.
    private void publish() {
        long gameTime = this.server.overworld().getGameTime();
        RadarNetworkManager radarNetworks = RadarNetworkManager.get(this.server);
        Iterator<Map.Entry<SubscriptionKey, Subscription>> iterator =
                this.subscriptions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SubscriptionKey, Subscription> entry = iterator.next();
            SubscriptionKey key = entry.getKey();
            Subscription subscription = entry.getValue();
            ServerPlayer player = this.server.getPlayerList().getPlayer(key.playerUuid);
            if (player == null
                    || gameTime - subscription.lastSeenGameTime > SUBSCRIPTION_TIMEOUT_TICKS) {
                iterator.remove();
                continue;
            }
            SelectedTargetRuntimeSnapshot snapshot =
                    radarNetworks.selectedTargetSnapshot(key.networkId);
            if (snapshot.revision() == subscription.lastSentRevision) {
                continue;
            }
            send(player, key.networkId, snapshot, gameTime);
            subscription.lastSentRevision = snapshot.revision();
        }
    }

    private static void send(
            ServerPlayer player,
            UUID networkId,
            SelectedTargetRuntimeSnapshot snapshot,
            long fallbackGameTime
    ) {
        TrackedTargetView target = snapshot.alive() ? snapshot.target() : null;
        PacketDistributor.sendToPlayer(player, new RadarCompassTargetPayload(
                networkId,
                snapshot.revision(),
                snapshot.selectedTargetUuid(),
                snapshot.confirmedByLatestScan(),
                snapshot.alive(),
                target == null ? null : target.dimensionId(),
                target == null ? null : target.position(),
                target == null ? Vec3.ZERO : target.velocity(),
                snapshot.serverGameTime() > 0L ? snapshot.serverGameTime() : fallbackGameTime));
    }

    private record SubscriptionKey(UUID playerUuid, UUID networkId) {
    }

    private static final class Subscription {
        private long lastSeenGameTime = Long.MIN_VALUE;
        private long lastSentRevision = Long.MIN_VALUE;
    }
}
