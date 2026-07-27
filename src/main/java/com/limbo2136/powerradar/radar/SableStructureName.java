package com.limbo2136.powerradar.radar;

import javax.annotation.Nullable;

public final class SableStructureName {
    public static final String DEFAULT_PLACEHOLDER = "Sable Structure";

    private SableStructureName() {
    }

    // Стандартная подпись Sable означает отсутствие имени и не участвует в allowlist.
    @Nullable
    public static String normalize(@Nullable String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim();
        return name.isEmpty() || DEFAULT_PLACEHOLDER.equalsIgnoreCase(name) ? null : name;
    }
}
