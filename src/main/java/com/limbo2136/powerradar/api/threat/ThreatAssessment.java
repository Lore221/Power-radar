package com.limbo2136.powerradar.api.threat;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/** Результат оценки угрозы; точки пересечения отсутствуют, когда соответствующая плоскость не достигнута. */
public record ThreatAssessment(
        @Nullable UUID targetUuid,
        boolean dangerous,
        String reason,
        @Nullable Vec3 upperCrossing,
        @Nullable Vec3 lowerCrossing
) {
}
