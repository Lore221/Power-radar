package com.limbo2136.powerradar.compat.electroenergetics.panel;

import com.george_vi.electroenergetics.CEERegistries;
import com.george_vi.electroenergetics.content.electrical_panel.PanelAttachmentMode;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachmentType;
import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Регистрирует предметы Power Radar как штатные вложения щитков CEE. */
public final class PowerRadarPanelAttachmentTypes {
    private static final ResourceLocation AERONAUTICS_GIMBAL_SENSOR =
            ResourceLocation.fromNamespaceAndPath("simulated", "gimbal_sensor");
    private static final DeferredRegister<PanelAttachmentType> ATTACHMENT_TYPES =
            DeferredRegister.create(CEERegistries.PANEL_ATTACHMENT_TYPE, PowerRadar.MOD_ID);

    public static final DeferredHolder<PanelAttachmentType, PanelAttachmentType> RADAR_LINK =
            ATTACHMENT_TYPES.register("panel_radar_link", () -> new PanelAttachmentType(
                    RadarLinkPanelAttachment::new,
                    ModItems.RADAR_LINK.get(),
                    PanelAttachmentMode.THIRD));

    public static final DeferredHolder<PanelAttachmentType, PanelAttachmentType> RADAR_DISPLAY =
            ATTACHMENT_TYPES.register("panel_radar_display", () -> new PanelAttachmentType(
                    RadarDisplayPanelAttachment::new,
                    ModItems.RADAR_DISPLAY.get(),
                    PanelAttachmentMode.FULL_DOUBLE));

    public static final DeferredHolder<PanelAttachmentType, PanelAttachmentType> ATTITUDE_INDICATOR =
            ATTACHMENT_TYPES.register("panel_attitude_indicator", () -> new PanelAttachmentType(
                    AttitudeIndicatorPanelAttachment::new,
                    registryItem(AERONAUTICS_GIMBAL_SENSOR),
                    PanelAttachmentMode.FULL_NONE));

    public static final DeferredHolder<PanelAttachmentType, PanelAttachmentType> LOGIC_DOCK =
            ATTACHMENT_TYPES.register("panel_logic_dock", () -> new PanelAttachmentType(
                    LogicDockPanelAttachment::new,
                    ModItems.LOGIC_DOCK.get(),
                    PanelAttachmentMode.HALF_VERTICAL));

    private PowerRadarPanelAttachmentTypes() {
    }

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }

    // CEE сравнивает ItemLike при наведении. Ленивое разрешение не запоминает AIR,
    // если реестр Power Radar обрабатывается раньше предметов необязательного Simulated.
    private static ItemLike registryItem(ResourceLocation itemId) {
        return () -> BuiltInRegistries.ITEM.get(itemId);
    }
}
