package com.limbo2136.powerradar.client.radarlink;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.item.InterceptionControllerBlockItem;
import com.limbo2136.powerradar.item.RadarLinkBlockItem;
import com.limbo2136.powerradar.item.ShellAlarmBlockItem;
import com.limbo2136.powerradar.item.OnboardComputerBlockItem;
import com.limbo2136.powerradar.registry.ModDataComponents;
import java.util.UUID;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RadarLinkClientOutlineHandler {
    private static final double OUTLINE_INSET = 1.0 / 16.0;
    private static final double OUTLINE_DEPTH = 4.0 / 16.0;

    private static ClientLevel lastLevel;

    private RadarLinkClientOutlineHandler() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            if (lastLevel != null) {
                RadarLinkClientCache.clear();
                InterceptionNetworkClientCache.clear();
                lastLevel = null;
            }
            return;
        }
        if (lastLevel != level) {
            RadarLinkClientCache.clear();
            InterceptionNetworkClientCache.clear();
            lastLevel = level;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof InterceptionControllerBlockItem) {
            outlineInterceptionNetwork(player, level, stack);
            return;
        }
        if (!(stack.getItem() instanceof RadarLinkBlockItem)
                && !(stack.getItem() instanceof ShellAlarmBlockItem)
                && !(stack.getItem() instanceof OnboardComputerBlockItem)) {
            return;
        }
        UUID networkId = stack.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get());
        if (networkId == null) {
            return;
        }

        int color = pulseColor();
        for (BlockPos pos : RadarLinkClientCache.getLinks(level, networkId)) {
            if (!level.isLoaded(pos)) {
                RadarLinkClientCache.unregister(level, pos);
                continue;
            }
            UUID nodeNetworkId;
            if (level.getBlockEntity(pos) instanceof RadarLinkBlockEntity link) {
                nodeNetworkId = link.networkId();
            } else if (level.getBlockEntity(pos) instanceof ShellAlarmBlockEntity alarm) {
                nodeNetworkId = alarm.networkId();
            } else if (level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer) {
                nodeNetworkId = computer.networkId();
            } else {
                RadarLinkClientCache.unregister(level, pos);
                continue;
            }
            if (!networkId.equals(nodeNetworkId)) {
                RadarLinkClientCache.registerOrUpdate(level, pos, nodeNetworkId);
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > outlineRangeSquared()) {
                continue;
            }
            outlineLink(level, pos, networkId, color);
        }
    }

    private static void outlineInterceptionNetwork(
            LocalPlayer player,
            ClientLevel level,
            ItemStack stack
    ) {
        UUID networkId = stack.get(ModDataComponents.INTERCEPTION_NETWORK_ID.get());
        if (networkId == null) {
            return;
        }
        int color = interceptionPulseColor();
        for (BlockPos pos : InterceptionNetworkClientCache.getNodes(level, networkId)) {
            if (!level.isLoaded(pos)) {
                InterceptionNetworkClientCache.unregister(level, pos);
                continue;
            }
            UUID nodeNetworkId = interceptionNetworkId(level, pos);
            if (nodeNetworkId == null) {
                InterceptionNetworkClientCache.unregister(level, pos);
                continue;
            }
            if (!networkId.equals(nodeNetworkId)) {
                InterceptionNetworkClientCache.registerOrUpdate(level, pos, nodeNetworkId);
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(pos)) > outlineRangeSquared()) {
                continue;
            }
            AABB outline = level.getBlockState(pos).getShape(level, pos).bounds().move(pos);
            CatnipOutlinerAdapter.showRadarLinkOutline(
                    new OutlineKey(
                            "interception",
                            level.dimension().location().toString(),
                            networkId,
                            pos.immutable()),
                    outline,
                    color);
        }
    }

    private static UUID interceptionNetworkId(ClientLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ShellAlarmBlockEntity alarm) {
            return alarm.interceptionNetworkId();
        }
        if (level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer) {
            return computer.interceptionNetworkId();
        }
        if (level.getBlockEntity(pos) instanceof InterceptionControllerBlockEntity controller) {
            return controller.interceptionNetworkId();
        }
        return null;
    }

    private static void outlineLink(ClientLevel level, BlockPos pos, UUID networkId, int color) {
        BlockState state = level.getBlockState(pos);
        AABB outline = state.hasProperty(RadarLinkBlock.FACING)
                ? outlineBoxFor(state.getValue(RadarLinkBlock.FACING)).move(pos)
                : state.getShape(level, pos).bounds().move(pos);
        CatnipOutlinerAdapter.showRadarLinkOutline(
                new OutlineKey(
                        "radar",
                        level.dimension().location().toString(),
                        networkId,
                        pos.immutable()),
                outline,
                color
        );
    }

    private static AABB outlineBoxFor(Direction facing) {
        double min = OUTLINE_INSET;
        double max = 1.0 - OUTLINE_INSET;
        return switch (facing) {
            case NORTH -> new AABB(min, min, 0.0, max, max, OUTLINE_DEPTH);
            case SOUTH -> new AABB(min, min, 1.0 - OUTLINE_DEPTH, max, max, 1.0);
            case WEST -> new AABB(0.0, min, min, OUTLINE_DEPTH, max, max);
            case EAST -> new AABB(1.0 - OUTLINE_DEPTH, min, min, 1.0, max, max);
            case DOWN -> new AABB(min, 0.0, min, max, OUTLINE_DEPTH, max);
            case UP -> new AABB(min, 1.0 - OUTLINE_DEPTH, min, max, 1.0, max);
        };
    }

    private static int pulseColor() {
        int phase = AnimationTickHolder.getTicks() % RadarConstants.RADAR_LINK_OUTLINE_PULSE_PERIOD_TICKS;
        return phase < RadarConstants.RADAR_LINK_OUTLINE_PULSE_HALF_PERIOD_TICKS
                ? RadarConstants.RADAR_LINK_OUTLINE_COLOR_A
                : RadarConstants.RADAR_LINK_OUTLINE_COLOR_B;
    }

    private static int interceptionPulseColor() {
        int phase = AnimationTickHolder.getTicks() % RadarConstants.RADAR_LINK_OUTLINE_PULSE_PERIOD_TICKS;
        return phase < RadarConstants.RADAR_LINK_OUTLINE_PULSE_HALF_PERIOD_TICKS
                ? RadarConstants.INTERCEPTION_NETWORK_OUTLINE_COLOR_A
                : RadarConstants.INTERCEPTION_NETWORK_OUTLINE_COLOR_B;
    }

    private static double outlineRangeSquared() {
        return RadarConstants.RADAR_LINK_OUTLINE_RANGE_BLOCKS * RadarConstants.RADAR_LINK_OUTLINE_RANGE_BLOCKS;
    }

    private record OutlineKey(String kind, String dimension, UUID networkId, BlockPos pos) {
    }
}
