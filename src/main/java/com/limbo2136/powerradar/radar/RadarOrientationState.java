package com.limbo2136.powerradar.radar;

/** Опорный угол и скорость вращения в градусах и градусах/тик на указанное серверное время. */
public record RadarOrientationState(
        RadarStructureType structureType,
        float referenceYawDegrees,
        float rotationSpeedDegreesPerTick,
        long referenceGameTime
) {
    public static RadarOrientationState fixed(float yawDegrees, long gameTime) {
        return fixed(RadarStructureType.PHASED_ARRAY, yawDegrees, gameTime);
    }

    public static RadarOrientationState fixed(RadarStructureType structureType, float yawDegrees, long gameTime) {
        return new RadarOrientationState(structureType, RadarGeometry.normalizeDegrees(yawDegrees), 0.0F, gameTime);
    }

    public float yawAt(double gameTimeWithPartialTick) {
        return RadarGeometry.normalizeDegrees((float) (
                this.referenceYawDegrees + this.rotationSpeedDegreesPerTick * (gameTimeWithPartialTick - this.referenceGameTime)
        ));
    }
}
