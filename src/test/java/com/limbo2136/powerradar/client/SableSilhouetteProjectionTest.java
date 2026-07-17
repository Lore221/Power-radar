package com.limbo2136.powerradar.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.limbo2136.powerradar.network.RadarMonitorSilhouettePayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SableSilhouetteProjectionTest {
    private static final double EPSILON = 0.0001D;

    @Test
    void identityStructureUsesWorldXZAtFixedNorth() {
        SableSilhouetteProjection.Point projected = SableSilhouetteProjection.projectOffset(
                3.0F, 5.0F, 0.0F, 0.0F, 2.0D);

        assertEquals(6.0D, projected.x(), EPSILON);
        assertEquals(10.0D, projected.y(), EPSILON);
    }

    @Test
    void structureHeadingAndMonitorYawBothRotateProjection() {
        SableSilhouetteProjection.Point structureTurn = SableSilhouetteProjection.projectOffset(
                0.0F, 4.0F, -90.0F, 0.0F, 1.0D);
        SableSilhouetteProjection.Point alignedMonitor = SableSilhouetteProjection.projectOffset(
                0.0F, 4.0F, -90.0F, 90.0F, 1.0D);

        assertEquals(4.0D, structureTurn.x(), EPSILON);
        assertEquals(0.0D, structureTurn.y(), EPSILON);
        assertEquals(0.0D, alignedMonitor.x(), EPSILON);
        assertEquals(-4.0D, alignedMonitor.y(), EPSILON);
    }

    @Test
    void zoomScaleAppliesDirectlyToStructureDimensions() {
        SableSilhouetteProjection.Point near = SableSilhouetteProjection.projectOffset(
                10.0F, 0.0F, 0.0F, 0.0F, 2.0D);
        SableSilhouetteProjection.Point far = SableSilhouetteProjection.projectOffset(
                10.0F, 0.0F, 0.0F, 0.0F, 0.5D);

        assertEquals(20.0D, near.x(), EPSILON);
        assertEquals(5.0D, far.x(), EPSILON);
    }

    @Test
    void projectedBoundsProduceSquareAroundWholeSilhouette() {
        RadarMonitorSilhouettePayload silhouette = new RadarMonitorSilhouettePayload(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                UUID.fromString("a44993f8-28d4-48df-bad4-c87005ee0402"),
                1,
                List.of(),
                List.of(new RadarMonitorSilhouettePayload.Fill(-4.0F, -1.0F, 4.0F, 1.0F)));

        SableSilhouetteProjection.Bounds bounds = SableSilhouetteProjection.projectBounds(
                silhouette, 0.0F, 0.0F, 3.0D);

        assertEquals(-12.0D, bounds.minX(), EPSILON);
        assertEquals(12.0D, bounds.maxX(), EPSILON);
        assertEquals(-3.0D, bounds.minY(), EPSILON);
        assertEquals(3.0D, bounds.maxY(), EPSILON);
        assertEquals(24.0D, bounds.squareSize(), EPSILON);
    }
}
