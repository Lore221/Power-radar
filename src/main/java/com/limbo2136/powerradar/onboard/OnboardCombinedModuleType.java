package com.limbo2136.powerradar.onboard;

import javax.annotation.Nullable;

/** Составные приборы, собираемые из двух модулей одного столбца. */
public enum OnboardCombinedModuleType {
    ACCELEROMETER(OnboardModuleType.SPEEDOMETER, OnboardModuleType.CLOCK),
    VARIOMETER(OnboardModuleType.ALTIMETER, OnboardModuleType.CLOCK);

    private final OnboardModuleType firstPart;
    private final OnboardModuleType secondPart;

    OnboardCombinedModuleType(OnboardModuleType firstPart, OnboardModuleType secondPart) {
        this.firstPart = firstPart;
        this.secondPart = secondPart;
    }

    private boolean matches(OnboardModuleType front, OnboardModuleType rear) {
        return (front == this.firstPart && rear == this.secondPart)
                || (front == this.secondPart && rear == this.firstPart);
    }

    @Nullable
    public static OnboardCombinedModuleType fromParts(
            OnboardModuleType front,
            OnboardModuleType rear
    ) {
        for (OnboardCombinedModuleType type : values()) {
            if (type.matches(front, rear)) {
                return type;
            }
        }
        return null;
    }
}
