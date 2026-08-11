package com.limbo2136.powerradar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SablePoseMotionTest {
    private static final double EPSILON = 0.0001D;

    @Test
    void rotationContinuesPastOldInterpolationDeadline() {
        SablePoseMotion motion = SablePoseMotion.initial(pose(0.0D, 0.0F), 0L)
                .update(pose(0.0D, 25.0F), 250L);

        assertEquals(35.0D, motion.valueAt(350L).headingDegrees(), EPSILON);
        assertEquals(45.0D, motion.valueAt(450L).headingDegrees(), EPSILON);
    }

    @Test
    void newSnapshotDoesNotPauseOrJumpHeading() {
        SablePoseMotion motion = SablePoseMotion.initial(pose(0.0D, 0.0F), 0L)
                .update(pose(0.0D, 25.0F), 250L);
        float beforeUpdate = motion.valueAt(500L).headingDegrees();

        SablePoseMotion updated = motion.update(pose(0.0D, 50.0F), 500L);

        assertEquals(beforeUpdate, updated.valueAt(500L).headingDegrees(), EPSILON);
        assertEquals(60.0D, updated.valueAt(600L).headingDegrees(), EPSILON);
    }

    @Test
    void translationIsNotSmoothedWhenHeadingDoesNotChange() {
        SablePoseMotion motion = SablePoseMotion.initial(pose(0.0D, 0.0F), 0L)
                .update(SablePoseInterpolator.fromCenter(10.0D, 0.0D, 0.0F, 1.0F, 0.0F), 250L);

        SablePoseInterpolator.Pose immediately = motion.valueAt(250L);

        assertEquals(11.0D, immediately.worldRotationPointX(), EPSILON);
        assertEquals(0.0D, immediately.worldRotationPointZ(), EPSILON);
        assertEquals(10.0D, immediately.centerX(), EPSILON);
        assertEquals(0.0D, immediately.centerZ(), EPSILON);
    }

    @Test
    void rotatingCenterDoesNotMoveWorldRotationPoint() {
        // Переданные Sable reference points сами вращаются и не совпадают с настоящей осью (0, 0).
        SablePoseInterpolator.Pose start = SablePoseInterpolator.fromCenter(
                -1.0D, 0.0D, 0.0F, 0.25F, 0.0F);
        SablePoseInterpolator.Pose next = SablePoseInterpolator.fromCenter(
                0.0D, -1.0D, 90.0F, 0.0F, 0.25F);
        SablePoseMotion motion = SablePoseMotion.initial(start, 0L).update(next, 250L);

        SablePoseInterpolator.Pose rendered = motion.valueAt(300L);

        assertEquals(0.0D, rendered.worldRotationPointX(), EPSILON);
        assertEquals(0.0D, rendered.worldRotationPointZ(), EPSILON);
    }

    @Test
    void consecutiveLogSamplesResolveTheSameMechanicalAxis() {
        SablePoseInterpolator.Pose first = SablePoseInterpolator.fromCenter(
                -41.24400489945917D, -330.83181613762224D, 138.07333F,
                0.006475145F, -0.38186795F);
        SablePoseInterpolator.Pose second = SablePoseInterpolator.fromCenter(
                -41.49439096521311D, -331.60579780780125D, -173.92686F,
                0.28811502F, -0.25070855F);
        SablePoseInterpolator.Pose third = SablePoseInterpolator.fromCenter(
                -41.08674846375487D, -332.30975254057944D, -125.92673F,
                0.37909922F, 0.046355322F);

        SablePoseInterpolator.Pose firstAxis = SablePoseInterpolator.resolveRotationAxis(first, second);
        SablePoseInterpolator.Pose secondAxis = SablePoseInterpolator.resolveRotationAxis(second, third);

        assertEquals(firstAxis.worldRotationPointX(), secondAxis.worldRotationPointX(), 0.001D);
        assertEquals(firstAxis.worldRotationPointZ(), secondAxis.worldRotationPointZ(), 0.001D);
    }

    @Test
    void duplicateSnapshotDoesNotRestoreTheOrbitingSableReferencePoint() {
        SablePoseInterpolator.Pose start = SablePoseInterpolator.fromCenter(
                -1.0D, 0.0D, 0.0F, 0.25F, 0.0F);
        SablePoseInterpolator.Pose next = SablePoseInterpolator.fromCenter(
                0.0D, -1.0D, 90.0F, 0.0F, 0.25F);
        SablePoseMotion resolved = SablePoseMotion.initial(start, 0L).update(next, 250L);

        SablePoseMotion duplicate = resolved.update(next, 251L);

        assertEquals(0.0D, duplicate.valueAt(251L).worldRotationPointX(), EPSILON);
        assertEquals(0.0D, duplicate.valueAt(251L).worldRotationPointZ(), EPSILON);
    }

    private static SablePoseInterpolator.Pose pose(double centerX, float headingDegrees) {
        return SablePoseInterpolator.fromCenter(centerX, 0.0D, headingDegrees, 1.0F, 0.0F);
    }
}
