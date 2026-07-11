package com.limbo2136.powerradar.compat.createbigcannons;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

public final class TargetControllerCbcCompat {
    private static final String CANNON_MOUNT_BE = "rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity";
    private static final String FIXED_CANNON_MOUNT_BE = "rbasamoyai.createbigcannons.cannon_control.fixed_cannon_mount.FixedCannonMountBlockEntity";
    private static final String MOUNTED_BIG_CANNON = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption";
    private static final String MOUNTED_AUTOCANNON = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedAutocannonContraption";
    private static final String AUTOCANNON_AMMO_ITEM = "rbasamoyai.createbigcannons.munitions.autocannon.AutocannonAmmoItem";
    private static final String AUTOCANNON_AMMO_CONTAINER_ITEM = "rbasamoyai.createbigcannons.munitions.autocannon.ammo_container.AutocannonAmmoContainerItem";
    private static final String ABSTRACT_AUTOCANNON_BREECH_BE = "rbasamoyai.createbigcannons.cannons.autocannon.breech.AbstractAutocannonBreechBlockEntity";
    private static final String I_AUTOCANNON_BLOCK_ENTITY = "rbasamoyai.createbigcannons.cannons.autocannon.IAutocannonBlockEntity";
    private static final String I_BIG_CANNON_BLOCK_ENTITY = "rbasamoyai.createbigcannons.cannons.big_cannons.IBigCannonBlockEntity";
    private static final String BIG_CANNON_PROPELLANT_BLOCK = "rbasamoyai.createbigcannons.munitions.big_cannon.propellant.BigCannonPropellantBlock";
    private static final String PROJECTILE_BLOCK = "rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlock";
    private static final String INTEGRATED_PROPELLANT_PROJECTILE = "rbasamoyai.createbigcannons.munitions.big_cannon.propellant.IntegratedPropellantProjectile";
    private static final String PROPELLANT_CONTEXT = "rbasamoyai.createbigcannons.cannon_control.contraption.MountedBigCannonContraption$PropellantContext";
    private static final Map<String, Optional<Class<?>>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Optional<Method>> PUBLIC_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<MethodKey, Optional<Method>> DECLARED_METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<FieldKey, Optional<Field>> PUBLIC_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<FieldKey, Optional<Field>> DECLARED_FIELD_CACHE = new ConcurrentHashMap<>();

    private TargetControllerCbcCompat() {
    }

    public static Optional<CannonState> inspect(ServerLevel level, BlockPos mountPos) {
        return inspect(level, mountPos, null, false);
    }

    public static Optional<CannonState> inspectForTargeting(
            ServerLevel level,
            BlockPos mountPos,
            BallisticProfile cachedBigCannonBallistics
    ) {
        return inspectForTargeting(level, mountPos, cachedBigCannonBallistics, null);
    }

    public static Optional<CannonState> inspectForTargeting(
            ServerLevel level,
            BlockPos mountPos,
            BallisticProfile cachedBigCannonBallistics,
            CannonKind cachedKind
    ) {
        return inspect(level, mountPos, cachedBigCannonBallistics, true, cachedKind);
    }

    public static Optional<CannonState> inspectForPreciseTargeting(
            ServerLevel level,
            BlockPos mountPos,
            BallisticProfile cachedBallistics,
            CannonKind cachedKind
    ) {
        return inspect(level, mountPos, cachedBallistics, false, cachedKind);
    }

    private static Optional<CannonState> inspect(
            ServerLevel level,
            BlockPos mountPos,
            BallisticProfile cachedBigCannonBallistics,
            boolean fastAutocannonBallistics
    ) {
        return inspect(level, mountPos, cachedBigCannonBallistics, fastAutocannonBallistics, null);
    }

    private static Optional<CannonState> inspect(
            ServerLevel level,
            BlockPos mountPos,
            BallisticProfile cachedBigCannonBallistics,
            boolean fastAutocannonBallistics,
            CannonKind cachedKind
    ) {
        BlockEntity blockEntity = level.getBlockEntity(mountPos);
        if (blockEntity == null) {
            return Optional.empty();
        }
        Class<?> type = blockEntity.getClass();
        if (isInstance(type, CANNON_MOUNT_BE)) {
            return inspectAdjustableMount(level, blockEntity, mountPos, cachedBigCannonBallistics, fastAutocannonBallistics, cachedKind);
        }
        if (isInstance(type, FIXED_CANNON_MOUNT_BE)) {
            return inspectFixedMount(level, blockEntity, mountPos, cachedBigCannonBallistics, fastAutocannonBallistics, cachedKind);
        }
        return Optional.empty();
    }

