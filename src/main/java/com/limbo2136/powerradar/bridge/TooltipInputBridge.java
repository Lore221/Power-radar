package com.limbo2136.powerradar.bridge;

import java.util.function.BooleanSupplier;

/** Передаёт common-предметам состояние клиентского ввода без reflection и client imports. */
public final class TooltipInputBridge {
    private static BooleanSupplier shiftDown = () -> false;
    private static BooleanSupplier wearingGoggles = () -> false;

    private TooltipInputBridge() {
    }

    public static void configure(BooleanSupplier shiftDown, BooleanSupplier wearingGoggles) {
        TooltipInputBridge.shiftDown = shiftDown;
        TooltipInputBridge.wearingGoggles = wearingGoggles;
    }

    public static boolean isShiftDown() {
        return shiftDown.getAsBoolean();
    }

    public static boolean isWearingGoggles() {
        return wearingGoggles.getAsBoolean();
    }
}
