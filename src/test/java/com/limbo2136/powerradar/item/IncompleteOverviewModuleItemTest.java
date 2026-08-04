package com.limbo2136.powerradar.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IncompleteOverviewModuleItemTest {
    @Test
    void mapsThreeAssemblyLoopsToThreeModelStages() {
        assertEquals(1, IncompleteOverviewModuleItem.stageForProgress(0.0F));
        assertEquals(1, IncompleteOverviewModuleItem.stageForProgress(0.333F));
        assertEquals(2, IncompleteOverviewModuleItem.stageForProgress(1.0F / 3.0F));
        assertEquals(2, IncompleteOverviewModuleItem.stageForProgress(0.666F));
        assertEquals(3, IncompleteOverviewModuleItem.stageForProgress(2.0F / 3.0F));
        assertEquals(3, IncompleteOverviewModuleItem.stageForProgress(1.0F));
    }
}
