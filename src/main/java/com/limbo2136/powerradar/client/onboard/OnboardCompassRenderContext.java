package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.radar.RadarGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.phys.Vec3;

/** Хранит общие направления, используемые всеми компасами одного Onboard Computer. */
record OnboardCompassRenderContext(
        ClientLevel level,
        Vec3 compassPosition,
        float worldYawAtZeroNeedleRotation,
        float spawnRotation,
        float recoveryRotation,
        float invalidRotation
) {
    static OnboardCompassRenderContext create(
            OnboardComputerBlockEntity computer,
            ClientLevel level,
            Vec3 compassPosition,
            float worldYawAtZeroNeedleRotation,
            float partialTick
    ) {
        float invalidRotation = invalidCompassRotation(computer, level.getGameTime(), partialTick);
        float spawnRotation = targetRotation(
                level,
                compassPosition,
                worldYawAtZeroNeedleRotation,
                CompassItem.getSpawnPosition(level),
                invalidRotation);
        LocalPlayer player = Minecraft.getInstance().player;
        float recoveryRotation = targetRotation(
                level,
                compassPosition,
                worldYawAtZeroNeedleRotation,
                player == null ? null : player.getLastDeathLocation().orElse(null),
                invalidRotation);
        return new OnboardCompassRenderContext(
                level,
                compassPosition,
                worldYawAtZeroNeedleRotation,
                spawnRotation,
                recoveryRotation,
                invalidRotation);
    }

    static OnboardCompassRenderContext unavailable() {
        return new OnboardCompassRenderContext(null, Vec3.ZERO, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    // Выбирает цель по сохранённым компонентам установленного предмета.
    float needleRotation(ItemStack compass) {
        if (this.level == null) {
            return 0.0F;
        }
        if (compass.is(Items.RECOVERY_COMPASS)) {
            return this.recoveryRotation;
        }

        LodestoneTracker lodestone = compass.get(DataComponents.LODESTONE_TRACKER);
        if (lodestone == null) {
            return this.spawnRotation;
        }
        return targetRotation(
                this.level,
                this.compassPosition,
                this.worldYawAtZeroNeedleRotation,
                lodestone.target().orElse(null),
                this.invalidRotation);
    }

    // Переводит мировое направление на цель в локальный поворот стрелки.
    private static float targetRotation(
            ClientLevel level,
            Vec3 compassPosition,
            float worldYawAtZeroNeedleRotation,
            GlobalPos targetPosition,
            float invalidRotation
    ) {
        if (targetPosition == null || !targetPosition.dimension().equals(level.dimension())) {
            return invalidRotation;
        }

        Vec3 target = Vec3.atCenterOf(targetPosition.pos());
        double deltaX = target.x - compassPosition.x;
        double deltaZ = target.z - compassPosition.z;
        if (deltaX * deltaX + deltaZ * deltaZ < 1.0E-5D) {
            return invalidRotation;
        }
        float targetYaw = RadarGeometry.normalizeDegrees(
                (float) Math.toDegrees(Math.atan2(deltaX, -deltaZ)));
        return Mth.wrapDegrees(worldYawAtZeroNeedleRotation - targetYaw);
    }

    // Разводит фазы вращения сломанных компасов по координатам блока.
    private static float invalidCompassRotation(
            OnboardComputerBlockEntity computer,
            long gameTime,
            float partialTick
    ) {
        int positionHash = computer.getBlockPos().hashCode() * 1327217883;
        float offset = (positionHash & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        return Mth.wrapDegrees((gameTime + partialTick) * 18.0F + offset * 360.0F);
    }
}
