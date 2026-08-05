package com.limbo2136.powerradar.bridge;

import com.simibubi.create.foundation.gui.AllIcons;
import java.util.function.Supplier;

/** Передаёт common-настройкам клиентские иконки, не загружая клиентские классы на сервере. */
public final class ShellAlarmIconBridge {
    private static Supplier<AllIcons> dimensionsProvider = () -> AllIcons.I_NONE;

    private ShellAlarmIconBridge() {
    }

    public static void configure(Supplier<AllIcons> dimensionsProvider) {
        ShellAlarmIconBridge.dimensionsProvider = dimensionsProvider;
    }

    public static AllIcons dimensions() {
        return dimensionsProvider.get();
    }
}
