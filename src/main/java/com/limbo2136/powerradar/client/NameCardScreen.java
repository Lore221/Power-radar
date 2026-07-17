package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.network.NameCardOpenPayload;
import com.limbo2136.powerradar.network.NameCardSavePayload;
import com.limbo2136.powerradar.registry.ModItems;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.Util;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NameCardScreen extends Screen {
    private static final ResourceLocation CARDS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/radar_ui/cards.png");
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/radar_ui/icons.png");
    private static final int GUI_WIDTH = 213;
    private static final int GUI_HEIGHT = 43;
    private static final int TEXTURE_SIZE = 256;
    private final NameCardOpenPayload snapshot;
    private EditBox nameBox;
    private int left;
    private int top;
    private boolean saved;

    public NameCardScreen(NameCardOpenPayload snapshot) {
        super(Component.translatable("screen.power_radar.name_card"));
        this.snapshot = snapshot;
    }

    @Override protected void init() {
        this.left = (this.width - 245) / 2;
        this.top = (this.height - GUI_HEIGHT) / 2;
        this.nameBox = new EditBox(this.font, this.left + 14, this.top + 24, 150, 9,
                Component.translatable("screen.power_radar.name_card.name"));
        this.nameBox.setBordered(false);
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(this.snapshot.name());
        this.nameBox.setTextColor(0x404040);
        addWidget(this.nameBox);
        setInitialFocus(this.nameBox);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override protected void renderBlurredBackground(float partialTick) { }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(CARDS, this.left, this.top, GUI_WIDTH, GUI_HEIGHT,
                0.0F, 184.0F, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        int titleX = this.left + (GUI_WIDTH - 8) / 2 - this.font.width(this.title) / 2;
        graphics.drawString(this.font, this.title, titleX, this.top + 4, 0x592424, false);
        renderName(graphics);
        renderSaveButton(graphics, mouseX, mouseY);
        var card = GuiGameElement.of(new ItemStack(ModItems.NAME_CARD.get()));
        card.at(this.left + GUI_WIDTH + 8, this.top + GUI_HEIGHT - 52, -200.0F);
        card.scale(4.0D).render(graphics);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY, this.left + 181, this.top + 20, 18, 18)) {
            playButtonSound();
            saveAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void onClose() {
        save();
        super.onClose();
    }

    private void saveAndClose() {
        save();
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    private void save() {
        if (this.saved) return;
        this.saved = true;
        PacketDistributor.sendToServer(new NameCardSavePayload(this.snapshot.hand(), this.nameBox.getValue()));
    }

    private void renderName(GuiGraphics graphics) {
        String value = this.nameBox.getValue();
        int cursor = Math.max(0, Math.min(this.nameBox.getCursorPosition(), value.length()));
        String beforeCursor = value.substring(0, cursor);
        String visiblePrefix = this.font.plainSubstrByWidth(beforeCursor, 146, true);
        int start = cursor - visiblePrefix.length();
        String visible = this.font.plainSubstrByWidth(value.substring(start), 150);
        int x = this.left + 14;
        int y = this.top + 24;
        graphics.enableScissor(x, y, x + 150, y + 9);
        graphics.drawString(this.font, visible, x, y, 0x404040, false);
        if (this.nameBox.isFocused() && (Util.getMillis() / 300L & 1L) == 0L) {
            int cursorX = x + this.font.width(value.substring(start, cursor));
            graphics.fill(cursorX, y, cursorX + 1, y + 9, 0xFF404040);
        }
        graphics.disableScissor();
    }

    private void renderSaveButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.left + 181;
        int y = this.top + 20;
        int sourceX = contains(mouseX, mouseY, x, y, 18, 18) ? 138 : 120;
        graphics.blit(ICONS, x, y, 18, 18, (float) sourceX, 0.0F,
                18, 18, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.blit(ICONS, x + 1, y + 1, 16, 16, 240.0F, 0.0F,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void playButtonSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
