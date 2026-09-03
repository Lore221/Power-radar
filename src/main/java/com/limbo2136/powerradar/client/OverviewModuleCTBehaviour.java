package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.PowerRadar;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Подключает вертикальные варианты текстур Overview Module к его единой OBJ-модели. */
public final class OverviewModuleCTBehaviour extends ConnectedTextureBehaviour.Base {
    private static final CTSpriteShiftEntry FRONT = vertical(
            "overview_module/overview_module_front", "overview_module/overview_module_front_connected", 31);
    private static final CTSpriteShiftEntry BACK = vertical(
            "overview_module/overview_module_back", "overview_module/overview_module_back_connected", 31);
    private static final CTSpriteShiftEntry SIDE = vertical(
            "overview_module/overview_module_side", "overview_module/overview_module_side_connected", 5);

    @Override
    @Nullable
    public CTSpriteShiftEntry getShift(
            BlockState state,
            Direction direction,
            @Nullable TextureAtlasSprite sprite
    ) {
        if (sprite != null) {
            if (FRONT.getOriginal() == sprite) {
                return FRONT;
            }
            if (BACK.getOriginal() == sprite) {
                return BACK;
            }
            if (SIDE.getOriginal() == sprite) {
                return SIDE;
            }
            return null;
        }

        // Все рабочие поверхности OBJ вертикальны; при построении контекста
        // достаточно вернуть любой из трёх сдвигов с тем же типом VERTICAL.
        return direction.getAxis().isVertical() ? null : FRONT;
    }

    private static CTSpriteShiftEntry vertical(String original, String connected, int contentWidth) {
        return new VerticalSpriteShift(
                PowerRadar.id("block/" + original),
                PowerRadar.id("block/" + connected),
                contentWidth);
    }

    private static final class VerticalSpriteShift extends CTSpriteShiftEntry {
        private static final float ATLAS_SIZE = 64.0f;
        private static final float ROW_HEIGHT = 16.0f / ATLAS_SIZE;
        private final float contentWidth;

        private VerticalSpriteShift(
                ResourceLocation original,
                ResourceLocation connected,
                int contentWidth
        ) {
            super(AllCTTypes.VERTICAL);
            this.contentWidth = contentWidth / ATLAS_SIZE;
            set(original, connected);
        }

        @Override
        public float getTargetU(float localU, int index) {
            float sourceU = getUnInterpolatedU(getOriginal(), localU);
            return getTarget().getU(sourceU * this.contentWidth);
        }

        @Override
        public float getTargetV(float localV, int index) {
            float sourceV = getUnInterpolatedV(getOriginal(), localV);
            return getTarget().getV((sourceV + atlasRow(index)) * ROW_HEIGHT);
        }

        private static int atlasRow(int index) {
            return switch (index) {
                case 1 -> 3; // Только сосед сверху: нижний модуль.
                case 2 -> 1; // Только сосед снизу: верхний модуль.
                case 3 -> 2; // Соседи сверху и снизу: центральный модуль.
                default -> 0; // Без соседей: одиночный модуль.
            };
        }
    }
}
