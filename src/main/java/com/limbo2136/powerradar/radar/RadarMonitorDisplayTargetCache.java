package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.RadarConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;

/**
 * Краткоживущий кэш видимости меток монитора. Он хранит последнюю видимую позицию только для
 * плавного затухания и не является источником игровых решений.
 */
public final class RadarMonitorDisplayTargetCache {
    private static final ResourceLocation RADAR_STRUCTURE_TYPE =
            ResourceLocation.fromNamespaceAndPath(PowerRadar.MOD_ID, "radar_structure");
    private static final double RADAR_MARKER_BIND_DISTANCE_SQUARED = 16.0D;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, RadarId> radarMarkerBindings = new HashMap<>();

    public RadarMonitorDisplayData update(RadarMonitorDisplayData displayData, long serverGameTime) {
        return update(displayData, serverGameTime, Set.of());
    }

    public RadarMonitorDisplayData update(
            RadarMonitorDisplayData displayData,
            long serverGameTime,
            Set<RadarId> fullScanRadarIds
    ) {
        if (!displayData.linked() || !displayData.structureValid() || displayData.currentRange() <= 0) {
            this.entries.clear();
            this.radarMarkerBindings.clear();
            return displayData;
        }

        ArrayList<RadarDisplayTarget> visibleTargets = new ArrayList<>(displayData.targets().size());
        Set<String> sourceKeys = new HashSet<>();
        int expirationTicks = expirationTicks();
        for (RadarDisplayTarget sourceTarget : displayData.targets()) {
            String key = sourceTarget.stableSelectionKey();
            sourceKeys.add(key);
            Entry entry = this.entries.get(key);
            RadarDisplayTarget anchoredMarker = anchoredRadarMarker(displayData, sourceTarget);
            if (anchoredMarker != null) {
                this.entries.put(key, new Entry(anchoredMarker, serverGameTime));
                visibleTargets.add(anchoredMarker.withDisplayAgeTicks(0));
                continue;
            }
            boolean freshFromRadar = sourceTarget.displayAgeTicks() <= 0;
            boolean insideCoverage = isTargetInsideDisplayCoverage(displayData, sourceTarget);
            if (freshFromRadar && insideCoverage) {
                Entry refreshed = new Entry(sourceTarget, serverGameTime);
                this.entries.put(key, refreshed);
                visibleTargets.add(sourceTarget.withDisplayAgeTicks(0));
                continue;
            }

            if (freshFromRadar && isTargetInsideFullScanRange(displayData, sourceTarget, fullScanRadarIds)) {
                long lastVisibleGameTime = entry == null
                        ? Long.MIN_VALUE
                        : entry.lastVisibleGameTime();
                RadarDisplayTarget frozenTarget = entry == null
                        ? sourceTarget
                        : entry.target();
                this.entries.put(key, new Entry(frozenTarget, lastVisibleGameTime));
                continue;
            }

            if (entry == null || entry.lastVisibleGameTime() == Long.MIN_VALUE) {
                continue;
            }

            long staleAgeTicks = Math.max(1L, serverGameTime - entry.lastVisibleGameTime());
            if (!freshFromRadar) {
                staleAgeTicks = Math.max(staleAgeTicks, sourceTarget.displayAgeTicks());
            }
            if (staleAgeTicks >= expirationTicks) {
                this.entries.remove(key);
                continue;
            }
            visibleTargets.add(entry.target().withDisplayAgeTicks((int) staleAgeTicks));
        }

        // Пропавший из исходного снимка ключ удаляется сразу: радар уже признал цель мёртвой,
        // отсутствующей или просроченной, поэтому монитор не должен продлевать её жизнь сам.
        this.entries.keySet().removeIf(key -> !sourceKeys.contains(key));
        this.radarMarkerBindings.keySet().removeIf(key -> !sourceKeys.contains(key));
        return displayData.withTargets(visibleTargets);
    }

    public RadarMonitorDisplayData refilter(RadarMonitorDisplayData displayData, long clientGameTime) {
        if (!displayData.linked() || !displayData.structureValid() || displayData.currentRange() <= 0) {
            return displayData.withTargets(java.util.List.of());
        }
        ArrayList<RadarDisplayTarget> visibleTargets = new ArrayList<>(displayData.targets().size());
        int expirationTicks = expirationTicks();
        for (RadarDisplayTarget target : displayData.targets()) {
            String key = target.stableSelectionKey();
            RadarDisplayTarget anchoredMarker = anchoredRadarMarker(displayData, target);
            if (anchoredMarker != null) {
                this.entries.put(key, new Entry(anchoredMarker, clientGameTime));
                visibleTargets.add(anchoredMarker.withDisplayAgeTicks(0));
                continue;
            }
            if (isTargetInsideDisplayCoverage(displayData, target)) {
                this.entries.put(key, new Entry(target, clientGameTime));
                visibleTargets.add(target.withDisplayAgeTicks(0));
                continue;
            }
            Entry entry = this.entries.get(key);
            if (entry == null || entry.lastVisibleGameTime() == Long.MIN_VALUE) {
                continue;
            }
            long ticksOutsideCone = Math.max(1L, clientGameTime - entry.lastVisibleGameTime());
            long visualAgeTicks = Math.max(0, RadarConstants.RADAR_MONITOR_BLIP_FADE_DELAY_TICKS)
                    + ticksOutsideCone;
            if (visualAgeTicks >= expirationTicks) {
                this.entries.remove(key);
                continue;
            }
            visibleTargets.add(entry.target().withDisplayAgeTicks((int) visualAgeTicks));
        }
        return displayData.withTargets(visibleTargets);
    }

