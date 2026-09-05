package com.limbo2136.powerradar.compat.createbigcannons;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.world.entity.Entity;

/** Извлекает баллистику CBC отражением и сохраняет прежние безопасные значения при дрейфе API. */
public final class ShellAlarmCbcCompat {
    private ShellAlarmCbcCompat() {
    }

    private static final ClassValue<java.util.Optional<Method>> IN_GROUND = new ClassValue<>() {
        @Override
        protected java.util.Optional<Method> computeValue(Class<?> type) {
            try {
                return java.util.Optional.of(type.getMethod("isInGround"));
            } catch (NoSuchMethodException exception) {
                return java.util.Optional.empty();
            }
        }
    };

    /** Флаг CBC отличает застрявший снаряд от вершины траектории и рикошета. */
    public static boolean isInGround(Entity projectile) {
        var method = IN_GROUND.get(projectile.getClass());
        if (method.isEmpty()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.get().invoke(projectile));
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    public static Ballistics ballistics(Entity projectile) {
        Object properties = invokeRecursive(projectile, "getBallisticProperties");
        if (properties == null) {
            return Ballistics.DEFAULT;
        }
        return new Ballistics(
                Math.abs(number(properties, "gravity")),
                Math.max(0.0, number(properties, "drag")),
                bool(properties, "isQuadraticDrag"));
    }

    private static Object invokeRecursive(Object target, String methodName) {
        Class<?> type = target == null ? null : target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        return null;
    }

    private static double number(Object target, String methodName) {
        Object result = invokeRecursive(target, methodName);
        return result instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue()
                : 0.0;
    }

    private static boolean bool(Object target, String methodName) {
        return invokeRecursive(target, methodName) instanceof Boolean value && value;
    }

    public record Ballistics(double gravity, double drag, boolean quadraticDrag) {
        private static final Ballistics DEFAULT = new Ballistics(0.05, 0.0, false);
    }
}
