package com.yppowers.power.shadowarcher;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.PersistentProjectileDamageAccessor;
import com.yppowers.mixin.PersistentProjectilePierceInvoker;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.EnergySystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Manages Shadow Archer's unlockable special arrows, Split Shot, Homing, TNT, Lightning, Ice Prison, and Railshot. */
final class SpecialArrowHandler {
    private static final String NBT_ARROWS = "UnlockedArrows";
    private static final String TAG_PREFIX = "yppowers_arrow_";
    private static final String TAG_CHILD = "yppowers_arrow_child";

    private static final class RailshotCharge {
        int stacks;
        int decayTicks;
    }

    private final Map<UUID, ArrowType> selectedArrow = new HashMap<>();
    private final Map<UUID, RailshotCharge> railshotCharge = new HashMap<>();
    private final Map<UUID, ServerWorld> homingArrows = new HashMap<>();

    ArrowType getSelected(ServerPlayerEntity player) {
        return selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
    }

    Set<ArrowType> getUnlocked(ServerPlayerEntity player) {
        Set<ArrowType> set = EnumSet.of(ArrowType.SPLIT_SHOT);
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return set;
        NbtList list = PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.SHADOW_ARCHER).getListOrEmpty(NBT_ARROWS);
        for (int i = 0; i < list.size(); i++) {
            try {
                set.add(ArrowType.valueOf(list.getString(i, "")));
            } catch (IllegalArgumentException ignored) {}
        }
        return set;
    }

    private void unlock(ServerPlayerEntity player, ArrowType type) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || type == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        NbtCompound nbt = store.getPowerNbt(player.getUuid(), PowerId.SHADOW_ARCHER);
        NbtList list = nbt.getListOrEmpty(NBT_ARROWS);
        for (int i = 0; i < list.size(); i++) {
            if (type.name().equals(list.getString(i, ""))) return;
        }
        list.add(NbtString.of(type.name()));
        nbt.put(NBT_ARROWS, list);
        store.markDirty();
    }

    void checkInventoryUnlocks(ServerPlayerEntity player) {
        int arrowCount = countItem(player, Items.ARROW);
        if (arrowCount < 8) return;
        Set<ArrowType> unlocked = getUnlocked(player);
        for (ArrowType type : ArrowType.values()) {
            if (unlocked.contains(type) || type.unlockCatalyst() == null) continue;
            if (countItem(player, type.unlockCatalyst()) >= 1) {
                unlock(player, type);
                player.sendMessage(Text.literal("Unlocked Special Arrow: " + type.displayName() + "!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
            }
        }
    }

    void cycleArrow(ServerPlayerEntity player) {
        checkInventoryUnlocks(player);
        List<ArrowType> unlocked = new ArrayList<>(getUnlocked(player));
        ArrowType current = getSelected(player);
        int idx = unlocked.indexOf(current);
        ArrowType next = unlocked.get((idx + 1) % unlocked.size());
        selectedArrow.put(player.getUuid(), next);
        if (next != ArrowType.RAILSHOT) railshotCharge.remove(player.getUuid());
        player.sendMessage(Text.literal("Arrow Type: " + next.displayName()).formatted(Formatting.LIGHT_PURPLE), true);
    }

    boolean onArrowSpawned(ServerWorld world, PersistentProjectileEntity arrow) {
        if (arrow.getCommandTags().contains(TAG_CHILD)) return true;
        if (!(arrow.getOwner() instanceof ServerPlayerEntity shooter) || world.getServer() == null) return true;
        if (!PlayerDataStore.get(world.getServer()).hasPower(shooter, PowerId.SHADOW_ARCHER)) return true;

        ArrowType type = getSelected(shooter);
        double cost = YPConfig.get().powers.shadowArcher.specialArrowEnergyCost;
        if (cost > 0.0 && !EnergySystem.get(world.getServer()).trySpend(shooter, cost, type.displayName())) {
            return false;
        }

        arrow.addCommandTag(TAG_PREFIX + type.name());
        switch (type) {
            case SPLIT_SHOT -> spawnSplitChildren(world, shooter, arrow);
            case HOMING -> homingArrows.put(arrow.getUuid(), world);
            case RAILSHOT -> applyRailshotBoost(shooter, arrow);
            default -> {}
        }
        return true;
    }

    private static void spawnSplitChildren(ServerWorld world, ServerPlayerEntity shooter, PersistentProjectileEntity parent) {
        Vec3d vel = parent.getVelocity();
        double[] angles = {-10.0, 10.0};
        for (double deg : angles) {
            ArrowEntity child = new ArrowEntity(world, shooter, new ItemStack(Items.ARROW), null);
            child.setPosition(parent.getX(), parent.getY(), parent.getZ());
            child.setVelocity(vel.rotateY((float) Math.toRadians(deg)));
            child.addCommandTag(TAG_CHILD);
            child.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
            world.spawnEntity(child);
        }
    }

    private void applyRailshotBoost(ServerPlayerEntity shooter, PersistentProjectileEntity arrow) {
        PowerConfigs.ShadowArcher cfg = YPConfig.get().powers.shadowArcher;
        RailshotCharge rc = railshotCharge.computeIfAbsent(shooter.getUuid(), ignored -> new RailshotCharge());
        rc.stacks = Math.min(10, rc.stacks + 1);
        rc.decayTicks = cfg.railshotChargeDecayTicks;

        double speedMult = 1.0 + rc.stacks * cfg.railshotSpeedMultiplierPerCharge;
        double dmgMult = 1.0 + rc.stacks * cfg.railshotDamageMultiplierPerCharge;
        arrow.setVelocity(arrow.getVelocity().multiply(speedMult));
        PersistentProjectileDamageAccessor dmgAcc = (PersistentProjectileDamageAccessor) arrow;
        dmgAcc.setYppowersDamage(dmgAcc.getYppowersDamage() * dmgMult);
        ((PersistentProjectilePierceInvoker) arrow).invokeYppowersSetPierceLevel((byte) Math.min(5, 1 + rc.stacks / 2));
        arrow.setNoGravity(true);
    }

    boolean onProjectileCollision(ProjectileEntity projectile, HitResult hit) {
        if (!(projectile.getEntityWorld() instanceof ServerWorld world)) return false;
        ArrowType type = readArrowTag(projectile);
        if (type == null) return false;
        homingArrows.remove(projectile.getUuid());

        ServerPlayerEntity owner = projectile.getOwner() instanceof ServerPlayerEntity sp ? sp : null;
        Vec3d pos = hit.getPos();

        switch (type) {
            case TNT -> {
                world.createExplosion(owner, pos.x, pos.y, pos.z, 2.5f, World.ExplosionSourceType.NONE);
                projectile.discard();
                return true;
            }
            case LIGHTNING -> {
                for (int i = 0; i < 2; i++) {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
                    if (bolt != null) {
                        if (owner != null) bolt.setChanneler(owner);
                        bolt.refreshPositionAfterTeleport(pos.x, pos.y, pos.z);
                        world.spawnEntity(bolt);
                    }
                }
                projectile.discard();
                return true;
            }
            case ICE_PRISON -> {
                Box area = Box.of(pos, 7.0, 7.0, 7.0);
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area,
                        x -> owner == null || CombatUtil.canHarm(owner, x))) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
                }
                VfxSystem.shockwave(world, ParticleTypes.SNOWFLAKE, ParticleTypes.CLOUD, pos, 3.5, 22);
                VfxSystem.playSound(world, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.8f);
                projectile.discard();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    void serverTick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, RailshotCharge>> rIt = railshotCharge.entrySet().iterator();
        while (rIt.hasNext()) {
            RailshotCharge rc = rIt.next().getValue();
            if (--rc.decayTicks <= 0) rIt.remove();
        }

        if (homingArrows.isEmpty()) return;
        Iterator<Map.Entry<UUID, ServerWorld>> hIt = homingArrows.entrySet().iterator();
        while (hIt.hasNext()) {
            Map.Entry<UUID, ServerWorld> entry = hIt.next();
            Entity e = entry.getValue().getEntity(entry.getKey());
            if (!(e instanceof PersistentProjectileEntity arrow) || !arrow.isAlive() || arrow.isOnGround()) {
                hIt.remove();
                continue;
            }
            if (!(arrow.getOwner() instanceof ServerPlayerEntity owner)) continue;
            LivingEntity target = findNearestToArrow(entry.getValue(), arrow, owner, 35.0);
            if (target != null) {
                Vec3d desired = target.getBoundingBox().getCenter().subtract(arrow.getEntityPos()).normalize();
                double speed = Math.max(1.2, arrow.getVelocity().length());
                Vec3d blended = arrow.getVelocity().normalize().multiply(0.75).add(desired.multiply(0.25)).normalize().multiply(speed);
                arrow.setVelocity(blended);
                VfxSystem.burst(entry.getValue(), ParticleTypes.END_ROD, arrow.getEntityPos(), 1, 0.05, 0.0);
            }
        }
    }

    private static LivingEntity findNearestToArrow(ServerWorld world, Entity arrow, ServerPlayerEntity owner, double radius) {
        Box box = arrow.getBoundingBox().expand(radius);
        LivingEntity best = null;
        double bestSq = radius * radius;
        for (LivingEntity cand : world.getEntitiesByClass(LivingEntity.class, box, x -> CombatUtil.canHarm(owner, x))) {
            double dSq = cand.squaredDistanceTo(arrow);
            if (dSq < bestSq) {
                bestSq = dSq;
                best = cand;
            }
        }
        return best;
    }

    private static ArrowType readArrowTag(ProjectileEntity proj) {
        for (String tag : proj.getCommandTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                try {
                    return ArrowType.valueOf(tag.substring(TAG_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    private static int countItem(ServerPlayerEntity player, Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.isOf(item)) total += s.getCount();
        }
        return total;
    }

    void clearPlayer(UUID uuid) {
        selectedArrow.remove(uuid);
        railshotCharge.remove(uuid);
    }
}