    private RadarDisplayTarget anchoredRadarMarker(
            RadarMonitorDisplayData displayData,
            RadarDisplayTarget target
    ) {
        if (!RADAR_STRUCTURE_TYPE.equals(target.entityTypeId())) {
            return null;
        }
        String key = target.stableSelectionKey();
        RadarId boundRadarId = this.radarMarkerBindings.get(key);
        RadarDisplayCoverage boundCoverage = coverageById(displayData, boundRadarId);
        if (boundCoverage == null) {
            boundCoverage = nearestCoverage(displayData, target);
            if (boundCoverage == null) {
                return null;
            }
            this.radarMarkerBindings.put(key, boundCoverage.radarId());
        }
        return target.withPosition(
                boundCoverage.originX(), boundCoverage.originY(), boundCoverage.originZ());
    }

    private static RadarDisplayCoverage coverageById(RadarMonitorDisplayData displayData, RadarId radarId) {
        if (radarId == null) {
            return null;
        }
        for (RadarDisplayCoverage coverage : displayData.coverages()) {
            if (radarId.equals(coverage.radarId())) {
                return coverage;
            }
        }
        return null;
    }

    private static RadarDisplayCoverage nearestCoverage(
            RadarMonitorDisplayData displayData,
            RadarDisplayTarget target
    ) {
        RadarDisplayCoverage nearest = null;
        double nearestDistanceSquared = RADAR_MARKER_BIND_DISTANCE_SQUARED;
        for (RadarDisplayCoverage coverage : displayData.coverages()) {
            if (!target.dimensionId().equals(coverage.dimensionId())) {
                continue;
            }
            double dx = target.x() - coverage.originX();
            double dy = target.y() - coverage.originY();
            double dz = target.z() - coverage.originZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= nearestDistanceSquared) {
                nearest = coverage;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private static boolean isTargetInsideFullScanRange(
            RadarMonitorDisplayData displayData,
            RadarDisplayTarget target,
            Set<RadarId> fullScanRadarIds
    ) {
        for (RadarDisplayCoverage coverage : displayData.coverages()) {
            if (!fullScanRadarIds.contains(coverage.radarId())
                    || !target.dimensionId().equals(coverage.dimensionId())) {
                continue;
            }
            double dx = target.x() - coverage.originX();
            double dz = target.z() - coverage.originZ();
            boolean insideRange = coverage.orientationState().structureType() == RadarStructureType.OVERVIEW
                    ? RadarCoverageFilter.isInsideOverviewFootprint(dx, dz, coverage.currentRange())
                    : dx * dx + dz * dz <= (double) coverage.currentRange() * coverage.currentRange();
            if (insideRange) {
                return true;
            }
        }
        return false;
    }

    private static int expirationTicks() {
        return Math.max(1, RadarConstants.RADAR_MONITOR_BLIP_FADE_DELAY_TICKS
                + RadarConstants.RADAR_MONITOR_BLIP_FADE_TICKS);
    }

    private static boolean isTargetInsideDisplayCoverage(RadarMonitorDisplayData displayData, RadarDisplayTarget target) {
        if (!displayData.coverages().isEmpty()) {
            for (RadarDisplayCoverage coverage : displayData.coverages()) {
                if (isTargetInsideCoverage(coverage, target, displayData.serverGameTime())) {
                    return true;
                }
            }
            return false;
        }
        return isTargetInsideLegacyCoverage(displayData, target);
    }

    private static boolean isTargetInsideCoverage(RadarDisplayCoverage coverage, RadarDisplayTarget target, long serverGameTime) {
        if (!target.dimensionId().equals(coverage.dimensionId())) {
            return false;
        }
        double dx = target.x() - coverage.originX();
        double dz = target.z() - coverage.originZ();
        double range = Math.max(1.0D, coverage.currentRange());
        if (coverage.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return RadarCoverageFilter.isInsideOverviewFootprint(dx, dz, range);
        }
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance > range) {
            return false;
        }
        double bearing = RadarCoverageFilter.bearingDegrees(
                coverage.orientationState().yawAt(serverGameTime),
                new Vec3(dx, 0.0D, dz));
        return Math.abs(bearing) <= coverage.sectorAngle() / 2.0D;
    }

    private static boolean isTargetInsideLegacyCoverage(RadarMonitorDisplayData displayData, RadarDisplayTarget target) {
        if (!target.dimensionId().equals(displayData.radarDimensionId())) {
            return false;
        }
        double dx = target.x() - displayData.radarOriginX();
        double dz = target.z() - displayData.radarOriginZ();
        double range = Math.max(1.0D, displayData.currentRange());
        if (displayData.orientationState().structureType() == RadarStructureType.OVERVIEW) {
            return RadarCoverageFilter.isInsideOverviewFootprint(dx, dz, range);
        }
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance > range) {
            return false;
        }
        double bearing = RadarCoverageFilter.bearingDegrees(
                displayData.orientationState().yawAt(displayData.serverGameTime()),
                new Vec3(dx, 0.0D, dz));
        return Math.abs(bearing) <= displayData.sectorAngle() / 2.0D;
    }

    private record Entry(RadarDisplayTarget target, long lastVisibleGameTime) {
    }
}
