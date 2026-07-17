package com.limbo2136.powerradar.interception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterceptionThreatPublicationTest {
    private static final UUID RETAINED = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WITHDRAWN = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void authoritativePublicationImmediatelyWithdrawsMissingThreats() {
        Map<UUID, String> threats = new HashMap<>();
        threats.put(RETAINED, "retained");
        threats.put(WITHDRAWN, "withdrawn");

        assertTrue(InterceptionCoordinator.retainOnlyPublishedThreats(threats, Set.of(RETAINED)));
        assertEquals(Map.of(RETAINED, "retained"), threats);
    }

    @Test
    void unchangedPublicationDoesNotReportARevisionChange() {
        Map<UUID, String> threats = new HashMap<>();
        threats.put(RETAINED, "retained");

        assertFalse(InterceptionCoordinator.retainOnlyPublishedThreats(threats, Set.of(RETAINED)));
        assertEquals(Map.of(RETAINED, "retained"), threats);
    }
}
