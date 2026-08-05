package com.limbo2136.powerradar.radar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Общий двадцатитиковый снимок имён игроков для блочного и панельного мониторов. */
public final class OnlinePlayersSnapshotCache {
    private static final long REFRESH_INTERVAL_TICKS = 20L;
    private static final Map<MinecraftServer, ServerCache> CACHES = new WeakHashMap<>();

    private OnlinePlayersSnapshotCache() {
    }

    public static Snapshot snapshot(ServerLevel level) {
        return CACHES.computeIfAbsent(level.getServer(), ignored -> new ServerCache()).snapshot(level);
    }

    public static void stopServer(MinecraftServer server) {
        CACHES.remove(server);
    }

    public record Snapshot(List<String> names, int hash) {
    }

    private static final class ServerCache {
        private long lastRefreshGameTime = Long.MIN_VALUE;
        private Snapshot snapshot = new Snapshot(List.of(), 1);

        private Snapshot snapshot(ServerLevel level) {
            long gameTime = level.getGameTime();
            long age = gameTime - this.lastRefreshGameTime;
            if (this.lastRefreshGameTime != Long.MIN_VALUE && age >= 0L && age < REFRESH_INTERVAL_TICKS) {
                return this.snapshot;
            }
            List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
            List<String> names = new ArrayList<>(players.size());
            for (ServerPlayer player : players) {
                names.add(player.getGameProfile().getName());
            }
            names.sort(String.CASE_INSENSITIVE_ORDER);
            int hash = 1;
            for (String name : names) {
                hash = 31 * hash + name.toLowerCase(Locale.ROOT).hashCode();
            }
            this.snapshot = new Snapshot(List.copyOf(names), hash);
            this.lastRefreshGameTime = gameTime;
            return this.snapshot;
        }
    }
}