    public static boolean applyAdjustableMountAngles(ServerLevel level, BlockPos mountPos, float yawDegrees, float logicalPitchDegrees) {
        return applyAdjustableMountAngles(level, mountPos, yawDegrees, logicalPitchDegrees, null);
    }

    public static boolean applyAdjustableMountAngles(
            ServerLevel level,
            BlockPos mountPos,
            float yawDegrees,
            float logicalPitchDegrees,
            CannonKind kind
    ) {
        BlockEntity blockEntity = level.getBlockEntity(mountPos);
        if (blockEntity == null || !isInstance(blockEntity.getClass(), CANNON_MOUNT_BE)) {
            return false;
        }
        Object contraptionEntity = invokeObject(blockEntity, "getContraption");
        if (contraptionEntity == null) {
            return false;
        }
        invokeVoid(blockEntity, "setYaw", new Class<?>[] { float.class }, new Object[] { yawDegrees });
        // CBC's cannonPitch is already a logical elevation angle: positive means up for every
        // horizontal initial orientation. CannonMountBlockEntity.applyRotation applies the
        // EAST/WEST/NORTH/SOUTH sign when it copies cannonPitch to contraption.pitch.
        float cbcPitch = kind == CannonKind.DROP_MORTAR
                ? dropMortarCbcPitch(blockEntity, logicalPitchDegrees)
                : logicalPitchDegrees;
        invokeVoid(blockEntity, "setPitch", new Class<?>[] { float.class }, new Object[] { cbcPitch });
        invokeVoid(blockEntity, "notifyUpdate");
        return true;
    }

    public static boolean applyAdjustableMountWorldAngles(
            ServerLevel level,
            CannonState cannonState,
            float yawDegrees,
            float worldPitchDegrees
    ) {
        return applyAdjustableMountAngles(
                level, cannonState.mountPos(), yawDegrees, worldPitchDegrees, cannonState.kind());
    }

    private static Optional<CannonState> inspectAdjustableMount(
            ServerLevel level,
            Object mount,
            BlockPos mountPos,
            BallisticProfile cachedBigCannonBallistics,
            boolean fastAutocannonBallistics,
            CannonKind cachedKind
    ) {
        Object contraptionEntity = invokeObject(mount, "getContraption");
        float yaw = contraptionEntity == null
                ? invokeFloat(mount, "getYawOffset", new Class<?>[] { float.class }, new Object[] { 1.0F })
                : readFloatField(contraptionEntity, "yaw");
        float logicalPitch = contraptionEntity == null
                ? invokeFloat(mount, "getPitchOffset", new Class<?>[] { float.class }, new Object[] { 1.0F })
                : readFloatFieldRecursive(mount, "cannonPitch");
        Object contraption = contraptionEntity == null ? null : invokeObject(contraptionEntity, "getContraption");
        CannonKind kind = cachedKind != null && contraptionEntity != null && contraption != null
                ? cachedKind
                : cannonKind(mount);
        Vec3 muzzle = muzzleOrigin(contraptionEntity, contraption, kind, mountPos);
        float physicalPitch = fastAutocannonBallistics
                ? logicalPitch
                : physicalPitchDegrees(muzzleDirection(contraptionEntity, contraption, kind));
        float currentPitch = kind == CannonKind.DROP_MORTAR ? physicalPitch : logicalPitch;
        return Optional.of(new CannonState(mountPos, muzzle,
                yaw, currentPitch, physicalPitch, 1.0F,
                kind, kind != CannonKind.NONE,
                inspectBallistics(level, contraption, kind, cachedBigCannonBallistics, fastAutocannonBallistics)));
    }

    private static Optional<CannonState> inspectFixedMount(
            ServerLevel level,
            Object mount,
            BlockPos mountPos,
            BallisticProfile cachedBigCannonBallistics,
            boolean fastAutocannonBallistics,
            CannonKind cachedKind
    ) {
        Direction direction = invokeDirection(mount, "getContraptionDirection");
        float yaw = switch (direction) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        Object contraptionEntity = invokeObject(mount, "getContraption");
        Object contraption = contraptionEntity == null ? null : invokeObject(contraptionEntity, "getContraption");
        CannonKind kind = cachedKind != null && contraptionEntity != null && contraption != null
                ? cachedKind
                : cannonKind(mount);
        Vec3 muzzle = muzzleOrigin(contraptionEntity, contraption, kind, mountPos);
        float physicalPitch = fastAutocannonBallistics
                ? 0.0F
                : physicalPitchDegrees(muzzleDirection(contraptionEntity, contraption, kind));
        return Optional.of(new CannonState(mountPos, muzzle,
                yaw, 0.0F, physicalPitch, 1.0F,
                kind, kind != CannonKind.NONE,
                inspectBallistics(level, contraption, kind, cachedBigCannonBallistics, fastAutocannonBallistics)));
    }

