package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.entity.RadarStructureEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public final class RadarStructureEntityRenderer extends EntityRenderer<RadarStructureEntity> {
    public RadarStructureEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RadarStructureEntity entity) {
        // Маркер намеренно невидим; рендерер нужен лишь для регистрации клиентского типа сущности.
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
