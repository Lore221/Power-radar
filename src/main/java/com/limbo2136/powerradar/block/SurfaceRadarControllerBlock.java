package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.radar.RadarScanMode;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

public class SurfaceRadarControllerBlock extends RadarControllerBlock {
    public static final MapCodec<SurfaceRadarControllerBlock> CODEC = simpleCodec(SurfaceRadarControllerBlock::new);

    public SurfaceRadarControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RadarScanMode scanMode() {
        return RadarScanMode.SURFACE_SCANNER;
    }
}
