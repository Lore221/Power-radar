package com.limbo2136.powerradar.client.radarlink;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.world.phys.AABB;

public final class CatnipOutlinerAdapter {
    private CatnipOutlinerAdapter() {
    }

    public static void showRadarLinkOutline(Object key, AABB bounds, int color) {
        Outliner.getInstance()
                .showAABB(key, bounds, 2)
                .lineWidth(0.03125F)
                .disableLineNormals()
                .colored(color);
    }
}
