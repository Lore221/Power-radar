package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModDataComponents;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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

public final class ShellAlarmBlockItem extends PowerRadarElectricalBlockItem {
    public ShellAlarmBlockItem(Block block, Item.Properties properties) {
        super(block, properties, TooltipKind.SHELL_ALARM);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player != null && !player.isShiftKeyDown()
                && (level.getBlockEntity(context.getClickedPos()) instanceof RadarLinkBlockEntity
                || level.getBlockEntity(context.getClickedPos()) instanceof ShellAlarmBlockEntity)) {
            ItemStack stack = context.getItemInHand();
            if (!level.isClientSide()) {
                UUID targetNetworkId = level.getBlockEntity(context.getClickedPos()) instanceof RadarLinkBlockEntity targetLink
                        ? targetLink.ensureNetworkId()
                        : ((ShellAlarmBlockEntity) level.getBlockEntity(context.getClickedPos())).ensureNetworkId();
                UUID currentNetworkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
                if (targetNetworkId.equals(currentNetworkId)) {
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.radar_link.already_tuned"), true);
                } else {
                    stack.set(ModDataComponents.POWER_RADAR_NETWORK_ID.get(), targetNetworkId);
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.radar_link.connected"), true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get())) {
            if (!level.isClientSide()) {
                stack.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
                player.displayClientMessage(Component.translatable(
                        "message.power_radar.network.item_cleared"), true);
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
                && level.getBlockEntity(pos) instanceof ShellAlarmBlockEntity alarm) {
            UUID networkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            if (networkId != null) {
                alarm.initializeNetwork(networkId);
                if (player != null) {
                    RadarNetworkConnectionStatus status = RadarNetworkManager.get(
                                    ((ServerLevel) level).getServer())
                            .resolveControllersForConsumer(networkId,
                                    GlobalPos.of(level.dimension(), pos)).status();
                    player.displayClientMessage(status == RadarNetworkConnectionStatus.OUT_OF_RANGE
                            ? Component.translatable("message.power_radar.network.link_out_of_range")
                                    .withStyle(ChatFormatting.RED)
                            : Component.translatable("message.power_radar.network.item_tuned"), true);
                }
            }
        }
        return result;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get())
                        ? "tooltip.power_radar.radar_link.tuned"
                        : "tooltip.power_radar.radar_link.untuned")
                .withStyle(stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get())
                        ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }
}
