package com.limbo2136.powerradar.item;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadarFilterCardItemTest {
    @Test
    void legacySableEntriesMigrateFromUuidToNameQueries() {
        RadarFilterCardItem.AllowlistData data = RadarFilterCardItem.decodeAllowlistLines(List.of(
                "S\t8f5d6f82-6580-4283-bd67-70c10f9b89d2\tAurora",
                "Q\taurora",
                "Q\tBorealis"), true);

        assertEquals(List.of("Aurora", "Borealis"), data.sableNames());
        assertEquals(List.of("Q\tAurora", "Q\tBorealis"), data.encodedLines());
    }

    @Test
    void legacyUnprefixedSableNamesRemainNameQueries() {
        RadarFilterCardItem.AllowlistData data = RadarFilterCardItem.decodeAllowlistLines(
                List.of("Old Ship"), true);

        assertEquals(List.of("Old Ship"), data.sableNames());
        assertEquals(List.of("Q\tOld Ship"), data.encodedLines());
    }
}
