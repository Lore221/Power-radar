package com.limbo2136.powerradar.bridge;

import com.simibubi.create.foundation.gui.AllIcons;
import java.util.function.Function;

/** Передаёт common-настройкам клиентские иконки, не загружая клиентские классы на сервере. */
public final class TrajectoryIconBridge {
    private static Function<Boolean, AllIcons> iconProvider = ignored -> AllIcons.I_NONE;

    private TrajectoryIconBridge() {
    }

    public static void configure(Function<Boolean, AllIcons> iconProvider) {
        TrajectoryIconBridge.iconProvider = iconProvider;
    }

    public static AllIcons icon(boolean highArc) {
        return iconProvider.apply(highArc);
    }
}
