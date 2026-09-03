package com.limbo2136.powerradar.client.radarlink;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.AircraftRadarBlock;
import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.block.RadarDisplayStructure;
import com.limbo2136.powerradar.block.entity.RadarDisplayBlockEntity;
import com.limbo2136.powerradar.block.entity.RadarLinkBlockEntity;
import com.limbo2136.powerradar.block.entity.ShellAlarmBlockEntity;
import com.limbo2136.powerradar.block.entity.OnboardComputerBlockEntity;
import com.limbo2136.powerradar.block.entity.InterceptionControllerBlockEntity;
import com.limbo2136.powerradar.item.InterceptionControllerBlockItem;
import com.limbo2136.powerradar.item.LinkerItem;
import com.limbo2136.powerradar.item.RadarLinkBlockItem;
import com.limbo2136.powerradar.item.RadarNetworkBlockItem;
import com.limbo2136.powerradar.item.ShellAlarmBlockItem;
import com.limbo2136.powerradar.item.OnboardComputerBlockItem;
import com.limbo2136.powerradar.compat.aeronautics.SableRadarIntegration;
import com.limbo2136.powerradar.compat.createbigcannons.CreateBigCannonsIntegration;
import com.limbo2136.powerradar.registry.ModDataComponents;
import com.limbo2136.powerradar.registry.ModBlocks;
import com.limbo2136.powerradar.radar.network.RadarNetworkMember;
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
    private static final double AIR_RADAR_OUTLINE_INFLATION = 1.0 / 16.0;
    private static final double TARGET_CONTROLLER_OUTLINE_INFLATION = 1.0 / 16.0;
    private static final double LOGIC_DOCK_OUTLINE_EXTRA_HEIGHT = 3.0 / 16.0;
    private static final double RADAR_DISPLAY_DEPTH = 11.0 / 16.0;
    private static final double RADAR_DISPLAY_FRONT_INSET = 3.0 / 16.0;

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
        if (!(stack.getItem() instanceof RadarNetworkBlockItem)
                && !(stack.getItem() instanceof RadarLinkBlockItem)
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

        int color = radarPulseColor();
        for (BlockPos pos : RadarLinkClientCache.getLinks(level, networkId)) {
            if (!level.isLoaded(pos)) {
                RadarLinkClientCache.unregister(level, pos);
                continue;
            }
            UUID nodeNetworkId;
            if (level.getBlockEntity(pos) instanceof RadarNetworkMember member) {
                nodeNetworkId = member.radarNetworkId();
            } else if (level.getBlockEntity(pos) instanceof RadarLinkBlockEntity link) {
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
            if (level.getBlockEntity(pos) instanceof RadarDisplayBlockEntity display) {
                if (display.isRoot()) {
                    outlineRadarDisplay(display, networkId, color);
                }
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
        AABB outline;
        if (state.is(ModBlocks.AIR_RADAR_CONTROLLER.get())) {
            outline = new AABB(pos).inflate(AIR_RADAR_OUTLINE_INFLATION);
        } else if (SableRadarIntegration.isAeronauticsLoaded()
                && state.is(ModBlocks.AIRCRAFT_RADAR.get())) {
            outline = aircraftRadarOutline(pos, state.getValue(AircraftRadarBlock.FACING));
        } else if (CreateBigCannonsIntegration.isLoaded()
                && state.is(ModBlocks.TARGET_CONTROLLER.get())) {
            outline = new AABB(pos).inflate(TARGET_CONTROLLER_OUTLINE_INFLATION);
        } else if (state.is(ModBlocks.LOGIC_DOCK.get())) {
            AABB bodyBounds = state.getShape(level, pos).bounds().move(pos);
            outline = new AABB(
                    bodyBounds.minX, bodyBounds.minY, bodyBounds.minZ,
                    bodyBounds.maxX, bodyBounds.maxY + LOGIC_DOCK_OUTLINE_EXTRA_HEIGHT, bodyBounds.maxZ);
        } else if (state.hasProperty(RadarLinkBlock.FACING)) {
            outline = outlineBoxFor(state.getValue(RadarLinkBlock.FACING)).move(pos);
        } else {
            outline = state.getShape(level, pos).bounds().move(pos);
        }
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

    /** The Aircraft Radar is a three-block-long multiblock with its core at the rear. */
    private static AABB aircraftRadarOutline(BlockPos core, Direction facing) {
        BlockPos front = core.relative(facing, 2);
        return new AABB(core).minmax(new AABB(front));
    }

    private static void outlineRadarDisplay(RadarDisplayBlockEntity display, UUID networkId, int color) {
        BlockPos origin = display.activeOrigin();
        if (origin == null || display.activeWidth() <= 0 || display.activeHeight() <= 0) {
            return;
        }
        AABB outline = null;
        for (BlockPos pos : RadarDisplayStructure.rectanglePositions(
                origin, display.activeFacing(), display.activeWidth(), display.activeHeight())) {
            AABB blockBounds = new AABB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
            outline = outline == null ? blockBounds : outline.minmax(blockBounds);
        }
        if (outline == null) {
            return;
        }
        outline = displayDepthBounds(outline, display.activeFacing());
        CatnipOutlinerAdapter.showRadarLinkOutline(
                new OutlineKey(
                        "radar_display",
                        display.getLevel().dimension().location().toString(),
                        networkId,
                        display.getBlockPos().immutable()),
                outline,
                color);
    }

    private static AABB displayDepthBounds(AABB fullBlocks, Direction facing) {
        return switch (facing) {
            case NORTH -> new AABB(
                    fullBlocks.minX, fullBlocks.minY, fullBlocks.minZ + RADAR_DISPLAY_FRONT_INSET,
                    fullBlocks.maxX, fullBlocks.maxY,
                    fullBlocks.minZ + RADAR_DISPLAY_FRONT_INSET + RADAR_DISPLAY_DEPTH);
            case SOUTH -> new AABB(
                    fullBlocks.minX, fullBlocks.minY,
                    fullBlocks.maxZ - RADAR_DISPLAY_FRONT_INSET - RADAR_DISPLAY_DEPTH,
                    fullBlocks.maxX, fullBlocks.maxY, fullBlocks.maxZ - RADAR_DISPLAY_FRONT_INSET);
            case WEST -> new AABB(
                    fullBlocks.minX + RADAR_DISPLAY_FRONT_INSET, fullBlocks.minY, fullBlocks.minZ,
                    fullBlocks.minX + RADAR_DISPLAY_FRONT_INSET + RADAR_DISPLAY_DEPTH,
                    fullBlocks.maxY, fullBlocks.maxZ);
            case EAST -> new AABB(
                    fullBlocks.maxX - RADAR_DISPLAY_FRONT_INSET - RADAR_DISPLAY_DEPTH,
                    fullBlocks.minY, fullBlocks.minZ,
                    fullBlocks.maxX - RADAR_DISPLAY_FRONT_INSET, fullBlocks.maxY, fullBlocks.maxZ);
            case UP, DOWN -> fullBlocks;
        };
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

    public static boolean isSelectedRadarNetwork(UUID networkId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || networkId == null) {
            return false;
        }
        ItemStack mainHand = minecraft.player.getMainHandItem();
        ItemStack offHand = minecraft.player.getOffhandItem();
        ItemStack selected = mainHand.getItem() instanceof LinkerItem
                ? mainHand
                : offHand.getItem() instanceof LinkerItem ? offHand : mainHand;
        return networkId.equals(selected.get(ModDataComponents.POWER_RADAR_NETWORK_ID.get()));
    }

    public static int radarPulseColor() {
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
