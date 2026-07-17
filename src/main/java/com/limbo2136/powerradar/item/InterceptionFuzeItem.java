package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.PowerRadarDebugOptions;
import com.limbo2136.powerradar.PowerRadarServerConfig;
import com.limbo2136.powerradar.interception.InterceptionCoordinator;
import com.limbo2136.powerradar.interception.InterceptionCoordinator.ThreatSnapshot;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.munitions.AbstractCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.AbstractBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.fuzes.FuzeItem;

public class InterceptionFuzeItem extends FuzeItem {
    private static final double DETECTION_RANGE_BLOCKS = 20.0;
    private static final double HALF_CONE_ANGLE_DEGREES = 30.0;
    private static final double MIN_DIRECTION_LENGTH_SQR = 1.0E-6;
    private static final int MIN_ARMING_TICKS = 2;
    private static final String MANUAL_MODE_TAG = "PowerRadarManualInterceptionFuze";
    private static final String TRAVELLED_DISTANCE_TAG = "PowerRadarInterceptionFuzeTravelled";
    private static final String LAST_X_TAG = "PowerRadarInterceptionFuzeLastX";
    private static final String LAST_Y_TAG = "PowerRadarInterceptionFuzeLastY";
    private static final String LAST_Z_TAG = "PowerRadarInterceptionFuzeLastZ";

    public InterceptionFuzeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public boolean onProjectileTick(ItemStack stack, AbstractCannonProjectile projectile) {
        if (!(projectile.level() instanceof ServerLevel level)) {
            return false;
        }
        double travelledDistance = updateTravelledDistance(projectile);
        if (projectile.tickCount < MIN_ARMING_TICKS) {
            return false;
        }
        UUID interceptorUuid = projectile.getUUID();
        CompoundTag projectileData = projectile.getPersistentData();
        if (projectileData.getBoolean(MANUAL_MODE_TAG)) {
            return tickManualAirburst(level, projectile, travelledDistance);
        }
        UUID targetUuid = InterceptionCoordinator.interceptorTarget(level, interceptorUuid);
        if (targetUuid == null) {
            targetUuid = InterceptionCoordinator.bindInterceptor(level, interceptorUuid, projectile.position());
        }
        if (targetUuid == null) {
            projectileData.putBoolean(MANUAL_MODE_TAG, true);
            if (PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
                PowerRadar.LOGGER.info(
                        "[PowerRadar BugReport][Interception][Fuze] interceptor={} pos={} state=manual-armed velocity={} travelledDistance={} airburstDistance={}",
                        interceptorUuid,
                        shortVec(projectile.position()),
                        shortVec(projectile.getDeltaMovement()),
                        round(travelledDistance),
                        round(PowerRadarServerConfig.manualInterceptionFuzeDistanceBlocks()));
            }
            return false;
        }
        Entity target = level.getEntity(targetUuid);
        if (!(target instanceof AbstractBigCannonProjectile)
                || target == projectile
                || !target.isAlive()) {
            InterceptionCoordinator.clearInterceptor(level.getServer(), interceptorUuid);
            logFuze(projectile, targetUuid, target, "target-invalid", 0.0, 0.0);
            return false;
        }
        Vec3 toTarget = target.position().subtract(projectile.position());
        double distanceSqr = toTarget.lengthSqr();
        if (distanceSqr > DETECTION_RANGE_BLOCKS * DETECTION_RANGE_BLOCKS
                || distanceSqr < MIN_DIRECTION_LENGTH_SQR) {
            return false;
        }
        Vec3 forward = projectile.getDeltaMovement();
        if (forward.lengthSqr() < MIN_DIRECTION_LENGTH_SQR) {
            forward = projectile.getOrientation();
        }
        if (forward.lengthSqr() < MIN_DIRECTION_LENGTH_SQR) {
            return false;
        }
        double minimumDot = Math.cos(Math.toRadians(HALF_CONE_ANGLE_DEGREES));
        double directionDot = forward.normalize().dot(toTarget.normalize());
        logFuze(projectile, targetUuid, target, "tracking", Math.sqrt(distanceSqr), directionDot);
        if (directionDot < minimumDot) {
            return false;
        }
        InterceptionCoordinator.DestructionRoll destruction =
                attemptThreatDestruction(level, targetUuid, target);
        destroyAdditionalThreatsInCone(level, projectile, targetUuid, minimumDot);
        logFuze(
                projectile,
                targetUuid,
                target,
                destruction.destroyed() ? "detonated-destroyed" : "detonated-shell-survived",
                Math.sqrt(distanceSqr),
                directionDot,
                destruction.probability(),
                destruction.roll(),
                destruction.nextProbability());
        InterceptionCoordinator.clearInterceptor(level.getServer(), interceptorUuid);
        return true;
    }

