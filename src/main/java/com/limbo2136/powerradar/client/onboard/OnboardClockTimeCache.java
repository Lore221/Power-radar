package com.limbo2136.powerradar.client.onboard;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.GameRules;

/** Общий сбрасываемый клиентский кэш для всех модулей часов Onboard Computer. */
final class OnboardClockTimeCache {
    private static final long CACHE_INTERVAL_TICKS = 20L * 30L;
    private static final double HALF_DAY_TICKS = 12_000.0D;
    private static final double SIX_HOURS_TICKS = 6_000.0D;

    @Nullable
    private static ClientLevel cachedLevel;
    private static long cachedDayTime;
    private static long cachedAtGameTime = Long.MIN_VALUE;
    private static boolean daylightCycleAdvances;

    private OnboardClockTimeCache() {
    }

    static float hourHandAngle(float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clear();
            return 0.0F;
        }

        long gameTime = level.getGameTime();
        if (level != cachedLevel
                || cachedAtGameTime == Long.MIN_VALUE
                || gameTime < cachedAtGameTime
                || gameTime - cachedAtGameTime >= CACHE_INTERVAL_TICKS) {
            cachedLevel = level;
            cachedDayTime = level.getDayTime();
            cachedAtGameTime = gameTime;
            daylightCycleAdvances = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        }

        double estimatedDayTime = cachedDayTime;
        if (daylightCycleAdvances) {
            estimatedDayTime += gameTime - cachedAtGameTime + partialTick;
        }
        double halfDayPosition = positiveModulo(
                estimatedDayTime + SIX_HOURS_TICKS,
                HALF_DAY_TICKS);
        return (float) (-halfDayPosition / HALF_DAY_TICKS * 360.0D);
    }

    private static double positiveModulo(double value, double divisor) {
        double remainder = value % divisor;
        return remainder < 0.0D ? remainder + divisor : remainder;
    }

    private static void clear() {
        cachedLevel = null;
        cachedDayTime = 0L;
        cachedAtGameTime = Long.MIN_VALUE;
        daylightCycleAdvances = false;
    }
}
