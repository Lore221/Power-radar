package com.limbo2136.powerradar.onboard;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Типы модулей, допустимые в компактной четырёхслотовой панели Onboard Computer. */
public enum OnboardModuleType {
    COMPASS,
    ALTIMETER,
    SPEEDOMETER,
    CLOCK,
    ATTITUDE_INDICATOR;

    private static final ResourceLocation AERONAUTICS_ALTITUDE_SENSOR =
            ResourceLocation.fromNamespaceAndPath("simulated", "altitude_sensor");
    private static final ResourceLocation AERONAUTICS_VELOCITY_SENSOR =
            ResourceLocation.fromNamespaceAndPath("simulated", "velocity_sensor");
    private static final ResourceLocation AERONAUTICS_GIMBAL_SENSOR =
            ResourceLocation.fromNamespaceAndPath("simulated", "gimbal_sensor");

    @Nullable
    public static OnboardModuleType fromStack(ItemStack stack) {
        if (stack.is(Items.COMPASS) || stack.is(Items.RECOVERY_COMPASS)) {
            return COMPASS;
        }
        if (stack.is(Items.CLOCK)) {
            return CLOCK;
        }
        if (AERONAUTICS_ALTITUDE_SENSOR.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return ALTIMETER;
        }
        if (AERONAUTICS_VELOCITY_SENSOR.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return SPEEDOMETER;
        }
        if (AERONAUTICS_GIMBAL_SENSOR.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
            return ATTITUDE_INDICATOR;
        }
        return null;
    }

    public static boolean accepts(ItemStack stack) {
        return fromStack(stack) != null;
    }
}
