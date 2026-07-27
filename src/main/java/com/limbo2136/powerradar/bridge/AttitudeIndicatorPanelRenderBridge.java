package com.limbo2136.powerradar.bridge;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.panel.AttitudeIndicatorPanelAttachment;

/** Нейтральный по стороне шлюз к клиентской отрисовке панельного авиагоризонта. */
public final class AttitudeIndicatorPanelRenderBridge {
    private static Handler handler = Handler.NO_OP;

    private AttitudeIndicatorPanelRenderBridge() {
    }

    public static void setHandler(Handler handler) {
        AttitudeIndicatorPanelRenderBridge.handler = handler;
    }

    public static void render(
            AttitudeIndicatorPanelAttachment attachment,
            ElectricalPanelBlockEntity panel,
            float partialTicks,
            Object poseStack,
            Object buffers,
            int packedLight,
            int packedOverlay
    ) {
        handler.render(
                attachment, panel, partialTicks, poseStack, buffers, packedLight, packedOverlay);
    }

    public interface Handler {
        Handler NO_OP = (attachment, panel, partialTicks, poseStack, buffers, packedLight, packedOverlay) -> {
        };

        void render(
                AttitudeIndicatorPanelAttachment attachment,
                ElectricalPanelBlockEntity panel,
                float partialTicks,
                Object poseStack,
                Object buffers,
                int packedLight,
                int packedOverlay
        );
    }
}
