package com.limbo2136.powerradar.radar;

import com.limbo2136.powerradar.RadarConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Строит консервативный spatial broad phase одного радара, не выполняя запросов к миру. */
public final class RadarScanSlicePlanner {
    private RadarScanSlicePlanner() {
    }

    public static RadarScanSlicePlan build(RadarScanProfile profile, RadarScanContext context) {
        HorizontalBounds bounds = horizontalSearchBounds(profile, context.radarYawDegrees());
        AABB searchBox = new AABB(
                context.radarOriginX() + bounds.minX(),
                context.radarOriginY() + profile.verticalMinOffset(),
                context.radarOriginZ() + bounds.minZ(),
                context.radarOriginX() + bounds.maxX(),
                context.radarOriginY() + profile.verticalMaxOffset(),
                context.radarOriginZ() + bounds.maxZ());
        double sliceSize = RadarConstants.entityQuerySliceSize();
        List<AxisInterval> xIntervals = worldAlignedIntervals(searchBox.minX, searchBox.maxX, sliceSize);
        List<AxisInterval> zIntervals = worldAlignedIntervals(searchBox.minZ, searchBox.maxZ, sliceSize);
        HorizontalSliceFilter filter = HorizontalSliceFilter.from(profile, context.radarYawDegrees());
        List<AABB> slices = new ArrayList<>();
        // Внутренние интервалы привязаны к мировой сетке и поэтому переиспользуются соседними радарами.
        // Крайние интервалы остаются точными и не расширяют область обнаружения.
        for (AxisInterval x : xIntervals) {
            for (AxisInterval z : zIntervals) {
                if (filter.mightIntersect(
                        x.minimum() - context.radarOriginX(), x.maximum() - context.radarOriginX(),
                        z.minimum() - context.radarOriginZ(), z.maximum() - context.radarOriginZ())) {
                    slices.add(new AABB(
                            x.minimum(), searchBox.minY, z.minimum(),
                            x.maximum(), searchBox.maxY, z.maximum()));
                }
            }
        }
        return new RadarScanSlicePlan(slices);
    }

    private static List<AxisInterval> worldAlignedIntervals(double minimum, double maximum, double cellSize) {
        if (maximum <= minimum) {
            return List.of();
        }
        List<AxisInterval> intervals = new ArrayList<>();
        double firstCellMinimum = Math.floor(minimum / cellSize) * cellSize;
        for (double cellMinimum = firstCellMinimum; cellMinimum < maximum; cellMinimum += cellSize) {
            intervals.add(new AxisInterval(
                    Math.max(cellMinimum, minimum),
                    Math.min(cellMinimum + cellSize, maximum)));
        }

        // Выравнивание может добавить один тонкий крайний интервал. Слияние меньшего края
        // сохраняет количество запросов невыровненного плана и большинство общих границ.
        int unalignedCount = (int) Math.ceil((maximum - minimum) / cellSize);
        if (intervals.size() > unalignedCount && intervals.size() >= 2) {
            AxisInterval first = intervals.getFirst();
            AxisInterval last = intervals.getLast();
            if (first.length() <= last.length()) {
                AxisInterval second = intervals.get(1);
                intervals.set(0, new AxisInterval(first.minimum(), second.maximum()));
                intervals.remove(1);
            } else {
                int lastIndex = intervals.size() - 1;
                AxisInterval previous = intervals.get(lastIndex - 1);
                intervals.set(lastIndex - 1, new AxisInterval(previous.minimum(), last.maximum()));
                intervals.remove(lastIndex);
            }
        }
        return intervals;
    }

    private static HorizontalBounds horizontalSearchBounds(RadarScanProfile profile, float yawDegrees) {
        double range = profile.range();
        if (!profile.useFovCheck() || profile.sectorAngle() >= 360) {
            return new HorizontalBounds(-range, -range, range, range);
        }

        double halfAngle = profile.sectorAngle() * 0.5D;
        double minX = 0.0D;
        double minZ = 0.0D;
        double maxX = 0.0D;
        double maxZ = 0.0D;
        // Экстремумы сектора лежат на крайних лучах либо на кардинальном направлении внутри него.
        double[] candidateYaws = {
                yawDegrees - halfAngle, yawDegrees + halfAngle, 0.0D, 90.0D, 180.0D, 270.0D
        };
        for (int index = 0; index < candidateYaws.length; index++) {
            double candidateYaw = candidateYaws[index];
            if (index >= 2 && Math.abs(Mth.wrapDegrees((float) (candidateYaw - yawDegrees))) > halfAngle) {
                continue;
            }
            double radians = Math.toRadians(candidateYaw);
            double x = Math.sin(radians) * range;
            double z = -Math.cos(radians) * range;
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }
        return new HorizontalBounds(minX, minZ, maxX, maxZ);
    }

    private record HorizontalBounds(double minX, double minZ, double maxX, double maxZ) {
    }

    private record AxisInterval(double minimum, double maximum) {
        private double length() {
            return this.maximum - this.minimum;
        }
    }

    private record HorizontalSliceFilter(
            boolean overview,
            double rangeSquared,
            double overviewDiagonalLimit,
            boolean sector,
            double firstBoundaryX,
            double firstBoundaryZ,
            double secondBoundaryX,
            double secondBoundaryZ
    ) {
        private static HorizontalSliceFilter from(RadarScanProfile profile, float yawDegrees) {
            double range = profile.range();
            if (!profile.useFovCheck()) {
                return new HorizontalSliceFilter(
                        profile.structureType() == RadarStructureType.OVERVIEW,
                        range * range,
                        RadarCoverageFilter.OVERVIEW_DIAGONAL_LIMIT * range,
                        false,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
            double radians = Math.toRadians(yawDegrees);
            double forwardX = Math.sin(radians);
            double forwardZ = -Math.cos(radians);
            double rightX = Math.cos(radians);
            double rightZ = Math.sin(radians);
            double tangent = Math.tan(Math.toRadians(profile.sectorAngle() * 0.5D));
            return new HorizontalSliceFilter(
                    false,
                    range * range,
                    0.0D,
                    true,
                    rightX - tangent * forwardX,
                    rightZ - tangent * forwardZ,
                    -rightX - tangent * forwardX,
                    -rightZ - tangent * forwardZ);
        }

        private boolean mightIntersect(double minX, double maxX, double minZ, double maxZ) {
            double closestX = distanceFromZero(minX, maxX);
            double closestZ = distanceFromZero(minZ, maxZ);
            if (this.overview) {
                return closestX + closestZ <= this.overviewDiagonalLimit;
            }
            if (closestX * closestX + closestZ * closestZ > this.rangeSquared) {
                return false;
            }
            return !this.sector
                    || linearMinimum(this.firstBoundaryX, this.firstBoundaryZ, minX, maxX, minZ, maxZ) <= 0.0D
                    && linearMinimum(this.secondBoundaryX, this.secondBoundaryZ, minX, maxX, minZ, maxZ) <= 0.0D;
        }

        private static double distanceFromZero(double minimum, double maximum) {
            if (minimum > 0.0D) {
                return minimum;
            }
            return maximum < 0.0D ? -maximum : 0.0D;
        }

        private static double linearMinimum(
                double xCoefficient,
                double zCoefficient,
                double minX,
                double maxX,
                double minZ,
                double maxZ
        ) {
            double x = xCoefficient >= 0.0D ? minX : maxX;
            double z = zCoefficient >= 0.0D ? minZ : maxZ;
            return xCoefficient * x + zCoefficient * z;
        }
    }
}
