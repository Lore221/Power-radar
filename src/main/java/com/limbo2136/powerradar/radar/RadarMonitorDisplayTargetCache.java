package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.RadarConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

public final class RadarMonitorDisplayTargetCache {
    private final Map<String, Entry> entries = new HashMap<>();

    public RadarMonitorDisplayData update(RadarMonitorDisplayData displayData, long serverGameTime) {
        if (!displayData.linked() || !displayData.structureValid() || displayData.currentRange() <= 0) {
            this.entries.clear();
            return displayData;
        }

        ArrayList<RadarDisplayTarget> visibleTargets = new ArrayList<>(displayData.targets().size());
        Set<String> sourceKeys = new HashSet<>();
        int expirationTicks = expirationTicks();
        for (RadarDisplayTarget sourceTarget : displayData.targets()) {
            String key = sourceTarget.stableSelectionKey();
            sourceKeys.add(key);
            Entry entry = this.entries.get(key);
            boolean freshFromRadar = sourceTarget.displayAgeTicks() <= 0;
            boolean insideCoverage = isTargetInsideDisplayCoverage(displayData, sourceTarget);
            if (freshFromRadar && insideCoverage) {
                Entry refreshed = new Entry(sourceTarget, serverGameTime);
                this.entries.put(key, refreshed);
                visibleTargets.add(sourceTarget.withDisplayAgeTicks(0));
                continue;
            }

            if (entry == null) {
                continue;
            }

            long staleAgeTicks = Math.max(1L, serverGameTime - entry.lastFreshDisplayGameTime());
            if (!freshFromRadar) {
                staleAgeTicks = Math.max(staleAgeTicks, sourceTarget.displayAgeTicks());
            }
            if (staleAgeTicks >= expirationTicks) {
                this.entries.remove(key);
                continue;
            }
            visibleTargets.add(entry.target().withDisplayAgeTicks((int) staleAgeTicks));
        }

        // Missing source entries are removed quickly. If the radar cache no longer reports a target,
        // it was likely removed as dead/missing/expired and should not linger on the monitor.
        this.entries.keySet().removeIf(key -> !sourceKeys.contains(key));
        return displayData.withTargets(visibleTargets);
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
            double normalizedX = Math.abs(dx) / range;
            double normalizedZ = Math.abs(dz) / range;
            return normalizedX <= 1.0D
                    && normalizedZ <= 1.0D
                    && normalizedX + normalizedZ <= 1.68D;
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
            double normalizedX = Math.abs(dx) / range;
            double normalizedZ = Math.abs(dz) / range;
            return normalizedX <= 1.0D
                    && normalizedZ <= 1.0D
                    && normalizedX + normalizedZ <= 1.68D;
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

    private record Entry(RadarDisplayTarget target, long lastFreshDisplayGameTime) {
    }
}
