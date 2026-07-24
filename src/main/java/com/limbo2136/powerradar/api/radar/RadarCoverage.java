package com.limbo2136.powerradar.api.radar;

import com.limbo2136.powerradar.radar.RadarCoverageShape;
import com.limbo2136.powerradar.radar.RadarScanMode;
import com.limbo2136.powerradar.radar.RadarStructureType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Снимок зоны обзора радара в мировых координатах.
 * Дальность и вертикальные смещения заданы в блоках, горизонтальный поворот — в градусах.
 */
public record RadarCoverage(
        Vec3 origin,
        int rangeBlocks,
        int verticalMinOffset,
        int verticalMaxOffset,
        Direction facing,
        float yawDegrees,
        RadarCoverageShape shape,
        RadarScanMode scanMode,
        RadarStructureType structureType
) {
    /** Нулевая дальность означает, что источник сейчас не участвует в обнаружении. */
    public boolean active() {
        return this.rangeBlocks > 0;
    }
}
