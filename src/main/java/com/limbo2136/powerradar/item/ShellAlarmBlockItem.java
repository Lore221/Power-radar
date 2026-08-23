package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ShellAlarmBlockItem extends RadarNetworkBlockItem {
    public ShellAlarmBlockItem(Block block, Item.Properties properties) {
        super(block, properties, Target.SHELL_ALARM);
    }
}
