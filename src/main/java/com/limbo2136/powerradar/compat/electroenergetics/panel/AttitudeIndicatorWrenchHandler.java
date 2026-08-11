package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.limbo2136.powerradar.PowerRadar;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Обычный клик ключом переключает режим авиагоризонта, а Shift-клик оставляет снятие модулю CEE. */
@EventBusSubscriber(modid = PowerRadar.MOD_ID)
public final class AttitudeIndicatorWrenchHandler {
    private AttitudeIndicatorWrenchHandler() {
    }

    @SubscribeEvent
    public static void toggleMode(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity().isShiftKeyDown()
                || !(event.getItemStack().getItem() instanceof WrenchItem)
                || !(event.getLevel().getBlockEntity(event.getPos())
                        instanceof ElectricalPanelBlockEntity panel)
                || event.getFace() != panel.getBlockState().getValue(ElectricalPanelBlock.FACING)) {
            return;
        }

        Vec3 localHit = event.getHitVec().getLocation().subtract(Vec3.atLowerCornerOf(event.getPos()));
        ElectricalPanelSlot slot = panel.getHoveringAttachmentIndex(localHit);
        if (slot == null) {
            return;
        }
        PanelAttachment attachment = panel.getAttachments()[slot.ordinal()];
        if (!(attachment instanceof AttitudeIndicatorPanelAttachment indicator)) {
            return;
        }

        if (!event.getLevel().isClientSide()) {
            indicator.toggleKagMode();
            IWrenchable.playRotateSound(event.getLevel(), event.getPos());
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);
    }
}
