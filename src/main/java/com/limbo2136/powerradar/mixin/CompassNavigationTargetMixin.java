package com.limbo2136.powerradar.mixin;

import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.radar.network.SelectedTargetRuntimeSnapshot;
import com.limbo2136.powerradar.registry.ModDataComponents;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Подменяет цель только у привязанного ванильного компаса в Navigation Table.
 * Строковая цель сохраняет необязательность Aeronautics для загрузки Power Radar.
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.navigation_targets.CompassNavigationTarget", remap = false)
public abstract class CompassNavigationTargetMixin {
    @Inject(
            method = "getTarget",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void powerRadar$useSelectedRadarTarget(
            @Coerce Object navigationTable,
            ItemStack compass,
            CallbackInfoReturnable<Vec3> callback
    ) {
        UUID networkId = compass.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        if (networkId == null || !(navigationTable instanceof BlockEntity blockEntity)) {
            return;
        }
        if (!(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }

        SelectedTargetRuntimeSnapshot snapshot =
                RadarNetworkManager.get(level.getServer()).selectedTargetSnapshot(networkId);
        if (!snapshot.alive()
                || snapshot.target() == null
                || !snapshot.target().dimensionId().equals(level.dimension().location())) {
            // Без radar target выполняется исходный метод Aeronautics: как у
            // потерявшего магнетит компаса, Navigation Table выбирает мировой спавн.
            return;
        }
        callback.setReturnValue(snapshot.target().position());
    }
}