    private static CannonKind cannonKind(Object mount) {
        Object contraptionEntity = invokeObject(mount, "getContraption");
        if (contraptionEntity == null) {
            return CannonKind.NONE;
        }
        Object contraption = invokeObject(contraptionEntity, "getContraption");
        if (contraption == null) {
            return CannonKind.UNKNOWN_CBC;
        }
        Class<?> type = contraption.getClass();
        if (isInstance(type, MOUNTED_BIG_CANNON)) {
            return invokeBoolean(contraption, "isDropMortar")
                    ? CannonKind.DROP_MORTAR
                    : CannonKind.BIG_CANNON;
        }
        if (isInstance(type, MOUNTED_AUTOCANNON)) {
            return CannonKind.AUTOCANNON;
        }
        return CannonKind.UNKNOWN_CBC;
    }

    private static BallisticProfile inspectBallistics(
            ServerLevel level,
            Object contraption,
            CannonKind kind,
            BallisticProfile cachedBigCannonBallistics,
            boolean fastAutocannonBallistics
    ) {
        if (contraption == null) {
            return BallisticProfile.unknown("no-contraption");
        }
        try {
            return switch (kind) {
                case AUTOCANNON -> cachedBigCannonBallistics != null && cachedBigCannonBallistics.available()
                        ? cachedBigCannonBallistics
                        : fastAutocannonBallistics
                        ? inspectAutocannonBallisticsFast(contraption)
                        : inspectAutocannonBallistics(level, contraption);
                case BIG_CANNON -> cachedBigCannonBallistics != null && cachedBigCannonBallistics.available()
                        ? cachedBigCannonBallistics
                        : inspectBigCannonBallistics(level, contraption);
                case DROP_MORTAR -> dropMortarBallistics();
                default -> BallisticProfile.unknown("unsupported-cannon-kind");
            };
        } catch (RuntimeException exception) {
            return BallisticProfile.unknown("inspect-failed");
        }
    }

    private static Vec3 muzzleOrigin(Object contraptionEntity, Object contraption, CannonKind kind, BlockPos fallbackMountPos) {
        if (contraptionEntity == null || contraption == null) {
            return Vec3.atCenterOf(fallbackMountPos).add(0.0, 2.0, 0.0);
        }
        Direction orientation = readDirectionFieldRecursive(contraption, "initialOrientation");
        BlockPos startPos = readBlockPosFieldRecursive(contraption, "startPos");
        Map<?, ?> presentBlockEntities = readMapFieldRecursive(contraption, "presentBlockEntities");
        if (orientation == null || startPos == null || presentBlockEntities == null) {
            return Vec3.atCenterOf(fallbackMountPos).add(0.0, 2.0, 0.0);
        }
        BlockPos cursor = startPos.immutable();
        BlockPos lastCannonBlock = null;
        String cannonBlockEntityType = kind == CannonKind.AUTOCANNON ? I_AUTOCANNON_BLOCK_ENTITY : I_BIG_CANNON_BLOCK_ENTITY;
        for (int i = 0; i < 256; i++) {
            Object blockEntity = presentBlockEntities.get(cursor);
            if (blockEntity == null || !isInstance(blockEntity.getClass(), cannonBlockEntityType)) {
                break;
            }
            lastCannonBlock = cursor.immutable();
            cursor = cursor.relative(orientation);
        }
        if (lastCannonBlock == null) {
            return Vec3.atCenterOf(fallbackMountPos).add(0.0, 2.0, 0.0);
        }
        Vec3 muzzleCenter = invokeVec3(contraptionEntity, "toGlobalVector",
                new Class<?>[] { Vec3.class, float.class },
                new Object[] { Vec3.atCenterOf(lastCannonBlock.relative(orientation)), 0.0F });
        Vec3 localCenter = invokeVec3(contraptionEntity, "toGlobalVector",
                new Class<?>[] { Vec3.class, float.class },
                new Object[] { Vec3.atCenterOf(BlockPos.ZERO), 0.0F });
        if (muzzleCenter == null || localCenter == null) {
            return Vec3.atCenterOf(fallbackMountPos).add(0.0, 2.0, 0.0);
        }
        Vec3 direction = muzzleCenter.subtract(localCenter);
        if (direction.lengthSqr() < 0.0001) {
            return muzzleCenter;
        }
        return muzzleCenter.subtract(direction.normalize().scale(2.0));
    }

