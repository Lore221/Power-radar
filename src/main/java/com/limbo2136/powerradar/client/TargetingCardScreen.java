package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.simibubi.create.foundation.item.TooltipHelper;
import net.createmod.catnip.gui.element.GuiGameElement;
import com.limbo2136.powerradar.network.TargetingCardOpenPayload;
import com.limbo2136.powerradar.network.TargetingCardSavePayload;
import com.limbo2136.powerradar.radar.RadarDetectionFilters;
import com.limbo2136.powerradar.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class TargetingCardScreen extends Screen {
    private static final ResourceLocation CARDS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/cards/cards.png");
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/cards/icons.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int GUI_WIDTH = 213;
    private static final int GUI_HEIGHT = 98;
    private static final int TOTAL_WIDTH = 245;
    private static final int[] CATEGORY_X = {8, 48, 88, 128, 168};
    private static final int[] CATEGORY_BITS = {
            RadarDetectionFilters.PASSIVE_MOBS,
            RadarDetectionFilters.HOSTILE_MOBS,
            RadarDetectionFilters.PLAYERS,
            RadarDetectionFilters.TARGETING_PHANTOMS,
            RadarDetectionFilters.SABLE_STRUCTURES
    };
    private static final int[] CATEGORY_ICON_U = {3, 31, 59, 87, 115};

    private final TargetingCardOpenPayload snapshot;
    private final boolean displayCard;
    private int filterMask;
    private int option;
    private PressedButton pressed = PressedButton.none();
    private boolean stateSaved;
    private int left;
    private int top;

    public TargetingCardScreen(TargetingCardOpenPayload snapshot) {
        super(Component.translatable(snapshot.cardKind() == 1
                ? "screen.power_radar.display_card"
                : "screen.power_radar.targeting_card"));
        this.snapshot = snapshot;
        this.displayCard = snapshot.cardKind() == 1;
        this.filterMask = RadarDetectionFilters.sanitize(snapshot.filterMask());
        this.option = snapshot.option() == 0 ? 0 : 1;
    }

    @Override
    protected void init() {
        this.left = (this.width - TOTAL_WIDTH) / 2;
        this.top = (this.height - GUI_HEIGHT) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(CARDS, this.left, this.top, GUI_WIDTH, GUI_HEIGHT,
                0.0F, 0.0F, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        int titleX = this.left + (GUI_WIDTH - 8) / 2 - this.font.width(this.title) / 2;
        graphics.drawString(this.font, this.title, titleX, this.top + 4, 0x592424, false);

        for (int i = 0; i < CATEGORY_X.length; i++) {
            renderCategoryButton(graphics, i, mouseX, mouseY);
        }
        renderOptionButton(graphics, 0, 15, mouseX, mouseY);
        renderOptionButton(graphics, 1, 33, mouseX, mouseY);
        renderSaveButton(graphics, mouseX, mouseY);

        ItemStack card = new ItemStack(this.displayCard ? ModItems.DISPLAY_CARD.get() : ModItems.TARGETING_CARD.get());
        var cardRender = GuiGameElement.of(card);
        cardRender.at(this.left + GUI_WIDTH + 8, this.top + GUI_HEIGHT - 52, -200.0F);
        cardRender.scale(4.0D).render(graphics);

        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderCategoryButton(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        int x = this.left + CATEGORY_X[index];
        int y = this.top + 28;
        boolean hovered = contains(mouseX, mouseY, x, y, 30, 30);
        boolean isPressed = this.pressed.type == ButtonType.CATEGORY && this.pressed.index == index;
        boolean enabled = (this.filterMask & CATEGORY_BITS[index]) != 0;
        int sourceX = isPressed ? 60 : hovered ? 30 : enabled ? 90 : 0;
        graphics.blit(ICONS, x, y, 30, 30, (float) sourceX, 0.0F,
                30, 30, TEXTURE_SIZE, TEXTURE_SIZE);
        int iconU = this.displayCard && index == 3 ? 143 : CATEGORY_ICON_U[index];
        graphics.blit(ICONS, x + 3, y + 3, 24, 24, (float) iconU, 34.0F,
                24, 24, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderOptionButton(GuiGraphics graphics, int index, int relativeX, int mouseX, int mouseY) {
        int x = this.left + relativeX;
        int y = this.top + 75;
        boolean hovered = contains(mouseX, mouseY, x, y, 18, 18);
        boolean isPressed = this.pressed.type == ButtonType.OPTION && this.pressed.index == index;
        boolean selected = this.option == index;
        int sourceX = isPressed ? 156 : hovered ? 138 : selected ? 174 : 120;
        graphics.blit(ICONS, x, y, 18, 18, (float) sourceX, 0.0F,
                18, 18, TEXTURE_SIZE, TEXTURE_SIZE);
        int iconX = index == 0 ? 224 : 240;
        graphics.blit(ICONS, x + 1, y + 1, 16, 16, (float) iconX, 32.0F,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderSaveButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.left + 181;
        int y = this.top + 75;
        boolean hovered = contains(mouseX, mouseY, x, y, 18, 18);
        int sourceX = hovered ? 138 : 120;
        graphics.blit(ICONS, x, y, 18, 18, (float) sourceX, 0.0F,
                18, 18, TEXTURE_SIZE, TEXTURE_SIZE);
        graphics.blit(ICONS, x + 1, y + 1, 16, 16, 240.0F, 0.0F,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < CATEGORY_X.length; i++) {
            if (contains(mouseX, mouseY, this.left + CATEGORY_X[i], this.top + 28, 30, 30)) {
                renderButtonTooltip(graphics, mouseX, mouseY,
                        screenKey("category." + i),
                        screenKey("category." + i + ".summary"));
                return;
            }
        }
        if (contains(mouseX, mouseY, this.left + 15, this.top + 75, 18, 18)) {
            renderButtonTooltip(graphics, mouseX, mouseY,
                    screenKey("blacklist"),
                    screenKey("blacklist.summary"));
        } else if (contains(mouseX, mouseY, this.left + 33, this.top + 75, 18, 18)) {
            renderButtonTooltip(graphics, mouseX, mouseY,
                    screenKey("whitelist"),
                    screenKey("whitelist.summary"));
        }
    }

    private String screenKey(String suffix) {
        return (this.displayCard
                ? "screen.power_radar.display_card."
                : "screen.power_radar.targeting_card.") + suffix;
    }

    private void renderButtonTooltip(GuiGraphics graphics, int mouseX, int mouseY, String titleKey, String summaryKey) {
        java.util.ArrayList<FormattedCharSequence> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable(titleKey).withStyle(ChatFormatting.WHITE).getVisualOrderText());
        if (Screen.hasShiftDown()) {
            lines.addAll(this.font.split(
                    Component.translatable(summaryKey).withStyle(ChatFormatting.GRAY),
                    TooltipHelper.MAX_WIDTH_PER_LINE));
        } else {
            lines.add(Component.translatable(
                    "screen.power_radar.targeting_card.hold_shift",
                    Component.literal("[Shift]").withStyle(ChatFormatting.GRAY))
                    .withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        }
        graphics.renderTooltip(this.font, lines, DefaultTooltipPositioner.INSTANCE, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (int i = 0; i < CATEGORY_X.length; i++) {
            if (contains(mouseX, mouseY, this.left + CATEGORY_X[i], this.top + 28, 30, 30)) {
                playButtonSound();
                this.pressed = new PressedButton(ButtonType.CATEGORY, i);
                return true;
            }
        }
        if (contains(mouseX, mouseY, this.left + 15, this.top + 75, 18, 18)) {
            playButtonSound();
            this.pressed = new PressedButton(ButtonType.OPTION, 0);
            return true;
        }
        if (contains(mouseX, mouseY, this.left + 33, this.top + 75, 18, 18)) {
            playButtonSound();
            this.pressed = new PressedButton(ButtonType.OPTION, 1);
            return true;
        }
        if (contains(mouseX, mouseY, this.left + 181, this.top + 75, 18, 18)) {
            playButtonSound();
            saveAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.pressed.type == ButtonType.CATEGORY) {
            int index = this.pressed.index;
            if (contains(mouseX, mouseY, this.left + CATEGORY_X[index], this.top + 28, 30, 30)) {
                this.filterMask ^= CATEGORY_BITS[index];
            }
            this.pressed = PressedButton.none();
            return true;
        }
        if (button == 0 && this.pressed.type == ButtonType.OPTION) {
            int index = this.pressed.index;
            int x = index == 0 ? 15 : 33;
            if (contains(mouseX, mouseY, this.left + x, this.top + 75, 18, 18) && this.option != index) {
                this.option = index;
            }
            this.pressed = PressedButton.none();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void saveAndClose() {
        saveState();
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void onClose() {
        saveState();
        super.onClose();
    }

    private void saveState() {
        if (this.stateSaved) {
            return;
        }
        this.stateSaved = true;
        PacketDistributor.sendToServer(new TargetingCardSavePayload(
                this.snapshot.hand(), this.displayCard ? 1 : 0, this.filterMask, this.option));
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

    private enum ButtonType {
        NONE,
        CATEGORY,
        OPTION
    }

    private record PressedButton(ButtonType type, int index) {
        private static PressedButton none() {
            return new PressedButton(ButtonType.NONE, -1);
        }
    }
}
