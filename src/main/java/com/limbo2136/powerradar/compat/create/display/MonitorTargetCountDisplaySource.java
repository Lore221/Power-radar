package com.limbo2136.powerradar.compat.create.display;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.entity.RadarMonitorControllerBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class MonitorTargetCountDisplaySource extends NumericSingleLineDisplaySource {
    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity source = context.getSourceBlockEntity();
        int targets = source instanceof RadarMonitorControllerBlockEntity monitor
                && context.level() instanceof ServerLevel serverLevel
                ? monitor.displayLinkTargetCount(serverLevel)
                : 0;
        return Component.literal(Integer.toString(targets));
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    public int getPassiveRefreshTicks() {
        return RadarConstants.RADAR_DISPLAY_LINK_REFRESH_INTERVAL_TICKS;
    }
}
