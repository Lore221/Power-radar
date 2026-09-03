package com.limbo2136.powerradar.compat.create;

import com.limbo2136.powerradar.block.RadarPanelBlock;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import java.util.Collection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Stationary radar aperture used by the controller's Create contraption.
 *
 * <p>The controller itself stays in the world as the electrical and network
 * anchor.  Only the validated panel/module blocks are captured, so the
 * aperture gets Create's entity collision, rendering and disassembly path
 * without moving the controller or its CEE connection.</p>
 */
public final class RadarPanelContraption extends BearingContraption {
    private final Direction panelFacing;

    public RadarPanelContraption(Direction panelFacing) {
        // BearingContraption is the same Create contraption type used by a
        // mechanical bearing.  Its facing also records the horizontal axis
        // around which this stationary aperture is tilted.
        super(false, panelFacing.getClockWise());
        this.panelFacing = panelFacing;
    }

    /**
     * Captures exactly the positions already accepted by the radar validator.
     */
    public boolean assembleRadar(Level level, BlockPos controllerPos, Collection<BlockPos> positions)
            throws AssemblyException {
        this.anchor = controllerPos.above();
        this.bounds = new AABB(BlockPos.ZERO);
        BlockPos firstPanelPos = controllerPos.above();

        for (BlockPos position : positions) {
            if (!RadarPanelBlock.isInPanelPlane(firstPanelPos, position, panelFacing)
                    || !level.isLoaded(position)
                    || !isRadarPart(level.getBlockState(position))) {
                return false;
            }
            addBlock(level, position, capture(level, position));
        }

        if (getBlocks().isEmpty()) {
            return false;
        }

        // Build the structural bounds only after every panel accepted by the
        // plane traversal has been captured.  The bounds must describe the
        // contraption contents; it must never be used as a discovery volume.
        buildCapturedBounds();
        startMoving(level);
        // The aperture pitches around its horizontal width axis.  Expanding
        // around that axis keeps Create's entity hitbox conservative while the
        // panels are tilted during activation.
        expandBoundsAroundAxis(panelFacing.getClockWise().getAxis());
        return true;
    }

    private void buildCapturedBounds() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos localPos : getBlocks().keySet()) {
            minX = Math.min(minX, localPos.getX());
            minY = Math.min(minY, localPos.getY());
            minZ = Math.min(minZ, localPos.getZ());
            maxX = Math.max(maxX, localPos.getX() + 1);
            maxY = Math.max(maxY, localPos.getY() + 1);
            maxZ = Math.max(maxZ, localPos.getZ() + 1);
        }

        this.bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isRadarPart(BlockState state) {
        return state.is(ModBlocks.RADAR_PANEL.get())
                || state.is(ModBlocks.OVERVIEW_MODULE.get());
    }
}
