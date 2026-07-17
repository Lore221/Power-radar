package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.network.RadarMonitorSilhouettePayload;

final class SableSilhouetteProjection {
    private SableSilhouetteProjection() {
    }

    static Point projectOffset(
            float localX,
            float localZ,
            float structureHeadingDegrees,
            float viewYawDegrees,
            double screenUnitsPerBlock
    ) {
        double heading = Math.toRadians(structureHeadingDegrees);
        double headingCosine = Math.cos(heading);
        double headingSine = Math.sin(heading);
        double worldX = localX * headingCosine - localZ * headingSine;
        double worldZ = localX * headingSine + localZ * headingCosine;

        double view = Math.toRadians(viewYawDegrees);
        double screenX = worldX * Math.cos(view) + worldZ * Math.sin(view);
        double screenY = -(worldX * Math.sin(view) - worldZ * Math.cos(view));
        return new Point((float) (screenX * screenUnitsPerBlock), (float) (screenY * screenUnitsPerBlock));
    }

    static Bounds projectBounds(
            RadarMonitorSilhouettePayload silhouette,
            float structureHeadingDegrees,
            float viewYawDegrees,
            double screenUnitsPerBlock
    ) {
        BoundsAccumulator bounds = new BoundsAccumulator();
        for (RadarMonitorSilhouettePayload.Fill fill : silhouette.fills()) {
            bounds.include(projectOffset(fill.minX(), fill.minZ(), structureHeadingDegrees, viewYawDegrees, screenUnitsPerBlock));
            bounds.include(projectOffset(fill.maxX(), fill.minZ(), structureHeadingDegrees, viewYawDegrees, screenUnitsPerBlock));
            bounds.include(projectOffset(fill.maxX(), fill.maxZ(), structureHeadingDegrees, viewYawDegrees, screenUnitsPerBlock));
            bounds.include(projectOffset(fill.minX(), fill.maxZ(), structureHeadingDegrees, viewYawDegrees, screenUnitsPerBlock));
        }
        for (RadarMonitorSilhouettePayload.Line line : silhouette.lines()) {
            bounds.include(projectOffset(line.x1(), line.z1(), structureHeadingDegrees, viewYawDegrees, screenUnitsPerBlock));
            bounds.include(projectOffset(line.x2(), line.z2(), structureHeadingDegrees, viewYawDegrees, screenUnitsPerBlock));
        }
        return bounds.build();
    }

    record Point(float x, float y) {
    }

    record Bounds(float minX, float minY, float maxX, float maxY) {
        static final Bounds EMPTY = new Bounds(0.0F, 0.0F, 0.0F, 0.0F);

        boolean empty() {
            return this == EMPTY;
        }

        float centerX() {
            return (this.minX + this.maxX) * 0.5F;
        }

        float centerY() {
            return (this.minY + this.maxY) * 0.5F;
        }

        float squareSize() {
            return Math.max(this.maxX - this.minX, this.maxY - this.minY);
        }
    }

    private static final class BoundsAccumulator {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        private void include(Point point) {
            this.minX = Math.min(this.minX, point.x());
            this.minY = Math.min(this.minY, point.y());
            this.maxX = Math.max(this.maxX, point.x());
            this.maxY = Math.max(this.maxY, point.y());
        }

        private Bounds build() {
            return Float.isFinite(this.minX)
                    ? new Bounds(this.minX, this.minY, this.maxX, this.maxY)
                    : Bounds.EMPTY;
        }
    }
}
