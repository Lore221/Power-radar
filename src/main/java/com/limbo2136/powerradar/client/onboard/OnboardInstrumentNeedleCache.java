package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/** Клиентская механическая инерция, общая для одинаковых приборов одного Onboard Computer. */
final class OnboardInstrumentNeedleCache {
    private static final double TICKS_PER_SECOND = 20.0D;
    // Критически затухающий отклик проходит около 90% скачка за 0,4 секунды.
    private static final double NATURAL_FREQUENCY_PER_SECOND = 10.0D;
    private static final Map<OnboardComputerBlockEntity, State> STATES = new WeakHashMap<>();

    @Nullable
    private static ClientLevel levelSession;

    private OnboardInstrumentNeedleCache() {
    }

    static NeedleAngles sample(
            OnboardComputerBlockEntity computer,
            float partialTick,
            float altimeterTargetDegrees,
            float speedometerTargetDegrees,
            float accelerometerTargetSourcePixels,
            float variometerTargetSourcePixels
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != levelSession) {
            STATES.clear();
            levelSession = level;
        }
        if (level == null) {
            return new NeedleAngles(
                    altimeterTargetDegrees,
                    speedometerTargetDegrees,
                    accelerometerTargetSourcePixels,
                    variometerTargetSourcePixels);
        }

        double renderTimeTicks = level.getGameTime() + partialTick;
        return STATES.computeIfAbsent(computer, ignored -> new State()).sample(
                renderTimeTicks,
                altimeterTargetDegrees,
                speedometerTargetDegrees,
                accelerometerTargetSourcePixels,
                variometerTargetSourcePixels);
    }

    record NeedleAngles(
            float altimeterDegrees,
            float speedometerDegrees,
            float accelerometerSourcePixels,
            float variometerSourcePixels
    ) {
    }

    private static final class State {
        private final SpringNeedle altimeter = new SpringNeedle();
        private final SpringNeedle speedometer = new SpringNeedle();
        private final SpringNeedle accelerometer = new SpringNeedle();
        private final SpringNeedle variometer = new SpringNeedle();
        private double lastRenderTimeTicks = Double.NaN;

        private NeedleAngles sample(
                double renderTimeTicks,
                float altimeterTargetDegrees,
                float speedometerTargetDegrees,
                float accelerometerTargetSourcePixels,
                float variometerTargetSourcePixels
        ) {
            if (!Double.isFinite(this.lastRenderTimeTicks) || renderTimeTicks < this.lastRenderTimeTicks) {
                this.altimeter.reset(altimeterTargetDegrees);
                this.speedometer.reset(speedometerTargetDegrees);
                this.accelerometer.reset(accelerometerTargetSourcePixels);
                this.variometer.reset(variometerTargetSourcePixels);
            } else {
                double deltaSeconds = (renderTimeTicks - this.lastRenderTimeTicks) / TICKS_PER_SECOND;
                this.altimeter.advance(altimeterTargetDegrees, deltaSeconds);
                this.speedometer.advance(speedometerTargetDegrees, deltaSeconds);
                this.accelerometer.advance(accelerometerTargetSourcePixels, deltaSeconds);
                this.variometer.advance(variometerTargetSourcePixels, deltaSeconds);
            }
            this.lastRenderTimeTicks = renderTimeTicks;
            return new NeedleAngles(
                    (float) this.altimeter.position,
                    (float) this.speedometer.position,
                    (float) this.accelerometer.position,
                    (float) this.variometer.position);
        }
    }

    private static final class SpringNeedle {
        private double position;
        private double velocityPerSecond;

        private void reset(double targetDegrees) {
            this.position = targetDegrees;
            this.velocityPerSecond = 0.0D;
        }

        private void advance(double targetDegrees, double deltaSeconds) {
            if (deltaSeconds <= 0.0D) {
                return;
            }

            // Точное решение критически затухающей пружины для постоянной цели на этом
            // интервале отрисовки остаётся устойчивым даже при низкой частоте кадров.
            double displacement = this.position - targetDegrees;
            double coefficient = this.velocityPerSecond
                    + NATURAL_FREQUENCY_PER_SECOND * displacement;
            double decay = Math.exp(-NATURAL_FREQUENCY_PER_SECOND * deltaSeconds);
            this.position = targetDegrees + (displacement + coefficient * deltaSeconds) * decay;
            this.velocityPerSecond = (this.velocityPerSecond
                    - NATURAL_FREQUENCY_PER_SECOND * coefficient * deltaSeconds) * decay;
        }
    }
}
