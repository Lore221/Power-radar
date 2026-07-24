package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** Клиентский кэш непрерывной ветви углов для циклической текстуры авиагоризонта. */
final class OnboardAttitudeStripCache {
    private static final Map<OnboardComputerBlockEntity, State> STATES = new WeakHashMap<>();

    @Nullable
    private static ClientLevel levelSession;

    private OnboardAttitudeStripCache() {
    }

    static OnboardAttitudeIndicatorRenderer.StripTransform sample(
            OnboardComputerBlockEntity computer,
            Vector3f moduleWorldUp
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != levelSession) {
            // Сравнение объекта по ссылке очищает ветви углов и при входе в то же измерение заново.
            STATES.clear();
            levelSession = level;
        }
        return STATES.computeIfAbsent(computer, ignored -> new State()).sample(moduleWorldUp);
    }

    private static final class State {
        private float bankDegrees;
        private float pitchDegrees;
        private boolean initialized;

        private OnboardAttitudeIndicatorRenderer.StripTransform sample(Vector3f worldUp) {
            // Один вектор мирового верха имеет два равнозначных разложения на крен и тангаж.
            // Выбор ближайшей ветви предотвращает скачок при вертикальном участке мёртвой петли.
            float standardBank = (float) Math.toDegrees(Math.atan2(worldUp.x, worldUp.y));
            float standardPitch = (float) Math.toDegrees(Math.atan2(
                    worldUp.z,
                    Math.sqrt(worldUp.x * worldUp.x + worldUp.y * worldUp.y)));
            float alternateBank = standardBank + 180.0F;
            float alternatePitch = standardPitch >= 0.0F
                    ? 180.0F - standardPitch
                    : -180.0F - standardPitch;

            if (!this.initialized) {
                this.bankDegrees = standardBank;
                this.pitchDegrees = standardPitch;
                this.initialized = true;
            } else {
                standardBank = unwrapNear(standardBank, this.bankDegrees);
                standardPitch = unwrapNear(standardPitch, this.pitchDegrees);
                alternateBank = unwrapNear(alternateBank, this.bankDegrees);
                alternatePitch = unwrapNear(alternatePitch, this.pitchDegrees);
                float standardDistance = squaredDistance(
                        standardBank, standardPitch, this.bankDegrees, this.pitchDegrees);
                float alternateDistance = squaredDistance(
                        alternateBank, alternatePitch, this.bankDegrees, this.pitchDegrees);
                if (alternateDistance < standardDistance) {
                    this.bankDegrees = alternateBank;
                    this.pitchDegrees = alternatePitch;
                } else {
                    this.bankDegrees = standardBank;
                    this.pitchDegrees = standardPitch;
                }
            }
            return new OnboardAttitudeIndicatorRenderer.StripTransform(
                    this.bankDegrees,
                    this.pitchDegrees);
        }

        private static float unwrapNear(float angleDegrees, float referenceDegrees) {
            return angleDegrees + 360.0F * Math.round((referenceDegrees - angleDegrees) / 360.0F);
        }

        private static float squaredDistance(float bank, float pitch, float previousBank, float previousPitch) {
            float bankDelta = bank - previousBank;
            float pitchDelta = pitch - previousPitch;
            return bankDelta * bankDelta + pitchDelta * pitchDelta;
        }
    }
}
