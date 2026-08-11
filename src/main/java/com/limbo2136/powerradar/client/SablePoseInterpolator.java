package com.limbo2136.powerradar.client;

final class SablePoseInterpolator {
    private static final float MIN_AXIS_SOLVE_ANGLE_DEGREES = 0.5F;

    private SablePoseInterpolator() {
    }

    static Pose fromCenter(
            double centerX,
            double centerZ,
            float headingDegrees,
            float worldRotationPointOffsetX,
            float worldRotationPointOffsetZ
    ) {
        Offset localPivot = rotate(
                worldRotationPointOffsetX, worldRotationPointOffsetZ, -headingDegrees);
        return new Pose(
                centerX + worldRotationPointOffsetX,
                centerZ + worldRotationPointOffsetZ,
                (float) localPivot.x(),
                (float) localPivot.z(),
                SableHeadingInterpolator.normalize(headingDegrees));
    }

    static Pose interpolate(Pose start, Pose target, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        return new Pose(
                lerp(start.worldRotationPointX(), target.worldRotationPointX(), clamped),
                lerp(start.worldRotationPointZ(), target.worldRotationPointZ(), clamped),
                (float) lerp(start.localRotationPointOffsetX(), target.localRotationPointOffsetX(), clamped),
                (float) lerp(start.localRotationPointOffsetZ(), target.localRotationPointOffsetZ(), clamped),
                SableHeadingInterpolator.interpolate(start.headingDegrees(), target.headingDegrees(), clamped));
    }

    static Pose resolveRotationAxis(Pose previous, Pose target) {
        float headingDelta = SableHeadingInterpolator.shortestDelta(
                previous.headingDegrees(), target.headingDegrees());
        if (Math.abs(headingDelta) < 0.001F
                && Math.abs(previous.centerX() - target.centerX()) < 0.0001D
                && Math.abs(previous.centerZ() - target.centerZ()) < 0.0001D) {
            return withHeading(previous, target.headingDegrees());
        }
        if (Math.abs(headingDelta) < MIN_AXIS_SOLVE_ANGLE_DEGREES) {
            return target;
        }

        double previousHeading = Math.toRadians(previous.headingDegrees());
        double targetHeading = Math.toRadians(target.headingDegrees());
        double a = Math.cos(previousHeading) - Math.cos(targetHeading);
        double b = Math.sin(targetHeading) - Math.sin(previousHeading);
        double determinant = a * a + b * b;
        if (determinant < 1.0E-8D) {
            return target;
        }

        double centerDeltaX = target.centerX() - previous.centerX();
        double centerDeltaZ = target.centerZ() - previous.centerZ();
        double localPivotX = (a * centerDeltaX - b * centerDeltaZ) / determinant;
        double localPivotZ = (b * centerDeltaX + a * centerDeltaZ) / determinant;
        Offset previousWorldPivotOffset = rotate(
                localPivotX, localPivotZ, previous.headingDegrees());
        double worldPivotX = previous.centerX() + previousWorldPivotOffset.x();
        double worldPivotZ = previous.centerZ() + previousWorldPivotOffset.z();
        if (!Double.isFinite(worldPivotX) || !Double.isFinite(worldPivotZ)
                || !Double.isFinite(localPivotX) || !Double.isFinite(localPivotZ)) {
            return target;
        }
        return new Pose(
                worldPivotX,
                worldPivotZ,
                (float) localPivotX,
                (float) localPivotZ,
                SableHeadingInterpolator.normalize(target.headingDegrees()));
    }

    static Pose withHeading(Pose pose, float headingDegrees) {
        return new Pose(
                pose.worldRotationPointX(),
                pose.worldRotationPointZ(),
                pose.localRotationPointOffsetX(),
                pose.localRotationPointOffsetZ(),
                SableHeadingInterpolator.normalize(headingDegrees));
    }

    private static double lerp(double start, double target, float amount) {
        return start + (target - start) * amount;
    }

    private static Offset rotate(double x, double z, float headingDegrees) {
        double heading = Math.toRadians(headingDegrees);
        double cosine = Math.cos(heading);
        double sine = Math.sin(heading);
        return new Offset(x * cosine - z * sine, x * sine + z * cosine);
    }

    record Pose(
            double worldRotationPointX,
            double worldRotationPointZ,
            float localRotationPointOffsetX,
            float localRotationPointOffsetZ,
            float headingDegrees
    ) {
        double centerX() {
            Offset rotatedPivot = rotate(
                    this.localRotationPointOffsetX, this.localRotationPointOffsetZ, this.headingDegrees);
            return this.worldRotationPointX - rotatedPivot.x();
        }

        double centerZ() {
            Offset rotatedPivot = rotate(
                    this.localRotationPointOffsetX, this.localRotationPointOffsetZ, this.headingDegrees);
            return this.worldRotationPointZ - rotatedPivot.z();
        }
    }

    private record Offset(double x, double z) {
    }
}
