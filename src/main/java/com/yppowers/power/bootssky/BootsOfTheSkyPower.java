package com.yppowers.power.bootssky;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.energy.EnergyManager;
import com.yppowers.system.EpicVfx;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.artifact.GrimoireFlightManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItemData;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import com.yppowers.progression.RitualManager;
import com.yppowers.system.CombatTagSystem;
import com.yppowers.system.PowerCombatUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public class BootsOfTheSkyPower extends Power {
   private final Map<UUID, Integer> flightTicksRemaining = new HashMap<>();
   private final Map<UUID, Integer> rechargeTicks = new HashMap<>();
   private final Map<UUID, Integer> flightEnergyDrainTicks = new HashMap<>();
   private final Map<UUID, Boolean> flightActive = new HashMap<>();
   private final Map<UUID, BootsOfTheSkyPower.FlightSnapshot> flightSnapshots = new HashMap<>();
   private final Map<UUID, BootsOfTheSkyPower.RaigoState> raigoCharges = new HashMap<>();

   public static void shutdown(MinecraftServer server) {
      if (server != null && PowerManager.get(server).getPowerInstance(PowerType.BOOTS_SKY) instanceof BootsOfTheSkyPower boots) {
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boots.stopFlight(player, false);
         }
         boots.raigoCharges.clear();
         boots.flightActive.clear();
         boots.flightSnapshots.clear();
         boots.flightEnergyDrainTicks.clear();
      }
   }

   public static void serverTick(MinecraftServer server) {
      if (PowerManager.get(server).getPowerInstance(PowerType.BOOTS_SKY) instanceof BootsOfTheSkyPower boots) {
         boots.tickRaigo(server);
      }
   }

   @Override
   public PowerType getType() {
      return PowerType.BOOTS_SKY;
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.giveBoots(player);
      this.applyFeatherFallingState(player);
      this.flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.giveBoots(player);
      this.applyFeatherFallingState(player);
      this.flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.raigoCharges.remove(player.getUuid());
      this.flightEnergyDrainTicks.put(player.getUuid(), 0);
      this.stopFlight(player, false);
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.raigoCharges.remove(player.getUuid());
      this.flightEnergyDrainTicks.put(player.getUuid(), 0);
      this.stopFlight(player, false);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      this.raigoCharges.remove(player.getUuid());
      this.flightEnergyDrainTicks.put(player.getUuid(), 0);
      this.stopFlight(player, false);
      this.giveBoots(player);
      this.applyFeatherFallingState(player);
   }

   private boolean giveBoots(ServerPlayerEntity player) {
      boolean hasBoots = false;

      for (int i = 0; i < player.getInventory().size(); i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (stack.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(stack, "YPSkyBoots")) {
            hasBoots = true;
            break;
         }
      }

      if (!hasBoots) {
         ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
         YPItemData.setFlag(boots, "YPSkyBoots", true);
         boots.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
         player.giveOrDropStack(boots);
         this.applyFeatherFallingState(player);
         return true;
      } else {
         this.applyFeatherFallingState(player);
         return false;
      }
   }

   @Override
   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      return this.giveBoots(player) ? List.of("Boots of the Sky") : List.of();
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.rechargeTicks.remove(playerId);
   }

   private void applyFeatherFallingState(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         boolean enabled = PowerManager.get(player.getEntityWorld().getServer()).isBootsSkyFeatherFallingEnabled(player.getUuid());
         Reference<Enchantment> feather = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING);

         for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(stack, "YPSkyBoots")) {
               EnchantmentHelper.apply(stack, builder -> {
                  builder.remove(entry -> entry.equals(feather));
                  if (enabled) {
                     builder.add(feather, 4);
                  }
               });
            }
         }

         player.currentScreenHandler.sendContentUpdates();
      }
   }

   public void toggleFeatherFalling(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         boolean enabled = PowerManager.get(player.getEntityWorld().getServer()).toggleBootsSkyFeatherFalling(player);
         this.applyFeatherFallingState(player);
         player.sendMessage(
            Text.literal("Boots of the Sky Feather Falling " + (enabled ? "enabled." : "disabled.")).formatted(enabled ? Formatting.GREEN : Formatting.YELLOW),
            true
         );
      }
   }

   private boolean hasSkyBootsEquipped(ServerPlayerEntity player) {
      ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
      return feet.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(feet, "YPSkyBoots");
   }

   public static boolean isElytraCrashProtected(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isGliding()) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getPowerType(player.getUuid()) != PowerType.BOOTS_SKY) {
            return false;
         } else {
            ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
            ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
            return feet.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(feet, "YPSkyBoots") && chest.isOf(Items.ELYTRA);
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
      return stack.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(stack, "YPSkyBoots");
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      UUID pId = player.getUuid();
      if (this.rechargeTicks.getOrDefault(pId, 0) > 0) {
         player.sendMessage(Text.literal("Flight recharging! " + this.rechargeTicks.get(pId) / 20 + " seconds remaining."), true);
      } else {
         boolean active = !this.flightActive.getOrDefault(pId, false);
         if (active && !this.hasSkyBootsEquipped(player)) {
            player.sendMessage(Text.literal("Equip the Boots of the Sky to fly."), true);
         } else {
            this.flightActive.put(pId, active);
            if (active) {
               this.flightSnapshots.putIfAbsent(pId, new BootsOfTheSkyPower.FlightSnapshot(player.getAbilities().allowFlying, player.getAbilities().flying));
               player.getAbilities().allowFlying = true;
               player.getAbilities().flying = true;
               player.sendAbilitiesUpdate();
               ServerWorld var5 = player.getEntityWorld();
               if (var5 instanceof ServerWorld) {
                  EpicVfx.activation(var5, player.getX(), player.getY(), player.getZ(), 0.9);
                  EpicVfx.cue(var5, player.getId(), "boots_sky", "aura", player.getX(), player.getY(), player.getZ(), Vec3d.ZERO, 0.9F, 24, 0.0F);
               }
            } else {
               this.stopFlight(player, true);
            }
         }
      }
   }

   private void stopFlight(ServerPlayerEntity player, boolean slowFall) {
      UUID id = player.getUuid();
      boolean wasActive = this.flightActive.getOrDefault(id, false);
      this.flightActive.put(id, false);
      BootsOfTheSkyPower.FlightSnapshot snapshot = this.flightSnapshots.remove(id);
      if ((snapshot != null || wasActive) && !player.isCreative() && !player.isSpectator()) {
         MinecraftServer server = player.getEntityWorld().getServer();
         boolean keepFlight = server != null && GrimoireFlightManager.hasAnyActiveModFlight(player, PowerManager.get(server));
         if (!keepFlight) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
         }
      }

      if (slowFall && wasActive) {
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
         ServerWorld var7 = player.getEntityWorld();
         if (var7 instanceof ServerWorld) {
            EpicVfx.implosion(var7, ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 1.1, 16, 0.12);
         }
      }
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         ServerWorld id = player.getEntityWorld();
         if (id instanceof ServerWorld) {
            UUID var11 = player.getUuid();
            if (this.raigoCharges.containsKey(var11)) {
               player.sendMessage(Text.literal("Raigo is already charging.").formatted(Formatting.YELLOW), true);
               return;
            }

            YPConfig config = YPConfig.getInstance();
            Vec3d eye = player.getEyePos();
            Vec3d look = player.getRotationVec(1.0F).normalize();
            Vec3d end = eye.add(look.multiply(Math.max(1.0, config.boots_raigo_range)));
            BlockHitResult hit = id.raycast(new RaycastContext(eye, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
            Vec3d target = hit.getType() == Type.MISS ? end : hit.getPos();
            BootsOfTheSkyPower.RaigoState charge = new BootsOfTheSkyPower.RaigoState(var11, id, target, player.getEyePos());
            this.raigoCharges.put(var11, charge);
            EpicVfx.cue(id, player.getId(), "boots_sky", "telegraph", target.x, target.y, target.z, look, 1.0F, 30, 0.0F);
            EnergyManager.get(player.getEntityWorld().getServer()).startRaigoCooldown(player);
            player.sendMessage(Text.literal("RAIGO — lightning gathers above the target!").formatted(Formatting.GOLD, Formatting.BOLD), true);
            id.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.55F);
            return;
         }
      }
   }

   public boolean isRaigoCharging(UUID id) {
      return this.raigoCharges.containsKey(id);
   }

   public boolean isFlightActive(UUID id) {
      return this.flightActive.getOrDefault(id, false);
   }

   private void tickRaigo(MinecraftServer server) {
      if (!this.raigoCharges.isEmpty()) {
         Iterator<Entry<UUID, BootsOfTheSkyPower.RaigoState>> iterator = this.raigoCharges.entrySet().iterator();
         YPConfig config = YPConfig.getInstance();

         while (iterator.hasNext()) {
            BootsOfTheSkyPower.RaigoState state = iterator.next().getValue();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(state.owner);
            if (owner != null
               && owner.isAlive()
               && owner.getEntityWorld().getRegistryKey().equals(state.world.getRegistryKey())
               && PowerManager.get(server).getPowerType(owner.getUuid()) == PowerType.BOOTS_SKY
               && !GraceManager.isActive(server)) {
               if (!state.resolved) {
                  state.ticks++;
                  int chargeTicks = Math.max(1, config.boots_raigo_charge_ticks);
                  this.renderRaigoCharge(state, chargeTicks, config);
                  double progress = Math.min(1.0, (double)state.ticks / chargeTicks);
                  if (state.ticks % 10 == 0) {
                     state.world
                        .playSound(
                           null,
                           BlockPos.ofFloored(state.target.x, state.target.y, state.target.z),
                           SoundEvents.BLOCK_BEACON_AMBIENT,
                           SoundCategory.PLAYERS,
                           0.9F,
                           (float)(0.5 + progress)
                        );
                  }

                  if (state.ticks >= chargeTicks) {
                     this.resolveRaigo(state, owner, config);
                  }
               }

               if (state.resolved && config.boots_raigo_terrain_damage) {
                  this.processRaigoTerrain(state, owner, config);
               }

               if (state.resolved && (!config.boots_raigo_terrain_damage || state.terrainCursor >= state.terrain.size())) {
                  iterator.remove();
               }
            } else {
               iterator.remove();
            }
         }
      }
   }

   private void renderRaigoCharge(BootsOfTheSkyPower.RaigoState state, int chargeTicks, YPConfig config) {
      int orbTravelTicks = Math.max(1, Math.min(20, chargeTicks / 3));
      int rainStart = Math.max(orbTravelTicks + 1, chargeTicks - Math.min(7, Math.max(1, chargeTicks / 4)));
      if (state.ticks <= orbTravelTicks) {
         double raw = Math.min(1.0, (double)state.ticks / orbTravelTicks);
         double eased = raw * raw * (3.0 - 2.0 * raw);
         Vec3d orb = state.orbStart.lerp(state.cloudCenter, eased);
         if (state.ticks == 1) {
            state.world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 16777215), true, true, orb.x, orb.y, orb.z, 1, 0.0, 0.0, 0.0, 0.0);
            state.world.spawnParticles(ParticleTypes.END_ROD, true, true, orb.x, orb.y, orb.z, 24, 0.16, 0.16, 0.16, 0.015);
         }

         state.world.spawnParticles(ParticleTypes.END_ROD, true, true, orb.x, orb.y, orb.z, 8, 0.08, 0.08, 0.08, 0.015);
         state.world.spawnParticles(ParticleTypes.CLOUD, true, true, orb.x, orb.y, orb.z, 3, 0.1, 0.1, 0.1, 0.008);
         state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, orb.x, orb.y, orb.z, 2, 0.12, 0.12, 0.12, 0.02);
      } else {
         double cloudProgress = Math.min(1.0, (double)(state.ticks - orbTravelTicks) / Math.max(1, chargeTicks - orbTravelTicks));
         double maxCloudRadius = Math.max(5.0, Math.min(12.0, config.boots_raigo_radius * 0.55));
         double cloudRadius = 1.5 + (maxCloudRadius - 1.5) * Math.sqrt(cloudProgress);
         if (state.ticks == orbTravelTicks + 1) {
            state.world
               .spawnParticles(
                  TintedParticleEffect.create(ParticleTypes.FLASH, 16777215), true, true,
                  state.cloudCenter.x,
                  state.cloudCenter.y,
                  state.cloudCenter.z,
                  1,
                  0.0,
                  0.0,
                  0.0,
                  0.0
               );
            state.world.spawnParticles(ParticleTypes.END_ROD, true, true, state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z, 28, 0.6, 0.45, 0.6, 0.06);
         }

         if ((state.ticks & 1) == 0) {
            int cloudCount = 14 + (int)(cloudProgress * 14.0);
            int smokeCount = 6 + (int)(cloudProgress * 8.0);
            int sparkCount = 8 + (int)(cloudProgress * 10.0);
            state.world
               .spawnParticles(
                  ParticleTypes.CLOUD, true, true,
                  state.cloudCenter.x,
                  state.cloudCenter.y,
                  state.cloudCenter.z,
                  cloudCount,
                  cloudRadius,
                  1.0 + cloudProgress * 1.6,
                  cloudRadius,
                  0.012
               );
            state.world
               .spawnParticles(
                  ParticleTypes.LARGE_SMOKE, true, true,
                  state.cloudCenter.x,
                  state.cloudCenter.y,
                  state.cloudCenter.z,
                  smokeCount,
                  cloudRadius * 0.72,
                  0.65 + cloudProgress,
                  cloudRadius * 0.72,
                  0.004
               );
            state.world
               .spawnParticles(
                  ParticleTypes.ELECTRIC_SPARK, true, true,
                  state.cloudCenter.x,
                  state.cloudCenter.y,
                  state.cloudCenter.z,
                  sparkCount,
                  cloudRadius * 0.85,
                  cloudRadius * 0.35,
                  cloudRadius * 0.85,
                  0.035
               );
            state.world
               .spawnParticles(
                  ParticleTypes.END_ROD, true, true, state.cloudCenter.x, state.cloudCenter.y, state.cloudCenter.z, 4, cloudRadius * 0.6, 0.8, cloudRadius * 0.6, 0.01
               );
         }

         if (state.ticks >= rainStart && (state.ticks & 1) == 0) {
            double spread = Math.min(8.0, Math.max(2.0, config.boots_raigo_radius * 0.4));
            int bolts = 2 + (state.ticks % 4 == 0 ? 1 : 0);

            for (int bolt = 0; bolt < bolts; bolt++) {
               double phase = state.ticks * 1.73 + bolt * 2.17;
               double offsetX = Math.sin(phase) * spread;
               double offsetZ = Math.cos(phase * 1.19) * spread;
               this.spawnRaigoLightning(state.world, state.target.x + offsetX, state.target.y + 0.1, state.target.z + offsetZ, true);
            }
         }
      }
   }

   private void spawnRaigoLightning(ServerWorld world, double x, double y, double z, boolean cosmetic) {
      LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
      if (lightning != null) {
         lightning.setCosmetic(cosmetic);
         lightning.refreshPositionAfterTeleport(x, y, z);
         world.spawnEntity(lightning);
      }
   }

   private void resolveRaigo(BootsOfTheSkyPower.RaigoState state, ServerPlayerEntity owner, YPConfig config) {
      state.resolved = true;
      double radius = Math.max(1.0, config.boots_raigo_radius);
      Box area = new Box(
         state.target.x - radius, state.target.y - radius, state.target.z - radius, state.target.x + radius, state.target.y + radius, state.target.z + radius
      );
      float damage = (float)Math.max(0.0, Math.min(16.0, config.boots_raigo_max_damage));

      for (LivingEntity target : state.world.getEntitiesByClass(LivingEntity.class, area, e -> PowerCombatUtil.canHarm(owner, e))) {
         if (!(target.squaredDistanceTo(state.target) > radius * radius)) {
            if (damage > 0.0F) {
               RitualManager.beginKillCredit(owner, PowerType.BOOTS_SKY, "raigo_mob_kills", target);

               try {
                  if (target.damage(state.world, state.world.getDamageSources().lightningBolt(), damage) && target instanceof ServerPlayerEntity targetPlayer) {
                     CombatTagSystem.tagPvp(owner, targetPlayer);
                  }
               } finally {
                  RitualManager.endKillCredit(target);
               }
            }

            Vec3d away = target.getEntityPos().subtract(state.target);
            if (away.lengthSquared() > 1.0E-5) {
               target.setVelocity(away.normalize().multiply(0.9).add(0.0, 0.3, 0.0));
               ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
            }
         }
      }

      EpicVfx.cue(state.world, owner.getId(), "boots_sky", "lightning_hit", state.target.x, state.target.y, state.target.z, Vec3d.ZERO, 1.8F, 36, 0.35F);
      this.spawnRaigoLightning(state.world, state.target.x, state.target.y, state.target.z, true);

      for (int bolt = 0; bolt < 6; bolt++) {
         double phase = bolt * Math.PI / 3.0;
         double offset = Math.min(7.0, radius * 0.45);
         this.spawnRaigoLightning(state.world, state.target.x + Math.cos(phase) * offset, state.target.y + 0.1, state.target.z + Math.sin(phase) * offset, true);
      }

      state.world
         .spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, state.target.x, state.target.y, state.target.z, 40, radius * 0.4, radius * 0.4, radius * 0.4, 0.16);
      state.world
         .spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 16777215), true, true, state.target.x, state.target.y, state.target.z, 1, 0.0, 0.0, 0.0, 0.0);
      state.world.spawnParticles(ParticleTypes.EXPLOSION, true, true, state.target.x, state.target.y, state.target.z, 8, radius * 0.25, radius * 0.25, radius * 0.25, 0.0);
      EpicVfx.shockwave(state.world, state.target.x, state.target.y, state.target.z, radius);
      EpicVfx.column(state.world, ParticleTypes.ELECTRIC_SPARK, state.target.x, state.target.y, state.target.z, 14.0, 20);
      EpicVfx.sphereBurst(state.world, ParticleTypes.END_ROD, state.target.x, state.target.y + 0.5, state.target.z, 48, 0.5);
      state.world
         .playSound(
            null,
            BlockPos.ofFloored(state.target.x, state.target.y, state.target.z),
            SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
            SoundCategory.WEATHER,
            5.0F,
            0.65F
         );
      state.world
         .playSound(
            null,
            BlockPos.ofFloored(state.target.x, state.target.y, state.target.z),
            SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
            SoundCategory.WEATHER,
            4.0F,
            0.8F
         );
      if (config.boots_raigo_terrain_damage && config.boots_raigo_max_blocks_per_tick > 0) {
         int r = (int)Math.ceil(radius);
         BlockPos origin = BlockPos.ofFloored(state.target.x, state.target.y, state.target.z);
         double r2 = radius * radius;

         for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
               for (int z = -r; z <= r; z++) {
                  if ((double)x * x + (double)y * y + (double)z * z <= r2) {
                     state.terrain.add(origin.add(x, y, z).toImmutable());
                  }
               }
            }
         }
      }

      owner.sendMessage(Text.literal("RAIGO! The sky answers.").formatted(Formatting.GOLD), true);
   }

   private void processRaigoTerrain(BootsOfTheSkyPower.RaigoState state, ServerPlayerEntity owner, YPConfig config) {
      int processed = 0;
      int max = Math.max(0, Math.min(512, config.boots_raigo_max_blocks_per_tick));

      while (processed < max && state.terrainCursor < state.terrain.size()) {
         BlockPos pos = state.terrain.get(state.terrainCursor++);
         processed++;
         if (state.world.isChunkLoaded(ChunkPos.toLong(pos))
            && state.world.isInBuildLimit(pos)
            && state.world.getWorldBorder().contains(pos)
            && TimeWarperDomainManager.mayModifyBlock(owner, pos)) {
            BlockState block = state.world.getBlockState(pos);
            if (!block.isAir()
               && !block.isOf(Blocks.BEDROCK)
               && !block.isOf(Blocks.HEAVY_CORE)
               && !block.isOf(Blocks.END_PORTAL_FRAME)
               && !(block.getHardness(state.world, pos) < 0.0F)) {
               BlockEntity entity = state.world.getBlockEntity(pos);
               if (entity == null) {
                  state.world.breakBlock(pos, true, owner);
               }
            }
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID pId = player.getUuid();
      if (this.flightActive.getOrDefault(pId, false)) {
         if (GraceManager.isActive(player.getEntityWorld().getServer())) {
            this.stopFlight(player, true);
            player.sendMessage(Text.literal("Flight deactivated during grace period."), true);
         } else if (!this.hasSkyBootsEquipped(player)) {
            this.stopFlight(player, true);
            player.sendMessage(Text.literal("Flight deactivated: Boots of the Sky are not equipped."), true);
         } else if (!player.getAbilities().flying) {
            this.stopFlight(player, true);
            player.sendMessage(Text.literal("Flight deactivated."), true);
         }
      }

      if (this.flightActive.getOrDefault(pId, false)) {
         int drainTicks = this.flightEnergyDrainTicks.getOrDefault(pId, 0) + 1;
         if (drainTicks >= 20) {
            drainTicks -= 20;
            MinecraftServer server = player.getEntityWorld().getServer();
            double upkeep = YPConfig.getInstance().boots_flight_energy_drain_per_second;
            if (server == null || !EnergyManager.get(server).trySpendFlightEnergy(player, upkeep)) {
               this.flightEnergyDrainTicks.put(pId, 0);
               this.stopFlight(player, true);
               player.sendMessage(Text.literal("Flight stopped: not enough Energy."), true);
               return;
            }
         }

         this.flightEnergyDrainTicks.put(pId, drainTicks);
         int ticks = this.flightTicksRemaining.getOrDefault(pId, YPConfig.getInstance().boots_flight_duration_ticks);
         this.flightTicksRemaining.put(pId, --ticks);
         if (ticks % 200 == 0) {
            player.sendMessage(Text.literal("Flight Time Remaining: " + ticks / 20 + "s"), true);
         }

         player.getEntityWorld().spawnParticles(ParticleTypes.CLOUD, true, true, player.getX(), player.getY() - 0.2, player.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
         if (ticks <= 0) {
            this.stopFlight(player, true);
            this.rechargeTicks.put(pId, YPConfig.getInstance().boots_recharge_duration_ticks);
            this.flightEnergyDrainTicks.put(pId, 0);
            player.sendMessage(Text.literal("Flight depleted! Recharging..."), true);
         }
      }

      if (this.rechargeTicks.getOrDefault(pId, 0) > 0) {
         int recharge = this.rechargeTicks.get(pId) - 1;
         this.rechargeTicks.put(pId, recharge);
         if (recharge <= 0) {
            this.flightTicksRemaining.put(pId, YPConfig.getInstance().boots_flight_duration_ticks);
            player.sendMessage(Text.literal("Flight recharged!"), true);
            player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
         }
      }
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.raigoCharges.remove(player.getUuid());
      this.stopFlight(player, true);
      this.flightTicksRemaining.remove(player.getUuid());
      this.rechargeTicks.remove(player.getUuid());
      this.flightEnergyDrainTicks.remove(player.getUuid());
   }

   private record FlightSnapshot(boolean allowFlying, boolean flying) {
   }

   @Override
   public String hudStatusChip(ServerPlayerEntity player) {
      return this.isFlightActive(player.getUuid()) ? "FLYING" : "";
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      if (player.getEntityWorld().getServer() == null) return List.of();
      YPConfig cfg = YPConfig.getInstance();
      UUID id = player.getUuid();
      EnergyManager em = EnergyManager.get(player.getEntityWorld().getServer());
      boolean ff = PowerManager.get(player.getEntityWorld().getServer()).isBootsSkyFeatherFallingEnabled(id);
      return List.of(
         HudPill.toggle(AbilityKey.PRIMARY, "Flight", this.isFlightActive(id)),
         HudPill.ability(AbilityKey.ALTERNATE, "Raigo", em.getRaigoCooldownTicks(id), cfg.boots_raigo_cooldown_ticks, energy >= cfg.boots_raigo_energy_cost),
         HudPill.toggle(AbilityKey.BOOTS_FEATHER_FALLING, "Feather", ff)
      );
   }

   private static final class RaigoState {
      final UUID owner;
      final ServerWorld world;
      final Vec3d target;
      final Vec3d orbStart;
      final Vec3d cloudCenter;
      int ticks;
      boolean resolved;
      final List<BlockPos> terrain = new ArrayList<>();
      int terrainCursor;

      RaigoState(UUID owner, ServerWorld world, Vec3d target, Vec3d orbStart) {
         this.owner = owner;
         this.world = world;
         this.target = target;
         this.orbStart = orbStart;
         this.cloudCenter = new Vec3d(target.x, Math.max(target.y + 16.0, orbStart.y + 24.0), target.z);
      }
   }
}
