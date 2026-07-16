package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OnboardComputerBlockItem extends PowerRadarElectricalBlockItem {
    public OnboardComputerBlockItem(Block block, Item.Properties properties) { super(block, properties, TooltipKind.MONITOR_CONTROLLER); }
    @Override public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        UUID target = context.getLevel().isClientSide() ? null
                : RadarNetworkTuning.ensureNetworkAt(context.getLevel(), context.getClickedPos());
        if (player != null && !player.isShiftKeyDown()
                && RadarNetworkTuning.isSourceAt(context.getLevel(), context.getClickedPos())) {
            if (!context.getLevel().isClientSide()) {
                UUID current = context.getItemInHand().get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
                if (target.equals(current)) {
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.radar_link.already_tuned"), true);
                } else {
                    context.getItemInHand().set(ModDataComponents.POWER_RADAR_NETWORK_ID.get(), target);
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.radar_link.connected"), true);
                }
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }
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

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
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
    @Override protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player,
            ItemStack stack, BlockState state) {
        boolean result = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer) {
            UUID stackNetworkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            boolean tuned = stackNetworkId != null;
            RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
            UUID id = tuned ? stackNetworkId : manager.createNetwork();
            computer.initializeNetwork(id);
            if (player != null) {
                RadarNetworkConnectionStatus status = manager.resolveControllersForConsumer(
                        id, GlobalPos.of(level.dimension(), pos)).status();
                player.displayClientMessage(status == RadarNetworkConnectionStatus.OUT_OF_RANGE
                        ? Component.translatable("message.power_radar.network.link_out_of_range")
                                .withStyle(ChatFormatting.RED)
                        : Component.translatable(tuned
                                ? "message.power_radar.network.item_tuned"
                                : "message.power_radar.network.created"), true);
            }
        }
        return result;
    }

    @Override public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get()) || super.isFoil(stack);
    }

    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        boolean tuned = stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        tooltip.add(Component.translatable(tuned
                ? "tooltip.power_radar.radar_link.tuned"
                : "tooltip.power_radar.radar_link.untuned")
                .withStyle(tuned ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }
}
