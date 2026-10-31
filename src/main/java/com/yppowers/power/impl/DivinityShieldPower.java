package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class DivinityShieldPower extends Power {
    private static final Set<UUID> ACTIVE_SHIELD_HOLDERS = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> activeShields = new HashMap<>();
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Map<UUID, Integer> healingTicks = new HashMap<>();
    private final Map<UUID, Integer> animationTicks = new HashMap<>();

    public boolean isActive(UUID uuid) { return activeShields.containsKey(uuid); }
    public static boolean isShieldActive(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return false;
        Power instance = com.yppowers.power.PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.DIVINITY);
        return instance instanceof DivinityShieldPower shield && shield.isActive(player.getUuid());
    }

    /**
     * Prevent non-allied entities from crossing the dome boundary. The server-side Entity.move
     * hook uses this check; the tick-based pushback below handles entities that were already inside
     * when the dome opened and supplies the perimeter impact feedback.
     */
    public static boolean blocksEntityMovement(Entity entity, Vec3d displacement) {
        if (ACTIVE_SHIELD_HOLDERS.isEmpty() || entity == null || displacement == null
                || !(entity.getEntityWorld() instanceof ServerWorld world)) return false;
        if (world.getServer() == null) return false;
        com.yppowers.power.PowerManager manager = com.yppowers.power.PowerManager.get(world.getServer());
        Power registered = manager.getPowerInstance(PowerType.DIVINITY);
        if (!(registered instanceof DivinityShieldPower shield)) return false;
        AllianceManager alliances = AllianceManager.get(world.getServer());

        Vec3d from = entity.getEntityPos();
        Vec3d to = from.add(displacement);
        for (ServerPlayerEntity holder : world.getServer().getPlayerManager().getPlayerList()) {
            if (!shield.activeShields.containsKey(holder.getUuid()) || holder.getEntityWorld() != world) continue;
            if (entity == holder || (entity instanceof ServerPlayerEntity target && alliances.isAlly(holder, target))) continue;
            if (entity instanceof ProjectileEntity projectile && projectile.getOwner() instanceof ServerPlayerEntity owner
                    && alliances.isAlly(holder, owner)) continue;

            double collisionRadius = YPConfig.getInstance().divinity_heal_radius + Math.max(0.0, entity.getWidth() * 0.5);
            double fromDistance = from.distanceTo(holder.getEntityPos());
            double toDistance = to.distanceTo(holder.getEntityPos());
            if (fromDistance >= collisionRadius && toDistance < collisionRadius && toDistance < fromDistance) return true;
        }
        return false;
    }

    @Override
    public PowerType getType() {
        return PowerType.DIVINITY;
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (activeShields.containsKey(uuid)) {
            activeShields.remove(uuid);
            ACTIVE_SHIELD_HOLDERS.remove(uuid);
            player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.2f);
            player.sendMessage(Text.literal("Divinity Shield: OFF").formatted(Formatting.RED), true);
            return;
        }

        if (cooldowns.getOrDefault(uuid, 0) > 0) {
            player.sendMessage(Text.literal("Divinity Shield on cooldown!").formatted(Formatting.RED), true);
            return;
        }

        activeShields.put(uuid, YPConfig.getInstance().divinity_forcefield_duration_ticks);
        ACTIVE_SHIELD_HOLDERS.add(uuid);
        cooldowns.put(uuid, Math.max(0, YPConfig.getInstance().divinity_forcefield_cooldown_ticks));
        player.sendMessage(Text.literal("Divinity Shield: ON").formatted(Formatting.GREEN), true);
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        int animationTick = animationTicks.merge(uuid, 1, Integer::sum);
        int healingTick = healingTicks.merge(uuid, 1, Integer::sum);
        int radius = Math.max(1, YPConfig.getInstance().divinity_heal_radius);

        // Healing aura is part of the active dome; one heal pulse every 20 server ticks.
        if (activeShields.containsKey(uuid)) {
            if (healingTick >= 20) {
                healingTicks.put(uuid, 0);
                AllianceManager allianceManager = AllianceManager.get(player.getEntityWorld().getServer());
                Box box = player.getBoundingBox().expand(radius);
                for (ServerPlayerEntity target : world.getEntitiesByClass(ServerPlayerEntity.class, box, e -> true)) {
                    if (target == player || (allianceManager != null && allianceManager.isAlly(player, target))) {
                        if (player.squaredDistanceTo(target) <= radius * radius) {
                            float amount = (float) YPConfig.getInstance().divinity_heal_amount;
                            target.heal(amount);
                            world.spawnParticles(ParticleTypes.HEART, target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.5, 0.5, 0.5, 0.1);
                        }
                    }
                }
            }
        } else {
            healingTicks.put(uuid, 0);
        }

        if (cooldowns.containsKey(uuid)) {
            int cd = cooldowns.get(uuid) - 1;
            if (cd <= 0) cooldowns.remove(uuid);
            else cooldowns.put(uuid, cd);
        }

        if (activeShields.containsKey(uuid)) {
            int ticksLeft = activeShields.get(uuid) - 1;
            if (ticksLeft <= 0) {
                activeShields.remove(uuid);
                ACTIVE_SHIELD_HOLDERS.remove(uuid);
                player.sendMessage(Text.literal("Divinity Shield: OFF").formatted(Formatting.RED), true);
            } else {
                activeShields.put(uuid, ticksLeft);

                // Particles
                double playerX = player.getX();
                double playerY = player.getY() + player.getHeight() / 2.0;
                double playerZ = player.getZ();

                // Ambient shield visuals are emitted every other tick and at a lower sample
                // count. Collision, healing, and projectile-deflection logic below still runs
                // every tick, so this is a VFX-only performance change.
                if ((animationTick & 1) == 0) {
                    // Dome shell END_ROD particles.
                    for (int i = 0; i < 32; i++) {
                        double theta = world.random.nextDouble() * 2 * Math.PI;
                        double phi = Math.acos(2 * world.random.nextDouble() - 1);
                        double dx = radius * Math.sin(phi) * Math.cos(theta);
                        double dy = radius * Math.cos(phi);
                        double dz = radius * Math.sin(phi) * Math.sin(theta);
                        if (dy > 0) {
                            world.spawnParticles(ParticleTypes.END_ROD, playerX + dx, playerY + dy, playerZ + dz, 1, 0, 0, 0, 0);
                        }
                    }

                    // Equator ring ELECTRIC_SPARK particles.
                    double angle = (animationTick % 360) * Math.PI / 180.0;
                    for (int i = 0; i < 10; i++) {
                        double a = angle + (i * Math.PI * 2 / 10);
                        double dx = radius * Math.cos(a);
                        double dz = radius * Math.sin(a);
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, playerX + dx, playerY, playerZ + dz, 1, 0, 0, 0, 0);
                    }

                    // Perimeter HAPPY_VILLAGER particles.
                    for (int i = 0; i < 14; i++) {
                        double a = world.random.nextDouble() * 2 * Math.PI;
                        double dx = radius * Math.cos(a);
                        double dz = radius * Math.sin(a);
                        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, playerX + dx, player.getY(), playerZ + dz, 1, 0, 0, 0, 0);
                    }
                }

                AllianceManager allianceManager = AllianceManager.get(player.getEntityWorld().getServer());
                Box hitBox = player.getBoundingBox().expand(radius);

                // Eject entities caught inside the dome, and spark only when an entity is pressing into its perimeter.
                for (Entity entity : world.getOtherEntities(player, hitBox, e -> !(e instanceof ProjectileEntity))) {
                    if (entity instanceof ServerPlayerEntity targetPlayer && allianceManager != null && allianceManager.isAlly(player, targetPlayer)) continue;
                    double distance = Math.sqrt(player.squaredDistanceTo(entity));
                    if (distance <= radius) {
                        Vec3d push = entity.getEntityPos().subtract(player.getEntityPos()).normalize().multiply(1.5);
                        entity.addVelocity(push.x, 0.5, push.z);
                        ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
                        world.spawnParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + 1.0, entity.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                    } else if (distance <= radius + Math.max(0.5, entity.getWidth() * 0.5 + 0.25)) {
                        Vec3d outward = entity.getEntityPos().subtract(player.getEntityPos()).normalize();
                        if (entity.getVelocity().dotProduct(outward) < -0.01) {
                            entity.setVelocity(entity.getVelocity().add(outward.multiply(1.25)));
                            ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
                            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(), 12, 0.25, 0.25, 0.25, 0.04);
                            world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.5f, 1.0f);
                        }
                    }
                }

                // Projectiles deflect
                for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, hitBox, p -> true)) {
                    if (projectile.getOwner() == player || (projectile.getOwner() instanceof ServerPlayerEntity owner && allianceManager != null && allianceManager.isAlly(player, owner))) {
                        continue;
                    }
                    double projectileDistance = Math.sqrt(player.squaredDistanceTo(projectile));
                    double projectileBoundary = radius + projectile.getWidth() * 0.5;
                    if (projectileDistance <= radius) {
                        Vec3d vel = projectile.getVelocity().multiply(-1);
                        projectile.setVelocity(vel);
                        ((com.yppowers.mixin.EntityVelocityAccessor) projectile).invokeYppowersScheduleVelocityUpdate();
                        world.spawnParticles(ParticleTypes.CRIT, projectile.getX(), projectile.getY(), projectile.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                    } else if (projectileDistance <= projectileBoundary + 0.25) {
                        Vec3d outward = projectile.getEntityPos().subtract(player.getEntityPos()).normalize();
                        if (projectile.getVelocity().dotProduct(outward) < -0.01) {
                            projectile.setVelocity(projectile.getVelocity().add(outward.multiply(1.25)));
                            ((com.yppowers.mixin.EntityVelocityAccessor) projectile).invokeYppowersScheduleVelocityUpdate();
                            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, projectile.getX(), projectile.getY(), projectile.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
                            world.playSound(null, projectile.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.5f, 1.0f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (activeShields.containsKey(player.getUuid()) && amount > 0 && !source.isOf(DamageTypes.THORNS) && source.getAttacker() != null && source.getAttacker() != player) {
            Entity attacker = source.getAttacker();
            attacker.damage(((ServerWorld) player.getEntityWorld()), ((ServerWorld) player.getEntityWorld()).getDamageSources().thorns(player), amount * 0.3f);
        }
        return amount;
    }

    @Override
    public boolean shouldPreventShieldDisable(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return stack.isOf(Items.SHIELD);
    }


    public static void playerDisconnected(ServerPlayerEntity player) {
        if (player == null) return;
        ACTIVE_SHIELD_HOLDERS.remove(player.getUuid());
        if (player.getEntityWorld().getServer() == null) return;
        Power registered = com.yppowers.power.PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.DIVINITY);
        if (registered instanceof DivinityShieldPower shield) {
            shield.activeShields.remove(player.getUuid());
            shield.healingTicks.remove(player.getUuid());
            shield.animationTicks.remove(player.getUuid());
        }
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        ACTIVE_SHIELD_HOLDERS.remove(player.getUuid());
        activeShields.remove(player.getUuid());
        cooldowns.remove(player.getUuid());
        healingTicks.remove(player.getUuid());
        animationTicks.remove(player.getUuid());
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureShield(player);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        ensureShield(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        ensureShield(player);
    }

    private void ensureShield(ServerPlayerEntity player) {
        if (!player.getInventory().containsAny(stack -> stack.isOf(Items.SHIELD))) {
            player.giveOrDropStack(new ItemStack(Items.SHIELD));
        }
    }
}