    private static boolean tickManualAirburst(
            ServerLevel level,
            AbstractCannonProjectile projectile,
            double travelledDistance
    ) {
        double airburstDistance = PowerRadarServerConfig.manualInterceptionFuzeDistanceBlocks();
        if (travelledDistance < airburstDistance) {
            return false;
        }
        double minimumDot = Math.cos(Math.toRadians(HALF_CONE_ANGLE_DEGREES));
        int candidates = destroyLiveThreatsInCone(level, projectile, minimumDot);
        if (PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            PowerRadar.LOGGER.info(
                    "[PowerRadar BugReport][Interception][Fuze] interceptor={} state=manual-detonated pos={} velocity={} travelledDistance={} airburstDistance={} coneCandidates={}",
                    projectile.getUUID(),
                    shortVec(projectile.position()),
                    shortVec(projectile.getDeltaMovement()),
                    round(travelledDistance),
                    round(airburstDistance),
                    candidates);
        }
        InterceptionCoordinator.clearInterceptor(level.getServer(), projectile.getUUID());
        return true;
    }

    private static int destroyLiveThreatsInCone(
            ServerLevel level,
            AbstractCannonProjectile projectile,
            double minimumDot
    ) {
        Vec3 forward = forward(projectile);
        if (forward.lengthSqr() < MIN_DIRECTION_LENGTH_SQR) {
            return 0;
        }
        Vec3 normalizedForward = forward.normalize();
        int candidates = 0;
        for (AbstractBigCannonProjectile threat : level.getEntitiesOfClass(
                AbstractBigCannonProjectile.class,
                projectile.getBoundingBox().inflate(DETECTION_RANGE_BLOCKS),
                entity -> entity != projectile && entity.isAlive())) {
            Vec3 toThreat = threat.position().subtract(projectile.position());
            double distanceSqr = toThreat.lengthSqr();
            if (!isInsideCone(distanceSqr, normalizedForward, toThreat, minimumDot)) {
                continue;
            }
            candidates++;
            InterceptionCoordinator.DestructionRoll destruction =
                    attemptThreatDestruction(level, threat.getUUID(), threat);
            logFuze(
                    projectile,
                    threat.getUUID(),
                    threat,
                    destruction.destroyed() ? "manual-destroyed" : "manual-shell-survived",
                    Math.sqrt(distanceSqr),
                    normalizedForward.dot(toThreat.normalize()),
                    destruction.probability(),
                    destruction.roll(),
                    destruction.nextProbability());
        }
        return candidates;
    }

    private static InterceptionCoordinator.DestructionRoll attemptThreatDestruction(
            ServerLevel level,
            UUID threatUuid,
            Entity threat
    ) {
        InterceptionCoordinator.DestructionRoll destruction =
                InterceptionCoordinator.rollDestruction(
                        level,
                        threatUuid,
                        PowerRadarServerConfig.interceptionShellDestructionProbability());
        if (destruction.destroyed()) {
            threat.discard();
            InterceptionCoordinator.resolveThreat(level.getServer(), threatUuid);
        }
        return destruction;
    }

    private static void destroyAdditionalThreatsInCone(
            ServerLevel level,
            AbstractCannonProjectile projectile,
            UUID primaryTargetUuid,
            double minimumDot
    ) {
        Vec3 forward = forward(projectile);
        if (forward.lengthSqr() < MIN_DIRECTION_LENGTH_SQR) {
            return;
        }
        Vec3 normalizedForward = forward.normalize();
        for (ThreatSnapshot snapshot : InterceptionCoordinator.interceptorThreatSnapshots(level, projectile.getUUID())) {
            UUID threatUuid = snapshot.threatUuid();
            if (primaryTargetUuid.equals(threatUuid)) {
                continue;
            }
            Entity threat = level.getEntity(threatUuid);
            if (!(threat instanceof AbstractBigCannonProjectile)
                    || threat == projectile
                    || !threat.isAlive()) {
                continue;
            }
            Vec3 toThreat = threat.position().subtract(projectile.position());
            double distanceSqr = toThreat.lengthSqr();
            if (!isInsideCone(distanceSqr, normalizedForward, toThreat, minimumDot)) {
                continue;
            }
            double directionDot = normalizedForward.dot(toThreat.normalize());
            InterceptionCoordinator.DestructionRoll destruction =
                    attemptThreatDestruction(level, threatUuid, threat);
            logFuze(
                    projectile,
                    threatUuid,
                    threat,
                    destruction.destroyed() ? "detonated-extra-destroyed" : "detonated-extra-survived",
                    Math.sqrt(distanceSqr),
                    directionDot,
                    destruction.probability(),
                    destruction.roll(),
                    destruction.nextProbability());
        }
    }

