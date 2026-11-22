package com.yppowers.power.divinity;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.SoulboundItemSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Divinity Shield: Protective dome repelling hostile entities & projectiles, healing allies, and reflecting damage. */
public final class DivinityShieldPower extends Power {
    private static final String CD_DOME = "divinity_dome";
    private static final String SHIELD_TAG = "divinity_shield";

    private final Map<UUID, Integer> activeDomeTicks = new HashMap<>();
    private final Map<UUID, Integer> animClock = new HashMap<>();

    @Override
    public PowerId id() {
        return PowerId.DIVINITY;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(AbilitySpec.active(AbilitySlot.PRIMARY, "Barrier Dome", CD_DOME));
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        Integer ticks = activeDomeTicks.get(player.getUuid());
        return ticks != null && ticks > 0 ? "Dome " + CooldownSystem.formatSeconds(ticks) : "Reflect 30%";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY;
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        if (activeDomeTicks.containsKey(player.getUuid())) return true; // allow manual cancel
        MinecraftServer server = player.getEntityWorld().getServer();
        return server != null && CooldownSystem.get(server).checkReady(player, CD_DOME, "Divinity Shield");
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        return activeDomeTicks.containsKey(player.getUuid()) ? 0.0 : super.energyCost(player, slot);
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        UUID uuid = player.getUuid();
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        if (activeDomeTicks.remove(uuid) != null) {
            if (player.getEntityWorld() instanceof ServerWorld world) {
                VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.2f);
            }
            player.sendMessage(Text.literal("Divinity Shield: OFF").formatted(Formatting.YELLOW), true);
            return;
        }

