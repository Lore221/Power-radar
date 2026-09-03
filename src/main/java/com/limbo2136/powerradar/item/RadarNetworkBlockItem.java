package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.radar.network.RadarNetworkMember;
import com.limbo2136.powerradar.block.RadarControllerBlock;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.TargetControllerBlockEntity;
import com.limbo2136.powerradar.block.TargetControllerBlock;
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

/**
 * Общая настройка предметов радарных источников и потребителей.
 * UUID можно скопировать предметом только с установленного Radar Controller.
 */
public class RadarNetworkBlockItem extends PowerRadarElectricalBlockItem {
    public RadarNetworkBlockItem(Block block, Item.Properties properties, Target tooltipTarget) {
        super(block, properties, tooltipTarget);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player != null && !player.isShiftKeyDown()
                && RadarNetworkTuning.isRadarSourceAt(level, context.getClickedPos())) {
            if (!level.isClientSide()) {
                ItemStack stack = context.getItemInHand();
                UUID sourceNetworkId = RadarNetworkTuning.ensureRadarSourceNetworkAt(
                        level, context.getClickedPos());
                UUID currentNetworkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
                if (sourceNetworkId != null
                        && getBlock() instanceof RadarControllerBlock sourceBlock
                        && level instanceof ServerLevel serverLevel
                        && !RadarNetworkManager.get(serverLevel.getServer())
                                .canRadarSourceJoinNetwork(sourceNetworkId, sourceBlock.networkKind())) {
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.network.radar_type_mismatch").withStyle(ChatFormatting.RED), true);
                } else if (sourceNetworkId != null
                        && getBlock() instanceof TargetControllerBlock
                        && !RadarNetworkManager.get(((ServerLevel) level).getServer())
                                .targetControllersAllowed(sourceNetworkId)) {
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.network.target_controller_forbidden").withStyle(ChatFormatting.RED), true);
                } else if (sourceNetworkId != null && sourceNetworkId.equals(currentNetworkId)) {
                    player.displayClientMessage(Component.translatable(
                            "message.power_radar.radar_link.already_tuned"), true);
                } else if (sourceNetworkId != null) {
                    stack.set(ModDataComponents.POWER_RADAR_NETWORK_ID.get(), sourceNetworkId);
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
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof RadarNetworkMember member)) {
            return result;
        }

        UUID networkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        if (networkId != null
                && member instanceof TargetControllerBlockEntity
                && !RadarNetworkManager.get(serverLevel.getServer()).targetControllersAllowed(networkId)) {
            stack.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            networkId = null;
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "message.power_radar.network.target_controller_forbidden").withStyle(ChatFormatting.RED), true);
            }
        }
        if (networkId != null
                && member instanceof RadarControllerBlockEntity controller
                && !controller.canJoinRadarNetwork(networkId)) {
            // A source item can outlive its network or be copied from a different
            // radar class. Do not let setRadarNetworkId silently ignore the stale
            // binding; clear it so placement creates a network of the source's kind.
            stack.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            networkId = null;
            if (player != null) {
                player.displayClientMessage(Component.translatable(
                        "message.power_radar.network.radar_type_mismatch").withStyle(ChatFormatting.RED), true);
            }
        }
        if (networkId == null && member.createsRadarNetworkWhenUntuned()) {
            networkId = member instanceof RadarControllerBlockEntity controller
                    ? RadarNetworkManager.get(serverLevel.getServer()).createNetwork(controller.radarNetworkKind())
                    : RadarNetworkManager.get(serverLevel.getServer()).createNetwork();
        }
        member.setRadarNetworkId(networkId);
        if (player != null) {
            if (networkId == null) {
                player.displayClientMessage(Component.translatable(
                        "message.power_radar.network.consumer_untuned").withStyle(ChatFormatting.GRAY), true);
            } else {
                player.displayClientMessage(Component.translatable(stack.has(
                        ModDataComponents.POWER_RADAR_NETWORK_ID.get())
                        ? "message.power_radar.network.item_tuned"
                        : "message.power_radar.network.created"), true);
            }
        }
        return result;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get()) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get())) {
            tooltip.add(Component.translatable("item.power_radar.network_bound")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}
