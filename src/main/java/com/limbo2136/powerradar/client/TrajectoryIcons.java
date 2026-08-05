package com.limbo2136.powerradar.client;

import com.simibubi.create.foundation.gui.AllIcons;

public final class TrajectoryIcons {
    private static final AllIcons FLAT = new PowerRadarGuiIcon(224, 48);
    private static final AllIcons HIGH = new PowerRadarGuiIcon(240, 48);

    private TrajectoryIcons() {
    }

    public static AllIcons icon(boolean highArc) {
        return highArc ? HIGH : FLAT;
    }
}