        PowerConfigs.Divinity cfg = YPConfig.get().powers.divinity;
        activeDomeTicks.put(uuid, cfg.forcefieldDurationTicks);
        CooldownSystem.get(server).set(uuid, CD_DOME, cfg.forcefieldCooldownTicks);
        if (player.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.2f);
            VfxSystem.shockwave(world, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, player.getEntityPos().add(0, 0.3, 0), cfg.healRadius, 36);
        }
        player.sendMessage(Text.literal("Divinity Shield: ON").formatted(Formatting.GREEN), true);
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureShield(player);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        ensureShield(player);
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        ensureShield(newPlayer);
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        activeDomeTicks.remove(player.getUuid());
        animClock.remove(player.getUuid());
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        activeDomeTicks.remove(player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        activeDomeTicks.remove(player.getUuid());
        animClock.remove(player.getUuid());
    }

    @Override
    public boolean shouldPreventShieldDisable(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return SoulboundItemSystem.isSoulbound(stack, PowerId.DIVINITY, SHIELD_TAG);
    }

    @Override
    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        double ratio = YPConfig.get().powers.divinity.damageReflectRatio;
        if (ratio > 0.0 && source.getAttacker() instanceof LivingEntity attacker
                && CombatUtil.canHarm(player, attacker)
                && player.getEntityWorld() instanceof ServerWorld world) {
            float reflect = (float) (amount * ratio);
            if (reflect > 0.05f) {
                attacker.damage(world, world.getDamageSources().thorns(player), reflect);
            }
        }
        return amount;
    }

    @Override
    public boolean blocksEntityMovement(Entity entity, Vec3d movement) {
        if (activeDomeTicks.isEmpty() || entity == null || movement == null) return false;
        if (!(entity.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return false;
        AllianceSystem alliances = AllianceSystem.get(world.getServer());
        int radius = YPConfig.get().powers.divinity.healRadius;
        Vec3d from = entity.getEntityPos();
        Vec3d to = from.add(movement);

        for (ServerPlayerEntity holder : world.getServer().getPlayerManager().getPlayerList()) {
            if (!activeDomeTicks.containsKey(holder.getUuid()) || holder.getEntityWorld() != world) continue;
            if (entity == holder || (entity instanceof ServerPlayerEntity p && alliances.isAlly(holder, p))) continue;
            if (CombatUtil.isFriendlyMinion(holder, entity)) continue;
            if (entity instanceof ProjectileEntity proj && proj.getOwner() instanceof ServerPlayerEntity owner
                    && alliances.isAlly(holder, owner)) continue;

            double r = radius + Math.max(0.0, entity.getWidth() * 0.5);
            double dFrom = from.distanceTo(holder.getEntityPos());
            double dTo = to.distanceTo(holder.getEntityPos());
            if (dFrom >= r && dTo < r && dTo < dFrom) return true;
        }
        return false;
    }

    @Override
    public boolean onProjectileCollision(ProjectileEntity projectile, HitResult hitResult) {
        if (activeDomeTicks.isEmpty() || hitResult.getType() != HitResult.Type.ENTITY) return false;
        Entity target = ((EntityHitResult) hitResult).getEntity();
        if (!(target instanceof ServerPlayerEntity holder) || !activeDomeTicks.containsKey(holder.getUuid())) return false;
        if (projectile.getOwner() == holder) return false;
        projectile.setVelocity(projectile.getVelocity().multiply(-1.2));
        ((EntityVelocityAccessor) projectile).invokeYppowersScheduleVelocityUpdate();
        return true;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        Integer remaining = activeDomeTicks.get(uuid);
        if (remaining == null) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;

        if (remaining <= 1) {
            activeDomeTicks.remove(uuid);
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.0f);
            player.sendMessage(Text.literal("Divinity Shield expired.").formatted(Formatting.GRAY), true);
            return;
        }
        activeDomeTicks.put(uuid, remaining - 1);

        int tick = animClock.merge(uuid, 1, Integer::sum);
        PowerConfigs.Divinity cfg = YPConfig.get().powers.divinity;
        int radius = cfg.healRadius;
        AllianceSystem alliances = AllianceSystem.get(world.getServer());
        Box area = player.getBoundingBox().expand(radius);

        // Heal allies every 20 ticks
        if (tick % 20 == 0) {
            for (ServerPlayerEntity ally : world.getEntitiesByClass(ServerPlayerEntity.class, area,
                    p -> alliances.isAlly(player, p) && player.squaredDistanceTo(p) <= radius * radius)) {
                ally.heal((float) cfg.healAmount);
                VfxSystem.burst(world, ParticleTypes.HEART, ally.getEntityPos().add(0, 1.0, 0), 2, 0.4, 0.05);
            }
        }

        // Push back hostile entities & deflect projectiles inside dome
        for (Entity e : world.getOtherEntities(player, area)) {
            if (e instanceof ServerPlayerEntity sp && alliances.isAlly(player, sp)) continue;
            if (CombatUtil.isFriendlyMinion(player, e)) continue;
            double dist = e.getEntityPos().distanceTo(player.getEntityPos());
            if (dist >= radius) continue;

            if (e instanceof ProjectileEntity proj) {
                if (proj.getOwner() instanceof ServerPlayerEntity owner && alliances.isAlly(player, owner)) continue;
                Vec3d out = proj.getEntityPos().subtract(player.getEntityPos()).normalize().multiply(1.4);
                proj.setVelocity(out);
                ((EntityVelocityAccessor) proj).invokeYppowersScheduleVelocityUpdate();
            } else if (e instanceof LivingEntity) {
                Vec3d out = e.getEntityPos().subtract(player.getEntityPos());
                out = out.lengthSquared() < 1.0e-4 ? new Vec3d(1, 0.2, 0) : out.normalize();
                e.setVelocity(out.x * 0.9, Math.max(0.2, out.y * 0.4), out.z * 0.9);
                ((EntityVelocityAccessor) e).invokeYppowersScheduleVelocityUpdate();
            }
        }

        // Throttled dome visuals every 4 ticks
        if (tick % 4 == 0) {
            VfxSystem.ring(world, ParticleTypes.HAPPY_VILLAGER, player.getEntityPos().add(0, 0.2, 0), radius, 28, 0.0);
            double equatorY = player.getY() + 1.0 + Math.sin(tick * 0.12) * (radius * 0.45);
            double equatorR = Math.sqrt(Math.max(1.0, radius * radius - Math.pow(equatorY - player.getY(), 2)));
            VfxSystem.ring(world, ParticleTypes.ELECTRIC_SPARK, new Vec3d(player.getX(), equatorY, player.getZ()), equatorR, 20, 0.0);
            VfxSystem.ring(world, ParticleTypes.END_ROD, player.getEntityPos().add(0, radius * 0.5, 0), radius * 0.85, 12, 0.0);
        }
    }

    private static void ensureShield(ServerPlayerEntity player) {
        SoulboundItemSystem.ensureEquippedOrPresent(player, PowerId.DIVINITY, SHIELD_TAG, () -> {
            ItemStack shield = new ItemStack(Items.SHIELD);
            shield.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Aegis of Divinity").formatted(Formatting.GOLD, Formatting.BOLD));
            shield.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
            return shield;
        });
    }
}
