package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.network.NameCardOpenPayload;
import com.limbo2136.powerradar.registry.ModDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NameCardItem extends Item {
    public NameCardItem(Properties properties) { super(properties); }

    public static String name(ItemStack stack) {
        String value = stack.get(ModDataComponents.NAME_CARD_NAME.get());
        return value == null ? "" : value;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new NameCardOpenPayload(hand, name(stack)));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
