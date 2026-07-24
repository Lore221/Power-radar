package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/** Общая неизменяемая тема экранов карт: атласы, кнопки, звук и предпросмотр предмета. */
final class RadarCardScreenTheme {
    static final ResourceLocation CARDS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/radar_ui/cards.png");
    static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/radar_ui/icons.png");
    static final int TEXTURE_SIZE = 256;

    private static final int BUTTON_NORMAL_U = 120;
    private static final int BUTTON_HOVERED_U = 138;
    private static final int BUTTON_PRESSED_U = 156;
    private static final int BUTTON_SELECTED_U = 174;
    private static final int BUTTON_DISABLED_U = 192;

    private RadarCardScreenTheme() {
    }

    // Порядок состояний соответствует пяти соседним авторским ячейкам 18x18 в icons.png.
    static int smallButtonSourceX(boolean active, boolean selected, boolean hovered, boolean pressed) {
        if (!active) {
            return BUTTON_DISABLED_U;
        }
        if (pressed) {
            return BUTTON_PRESSED_U;
        }
        if (hovered) {
            return BUTTON_HOVERED_U;
        }
        return selected ? BUTTON_SELECTED_U : BUTTON_NORMAL_U;
    }

    static void blitSmallButtonBackground(GuiGraphics graphics, int x, int y, int sourceX) {
        graphics.blit(ICONS, x, y, 18, 18, (float) sourceX, 0.0F,
                18, 18, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    static void renderCardPreview(GuiGraphics graphics, ItemStack card, int x, int y) {
        var cardRender = GuiGameElement.of(card);
        cardRender.at(x, y, -200.0F);
        cardRender.scale(4.0D).render(graphics);
    }

    static void playButtonSound(Minecraft minecraft) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
