package com.limbo2136.powerradar.block;

import com.limbo2136.powerradar.radar.RadarScanMode;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;

public class AirRadarControllerBlock extends RadarControllerBlock {
    public static final MapCodec<AirRadarControllerBlock> CODEC = simpleCodec(AirRadarControllerBlock::new);

    public AirRadarControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RadarScanMode scanMode() {
        return RadarScanMode.SKY;
    }
}
