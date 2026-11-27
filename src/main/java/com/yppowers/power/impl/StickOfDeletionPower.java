package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class StickOfDeletionPower extends Power {
   private final Map<UUID, Integer> cooldowns = new HashMap<>();
   private final Map<UUID, Integer> swapCooldowns = new HashMap<>();
   private final Map<UUID, Map<UUID, Integer>> knockedEntities = new HashMap<>();

   @Override
   public PowerType getType() {
      return PowerType.DELETION;
   }

   public void trySwapPlaces(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var9 = player.getUuid();
         int cooldown = this.swapCooldowns.getOrDefault(var9, 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Swap Places cooldown: " + (cooldown + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
         } else {
            YPConfig cfg = YPConfig.getInstance();
            Entity target = TargetingUtil.pickLookTarget(player, cfg.stick_of_deletion_swap_range);
            if (target == null) {
               player.sendMessage(Text.literal("No entity in your line of sight.").formatted(Formatting.GRAY), true);
            } else {
               Vec3d destination = TargetingUtil.findSafeNear(id, player, target.getEntityPos());
               if (destination == null) {
                  player.sendMessage(Text.literal("There is no safe space at that entity's position.").formatted(Formatting.RED), true);
               } else if (EnergyManager.get(id.getServer()).trySpendDirect(player, cfg.stick_of_deletion_swap_energy_cost, "Swap Places")) {
                  Vec3d origin = player.getEntityPos();
                  id.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 0.9, player.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
                  id.spawnParticles(ParticleTypes.PORTAL, true, true, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
                  EpicVfx.beam(id, ParticleTypes.END_ROD, origin.add(0.0, 1.0, 0.0), target.getEntityPos().add(0.0, 1.0, 0.0), 0.4);
                  player.teleport(id, destination.x, destination.y, destination.z, Set.of(), player.getYaw(), player.getPitch(), false);
                  target.teleport(id, origin.x, origin.y, origin.z, Set.of(), target.getYaw(), target.getPitch(), false);
                  target.addVelocity(0.0, 0.15, 0.0);
                  ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
                  EpicVfx.cue(id, player.getId(), "deletion", "swap", player.getX(), player.getY(), player.getZ(), Vec3d.ZERO, 0.9F, 16, 0.0F);
                  EpicVfx.cue(id, player.getId(), "deletion", "swap", target.getX(), target.getY(), target.getZ(), Vec3d.ZERO, 0.75F, 16, 0.0F);
                  id.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 0.9, player.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
                  EpicVfx.activation(id, player.getX(), player.getY(), player.getZ(), 0.8);
                  EpicVfx.activation(id, target.getX(), target.getY(), target.getZ(), 0.65);
                  id.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.8F);
                  id.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.2F);
                  this.swapCooldowns.put(var9, Math.max(0, cfg.stick_of_deletion_swap_cooldown_ticks));
               }
            }
         }
      }
   }

   @Override
   public void onAttack(ServerPlayerEntity attacker, Entity target) {
      if (attacker.getMainHandStack().isOf(Items.STICK)) {
         if (target instanceof ServerPlayerEntity targetPlayer && AllianceManager.get(attacker.getEntityWorld().getServer()).isAlly(attacker, targetPlayer)) {
            return;
         }

         if (!(target instanceof LivingEntity) || attacker.getEntityWorld().getServer() == null) {
            return;
         }

         UUID aId = attacker.getUuid();
         if (this.cooldowns.getOrDefault(aId, 0) <= 0) {
            Vec3d dir = target.getEntityPos().subtract(attacker.getEntityPos()).normalize();
            if (dir.lengthSquared() < 1.0E-6) {
               dir = attacker.getRotationVec(1.0F).normalize();
            }

            Vec3d launch = new Vec3d(dir.x * 12.0, Math.max(1.5, dir.y * 12.0), dir.z * 12.0);
            target.setVelocity(launch);
            ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
            ServerWorld cueWorld = (ServerWorld)target.getEntityWorld();
            EpicVfx.cue(cueWorld, attacker.getId(), "deletion", "impact", target.getX(), target.getY(), target.getZ(), dir, 1.0F, 18, 0.0F);
            target.getEntityWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0F, 1.0F);
            ((ServerWorld)target.getEntityWorld())
               .spawnParticles(ParticleTypes.EXPLOSION, true, true, target.getX(), target.getY() + 1.0, target.getZ(), 5, 0.5, 0.5, 0.5, 0.0);
            ServerWorld vfxWorld = (ServerWorld)target.getEntityWorld();
            EpicVfx.impact(vfxWorld, target.getX(), target.getY(), target.getZ(), 1.4);
            EpicVfx.beam(vfxWorld, ParticleTypes.END_ROD, attacker.getEntityPos().add(0.0, 1.0, 0.0), target.getEntityPos().add(0.0, 1.0, 0.0), 0.4);
            this.knockedEntities.computeIfAbsent(aId, k -> new HashMap<>()).put(target.getUuid(), 0);
            int cooldownTicks = Math.max(0, YPConfig.getInstance().stick_of_deletion_cooldown_ticks);
            if (cooldownTicks > 0) {
               this.cooldowns.put(aId, cooldownTicks);
               attacker.getItemCooldownManager().set(Items.STICK.getDefaultStack(), cooldownTicks);
            } else {
               this.cooldowns.remove(aId);
            }
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID pId = player.getUuid();
      if (this.cooldowns.getOrDefault(pId, 0) > 0) {
         this.cooldowns.put(pId, this.cooldowns.get(pId) - 1);
      }

      if (this.swapCooldowns.getOrDefault(pId, 0) > 0) {
         int swap = this.swapCooldowns.get(pId) - 1;
         if (swap <= 0) {
            this.swapCooldowns.remove(pId);
         } else {
            this.swapCooldowns.put(pId, swap);
         }
      }

      Map<UUID, Integer> knocked = this.knockedEntities.get(pId);
      if (knocked != null) {
         ServerWorld world = player.getEntityWorld();
         Iterator<Entry<UUID, Integer>> it = knocked.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            Entity target = world.getEntity(entry.getKey());
            if (target == null || target.isRemoved()) {
               it.remove();
            } else if (target.horizontalCollision) {
               if (target.damage(world, world.getDamageSources().magic(), 8.0F)) {
                  EpicVfx.impact(world, target.getX(), target.getY(), target.getZ(), 1.4);
               }

               EpicVfx.cue(world, player.getId(), "deletion", "detonation", target.getX(), target.getY(), target.getZ(), Vec3d.ZERO, 1.25F, 22, 0.18F);
               world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
               world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.0F, 1.0F);
               it.remove();
            } else {
               int ticks = entry.getValue() + 1;
               if (ticks > 60) {
                  it.remove();
               } else {
                  entry.setValue(ticks);
               }
            }
         }
      }
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.cooldowns.remove(playerId);
      this.swapCooldowns.remove(playerId);
   }
}
