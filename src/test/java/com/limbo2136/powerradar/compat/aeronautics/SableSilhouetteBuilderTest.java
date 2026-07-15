package com.limbo2136.powerradar.compat.aeronautics;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SableSilhouetteBuilderTest {
    @Test
    void adjacentColumnsCollapseIntoFourRectangleEdges() {
        Set<Long> occupied = Set.of(
                SableSilhouetteBuilder.pack(0, 0),
                SableSilhouetteBuilder.pack(1, 0));

        SableSilhouetteBuilder.Result result = SableSilhouetteBuilder.build(occupied, 1.0D, 0.5D);

        assertEquals(4, result.lines().size());
        assertTrue(result.lines().contains(new SableSilhouetteLine(-1.0F, -0.5F, 1.0F, -0.5F)));
        assertTrue(result.lines().contains(new SableSilhouetteLine(-1.0F, 0.5F, 1.0F, 0.5F)));
        assertTrue(result.lines().contains(new SableSilhouetteLine(-1.0F, -0.5F, -1.0F, 0.5F)));
        assertTrue(result.lines().contains(new SableSilhouetteLine(1.0F, -0.5F, 1.0F, 0.5F)));
        assertEquals(List.of(new SableSilhouetteFill(-1.0F, -0.5F, 1.0F, 0.5F)), result.fills());
    }

    @Test
    void geometryHashDoesNotDependOnColumnInsertionOrder() {
        Set<Long> first = new LinkedHashSet<>();
        first.add(SableSilhouetteBuilder.pack(-2, 3));
        first.add(SableSilhouetteBuilder.pack(-1, 3));
        first.add(SableSilhouetteBuilder.pack(-1, 4));
        Set<Long> second = new LinkedHashSet<>();
        second.add(SableSilhouetteBuilder.pack(-1, 4));
        second.add(SableSilhouetteBuilder.pack(-1, 3));
        second.add(SableSilhouetteBuilder.pack(-2, 3));

        SableSilhouetteBuilder.Result firstResult = SableSilhouetteBuilder.build(first, -0.5D, 4.0D);
        SableSilhouetteBuilder.Result secondResult = SableSilhouetteBuilder.build(second, -0.5D, 4.0D);

        assertEquals(firstResult.lines(), secondResult.lines());
        assertEquals(firstResult.fills(), secondResult.fills());
        assertEquals(firstResult.geometryHash(), secondResult.geometryHash());
    }
}
