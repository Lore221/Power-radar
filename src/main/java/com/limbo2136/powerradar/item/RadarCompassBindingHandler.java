package com.limbo2136.powerradar.item;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.entity.RadarControllerBlockEntity;
import com.limbo2136.powerradar.registry.ModDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Превращает один обычный компас в сетевой без регистрации отдельного предмета. */
@EventBusSubscriber(modid = PowerRadar.MOD_ID)
public final class RadarCompassBindingHandler {
    private RadarCompassBindingHandler() {
    }

    @SubscribeEvent
    public static void bindToRadarController(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.is(Items.COMPASS)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof RadarControllerBlockEntity controller)
                || controller.radarNetworkId() == null) {
            return;
        }

        if (!event.getLevel().isClientSide()) {
            event.getLevel().playSound(
                    null, event.getPos(), SoundEvents.LODESTONE_COMPASS_LOCK,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
            bindOneCompass(held, event.getEntity(), controller.radarNetworkId());
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }

    private static void bindOneCompass(
            ItemStack held,
            net.minecraft.world.entity.player.Player player,
            java.util.UUID networkId
    ) {
        boolean replaceHeld = !player.hasInfiniteMaterials() && held.getCount() == 1;
        ItemStack radarCompass = replaceHeld ? held : held.transmuteCopy(Items.COMPASS, 1);
        if (!replaceHeld) {
            held.consume(1, player);
        }

        radarCompass.remove(DataComponents.LODESTONE_TRACKER);
        radarCompass.set(ModDataComponents.POWER_RADAR_NETWORK_ID.get(), networkId);
        radarCompass.set(DataComponents.ITEM_NAME, Component.translatable("item.power_radar.radar_compass"));
        radarCompass.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        if (!replaceHeld && !player.getInventory().add(radarCompass)) {
            player.drop(radarCompass, false);
        }
    }
}
