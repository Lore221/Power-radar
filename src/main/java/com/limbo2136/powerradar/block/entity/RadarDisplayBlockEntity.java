package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.block.RadarDisplayBlock;
import com.limbo2136.powerradar.block.RadarDisplayFrameShape;
import com.limbo2136.powerradar.block.RadarDisplayStructure;
import com.limbo2136.powerradar.block.RadarDisplayStructureResolver;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Состояние одного блока составного монитора. Корневой блок владеет питанием,
 * радарной сетью и серверным snapshot; дочерние блоки хранят только принадлежность.
 */
public final class RadarDisplayBlockEntity extends AbstractRadarMonitorBlockEntity {
    private UUID displayId = UUID.randomUUID();
    private BlockPos rootPos;
    @Nullable
    private UUID displayNetworkId;
    private boolean groupDirty = true;
    private boolean removingDisplay;

    public RadarDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RADAR_DISPLAY.get(), pos, state);
        this.rootPos = pos.immutable();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RadarDisplayBlockEntity display) {
        if (level.isClientSide() || display.removingDisplay) {
            return;
        }
        if (!display.isRoot()) {
            return;
        }
        if (display.groupDirty) {
            display.groupDirty = false;
            display.reconcileGroup();
        }
        AbstractRadarMonitorBlockEntity.tick(level, pos, state, display);
    }

    public UUID displayId() {
        return this.displayId;
    }

    public BlockPos rootPos() {
        return this.rootPos;
    }

    public boolean isRoot() {
        return this.worldPosition.equals(this.rootPos);
    }

    public void joinDisplay(RadarDisplayBlockEntity member) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        RadarDisplayBlockEntity root = member.loadedRoot();
        if (root == null || root.removingDisplay) {
            return;
        }
        this.displayId = root.displayId;
        this.rootPos = root.worldPosition.immutable();
        this.displayNetworkId = root.displayNetworkId;
        this.groupDirty = false;
        syncDisplay();
        root.groupDirty = true;
        serverLevel.scheduleTick(root.worldPosition, root.getBlockState().getBlock(), 1);
    }

    public boolean canJoinAt(BlockPos targetPos) {
        RadarDisplayBlockEntity root = loadedRoot();
        if (root == null || root.removingDisplay) {
            return false;
        }
        Direction facing = root.facing();
        Direction right = RadarDisplayStructureResolver.right(facing);
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;
        for (RadarDisplayBlockEntity member : root.groupMembers()) {
            int dx = member.worldPosition.getX() - root.worldPosition.getX();
            int dz = member.worldPosition.getZ() - root.worldPosition.getZ();
            int depth = dx * facing.getStepX() + dz * facing.getStepZ();
            if (depth != 0 || member.facing() != facing) {
                return false;
            }
            int u = dx * right.getStepX() + dz * right.getStepZ();
            int v = member.worldPosition.getY() - root.worldPosition.getY();
            minU = Math.min(minU, u);
            maxU = Math.max(maxU, u);
            minV = Math.min(minV, v);
            maxV = Math.max(maxV, v);
        }
        int targetDx = targetPos.getX() - root.worldPosition.getX();
        int targetDz = targetPos.getZ() - root.worldPosition.getZ();
        if (targetDx * facing.getStepX() + targetDz * facing.getStepZ() != 0) {
            return false;
        }
        int targetU = targetDx * right.getStepX() + targetDz * right.getStepZ();
        int targetV = targetPos.getY() - root.worldPosition.getY();
        minU = Math.min(minU, targetU);
        maxU = Math.max(maxU, targetU);
        minV = Math.min(minV, targetV);
        maxV = Math.max(maxV, targetV);
        return maxU - minU + 1 <= RadarDisplayStructureResolver.MAX_SIZE
                && maxV - minV + 1 <= RadarDisplayStructureResolver.MAX_SIZE;
    }

    public void prepareForDisplayRemoval() {
        this.removingDisplay = true;
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<RadarDisplayBlockEntity> remaining = groupMembers().stream()
                .filter(member -> member != this)
                .sorted(Comparator.comparing(RadarDisplayBlockEntity::getBlockPos,
                        RadarDisplayStructureResolver::compareBlockPos))
                .toList();
        if (remaining.isEmpty()) {
            return;
        }
        if (isRoot()) {
            RadarDisplayBlockEntity replacement = remaining.getFirst();
            for (RadarDisplayBlockEntity member : remaining) {
                member.rootPos = replacement.worldPosition.immutable();
                member.displayNetworkId = this.displayNetworkId;
                member.groupDirty = member == replacement;
                member.syncDisplay();
            }
            serverLevel.scheduleTick(replacement.worldPosition, replacement.getBlockState().getBlock(), 1);
        } else {
            RadarDisplayBlockEntity root = loadedRoot();
            if (root != null) {
                root.groupDirty = true;
                serverLevel.scheduleTick(root.worldPosition, root.getBlockState().getBlock(), 1);
            }
        }
    }

    public void markGroupDirty() {
        RadarDisplayBlockEntity root = loadedRoot();
        if (root != null) {
            root.groupDirty = true;
        }
    }

    @Nullable
    public RadarDisplayBlockEntity loadedRoot() {
        if (this.level == null || !this.level.isLoaded(this.rootPos)) {
            return null;
        }
        BlockEntity blockEntity = this.level.getBlockEntity(this.rootPos);
        return blockEntity instanceof RadarDisplayBlockEntity display
                && display.displayId.equals(this.displayId) ? display : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        RadarDisplayBlockEntity root = loadedRoot();
        if (root != null && root != this) {
            return root.addToGoggleTooltip(tooltip, isPlayerSneaking);
        }
        return super.addToGoggleTooltip(tooltip, isPlayerSneaking);
    }

    private void reconcileGroup() {
        if (!(this.level instanceof ServerLevel serverLevel) || !isRoot()) {
            return;
        }
        List<RadarDisplayBlockEntity> members = groupMembers();
        if (members.isEmpty()) {
            updateDisplayStructure(null, 0, 0, facing(),
                    RadarDisplayStructureResolver.StructureStatus.NO_DISPLAY);
            return;
        }

        Direction facing = facing();
        Direction right = RadarDisplayStructureResolver.right(facing);
        Map<DisplayCell, RadarDisplayBlockEntity> planeMembers = new HashMap<>();
        for (RadarDisplayBlockEntity member : members) {
            BlockPos pos = member.worldPosition;
            int dx = pos.getX() - this.worldPosition.getX();
            int dz = pos.getZ() - this.worldPosition.getZ();
            int depth = dx * facing.getStepX() + dz * facing.getStepZ();
            if (depth != 0 || member.facing() != facing) {
                continue;
            }
            int u = dx * right.getStepX() + dz * right.getStepZ();
            int v = pos.getY() - this.worldPosition.getY();
            planeMembers.put(new DisplayCell(u, v), member);
        }
        DisplayRectangle rectangle = largestCompleteRectangle(planeMembers, right);
        if (rectangle == null) {
            updateDisplayStructure(null, 0, 0, facing,
                    RadarDisplayStructureResolver.StructureStatus.NO_DISPLAY);
            return;
        }

        BlockPos origin = RadarDisplayStructureResolver.localOffset(
                this.worldPosition, right, rectangle.minU(), rectangle.minV());
        Set<BlockPos> activePositions = Set.copyOf(RadarDisplayStructure.rectanglePositions(
                origin, facing, rectangle.width(), rectangle.height()));
        RadarDisplayStructure structure = new RadarDisplayStructure(
                origin, rectangle.width(), rectangle.height(), facing, activePositions);
        applyMemberStates(serverLevel, members, structure);
        updateDisplayStructure(
                origin,
                rectangle.width(),
                rectangle.height(),
                facing,
                RadarDisplayStructureResolver.StructureStatus.ACTIVE);
    }

    @Nullable
    private DisplayRectangle largestCompleteRectangle(
            Map<DisplayCell, RadarDisplayBlockEntity> members,
            Direction right
    ) {
        DisplayRectangle active = activeRectangle(right);
        if (active != null && isComplete(members, active)) {
            return expandByCompleteLayers(members, active);
        }

        DisplayRectangle best = null;
        for (DisplayCell origin : members.keySet()) {
            for (int width = 1; width <= RadarDisplayStructureResolver.MAX_SIZE; width++) {
                for (int height = 1; height <= RadarDisplayStructureResolver.MAX_SIZE; height++) {
                    if (!containsRoot(origin, width, height)
                            || !isComplete(members, origin, width, height)) {
                        continue;
                    }
                    DisplayRectangle candidate = new DisplayRectangle(origin.u(), origin.v(), width, height);
                    if (isBetterRectangle(candidate, best, right)) {
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private DisplayRectangle activeRectangle(Direction right) {
        BlockPos activeOrigin = activeOrigin();
        if (activeOrigin == null || activeWidth() <= 0 || activeHeight() <= 0) {
            return null;
        }
        int dx = activeOrigin.getX() - this.worldPosition.getX();
        int dz = activeOrigin.getZ() - this.worldPosition.getZ();
        int u = dx * right.getStepX() + dz * right.getStepZ();
        int v = activeOrigin.getY() - this.worldPosition.getY();
        return new DisplayRectangle(u, v, activeWidth(), activeHeight());
    }

    private static DisplayRectangle expandByCompleteLayers(
            Map<DisplayCell, RadarDisplayBlockEntity> members,
            DisplayRectangle initial
    ) {
        DisplayRectangle rectangle = initial;
        while (true) {
            DisplayRectangle expanded = null;
            if (rectangle.height() < RadarDisplayStructureResolver.MAX_SIZE) {
                expanded = betterCompleteExpansion(members, expanded, new DisplayRectangle(
                        rectangle.minU(), rectangle.minV() - 1,
                        rectangle.width(), rectangle.height() + 1));
                expanded = betterCompleteExpansion(members, expanded, new DisplayRectangle(
                        rectangle.minU(), rectangle.minV(),
                        rectangle.width(), rectangle.height() + 1));
            }
            if (rectangle.width() < RadarDisplayStructureResolver.MAX_SIZE) {
                expanded = betterCompleteExpansion(members, expanded, new DisplayRectangle(
                        rectangle.minU() - 1, rectangle.minV(),
                        rectangle.width() + 1, rectangle.height()));
                expanded = betterCompleteExpansion(members, expanded, new DisplayRectangle(
                        rectangle.minU(), rectangle.minV(),
                        rectangle.width() + 1, rectangle.height()));
            }
            if (expanded == null) {
                return rectangle;
            }
            rectangle = expanded;
        }
    }

    @Nullable
    private static DisplayRectangle betterCompleteExpansion(
            Map<DisplayCell, RadarDisplayBlockEntity> members,
            @Nullable DisplayRectangle current,
            DisplayRectangle candidate
    ) {
        if (!isComplete(members, candidate)) {
            return current;
        }
        return current == null || candidate.area() > current.area() ? candidate : current;
    }

    private boolean isBetterRectangle(
            DisplayRectangle candidate,
            @Nullable DisplayRectangle currentBest,
            Direction right
    ) {
        if (currentBest == null || candidate.area() != currentBest.area()) {
            return currentBest == null || candidate.area() > currentBest.area();
        }
        boolean candidateIsActive = matchesActiveRectangle(candidate, right);
        boolean bestIsActive = matchesActiveRectangle(currentBest, right);
        if (candidateIsActive != bestIsActive) {
            return candidateIsActive;
        }
        int candidateMinimumSide = Math.min(candidate.width(), candidate.height());
        int bestMinimumSide = Math.min(currentBest.width(), currentBest.height());
        if (candidateMinimumSide != bestMinimumSide) {
            return candidateMinimumSide > bestMinimumSide;
        }
        if (candidate.minV() != currentBest.minV()) {
            return candidate.minV() < currentBest.minV();
        }
        return candidate.minU() < currentBest.minU();
    }

    private boolean matchesActiveRectangle(DisplayRectangle candidate, Direction right) {
        BlockPos activeOrigin = activeOrigin();
        if (activeOrigin == null
                || candidate.width() != activeWidth()
                || candidate.height() != activeHeight()) {
            return false;
        }
        int dx = activeOrigin.getX() - this.worldPosition.getX();
        int dz = activeOrigin.getZ() - this.worldPosition.getZ();
        int u = dx * right.getStepX() + dz * right.getStepZ();
        int v = activeOrigin.getY() - this.worldPosition.getY();
        return candidate.minU() == u && candidate.minV() == v;
    }

    private static boolean containsRoot(DisplayCell origin, int width, int height) {
        return origin.u() <= 0 && origin.u() + width > 0
                && origin.v() <= 0 && origin.v() + height > 0;
    }

    private static boolean isComplete(
            Map<DisplayCell, RadarDisplayBlockEntity> members,
            DisplayCell origin,
            int width,
            int height
    ) {
        for (int u = origin.u(); u < origin.u() + width; u++) {
            for (int v = origin.v(); v < origin.v() + height; v++) {
                if (!members.containsKey(new DisplayCell(u, v))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isComplete(
            Map<DisplayCell, RadarDisplayBlockEntity> members,
            DisplayRectangle rectangle
    ) {
        return isComplete(
                members,
                new DisplayCell(rectangle.minU(), rectangle.minV()),
                rectangle.width(),
                rectangle.height());
    }

    private static void applyMemberStates(
            ServerLevel level,
            List<RadarDisplayBlockEntity> members,
            RadarDisplayStructure structure
    ) {
        for (RadarDisplayBlockEntity member : members) {
            BlockState state = member.getBlockState();
            boolean active = structure.contains(member.worldPosition);
            RadarDisplayFrameShape shape = active
                    ? structure.frameShape(member.worldPosition)
                    : RadarDisplayFrameShape.SINGLE;
            BlockState target = state
                    .setValue(RadarDisplayBlock.ACTIVE, active)
                    .setValue(RadarDisplayBlock.FRAME_SHAPE, shape);
            if (!state.equals(target)) {
                level.setBlock(member.worldPosition, target, 3);
            }
            member.syncDisplay();
        }
    }

    private record DisplayCell(int u, int v) {
    }

    private record DisplayRectangle(int minU, int minV, int width, int height) {
        private int area() {
            return this.width * this.height;
        }
    }

    private List<RadarDisplayBlockEntity> groupMembers() {
        if (this.level == null) {
            return List.of();
        }
        Direction right = RadarDisplayStructureResolver.right(facing());
        ArrayList<RadarDisplayBlockEntity> members = new ArrayList<>();
        int radius = RadarDisplayStructureResolver.MAX_SIZE - 1;
        for (int u = -radius; u <= radius; u++) {
            for (int v = -radius; v <= radius; v++) {
                BlockPos pos = RadarDisplayStructureResolver.localOffset(this.rootPos, right, u, v);
                if (!this.level.isLoaded(pos)) {
                    continue;
                }
                BlockEntity blockEntity = this.level.getBlockEntity(pos);
                if (blockEntity instanceof RadarDisplayBlockEntity display
                        && display.displayId.equals(this.displayId)) {
                    members.add(display);
                }
            }
        }
        return List.copyOf(members);
    }

    @Override
    @Nullable
    protected UUID directNetworkId() {
        return this.displayNetworkId;
    }

    @Override
    @Nullable
    public UUID radarNetworkId() {
        return this.displayNetworkId;
    }

    @Override
    public void setRadarNetworkId(@Nullable UUID networkId) {
        RadarDisplayBlockEntity root = loadedRoot();
        if (root != null && root != this) {
            root.setRadarNetworkId(networkId);
            return;
        }
        if (Objects.equals(this.displayNetworkId, networkId)) {
            return;
        }
        UUID oldNetworkId = this.displayNetworkId;
        for (RadarDisplayBlockEntity member : groupMembers()) {
            member.displayNetworkId = networkId;
            RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                    member.level, member.worldPosition, oldNetworkId, networkId);
            member.syncDisplay();
        }
        if (this.level instanceof ServerLevel serverLevel && networkId != null) {
            RadarNetworkManager.get(serverLevel.getServer()).ensureNetwork(networkId);
        }
        invalidateSnapshotCache();
    }

    @Override
    protected void onDisplayStructureChanged() {
        if (!this.removingDisplay && this.level instanceof ServerLevel serverLevel) {
            com.limbo2136.powerradar.compat.electroenergetics.PowerRadarCeeIntegration.configureMonitorLoad(
                    serverLevel, this.worldPosition, hasValidDisplayStructure(), activeDisplayCount());
        }
    }

    private void syncDisplay() {
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("DisplayId", this.displayId);
        tag.putInt("DisplayRootX", this.rootPos.getX());
        tag.putInt("DisplayRootY", this.rootPos.getY());
        tag.putInt("DisplayRootZ", this.rootPos.getZ());
        if (this.displayNetworkId != null) {
            tag.putUUID("PowerRadarNetworkId", this.displayNetworkId);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        UUID oldNetworkId = this.displayNetworkId;
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("DisplayId")) {
            this.displayId = tag.getUUID("DisplayId");
        }
        if (tag.contains("DisplayRootX")) {
            this.rootPos = new BlockPos(
                    tag.getInt("DisplayRootX"),
                    tag.getInt("DisplayRootY"),
                    tag.getInt("DisplayRootZ"));
        }
        this.displayNetworkId = tag.hasUUID("PowerRadarNetworkId")
                ? tag.getUUID("PowerRadarNetworkId") : null;
        RadarNetworkNodeClientCacheBridge.onNetworkChanged(
                this.level, this.worldPosition, oldNetworkId, this.displayNetworkId);
        this.groupDirty = isRoot();
    }
}
