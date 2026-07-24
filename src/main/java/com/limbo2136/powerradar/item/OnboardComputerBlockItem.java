package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OnboardComputerBlockItem extends PowerRadarElectricalBlockItem {
    public OnboardComputerBlockItem(Block block, Item.Properties properties) {
        super(block, properties, Target.ONBOARD_COMPUTER);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        if (!SableRadarIntegration.canPlaceOnStructure(context.getLevel(), placeContext.getClickedPos())) {
            if (!context.getLevel().isClientSide() && player != null) {
                player.displayClientMessage(Component.translatable(
                        "message.power_radar.onboard_computer.sable_only").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    protected boolean updateCustomBlockEntityTag(
            BlockPos pos,
            Level level,
            @Nullable Player player,
            ItemStack stack,
            BlockState state
    ) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer) {
            // Каждый установленный Onboard Computer получает собственную сеть независимо от компонента стака.
            stack.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            computer.createNewOwnNetwork(serverLevel);
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.power_radar.network.created"), true);
            }
        }
        return result;
    }
}
