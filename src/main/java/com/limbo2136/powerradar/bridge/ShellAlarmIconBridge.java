package com.limbo2136.powerradar.bridge;

import com.simibubi.create.foundation.gui.AllIcons;
import java.lang.reflect.Method;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class ShellAlarmIconBridge {
    private static final String CLIENT_ICONS = "com.limbo2136.powerradar.client.ShellAlarmIcons";
    private static Method dimensionsMethod;

    private ShellAlarmIconBridge() {
    }

    public static AllIcons dimensions() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return AllIcons.I_NONE;
        }
        try {
            if (dimensionsMethod == null) {
                Class<?> icons = Class.forName(CLIENT_ICONS);
                dimensionsMethod = icons.getMethod("dimensions");
            }
            return (AllIcons) dimensionsMethod.invoke(null);
        } catch (ReflectiveOperationException exception) {
            return AllIcons.I_NONE;
        }
    }
}
