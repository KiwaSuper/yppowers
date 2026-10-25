package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.entity.ThorMaceEntity;
import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Thor: a soulbound enchanted mace (Density 5, Wind Burst 1, Mending, Unbreaking 3).
 * <p>
 * - The mace never drops on death and is re-issued on respawn/join/assign.
 * - Holding right-click charges a Riptide III launch that works out of water.
 * - Pressing G throws the mace; where it lands it strikes lightning and digs a crater,
 *   then the mace flies back to its owner like a Loyalty trident.
 */
public class ThorPower extends Power {
    public static final String MACE_FLAG = "YPThorMace";

    /** Owners whose mace is currently in flight as a {@link ThorMaceEntity}. */
    private final Map<UUID, ThorMaceEntity> thrownMaces = new HashMap<>();

    @Override
    public PowerType getType() {
        return PowerType.THOR;
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureMace(player);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        ensureMace(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        // A mace that is mid-flight when its owner dies is lost: discard it and re-issue a fresh one.
        ThorMaceEntity flying = thrownMaces.remove(player.getUuid());
        if (flying != null && !flying.isRemoved()) {
            flying.discard();
        }
        ensureMace(player);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        ThorMaceEntity flying = thrownMaces.remove(player.getUuid());
        if (flying != null && !flying.isRemoved()) {
            flying.discard();
        }
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        handleThrowOrTeleport(player);
    }

    private void handleThrowOrTeleport(ServerPlayerEntity player) {
        ThorMaceEntity existing = thrownMaces.get(player.getUuid());
        if (existing != null && !existing.isRemoved()) {
            // The mace is already out: if it is still flying, teleport to it.
            if (!existing.isReturning()) {
                teleportToMace(player, existing);
            }
            return;
        }
        thrownMaces.remove(player.getUuid()); // clear a stale entry if any
        throwMace(player);
    }

    private void teleportToMace(ServerPlayerEntity player, ThorMaceEntity mace) {
        player.teleport((ServerWorld) player.getEntityWorld(),
                mace.getX(), mace.getY(), mace.getZ(),
                Set.of(), player.getYaw(), player.getPitch(), false);
        mace.startReturning(); // the mace comes back to you instead of continuing to fly
        player.getEntityWorld().playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return isThorMace(stack);
    }

    public static boolean isThorMace(ItemStack stack) {
        return stack.isOf(Items.MACE) && YPItemData.hasFlag(stack, MACE_FLAG);
    }

    /** Builds the enchanted Thor mace stack. */
    public static ItemStack createThorMace(ServerPlayerEntity player) {
        ItemStack mace = new ItemStack(Items.MACE);
        applyThorEnchantments(player, mace);
        YPItemData.setFlag(mace, MACE_FLAG, true);
        return mace;
    }

    /** Applies the fixed enchantment spec, resetting any older levels (e.g. Wind Burst bumps). */
    private static void applyThorEnchantments(ServerPlayerEntity player, ItemStack mace) {
        var enchRegistry = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        mace.set(net.minecraft.component.DataComponentTypes.ENCHANTMENTS,
                net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);
        EnchantmentHelper.apply(mace, builder -> {
            builder.add(enchRegistry.getOrThrow(Enchantments.DENSITY), 5);
            builder.add(enchRegistry.getOrThrow(Enchantments.WIND_BURST), 2);
            builder.add(enchRegistry.getOrThrow(Enchantments.MENDING), 1);
            builder.add(enchRegistry.getOrThrow(Enchantments.UNBREAKING), 3);
        });
    }

    private void throwMace(ServerPlayerEntity player) {
        ItemStack held = player.getMainHandStack();
        if (!isThorMace(held)) return;

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        ItemStack mace = held.copy();
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);

        ThorMaceEntity entity = new ThorMaceEntity(world, player, mace);
        Vec3d look = player.getRotationVec(1.0f);
        entity.setVelocity(look.multiply(YPConfig.getInstance().thor_throw_speed));
        entity.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
        world.spawnEntity(entity);
        thrownMaces.put(player.getUuid(), entity);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private void ensureMace(ServerPlayerEntity player) {
        ThorMaceEntity existing = thrownMaces.get(player.getUuid());
        if (existing != null && !existing.isRemoved()) return; // mace is still out there
        thrownMaces.remove(player.getUuid());

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isThorMace(stack)) {
                applyThorEnchantments(player, stack); // keep enchantments up to date
                return;
            }
        }
        player.giveOrDropStack(createThorMace(player));
    }

    /** Called by {@link ThorMaceEntity} when it is handed back to its owner. */
    public void onMaceReturned(UUID owner) {
        thrownMaces.remove(owner);
    }

    /** Called by {@link ThorMaceEntity} when it is discarded without returning (owner gone). */
    public void onMaceLost(UUID owner) {
        thrownMaces.remove(owner);
    }

    // ----- impact effects (invoked by ThorMaceEntity) -----

    public static void doImpact(ServerWorld world, ServerPlayerEntity owner, Vec3d pos) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
            // Cosmetic: the bolt renders and thunders but applies no vanilla damage, fire or
            // mob conversion. Damage is handled by damageArea below so allies are skipped.
            bolt.setCosmetic(true);
            if (owner != null) bolt.setChanneler(owner);
            world.spawnEntity(bolt);
        }
        makeCrater(world, BlockPos.ofFloored(pos));
        damageArea(world, owner, pos);
    }

    private static void makeCrater(ServerWorld world, BlockPos center) {
        YPConfig cfg = YPConfig.getInstance();
        if (!cfg.thor_crater_enabled) return;
        int radius = cfg.thor_crater_radius;
        int depth = cfg.thor_crater_depth;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) continue;
                int maxDepth = Math.min(depth, (int) Math.floor(radius - dist) + 1);
                for (int dy = 0; dy <= maxDepth; dy++) {
                    breakCraterBlock(world, center.add(dx, -dy, dz));
                }
            }
        }
    }

    private static void breakCraterBlock(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.isOf(Blocks.BEDROCK)) return;
        if (state.getHardness(world, pos) < 0) return; // unbreakable
        if (world.getBlockEntity(pos) != null) return; // protect chests, furnaces, etc.
        world.breakBlock(pos, false);
    }

    /** Breaks a block box around a position, so the returning mace digs a tunnel. */
    public static void breakAround(ServerWorld world, BlockPos center, int radius) {
        if (!YPConfig.getInstance().thor_crater_enabled) return;
        if (radius <= 0) return;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    breakCraterBlock(world, center.add(dx, dy, dz));
                }
            }
        }
    }

    private static void damageArea(ServerWorld world, ServerPlayerEntity owner, Vec3d center) {
        YPConfig cfg = YPConfig.getInstance();
        double radius = cfg.thor_impact_radius;
        double damage = cfg.thor_impact_damage;
        if (damage <= 0) return;

        AllianceManager alliances = owner != null ? AllianceManager.get(world.getServer()) : null;
        Box box = new Box(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);
        DamageSource source = owner != null
                ? world.getDamageSources().playerAttack(owner)
                : world.getDamageSources().lightningBolt();

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (target == owner) continue;
            if (alliances != null && alliances.isAlly(owner, target)) continue;
            if (target.getBoundingBox().getCenter().squaredDistanceTo(center) > radius * radius) continue;
            target.damage(world, source, (float) damage);
        }
    }
}
