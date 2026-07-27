package com.limbo2136.powerradar.client.instrument;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Общий клиентский расчёт непрерывных углов механического авиагоризонта. */
public final class AttitudeIndicatorAngleCache {
    private static final Map<Object, State> STATES = new WeakHashMap<>();

    @Nullable
    private static ClientLevel levelSession;

    private AttitudeIndicatorAngleCache() {
    }

    /**
     * Переводит мировой верх из локальных осей Sable в оси лицевой модели.
     * Владелец служит слабым ключом, поэтому несколько приборов не делят ветвь углов.
     */
    public static Transform sample(Object owner, Direction facing, Vec3 localWorldUp) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != levelSession) {
            // Смена объекта мира очищает кэш даже при повторном входе в то же измерение.
            STATES.clear();
            levelSession = level;
        }
        Vector3f modelWorldUp = toModelAxes(facing, localWorldUp);
        return STATES.computeIfAbsent(owner, ignored -> new State()).sample(modelWorldUp);
    }

    private static Vector3f toModelAxes(Direction facing, Vec3 localWorldUp) {
        Vector3f result = new Vector3f(
                (float) localWorldUp.x,
                (float) localWorldUp.y,
                (float) localWorldUp.z);
        if (result.lengthSquared() < 1.0E-6F) {
            result.set(0.0F, 1.0F, 0.0F);
        } else {
            result.normalize();
        }

        // Этот угол совпадает с поворотом CEE transformPose и модулей OnBoard.
        float modelYawDegrees = Mth.wrapDegrees(180.0F - facing.toYRot());
        Quaternionf blockFacingRotation = new Quaternionf()
                .rotateY((float) Math.toRadians(modelYawDegrees));
        blockFacingRotation.conjugate().transform(result).normalize();
        return result;
    }

    private static final class State {
        private float bankDegrees;
        private float pitchDegrees;
        private boolean initialized;

        private Transform sample(Vector3f worldUp) {
            // Один вектор верха имеет две равнозначные пары Эйлеровых углов.
            // Ближайшая к прошлому кадру пара не даёт скачка на вертикали мёртвой петли.
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
            return new Transform(this.bankDegrees, this.pitchDegrees);
        }
    }

    private static float unwrapNear(float angleDegrees, float referenceDegrees) {
        return angleDegrees + 360.0F * Math.round((referenceDegrees - angleDegrees) / 360.0F);
    }

    private static float squaredDistance(float bank, float pitch, float previousBank, float previousPitch) {
        float bankDelta = bank - previousBank;
        float pitchDelta = pitch - previousPitch;
        return bankDelta * bankDelta + pitchDelta * pitchDelta;
    }

    public record Transform(float bankDegrees, float pitchDegrees) {
    }
}
