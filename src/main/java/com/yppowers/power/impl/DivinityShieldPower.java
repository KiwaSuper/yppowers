package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.RitualManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class DivinityShieldPower extends Power {
   private static final Set<UUID> ACTIVE_SHIELD_HOLDERS = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Integer> activeShields = new HashMap<>();
   private final Map<UUID, Integer> cooldowns = new HashMap<>();
   private final Map<UUID, Integer> reflectCooldowns = new HashMap<>();
   private final Map<UUID, Integer> healingTicks = new HashMap<>();
   private final Map<UUID, Integer> animationTicks = new HashMap<>();
   private final Map<UUID, Double> storedDamage = new HashMap<>();
   private final Map<UUID, Set<UUID>> chargedProjectiles = new HashMap<>();

   public boolean isActive(UUID uuid) {
      return this.activeShields.containsKey(uuid);
   }

   public static boolean isShieldActive(ServerPlayerEntity player) {
      return player.getEntityWorld().getServer() == null
         ? false
         : PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.DIVINITY) instanceof DivinityShieldPower shield
            && shield.isActive(player.getUuid());
   }

   private static int shieldRadius() {
      return Math.max(1, YPConfig.getInstance().divinity_shield_radius);
   }

   private static Vec3d domeCenter(ServerPlayerEntity holder) {
      return new Vec3d(holder.getX(), holder.getY() + holder.getHeight() * 0.5, holder.getZ());
   }

   private static boolean isInsideDome(ServerPlayerEntity holder, Vec3d position, double padding) {
      double radius = shieldRadius() + Math.max(0.0, padding);
      return position.squaredDistanceTo(domeCenter(holder)) <= radius * radius;
   }

   public static boolean blocksEntityMovement(Entity entity, Vec3d displacement) {
      if (!(!ACTIVE_SHIELD_HOLDERS.isEmpty() && entity != null && displacement != null && entity.getEntityWorld() instanceof ServerWorld world)) {
         return false;
      } else if (world.getServer() == null) {
         return false;
      } else {
         PowerManager manager = PowerManager.get(world.getServer());
         if (!(manager.getPowerInstance(PowerType.DIVINITY) instanceof DivinityShieldPower shield)) {
            return false;
         } else {
            AllianceManager alliances = AllianceManager.get(world.getServer());
            Vec3d from = entity.getEntityPos();
            Vec3d to = from.add(displacement);

            for (ServerPlayerEntity holder : world.getServer().getPlayerManager().getPlayerList()) {
               if (shield.activeShields.containsKey(holder.getUuid())
                  && holder.getEntityWorld() == world
                  && entity != holder
                  && !(entity instanceof ServerPlayerEntity target && alliances.isAlly(holder, target))
                  && !(
                     entity instanceof ProjectileEntity projectile
                        && projectile.getOwner() instanceof ServerPlayerEntity owner
                        && alliances.isAlly(holder, owner)
                  )) {
                  double padding = Math.max(0.0, entity.getWidth() * 0.5);
                  boolean wasOutside = !isInsideDome(holder, from, padding);
                  boolean wouldBeInside = isInsideDome(holder, to, padding);
                  if (wasOutside && wouldBeInside && to.squaredDistanceTo(domeCenter(holder)) < from.squaredDistanceTo(domeCenter(holder))) {
                     if (entity instanceof WardenEntity warden) {
                        RitualManager.recordDivinityWardenBlocked(holder, warden);
                     }

                     return true;
                  }
               }
            }

            return false;
         }
      }
   }

   @Override
   public PowerType getType() {
      return PowerType.DIVINITY;
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (this.activeShields.containsKey(uuid)) {
         ServerWorld var7 = player.getEntityWorld();
         if (var7 instanceof ServerWorld) {
            Vec3d center = domeCenter(player);
            EpicVfx.implosion(var7, ParticleTypes.END_ROD, center.x, center.y, center.z, shieldRadius(), 28, 0.18);
            EpicVfx.cue(var7, player.getId(), "divinity_shield", "phase", center.x, center.y, center.z, Vec3d.ZERO, 0.85F, 18, 0.0F);
         }

         this.deactivate(player, "Divinity Shield: OFF");
      } else if (this.cooldowns.getOrDefault(uuid, 0) > 0) {
         player.sendMessage(Text.literal("Divinity Shield on cooldown!").formatted(Formatting.RED), true);
      } else {
         this.activeShields.put(uuid, YPConfig.getInstance().divinity_forcefield_duration_ticks);
         ACTIVE_SHIELD_HOLDERS.add(uuid);
         this.cooldowns.put(uuid, Math.max(0, YPConfig.getInstance().divinity_forcefield_cooldown_ticks));
         ServerWorld center = player.getEntityWorld();
         if (center instanceof ServerWorld) {
            Vec3d centerx = domeCenter(player);
            EpicVfx.activation(center, centerx.x, centerx.y, centerx.z, 1.0);
            EpicVfx.ring(center, ParticleTypes.END_ROD, centerx.x, centerx.y, centerx.z, 0.4, 0.12, 28, 0.05);
            EpicVfx.cue(center, player.getId(), "divinity_shield", "aura", centerx.x, centerx.y, centerx.z, Vec3d.ZERO, 1.0F, 30, 0.0F);
         }

         player.sendMessage(Text.literal("Divinity Shield: ON").formatted(Formatting.GREEN), true);
      }
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      int cooldown = this.reflectCooldowns.getOrDefault(uuid, 0);
      if (cooldown > 0) {
         player.sendMessage(Text.literal("Retribution cooldown: " + (cooldown + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
      } else {
         double stored = this.storedDamage.getOrDefault(uuid, 0.0);
         if (stored < 0.5) {
            player.sendMessage(Text.literal("No damage is stored in your shield yet.").formatted(Formatting.GRAY), true);
         } else {
            ServerWorld target = player.getEntityWorld();
            if (target instanceof ServerWorld) {
               if (!(TargetingUtil.pickLookTarget(player, YPConfig.getInstance().divinity_reflect_range) instanceof LivingEntity living && living != player)) {
                  player.sendMessage(Text.literal("Aim at a living target to release Retribution.").formatted(Formatting.YELLOW), true);
               } else if (living instanceof ServerPlayerEntity other && AllianceManager.get(target.getServer()).isAlly(player, other)) {
                  player.sendMessage(Text.literal("You cannot turn Retribution on an ally.").formatted(Formatting.RED), true);
               } else {
                  YPConfig cfg = YPConfig.getInstance();
                  float damage = (float)Math.max(0.5, Math.min(cfg.divinity_stored_damage_cap, stored * Math.max(0.0, cfg.divinity_reflect_damage_multiplier)));
                  if (!living.damage(target, target.getDamageSources().playerAttack(player), damage)) {
                     player.sendMessage(Text.literal("Retribution was rejected; your stored damage is preserved.").formatted(Formatting.YELLOW), true);
                  } else {
                     this.storedDamage.remove(uuid);
                     this.reflectCooldowns.put(uuid, Math.max(0, cfg.divinity_reflect_cooldown_ticks));
                     Vec3d from = player.getEyePos();
                     Vec3d to = living.getBoundingBox().getCenter();
                     EpicVfx.beam(target, ParticleTypes.ELECTRIC_SPARK, from, to, 0.35);
                     EpicVfx.beam(target, ParticleTypes.END_ROD, from, to, 0.55);
                     EpicVfx.impact(target, to.x, to.y, to.z, 1.1);
                     EpicVfx.cue(target, player.getId(), "divinity_shield", "lightning_hit", to.x, to.y, to.z, to.subtract(from), 1.1F, 20, 0.12F);
                     target.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, to.x, to.y, to.z, 24, 0.5, 0.6, 0.5, 0.08);
                     target.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0F, 1.15F);
                     player.sendMessage(
                        Text.literal(
                              "RETRIBUTION — "
                                 + String.format(Locale.ROOT, "%.1f", damage)
                                 + " stored damage returned to "
                                 + living.getName().getString()
                                 + "."
                           )
                           .formatted(Formatting.GOLD, Formatting.BOLD),
                        true
                     );
                  }
               }
            }
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      ServerWorld world = player.getEntityWorld();
      int animationTick = this.animationTicks.merge(uuid, 1, Integer::sum);
      int healingTick = this.healingTicks.merge(uuid, 1, Integer::sum);
      int healRadius = Math.max(1, YPConfig.getInstance().divinity_heal_radius);
      int radius = shieldRadius();
      if (this.activeShields.containsKey(uuid)) {
         if (healingTick >= 20) {
            this.healingTicks.put(uuid, 0);
            AllianceManager allianceManager = AllianceManager.get(player.getEntityWorld().getServer());
            Box box = player.getBoundingBox().expand(healRadius);

            for (ServerPlayerEntity target : world.getEntitiesByClass(ServerPlayerEntity.class, box, e -> true)) {
               if ((target == player || allianceManager != null && allianceManager.isAlly(player, target))
                  && player.squaredDistanceTo(target) <= healRadius * healRadius) {
                  float amount = (float)YPConfig.getInstance().divinity_heal_amount;
                  target.heal(amount);
                  world.spawnParticles(ParticleTypes.HEART, true, true, target.getX(), target.getY() + 1.0, target.getZ(), 3, 0.5, 0.5, 0.5, 0.1);
               }
            }
         }
      } else {
         this.healingTicks.put(uuid, 0);
      }

      tickDown(this.cooldowns, uuid);
      tickDown(this.reflectCooldowns, uuid);
      if (this.activeShields.containsKey(uuid)) {
         int ticksLeft = this.activeShields.get(uuid) - 1;
         if (ticksLeft <= 0) {
            this.deactivate(player, "Divinity Shield: OFF");
         } else {
            this.activeShields.put(uuid, ticksLeft);
            double playerX = player.getX();
            double playerY = player.getY() + player.getHeight() / 2.0;
            double playerZ = player.getZ();
            if (animationTick % 4 == 0) {
               for (int i = 0; i < 20; i++) {
                  double theta = world.random.nextDouble() * 2.0 * Math.PI;
                  double phi = Math.acos(2.0 * world.random.nextDouble() - 1.0);
                  double dx = radius * Math.sin(phi) * Math.cos(theta);
                  double dy = radius * Math.cos(phi);
                  double dz = radius * Math.sin(phi) * Math.sin(theta);
                  world.spawnParticles(ParticleTypes.END_ROD, true, true, playerX + dx, playerY + dy, playerZ + dz, 1, 0.0, 0.0, 0.0, 0.0);
               }

               double angle = animationTick % 360 * Math.PI / 180.0;

               for (int i = 0; i < 6; i++) {
                  double a = angle + i * Math.PI * 2.0 / 10.0;
                  double dx = radius * Math.cos(a);
                  double dz = radius * Math.sin(a);
                  world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, playerX + dx, playerY, playerZ + dz, 1, 0.0, 0.0, 0.0, 0.0);
               }

               for (int i = 0; i < 8; i++) {
                  double a = world.random.nextDouble() * 2.0 * Math.PI;
                  double dx = radius * Math.cos(a);
                  double dz = radius * Math.sin(a);
                  world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, true, true, playerX + dx, player.getY(), playerZ + dz, 1, 0.0, 0.0, 0.0, 0.0);
               }
            }

            AllianceManager allianceManager = AllianceManager.get(player.getEntityWorld().getServer());
            Box hitBox = player.getBoundingBox().expand(radius);

            for (Entity entity : world.getOtherEntities(player, hitBox, e -> !(e instanceof ProjectileEntity))) {
               if (!(entity instanceof ServerPlayerEntity targetPlayer && allianceManager != null && allianceManager.isAlly(player, targetPlayer))) {
                  Vec3d center = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ());
                  double distance = center.distanceTo(domeCenter(player));
                  if (distance <= radius) {
                     Vec3d push = entity.getEntityPos().subtract(player.getEntityPos()).normalize().multiply(1.5);
                     entity.addVelocity(push.x, 0.5, push.z);
                     ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
                     world.spawnParticles(ParticleTypes.CRIT, true, true, entity.getX(), entity.getY() + 1.0, entity.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                  } else if (distance <= radius + Math.max(0.5, entity.getWidth() * 0.5 + 0.25)) {
                     Vec3d outward = entity.getEntityPos().subtract(player.getEntityPos()).normalize();
                     if (entity.getVelocity().dotProduct(outward) < -0.01) {
                        entity.setVelocity(entity.getVelocity().add(outward.multiply(1.25)));
                        ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
                        world.spawnParticles(
                           ParticleTypes.ELECTRIC_SPARK, true, true, entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(), 12, 0.25, 0.25, 0.25, 0.04
                        );
                        world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.5F, 1.0F);
                     }
                  }
               }
            }

            Set<UUID> observedProjectiles = new HashSet<>();
            Set<UUID> charged = this.chargedProjectiles.computeIfAbsent(uuid, ignored -> new HashSet<>());

            for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, hitBox, p -> true)) {
               UUID projectileId = projectile.getUuid();
               observedProjectiles.add(projectileId);
               if (projectile.getOwner() != player
                  && !(projectile.getOwner() instanceof ServerPlayerEntity owner && allianceManager != null && allianceManager.isAlly(player, owner))) {
                  Vec3d center = new Vec3d(projectile.getX(), projectile.getY() + projectile.getHeight() * 0.5, projectile.getZ());
                  double projectileDistance = center.distanceTo(domeCenter(player));
                  double projectileBoundary = radius + projectile.getWidth() * 0.5;
                  if (projectileDistance <= radius) {
                     Vec3d vel = projectile.getVelocity().multiply(-1.0);
                     projectile.setVelocity(vel);
                     ((EntityVelocityAccessor)projectile).invokeYppowersScheduleVelocityUpdate();
                     world.spawnParticles(ParticleTypes.CRIT, true, true, projectile.getX(), projectile.getY(), projectile.getZ(), 5, 0.2, 0.2, 0.2, 0.1);
                     if (charged.add(projectileId)) {
                        this.bankDeflectedProjectile(player);
                     }
                  } else if (projectileDistance <= projectileBoundary + 0.25) {
                     charged.remove(projectileId);
                     Vec3d outward = projectile.getEntityPos().subtract(player.getEntityPos()).normalize();
                     if (projectile.getVelocity().dotProduct(outward) < -0.01) {
                        projectile.setVelocity(projectile.getVelocity().add(outward.multiply(1.25)));
                        ((EntityVelocityAccessor)projectile).invokeYppowersScheduleVelocityUpdate();
                        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, projectile.getX(), projectile.getY(), projectile.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
                        world.playSound(null, projectile.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.5F, 1.0F);
                        this.bankDeflectedProjectile(player);
                     }
                  } else {
                     charged.remove(projectileId);
                  }
               } else {
                  charged.remove(projectileId);
               }
            }

            charged.retainAll(observedProjectiles);
            if (animationTick % 20 == 0 && this.storedDamage.getOrDefault(uuid, 0.0) >= 0.5) {
               player.sendMessage(
                  Text.literal(
                        "Shield stored: "
                           + String.format(Locale.ROOT, "%.1f", this.storedDamage.get(uuid))
                           + " / "
                           + String.format(Locale.ROOT, "%.0f", YPConfig.getInstance().divinity_stored_damage_cap)
                           + " — H to release"
                     )
                     .formatted(Formatting.AQUA),
                  true
               );
            }
         }
      }
   }

   private void deactivate(ServerPlayerEntity player, String message) {
      UUID uuid = player.getUuid();
      this.activeShields.remove(uuid);
      ACTIVE_SHIELD_HOLDERS.remove(uuid);
      this.chargedProjectiles.remove(uuid);
      player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8F, 1.2F);
      player.sendMessage(Text.literal(message).formatted(Formatting.RED), true);
   }

   private static boolean isShieldBlocking(ServerPlayerEntity player, DamageSource source, Entity attacker) {
      if (!player.isBlocking() || !player.getActiveItem().isOf(Items.SHIELD)) {
         return false;
      } else if (source.isIn(DamageTypeTags.BYPASSES_SHIELD)) {
         return false;
      } else {
         Vec3d toAttacker = attacker.getEntityPos().subtract(player.getEntityPos());
         return toAttacker.lengthSquared() < 1.0E-6 || player.getRotationVec(1.0F).dotProduct(toAttacker.normalize()) > 0.0;
      }
   }

   private void bankDeflectedProjectile(ServerPlayerEntity player) {
      this.bankDamage(player, YPConfig.getInstance().divinity_deflected_projectile_charge);
   }

   private void bankDamage(ServerPlayerEntity player, double amount) {
      if (amount > 0.0 && Double.isFinite(amount)) {
         YPConfig cfg = YPConfig.getInstance();
         double cap = Math.max(0.0, cfg.divinity_stored_damage_cap);
         if (!(cap <= 0.0)) {
            double current = this.storedDamage.getOrDefault(player.getUuid(), 0.0);
            this.storedDamage.put(player.getUuid(), Math.min(cap, current + amount));
         }
      }
   }

   private static void tickDown(Map<UUID, Integer> map, UUID uuid) {
      Integer value = map.get(uuid);
      if (value != null) {
         if (value <= 1) {
            map.remove(uuid);
         } else {
            map.put(uuid, value - 1);
         }
      }
   }

   @Override
   public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
      if (amount <= 0.0) {
         return amount;
      } else if (!source.isOf(DamageTypes.THORNS) && !source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL)) {
         Entity attacker = source.getAttacker();
         if (attacker != null && attacker != player) {
            if (isShieldBlocking(player, source, attacker)) {
               this.bankDamage(player, amount * Math.max(0.0, Math.min(1.0, YPConfig.getInstance().divinity_shield_block_charge_fraction)));
            }

            if (!this.activeShields.containsKey(player.getUuid())) {
               return amount;
            } else {
               float reduction = (float)Math.max(0.0, Math.min(0.9, YPConfig.getInstance().divinity_shield_damage_reduction));
               float blocked = amount * reduction;
               this.bankDamage(player, blocked);
               attacker.damage(player.getEntityWorld(), player.getEntityWorld().getDamageSources().thorns(player), amount * 0.3F);
               if (blocked > 0.0F) {
                  Vec3d center = domeCenter(player);
                  EpicVfx.ring(player.getEntityWorld(), ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 0.45, 0.08, 12, 0.12);
               }

               return amount - blocked;
            }
         } else {
            return amount;
         }
      } else {
         return amount;
      }
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
      if (player != null) {
         ACTIVE_SHIELD_HOLDERS.remove(player.getUuid());
         if (player.getEntityWorld().getServer() != null) {
            if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.DIVINITY) instanceof DivinityShieldPower shield) {
               shield.activeShields.remove(player.getUuid());
               shield.healingTicks.remove(player.getUuid());
               shield.animationTicks.remove(player.getUuid());
               shield.storedDamage.remove(player.getUuid());
               shield.reflectCooldowns.remove(player.getUuid());
               shield.chargedProjectiles.remove(player.getUuid());
            }
         }
      }
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      ACTIVE_SHIELD_HOLDERS.remove(player.getUuid());
      this.activeShields.remove(player.getUuid());
      this.cooldowns.remove(player.getUuid());
      this.reflectCooldowns.remove(player.getUuid());
      this.healingTicks.remove(player.getUuid());
      this.animationTicks.remove(player.getUuid());
      this.storedDamage.remove(player.getUuid());
      this.chargedProjectiles.remove(player.getUuid());
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      ACTIVE_SHIELD_HOLDERS.remove(player.getUuid());
      this.activeShields.remove(player.getUuid());
      this.storedDamage.remove(player.getUuid());
      this.reflectCooldowns.remove(player.getUuid());
      this.chargedProjectiles.remove(player.getUuid());
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.cooldowns.remove(playerId);
      this.reflectCooldowns.remove(playerId);
   }

   @Override
   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      if (player.getInventory().containsAny(stack -> stack.isOf(Items.SHIELD))) {
         return List.of();
      } else {
         player.giveOrDropStack(new ItemStack(Items.SHIELD));
         return List.of("Divinity Shield");
      }
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.ensureShield(player);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.ensureShield(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      this.ensureShield(player);
   }

   private void ensureShield(ServerPlayerEntity player) {
      this.ensureSpecialGear(player);
   }
}
