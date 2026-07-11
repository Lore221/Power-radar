package com.limbo2136.powerradar.radar.network;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;

public class RadarNetworkRecord {
    public static final int SCHEMA_VERSION = 3;

    private final UUID id;
    private final int schemaVersion;
    private final Set<GlobalPos> linkNodes = new LinkedHashSet<>();
    private final Set<RadarControllerEndpointBinding> controllerBindings = new LinkedHashSet<>();
    private final Set<String> whitelistedPlayerNames = new LinkedHashSet<>();
    private final Set<String> whitelistedSableNames = new LinkedHashSet<>();
    private UUID selectedTargetUuid;
    private int autotargetFilterMask;

    public RadarNetworkRecord(UUID id) {
        this(id, SCHEMA_VERSION);
    }

    public RadarNetworkRecord(UUID id, int schemaVersion) {
        this.id = id;
        this.schemaVersion = schemaVersion;
    }

    public UUID id() {
        return this.id;
    }

    public int schemaVersion() {
        return this.schemaVersion;
    }

    public Set<GlobalPos> linkNodes() {
        return this.linkNodes;
    }

    public Set<RadarControllerEndpointBinding> controllerBindings() {
        return this.controllerBindings;
    }

    public Set<String> whitelistedPlayerNames() {
        return this.whitelistedPlayerNames;
    }

    public Set<String> whitelistedSableNames() {
        return this.whitelistedSableNames;
    }

    public UUID selectedTargetUuid() {
        return this.selectedTargetUuid;
    }

    public void setSelectedTargetUuid(UUID selectedTargetUuid) {
        this.selectedTargetUuid = selectedTargetUuid;
    }

    public int autotargetFilterMask() {
        return this.autotargetFilterMask;
    }

    public void setAutotargetFilterMask(int autotargetFilterMask) {
        this.autotargetFilterMask = autotargetFilterMask;
    }
}