    private static Vec3 muzzleDirection(Object contraptionEntity, Object contraption, CannonKind kind) {
        if (contraptionEntity == null || contraption == null) {
            return Vec3.ZERO;
        }
        Direction orientation = readDirectionFieldRecursive(contraption, "initialOrientation");
        BlockPos startPos = readBlockPosFieldRecursive(contraption, "startPos");
        Map<?, ?> presentBlockEntities = readMapFieldRecursive(contraption, "presentBlockEntities");
        if (orientation == null || startPos == null || presentBlockEntities == null) {
            return Vec3.ZERO;
        }
        BlockPos cursor = startPos.immutable();
        BlockPos lastCannonBlock = null;
        String cannonBlockEntityType =
                kind == CannonKind.AUTOCANNON ? I_AUTOCANNON_BLOCK_ENTITY : I_BIG_CANNON_BLOCK_ENTITY;
        for (int i = 0; i < 256; i++) {
            Object blockEntity = presentBlockEntities.get(cursor);
            if (blockEntity == null || !isInstance(blockEntity.getClass(), cannonBlockEntityType)) {
                break;
            }
            lastCannonBlock = cursor.immutable();
            cursor = cursor.relative(orientation);
        }
        if (lastCannonBlock == null) {
            return Vec3.ZERO;
        }
        Vec3 muzzleCenter = invokeVec3(contraptionEntity, "toGlobalVector",
                new Class<?>[] { Vec3.class, float.class },
                new Object[] { Vec3.atCenterOf(lastCannonBlock.relative(orientation)), 0.0F });
        Vec3 localCenter = invokeVec3(contraptionEntity, "toGlobalVector",
                new Class<?>[] { Vec3.class, float.class },
                new Object[] { Vec3.atCenterOf(BlockPos.ZERO), 0.0F });
        return muzzleCenter == null || localCenter == null
                ? Vec3.ZERO
                : muzzleCenter.subtract(localCenter).normalize();
    }

