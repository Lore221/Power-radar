package com.limbo2136.powerradar.client;

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
        // Сначала локальные XZ структуры поворачиваются в мировой XZ на угол её курса.
        double heading = Math.toRadians(structureHeadingDegrees);
        double headingCosine = Math.cos(heading);
        double headingSine = Math.sin(heading);
        double worldX = localX * headingCosine - localZ * headingSine;
        double worldZ = localX * headingSine + localZ * headingCosine;

        // Затем мировой XZ переводится в экран: +X вправо, +Y экрана вниз.
        double view = Math.toRadians(viewYawDegrees);
        double screenX = worldX * Math.cos(view) + worldZ * Math.sin(view);
        double screenY = -(worldX * Math.sin(view) - worldZ * Math.cos(view));
        return new Point((float) (screenX * screenUnitsPerBlock), (float) (screenY * screenUnitsPerBlock));
    }

    record Point(float x, float y) {
    }
}
