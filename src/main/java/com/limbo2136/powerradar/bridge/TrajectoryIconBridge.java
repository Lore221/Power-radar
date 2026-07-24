package com.limbo2136.powerradar.bridge;

import com.simibubi.create.foundation.gui.AllIcons;
import java.lang.reflect.Method;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class TrajectoryIconBridge {
    // Строка класса и сигнатура icon(boolean) отделяют общий интерфейс прокрутки от клиентского атласа.
    private static final String CLIENT_ICONS = "com.limbo2136.powerradar.client.TrajectoryIcons";
    private static Method clientIconMethod;

    private TrajectoryIconBridge() {
    }

    public static AllIcons icon(boolean highArc) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return AllIcons.I_NONE;
        }
        try {
            if (clientIconMethod == null) {
                Class<?> icons = Class.forName(CLIENT_ICONS);
                clientIconMethod = icons.getMethod("icon", boolean.class);
            }
            return (AllIcons) clientIconMethod.invoke(null, highArc);
        } catch (ReflectiveOperationException exception) {
            return AllIcons.I_NONE;
        }
    }
}
