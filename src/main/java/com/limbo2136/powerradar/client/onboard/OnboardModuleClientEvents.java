package com.limbo2136.powerradar.client.onboard;

import com.limbo2136.powerradar.PowerRadar;
import com.limbo2136.powerradar.block.OnboardComputerBlock;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.onboard.OnboardModuleSlot;
import com.limbo2136.powerradar.onboard.OnboardModuleType;
import com.limbo2136.powerradar.onboard.OnboardPanelGeometry;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = PowerRadar.MOD_ID, value = Dist.CLIENT)
public final class OnboardModuleClientEvents {
    private static final int OUTLINE_COLOR = 0xFFFFFF;

    private OnboardModuleClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }
        ItemStack held = OnboardModuleType.accepts(player.getMainHandItem())
                ? player.getMainHandItem()
                : player.getOffhandItem();
        if (!OnboardModuleType.accepts(held)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        if (!(state.getBlock() instanceof OnboardComputerBlock)
                || !(minecraft.level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer)) {
            return;
        }
        OnboardModuleSlot slot = OnboardPanelGeometry.slotAt(
                pos, state.getValue(OnboardComputerBlock.FACING), hit);
        if (slot == null || !computer.module(slot).isEmpty()) {
            return;
        }
        Vec3[] corners = OnboardPanelGeometry.outline(
                pos, state.getValue(OnboardComputerBlock.FACING), slot);
        for (int corner = 0; corner < corners.length; corner++) {
            corners[corner] = SableRadarIntegration.worldPosition(minecraft.level, pos, corners[corner]);
        }
        for (int edge = 0; edge < corners.length; edge++) {
            Outliner.getInstance()
                    .showLine(new OutlineKey(pos.immutable(), slot, edge), corners[edge], corners[(edge + 1) % 4])
                    .lineWidth(0.03125F)
                    .disableLineNormals()
                    .colored(OUTLINE_COLOR);
        }
    }

    private record OutlineKey(BlockPos pos, OnboardModuleSlot slot, int edge) {
    }
}
