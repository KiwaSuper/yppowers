package com.yppowers.power;

import com.yppowers.item.GrimoireOfLightManager;
import com.yppowers.power.impl.DivinityShieldPower;
import com.yppowers.power.impl.GoatPower;
import com.yppowers.power.impl.ShadowArcherPower;
import com.yppowers.power.impl.SpiritChannelerPower;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class RitualManager {
   private static final String COMPLETE_PREFIX = "ritual.complete.";
   private static final String PROGRESS_PREFIX = "ritual.progress.";
   private static final String AWAKENING_UNLOCK_PREFIX = "awakening.unlocked.";
   private static final Identifier YUMMY_KEBAB_ADVANCEMENT = Identifier.of("yppowers", "yummy_kebab");
   private static final int SPIDEY_GOAL_TICKS = 12000;
   private static final int FIRE_GOD_GOAL_TICKS = 6000;
   private static final int TIME_WARPER_WINDOW_TICKS = 3600;
   private static final int GOAT_WINDOW_TICKS = 600;
   private static final int ILLUSIONIST_THROW_WINDOW_TICKS = 300;
   private static final Map<UUID, RitualManager.KillCredit> immediateKillCredits = new HashMap<>();
   private static final Map<UUID, RitualManager.DragonTrial> dragonTrialStarts = new HashMap<>();
   private static final Map<UUID, RitualManager.ShadowFight> shadowFights = new HashMap<>();
   private static final Map<UUID, Integer> spideyAirTicks = new HashMap<>();
   private static final Map<UUID, Integer> fireGodLavaTicks = new HashMap<>();
   private static final Set<UUID> fireGodTrialActive = new HashSet<>();
   private static final Map<UUID, RitualManager.GoatTrial> goatTrials = new HashMap<>();
   private static final Map<UUID, RitualManager.IllusionistThrow> illusionistThrows = new HashMap<>();
   private static final Map<UUID, Set<UUID>> divinityBlockedWardens = new HashMap<>();

   private RitualManager() {
   }

   public static boolean isComplete(PowerManager manager, UUID playerId, PowerType power) {
      return manager.getRitualProgress(playerId).getBoolean(completionKey(power), false);
   }

   public static boolean isComplete(ServerPlayerEntity player, PowerType power) {
      MinecraftServer server = player.getEntityWorld().getServer();
      return server != null && isComplete(PowerManager.get(server), player.getUuid(), power);
   }

   public static boolean isAwakeningUnlocked(PowerManager manager, UUID playerId, PowerType power) {
      return manager != null && playerId != null && power != null && isComplete(manager, playerId, power)
         ? manager.getRitualProgress(playerId).getBoolean(awakeningUnlockKey(power), true)
         : false;
   }

   public static boolean unlockAwakening(PowerManager manager, UUID playerId, PowerType power) {
      if (manager != null && playerId != null && power != null && isComplete(manager, playerId, power)) {
         NbtCompound data = manager.getRitualProgress(playerId);
         String key = awakeningUnlockKey(power);
         if (data.getBoolean(key, false)) {
            return false;
         } else {
            data.putBoolean(key, true);
            manager.setRitualProgress(playerId, data);
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean isAssigned(ServerPlayerEntity player, PowerType power) {
      MinecraftServer server = player == null ? null : player.getEntityWorld().getServer();
      return server != null && PowerManager.get(server).getAssignedPowerType(player.getUuid()) == power;
   }

   public static boolean complete(ServerPlayerEntity player, PowerType power) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null && power != null) {
         PowerManager manager = PowerManager.get(server);
         NbtCompound data = manager.getRitualProgress(player.getUuid());
         String key = completionKey(power);
         if (data.getBoolean(key, false)) {
            return false;
         } else {
            data.putBoolean(key, true);
            boolean carriesGrimoire = GrimoireOfLightManager.hasGrimoire(player);
            data.putBoolean(awakeningUnlockKey(power), carriesGrimoire);
            manager.setRitualProgress(player.getUuid(), data);
            player.sendMessage(Text.literal(power.getDisplayName() + " ritual complete.").formatted(Formatting.GOLD, Formatting.BOLD), false);
            player.sendMessage(
               Text.literal(
                     carriesGrimoire
                        ? "Your Awakening is permanently unlocked; the Grimoire is no longer required for its abilities."
                        : "Carry the Grimoire of Light and activate your Awakening once to unlock its moves permanently."
                  )
                  .formatted(Formatting.AQUA),
               false
            );
            if (power == PowerType.GOAT) {
               grantYummyKebab(player);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static int getInt(PowerManager manager, UUID playerId, String key, int fallback) {
      return manager.getRitualProgress(playerId).getInt(key, fallback);
   }

   public static void setInt(PowerManager manager, UUID playerId, String key, int value) {
      NbtCompound data = manager.getRitualProgress(playerId);
      if (data.getInt(key, Integer.MIN_VALUE) != value) {
         data.putInt(key, value);
         manager.setRitualProgress(playerId, data);
      }
   }

   public static boolean getBoolean(PowerManager manager, UUID playerId, String key, boolean fallback) {
      return manager.getRitualProgress(playerId).getBoolean(key, fallback);
   }

   public static void setBoolean(PowerManager manager, UUID playerId, String key, boolean value) {
      NbtCompound data = manager.getRitualProgress(playerId);
      if (data.getBoolean(key, !value) != value) {
         data.putBoolean(key, value);
         manager.setRitualProgress(playerId, data);
      }
   }

   public static void clear(PowerManager manager, UUID playerId, String key) {
      NbtCompound data = manager.getRitualProgress(playerId);
      if (data.contains(key)) {
         data.remove(key);
         manager.setRitualProgress(playerId, data);
      }
   }

   public static int addProgress(ServerPlayerEntity player, PowerType power, String id, int amount, int goal) {
      if (isAssigned(player, power) && amount > 0 && goal > 0 && !isComplete(player, power)) {
         MinecraftServer server = player.getEntityWorld().getServer();
         PowerManager manager = PowerManager.get(server);
         String key = progressKey(id);
         int next = Math.min(goal, Math.max(0, getInt(manager, player.getUuid(), key, 0)) + amount);
         setInt(manager, player.getUuid(), key, next);
         if (next >= goal) {
            complete(player, power);
         } else if (next == 1 || next % Math.max(1, goal / 10) == 0) {
            player.sendMessage(Text.literal(power.getDisplayName() + " ritual progress: " + next + "/" + goal).formatted(Formatting.AQUA), true);
         }

         return next;
      } else {
         return 0;
      }
   }

   public static void beginKillCredit(ServerPlayerEntity owner, PowerType power, String progressId, Entity target) {
      if (owner != null && target != null && isAssigned(owner, power) && !isComplete(owner, power)) {
         immediateKillCredits.put(target.getUuid(), new RitualManager.KillCredit(owner.getUuid(), power, progressId));
      }
   }

   public static void endKillCredit(Entity target) {
      if (target != null && target.isAlive()) {
         immediateKillCredits.remove(target.getUuid());
      }
   }

   public static void onEntityDeath(LivingEntity dead, DamageSource source) {
      if (dead.getEntityWorld() instanceof ServerWorld world && world.getServer() != null) {
         MinecraftServer server = world.getServer();
         PowerManager manager = PowerManager.get(server);
         RitualManager.KillCredit credit = immediateKillCredits.remove(dead.getUuid());
         if (credit != null && dead instanceof MobEntity) {
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(credit.owner());
            if (owner != null) {
               addProgress(
                  owner,
                  credit.power(),
                  credit.progressId(),
                  1,
                  credit.progressId().equals("serious_punch_kills") ? 40 : (credit.progressId().equals("kitsune_detonation_kills") ? 40 : 10)
               );
            }
         }

         Entity damageAttacker = source.getAttacker();
         boolean projectileAttack = damageAttacker instanceof ProjectileEntity || source.getSource() instanceof ProjectileEntity;
         Entity attacker = damageAttacker;
         if (damageAttacker instanceof ProjectileEntity projectile && projectile.getOwner() != null) {
            attacker = projectile.getOwner();
         }

         if (dead instanceof EnderDragonEntity dragon && attacker instanceof ServerPlayerEntity dragonKiller) {
            PowerType dragonPower = manager.getAssignedPowerType(dragonKiller.getUuid());
            ItemStack dragonWeapon = sourceWeapon(source, dragonKiller, projectileAttack);
            if (dragonPower == PowerType.THOR && dragonWeapon.isOf(Items.MACE)) {
               complete(dragonKiller, PowerType.THOR);
            }

            RitualManager.DragonTrial trial = dragonTrialStarts.remove(dragonKiller.getUuid());
            if (trial != null
               && trial.dragonId().equals(dragon.getUuid())
               && server.getTicks() - trial.startedAt() <= 3600L
               && dragonPower == PowerType.TIME_WARPER
               && dragon.getEntityWorld().getRegistryKey().equals(World.END)) {
               complete(dragonKiller, PowerType.TIME_WARPER);
            }
         }

         if (dead instanceof MobEntity thrownMob) {
            RitualManager.IllusionistThrow attempt = illusionistThrows.remove(thrownMob.getUuid());
            if (attempt != null) {
               thrownMob.removeCommandTag("yppowers_illusionist_throw_owner:" + attempt.owner());
            }
         }

         if (dead instanceof ServerPlayerEntity victim) {
            if (attacker instanceof MobEntity guard && guard.getCommandTags().stream().anyMatch(tag -> tag.startsWith("yppowers_ice_guard_owner:"))) {
               UUID ownerId = ownerOf(guard);
               ServerPlayerEntity owner = ownerId == null ? null : server.getPlayerManager().getPlayer(ownerId);
               if (owner != null && manager.getAssignedPowerType(ownerId) == PowerType.ICE_EMPEROR) {
                  addProgress(owner, PowerType.ICE_EMPEROR, "ice_guard_player_kills", 1, 3);
               }
            }

            if (attacker instanceof ServerPlayerEntity killer && killer != victim) {
               PowerType assigned = manager.getAssignedPowerType(killer.getUuid());
               ItemStack weapon = sourceWeapon(source, killer, projectileAttack);
               if (assigned == PowerType.POSEIDON && killer.isTouchingWater() && victim.isTouchingWater()) {
                  ServerWorld var15 = killer.getEntityWorld();
                  if (var15 instanceof ServerWorld) {
                     var15 = victim.getEntityWorld();
                     if (var15 instanceof ServerWorld
                        && var15.getBiome(killer.getBlockPos()).isIn(BiomeTags.IS_OCEAN)
                        && var15.getBiome(victim.getBlockPos()).isIn(BiomeTags.IS_OCEAN)) {
                        addProgress(killer, PowerType.POSEIDON, "poseidon_sea_kills", 1, 5);
                     }
                  }
               }

               if (assigned == PowerType.DELETION && weapon.isOf(Items.STICK)) {
                  addProgress(killer, PowerType.DELETION, "stick_player_kills", 1, 5);
               }

               if (assigned == PowerType.GOAT && GoatPower.isSpear(weapon) && GoatPower.hasReachedMaxSpeed(killer)) {
                  recordGoatSpearKill(killer, server.getTicks());
               }
            }
         } else {
            if (dead instanceof MobEntity) {
               if (attacker instanceof ServerPlayerEntity player) {
                  PowerType assignedx = manager.getAssignedPowerType(player.getUuid());
                  if (assignedx == PowerType.SHADOW_ARCHER && manager.getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer) {
                     archer.recordKill(player, dead.getType());
                  }

                  if (assignedx == PowerType.SPIRIT_CHANNELER && manager.getPowerInstance(PowerType.SPIRIT_CHANNELER) instanceof SpiritChannelerPower channeler
                     )
                   {
                     channeler.recordKill(player, dead);
                  }

                  if (assignedx == PowerType.GOAT && GoatPower.isSpear(sourceWeapon(source, player, projectileAttack)) && GoatPower.hasReachedMaxSpeed(player)) {
                     recordGoatSpearKill(player, server.getTicks());
                  }
               } else if (attacker instanceof MobEntity minion) {
                  UUID ownerId = ownerOf(minion);
                  ServerPlayerEntity owner = ownerId == null ? null : server.getPlayerManager().getPlayer(ownerId);
                  if (owner != null && dead.getType() == EntityType.WARDEN) {
                     if (minion.getCommandTags().contains("yppowers_fallout_minion") && manager.getAssignedPowerType(ownerId) == PowerType.FALLOUT_WARRIOR) {
                        complete(owner, PowerType.FALLOUT_WARRIOR);
                     }

                     if (minion.getCommandTags().contains("yppowers_restless_shadow") && manager.getAssignedPowerType(ownerId) == PowerType.SPIRIT_CHANNELER) {
                        complete(owner, PowerType.SPIRIT_CHANNELER);
                     }
                  }
               }
            }
         }
      }
   }

   private static UUID ownerOf(MobEntity minion) {
      for (String tag : minion.getCommandTags()) {
         String prefix = tag.startsWith("yppowers_owner_")
            ? "yppowers_owner_"
            : (tag.startsWith("yppowers_ice_guard_owner:") ? "yppowers_ice_guard_owner:" : null);
         if (prefix != null) {
            try {
               return UUID.fromString(tag.substring(prefix.length()));
            } catch (IllegalArgumentException var5) {
            }
         }
      }

      return null;
   }

   private static UUID taggedOwner(Entity entity, String prefix) {
      for (String tag : entity.getCommandTags()) {
         if (tag.startsWith(prefix)) {
            try {
               return UUID.fromString(tag.substring(prefix.length()));
            } catch (IllegalArgumentException var5) {
            }
         }
      }

      return null;
   }

   private static ItemStack sourceWeapon(DamageSource source, ServerPlayerEntity attacker, boolean projectileAttack) {
      ItemStack stack = source.getWeaponStack();
      if (stack != null && !stack.isEmpty()) {
         return stack;
      } else {
         return projectileAttack ? ItemStack.EMPTY : attacker.getMainHandStack();
      }
   }

   private static void recordGoatSpearKill(ServerPlayerEntity player, long now) {
      UUID id = player.getUuid();
      RitualManager.GoatTrial prior = goatTrials.get(id);
      if (prior == null) {
         prior = new RitualManager.GoatTrial(now, 0);
      } else if (now < prior.startedAt() || now - prior.startedAt() > 600L) {
         prior = new RitualManager.GoatTrial(now, 0);
      }

      int kills = prior.kills() + 1;
      if (kills >= 10) {
         goatTrials.remove(id);
         complete(player, PowerType.GOAT);
      } else {
         goatTrials.put(id, new RitualManager.GoatTrial(prior.startedAt(), kills));
         player.sendMessage(Text.literal("GOAT ritual: Yummy Kebab " + kills + "/10 within 30 seconds.").formatted(Formatting.GOLD), true);
      }
   }

   public static void onDamage(ServerPlayerEntity attacker, LivingEntity target) {
      if (attacker != null && target != null && attacker.getEntityWorld().getServer() != null) {
         PowerType type = PowerManager.get(attacker.getEntityWorld().getServer()).getAssignedPowerType(attacker.getUuid());
         if (type == PowerType.TIME_WARPER && target instanceof EnderDragonEntity && target.getEntityWorld().getRegistryKey().equals(World.END)) {
            long now = attacker.getEntityWorld().getServer().getTicks();
            RitualManager.DragonTrial trial = dragonTrialStarts.get(attacker.getUuid());
            if (trial == null || !trial.dragonId().equals(target.getUuid())) {
               dragonTrialStarts.put(attacker.getUuid(), new RitualManager.DragonTrial(target.getUuid(), now));
            }
         }

         if (type == PowerType.SHADOW_ARCHER) {
            ServerWorld area = attacker.getEntityWorld();
            if (area instanceof ServerWorld) {
               if (!(target instanceof WitherEntity) && !(target instanceof WardenEntity)) {
                  return;
               }

               Box areax = attacker.getBoundingBox().expand(64.0);
               boolean witherPresent = !area.getEntitiesByClass(WitherEntity.class, areax, Entity::isAlive).isEmpty();
               boolean wardenPresent = !area.getEntitiesByClass(WardenEntity.class, areax, Entity::isAlive).isEmpty();
               if (witherPresent && wardenPresent) {
                  long now = area.getServer().getTicks();
                  RitualManager.ShadowFight prior = shadowFights.get(attacker.getUuid());
                  boolean hitWither = prior != null && prior.expiresAt() >= now && prior.hitWither();
                  boolean hitWarden = prior != null && prior.expiresAt() >= now && prior.hitWarden();
                  if (target instanceof WitherEntity) {
                     hitWither = true;
                  }

                  if (target instanceof WardenEntity) {
                     hitWarden = true;
                  }

                  if (hitWither && hitWarden) {
                     shadowFights.remove(attacker.getUuid());
                     complete(attacker, PowerType.SHADOW_ARCHER);
                  } else {
                     shadowFights.put(attacker.getUuid(), new RitualManager.ShadowFight(hitWither, hitWarden, now + 1200L));
                  }

                  return;
               }

               return;
            }
         }
      }
   }

   public static void tickPlayer(ServerPlayerEntity player) {
      if (player.isAlive() && player.getEntityWorld().getServer() != null) {
         UUID id = player.getUuid();
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         long now = player.getEntityWorld().getServer().getTicks();
         illusionistThrows.entrySet().removeIf(entry -> now > entry.getValue().expiresAt());
         RitualManager.DragonTrial dragonTrial = dragonTrialStarts.get(id);
         if (dragonTrial != null && (manager.getAssignedPowerType(id) != PowerType.TIME_WARPER || now - dragonTrial.startedAt() > 3600L)) {
            dragonTrialStarts.remove(id);
         }

         if (manager.getAssignedPowerType(id) == PowerType.SPIDEY
            && !isComplete(manager, id, PowerType.SPIDEY)
            && !player.isOnGround()
            && !player.isTouchingWater()
            && !player.isClimbing()
            && !player.hasVehicle()) {
            int ticks = spideyAirTicks.merge(id, 1, Integer::sum);
            if (ticks >= 12000) {
               spideyAirTicks.remove(id);
               complete(player, PowerType.SPIDEY);
            }
         } else {
            spideyAirTicks.remove(id);
         }

         if (fireGodTrialActive.contains(id)) {
            if (manager.getAssignedPowerType(id) != PowerType.FIRE_GOD) {
               fireGodTrialActive.remove(id);
               fireGodLavaTicks.remove(id);
               clear(manager, id, progressKey("fire_god_lava_ticks"));
            } else if (player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
               fireGodTrialActive.remove(id);
               fireGodLavaTicks.remove(id);
               clear(manager, id, progressKey("fire_god_lava_ticks"));
               player.sendMessage(
                  Text.literal("Fire God ritual failed: Fire Resistance returned before the trial was complete.").formatted(Formatting.RED), false
               );
            } else if (isInLava(player)) {
               int ticks = Math.max(0, fireGodLavaTicks.getOrDefault(id, -1)) + 1;
               fireGodLavaTicks.put(id, ticks);
               if (ticks >= 6000) {
                  fireGodTrialActive.remove(id);
                  fireGodLavaTicks.remove(id);
                  complete(player, PowerType.FIRE_GOD);
               }
            } else if (fireGodLavaTicks.getOrDefault(id, -1) >= 0) {
               fireGodTrialActive.remove(id);
               fireGodLavaTicks.remove(id);
            }
         }

         RitualManager.ShadowFight shadow = shadowFights.get(id);
         if (shadow != null && (manager.getAssignedPowerType(id) != PowerType.SHADOW_ARCHER || now > shadow.expiresAt())) {
            shadowFights.remove(id);
         }

         RitualManager.GoatTrial goat = goatTrials.get(id);
         if (goat != null && (manager.getAssignedPowerType(id) != PowerType.GOAT || isComplete(manager, id, PowerType.GOAT) || now - goat.startedAt() > 600L)) {
            goatTrials.remove(id);
         }
      }
   }

   public static void trackThrownMob(ServerPlayerEntity owner, Entity target) {
      if (target instanceof MobEntity
         && isAssigned(owner, PowerType.ILLUSIONIST)
         && !isComplete(owner, PowerType.ILLUSIONIST)
         && owner.getEntityWorld().getServer() != null
         && !isInLava(target)) {
         String prefix = "yppowers_illusionist_throw_owner:";
         target.getCommandTags().stream().filter(tag -> tag.startsWith(prefix)).toList().forEach(target::removeCommandTag);
         target.addCommandTag(prefix + owner.getUuid());
         long expiresAt = owner.getEntityWorld().getServer().getTicks() + 300;
         illusionistThrows.put(target.getUuid(), new RitualManager.IllusionistThrow(owner.getUuid(), expiresAt));
      }
   }

   public static void onThrownMobTick(MobEntity mob) {
      if (mob.getEntityWorld() instanceof ServerWorld world) {
         RitualManager.IllusionistThrow attempt = illusionistThrows.get(mob.getUuid());
         if (attempt != null) {
            MinecraftServer server = world.getServer();
            if (server != null && server.getTicks() <= attempt.expiresAt()) {
               if (isInLava(mob)) {
                  illusionistThrows.remove(mob.getUuid());
                  mob.removeCommandTag("yppowers_illusionist_throw_owner:" + attempt.owner());
                  ServerPlayerEntity owner = server.getPlayerManager().getPlayer(attempt.owner());
                  if (owner != null && PowerManager.get(server).getAssignedPowerType(attempt.owner()) == PowerType.ILLUSIONIST) {
                     addProgress(owner, PowerType.ILLUSIONIST, "illusionist_lava_throws", 1, 50);
                  }
               }
            } else {
               illusionistThrows.remove(mob.getUuid());
               mob.removeCommandTag("yppowers_illusionist_throw_owner:" + attempt.owner());
            }
         }
      }
   }

   private static boolean isInLava(Entity entity) {
      return entity.isInLava() || entity.getEntityWorld().getFluidState(entity.getBlockPos()).isOf(Fluids.LAVA);
   }

   public static boolean isFireGodTrialActive(UUID playerId) {
      return fireGodTrialActive.contains(playerId);
   }

   public static void beginFireGodTrial(ServerPlayerEntity player) {
      if (isAssigned(player, PowerType.FIRE_GOD) && !isComplete(player, PowerType.FIRE_GOD)) {
         boolean alreadyActive = !fireGodTrialActive.add(player.getUuid());
         if (!alreadyActive) {
            fireGodLavaTicks.put(player.getUuid(), -1);
         }

         player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);
         player.sendMessage(
            Text.literal(
                  alreadyActive
                     ? "Fire God ritual is still active: your fire immunity remains suppressed; existing progress is unchanged."
                     : "Fire God ritual started: your fire immunity is suppressed. Stay in lava for five continuous minutes."
               )
               .formatted(Formatting.RED, Formatting.BOLD),
            false
         );
      }
   }

   public static void recordDangerEscape(ServerPlayerEntity player) {
      recordDangerEscape(player, isInDanger(player));
   }

   public static boolean isInDanger(ServerPlayerEntity player) {
      if (player != null) {
         ServerWorld var2 = player.getEntityWorld();
         if (var2 instanceof ServerWorld) {
            return player.getAttacker() != null && player.getAttacker().isAlive() && player.squaredDistanceTo(player.getAttacker()) <= 144.0
               || !var2.getEntitiesByClass(HostileEntity.class, player.getBoundingBox().expand(8.0), mob -> mob.isAlive() && mob.getTarget() == player)
                  .isEmpty();
         }
      }

      return false;
   }

   public static void recordDangerEscape(ServerPlayerEntity player, boolean wasInDanger) {
      if (wasInDanger && !isInDanger(player) && isAssigned(player, PowerType.GAMBIT)) {
         addProgress(player, PowerType.GAMBIT, "gambit_danger_teleports", 1, 20);
      }
   }

   public static void tickDivinity(ServerPlayerEntity player, int radius) {
      if (isAssigned(player, PowerType.DIVINITY) && DivinityShieldPower.isShieldActive(player)) {
         ServerWorld safeRadius = player.getEntityWorld();
         if (safeRadius instanceof ServerWorld) {
            int var11 = Math.max(1, radius);
            Set blocked = divinityBlockedWardens.computeIfAbsent(player.getUuid(), ignored -> new HashSet<>());
            Box area = player.getBoundingBox().expand(var11 + 2.0);
            HashSet currentlyPressing = new HashSet();

            for (WardenEntity warden : safeRadius.getEntitiesByClass(WardenEntity.class, area, Entity::isAlive)) {
               double centerDistance = warden.getBoundingBox()
                  .getCenter()
                  .distanceTo(new Vec3d(player.getX(), player.getY() + player.getHeight() * 0.5, player.getZ()));
               if (centerDistance <= var11 + Math.max(1.0, (double)warden.getWidth())) {
                  currentlyPressing.add(warden.getUuid());
               }
            }

            blocked.retainAll(currentlyPressing);
            if (blocked.size() >= 10) {
               complete(player, PowerType.DIVINITY);
            }

            return;
         }
      }

      divinityBlockedWardens.remove(player.getUuid());
   }

   public static void recordDivinityWardenBlocked(ServerPlayerEntity player, WardenEntity warden) {
      if (player != null && warden != null && isAssigned(player, PowerType.DIVINITY) && DivinityShieldPower.isShieldActive(player)) {
         divinityBlockedWardens.computeIfAbsent(player.getUuid(), ignored -> new HashSet<>()).add(warden.getUuid());
      }
   }

   public static void clearPlayerRuntime(UUID playerId) {
      immediateKillCredits.values().removeIf(credit -> credit.owner().equals(playerId));
      dragonTrialStarts.remove(playerId);
      shadowFights.remove(playerId);
      spideyAirTicks.remove(playerId);
      fireGodLavaTicks.remove(playerId);
      fireGodTrialActive.remove(playerId);
      goatTrials.remove(playerId);
      illusionistThrows.entrySet().removeIf(entry -> entry.getValue().owner().equals(playerId));
      divinityBlockedWardens.remove(playerId);
   }

   public static void clearPlayerRuntime(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         clearPlayerRuntime(player.getUuid());
      }
   }

   public static String progressSummary(MinecraftServer server, PowerManager manager, UUID id, PowerType power) {
      if (isComplete(manager, id, power)) {
         return "";
      } else if (power == PowerType.TIME_WARPER) {
         RitualManager.DragonTrial trial = dragonTrialStarts.get(id);
         if (trial == null) {
            return "";
         } else {
            long elapsed = Math.max(0L, server.getTicks() - trial.startedAt());
            int remaining = (int)Math.max(0L, (3600L - elapsed + 19L) / 20L);
            return " (dragon trial: " + remaining + " seconds remaining)";
         }
      } else {
         if (power == PowerType.SHADOW_ARCHER) {
            RitualManager.ShadowFight shadow = shadowFights.get(id);
            if (shadow != null) {
               String boss = shadow.hitWither() ? "Wither" : "Warden";
               return " (1/2 bosses engaged: " + boss + ")";
            }
         }

         if (power == PowerType.SPIDEY) {
            return " (" + spideyAirTicks.getOrDefault(id, 0) / 20 + "/600 seconds airborne)";
         } else if (power == PowerType.FIRE_GOD) {
            return " (" + Math.max(0, fireGodLavaTicks.getOrDefault(id, -1)) / 20 + "/300 seconds in lava)";
         } else if (power == PowerType.GOAT) {
            RitualManager.GoatTrial goat = goatTrials.get(id);
            return goat == null ? "" : " (" + goat.kills() + "/10 within 30 seconds)";
         } else {
            String progressId = switch (power) {
               case PUNCH -> "serious_punch_kills";
               case GAMBIT -> "gambit_danger_teleports";
               case POSEIDON -> "poseidon_sea_kills";
               case DELETION -> "stick_player_kills";
               case BOOTS_SKY -> "raigo_mob_kills";
               case ILLUSIONIST -> "illusionist_lava_throws";
               case KITSUNE -> "kitsune_detonation_kills";
               case ICE_EMPEROR -> "ice_guard_player_kills";
               default -> null;
            };

            int goal = switch (power) {
               case PUNCH, KITSUNE -> 40;
               case GAMBIT -> 20;
               case POSEIDON, DELETION -> 5;
               case BOOTS_SKY -> 10;
               case ILLUSIONIST -> 50;
               case ICE_EMPEROR -> 3;
               default -> -1;
            };
            if (progressId != null && goal >= 0) {
               int value = getInt(manager, id, progressKey(progressId), 0);
               return " (" + value + "/" + goal + ")";
            } else {
               return "";
            }
         }
      }
   }

   private static String completionKey(PowerType power) {
      return "ritual.complete." + power.getId();
   }

   private static String awakeningUnlockKey(PowerType power) {
      return "awakening.unlocked." + power.getId();
   }

   private static String progressKey(String id) {
      return "ritual.progress." + id;
   }

   private static void grantYummyKebab(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         AdvancementEntry advancement = server.getAdvancementLoader().get(YUMMY_KEBAB_ADVANCEMENT);
         if (advancement != null) {
            player.getAdvancementTracker().grantCriterion(advancement, "yummy_kebab");
         }
      }
   }

   private record DragonTrial(UUID dragonId, long startedAt) {
   }

   private record GoatTrial(long startedAt, int kills) {
   }

   private record IllusionistThrow(UUID owner, long expiresAt) {
   }

   private record KillCredit(UUID owner, PowerType power, String progressId) {
   }

   private record ShadowFight(boolean hitWither, boolean hitWarden, long expiresAt) {
   }
}
