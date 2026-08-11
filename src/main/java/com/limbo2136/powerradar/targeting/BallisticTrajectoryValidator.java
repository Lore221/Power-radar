package com.limbo2136.powerradar.targeting;

import com.limbo2136.powerradar.api.target.TrackedTargetView;
import com.limbo2136.powerradar.api.weapon.WeaponBallistics;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Подтверждает рассчитанные углы дискретной физикой CBC без случайного разброса. */
public final class BallisticTrajectoryValidator {
    private static final int MAX_SIMULATION_TICKS = 600;
    private static final double MIN_SPEED = 1.0E-6D;
    private static final double MAX_CORRECTION_DEGREES = 8.0D;

    private BallisticTrajectoryValidator() {
    }

    public static Result validate(
            ServerLevel level,
            TrackedTargetView target,
            Vec3 origin,
            float worldYawDegrees,
            float worldPitchDegrees,
            WeaponBallistics ballistics,
            Vec3 inheritedVelocity,
            boolean useAcceleration,
            boolean checkBlockCollisions,
            long gameTime
    ) {
        Simulation first = simulate(
                level, target, origin, worldYawDegrees, worldPitchDegrees, ballistics,
                inheritedVelocity, useAcceleration, checkBlockCollisions, gameTime);
        if (first.status() != Status.MISS || first.closest() == null) {
            return first.toResult(worldYawDegrees, worldPitchDegrees, false);
        }

        AngleCorrection correction = correction(
                worldYawDegrees, worldPitchDegrees, ballistics.speedBlocksPerTick(), first.closest(), ballistics);
        if (correction == null) {
            return first.toResult(worldYawDegrees, worldPitchDegrees, false);
        }
        Simulation second = simulate(
                level, target, origin, correction.yawDegrees(), correction.pitchDegrees(), ballistics,
                inheritedVelocity, useAcceleration, checkBlockCollisions, gameTime);
        if (second.status() == Status.HIT || second.status() == Status.BLOCKED) {
            return second.toResult(correction.yawDegrees(), correction.pitchDegrees(), true);
        }
        // Не передаём приводу неподтверждённую поправку: она может меняться между попытками.
        return second.toResult(worldYawDegrees, worldPitchDegrees, false);
    }

    private static Simulation simulate(
            ServerLevel level,
            TrackedTargetView target,
            Vec3 origin,
            float yawDegrees,
            float pitchDegrees,
            WeaponBallistics ballistics,
            Vec3 inheritedVelocity,
            boolean useAcceleration,
            boolean checkBlockCollisions,
            long gameTime
    ) {
        if (ballistics == null || !ballistics.available() || ballistics.speedBlocksPerTick() <= MIN_SPEED) {
            return Simulation.UNREACHABLE;
        }
        Vec3 shotVelocity = TargetingMath.directionFromAngles(yawDegrees, pitchDegrees)
                .scale(ballistics.speedBlocksPerTick());
        Vec3 velocity = shotVelocity.add(inheritedVelocity);
        Vec3 position = origin;
        ClosestApproach closest = null;
        boolean blockedBeforeHit = false;
        int maxTicks = ballistics.hasLifetimeLimit()
                ? Math.min(MAX_SIMULATION_TICKS, Math.max(1, ballistics.lifetimeTicks() + 1))
                : MAX_SIMULATION_TICKS;

        for (int tick = 0; tick < maxTicks; tick++) {
            double speed = velocity.length();
            if (speed <= MIN_SPEED) {
                break;
            }
            double dragForce = ballistics.drag() * speed;
            if (ballistics.quadraticDrag()) {
                dragForce *= speed;
            }
            dragForce = Math.min(dragForce, speed);
            Vec3 acceleration = velocity.scale(-dragForce / speed)
                    .add(0.0D, -ballistics.gravityBlocksPerTickSquared(), 0.0D);
            Vec3 nextPosition = position.add(velocity).add(acceleration.scale(0.5D));
            double sampleTicks = tick + 1.0D;
            AABB targetBox = targetBox(target, gameTime, sampleTicks, useAcceleration);
            Optional<Vec3> targetHit = segmentHit(targetBox, position, nextPosition);

            BlockHitResult blockHit = checkBlockCollisions && segmentChunksLoaded(level, position, nextPosition)
                    ? level.clip(new ClipContext(
                            position,
                            nextPosition,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            CollisionContext.empty()))
                    : null;
            boolean blockBeforeTarget = blockHit != null
                    && blockHit.getType() != HitResult.Type.MISS
                    && (targetHit.isEmpty()
                            || position.distanceToSqr(blockHit.getLocation())
                                    < position.distanceToSqr(targetHit.get()));
            if (blockBeforeTarget) {
                blockedBeforeHit = true;
            }
            if (targetHit.isPresent()) {
                double segmentLength = Math.max(MIN_SPEED, position.distanceTo(nextPosition));
                double fraction = position.distanceTo(targetHit.get()) / segmentLength;
                return new Simulation(
                        blockedBeforeHit ? Status.BLOCKED : Status.HIT,
                        tick + Math.clamp(fraction, 0.0D, 1.0D),
                        closest);
            }

            ClosestApproach candidate = closestApproach(position, nextPosition, targetBox, sampleTicks);
            if (closest == null || candidate.missDistance() < closest.missDistance()) {
                closest = candidate;
            }
            position = nextPosition;
            velocity = velocity.add(acceleration);
        }
        return closest == null ? Simulation.UNREACHABLE : new Simulation(Status.MISS, closest.flightTicks(), closest);
    }

