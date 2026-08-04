package com.limbo2136.powerradar.item;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.minecraft.world.item.ItemStack;

public final class IncompleteOverviewModuleItem extends SequencedAssemblyItem {
    private static final float SECOND_STAGE_PROGRESS = 1.0F / 3.0F;
    private static final float THIRD_STAGE_PROGRESS = 2.0F / 3.0F;

    public IncompleteOverviewModuleItem(Properties properties) {
        super(properties);
    }

    public float modelStage(ItemStack stack) {
        return stageForProgress(getProgress(stack));
    }

    static int stageForProgress(float progress) {
        if (progress >= THIRD_STAGE_PROGRESS) {
            return 3;
        }
        if (progress >= SECOND_STAGE_PROGRESS) {
            return 2;
        }
        return 1;
    }
}
