package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.tooltip.PowerRadarTooltipSettings.Target;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Переносит UUID радарной сети и сети перехвата между совместимыми блоками.
 * Защитные корни можно только считать: менять их interception-сеть намеренно нельзя.
 */
public final class LinkerItem extends Item {
    public LinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!isSupported(blockEntity)) {
            return InteractionResult.PASS;
        }

        if (!context.getLevel().isClientSide()) {
            configure(context.getItemInHand(), player, blockEntity);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }

        boolean hadRadarNetwork = stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        boolean hadInterceptionNetwork = stack.has(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
        if (!hadRadarNetwork && !hadInterceptionNetwork) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide()) {
            stack.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            stack.remove(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
            message(player, "message.power_radar.linker.cleared");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.POWER_RADAR_NETWORK_ID.get())
                || stack.has(ModDataComponents.INTERCEPTION_NETWORK_ID.get())
                || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            TooltipContext context,
            List<Component> tooltip,
            TooltipFlag flag
    ) {
        super.appendHoverText(stack, context, tooltip, flag);
        PowerRadarElectricalBlockItem.appendConfiguredText(Target.LINKER, tooltip);
    }

    private static boolean isSupported(BlockEntity blockEntity) {
        return blockEntity instanceof RadarLinkBlockEntity
                || blockEntity instanceof ShellAlarmBlockEntity
                || blockEntity instanceof OnboardComputerBlockEntity
                || blockEntity instanceof InterceptionControllerBlockEntity;
    }

    private static void configure(ItemStack linker, Player player, BlockEntity blockEntity) {
        if (blockEntity instanceof InterceptionControllerBlockEntity controller) {
            configureInterceptionController(linker, player, controller);
            return;
        }

        UUID selectedInterceptionNetwork =
                linker.get(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
        if (selectedInterceptionNetwork != null) {
            configureInterceptionRoot(
                    linker, player, blockEntity, selectedInterceptionNetwork);
            return;
        }

        UUID selectedRadarNetwork = linker.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        UUID blockRadarNetwork = ensureRadarNetwork(blockEntity);
        if (selectedRadarNetwork == null) {
            linker.remove(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
            linker.set(ModDataComponents.POWER_RADAR_NETWORK_ID.get(), blockRadarNetwork);
            message(player, "message.power_radar.linker.radar_copied");
            return;
        }

        if (!selectedRadarNetwork.equals(blockRadarNetwork)) {
            if (blockEntity instanceof OnboardComputerBlockEntity) {
                message(player, "message.power_radar.linker.onboard_locked");
                return;
            }
            applyRadarNetwork(blockEntity, selectedRadarNetwork, player);
            message(player, "message.power_radar.linker.radar_applied");
            return;
        }

        // Повторное нажатие по защитному корню переключает палочку на его сеть перехвата.
        if (blockEntity instanceof ShellAlarmBlockEntity alarm) {
            copyInterceptionNetwork(linker, player, alarm.ensureInterceptionNetworkId());
        } else if (blockEntity instanceof OnboardComputerBlockEntity computer) {
            copyInterceptionNetwork(linker, player, computer.ensureInterceptionNetworkId());
        } else {
            message(player, "message.power_radar.linker.radar_already_connected");
        }
    }

    private static void configureInterceptionController(
            ItemStack linker,
            Player player,
            InterceptionControllerBlockEntity controller
    ) {
        UUID selectedNetwork = linker.get(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
        UUID controllerNetwork = controller.interceptionNetworkId();
        if (selectedNetwork == null) {
            if (controllerNetwork == null) {
                message(player, "message.power_radar.linker.interception_missing");
                return;
            }
            linker.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
            linker.set(ModDataComponents.INTERCEPTION_NETWORK_ID.get(), controllerNetwork);
            message(player, "message.power_radar.linker.interception_copied");
            return;
        }
        if (Objects.equals(selectedNetwork, controllerNetwork)) {
            message(player, "message.power_radar.linker.interception_already_connected");
            return;
        }
        controller.setInterceptionNetworkId(selectedNetwork);
        message(player, "message.power_radar.linker.interception_applied");
    }

    private static void configureInterceptionRoot(
            ItemStack linker,
            Player player,
            BlockEntity blockEntity,
            UUID selectedNetwork
    ) {
        linker.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        UUID rootNetwork;
        if (blockEntity instanceof ShellAlarmBlockEntity alarm) {
            rootNetwork = alarm.ensureInterceptionNetworkId();
        } else if (blockEntity instanceof OnboardComputerBlockEntity computer) {
            rootNetwork = computer.ensureInterceptionNetworkId();
        } else {
            message(player, "message.power_radar.linker.interception_mode");
            return;
        }
        if (selectedNetwork.equals(rootNetwork)) {
            message(player, "message.power_radar.linker.interception_already_connected");
            return;
        }
        linker.set(ModDataComponents.INTERCEPTION_NETWORK_ID.get(), rootNetwork);
        message(player, "message.power_radar.linker.interception_copied");
    }

    private static UUID ensureRadarNetwork(BlockEntity blockEntity) {
        if (blockEntity instanceof RadarLinkBlockEntity link) {
            return link.ensureNetworkId();
        }
        if (blockEntity instanceof ShellAlarmBlockEntity alarm) {
            return alarm.ensureNetworkId();
        }
        return ((OnboardComputerBlockEntity) blockEntity).ensureNetworkId();
    }

    private static void applyRadarNetwork(BlockEntity blockEntity, UUID networkId, Player player) {
        if (blockEntity instanceof RadarLinkBlockEntity link) {
            link.initializeNetwork(networkId, player);
            return;
        }
        if (blockEntity instanceof ShellAlarmBlockEntity alarm) {
            alarm.initializeNetwork(networkId);
        }
    }

    private static void copyInterceptionNetwork(ItemStack linker, Player player, UUID networkId) {
        UUID selectedNetwork = linker.get(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
        if (networkId.equals(selectedNetwork)) {
            message(player, "message.power_radar.linker.interception_already_connected");
            return;
        }
        linker.remove(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        linker.set(ModDataComponents.INTERCEPTION_NETWORK_ID.get(), networkId);
        message(player, "message.power_radar.linker.interception_copied");
    }

    private static void message(Player player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey), true);
    }
}