    private static Vec3 forward(AbstractCannonProjectile projectile) {
        Vec3 forward = projectile.getDeltaMovement();
        return forward.lengthSqr() < MIN_DIRECTION_LENGTH_SQR
                ? projectile.getOrientation()
                : forward;
    }

    private static boolean isInsideCone(
            double distanceSqr,
            Vec3 normalizedForward,
            Vec3 toThreat,
            double minimumDot
    ) {
        return distanceSqr <= DETECTION_RANGE_BLOCKS * DETECTION_RANGE_BLOCKS
                && distanceSqr >= MIN_DIRECTION_LENGTH_SQR
                && normalizedForward.dot(toThreat.normalize()) >= minimumDot;
    }

    private static double updateTravelledDistance(AbstractCannonProjectile projectile) {
        CompoundTag data = projectile.getPersistentData();
        Vec3 current = projectile.position();
        Vec3 previous = data.contains(LAST_X_TAG)
                ? new Vec3(data.getDouble(LAST_X_TAG), data.getDouble(LAST_Y_TAG), data.getDouble(LAST_Z_TAG))
                : new Vec3(projectile.xo, projectile.yo, projectile.zo);
        double travelled = data.getDouble(TRAVELLED_DISTANCE_TAG);
        double segment = current.distanceTo(previous);
        if (Double.isFinite(segment)) {
            travelled += segment;
        }
        data.putDouble(TRAVELLED_DISTANCE_TAG, travelled);
        data.putDouble(LAST_X_TAG, current.x);
        data.putDouble(LAST_Y_TAG, current.y);
        data.putDouble(LAST_Z_TAG, current.z);
        return travelled;
    }

    private static void logFuze(
            AbstractCannonProjectile projectile,
            UUID targetUuid,
            Entity target,
            String state,
            double distance,
            double directionDot
    ) {
        logFuze(projectile, targetUuid, target, state, distance, directionDot, -1.0, -1.0, -1.0);
    }

    private static void logFuze(
            AbstractCannonProjectile projectile,
            UUID targetUuid,
            Entity target,
            String state,
            double distance,
            double directionDot,
            double destructionProbability,
            double destructionRoll,
            double nextDestructionProbability
    ) {
        if (!PowerRadarDebugOptions.interceptionSystemBugReportLogging()) {
            return;
        }
        PowerRadar.LOGGER.info(
                "[PowerRadar BugReport][Interception][Fuze] interceptor={} state={} pos={} velocity={} target={} targetPos={} targetVelocity={} distance={} directionDot={} destructionProbability={} destructionRoll={} nextDestructionProbability={}",
                projectile.getUUID(),
                state,
                shortVec(projectile.position()),
                shortVec(projectile.getDeltaMovement()),
                targetUuid,
                target == null ? "null" : shortVec(target.position()),
                target == null ? "null" : shortVec(target.getDeltaMovement()),
                round(distance),
                round(directionDot),
                destructionProbability < 0.0 ? "n/a" : round(destructionProbability),
                destructionRoll < 0.0 ? "n/a" : round(destructionRoll),
                nextDestructionProbability < 0.0 ? "n/a" : round(nextDestructionProbability));
    }

    private static String shortVec(Vec3 vec) {
        return "(" + round(vec.x) + "," + round(vec.y) + "," + round(vec.z) + ")";
    }

    private static double round(double value) {
        return Double.isFinite(value) ? Math.round(value * 1000.0) / 1000.0 : value;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("power_radar.tooltip.interception_fuze.range",
                        Math.round(DETECTION_RANGE_BLOCKS))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("power_radar.tooltip.interception_fuze.cone",
                        Math.round(HALF_CONE_ANGLE_DEGREES * 2.0))
                .withStyle(ChatFormatting.GRAY));
    }
}
