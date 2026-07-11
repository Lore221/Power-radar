package com.limbo2136.powerradar.compat.create.display;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DetectedRadarTracksDisplaySource extends DisplaySource {
    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity source = context.getSourceBlockEntity();
        int tracks = source instanceof RadarControllerBlockEntity controller
                ? controller.cachedTargetCount()
                : 0;
        return List.of(Component.translatable("display.power_radar.detected_radar_tracks", tracks));
    }

    @Override
    public int getPassiveRefreshTicks() {
        return RadarConstants.RADAR_DISPLAY_LINK_REFRESH_INTERVAL_TICKS;
    }
}
