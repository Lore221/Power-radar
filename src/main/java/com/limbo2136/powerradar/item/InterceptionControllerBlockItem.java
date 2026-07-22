package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class InterceptionControllerBlockItem extends PowerRadarElectricalBlockItem {
    public InterceptionControllerBlockItem(Block block, Item.Properties properties) {
        super(block, properties, Target.INTERCEPTION_CONTROLLER);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player != null && !player.isShiftKeyDown()
                && InterceptionNetworkTuning.isSourceAt(level, context.getClickedPos())) {
            ItemStack stack = context.getItemInHand();
            if (!level.isClientSide()) {
                UUID targetNetworkId = InterceptionNetworkTuning.ensureNetworkAt(
                        level, context.getClickedPos());
                UUID currentNetworkId = stack.get(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
                if (targetNetworkId != null && targetNetworkId.equals(currentNetworkId)) {
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.interception_network.already_connected"), true);
                } else if (targetNetworkId != null) {
                    stack.set(ModDataComponents.INTERCEPTION_NETWORK_ID.get(), targetNetworkId);
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.interception_network.connected"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(ModDataComponents.INTERCEPTION_NETWORK_ID.get())) {
            if (!level.isClientSide()) {
                stack.remove(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
                player.displayClientMessage(Component.translatable(
                        "message.power_radar.interception_network.item_cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return super.use(level, player, hand);
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
        if (level instanceof ServerLevel
                && level.getBlockEntity(pos) instanceof InterceptionControllerBlockEntity controller) {
            controller.setInterceptionNetworkId(
                    stack.get(ModDataComponents.INTERCEPTION_NETWORK_ID.get()));
        }
        return result;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.INTERCEPTION_NETWORK_ID.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        boolean tuned = stack.has(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
        tooltip.add(Component.translatable(tuned
                        ? "tooltip.power_radar.interception_controller.tuned"
                        : "tooltip.power_radar.interception_controller.untuned")
                .withStyle(tuned ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }
}
