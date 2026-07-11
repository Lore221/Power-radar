package com.limbo2136.powerradar.radar.network;

import com.limbo2136.powerradar.radar.RadarMonitorDisplayData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;

public class RadarNetworkRuntime {
    private final Set<GlobalPos> loadedLinks = new HashSet<>();
    private final Map<GlobalPos, GlobalPos> monitorLinkToMonitorPos = new HashMap<>();
    private final RadarNetworkChunkLoadState chunkLoadState = new RadarNetworkChunkLoadState();
    private UUID selectedTargetUuid;
    private int autotargetFilterMask;
    private final LinkedHashSet<String> whitelistedPlayerNames = new LinkedHashSet<>();
    private final LinkedHashSet<String> whitelistedSableNames = new LinkedHashSet<>();
    private DisplaySnapshotCacheEntry displaySnapshot;
    private long settingsRevision;

    public Set<GlobalPos> loadedLinks() {
        return this.loadedLinks;
    }

    public Map<GlobalPos, GlobalPos> monitorLinkToMonitorPos() {
        return this.monitorLinkToMonitorPos;
    }

    public RadarNetworkChunkLoadState chunkLoadState() {
        return this.chunkLoadState;
    }

    public Optional<UUID> selectedTargetUuid() {
        return Optional.ofNullable(this.selectedTargetUuid);
    }

    public void setSelectedTargetUuid(UUID selectedTargetUuid) {
        if (!java.util.Objects.equals(this.selectedTargetUuid, selectedTargetUuid)) {
            this.selectedTargetUuid = selectedTargetUuid;
            this.settingsRevision++;
            invalidateDisplaySnapshots();
        }
    }

    public int autotargetFilterMask() {
        return this.autotargetFilterMask;
    }

    public void setAutotargetFilterMask(int autotargetFilterMask) {
        if (this.autotargetFilterMask != autotargetFilterMask) {
            this.autotargetFilterMask = autotargetFilterMask;
            this.settingsRevision++;
            invalidateDisplaySnapshots();
        }
    }

    public List<String> whitelistedPlayerNames() {
        return List.copyOf(this.whitelistedPlayerNames);
    }

    public List<String> whitelistedSableNames() {
        return List.copyOf(this.whitelistedSableNames);
    }

    public boolean isPlayerWhitelisted(String playerName) {
        return playerName != null && this.whitelistedPlayerNames.stream().anyMatch(playerName::equalsIgnoreCase);
    }

    public void addWhitelistedPlayerName(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            if (this.whitelistedPlayerNames.add(playerName.trim())) {
                this.settingsRevision++;
                invalidateDisplaySnapshots();
            }
        }
    }

    public void removeWhitelistedPlayerName(String playerName) {
        if (playerName != null) {
            if (this.whitelistedPlayerNames.removeIf(playerName::equalsIgnoreCase)) {
                this.settingsRevision++;
                invalidateDisplaySnapshots();
            }
        }
    }

    public void loadWhitelists(List<String> playerNames, List<String> sableNames) {
        this.whitelistedPlayerNames.clear();
        this.whitelistedSableNames.clear();
        playerNames.forEach(this::addWhitelistedPlayerName);
        sableNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .forEach(this.whitelistedSableNames::add);
    }

    public void loadPersistentSettings(UUID selectedTargetUuid, int autotargetFilterMask) {
        this.selectedTargetUuid = selectedTargetUuid;
        this.autotargetFilterMask = autotargetFilterMask;
        this.settingsRevision++;
        invalidateDisplaySnapshots();
    }

    public long settingsRevision() {
        return this.settingsRevision;
    }

    public DisplaySnapshotCacheEntry displaySnapshot() {
        return this.displaySnapshot;
    }

    public void putDisplaySnapshot(long revision, RadarMonitorDisplayData data) {
        this.displaySnapshot = new DisplaySnapshotCacheEntry(revision, data);
    }

    public void invalidateDisplaySnapshots() {
        this.displaySnapshot = null;
    }

    public record DisplaySnapshotCacheEntry(long revision, RadarMonitorDisplayData data) {
    }
}
