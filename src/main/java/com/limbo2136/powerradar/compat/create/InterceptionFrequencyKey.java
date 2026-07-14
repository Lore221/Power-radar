package com.limbo2136.powerradar.compat.create;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.createmod.catnip.data.Couple;

public final class InterceptionFrequencyKey {
    private static final Map<Key, UUID> CHANNELS = new HashMap<>();

    private InterceptionFrequencyKey() {
    }

    @Nullable
    public static synchronized UUID from(LinkBehaviour link) {
        if (link == null) {
            return null;
        }
        Couple<Frequency> key = link.getNetworkKey();
        Frequency first = key.get(true);
        Frequency second = key.get(false);
        return CHANNELS.computeIfAbsent(new Key(first, second), ignored -> UUID.randomUUID());
    }

    private record Key(Frequency first, Frequency second) {
    }
}
