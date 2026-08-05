package com.limbo2136.powerradar.compat.create.display;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeState;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ShellAlarmDisplaySource extends NumericSingleLineDisplaySource {
    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof ShellAlarmBlockEntity alarm)
                || alarm.electricalState() != PowerRadarCeeState.POWERED) {
            return EMPTY_LINE;
        }
        return Component.literal(Integer.toString(alarm.dangerousShellCount()));
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
