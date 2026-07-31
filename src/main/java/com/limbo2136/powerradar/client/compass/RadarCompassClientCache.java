package com.limbo2136.powerradar.client.compass;

import com.limbo2136.powerradar.network.RadarCompassTargetPayload;
import com.limbo2136.powerradar.network.RadarCompassSubscriptionPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Подписочный клиентский кэш координат; gameplay-решения его не используют. */
public final class RadarCompassClientCache {
    private static final long SUBSCRIPTION_KEEPALIVE_TICKS = 20L;
    private static final double MAX_EXTRAPOLATION_TICKS = 3.0D;
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static ClientLevel cachedLevel;

    private RadarCompassClientCache() {
    }

    @Nullable
    public static Target target(UUID networkId, ClientLevel level) {
        float partialTick = net.minecraft.client.Minecraft.getInstance()
                .getTimer().getGameTimeDeltaPartialTick(false);
        return target(networkId, level, partialTick);
    }

    @Nullable
    public static Target target(UUID networkId, ClientLevel level, float partialTick) {
        clearIfLevelChanged(level);
        Entry entry = ENTRIES.computeIfAbsent(networkId, ignored -> new Entry());
        long gameTime = level.getGameTime();
        if (entry.lastKeepaliveGameTime == Long.MIN_VALUE
                || gameTime - entry.lastKeepaliveGameTime >= SUBSCRIPTION_KEEPALIVE_TICKS) {
            entry.lastKeepaliveGameTime = gameTime;
            PacketDistributor.sendToServer(new RadarCompassSubscriptionPayload(networkId));
        }
        if (!entry.confirmedByLatestScan || !entry.alive || entry.targetUuid == null
                || entry.dimensionId == null || entry.position == null) {
            return null;
        }
        Vec3 predictedPosition = extrapolate(
                entry.position,
                entry.velocity,
                entry.serverGameTime,
                gameTime + partialTick);
        return new Target(
                entry.targetUuid,
                entry.dimensionId,
                predictedPosition,
                entry.velocity,
                entry.serverGameTime);
    }

    public static void apply(RadarCompassTargetPayload payload) {
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        clearIfLevelChanged(level);
        Entry entry = ENTRIES.computeIfAbsent(payload.networkId(), ignored -> new Entry());
        entry.targetUuid = payload.targetUuid();
        entry.confirmedByLatestScan = payload.confirmedByLatestScan();
        entry.alive = payload.alive();
        entry.dimensionId = payload.dimensionId();
        entry.position = payload.position();
        entry.velocity = payload.velocity();
        entry.serverGameTime = payload.serverGameTime();
    }

    private static void clearIfLevelChanged(ClientLevel level) {
        if (cachedLevel != level) {
            cachedLevel = level;
            ENTRIES.clear();
        }
    }

    static Vec3 extrapolate(
            Vec3 position,
            Vec3 velocity,
            long serverGameTime,
            double clientRenderGameTime
    ) {
        double elapsedTicks = Math.max(0.0D, Math.min(
                MAX_EXTRAPOLATION_TICKS,
                clientRenderGameTime - serverGameTime));
        return position.add(velocity.scale(elapsedTicks));
    }

    public record Target(
            UUID targetUuid,
            ResourceLocation dimensionId,
            Vec3 position,
            Vec3 velocity,
            long serverGameTime
    ) {
    }

    private static final class Entry {
        private long lastKeepaliveGameTime = Long.MIN_VALUE;
        private UUID targetUuid;
        private boolean confirmedByLatestScan;
        private boolean alive;
        private ResourceLocation dimensionId;
        private Vec3 position;
        private Vec3 velocity = Vec3.ZERO;
        private long serverGameTime;
    }
}
