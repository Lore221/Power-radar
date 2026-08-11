package com.limbo2136.powerradar.client;

/** Непрерывно восстанавливает вращение Sable между радарными снимками. */
final class SablePoseMotion {
    private static final long CORRECTION_MILLIS = 100L;
    private static final long MAX_EXTRAPOLATION_MILLIS = 500L;
    private static final float HEADING_EPSILON = 0.001F;

    private final SablePoseInterpolator.Pose authoritativePose;
    private final float correctionStartHeading;
    private final float angularVelocityDegreesPerMillis;
    private final long receivedAtMillis;
    private final long lastHeadingChangeMillis;

    private SablePoseMotion(
            SablePoseInterpolator.Pose authoritativePose,
            float correctionStartHeading,
            float angularVelocityDegreesPerMillis,
            long receivedAtMillis,
            long lastHeadingChangeMillis
    ) {
        this.authoritativePose = authoritativePose;
        this.correctionStartHeading = correctionStartHeading;
        this.angularVelocityDegreesPerMillis = angularVelocityDegreesPerMillis;
        this.receivedAtMillis = receivedAtMillis;
        this.lastHeadingChangeMillis = lastHeadingChangeMillis;
    }

    static SablePoseMotion initial(SablePoseInterpolator.Pose pose, long receivedAtMillis) {
        return new SablePoseMotion(pose, pose.headingDegrees(), 0.0F,
                receivedAtMillis, receivedAtMillis);
    }

    SablePoseMotion update(SablePoseInterpolator.Pose pose, long nowMillis) {
        long sampleMillis = Math.max(1L, nowMillis - this.receivedAtMillis);
        float headingDelta = SableHeadingInterpolator.shortestDelta(
                this.authoritativePose.headingDegrees(), pose.headingDegrees());
        float velocity = this.angularVelocityDegreesPerMillis;
        long lastChange = this.lastHeadingChangeMillis;
        if (Math.abs(headingDelta) >= HEADING_EPSILON) {
            velocity = headingDelta / sampleMillis;
            lastChange = nowMillis;
        } else if (nowMillis - lastChange >= MAX_EXTRAPOLATION_MILLIS) {
            velocity = 0.0F;
        }

        SablePoseInterpolator.Pose resolvedPose = SablePoseInterpolator.resolveRotationAxis(
                this.authoritativePose, pose);
        return new SablePoseMotion(
                resolvedPose,
                valueAt(nowMillis).headingDegrees(),
                velocity,
                nowMillis,
                lastChange);
    }

    SablePoseInterpolator.Pose valueAt(long nowMillis) {
        long elapsedMillis = Math.max(0L,
                Math.min(MAX_EXTRAPOLATION_MILLIS, nowMillis - this.receivedAtMillis));
        float elapsed = elapsedMillis;
        float startTrajectory = this.correctionStartHeading
                + this.angularVelocityDegreesPerMillis * elapsed;
        float targetTrajectory = this.authoritativePose.headingDegrees()
                + this.angularVelocityDegreesPerMillis * elapsed;
        float correction = Math.min(1.0F, elapsed / CORRECTION_MILLIS);
        float heading = SableHeadingInterpolator.interpolate(
                startTrajectory, targetTrajectory, correction);
        return SablePoseInterpolator.withHeading(this.authoritativePose, heading);
    }
}
