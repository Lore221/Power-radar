package com.limbo2136.powerradar.client.radarlink;

import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.attachments.PanelAttachment;
import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.compat.electroenergetics.panel.RadarLinkPanelAttachment;
import com.limbo2136.powerradar.item.InterceptionControllerBlockItem;
import com.limbo2136.powerradar.item.LinkerItem;
import com.limbo2136.powerradar.item.RadarLinkBlockItem;
import com.limbo2136.powerradar.item.ShellAlarmBlockItem;
import com.limbo2136.powerradar.item.OnboardComputerBlockItem;
import com.limbo2136.powerradar.registry.ModDataComponents;
import java.util.UUID;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
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
                PanelRadarLinkClientCache.clear();
                InterceptionNetworkClientCache.clear();
                lastLevel = null;
            }
            return;
        }
        if (lastLevel != level) {
            RadarLinkClientCache.clear();
            PanelRadarLinkClientCache.clear();
            InterceptionNetworkClientCache.clear();
            lastLevel = level;
        }

        ItemStack stack = player.getMainHandItem();
        ItemStack linkerStack = stack.getItem() instanceof LinkerItem
                ? stack
                : player.getOffhandItem();
        if (linkerStack.getItem() instanceof LinkerItem) {
            outlineRadarNetwork(player, level, linkerStack);
            outlineInterceptionNetwork(player, level, linkerStack);
            return;
        }
        if (stack.getItem() instanceof InterceptionControllerBlockItem) {
            outlineInterceptionNetwork(player, level, stack);
            return;
        }
        if (!(stack.getItem() instanceof RadarLinkBlockItem)
                && !(stack.getItem() instanceof ShellAlarmBlockItem)
                && !(stack.getItem() instanceof OnboardComputerBlockItem)) {
            return;
        }
        outlineRadarNetwork(player, level, stack);
    }

    private static void outlineRadarNetwork(LocalPlayer player, ClientLevel level, ItemStack stack) {
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
        outlinePanelLinks(player, level, networkId, color);
    }

    /** Подсвечивает только секцию Link, а не весь электрический щиток. */
    private static void outlinePanelLinks(
            LocalPlayer player,
            ClientLevel level,
            UUID networkId,
            int color
    ) {
        for (PanelRadarLinkClientCache.LinkLocation location
                : PanelRadarLinkClientCache.getLinks(level, networkId)) {
            BlockPos panelPos = location.panelPos();
            if (!level.isLoaded(panelPos)) {
                PanelRadarLinkClientCache.unregister(level, location);
                continue;
            }
            if (!(level.getBlockEntity(panelPos) instanceof ElectricalPanelBlockEntity panel)) {
                PanelRadarLinkClientCache.unregister(level, location);
                continue;
            }
            PanelAttachment attachment = panel.getAttachments()[location.slot().ordinal()];
            if (!(attachment instanceof RadarLinkPanelAttachment link)) {
                PanelRadarLinkClientCache.unregister(level, location);
                continue;
            }
            UUID actualNetworkId = link.networkId();
            if (!networkId.equals(actualNetworkId)) {
                PanelRadarLinkClientCache.registerOrUpdate(
                        level, panelPos, location.slot(), actualNetworkId);
                continue;
            }
            if (player.distanceToSqr(Vec3.atCenterOf(panelPos)) > outlineRangeSquared()) {
                continue;
            }

            Direction facing = panel.getBlockState().getValue(ElectricalPanelBlock.FACING);
            AABB localBounds = location.slot().shape;
            Vec3 lowerCorner = VecHelper.rotateCentered(
                    localBounds.getMinPosition(), -facing.toYRot() + 180.0F, Direction.Axis.Y);
            Vec3 upperCorner = VecHelper.rotateCentered(
                    localBounds.getMaxPosition(), -facing.toYRot() + 180.0F, Direction.Axis.Y);
            AABB worldBounds = new AABB(lowerCorner, upperCorner).move(panelPos);
            CatnipOutlinerAdapter.showRadarLinkOutline(
                    new PanelOutlineKey(
                            level.dimension().location().toString(),
                            networkId,
                            panelPos.immutable(),
                            location.slot().ordinal()),
                    worldBounds,
                    color);
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
        reconcileInterceptionRoots(level);
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

    // Shell Alarm и Onboard уже известны синему кэшу как радарные узлы.
    // Это восстанавливает их оранжевую регистрацию без поиска block entity по чанкам.
    private static void reconcileInterceptionRoots(ClientLevel level) {
        for (BlockPos pos : RadarLinkClientCache.getKnownNodePositions(level)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            UUID rootNetworkId;
            if (level.getBlockEntity(pos) instanceof ShellAlarmBlockEntity alarm) {
                rootNetworkId = alarm.interceptionNetworkId();
            } else if (level.getBlockEntity(pos) instanceof OnboardComputerBlockEntity computer) {
                rootNetworkId = computer.interceptionNetworkId();
            } else {
                continue;
            }
            InterceptionNetworkClientCache.registerOrUpdate(level, pos, rootNetworkId);
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

    private record PanelOutlineKey(
            String dimension,
            UUID networkId,
            BlockPos panelPos,
            int slot
    ) {
    }
}
