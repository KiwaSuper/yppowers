package com.yppowers.power.firegod;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.progression.AwakeningManager;
import com.yppowers.system.PowerCombatUtil;
import com.yppowers.system.SummonTuning;
import com.yppowers.system.EpicVfx;
import net.minecraft.server.MinecraftServer;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.command.TestMode;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.progression.RitualManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.mob.HoglinEntity;
import net.minecraft.entity.mob.MagmaCubeEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZoglinEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
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

public class FireGodPower extends Power {
   private final Map<UUID, Integer> ghastCooldowns = new HashMap<>();
   private final Map<UUID, Integer> blazeCooldowns = new HashMap<>();
   private final Map<UUID, Integer> skeletonCooldowns = new HashMap<>();
   private final Map<UUID, Integer> witherSkullCooldowns = new HashMap<>();
   private final Map<UUID, List<FireGodPower.OwnedSummon>> ownedSummons = new HashMap<>();
   private final Set<UUID> activeBlazeBeamUsers = new HashSet<>();
   private final Map<UUID, Integer> blazeBeamShotClocks = new HashMap<>();

   @Override
   public PowerType getType() {
      return PowerType.FIRE_GOD;
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      if (!RitualManager.isFireGodTrialActive(player.getUuid())) {
         if (player.isOnFire()) {
            player.extinguish();
         }

         StatusEffectInstance fireResistance = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
         if (fireResistance == null || fireResistance.getAmplifier() == 0 && fireResistance.getDuration() <= 10) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, false, false));
         }
      }

      UUID pId = player.getUuid();
      tickCooldown(this.ghastCooldowns, pId);
      tickCooldown(this.blazeCooldowns, pId);
      tickCooldown(this.skeletonCooldowns, pId);
      tickCooldown(this.witherSkullCooldowns, pId);
      this.tickSummons(player);
      this.tickBlazeBeam(player);
   }

   public void setBlazeBeamFiring(ServerPlayerEntity player, boolean active) {
      UUID id = player.getUuid();
      if (!active) {
         this.activeBlazeBeamUsers.remove(id);
         this.blazeBeamShotClocks.remove(id);
      } else if (player.isAlive() && !GraceManager.blockAbility(player)) {
         boolean started = this.activeBlazeBeamUsers.add(id);
         this.blazeBeamShotClocks.putIfAbsent(id, 0);
         if (started) {
            ServerWorld var6 = player.getEntityWorld();
            if (var6 instanceof ServerWorld) {
               EpicVfx.activation(var6, player.getX(), player.getEyeY(), player.getZ(), 0.7);
               EpicVfx.cue(
                  var6, player.getId(), "fire_god", "beam", player.getX(), player.getEyeY(), player.getZ(), player.getRotationVec(1.0F), 6.0F, 16, 0.0F
               );
            }
         }
      }
   }

   private void tickBlazeBeam(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (this.activeBlazeBeamUsers.contains(id)) {
         if (player.isAlive()
            && !GraceManager.isActive(player.getEntityWorld().getServer())
            && PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.FIRE_GOD)) {
            EnergyManager energyManager = EnergyManager.get(player.getEntityWorld().getServer());
            if (energyManager.getBleedingTicks(id) > 0) {
               this.setBlazeBeamFiring(player, false);
               player.sendMessage(Text.literal("Blaze Barrage stopped: abilities are disabled while Bleeding.").formatted(Formatting.YELLOW), true);
            } else {
               int delay = this.blazeBeamShotClocks.getOrDefault(id, 0);
               if (delay > 0) {
                  this.blazeBeamShotClocks.put(id, delay - 1);
               } else {
                  YPConfig cfg = YPConfig.getInstance();
                  double shotCost = Math.max(0.0, cfg.fire_god_blaze_beam_energy_per_shot);
                  if (!energyManager.trySpendFlightEnergy(player, shotCost)) {
                     this.setBlazeBeamFiring(player, false);
                     player.sendMessage(Text.literal("Blaze Barrage stopped: not enough Energy.").formatted(Formatting.YELLOW), true);
                  } else {
                     ServerWorld look = player.getEntityWorld();
                     if (look instanceof ServerWorld) {
                        Vec3d var11 = player.getRotationVec(1.0F).normalize();
                        SmallFireballEntity shot = new SmallFireballEntity(
                           look, player, var11.multiply(Math.max(0.1, cfg.fire_god_blaze_beam_projectile_speed))
                        );
                        shot.setPosition(player.getX() + var11.x * 0.75, player.getEyeY() - 0.1 + var11.y * 0.75, player.getZ() + var11.z * 0.75);
                        look.spawnEntity(shot);
                        look.spawnParticles(
                           ParticleTypes.FLAME, true, true,
                           player.getX() + var11.x * 0.8,
                           player.getEyeY() - 0.1 + var11.y * 0.8,
                           player.getZ() + var11.z * 0.8,
                           5,
                           0.08,
                           0.08,
                           0.08,
                           0.015
                        );
                        if (player.age % 6 == 0) {
                           look.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.55F, 1.15F);
                        }

                        this.blazeBeamShotClocks.put(id, Math.max(1, cfg.fire_god_blaze_beam_shot_interval_ticks) - 1);
                     } else {
                        this.setBlazeBeamFiring(player, false);
                     }
                  }
               }
            }
         } else {
            this.setBlazeBeamFiring(player, false);
         }
      }
   }

   private void clearBlazeBeam(UUID id) {
      this.activeBlazeBeamUsers.remove(id);
      this.blazeBeamShotClocks.remove(id);
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      this.summonFireGodMinions(player, true);
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      this.summonFireGodMinions(player, false);
   }

   private void summonFireGodMinions(ServerPlayerEntity player, boolean blaze) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var16 = player.getUuid();
         YPConfig cfg = YPConfig.getInstance();
         Map<UUID, Integer> cooldownMap = blaze ? this.blazeCooldowns : this.skeletonCooldowns;
         int cooldown = cooldownMap.getOrDefault(var16, 0);
         String name = blaze ? "Ember Guard" : "Cinder Sentinel";
         int cap = blaze ? Math.max(0, cfg.fire_god_blaze_max_active) : Math.max(0, cfg.fire_god_wither_skeleton_max_active);
         int count = 0;
         List<FireGodPower.OwnedSummon> list = this.ownedSummons.computeIfAbsent(var16, ignored -> new ArrayList<>());
         list.removeIf(s -> s.entity.isRemoved() || !s.entity.isAlive() || s.ticks <= 0);

         for (FireGodPower.OwnedSummon s : list) {
            if (s.blaze == blaze) {
               count++;
            }
         }

         if (cooldown > 0) {
            player.sendMessage(Text.literal(name + " cooldown: " + (cooldown + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
         } else if (cap != 0 && count < cap) {
            MobEntity mob = blaze ? EntityType.BLAZE.create(id, SpawnReason.TRIGGERED) : EntityType.WITHER_SKELETON.create(id, SpawnReason.TRIGGERED);
            if (mob == null) {
               player.sendMessage(Text.literal("The summon could not spawn here.").formatted(Formatting.RED), true);
            } else {
               mob.refreshPositionAndAngles(player.getX() + 1.2, player.getY() + 0.4, player.getZ() + 1.2, player.getYaw(), 0.0F);
               mob.setCustomName(Text.literal(name + " · " + player.getName().getString()).formatted(blaze ? Formatting.GOLD : Formatting.DARK_GRAY));
               mob.setCustomNameVisible(true);
               mob.addCommandTag("yppowers_fire_god_summon");
               mob.addCommandTag("yppowers_owner_" + var16);
               mob.setPersistent();
               double hp = blaze ? Math.max(1.0, cfg.fire_god_blaze_max_health) : Math.max(1.0, cfg.fire_god_wither_skeleton_max_health);
               if (mob.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
                  mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
               }

               mob.setHealth((float)hp);
               if (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
                  mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                     .setBaseValue(Math.max(0.1, blaze ? cfg.fire_god_blaze_attack_damage : cfg.fire_god_wither_skeleton_attack_damage));
               }

               SummonTuning.strengthen(player, mob);
               id.spawnEntity(mob);
               EpicVfx.cue(id, player.getId(), "fire_god", "summon", mob.getX(), mob.getY(), mob.getZ(), player.getRotationVec(1.0F), 1.0F, 20, 0.0F);
               int life = blaze ? cfg.fire_god_blaze_lifespan_ticks : cfg.fire_god_wither_skeleton_lifespan_ticks;
               list.add(new FireGodPower.OwnedSummon(mob, var16, blaze, Math.max(20, life)));
               cooldownMap.put(var16, Math.max(0, blaze ? cfg.fire_god_blaze_cooldown_ticks : cfg.fire_god_wither_skeleton_cooldown_ticks));
               id.spawnParticles(
                  blaze ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, true, true, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 20, 0.5, 0.5, 0.5, 0.04
               );
               EpicVfx.shockwave(id, mob.getX(), mob.getY(), mob.getZ(), 3.5);
               EpicVfx.helix(id, blaze ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY(), mob.getZ(), 2.4, 0.9, 2.0, 22, 0.08);
               id.playSound(
                  null,
                  player.getBlockPos(),
                  blaze ? SoundEvents.ENTITY_BLAZE_AMBIENT : SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT,
                  SoundCategory.PLAYERS,
                  0.8F,
                  blaze ? 1.2F : 0.8F
               );
            }
         } else {
            player.sendMessage(Text.literal("Active " + name + " limit reached.").formatted(Formatting.YELLOW), true);
         }
      }
   }

   private void tickSummons(ServerPlayerEntity owner) {
      List<FireGodPower.OwnedSummon> list = this.ownedSummons.get(owner.getUuid());
      if (list != null) {
         MinecraftServer server = owner.getEntityWorld().getServer();
         boolean grace = GraceManager.isActive(server);
         list.removeIf(
            s -> {
               if (!s.entity.isRemoved() && s.entity.isAlive() && --s.ticks > 0) {
                  if (grace) {
                     if (s.entity.getTarget() != null) {
                        s.entity.setTarget(null);
                     }
                     return false;
                  }

                  if (s.ticks % 10 == 0 && s.entity.getEntityWorld() instanceof ServerWorld summonWorld) {
                     LivingEntity chosen = null;
                     double best = 324.0;
                     Box area = s.entity.getBoundingBox().expand(18.0);

                     for (LivingEntity candidate : summonWorld.getEntitiesByClass(
                        LivingEntity.class,
                        area,
                        e -> e != s.entity && PowerCombatUtil.canHarm(owner, e)
                     )) {
                        double d = s.entity.squaredDistanceTo(candidate);
                        if (d < best) {
                           chosen = candidate;
                           best = d;
                        }
                     }

                     s.entity.setTarget(chosen);
                     if (chosen == null && s.entity.getEntityWorld() == owner.getEntityWorld() && s.entity.squaredDistanceTo(owner) > 64.0) {
                        s.entity.getNavigation().startMovingTo(owner, 1.1);
                     } else if (chosen != null) {
                        s.entity.getNavigation().stop();
                     }
                  }

                  return false;
               } else {
                  s.entity.discard();
                  return true;
               }
            }
         );
         if (list.isEmpty()) {
            this.ownedSummons.remove(owner.getUuid());
         }
      }
   }

   private static void tickCooldown(Map<UUID, Integer> map, UUID id) {
      int value = map.getOrDefault(id, 0);
      if (value > 0) {
         map.put(id, value - 1);
      }
   }

   public void fireWitherSkullVolley(ServerPlayerEntity player) {
      if (player != null && player.isAlive() && player.getEntityWorld().getServer() != null) {
         ServerWorld id = player.getEntityWorld();
         if (id instanceof ServerWorld) {
            ServerWorld world = id;
            UUID var19 = player.getUuid();
            PowerManager manager = PowerManager.get(id.getServer());
            if (manager.getAssignedPowerType(var19) == PowerType.FIRE_GOD
               && manager.isPowerEnabled(player, PowerType.FIRE_GOD)
               && !manager.isGrimoireSkillsetEnabled(var19)
               && AwakeningManager.isWitherForm(var19)
               && AwakeningManager.isAwakened(manager, var19, PowerType.FIRE_GOD)) {
               if (GraceManager.blockAbility(player)) {
                  return;
               }

               boolean testing = TestMode.isEnabled(var19);
               int remaining = this.witherSkullCooldowns.getOrDefault(var19, 0);
               if (!testing && remaining > 0) {
                  player.sendMessage(Text.literal("Wither Skull Volley recharging: " + (remaining + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
                  return;
               }

               YPConfig config = YPConfig.getInstance();
               EnergyManager energy = EnergyManager.get(id.getServer());
               double previousEnergy = energy.getEnergy(var19);
               if (!energy.trySpendAwakening(player, config.fire_god_wither_skull_energy_cost)) {
                  return;
               }

               Vec3d look = player.getRotationVec(1.0F).normalize();
               Vec3d right = new Vec3d(-look.z, 0.0, look.x);
               if (right.lengthSquared() < 1.0E-6) {
                  right = new Vec3d(1.0, 0.0, 0.0);
               } else {
                  right = right.normalize();
               }

               double speed = Math.max(0.1, config.fire_god_wither_skull_projectile_speed);
               int launched = 0;

               for (int spread = -1; spread <= 1; spread++) {
                  Vec3d velocity = look.add(right.multiply(spread * 0.12)).normalize().multiply(speed);
                  WitherAscendanceSkullEntity skull = new WitherAscendanceSkullEntity(world, player, velocity);
                  skull.setCharged(spread == 0);
                  skull.setPosition(
                     player.getX() + look.x * 0.85 + right.x * spread * 0.35,
                     player.getEyeY() - 0.1 + look.y * 0.85,
                     player.getZ() + look.z * 0.85 + right.z * spread * 0.35
                  );
                  if (world.spawnEntity(skull)) {
                     launched++;
                  }
               }

               if (launched == 0) {
                  energy.setEnergy(var19, previousEnergy);
                  player.sendMessage(Text.literal("The Wither Skulls could not be launched; no Energy was spent.").formatted(Formatting.YELLOW), true);
                  return;
               }

               if (!testing) {
                  this.witherSkullCooldowns.put(var19, Math.max(0, config.fire_god_wither_skull_cooldown_ticks));
               }

               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.0F, 0.9F);
               world.spawnParticles(
                  ParticleTypes.SOUL_FIRE_FLAME, true, true,
                  player.getX() + look.x * 0.8,
                  player.getEyeY() - 0.1 + look.y * 0.8,
                  player.getZ() + look.z * 0.8,
                  12,
                  0.22,
                  0.22,
                  0.22,
                  0.025
               );
               return;
            }

            player.sendMessage(Text.literal("Wither Skulls require your unlocked, active Wither Ascendance.").formatted(Formatting.YELLOW), true);
            return;
         }
      }
   }

   public static void shutdown(MinecraftServer server) {
      if (server != null && PowerManager.get(server).getPowerInstance(PowerType.FIRE_GOD) instanceof FireGodPower fireGod) {
         for (List<FireGodPower.OwnedSummon> list : fireGod.ownedSummons.values()) {
            for (FireGodPower.OwnedSummon summon : list) {
               if (summon.entity != null && !summon.entity.isRemoved()) {
                  summon.entity.discard();
               }
            }
         }
         fireGod.ownedSummons.clear();
         fireGod.activeBlazeBeamUsers.clear();
         fireGod.blazeBeamShotClocks.clear();
      }
   }

   private void clearSummons(UUID id) {
      List<FireGodPower.OwnedSummon> list = this.ownedSummons.remove(id);
      if (list != null) {
         for (FireGodPower.OwnedSummon summon : list) {
            summon.entity.discard();
         }
      }
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.clearSummons(player.getUuid());
      this.clearBlazeBeam(player.getUuid());
      AwakeningManager.endWitherForm(player);
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.clearSummons(player.getUuid());
      this.clearBlazeBeam(player.getUuid());
      AwakeningManager.endWitherForm(player);
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      this.clearSummons(id);
      this.clearBlazeBeam(id);
      this.ghastCooldowns.remove(id);
      this.blazeCooldowns.remove(id);
      this.skeletonCooldowns.remove(id);
      this.witherSkullCooldowns.remove(id);
      AwakeningManager.endWitherForm(player);
   }

   @Override
   public boolean isFireImmune(ServerPlayerEntity player) {
      return !RitualManager.isFireGodTrialActive(player.getUuid());
   }

   @Override
   public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
      if (RitualManager.isFireGodTrialActive(player.getUuid())) {
         return amount;
      } else {
         return !source.isOf(DamageTypes.IN_FIRE) && !source.isOf(DamageTypes.ON_FIRE) && !source.isOf(DamageTypes.LAVA) && !source.isOf(DamageTypes.HOT_FLOOR)
            ? amount
            : 0.0F;
      }
   }

   @Override
   public boolean shouldMobIgnore(ServerPlayerEntity player, MobEntity mob) {
      return mob instanceof BlazeEntity
         || mob instanceof GhastEntity
         || mob instanceof PiglinEntity
         || mob instanceof PiglinBruteEntity
         || mob instanceof ZombifiedPiglinEntity
         || mob instanceof MagmaCubeEntity
         || mob instanceof WitherSkeletonEntity
         || mob instanceof HoglinEntity
         || mob instanceof ZoglinEntity
         || mob instanceof SkeletonEntity
         || mob instanceof EndermanEntity;
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      UUID pId = player.getUuid();
      if (this.ghastCooldowns.getOrDefault(pId, 0) <= 0) {
         ServerWorld world = player.getEntityWorld();
         Vec3d look = player.getRotationVec(1.0F);
         FireballEntity fireball = new FireballEntity(world, player, new Vec3d(look.x * 0.5, look.y * 0.5, look.z * 0.5), 1);
         fireball.setPosition(player.getX(), player.getEyeY(), player.getZ());
         world.spawnEntity(fireball);
         EpicVfx.cue(
            world, player.getId(), "fire_god", "trail", player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, look, 0.9F, 14, 0.0F
         );
         world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GHAST_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F);
         world.spawnParticles(ParticleTypes.FLAME, true, true, player.getX(), player.getEyeY(), player.getZ(), 8, 0.35, 0.35, 0.35, 0.08);
         EpicVfx.activation(world, player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 0.9);
         EpicVfx.sphereBurst(world, ParticleTypes.FLAME, player.getX() + look.x, player.getEyeY() + look.y, player.getZ() + look.z, 24, 0.3);
         this.ghastCooldowns.put(pId, Math.max(0, YPConfig.getInstance().fire_god_cannon_cooldown_ticks));
      } else {
         player.sendMessage(Text.literal("Ghast Cannon on cooldown: " + this.ghastCooldowns.get(pId) / 20 + "s"), true);
      }
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.giveRod(player);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.giveRod(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      this.giveRod(player);
   }

   private boolean giveRod(ServerPlayerEntity player) {
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
         rod.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Ember of the Undying Flame").formatted(Formatting.ITALIC, Formatting.GOLD));
         YPItemData.setFlag(rod, "YPSoulbound", true);
         player.giveOrDropStack(rod);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      return this.giveRod(player) ? List.of("Ember of the Undying Flame") : List.of();
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.ghastCooldowns.remove(playerId);
      this.blazeCooldowns.remove(playerId);
      this.skeletonCooldowns.remove(playerId);
      this.witherSkullCooldowns.remove(playerId);
      this.blazeBeamShotClocks.remove(playerId);
   }

   @Override
   public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
      return stack.isOf(Items.BLAZE_ROD) && YPItemData.hasFlag(stack, "YPSoulbound");
   }

   @Override
   public String hudStatusChip(ServerPlayerEntity player) {
      return AwakeningManager.isWitherForm(player.getUuid()) ? "WITHER FORM" : "";
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      YPConfig cfg = YPConfig.getInstance();
      UUID id = player.getUuid();
      List<HudPill> list = new ArrayList<>();
      list.add(HudPill.ability(AbilityKey.PRIMARY, "Cannon", this.ghastCooldowns.getOrDefault(id, 0), cfg.fire_god_cannon_cooldown_ticks, energy >= cfg.energy_cost_primary));
      list.add(HudPill.ability(AbilityKey.SECONDARY, "Blaze", this.blazeCooldowns.getOrDefault(id, 0), cfg.fire_god_blaze_cooldown_ticks, energy >= cfg.energy_cost_secondary));
      list.add(HudPill.ability(AbilityKey.TERTIARY, "Sentinel", this.skeletonCooldowns.getOrDefault(id, 0), cfg.fire_god_wither_skeleton_cooldown_ticks, energy >= cfg.energy_cost_tertiary));
      if (AwakeningManager.isWitherForm(id)) {
         list.add(HudPill.ability(AbilityKey.WITHER_ASCENDANCE_SKULL, "Skulls", this.witherSkullCooldowns.getOrDefault(id, 0), cfg.fire_god_wither_skull_cooldown_ticks, energy >= cfg.fire_god_wither_skull_energy_cost));
      }
      return list;
   }

   private static final class OwnedSummon {
      final MobEntity entity;
      final UUID owner;
      final boolean blaze;
      int ticks;

      OwnedSummon(MobEntity entity, UUID owner, boolean blaze, int ticks) {
         this.entity = entity;
         this.owner = owner;
         this.blaze = blaze;
         this.ticks = ticks;
      }
   }
}