    private static AABB targetBox(
            TrackedTargetView target,
            long gameTime,
            double flightTicks,
            boolean useAcceleration
    ) {
        double age = Math.clamp(gameTime - target.lastSeenGameTime(), 0.0D, 40.0D);
        double totalTicks = age + flightTicks;
        Vec3 velocity = target.hasVelocity() ? target.velocity() : Vec3.ZERO;
        Vec3 acceleration = useAcceleration && target.hasAcceleration() ? target.acceleration() : Vec3.ZERO;
        Vec3 base = target.position()
                .add(velocity.scale(totalTicks))
                .add(acceleration.scale(0.5D * totalTicks * totalTicks));
        return target.targetBounds().move(base.subtract(target.position()));
    }

    private static Optional<Vec3> segmentHit(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) ? Optional.of(from) : box.clip(from, to);
    }

    private static ClosestApproach closestApproach(Vec3 from, Vec3 to, AABB box, double flightTicks) {
        Vec3 center = box.getCenter();
        Vec3 segment = to.subtract(from);
        double denominator = segment.lengthSqr();
        double fraction = denominator <= MIN_SPEED
                ? 0.0D
                : Math.clamp(center.subtract(from).dot(segment) / denominator, 0.0D, 1.0D);
        Vec3 projectilePoint = from.add(segment.scale(fraction));
        double dx = axisMiss(projectilePoint.x, box.minX, box.maxX);
        double dy = axisMiss(projectilePoint.y, box.minY, box.maxY);
        double dz = axisMiss(projectilePoint.z, box.minZ, box.maxZ);
        return new ClosestApproach(
                projectilePoint,
                center,
                Math.sqrt(dx * dx + dy * dy + dz * dz),
                flightTicks - 1.0D + fraction);
    }

    private static double axisMiss(double value, double minimum, double maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0.0D;
    }

    private static AngleCorrection correction(
            float yawDegrees,
            float pitchDegrees,
            double muzzleSpeed,
            ClosestApproach closest,
            WeaponBallistics ballistics
    ) {
        double response = displacementResponse(Math.max(1.0D, closest.flightTicks()), ballistics);
        if (response <= MIN_SPEED) {
            return null;
        }
        Vec3 residual = closest.targetCenter().subtract(closest.projectilePoint());
        Vec3 correctedShotVelocity = TargetingMath.directionFromAngles(yawDegrees, pitchDegrees)
                .scale(muzzleSpeed)
                .add(residual.scale(1.0D / response));
        if (correctedShotVelocity.lengthSqr() <= MIN_SPEED) {
            return null;
        }
        Vec3 correctedDirection = correctedShotVelocity.normalize();
        float correctedYaw = TargetingMath.yawTo(correctedDirection);
        float correctedPitch = (float) Math.toDegrees(Math.asin(Math.clamp(correctedDirection.y, -1.0D, 1.0D)));
        double yawDelta = Math.abs(net.minecraft.util.Mth.wrapDegrees(correctedYaw - yawDegrees));
        double pitchDelta = Math.abs(correctedPitch - pitchDegrees);
        return yawDelta <= MAX_CORRECTION_DEGREES && pitchDelta <= MAX_CORRECTION_DEGREES
                ? new AngleCorrection(correctedYaw, correctedPitch)
                : null;
    }

    private static double displacementResponse(double ticks, WeaponBallistics ballistics) {
        if (!ballistics.quadraticDrag() && LinearDragTrajectory.supported(ballistics.drag())) {
            return LinearDragTrajectory.stepScale(ballistics.drag())
                    * LinearDragTrajectory.geometricSum(ticks, ballistics.drag());
        }
        return ticks;
    }

    private static boolean segmentChunksLoaded(ServerLevel level, Vec3 from, Vec3 to) {
        int minimumChunkX = SectionPos.blockToSectionCoord(BlockPos.containing(
                Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)).getX());
        int maximumChunkX = SectionPos.blockToSectionCoord(BlockPos.containing(
                Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).getX());
        int minimumChunkZ = SectionPos.blockToSectionCoord(BlockPos.containing(
                Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)).getZ());
        int maximumChunkZ = SectionPos.blockToSectionCoord(BlockPos.containing(
                Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)).getZ());
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public enum Status {
        HIT,
        MISS,
        UNREACHABLE,
        BLOCKED
    }

    public record Result(
            Status status,
            float worldYawDegrees,
            float worldPitchDegrees,
            double flightTicks,
            boolean corrected,
            double missDistanceBlocks
    ) {
        public boolean confirmed() {
            return this.status == Status.HIT;
        }

        public boolean blocked() {
            return this.status == Status.BLOCKED;
        }
    }

    private record Simulation(Status status, double flightTicks, ClosestApproach closest) {
        private static final Simulation UNREACHABLE = new Simulation(Status.UNREACHABLE, 0.0D, null);

        private Result toResult(float yawDegrees, float pitchDegrees, boolean corrected) {
            double missDistance = this.status == Status.HIT || this.status == Status.BLOCKED
                    ? 0.0D
                    : this.closest == null ? Double.NaN : this.closest.missDistance();
            return new Result(this.status, yawDegrees, pitchDegrees, this.flightTicks, corrected, missDistance);
        }
    }

    private record ClosestApproach(
            Vec3 projectilePoint,
            Vec3 targetCenter,
            double missDistance,
            double flightTicks
    ) {
    }

    private record AngleCorrection(float yawDegrees, float pitchDegrees) {
    }
}
