package com.limbo2136.powerradar.block.entity;

import com.limbo2136.powerradar.RadarConstants;
import com.limbo2136.powerradar.block.RadarControllerBlock;
import com.limbo2136.powerradar.block.RadarLinkBlock;
import com.limbo2136.powerradar.bridge.RadarNetworkNodeClientCacheBridge;
import com.limbo2136.powerradar.radar.network.RadarLinkEndpointRole;
import com.limbo2136.powerradar.radar.network.RadarLinkReconcileResult;
import com.limbo2136.powerradar.radar.network.RadarNetworkConnectionStatus;
import com.limbo2136.powerradar.radar.network.RadarNetworkManager;
import com.limbo2136.powerradar.registry.ModBlockEntities;
import com.limbo2136.powerradar.registry.ModBlocks;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RadarLinkBlockEntity extends BlockEntity {
    @Nullable
    private UUID networkId;
    private RadarLinkEndpointRole endpointRole = RadarLinkEndpointRole.NONE;
    @Nullable
    private GlobalPos endpointPos;
    private boolean runtimeRegisteredLoaded;
    private boolean needsRuntimeRegister;
    private boolean needsEndpointReconcile;
    private int startupSafetyTicks;
    private int ticksSinceReconcile;

    public RadarLinkBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.RADAR_LINK.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RadarLinkBlockEntity link) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (link.networkId == null) {
            link.initializeNetwork(UUID.randomUUID(), null);
        }
        if (link.startupSafetyTicks > 0) {
            link.startupSafetyTicks--;
            return;
        }
        // После безопасной задержки сначала восстанавливается runtime-регистрация,
        // затем привязка блока перед лицевой стороной и периодическая force-load сверка.
        if (link.needsRuntimeRegister || !link.runtimeRegisteredLoaded) {
            link.registerLoaded(serverLevel);
            link.needsRuntimeRegister = false;
            link.needsEndpointReconcile = true;
        }
        if (link.needsEndpointReconcile) {
            link.needsEndpointReconcile = false;
            link.reconcileFacingEndpoint(null);
        }
        link.ticksSinceReconcile++;
        if (link.ticksSinceReconcile >= RadarConstants.RADAR_LINK_FORCELOAD_RECONCILE_INTERVAL_TICKS) {
            link.ticksSinceReconcile = 0;
            link.reconcileFacingEndpoint(null);
        }
    }

    @Nullable
    public UUID networkId() {
        return this.networkId;
    }

    public RadarLinkEndpointRole endpointRole() {
        return this.endpointRole;
    }

    @Nullable
    public GlobalPos endpointPos() {
        return this.endpointPos;
    }

    public void initializeNetwork(UUID id, @Nullable Player player) {
        UUID oldNetworkId = this.networkId;
        this.networkId = id;
        if (this.level instanceof ServerLevel serverLevel) {
            RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
            manager.ensureNetwork(id);
            manager.addPersistentLink(id, this.globalPos());
            this.registerLoaded(serverLevel);
            this.reconcileFacingEndpoint(player);
        }
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onNetworkChanged(this.level, this.worldPosition, oldNetworkId, this.networkId);
        }
        syncChanged();
    }

    public UUID ensureNetworkId() {
        if (this.networkId == null) {
            this.initializeNetwork(UUID.randomUUID(), null);
        }
        return this.networkId;
    }

    public RadarLinkReconcileResult reconcileFacingEndpoint(@Nullable Player player) {
        if (!(this.level instanceof ServerLevel serverLevel) || this.networkId == null) {
            return RadarLinkReconcileResult.NONE;
        }
        BlockPos frontPos = this.worldPosition.relative(this.getBlockState().getValue(RadarLinkBlock.FACING));
        if (!serverLevel.isLoaded(frontPos)) {
            return RadarLinkReconcileResult.NONE;
        }
        BlockState frontState = serverLevel.getBlockState(frontPos);
        GlobalPos linkGlobalPos = this.globalPos();
        GlobalPos newEndpointPos = GlobalPos.of(serverLevel.dimension(), frontPos);
        RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());

        if (frontState.getBlock() instanceof RadarControllerBlock
                && serverLevel.getBlockEntity(frontPos) instanceof RadarControllerBlockEntity) {
            if (this.endpointRole == RadarLinkEndpointRole.RADAR_MONITOR) {
                manager.detachMonitorFromLink(this.networkId, linkGlobalPos);
            }
            RadarLinkReconcileResult result = manager.attachControllerFromLink(this.networkId, linkGlobalPos, newEndpointPos);
            this.endpointRole = RadarLinkEndpointRole.RADAR_CONTROLLER;
            this.endpointPos = newEndpointPos;
            syncChanged();
            return result;
        }

        if (frontState.is(ModBlocks.RADAR_MONITOR_CONTROLLER.get())
                && serverLevel.getBlockEntity(frontPos) instanceof RadarMonitorControllerBlockEntity) {
            detachCurrentEndpoint(manager, linkGlobalPos);
            manager.attachMonitorFromLink(this.networkId, linkGlobalPos, newEndpointPos);
            this.endpointRole = RadarLinkEndpointRole.RADAR_MONITOR;
            this.endpointPos = newEndpointPos;
            syncChanged();
            if (manager.resolveControllersForConsumer(this.networkId, linkGlobalPos).status()
                    == RadarNetworkConnectionStatus.OUT_OF_RANGE) {
                return RadarLinkReconcileResult.OUT_OF_RANGE;
            }
            return RadarLinkReconcileResult.MONITOR_ATTACHED;
        }

        if (frontState.is(ModBlocks.COMPUTING_BLOCK.get())
                && serverLevel.getBlockEntity(frontPos) instanceof ComputingBlockEntity) {
            detachCurrentEndpoint(manager, linkGlobalPos);
            this.endpointRole = RadarLinkEndpointRole.COMPUTING_BLOCK;
            this.endpointPos = newEndpointPos;
            syncChanged();
            return RadarLinkReconcileResult.NONE;
        }

        if (frontState.is(ModBlocks.TARGET_CONTROLLER.get())) {
            detachCurrentEndpoint(manager, linkGlobalPos);
            this.endpointRole = RadarLinkEndpointRole.FUTURE_CONSUMER;
            this.endpointPos = newEndpointPos;
            syncChanged();
            return RadarLinkReconcileResult.NONE;
        }

        if (this.endpointRole != RadarLinkEndpointRole.NONE) {
            detachCurrentEndpoint(manager, linkGlobalPos);
            this.endpointRole = RadarLinkEndpointRole.NONE;
            this.endpointPos = null;
            syncChanged();
            return RadarLinkReconcileResult.MONITOR_DETACHED;
        }
        return RadarLinkReconcileResult.NONE;
    }

    public void destroyNetworkMembership() {
        if (!(this.level instanceof ServerLevel serverLevel) || this.networkId == null) {
            return;
        }
        RadarNetworkManager manager = RadarNetworkManager.get(serverLevel.getServer());
        GlobalPos linkPos = this.globalPos();
        detachCurrentEndpoint(manager, linkPos);
        manager.removePersistentLink(this.networkId, linkPos);
        this.runtimeRegisteredLoaded = false;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level instanceof ServerLevel && this.networkId != null) {
            this.needsRuntimeRegister = true;
            this.needsEndpointReconcile = true;
            this.startupSafetyTicks = 2;
        }
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onLoaded(this.level, this.worldPosition, this.networkId);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        }
        if (this.level instanceof ServerLevel serverLevel && this.networkId != null && this.runtimeRegisteredLoaded) {
            RadarNetworkManager.get(serverLevel.getServer()).unloadLink(this.networkId, this.globalPos());
            this.runtimeRegisteredLoaded = false;
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (this.level instanceof ServerLevel serverLevel && this.networkId != null && this.runtimeRegisteredLoaded) {
            RadarNetworkManager.get(serverLevel.getServer()).unloadLink(this.networkId, this.globalPos());
            this.runtimeRegisteredLoaded = false;
        }
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onRemoved(this.level, this.worldPosition);
        }
        super.onChunkUnloaded();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.networkId != null) {
            tag.putUUID("PowerRadarNetworkId", this.networkId);
        }
        tag.putString("EndpointRole", this.endpointRole.name());
        if (this.endpointPos != null) {
            tag.putString("EndpointDimension", this.endpointPos.dimension().location().toString());
            tag.putInt("EndpointX", this.endpointPos.pos().getX());
            tag.putInt("EndpointY", this.endpointPos.pos().getY());
            tag.putInt("EndpointZ", this.endpointPos.pos().getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        UUID oldNetworkId = this.networkId;
        this.networkId = tag.hasUUID("PowerRadarNetworkId") ? tag.getUUID("PowerRadarNetworkId") : null;
        try {
            this.endpointRole = RadarLinkEndpointRole.valueOf(tag.getString("EndpointRole"));
        } catch (IllegalArgumentException exception) {
            this.endpointRole = RadarLinkEndpointRole.NONE;
        }
        // EndpointPos проверяется заново после загрузки: сохранённые координаты не могут
        // считаться авторитетными после поворота блока или изменения соседнего endpoint.
        this.endpointPos = null;
        if (this.level != null && this.level.isClientSide()) {
            RadarNetworkNodeClientCacheBridge.onNetworkChanged(this.level, this.worldPosition, oldNetworkId, this.networkId);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    private void registerLoaded(ServerLevel level) {
        if (this.networkId == null) {
            return;
        }
        RadarNetworkManager.get(level.getServer()).loadLink(this.networkId, this.globalPos());
        this.runtimeRegisteredLoaded = true;
    }

    private void detachCurrentEndpoint(RadarNetworkManager manager, GlobalPos linkGlobalPos) {
        if (this.networkId == null) {
            return;
        }
        if (this.endpointRole == RadarLinkEndpointRole.RADAR_MONITOR) {
            manager.detachMonitorFromLink(this.networkId, linkGlobalPos);
        }
        manager.detachControllerFromLink(this.networkId, linkGlobalPos);
    }

    private GlobalPos globalPos() {
        return GlobalPos.of(this.level == null ? Level.OVERWORLD : this.level.dimension(), this.worldPosition);
    }

    private void syncChanged() {
        setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            if (this.networkId != null) {
                RadarNetworkManager.get(serverLevel.getServer()).invalidateComputingCache(this.networkId);
            }
            serverLevel.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 2);
        }
    }
}
