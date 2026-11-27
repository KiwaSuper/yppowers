package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class SpiritChannelerPower extends Power {
   private static final int MAX_ACTIVE_SUMMONS = 6;
   private static final int MAX_SUMMON_LIFETIME_TICKS = 12000;
   private final Map<UUID, String> selectedForm = new HashMap<>();
   private final Map<UUID, Integer> summonCooldown = new HashMap<>();
   private final Map<UUID, Integer> stepCooldown = new HashMap<>();
   private final Map<UUID, Integer> ultimateCooldown = new HashMap<>();
   private final Map<UUID, List<SpiritChannelerPower.Summon>> summons = new HashMap<>();
   private final Map<UUID, Integer> warningTicks = new HashMap<>();

   @Override
   public PowerType getType() {
      return PowerType.SPIRIT_CHANNELER;
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      this.summonSpirit(player);
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      this.shadowStep(player);
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      this.activateRestlessShadows(player);
   }

   @Override
   public void onQuaternaryActivate(ServerPlayerEntity player) {
      this.cycleSpirit(player);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      this.cycleSpirit(player);
   }

   private Set<String> unlocks(ServerPlayerEntity player) {
      return PowerManager.get(player.getEntityWorld().getServer()).getSpiritChannelerUnlocks(player.getUuid());
   }

   public void recordKill(ServerPlayerEntity player, Entity killed) {
      if (player.getEntityWorld().getServer() != null) {
         String form = null;
         Item required = null;
         if (killed.getType() == EntityType.WOLF) {
            form = "WOLF";
            required = Items.BONE;
         } else if (killed.getType() == EntityType.GUARDIAN) {
            form = "GUARDIAN";
            required = Items.PRISMARINE_SHARD;
         } else if (killed.getType() == EntityType.WITHER_SKELETON) {
            form = "WRAITH";
            required = Items.PHANTOM_MEMBRANE;
         }

         if (form != null) {
            if (!this.consumeOne(player, required)) {
               player.sendMessage(
                  Text.literal("Spirit trace found. Carry a " + required.getName().getString() + " to bind it permanently.").formatted(Formatting.GRAY), true
               );
            } else {
               boolean newUnlock = PowerManager.get(player.getEntityWorld().getServer()).unlockSpiritForm(player.getUuid(), form);
               if (newUnlock) {
                  player.sendMessage(Text.literal("Spirit Form unlocked permanently: " + form).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
               }
            }
         }
      }
   }

   private boolean consumeOne(ServerPlayerEntity player, Item item) {
      for (int i = 0; i < player.getInventory().size(); i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (stack.isOf(item)) {
            stack.decrement(1);
            return true;
         }
      }

      return false;
   }

   private void cycleSpirit(ServerPlayerEntity player) {
      List<String> forms = new ArrayList<>();

      for (String form : List.of("WOLF", "GUARDIAN", "WRAITH")) {
         if (this.unlocks(player).contains(form)) {
            forms.add(form);
         }
      }

      if (forms.isEmpty()) {
         player.sendMessage(
            Text.literal("No Spirit Forms unlocked. Kill a wolf + carry a bone, a guardian + prismarine shard, or a wither skeleton + phantom membrane.")
               .formatted(Formatting.YELLOW),
            true
         );
      } else {
         String current = this.selectedForm.getOrDefault(player.getUuid(), forms.get(0));
         int index = forms.indexOf(current);
         this.selectedForm.put(player.getUuid(), forms.get((index + 1 + forms.size()) % forms.size()));
         player.sendMessage(Text.literal("Selected Spirit: " + this.selectedForm.get(player.getUuid())).formatted(Formatting.LIGHT_PURPLE), true);
         ServerWorld var6 = player.getEntityWorld();
         if (var6 instanceof ServerWorld) {
            EpicVfx.activation(var6, player.getX(), player.getY(), player.getZ(), 0.65);
         }
      }
   }

   public List<String> getUnlockedSpiritForms(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         Set<String> unlocked = this.unlocks(player);
         return List.of("WOLF", "GUARDIAN", "WRAITH").stream().filter(unlocked::contains).toList();
      } else {
         return List.of();
      }
   }

   public String getSelectedSpiritForm(ServerPlayerEntity player) {
      if (player == null) {
         return "";
      } else {
         List<String> available = this.getUnlockedSpiritForms(player);
         String selected = this.selectedForm.get(player.getUuid());
         return selected != null && available.contains(selected) ? selected : available.stream().findFirst().orElse("");
      }
   }

   public boolean selectSpiritForm(ServerPlayerEntity player, String requested) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getPowerType(player.getUuid()) == PowerType.SPIRIT_CHANNELER
            && manager.isPowerEnabled(player, PowerType.SPIRIT_CHANNELER)
            && !GraceManager.blockAbility(player)) {
            String form = requested == null ? "" : requested.trim().toUpperCase(Locale.ROOT);
            if (!this.getUnlockedSpiritForms(player).contains(form)) {
               player.sendMessage(Text.literal("That Spirit Form is not unlocked.").formatted(Formatting.YELLOW), true);
               return false;
            } else {
               this.selectedForm.put(player.getUuid(), form);
               player.sendMessage(Text.literal("Selected Spirit: " + form).formatted(Formatting.LIGHT_PURPLE), true);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void summonSpirit(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var14 = player.getUuid();
         int cd = this.summonCooldown.getOrDefault(var14, 0);
         if (cd > 0) {
            player.sendMessage(Text.literal("Spirit Summon cooldown: " + (cd + 19) / 20 + "s"), true);
         } else {
            Set<String> available = this.unlocks(player);
            String form = this.selectedForm.getOrDefault(var14, available.stream().sorted().findFirst().orElse(""));
            if (!form.isEmpty() && available.contains(form)) {
               List<SpiritChannelerPower.Summon> list = this.summons.computeIfAbsent(var14, ignored -> new ArrayList<>());
               list.removeIf(s -> s.mob.isRemoved() || !s.mob.isAlive());
               if (list.size() >= 6) {
                  player.sendMessage(Text.literal("Your summon limit is already reached."), true);
               } else {
                  long active = list.stream().filter(s -> !s.ultimate).count();
                  if (active >= 3L) {
                     player.sendMessage(Text.literal("You already have three spirits active."), true);
                  } else {
                     MobEntity mob = (MobEntity)(switch (form) {
                        case "GUARDIAN" -> (IronGolemEntity)EntityType.IRON_GOLEM.create(id, SpawnReason.TRIGGERED);
                        case "WRAITH" -> (VexEntity)EntityType.VEX.create(id, SpawnReason.TRIGGERED);
                        default -> (WolfEntity)EntityType.WOLF.create(id, SpawnReason.TRIGGERED);
                     });
                     if (mob == null) {
                        player.sendMessage(Text.literal("Spirit could not manifest here."), true);
                     } else {
                        mob.refreshPositionAndAngles(player.getX() + 1.0, player.getY(), player.getZ() + 1.0, player.getYaw(), 0.0F);
                        mob.setCustomName(Text.literal(form + " Spirit · " + player.getName().getString()).formatted(Formatting.LIGHT_PURPLE));
                        mob.setCustomNameVisible(true);
                        mob.addCommandTag("yppowers_spirit");
                        mob.addCommandTag("yppowers_owner_" + var14);
                        double hp = form.equals("GUARDIAN") ? 80.0 : (form.equals("WRAITH") ? 18.0 : 30.0);
                        if (mob.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
                           mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
                        }

                        mob.setHealth((float)hp);
                        if (form.equals("WOLF") && mob instanceof WolfEntity wolf) {
                           wolf.setOwner(player);
                        }

                        if (form.equals("GUARDIAN") && mob instanceof IronGolemEntity) {
                           mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                        }

                        if (form.equals("WRAITH")) {
                           mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100000, 0, false, false));
                        }

                        SummonTuning.strengthen(player, mob);
                        id.spawnEntity(mob);
                        list.add(new SpiritChannelerPower.Summon(mob, clampLifetime(YPConfig.getInstance().spirit_lifespan_ticks), false));
                        this.summonCooldown.put(var14, Math.max(20, YPConfig.getInstance().spirit_summon_cooldown_ticks));
                        EpicVfx.cue(id, player.getId(), "spirit_channeler", "summon", mob.getX(), mob.getY(), mob.getZ(), Vec3d.ZERO, 1.0F, 18, 0.0F);
                        id.spawnParticles(ParticleTypes.SOUL, true, true, player.getX(), player.getBodyY(0.6), player.getZ(), 25, 0.5, 0.5, 0.5, 0.04);
                        EpicVfx.activation(id, player.getX(), player.getY(), player.getZ(), 0.9);
                     }
                  }
               }
            } else {
               player.sendMessage(Text.literal("Unlock a Spirit Form before summoning.").formatted(Formatting.YELLOW), true);
            }
         }
      }
   }

   private void shadowStep(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var10 = player.getUuid();
         int cd = this.stepCooldown.getOrDefault(var10, 0);
         if (cd > 0) {
            player.sendMessage(Text.literal("Shadow Step cooldown: " + (cd + 19) / 20 + "s"), true);
         } else {
            Vec3d look = PowerCombatUtil.safeLook(player);
            double range = Math.max(1.0, YPConfig.getInstance().spirit_shadow_step_range);
            BlockPos base = BlockPos.ofFloored(player.getEntityPos().add(look.multiply(range)));
            BlockPos safe = this.findSafeSpot(id, base, 3);
            if (safe == null) {
               player.sendMessage(Text.literal("No safe destination in range."), true);
            } else {
               id.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 0.8, player.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
               EpicVfx.implosion(id, ParticleTypes.PORTAL, player.getX(), player.getY() + 0.2, player.getZ(), 1.8, 16, 0.25);
               player.teleport(id, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch(), false);
               EpicVfx.cue(id, player.getId(), "spirit_channeler", "soul_step", player.getX(), player.getY(), player.getZ(), look, 1.0F, 16, 0.0F);
               EpicVfx.activation(id, player.getX(), player.getY(), player.getZ(), 0.8);
               id.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 0.8, player.getZ(), 30, 0.4, 0.6, 0.4, 0.2);
               this.stepCooldown.put(var10, Math.max(0, YPConfig.getInstance().spirit_shadow_step_cooldown_ticks));
            }
         }
      }
   }

   private BlockPos findSafeSpot(ServerWorld world, BlockPos base, int radius) {
      for (int dy = 2; dy >= -2; dy--) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               BlockPos p = base.add(dx, dy, dz);
               if (world.getWorldBorder().contains(p)
                  && world.getBlockState(p).isAir()
                  && world.getBlockState(p.up()).isAir()
                  && world.getBlockState(p.down()).isSolidBlock(world, p.down())) {
                  return p;
               }
            }
         }
      }

      return null;
   }

   public void activateRestlessShadows(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         ServerWorld world = id;
         if (id.getServer() != null) {
            UUID idx = player.getUuid();
            int cd = this.ultimateCooldown.getOrDefault(idx, 0);
            if (cd > 0) {
               player.sendMessage(Text.literal("Restless Shadows cooldown: " + (cd + 19) / 20 + "s"), true);
               return;
            }

            List<SpiritChannelerPower.Summon> list = this.summons.computeIfAbsent(idx, ignored -> new ArrayList<>());
            list.removeIf(s -> s.mob.isRemoved() || !s.mob.isAlive());
            if (list.size() >= 6) {
               player.sendMessage(Text.literal("Your summon limit is already reached."), true);
               return;
            }

            VexEntity shade = EntityType.VEX.create(id, SpawnReason.TRIGGERED);
            if (shade == null) {
               player.sendMessage(Text.literal("Restless Shadows could not manifest; no Energy was spent."), true);
               return;
            }

            double cost = Math.max(0.0, YPConfig.getInstance().spirit_restless_shadows_energy_cost);
            double energy = EnergyManager.get(id.getServer()).getEnergy(idx);
            boolean catastrophic = energy + 1.0E-6 < cost;
            if (catastrophic) {
               EnergyManager.get(id.getServer()).setEnergy(idx, 0.0);
            } else if (!EnergyManager.get(id.getServer()).trySpendDirect(player, cost, "Restless Shadows")) {
               return;
            }

            int duration = clampLifetime(YPConfig.getInstance().spirit_restless_shadows_duration_ticks);
            shade.refreshPositionAndAngles(player.getX(), player.getY() + 1.0, player.getZ(), player.getYaw(), 0.0F);
            shade.setInvisible(true);
            shade.setCustomName(Text.literal("Restless Shadow").formatted(Formatting.DARK_PURPLE));
            shade.setCustomNameVisible(false);
            shade.addCommandTag("yppowers_restless_shadow");
            shade.addCommandTag("yppowers_owner_" + idx);
            shade.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, 2, false, false));
            double shadeHealth = Math.max(1.0, YPConfig.getInstance().spirit_restless_shadows_health);
            shade.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(shadeHealth);
            shade.setHealth((float)shadeHealth);
            EntityAttributeInstance shadeDamage = shade.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
            if (shadeDamage != null) {
               shadeDamage.setBaseValue(Math.max(1.0, YPConfig.getInstance().spirit_restless_shadows_damage));
            }

            SummonTuning.strengthen(player, shade);
            id.spawnEntity(shade);
            list.add(new SpiritChannelerPower.Summon(shade, duration, true));
            EpicVfx.cue(id, player.getId(), "spirit_channeler", "summon", shade.getX(), shade.getY(), shade.getZ(), Vec3d.ZERO, 1.15F, 20, 0.0F);
            this.ultimateCooldown.put(idx, Math.max(20, YPConfig.getInstance().spirit_restless_shadows_cooldown_ticks));
            if (catastrophic) {
               this.warningTicks.put(idx, 100);
               YPNetworking.syncHudWarning(player, "ARE YOU SURE?", 100);
               player.sendMessage(Text.literal("ARE YOU SURE?").formatted(Formatting.RED, Formatting.BOLD), true);
               double radius = Math.max(1.0, YPConfig.getInstance().spirit_restless_shadows_radius);
               Box area = player.getBoundingBox().expand(radius);

               for (Entity e : id.getOtherEntities(player, area, x -> x.isAlive())) {
                  e.damage(world, world.getDamageSources().magic(), 8.0F);
                  Vec3d dir = e.getEntityPos().subtract(player.getEntityPos()).normalize();
                  e.addVelocity(dir.x * 3.0, 1.2, dir.z * 3.0);
                  ((EntityVelocityAccessor)e).invokeYppowersScheduleVelocityUpdate();
               }

               player.damage(world, world.getDamageSources().magic(), 8.0F);
               Vec3d own = player.getRotationVec(1.0F).multiply(-3.0).add(0.0, 1.2, 0.0);
               player.addVelocity(own);
               ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
               world.spawnParticles(ParticleTypes.SONIC_BOOM, true, true, player.getX(), player.getY() + 0.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
               EpicVfx.shockwave(world, player.getX(), player.getY(), player.getZ(), 10.0);
               EpicVfx.impact(world, player.getX(), player.getY(), player.getZ(), 1.8);
               EpicVfx.cue(world, player.getId(), "spirit_channeler", "detonation", player.getX(), player.getY(), player.getZ(), Vec3d.ZERO, 1.5F, 22, 0.3F);
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0F, 0.5F);
               player.sendMessage(Text.literal("The shadow is uncontrolled! Energy collapsed and a shockwave erupted.").formatted(Formatting.DARK_RED), false);
            } else {
               EpicVfx.activation(id, shade.getX(), shade.getY(), shade.getZ(), 0.65);
               EpicVfx.implosion(id, ParticleTypes.SOUL, shade.getX(), shade.getY() + 0.5, shade.getZ(), 1.2, 14, 0.16);
               id.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.0F, 0.6F);
               id.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.HOSTILE, 1.2F, 0.5F);
               player.sendMessage(Text.literal("Restless Shadows is hunting your enemies."), true);
               id.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.5F, 0.6F);
            }

            return;
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      decrement(this.summonCooldown, id);
      decrement(this.stepCooldown, id);
      decrement(this.ultimateCooldown, id);
      int warning = this.warningTicks.getOrDefault(id, 0);
      if (warning > 0) {
         this.warningTicks.put(id, warning - 1);
      }

      ServerWorld list = player.getEntityWorld();
      if (list instanceof ServerWorld && list.getServer() != null) {
         List<SpiritChannelerPower.Summon> listx = this.summons.get(id);
         if (listx != null) {
            AllianceManager allies = AllianceManager.get(list.getServer());
            listx.removeIf(s -> {
               if (!s.mob.isRemoved() && s.mob.isAlive() && --s.ticks > 0) {
                  if (s.mob.getEntityWorld() != list || s.mob.squaredDistanceTo(player) > 1600.0) {
                     s.mob.teleport(list, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYaw(), 0.0F, false);
                  }

                  if (s.ticks % 5 == 0) {
                     if (GraceManager.isActive(list.getServer())) {
                        s.mob.setTarget(null);
                        return false;
                     }

                     LivingEntity target = PowerCombatUtil.pickDefenseTarget(player, 24.0);
                     s.mob.setTarget(target);
                     if (target == null && s.mob.squaredDistanceTo(player) > 100.0) {
                        s.mob.getNavigation().startMovingTo(player, 1.1);
                     }

                     if (s.ultimate) {
                        creepyShadow(list, player, s, allies);
                     }
                  }

                  return false;
               } else {
                  s.mob.discard();
                  return true;
               }
            });
            if (listx.isEmpty()) {
               this.summons.remove(id);
            }
         }
      }
   }

   private static void creepyShadow(ServerWorld world, ServerPlayerEntity owner, SpiritChannelerPower.Summon s, AllianceManager allies) {
      YPConfig cfg = YPConfig.getInstance();
      double x = s.mob.getX();
      double y = s.mob.getY() + 0.5;
      double z = s.mob.getZ();
      world.spawnParticles(ParticleTypes.SCULK_SOUL, true, true, x, y, z, 2, 0.3, 0.4, 0.3, 0.01);
      world.spawnParticles(ParticleTypes.SQUID_INK, true, true, x, y, z, 3, 0.35, 0.4, 0.35, 0.01);
      world.spawnParticles(ParticleTypes.WITCH, true, true, x, y, z, 3, 0.3, 0.3, 0.3, 0.02);
      if (s.ticks % 40 == 0) {
         world.playSound(null, s.mob.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 1.2F, 0.5F);
      }

      if (s.ticks % 100 == 0) {
         world.playSound(null, s.mob.getBlockPos(), SoundEvents.ENTITY_VEX_AMBIENT, SoundCategory.HOSTILE, 1.0F, 0.5F);
      }

      if (s.ticks % 160 == 0) {
         world.playSound(null, s.mob.getBlockPos(), SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.HOSTILE, 1.0F, 0.6F);
      }

      if (s.ticks % 10 == 0) {
         double radius = Math.max(0.0, cfg.spirit_restless_shadows_darkness_radius);
         if (!(radius <= 0.0)) {
            for (ServerPlayerEntity target : world.getEntitiesByClass(
               ServerPlayerEntity.class, s.mob.getBoundingBox().expand(radius), p -> p != owner && p.isAlive() && !allies.isAlly(owner, p)
            )) {
               if (!(target.squaredDistanceTo(s.mob) > radius * radius)) {
                  target.addStatusEffect(
                     new StatusEffectInstance(StatusEffects.DARKNESS, Math.max(20, cfg.spirit_restless_shadows_darkness_ticks), 0, false, false)
                  );
               }
            }
         }
      }
   }

   private static void decrement(Map<UUID, Integer> map, UUID id) {
      int n = map.getOrDefault(id, 0);
      if (n > 0) {
         map.put(id, n - 1);
      }
   }

   private static int clampLifetime(int ticks) {
      return Math.max(20, Math.min(12000, ticks));
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.clear(player.getUuid());
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.clear(player.getUuid());
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.clear(player.getUuid());
   }

   private void clear(UUID id) {
      List<SpiritChannelerPower.Summon> list = this.summons.remove(id);
      if (list != null) {
         for (SpiritChannelerPower.Summon s : list) {
            s.mob.discard();
         }
      }

      this.warningTicks.remove(id);
      this.summonCooldown.remove(id);
      this.stepCooldown.remove(id);
      this.ultimateCooldown.remove(id);
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.summonCooldown.remove(playerId);
      this.stepCooldown.remove(playerId);
      this.ultimateCooldown.remove(playerId);
   }

   private static final class Summon {
      final MobEntity mob;
      int ticks;
      final boolean ultimate;

      Summon(MobEntity mob, int ticks, boolean ultimate) {
         this.mob = mob;
         this.ticks = ticks;
         this.ultimate = ultimate;
      }
   }
}