    private static float physicalPitchDegrees(Vec3 direction) {
        if (direction.lengthSqr() < 1.0E-6) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, direction.normalize().y))));
    }

    private static float dropMortarCbcPitch(Object mount, float physicalElevationDegrees) {
        Object contraptionEntity = invokeObject(mount, "getContraption");
        Object contraption = contraptionEntity == null ? null : invokeObject(contraptionEntity, "getContraption");
        Direction orientation = contraption == null ? null : readDirectionFieldRecursive(contraption, "initialOrientation");
        if (orientation == Direction.UP) {
            return physicalElevationDegrees - 90.0F;
        }
        if (orientation == Direction.DOWN) {
            return 90.0F - physicalElevationDegrees;
        }
        return physicalElevationDegrees;
    }

    private static BallisticProfile inspectAutocannonBallistics(ServerLevel level, Object contraption) {
        Object material = readFieldRecursive(contraption, "cannonMaterial");
        Object properties = invokeObject(material, "properties");
        if (properties == null) {
            return BallisticProfile.unknown("missing-autocannon-material");
        }
        ItemStack stack = autocannonInputStack(contraption);
        if (stack.isEmpty() || !isInstance(stack.getItem().getClass(), AUTOCANNON_AMMO_ITEM)) {
            return BallisticProfile.unknown("no-autocannon-ammo");
        }

        int barrelCount = autocannonBarrelCount(contraption, stack);
        int speedIncreases = Math.min(barrelCount, Math.max(0, invokeInt(properties, "maxSpeedIncreases")));
        double speed = invokeFloat(properties, "baseSpeed") + speedIncreases * invokeFloat(properties, "speedIncreasePerBarrel");
        int lifetime = Math.max(1, invokeInt(properties, "projectileLifetime"));
        Object projectile = invokeObject(stack.getItem(), "getAutocannonProjectile",
                new Class<?>[] { ItemStack.class, net.minecraft.world.level.Level.class }, new Object[] { stack, level });
        BallisticNumbers numbers = ballisticNumbers(projectile);
        return new BallisticProfile(true, "autocannon", speed, numbers.gravity(), numbers.drag(), numbers.quadraticDrag(),
                lifetime, barrelCount, stack.getItem().toString(), false);
    }

    private static BallisticProfile inspectAutocannonBallisticsFast(Object contraption) {
        Object material = readFieldRecursive(contraption, "cannonMaterial");
        Object properties = invokeObject(material, "properties");
        if (properties == null) {
            return BallisticProfile.unknown("missing-autocannon-material");
        }
        int barrelCount = Math.max(0, readIntFieldRecursive(contraption, "frontExtensionLength"));
        int speedIncreases = Math.min(barrelCount, Math.max(0, invokeInt(properties, "maxSpeedIncreases")));
        double speed = invokeFloat(properties, "baseSpeed") + speedIncreases * invokeFloat(properties, "speedIncreasePerBarrel");
        int lifetime = Math.max(1, invokeInt(properties, "projectileLifetime"));
        BallisticNumbers numbers = BallisticNumbers.DEFAULT;
        return new BallisticProfile(true, "autocannon:no_ammo_check", speed, numbers.gravity(), numbers.drag(), numbers.quadraticDrag(),
                lifetime, barrelCount, "autocannon", false);
    }

    private static BallisticProfile dropMortarBallistics() {
        return new BallisticProfile(
                true,
                "drop_mortar",
                6.0,
                0.05,
                0.01,
                false,
                0,
                0,
                "createbigcannons:drop_mortar_shell",
                true);
    }

    private static ItemStack autocannonInputStack(Object contraption) {
        ItemStack breechStack = autocannonBreechInputStack(contraption);
        if (!breechStack.isEmpty()) {
            return breechStack;
        }
        Object storage = invokeObject(contraption, "getItemStorage");
        if (storage == null) {
            return ItemStack.EMPTY;
        }
        ItemStack input = invokeItemStack(storage, "getStackInSlot",
                new Class<?>[] { int.class }, new Object[] { 1 });
        if (!input.isEmpty()) {
            return input;
        }
        return invokeItemStack(storage, "getStackInSlot",
                new Class<?>[] { int.class }, new Object[] { 0 });
    }

    private static ItemStack autocannonBreechInputStack(Object contraption) {
        BlockPos startPos = readBlockPosFieldRecursive(contraption, "startPos");
        Map<?, ?> presentBlockEntities = readMapFieldRecursive(contraption, "presentBlockEntities");
        if (startPos == null || presentBlockEntities == null) {
            return ItemStack.EMPTY;
        }
        Object breech = presentBlockEntities.get(startPos);
        if (breech == null || !isInstance(breech.getClass(), ABSTRACT_AUTOCANNON_BREECH_BE)) {
            return ItemStack.EMPTY;
        }
        Object inputBuffer = invokeObject(breech, "getInputBuffer");
        Object peek = invokeObject(inputBuffer, "peek");
        if (peek instanceof ItemStack stack && !stack.isEmpty()) {
            return stack;
        }
        ItemStack magazine = invokeItemStack(breech, "getMagazine", new Class<?>[0], new Object[0]);
        if (magazine.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack main = invokeStaticItemStack(AUTOCANNON_AMMO_CONTAINER_ITEM, "getMainAmmoStack",
                new Class<?>[] { ItemStack.class }, new Object[] { magazine });
        if (!main.isEmpty()) {
            return main;
        }
        return invokeStaticItemStack(AUTOCANNON_AMMO_CONTAINER_ITEM, "getTracerAmmoStack",
                new Class<?>[] { ItemStack.class }, new Object[] { magazine });
    }

    private static int autocannonBarrelCount(Object contraption, ItemStack stack) {
        Direction orientation = readDirectionFieldRecursive(contraption, "initialOrientation");
        BlockPos startPos = readBlockPosFieldRecursive(contraption, "startPos");
        Map<?, ?> presentBlockEntities = readMapFieldRecursive(contraption, "presentBlockEntities");
        if (orientation == null || startPos == null || presentBlockEntities == null) {
            return Math.max(0, readIntFieldRecursive(contraption, "frontExtensionLength"));
        }

        int count = 0;
        BlockPos cursor = startPos.relative(orientation);
        for (int i = 0; i < 256; i++) {
            Object blockEntity = presentBlockEntities.get(cursor);
            if (blockEntity == null || !isInstance(blockEntity.getClass(), I_AUTOCANNON_BLOCK_ENTITY)) {
                break;
            }
            Object behavior = invokeObject(blockEntity, "cannonBehavior");
            Object canLoad = invokeObject(behavior, "canLoadItem",
                    new Class<?>[] { ItemStack.class }, new Object[] { stack });
            if (!(canLoad instanceof Boolean bool) || !bool) {
                break;
            }
            count++;
            cursor = cursor.relative(orientation);
        }
        return count;
    }

    private static BallisticProfile inspectBigCannonBallistics(ServerLevel level, Object contraption) {
        Direction orientation = readDirectionFieldRecursive(contraption, "initialOrientation");
        BlockPos startPos = readBlockPosFieldRecursive(contraption, "startPos");
        Object context = newPropellantContext();
        Map<?, ?> presentBlockEntities = readMapFieldRecursive(contraption, "presentBlockEntities");
        if (orientation == null || startPos == null || context == null || presentBlockEntities == null) {
            return BallisticProfile.unknown("missing-big-cannon-layout");
        }

        BlockPos cursor = startPos.immutable();
        Object projectile = null;
        String projectileName = "none";
        int barrels = 0;
        for (int i = 0; i < 256; i++) {
            Object blockEntity = presentBlockEntities.get(cursor);
            if (blockEntity == null || !isInstance(blockEntity.getClass(), I_BIG_CANNON_BLOCK_ENTITY)) {
                break;
            }
            Object behavior = invokeObject(blockEntity, "cannonBehavior");
            StructureTemplate.StructureBlockInfo info = invokeBlockInfo(behavior, "block");
            if (info == null) {
                cursor = cursor.relative(orientation);
                continue;
            }
            BlockState infoState = info.state();
            Object block = infoState.getBlock();
            if (isInstance(block.getClass(), BIG_CANNON_PROPELLANT_BLOCK) && !isInstance(block.getClass(), PROJECTILE_BLOCK)) {
                invokeObject(context, "addPropellant",
                        new Class<?>[] { classForName(BIG_CANNON_PROPELLANT_BLOCK), StructureTemplate.StructureBlockInfo.class, Direction.class },
                        new Object[] { block, info, orientation });
            } else if (isInstance(block.getClass(), PROJECTILE_BLOCK) && projectile == null) {
                List<StructureTemplate.StructureBlockInfo> projectileBlocks = collectProjectileBlocks(presentBlockEntities, cursor, orientation);
                projectile = invokeObject(block, "getProjectile",
                        new Class<?>[] { net.minecraft.world.level.Level.class, List.class },
                        new Object[] { level, projectileBlocks });
                projectileName = block.toString();
                if (projectile != null) {
                    setFloatField(context, "chargesUsed", readFloatField(context, "chargesUsed") + invokeFloat(projectile, "addedChargePower"));
                    float minimumCharge = invokeFloat(projectile, "minimumChargePower");
                    if (readFloatField(context, "chargesUsed") < minimumCharge) {
                        setFloatField(context, "chargesUsed", minimumCharge);
                    }
                }
            } else if (projectile != null && infoState.isAir()) {
                barrels++;
            }
            cursor = cursor.relative(orientation);
        }

        if (projectile == null) {
            return BallisticProfile.unknown("no-big-cannon-projectile");
        }
        if (isInstance(projectile.getClass(), INTEGRATED_PROPELLANT_PROJECTILE)) {
            List<StructureTemplate.StructureBlockInfo> projectileBlocks = collectProjectileBlocks(presentBlockEntities, startPos, orientation);
            if (!projectileBlocks.isEmpty()) {
                invokeObject(context, "addIntegratedPropellant",
                        new Class<?>[] { classForName(INTEGRATED_PROPELLANT_PROJECTILE), StructureTemplate.StructureBlockInfo.class, Direction.class },
                        new Object[] { projectile, projectileBlocks.get(0), orientation });
            }
        }
        double speed = Math.max(0.001, readFloatField(context, "chargesUsed"));
        BallisticNumbers numbers = ballisticNumbers(projectile);
        return new BallisticProfile(true, "big_cannon_flat", speed, numbers.gravity(), numbers.drag(), numbers.quadraticDrag(),
                0, barrels, projectileName, false);
    }

    private static List<StructureTemplate.StructureBlockInfo> collectProjectileBlocks(Map<?, ?> presentBlockEntities, BlockPos start, Direction orientation) {
        ArrayList<StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        BlockPos cursor = start;
        for (int i = 0; i < 16; i++) {
            Object blockEntity = presentBlockEntities.get(cursor);
            if (blockEntity == null || !isInstance(blockEntity.getClass(), I_BIG_CANNON_BLOCK_ENTITY)) {
                break;
            }
            StructureTemplate.StructureBlockInfo info = invokeBlockInfo(invokeObject(blockEntity, "cannonBehavior"), "block");
            if (info == null || !isInstance(info.state().getBlock().getClass(), PROJECTILE_BLOCK)) {
                break;
            }
            blocks.add(info);
            cursor = cursor.relative(orientation);
        }
        return blocks;
    }

    private static BallisticNumbers ballisticNumbers(Object projectile) {
        Object ballistic = invokeObjectRecursive(projectile, "getBallisticProperties");
        if (ballistic == null) {
            return BallisticNumbers.DEFAULT;
        }
        return new BallisticNumbers(
                Math.abs(invokeDouble(ballistic, "gravity")),
                Math.max(0.0, invokeDouble(ballistic, "drag")),
                invokeBoolean(ballistic, "isQuadraticDrag"));
    }

    private static Object newPropellantContext() {
        Class<?> type = classForName(PROPELLANT_CONTEXT);
        if (type == null) {
            return null;
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean isInstance(Class<?> actualType, String expectedName) {
        Class<?> expectedType = classForName(expectedName);
        return expectedType != null && expectedType.isAssignableFrom(actualType);
    }

    private static Class<?> classForName(String expectedName) {
        return CLASS_CACHE.computeIfAbsent(expectedName, name -> {
            try {
                return Optional.of(Class.forName(name));
            } catch (ClassNotFoundException ignored) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static float invokeFloat(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        Object value = invokeObject(target, methodName, parameterTypes, args);
        return value instanceof Number number ? number.floatValue() : 0.0F;
    }

    private static float invokeFloat(Object target, String methodName) {
        return invokeFloat(target, methodName, new Class<?>[0], new Object[0]);
    }

    private static int invokeInt(Object target, String methodName) {
        Object value = invokeObject(target, methodName);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double invokeDouble(Object target, String methodName) {
        Object value = invokeObject(target, methodName);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static boolean invokeBoolean(Object target, String methodName) {
        Object value = invokeObject(target, methodName);
        return value instanceof Boolean bool && bool;
    }

    private static Direction invokeDirection(Object target, String methodName) {
        Object value = invokeObject(target, methodName);
        return value instanceof Direction direction ? direction : Direction.NORTH;
    }

    private static StructureTemplate.StructureBlockInfo invokeBlockInfo(Object target, String methodName) {
        Object value = invokeObject(target, methodName);
        if (value instanceof StructureTemplate.StructureBlockInfo info) {
            return info;
        }
        if (value instanceof Optional<?> optional
                && optional.orElse(null) instanceof StructureTemplate.StructureBlockInfo info) {
            return info;
        }
        Object contained = readFieldRecursive(target, "containedBlockInfo");
        if (contained instanceof StructureTemplate.StructureBlockInfo info) {
            return info;
        }
        return contained instanceof Optional<?> optional
                && optional.orElse(null) instanceof StructureTemplate.StructureBlockInfo info
                ? info
                : null;
    }

    private static Object invokeObject(Object target, String methodName) {
        return invokeObject(target, methodName, new Class<?>[0], new Object[0]);
    }

    private static Object invokeObject(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = publicMethod(target.getClass(), methodName, parameterTypes).orElse(null);
            if (method == null) {
                return null;
            }
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | NullPointerException ignored) {
            return null;
        }
    }

    private static Vec3 invokeVec3(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        Object value = invokeObject(target, methodName, parameterTypes, args);
        return value instanceof Vec3 vec ? vec : null;
    }

    private static Object invokeObjectRecursive(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = declaredMethod(target.getClass(), methodName, new Class<?>[0]).orElse(null);
            if (method == null) {
                return null;
            }
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static ItemStack invokeItemStack(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        Object value = invokeObject(target, methodName, parameterTypes, args);
        return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
    }

    private static ItemStack invokeStaticItemStack(String className, String methodName, Class<?>[] parameterTypes, Object[] args) {
        Class<?> type = classForName(className);
        if (type == null) {
            return ItemStack.EMPTY;
        }
        try {
            Method method = publicMethod(type, methodName, parameterTypes).orElse(null);
            if (method == null) {
                return ItemStack.EMPTY;
            }
            Object value = method.invoke(null, args);
            return value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (IllegalAccessException | InvocationTargetException | NullPointerException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void invokeVoid(Object target, String methodName) {
        invokeVoid(target, methodName, new Class<?>[0], new Object[0]);
    }

    private static void invokeVoid(Object target, String methodName, Class<?>[] parameterTypes, Object[] args) {
        invokeObject(target, methodName, parameterTypes, args);
    }

    private static float readFloatField(Object target, String fieldName) {
        if (target == null) {
            return 0.0F;
        }
        try {
            Field field = publicField(target.getClass(), fieldName).orElse(null);
            if (field == null) {
                return 0.0F;
            }
            return field.getFloat(target);
        } catch (IllegalAccessException ignored) {
            return 0.0F;
        }
    }

    private static void setFloatField(Object target, String fieldName, float value) {
        if (target == null) {
            return;
        }
        try {
            Field field = publicField(target.getClass(), fieldName).orElse(null);
            if (field == null) {
                return;
            }
            field.setFloat(target, value);
        } catch (IllegalAccessException ignored) {
        }
    }

    private static Object readFieldRecursive(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Field field = declaredField(target.getClass(), fieldName).orElse(null);
            return field == null ? null : field.get(target);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Optional<Method> publicMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        return PUBLIC_METHOD_CACHE.computeIfAbsent(MethodKey.of(owner, name, parameterTypes), key -> {
            try {
                return Optional.of(key.owner().getMethod(key.name(), key.parameterTypes().toArray(Class<?>[]::new)));
            } catch (NoSuchMethodException | SecurityException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Method> declaredMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        Class<?> type = owner;
        while (type != null) {
            Optional<Method> method = DECLARED_METHOD_CACHE.computeIfAbsent(MethodKey.of(type, name, parameterTypes), key -> {
                try {
                    Method found = key.owner().getDeclaredMethod(key.name(), key.parameterTypes().toArray(Class<?>[]::new));
                    found.setAccessible(true);
                    return Optional.of(found);
                } catch (NoSuchMethodException | SecurityException ignored) {
                    return Optional.empty();
                }
            });
            if (method.isPresent()) {
                return method;
            }
            type = type.getSuperclass();
        }
        return Optional.empty();
    }

    private static Optional<Field> publicField(Class<?> owner, String name) {
        return PUBLIC_FIELD_CACHE.computeIfAbsent(new FieldKey(owner, name), key -> {
            try {
                return Optional.of(key.owner().getField(key.name()));
            } catch (NoSuchFieldException | SecurityException ignored) {
                return Optional.empty();
            }
        });
    }

    private static Optional<Field> declaredField(Class<?> owner, String name) {
        Class<?> type = owner;
        while (type != null) {
            Optional<Field> field = DECLARED_FIELD_CACHE.computeIfAbsent(new FieldKey(type, name), key -> {
                try {
                    Field found = key.owner().getDeclaredField(key.name());
                    found.setAccessible(true);
                    return Optional.of(found);
                } catch (NoSuchFieldException | SecurityException ignored) {
                    return Optional.empty();
                }
            });
            if (field.isPresent()) {
                return field;
            }
            type = type.getSuperclass();
        }
        return Optional.empty();
    }

    private static int readIntFieldRecursive(Object target, String fieldName) {
        Object value = readFieldRecursive(target, fieldName);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static float readFloatFieldRecursive(Object target, String fieldName) {
        Object value = readFieldRecursive(target, fieldName);
        return value instanceof Number number ? number.floatValue() : 0.0F;
    }

    private static Direction readDirectionFieldRecursive(Object target, String fieldName) {
        Object value = readFieldRecursive(target, fieldName);
        return value instanceof Direction direction ? direction : null;
    }

    private static BlockPos readBlockPosFieldRecursive(Object target, String fieldName) {
        Object value = readFieldRecursive(target, fieldName);
        return value instanceof BlockPos pos ? pos : null;
    }

    private static Map<?, ?> readMapFieldRecursive(Object target, String fieldName) {
        Object value = readFieldRecursive(target, fieldName);
        return value instanceof Map<?, ?> map ? map : null;
    }

    public record CannonState(
            BlockPos mountPos,
            Vec3 muzzleOrigin,
            float currentYawDegrees,
            float currentPitchDegrees,
            float physicalPitchDegrees,
            float worldToLogicalPitchMultiplier,
            CannonKind kind,
            boolean fireCapableContraptionPresent,
            BallisticProfile ballistics
    ) {
    }

    public record BallisticProfile(
            boolean available,
            String mode,
            double speedBlocksPerTick,
            double gravityBlocksPerTickSquared,
            double drag,
            boolean quadraticDrag,
            int lifetimeTicks,
            int barrelCount,
            String ammunition,
            boolean highArcEnabled
    ) {
        private static BallisticProfile unknown(String mode) {
            return new BallisticProfile(false, mode, 0.0, 0.0, 0.0, false, 0, 0, "unknown", false);
        }

        public boolean hasLifetimeLimit() {
            return this.lifetimeTicks > 0;
        }
    }

    private record BallisticNumbers(double gravity, double drag, boolean quadraticDrag) {
        private static final BallisticNumbers DEFAULT = new BallisticNumbers(0.05, 0.0, false);
    }

    private record MethodKey(Class<?> owner, String name, List<Class<?>> parameterTypes) {
        private static MethodKey of(Class<?> owner, String name, Class<?>[] parameterTypes) {
            return new MethodKey(owner, name, List.of(parameterTypes));
        }
    }

    private record FieldKey(Class<?> owner, String name) {
    }

    public enum CannonKind {
        NONE,
        BIG_CANNON,
        DROP_MORTAR,
        AUTOCANNON,
        UNKNOWN_CBC
    }
}
