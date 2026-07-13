package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.network.TargetingCardOpenPayload;
import com.limbo2136.powerradar.network.AllowlistCardOpenPayload;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.List;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

public class RadarFilterCardItem extends Item {
    public enum Kind {
        TARGETING,
        DISPLAY,
        ALLOWLIST
    }

    private final Kind kind;

    public RadarFilterCardItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    public static int filterMask(ItemStack stack, int fallback) {
        Integer value = stack.get(ModDataComponents.RADAR_FILTER_MASK.get());
        return value == null ? fallback : RadarDetectionFilters.sanitize(value);
    }

    public static String allowlist(ItemStack stack) {
        String value = stack.get(ModDataComponents.RADAR_ALLOWLIST.get());
        return value == null ? "" : value;
    }

    public static List<String> allowlistNames(ItemStack stack) {
        return java.util.Arrays.stream(allowlist(stack).split("\\n"))
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .toList();
    }

    public static boolean allowlistSableMode(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModDataComponents.ALLOWLIST_SABLE_MODE.get()));
    }

    public static int cardOption(ItemStack stack, int fallback) {
        Integer value = stack.get(ModDataComponents.TARGETING_CARD_OPTION.get());
        return value == null ? fallback : value == 0 ? 0 : 1;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (this.kind == Kind.TARGETING || this.kind == Kind.DISPLAY) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                boolean displayCard = this.kind == Kind.DISPLAY;
                PacketDistributor.sendToPlayer(serverPlayer, new TargetingCardOpenPayload(
                        hand,
                        displayCard ? 1 : 0,
                        filterMask(stack, 0),
                        cardOption(stack, displayCard ? 0 : 1)));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (this.kind == Kind.ALLOWLIST) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                List<String> onlinePlayers = serverPlayer.getServer().getPlayerList().getPlayers().stream()
                        .map(online -> online.getGameProfile().getName())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                PacketDistributor.sendToPlayer(serverPlayer, new AllowlistCardOpenPayload(
                        hand,
                        allowlistSableMode(stack),
                        cardOption(stack, 1),
                        onlinePlayers,
                        allowlistNames(stack)));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
                                                   InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
