package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * BlockItem без вычисляемых параметров, которому нужна только настраиваемая Shift-подсказка.
 */
public final class PowerRadarDescriptionBlockItem extends BlockItem {
    private final Target tooltipTarget;

    public PowerRadarDescriptionBlockItem(Block block, Item.Properties properties, Target tooltipTarget) {
        super(block, properties);
        this.tooltipTarget = tooltipTarget;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        PowerRadarElectricalBlockItem.appendConfiguredText(this.tooltipTarget, tooltip);
    }
}
