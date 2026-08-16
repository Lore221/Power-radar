package com.limbo2136.powerradar.radar.network;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Читает сетевые endpoint-ы только из уже готовых FULL-чанков. */
final class LoadedRadarWorldAccess {
    private LoadedRadarWorldAccess() {
    }

    @Nullable
    static BlockEntity blockEntity(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }

    @Nullable
    static BlockState blockState(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = chunk(level, pos);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    private static LevelChunk chunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
