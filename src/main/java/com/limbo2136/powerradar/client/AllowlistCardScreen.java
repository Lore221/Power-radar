package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.network.AllowlistCardOpenPayload;
import com.limbo2136.powerradar.network.AllowlistCardSavePayload;
import com.limbo2136.powerradar.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class AllowlistCardScreen extends Screen {
    private static final ResourceLocation CARDS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/radar_ui/cards.png");
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath(
            PowerRadar.MOD_ID, "textures/gui/radar_ui/icons.png");
    private static final int TEXTURE_SIZE = 256;
    private static final int GUI_WIDTH = 240;
    private static final int GUI_HEIGHT = 86;
    private static final int TOTAL_WIDTH = 272;
    private static final int TOP_SOURCE_Y = 98;

    private final AllowlistCardOpenPayload snapshot;
    private final List<String> onlinePlayers;
    private final ArrayList<String> storedNames;
    private boolean sableMode;
    private int option;
    private int playerIndex;
    private PressedButton pressed = PressedButton.none();
    private boolean stateSaved;
    private int left;
    private int top;

    public AllowlistCardScreen(AllowlistCardOpenPayload snapshot) {
        super(Component.translatable("screen.power_radar.allowlist_card"));
        this.snapshot = snapshot;
        this.sableMode = snapshot.sableMode();
        this.option = snapshot.option() == 0 ? 0 : 1;
        this.onlinePlayers = List.copyOf(snapshot.onlinePlayers());
        this.storedNames = new ArrayList<>(snapshot.storedNames());
        this.playerIndex = this.onlinePlayers.isEmpty() ? -1 : 0;
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
                0.0F, (float) TOP_SOURCE_Y, GUI_WIDTH, GUI_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
        int titleX = this.left + (GUI_WIDTH - 8) / 2 - this.font.width(this.title) / 2;
        graphics.drawString(this.font, this.title, titleX, this.top + 4, 0x592424, false);

        renderSableButton(graphics, mouseX, mouseY);
        renderCandidate(graphics);
        renderListActionButton(graphics, ButtonType.ADD, 182, 224, 16, mouseX, mouseY);
        renderListActionButton(graphics, ButtonType.REMOVE, 200, 240, 16, mouseX, mouseY);
        renderModeButton(graphics, 0, 38, mouseX, mouseY);
        renderModeButton(graphics, 1, 56, mouseX, mouseY);
        renderSaveButton(graphics, mouseX, mouseY);
        graphics.renderItem(new ItemStack(Items.NAME_TAG), this.left + 16, this.top + 63);

        ItemStack card = new ItemStack(ModItems.ALLOWLIST_CARD.get());
        var cardRender = GuiGameElement.of(card);
        cardRender.at(this.left + GUI_WIDTH + 8, this.top + GUI_HEIGHT - 52, -200.0F);
        cardRender.scale(4.0D).render(graphics);

        renderHoveredTooltip(graphics, mouseX, mouseY);
    }

    private void renderSableButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.left + 15;
        int y = this.top + 27;
        boolean hovered = contains(mouseX, mouseY, x, y, 18, 18);
        boolean isPressed = this.pressed.type == ButtonType.SABLE;
        int sourceX = isPressed ? 156 : hovered ? 138 : this.sableMode ? 174 : 120;
        blitSmallBackground(graphics, x, y, sourceX);
        graphics.blit(ICONS, x + 1, y + 1, 16, 16, 224.0F, 0.0F,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderCandidate(GuiGraphics graphics) {
        String candidate = candidateName();
        if (candidate == null) return;
        String fitted = this.font.plainSubstrByWidth(candidate, 131);
        graphics.drawString(this.font, fitted, this.left + 42, this.top + 32, 0x404040, false);
    }

    private void renderListActionButton(GuiGraphics graphics, ButtonType type, int relativeX,
                                        int iconU, int iconV, int mouseX, int mouseY) {
        int x = this.left + relativeX;
        int y = this.top + 27;
        boolean active = type == ButtonType.ADD ? canAddCandidate() : canRemoveCandidate();
        boolean hovered = active && contains(mouseX, mouseY, x, y, 18, 18);
        boolean isPressed = active && this.pressed.type == type;
        int sourceX = active ? isPressed ? 156 : hovered ? 138 : 120 : 192;
        blitSmallBackground(graphics, x, y, sourceX);
        graphics.blit(ICONS, x + 1, y + 1, 16, 16, (float) iconU, (float) iconV,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderModeButton(GuiGraphics graphics, int index, int relativeX, int mouseX, int mouseY) {
        int x = this.left + relativeX;
        int y = this.top + 62;
        boolean hovered = contains(mouseX, mouseY, x, y, 18, 18);
        boolean isPressed = this.pressed.type == ButtonType.MODE && this.pressed.index == index;
        int sourceX = isPressed ? 156 : hovered ? 138 : this.option == index ? 174 : 120;
        blitSmallBackground(graphics, x, y, sourceX);
        int iconU = index == 0 ? 224 : 240;
        graphics.blit(ICONS, x + 1, y + 1, 16, 16, (float) iconU, 32.0F,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderSaveButton(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.left + 208;
        int y = this.top + 62;
        int sourceX = contains(mouseX, mouseY, x, y, 18, 18) ? 138 : 120;
        blitSmallBackground(graphics, x, y, sourceX);
        graphics.blit(ICONS, x + 1, y + 1, 16, 16, 240.0F, 0.0F,
                16, 16, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private static void blitSmallBackground(GuiGraphics graphics, int x, int y, int sourceX) {
        graphics.blit(ICONS, x, y, 18, 18, (float) sourceX, 0.0F,
                18, 18, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderHoveredTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (contains(mouseX, mouseY, this.left + 15, this.top + 27, 18, 18)) {
            renderSimpleTooltip(graphics, mouseX, mouseY, "screen.power_radar.allowlist_card.sable");
        } else if (contains(mouseX, mouseY, this.left + 182, this.top + 27, 18, 18)) {
            renderSimpleTooltip(graphics, mouseX, mouseY, "screen.power_radar.allowlist_card.add");
        } else if (contains(mouseX, mouseY, this.left + 200, this.top + 27, 18, 18)) {
            renderSimpleTooltip(graphics, mouseX, mouseY, "screen.power_radar.allowlist_card.remove");
        } else if (contains(mouseX, mouseY, this.left + 38, this.top + 62, 18, 18)) {
            renderSimpleTooltip(graphics, mouseX, mouseY, "screen.power_radar.targeting_card.blacklist");
        } else if (contains(mouseX, mouseY, this.left + 56, this.top + 62, 18, 18)) {
            renderSimpleTooltip(graphics, mouseX, mouseY, "screen.power_radar.targeting_card.whitelist");
        } else if (contains(mouseX, mouseY, this.left + 15, this.top + 62, 18, 18)) {
            ArrayList<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(this.option == 1
                    ? "screen.power_radar.targeting_card.whitelist"
                    : "screen.power_radar.targeting_card.blacklist").withStyle(ChatFormatting.YELLOW));
            for (String name : this.storedNames) {
                lines.add(Component.literal(name).withStyle(ChatFormatting.GRAY));
            }
            graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
        }
    }

    private void renderSimpleTooltip(GuiGraphics graphics, int mouseX, int mouseY, String key) {
        graphics.renderComponentTooltip(this.font,
                List.of(Component.translatable(key).withStyle(ChatFormatting.WHITE)), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (contains(mouseX, mouseY, this.left + 15, this.top + 27, 18, 18)) {
            playButtonSound();
            this.pressed = new PressedButton(ButtonType.SABLE, -1);
            return true;
        }
        if (canAddCandidate() && contains(mouseX, mouseY, this.left + 182, this.top + 27, 18, 18)) {
            playButtonSound();
            this.pressed = new PressedButton(ButtonType.ADD, -1);
            return true;
        }
        if (canRemoveCandidate() && contains(mouseX, mouseY, this.left + 200, this.top + 27, 18, 18)) {
            playButtonSound();
            this.pressed = new PressedButton(ButtonType.REMOVE, -1);
            return true;
        }
        for (int i = 0; i < 2; i++) {
            if (contains(mouseX, mouseY, this.left + 38 + i * 18, this.top + 62, 18, 18)) {
                playButtonSound();
                this.pressed = new PressedButton(ButtonType.MODE, i);
                return true;
            }
        }
        if (contains(mouseX, mouseY, this.left + 208, this.top + 62, 18, 18)) {
            playButtonSound();
            saveAndClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || this.pressed.type == ButtonType.NONE) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        PressedButton released = this.pressed;
        this.pressed = PressedButton.none();
        switch (released.type) {
            case SABLE -> {
                if (contains(mouseX, mouseY, this.left + 15, this.top + 27, 18, 18)) {
                    this.sableMode = !this.sableMode;
                }
            }
            case ADD -> {
                if (contains(mouseX, mouseY, this.left + 182, this.top + 27, 18, 18)) addCandidate();
            }
            case REMOVE -> {
                if (contains(mouseX, mouseY, this.left + 200, this.top + 27, 18, 18)) removeCandidate();
            }
            case MODE -> {
                int x = 38 + released.index * 18;
                if (contains(mouseX, mouseY, this.left + x, this.top + 62, 18, 18)) this.option = released.index;
            }
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.sableMode && !this.onlinePlayers.isEmpty()
                && contains(mouseX, mouseY, this.left + 39, this.top + 27, 137, 18)) {
            int step = scrollY > 0 ? -1 : 1;
            this.playerIndex = Math.floorMod(this.playerIndex + step, this.onlinePlayers.size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private String candidateName() {
        return this.sableMode || this.playerIndex < 0 || this.playerIndex >= this.onlinePlayers.size()
                ? null : this.onlinePlayers.get(this.playerIndex);
    }

    private void addCandidate() {
        String candidate = candidateName();
        if (candidate != null && this.storedNames.stream().noneMatch(candidate::equalsIgnoreCase)) {
            this.storedNames.add(candidate);
        }
    }

    private boolean canAddCandidate() {
        String candidate = candidateName();
        return candidate != null && this.storedNames.stream().noneMatch(candidate::equalsIgnoreCase);
    }

    private boolean canRemoveCandidate() {
        String candidate = candidateName();
        return candidate != null && this.storedNames.stream().anyMatch(candidate::equalsIgnoreCase);
    }

    private void removeCandidate() {
        String candidate = candidateName();
        if (candidate != null) this.storedNames.removeIf(candidate::equalsIgnoreCase);
    }

    private void saveAndClose() {
        saveState();
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public void onClose() {
        saveState();
        super.onClose();
    }

    private void saveState() {
        if (this.stateSaved) return;
        this.stateSaved = true;
        PacketDistributor.sendToServer(new AllowlistCardSavePayload(
                this.snapshot.hand(), this.sableMode, this.option, List.copyOf(this.storedNames)));
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
        NONE, SABLE, ADD, REMOVE, MODE
    }

    private record PressedButton(ButtonType type, int index) {
        private static PressedButton none() {
            return new PressedButton(ButtonType.NONE, -1);
        }
    }
}
