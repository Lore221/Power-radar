package com.limbo2136.powerradar.client;

import com.limbo2136.powerradar.block.AircraftRadarBlock;
import com.limbo2136.powerradar.block.AircraftRadarPartBlock;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Keeps the three-block aircraft radar visually represented by one model while
 * giving every physical block its own full-block destroy overlay.
 */
final class AircraftRadarBakedModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel breakingModel;

    AircraftRadarBakedModel(BakedModel originalModel, BakedModel breakingModel) {
        super(originalModel);
        this.breakingModel = breakingModel;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState state,
            @Nullable Direction side,
            RandomSource rand,
            ModelData modelData,
            @Nullable RenderType renderType
    ) {
        // BlockRenderDispatcher uses a null render type for the destroy
        // overlay. A full cube is intentional here: the overlay belongs to
        // the one physical block being mined, not to the long visual model.
        if (renderType == null && state != null) {
            return breakingModel.getQuads(state, side, rand, modelData, null);
        }

        // The item model has no block state and must keep the normal item
        // geometry and transforms.
        if (state == null) {
            return super.getQuads(state, side, rand, modelData, renderType);
        }

        // The core and the front companion provide collision/electrical
        // footprint only. The middle companion remains the sole normal model.
        if (state.getBlock() instanceof AircraftRadarBlock
                || (state.getBlock() instanceof AircraftRadarPartBlock
                && !state.getValue(AircraftRadarPartBlock.MODEL_VISIBLE))) {
            return List.of();
        }
        return super.getQuads(state, side, rand, modelData, renderType);
    }
}
