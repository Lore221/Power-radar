package com.limbo2136.powerradar.compat.aeronautics;

/** Общие пределы построения, передачи и хранения палубного XZ-силуэта. */
public final class SableSilhouetteLimits {
    public static final int MAX_SCANNED_BLOCKS = 1_000_000;
    public static final int MAX_LINES = 32_768;
    public static final int MAX_FILLS = 32_768;
    public static final int MAX_GEOMETRY_BYTES = 1_048_576;

    private SableSilhouetteLimits() {
    }

    public static int estimatedGeometryBytes(int lineCount, int fillCount) {
        long bytes = 16L * ((long) lineCount + fillCount);
        return bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes;
    }

    public static boolean accepts(int lineCount, int fillCount) {
        return lineCount >= 0 && lineCount <= MAX_LINES
                && fillCount >= 0 && fillCount <= MAX_FILLS
                && estimatedGeometryBytes(lineCount, fillCount) <= MAX_GEOMETRY_BYTES;
    }
}
