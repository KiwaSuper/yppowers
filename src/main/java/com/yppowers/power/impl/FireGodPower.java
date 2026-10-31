package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.item.YPItemData;
import com.yppowers.power.PowerType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FireGodPower extends Power {
    private final Map<UUID, Integer> ghastCooldowns = new HashMap<>();
    private final Map<UUID, Integer> blazeCooldowns = new HashMap<>();
    private final Map<UUID, Integer> skeletonCooldowns = new HashMap<>();
    private static final class OwnedSummon { final MobEntity entity; final UUID owner; final boolean blaze; int ticks;
        OwnedSummon(MobEntity entity, UUID owner, boolean blaze, int ticks) { this.entity = entity; this.owner = owner; this.blaze = blaze; this.ticks = ticks; } }
    private final Map<UUID, List<OwnedSummon>> ownedSummons = new HashMap<>();

    @Override
    public PowerType getType() {
        return PowerType.FIRE_GOD;
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        if (player.isOnFire()) {
            player.extinguish();
        }
        // Avoid constructing/re-sending a status effect every tick. Refresh with a short
        // safety margin, while respecting any equal-or-stronger independent Fire Resistance.
        StatusEffectInstance fireResistance = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
        if (fireResistance == null || (fireResistance.getAmplifier() == 0 && fireResistance.getDuration() <= 10)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, false, false));
        }
        // MobEntityMixin blocks fresh target assignments and clears stale ones before each mob's
        // AI tick. Avoid a per-player world-wide entity scan here to keep this passive inexpensive.

        UUID pId = player.getUuid();
        tickCooldown(ghastCooldowns, pId);
        tickCooldown(blazeCooldowns, pId);
        tickCooldown(skeletonCooldowns, pId);
        tickSummons(player);
    }


    @Override
    public void onSecondaryActivate(ServerPlayerEntity player) {
        summonFireGodMinions(player, true);
    }

    @Override
    public void onTertiaryActivate(ServerPlayerEntity player) {
        summonFireGodMinions(player, false);
    }

    private void summonFireGodMinions(ServerPlayerEntity player, boolean blaze) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id = player.getUuid();
        YPConfig cfg = YPConfig.getInstance();
        Map<UUID, Integer> cooldownMap = blaze ? blazeCooldowns : skeletonCooldowns;
        int cooldown = cooldownMap.getOrDefault(id, 0);
        String name = blaze ? "Ember Guard" : "Cinder Sentinel";
        int cap = blaze ? Math.max(0, cfg.fire_god_blaze_max_active) : Math.max(0, cfg.fire_god_wither_skeleton_max_active);
        int count = 0;
        List<OwnedSummon> list = ownedSummons.computeIfAbsent(id, ignored -> new ArrayList<>());
        list.removeIf(s -> s.entity.isRemoved() || !s.entity.isAlive() || s.ticks <= 0);
        for (OwnedSummon s : list) if (s.blaze == blaze) count++;
        if (cooldown > 0) { player.sendMessage(Text.literal(name + " cooldown: " + ((cooldown + 19) / 20) + "s").formatted(Formatting.YELLOW), true); return; }
        if (cap == 0 || count >= cap) { player.sendMessage(Text.literal("Active " + name + " limit reached.").formatted(Formatting.YELLOW), true); return; }
        MobEntity mob = blaze ? EntityType.BLAZE.create(world, SpawnReason.TRIGGERED)
                : EntityType.WITHER_SKELETON.create(world, SpawnReason.TRIGGERED);
        if (mob == null) { player.sendMessage(Text.literal("The summon could not spawn here.").formatted(Formatting.RED), true); return; }
        mob.refreshPositionAndAngles(player.getX() + 1.2, player.getY() + 0.4, player.getZ() + 1.2, player.getYaw(), 0);
        mob.setCustomName(Text.literal(name + " · " + player.getName().getString()).formatted(blaze ? Formatting.GOLD : Formatting.DARK_GRAY));
        mob.setCustomNameVisible(true);
        mob.addCommandTag("yppowers_fire_god_summon");
        mob.addCommandTag("yppowers_owner_" + id);
        mob.setPersistent();
        double hp = blaze ? Math.max(1, cfg.fire_god_blaze_max_health) : Math.max(1, cfg.fire_god_wither_skeleton_max_health);
        if (mob.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
        mob.setHealth((float) hp);
        if (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(Math.max(0.1,
                    blaze ? cfg.fire_god_blaze_attack_damage : cfg.fire_god_wither_skeleton_attack_damage));
        }
        world.spawnEntity(mob);
        int life = blaze ? cfg.fire_god_blaze_lifespan_ticks : cfg.fire_god_wither_skeleton_lifespan_ticks;
        list.add(new OwnedSummon(mob, id, blaze, Math.max(20, life)));
        cooldownMap.put(id, Math.max(0, blaze ? cfg.fire_god_blaze_cooldown_ticks : cfg.fire_god_wither_skeleton_cooldown_ticks));
        world.spawnParticles(blaze ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 20, .5, .5, .5, .04);
        world.playSound(null, player.getBlockPos(), blaze ? SoundEvents.ENTITY_BLAZE_AMBIENT : SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT,
                SoundCategory.PLAYERS, .8f, blaze ? 1.2f : .8f);
    }

    private void tickSummons(ServerPlayerEntity owner) {
        List<OwnedSummon> list = ownedSummons.get(owner.getUuid());
        if (list == null) return;
        if (!(owner.getEntityWorld() instanceof ServerWorld world)) return;
        list.removeIf(s -> {
            if (s.entity.isRemoved() || !s.entity.isAlive() || --s.ticks <= 0) { s.entity.discard(); return true; }
            if (s.ticks % 10 == 0) {
                if (GraceManager.isActive(world.getServer())) { s.entity.setTarget(null); return false; }
                LivingEntity chosen = null;
                double best = 18.0 * 18.0;
                AllianceManager alliances = AllianceManager.get(world.getServer());
                Box area = s.entity.getBoundingBox().expand(18.0);
                for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, area,
                        e -> e != owner && e != s.entity && e.isAlive()
                                && (!(e instanceof ServerPlayerEntity p) || !alliances.isAlly(owner, p)))) {
                    double d = s.entity.squaredDistanceTo(candidate);
                    if (d < best) { chosen = candidate; best = d; }
                }
                s.entity.setTarget(chosen);
                if (chosen == null && s.entity.squaredDistanceTo(owner) > 64) s.entity.getNavigation().startMovingTo(owner, 1.1);
                else if (chosen != null) s.entity.getNavigation().stop();
            }
            return false;
        });
        if (list.isEmpty()) ownedSummons.remove(owner.getUuid());
    }

    private static void tickCooldown(Map<UUID, Integer> map, UUID id) {
        int value = map.getOrDefault(id, 0); if (value > 0) map.put(id, value - 1);
    }

    private void clearSummons(UUID id) {
        List<OwnedSummon> list = ownedSummons.remove(id);
        if (list != null) for (OwnedSummon summon : list) summon.entity.discard();
        blazeCooldowns.remove(id); skeletonCooldowns.remove(id);
    }

    @Override public void onPlayerDeath(ServerPlayerEntity player) { clearSummons(player.getUuid()); }
    @Override public void onPlayerDisconnect(ServerPlayerEntity player) { clearSummons(player.getUuid()); }
    @Override public void onRemove(ServerPlayerEntity player) { clearSummons(player.getUuid()); ghastCooldowns.remove(player.getUuid()); }

    @Override
    public boolean isFireImmune(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.HOT_FLOOR)) {
            return 0;
        }
        return amount;
    }

    @Override
    public boolean shouldMobIgnore(ServerPlayerEntity player, MobEntity mob) {
        // These Nether-native hostiles should ignore Fire God even if they encounter the player
        // outside the Nether. Include variants that do not inherit from PiglinEntity.
        return mob instanceof BlazeEntity
                || mob instanceof GhastEntity
                || mob instanceof PiglinEntity
                || mob instanceof PiglinBruteEntity
                || mob instanceof ZombifiedPiglinEntity
                || mob instanceof MagmaCubeEntity
                || mob instanceof WitherSkeletonEntity
                || mob instanceof HoglinEntity
                || mob instanceof ZoglinEntity
                // Skeletons naturally spawn in soul-sand valleys and Endermen also spawn in Nether biomes.
                || mob instanceof SkeletonEntity
                || mob instanceof EndermanEntity;
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID pId = player.getUuid();
        if (ghastCooldowns.getOrDefault(pId, 0) <= 0) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            Vec3d look = player.getRotationVec(1.0F);
            FireballEntity fireball = new FireballEntity(world, player, new Vec3d(look.x * 0.5, look.y * 0.5, look.z * 0.5), 1);
            fireball.setPosition(player.getX(), player.getEyeY(), player.getZ());
            world.spawnEntity(fireball);
            
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getEyeY(), player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            
            ghastCooldowns.put(pId, Math.max(0, YPConfig.getInstance().fire_god_cannon_cooldown_ticks));
        } else {
            player.sendMessage(Text.literal("Ghast Cannon on cooldown: " + (ghastCooldowns.get(pId) / 20) + "s"), true);
        }
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        giveRod(player);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        giveRod(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        giveRod(player);
    }

    private void giveRod(ServerPlayerEntity player) {
        boolean hasRod = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.BLAZE_ROD) && YPItemData.hasFlag(stack, "YPSoulbound")) {
                hasRod = true;
                break;
            }
        }
        if (!hasRod) {
            ItemStack rod = new ItemStack(Items.BLAZE_ROD);
            rod.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal("Ember of the Undying Flame").formatted(Formatting.ITALIC, Formatting.GOLD));
            YPItemData.setFlag(rod, "YPSoulbound", true);
            player.giveOrDropStack(rod);
        }
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return stack.isOf(Items.BLAZE_ROD) && YPItemData.hasFlag(stack, "YPSoulbound");
    }
}
