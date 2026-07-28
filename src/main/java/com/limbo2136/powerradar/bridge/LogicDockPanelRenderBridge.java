package com.limbo2136.powerradar.bridge;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.panel.LogicDockPanelAttachment;

/** Нейтральный по стороне шлюз к клиентской отрисовке панельного Logic Dock. */
public final class LogicDockPanelRenderBridge {
    private static Handler handler = Handler.NO_OP;

    private LogicDockPanelRenderBridge() {
    }

    public static void setHandler(Handler handler) {
        LogicDockPanelRenderBridge.handler = handler;
    }

    public static void render(
            LogicDockPanelAttachment attachment,
            ElectricalPanelBlockEntity panel,
            Object poseStack,
            Object buffers,
            int packedLight,
            int packedOverlay
    ) {
        handler.render(attachment, panel, poseStack, buffers, packedLight, packedOverlay);
    }

    public interface Handler {
        Handler NO_OP = (attachment, panel, poseStack, buffers, packedLight, packedOverlay) -> {
        };

        void render(
                LogicDockPanelAttachment attachment,
                ElectricalPanelBlockEntity panel,
                Object poseStack,
                Object buffers,
                int packedLight,
                int packedOverlay
        );
    }
}
