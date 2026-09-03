package com.limbo2136.powerradar.radar;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class RadarCoverageFilter {
    // Повторяет radar_overview_octagon.png: поле 128 px с угловыми срезами по 29 px.
    static final double OVERVIEW_DIAGONAL_LIMIT = 98.0D / 64.0D;
    private static final double AIR_MIN_HEIGHT_ABOVE_SURFACE = 20.0D;

    private RadarCoverageFilter() {
    }

    public static boolean isEntityInCoverage(RadarScanProfile profile, RadarScanContext context, Entity entity) {
        return prepare(profile, context).isEntityInCoverage(entity);
    }

    public static boolean isPointInCoverage(RadarScanProfile profile, RadarScanContext context, Vec3 position) {
        return prepare(profile, context).isPointInCoverage(position);
    }

    public static PreparedCoverage prepare(RadarScanProfile profile, RadarScanContext context) {
        return new PreparedCoverage(profile, context);
    }

    public static boolean isInsideOverviewFootprint(double deltaX, double deltaZ, double range) {
        double safeRange = Math.max(1.0D, range);
        double normalizedX = Math.abs(deltaX) / safeRange;
        double normalizedZ = Math.abs(deltaZ) / safeRange;
        return normalizedX <= 1.0D
                && normalizedZ <= 1.0D
                && normalizedX + normalizedZ <= OVERVIEW_DIAGONAL_LIMIT;
    }

    /** Возвращает знаковое отклонение цели от направления радара в градусах. */
    public static double bearingDegrees(float yawDegrees, double deltaX, double deltaZ) {
        // В координатах Minecraft yaw цели равен atan2(dx, -dz), поэтому относительный
        // азимут не требует построения forward/right-базиса и двух тригонометрических функций.
        double targetYawDegrees = Math.toDegrees(Math.atan2(deltaX, -deltaZ));
        return Mth.wrapDegrees((float) (targetYawDegrees - yawDegrees));
    }

    /** Неизменяемое покрытие одного scan request; тригонометрия сектора вычисляется один раз. */
    public static final class PreparedCoverage {
        private final RadarScanProfile profile;
        private final RadarScanContext context;
        private final double rangeSquared;
        private final double forwardX;
        private final double forwardY;
        private final double forwardZ;
        private final double cosineHalfAngleSquared;

        private PreparedCoverage(RadarScanProfile profile, RadarScanContext context) {
            this.profile = profile;
            this.context = context;
            this.rangeSquared = (double) profile.range() * profile.range();
            if (profile.useFovCheck()) {
                Vec3 forward = profile.scanMode() == RadarScanMode.AIRCRAFT
                        ? context.radarForward()
                        : horizontalForward(context.radarYawDegrees());
                Vec3 normalizedForward = forward.lengthSqr() < 1.0E-12D
                        ? new Vec3(0.0D, 0.0D, -1.0D)
                        : forward.normalize();
                this.forwardX = normalizedForward.x;
                this.forwardY = normalizedForward.y;
                this.forwardZ = normalizedForward.z;
                double cosineHalfAngle = Math.cos(Math.toRadians(profile.sectorAngle() * 0.5D));
                this.cosineHalfAngleSquared = cosineHalfAngle * cosineHalfAngle;
            } else {
                this.forwardX = 0.0D;
                this.forwardY = 0.0D;
                this.forwardZ = 0.0D;
                this.cosineHalfAngleSquared = 0.0D;
            }
        }

        public boolean isEntityInCoverage(Entity entity) {
            return isPointInCoverage(entity.position(), entity.getBlockX(), entity.getBlockZ(), null);
        }

        boolean isEntityInCoverage(Entity entity, RadarSurfaceHeightCache surfaceHeights) {
            return isPointInCoverage(
                    entity.position(), entity.getBlockX(), entity.getBlockZ(), surfaceHeights);
        }

        public boolean isPointInCoverage(Vec3 position) {
            return isPointInCoverage(
                    position,
                    (int) Math.floor(position.x),
                    (int) Math.floor(position.z),
                    null);
        }

        boolean isPointInCoverage(Vec3 position, RadarSurfaceHeightCache surfaceHeights) {
            return isPointInCoverage(
                    position,
                    (int) Math.floor(position.x),
                    (int) Math.floor(position.z),
                    surfaceHeights);
        }

        private boolean isPointInCoverage(
                Vec3 position,
                int blockX,
                int blockZ,
                RadarSurfaceHeightCache surfaceHeights
        ) {
            double deltaX = position.x - this.context.radarOriginX();
            double deltaY = position.y - this.context.radarOriginY();
            double deltaZ = position.z - this.context.radarOriginZ();
            double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
            double distanceSquared = horizontalDistanceSquared + deltaY * deltaY;
            boolean insideHorizontalCoverage = this.profile.scanMode() == RadarScanMode.AIRCRAFT
                    ? distanceSquared <= this.rangeSquared
                    : this.profile.structureType() == RadarStructureType.OVERVIEW
                    ? isInsideOverviewFootprint(deltaX, deltaZ, this.profile.range())
                    : horizontalDistanceSquared <= this.rangeSquared;
            if (!insideHorizontalCoverage
                    || deltaY < this.profile.verticalMinOffset()
                    || deltaY > this.profile.verticalMaxOffset()) {
                return false;
            }
            if (this.profile.useFovCheck()) {
                double forwardDistance = deltaX * this.forwardX
                        + (this.profile.scanMode() == RadarScanMode.AIRCRAFT ? deltaY * this.forwardY : 0.0D)
                        + deltaZ * this.forwardZ;
                if (forwardDistance < 0.0D
                        || forwardDistance * forwardDistance + horizontalDistanceSquared * 1.0E-12D
                                < (this.profile.scanMode() == RadarScanMode.AIRCRAFT
                                        ? (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
                                        : horizontalDistanceSquared) * this.cosineHalfAngleSquared) {
                    return false;
                }
            }
            // Heightmap читается только после дешёвых дальности, Y и сектора.
            if (this.profile.scanMode() == RadarScanMode.SKY) {
                int surfaceY = surfaceHeight(blockX, blockZ, surfaceHeights);
                if (position.y < surfaceY + AIR_MIN_HEIGHT_ABOVE_SURFACE) {
                    return false;
                }
            }
            if (this.profile.scanMode() == RadarScanMode.SURFACE_SCANNER
                    || this.profile.scanMode() == RadarScanMode.AIRCRAFT) {
                int surfaceY = surfaceHeight(blockX, blockZ, surfaceHeights);
                if (position.y < surfaceY - 10.0D) {
                    return false;
                }
            }
            return true;
        }

        private static Vec3 horizontalForward(float yawDegrees) {
            double radians = Math.toRadians(yawDegrees);
            return new Vec3(Math.sin(radians), 0.0D, -Math.cos(radians));
        }

        private int surfaceHeight(int blockX, int blockZ, RadarSurfaceHeightCache surfaceHeights) {
            return surfaceHeights == null
                    ? this.context.level().getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            blockX,
                            blockZ)
                    : surfaceHeights.height(blockX, blockZ);
        }
    }
}
