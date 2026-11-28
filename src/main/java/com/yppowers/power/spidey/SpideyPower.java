package com.yppowers.power.spidey;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.system.EpicVfx;
import com.yppowers.system.PowerCombatUtil;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItems;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class SpideyPower extends Power {
   private final Map<UUID, Integer> grenadeCooldowns = new HashMap<>();
   private final Map<UUID, Integer> rageCooldowns = new HashMap<>();
   private final Map<UUID, Integer> rageTicks = new HashMap<>();
   private final Map<UUID, Integer> diveCooldowns = new HashMap<>();
   private final Map<UUID, Integer> diveChain = new HashMap<>();
   private final Map<UUID, Integer> diveWindow = new HashMap<>();
   private final Map<UUID, Boolean> mlgEnabled = new HashMap<>();
   private final Map<UUID, Integer> webLaunchBoost = new HashMap<>();
   private final Map<UUID, SpideyPower.WebSwingState> webSwings = new HashMap<>();
   private final Map<UUID, Double> lastY = new HashMap<>();
   private final Map<UUID, List<SpideyPower.TempWeb>> tempWebs = new HashMap<>();
   private static final String WEB_PROJECTILE_TAG = "yppowers_spidey_web_projectile";
   private static final int MAX_TEMP_WEBS_PER_OWNER = 64;

   public static void serverTick(MinecraftServer server) {
      if (PowerManager.get(server).getPowerInstance(PowerType.SPIDEY) instanceof SpideyPower spidey) {
         spidey.tickTemporaryWebs();
      }
   }

   public static void shutdown(MinecraftServer server) {
      if (server != null && PowerManager.get(server).getPowerInstance(PowerType.SPIDEY) instanceof SpideyPower spidey) {
         for (List<SpideyPower.TempWeb> webs : spidey.tempWebs.values()) {
            for (SpideyPower.TempWeb web : webs) {
               if (web.world.isChunkLoaded(ChunkPos.toLong(web.pos)) && web.world.getBlockState(web.pos).isOf(Blocks.COBWEB)) {
                  web.world.setBlockState(web.pos, Blocks.AIR.getDefaultState());
               }
            }
         }
         spidey.tempWebs.clear();
         spidey.webSwings.clear();
         spidey.rageTicks.clear();
         spidey.diveChain.clear();
         spidey.diveWindow.clear();
         spidey.webLaunchBoost.clear();
         spidey.lastY.clear();
      }
   }

   @Override
   public PowerType getType() {
      return PowerType.SPIDEY;
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.mlgEnabled.putIfAbsent(player.getUuid(), true);
      this.ensureWebSlingers(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      this.ensureWebSlingers(player);
   }

   @Override
   public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
      return stack.isOf(YPItems.WEB_SLINGERS);
   }

   private boolean ensureWebSlingers(ServerPlayerEntity player) {
      for (int i = 0; i < player.getInventory().size(); i++) {
         if (player.getInventory().getStack(i).isOf(YPItems.WEB_SLINGERS)) {
            return false;
         }
      }

      ItemStack stack = new ItemStack(YPItems.WEB_SLINGERS);
      stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Web Slingers").formatted(Formatting.AQUA, Formatting.BOLD));
      player.giveOrDropStack(stack);
      return true;
   }

   @Override
   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      return this.ensureWebSlingers(player) ? List.of("Web Slingers") : List.of();
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.grenadeCooldowns.remove(playerId);
      this.rageCooldowns.remove(playerId);
      this.diveCooldowns.remove(playerId);
   }

   public int getDiveChain(UUID owner) {
      return this.diveChain.getOrDefault(owner, 0);
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      this.webSling(player);
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      this.webGrenade(player);
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      this.rageMode(player);
   }

   @Override
   public void onQuaternaryActivate(ServerPlayerEntity player) {
      this.spiderDive(player);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      boolean enabled = !this.mlgEnabled.getOrDefault(player.getUuid(), true);
      this.mlgEnabled.put(player.getUuid(), enabled);
      player.sendMessage(
         Text.literal("Automatic Cobweb MLG " + (enabled ? "enabled" : "disabled")).formatted(enabled ? Formatting.GREEN : Formatting.YELLOW), true
      );
   }

   public void useWebSling(ServerPlayerEntity player) {
      this.webSling(player);
   }

   public void fireWebProjectile(ServerPlayerEntity player) {
      ServerWorld look = player.getEntityWorld();
      if (look instanceof ServerWorld && !GraceManager.isActive(look.getServer())) {
         Vec3d lookx = PowerCombatUtil.safeLook(player);
         SnowballEntity web = new SpideyPower.WebProjectile(look, player);
         web.setPosition(player.getEyePos().add(lookx.multiply(0.45)));
         web.setVelocity(lookx.multiply(Math.max(0.5, YPConfig.getInstance().spidey_web_projectile_speed)));
         web.addCommandTag("yppowers_spidey_web_projectile");
         if (look.spawnEntity(web)) {
            look.spawnParticles(ParticleTypes.CLOUD, true, true, web.getX(), web.getY(), web.getZ(), 6, 0.08, 0.08, 0.08, 0.01);
            look.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.7F, 1.85F);
         }
      }
   }

   public static boolean handleWebProjectileCollision(ProjectileEntity projectile, HitResult hitResult) {
      if (projectile.getCommandTags().contains("yppowers_spidey_web_projectile")
         && projectile.getOwner() instanceof ServerPlayerEntity owner
         && owner.getEntityWorld().getServer() != null) {
         ServerWorld world = (ServerWorld)projectile.getEntityWorld();
         PowerManager manager = PowerManager.get(world.getServer());
         boolean belongsToSpidey = manager.getPowerType(owner.getUuid()) == PowerType.SPIDEY
            && manager.getPowerInstance(PowerType.SPIDEY) instanceof SpideyPower;
         if (belongsToSpidey && !GraceManager.isActive(world.getServer()) && hitResult instanceof BlockHitResult blockHit && hitResult.getType() == Type.BLOCK) {
            SpideyPower spidey = (SpideyPower)manager.getPowerInstance(PowerType.SPIDEY);
            spidey.placeTemporaryWeb(world, blockHit, owner.getUuid());
         }

         projectile.discard();
         return true;
      } else {
         return false;
      }
   }

   private void placeTemporaryWeb(ServerWorld world, BlockHitResult hit, UUID ownerId) {
      BlockPos supportPos = hit.getBlockPos();
      BlockPos webPos = supportPos.offset(hit.getSide());
      ServerPlayerEntity owner = world.getServer() == null ? null : world.getServer().getPlayerManager().getPlayer(ownerId);
      if (owner != null && !TimeWarperDomainManager.mayModifyBlock(owner, webPos)) {
         return;
      }
      if (world.isInBuildLimit(webPos) && world.getWorldBorder().contains(webPos)) {
         BlockState support = world.getBlockState(supportPos);
         if (!support.getCollisionShape(world, supportPos).isEmpty() && support.isSideSolidFullSquare(world, supportPos, hit.getSide())) {
            BlockState destination = world.getBlockState(webPos);
            if (destination.isAir() && destination.getFluidState().isEmpty()) {
               if (world.setBlockState(webPos, Blocks.COBWEB.getDefaultState())) {
                  this.addTemporaryWeb(ownerId, world, webPos);
                  world.spawnParticles(ParticleTypes.CLOUD, true, true, webPos.getX() + 0.5, webPos.getY() + 0.5, webPos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.015);
                  world.spawnParticles(ParticleTypes.END_ROD, true, true, webPos.getX() + 0.5, webPos.getY() + 0.5, webPos.getZ() + 0.5, 8, 0.18, 0.18, 0.18, 0.01);
                  world.playSound(null, webPos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.75F, 1.55F);
               }
            }
         }
      }
   }

   private void addTemporaryWeb(UUID ownerId, ServerWorld world, BlockPos pos) {
      int lifetime = Math.max(20, YPConfig.getInstance().spidey_temporary_web_duration_ticks);
      List<SpideyPower.TempWeb> webs = this.tempWebs.computeIfAbsent(ownerId, ignored -> new ArrayList<>());

      while (webs.size() >= 64) {
         SpideyPower.TempWeb oldest = webs.remove(0);
         if (oldest.world.isChunkLoaded(ChunkPos.toLong(oldest.pos)) && oldest.world.getBlockState(oldest.pos).isOf(Blocks.COBWEB)) {
            oldest.world.setBlockState(oldest.pos, Blocks.AIR.getDefaultState());
         }
      }

      webs.add(new SpideyPower.TempWeb(world, pos, lifetime));
   }

   private void webSling(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var16 = player.getUuid();
         if (this.webSwings.remove(var16) != null) {
            player.sendMessage(Text.literal("Web released — momentum retained.").formatted(Formatting.AQUA), true);
         } else {
            Vec3d look = PowerCombatUtil.safeLook(player);
            double range = Math.max(2.0, YPConfig.getInstance().spidey_web_sling_range);
            Vec3d end = player.getEyePos().add(look.multiply(range));
            BlockHitResult hit = id.raycast(new RaycastContext(player.getEyePos(), end, ShapeType.OUTLINE, FluidHandling.NONE, player));
            if (hit.getType() != Type.BLOCK) {
               player.sendMessage(Text.literal("No solid anchor in web-sling range."), true);
            } else {
               Vec3d anchor = hit.getPos();
               Vec3d pull = anchor.subtract(player.getEntityPos());
               if (!(pull.lengthSquared() < 0.01)) {
                  double velocity = Math.max(0.1, YPConfig.getInstance().spidey_web_sling_velocity);
                  if (this.rageTicks.getOrDefault(var16, 0) > 0) {
                     velocity *= Math.max(1.0, YPConfig.getInstance().spidey_rage_web_velocity_multiplier);
                  }

                  Vec3d towardAnchor = pull.normalize();
                  if (player.isOnGround()) {
                     Vec3d tangent = new Vec3d(-towardAnchor.z, 0.0, towardAnchor.x);
                     if (tangent.lengthSquared() > 1.0E-5) {
                        tangent = tangent.normalize().multiply(velocity * 0.25);
                     }

                     Vec3d impulse = towardAnchor.multiply(velocity).add(tangent);
                     player.addVelocity(impulse.x, impulse.y + 0.12, impulse.z);
                  } else {
                     player.addVelocity(towardAnchor.x * velocity, towardAnchor.y * velocity, towardAnchor.z * velocity);
                     this.webSwings.put(var16, new SpideyPower.WebSwingState(id, anchor, pull.length()));
                  }

                  ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
                  this.webLaunchBoost.put(var16, 40);
                  EpicVfx.cue(
                     id,
                     player.getId(),
                     "spidey",
                     "web_rope",
                     anchor.x,
                     anchor.y,
                     anchor.z,
                     towardAnchor,
                     Math.min(8.0F, Math.max(2.0F, (float)pull.length())),
                     18,
                     0.0F
                  );
                  EpicVfx.activation(id, anchor.x, anchor.y, anchor.z, 0.55);
                  drawWebLine(id, player.getEyePos().add(0.0, -0.3, 0.0), hit.getPos(), 48, 0.7);
                  id.spawnParticles(ParticleTypes.WHITE_SMOKE, true, true, player.getEyePos().x, player.getEyePos().y, player.getEyePos().z, 12, 0.15, 0.15, 0.15, 0.02);
                  id.spawnParticles(ParticleTypes.CRIT, true, true, anchor.x, anchor.y, anchor.z, 12, 0.2, 0.2, 0.2, 0.02);
                  id.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 0.8F, 1.5F);
               }
            }
         }
      }
   }

   private static void drawWebLine(ServerWorld world, Vec3d start, Vec3d end, int maxSteps, double spacing) {
      Vec3d line = end.subtract(start);
      int steps = (int)Math.max(1.0, Math.min((double)maxSteps, line.length() / spacing));

      for (int i = 1; i <= steps; i++) {
         Vec3d point = start.add(line.multiply((double)i / steps));
         world.spawnParticles(ParticleTypes.END_ROD, true, true, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private void tickWebSwing(ServerPlayerEntity player, ServerWorld world) {
      UUID id = player.getUuid();
      SpideyPower.WebSwingState swing = this.webSwings.get(id);
      if (swing != null) {
         if (swing.world == world && player.isAlive() && !player.isOnGround() && swing.age < 1200 && !GraceManager.isActive(world.getServer())) {
            Vec3d offset = player.getEntityPos().subtract(swing.anchor);
            double distance = offset.length();
            if (distance < 1.0E-5) {
               this.webSwings.remove(id);
            } else {
               if (swing.approachTicks > 0 && distance > 2.5) {
                  Vec3d towardAnchor = swing.anchor.subtract(player.getEntityPos()).normalize();
                  double configured = Math.max(0.1, YPConfig.getInstance().spidey_web_sling_velocity);
                  if (this.rageTicks.getOrDefault(id, 0) > 0) {
                     configured *= Math.max(1.0, YPConfig.getInstance().spidey_rage_web_velocity_multiplier);
                  }

                  double pullAcceleration = Math.min(0.18, Math.max(0.04, configured * 0.08));
                  player.addVelocity(towardAnchor.x * pullAcceleration, towardAnchor.y * pullAcceleration, towardAnchor.z * pullAcceleration);
                  swing.ropeLength = Math.max(3.0, Math.min(swing.ropeLength, distance - 0.25));
                  swing.approachTicks--;
               } else {
                  swing.approachTicks = 0;
                  if (distance > swing.ropeLength) {
                     Vec3d outward = offset.multiply(1.0 / distance);
                     Vec3d correctedPosition = swing.anchor.add(outward.multiply(swing.ropeLength));
                     if (world.isSpaceEmpty(player, player.getBoundingBox().offset(correctedPosition.subtract(player.getEntityPos())))) {
                        player.setPosition(correctedPosition);
                     }
                     double outwardSpeed = player.getVelocity().dotProduct(outward);
                     if (outwardSpeed > 0.0) {
                        player.setVelocity(player.getVelocity().subtract(outward.multiply(outwardSpeed)));
                     }
                  }
               }

               ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
               if (++swing.age % 3 == 0) {
                  drawWebLine(world, player.getEyePos().add(0.0, -0.3, 0.0), swing.anchor, 14, 1.5);
               }
            }
         } else {
            this.webSwings.remove(id);
         }
      }
   }

   private void webGrenade(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         ServerWorld world = id;
         UUID var18 = player.getUuid();
         int cd = this.grenadeCooldowns.getOrDefault(var18, 0);
         if (cd > 0) {
            player.sendMessage(Text.literal("Web Grenade cooldown: " + (cd + 19) / 20 + "s"), true);
         } else {
            Vec3d center = player.getEyePos().add(PowerCombatUtil.safeLook(player).multiply(8.0));
            double radius = Math.max(1.0, YPConfig.getInstance().spidey_web_grenade_radius);
            EpicVfx.cue(id, player.getId(), "spidey", "detonation", center.x, center.y, center.z, PowerCombatUtil.safeLook(player), 1.1F, 24, 0.15F);
            EpicVfx.shockwave(id, center.x, center.y, center.z, Math.min(12.0, radius));
            EpicVfx.sphereBurst(id, ParticleTypes.CLOUD, center.x, center.y, center.z, 40, 0.3);
            EpicVfx.sphereBurst(id, ParticleTypes.END_ROD, center.x, center.y, center.z, 24, 0.2);
            AllianceManager allies = AllianceManager.get(id.getServer());
            Box area = new Box(center.x - radius, center.y - radius, center.z - radius, center.x + radius, center.y + radius, center.z + radius);
            int duration = Math.max(1, YPConfig.getInstance().spidey_web_grenade_duration_ticks);

            for (LivingEntity target : id.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
               if (!(target.squaredDistanceTo(center) > radius * radius)
                  && PowerCombatUtil.canHarm(player, target)
                  && !(target instanceof ServerPlayerEntity p && allies.isAlly(player, p))) {
                  target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, 2));
                  target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, duration, 0));
                  target.damage(world, world.getDamageSources().indirectMagic(player, player), (float)Math.max(0.0, YPConfig.getInstance().spidey_web_grenade_damage));
               }
            }

            for (int i = 0; i < 45; i++) {
               double x = center.x + (world.random.nextDouble() * 2.0 - 1.0) * radius;
               double y = center.y + (world.random.nextDouble() * 2.0 - 1.0) * radius * 0.6;
               double z = center.z + (world.random.nextDouble() * 2.0 - 1.0) * radius;
               world.spawnParticles(ParticleTypes.CLOUD, true, true, x, y, z, 1, 0.05, 0.05, 0.05, 0.02);
               world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
            }

            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.0F, 1.2F);
            this.grenadeCooldowns.put(var18, Math.max(0, YPConfig.getInstance().spidey_web_grenade_cooldown_ticks));
         }
      }
   }

   private void rageMode(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      int cd = this.rageCooldowns.getOrDefault(id, 0);
      if (this.rageTicks.getOrDefault(id, 0) > 0) {
         player.sendMessage(Text.literal("Rage Mode is already active."), true);
      } else if (cd > 0) {
         player.sendMessage(Text.literal("Rage Mode cooldown: " + (cd + 19) / 20 + "s"), true);
      } else {
         int duration = Math.max(1, YPConfig.getInstance().spidey_rage_duration_ticks);
         this.rageTicks.put(id, duration);
         this.rageCooldowns.put(id, Math.max(0, YPConfig.getInstance().spidey_rage_cooldown_ticks));
         player.addStatusEffect(
            new StatusEffectInstance(
               StatusEffects.SPEED, duration, Math.max(0, (int)Math.round(YPConfig.getInstance().spidey_rage_speed_multiplier) - 1), false, true
            )
         );
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, duration, 1, false, true));
         player.sendMessage(Text.literal("RAGE MODE — web launch and melee power increased!").formatted(Formatting.RED, Formatting.BOLD), true);
         player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.8F, 1.4F);
         ServerWorld var6 = player.getEntityWorld();
         if (var6 instanceof ServerWorld) {
            EpicVfx.cue(var6, player.getId(), "spidey", "overdrive", player.getX(), player.getY(), player.getZ(), Vec3d.ZERO, 1.0F, 30, 0.0F);
            EpicVfx.shockwave(var6, player.getX(), player.getY(), player.getZ(), 6.0);
            EpicVfx.activation(var6, player.getX(), player.getY(), player.getZ(), 1.2);
         }
      }
   }

   private void spiderDive(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var11 = player.getUuid();
         if (this.diveCooldowns.getOrDefault(var11, 0) > 0) {
            player.sendMessage(Text.literal("Spider Dive cooldown: " + (this.diveCooldowns.get(var11) + 19) / 20 + "s"), true);
         } else {
            int chain = this.diveChain.getOrDefault(var11, 0);
            if (chain == 0) {
               Vec3d look = PowerCombatUtil.safeLook(player);
               player.setVelocity(-look.x * 0.45, 1.05, -look.z * 0.45);
               ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
               this.diveChain.put(var11, 1);
               this.diveWindow.put(var11, Math.max(1, YPConfig.getInstance().spidey_dive_chain_window_ticks));
               id.spawnParticles(ParticleTypes.CLOUD, true, true, player.getX(), player.getY(), player.getZ(), 18, 0.4, 0.2, 0.4, 0.03);
               EpicVfx.activation(id, player.getX(), player.getY(), player.getZ(), 0.8);
               player.sendMessage(Text.literal("Spider Dive: launch! Press B again to dive at a target.").formatted(Formatting.AQUA), true);
            } else {
               LivingEntity target = this.findLookTarget(player, 20.0);
               if (target != null && PowerCombatUtil.canHarm(player, target)) {
                  int maxChain = Math.max(1, YPConfig.getInstance().spidey_dive_max_chain);
                  boolean finisher = chain >= maxChain;
                  double damage = finisher ? YPConfig.getInstance().spidey_dive_finisher_damage : YPConfig.getInstance().spidey_dive_hit_damage;
                  target.damage(id, id.getDamageSources().playerAttack(player), (float)Math.max(0.0, damage));
                  Vec3d dir = target.getEntityPos().subtract(player.getEntityPos()).normalize();
                  target.addVelocity(dir.x * 0.6, finisher ? 0.9 : 0.35, dir.z * 0.6);
                  ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
                  id.spawnParticles(
                     finisher ? ParticleTypes.EXPLOSION : ParticleTypes.CRIT, true, true,
                     target.getX(),
                     target.getBodyY(0.5),
                     target.getZ(),
                     finisher ? 2 : 10,
                     0.3,
                     0.2,
                     0.3,
                     0.02
                  );
                  EpicVfx.cue(
                     id,
                     player.getId(),
                     "spidey",
                     "impact",
                     target.getX(),
                     target.getBodyY(0.5),
                     target.getZ(),
                     dir,
                     finisher ? 1.25F : 0.75F,
                     16,
                     finisher ? 0.2F : 0.0F
                  );
                  if (finisher) {
                     EpicVfx.impact(id, target.getX(), target.getBodyY(0.5), target.getZ(), 1.0);
                  } else {
                     EpicVfx.impact(id, target.getX(), target.getBodyY(0.5), target.getZ(), 0.55);
                  }

                  if (finisher) {
                     this.diveChain.remove(var11);
                     this.diveWindow.remove(var11);
                     this.diveCooldowns.put(var11, Math.max(0, YPConfig.getInstance().spidey_dive_cooldown_ticks));
                  } else {
                     this.diveChain.put(var11, chain + 1);
                     this.diveWindow.put(var11, Math.max(1, YPConfig.getInstance().spidey_dive_chain_window_ticks));
                     player.setVelocity(0.0, 0.9, 0.0);
                     ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
                  }
               } else {
                  player.setVelocity(player.getRotationVec(1.0F).multiply(0.8).add(0.0, 0.2, 0.0));
                  ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
                  this.diveChain.remove(var11);
                  this.diveWindow.remove(var11);
                  this.diveCooldowns.put(var11, Math.max(0, YPConfig.getInstance().spidey_dive_cooldown_ticks));
                  player.sendMessage(Text.literal("Spider Dive missed; sequence ended."), true);
               }
            }
         }
      }
   }

   private LivingEntity findLookTarget(ServerPlayerEntity player, double maxRange) {
      ServerWorld start = player.getEntityWorld();
      if (start instanceof ServerWorld) {
         Vec3d var19 = player.getEyePos();
         Vec3d direction = PowerCombatUtil.safeLook(player);
         Box box = player.getBoundingBox().stretch(direction.multiply(maxRange)).expand(1.5);
         LivingEntity best = null;
         double bestDot = -1.0;

         for (LivingEntity e : start.getEntitiesByClass(
            LivingEntity.class, box, x -> PowerCombatUtil.canHarm(player, x)
         )) {
            Vec3d toward = e.getBoundingBox().getCenter().subtract(var19);
            double distance = toward.length();
            if (!(distance > maxRange) && !(distance < 0.01)) {
               double dot = direction.dotProduct(toward.normalize());
               if (dot > 0.94 && dot > bestDot) {
                  best = e;
                  bestDot = dot;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   @Override
   public void onAttack(ServerPlayerEntity attacker, Entity target) {
      if (target instanceof LivingEntity living && PowerCombatUtil.canHarm(attacker, target) && this.diveChain.getOrDefault(attacker.getUuid(), 0) > 0) {
         int chain = this.diveChain.getOrDefault(attacker.getUuid(), 0);
         if (chain >= Math.max(1, YPConfig.getInstance().spidey_dive_max_chain)) {
            this.diveChain.remove(attacker.getUuid());
            this.diveWindow.remove(attacker.getUuid());
            this.diveCooldowns.put(attacker.getUuid(), Math.max(0, YPConfig.getInstance().spidey_dive_cooldown_ticks));
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      decrement(this.grenadeCooldowns, id);
      decrement(this.rageCooldowns, id);
      decrement(this.diveCooldowns, id);
      decrement(this.webLaunchBoost, id);
      int rage = this.rageTicks.getOrDefault(id, 0);
      if (rage > 0) {
         if (--rage == 0) {
            this.rageTicks.remove(id);
            int debuff = Math.max(1, YPConfig.getInstance().spidey_rage_debuff_ticks);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, debuff, 0, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, debuff, 0, false, true));
            player.sendMessage(
               Text.literal("Heart Palpitations: Rage Mode has ended; movement and melee are weakened briefly.").formatted(Formatting.DARK_RED), true
            );
            ServerWorld var6 = player.getEntityWorld();
            if (var6 instanceof ServerWorld) {
               EpicVfx.shockwave(var6, player.getX(), player.getY(), player.getZ(), 3.0);
            }
         } else {
            this.rageTicks.put(id, rage);
         }
      }

      int window = this.diveWindow.getOrDefault(id, 0);
      if (window > 0) {
         if (--window == 0) {
            this.diveWindow.remove(id);
            this.diveChain.remove(id);
            this.diveCooldowns.put(id, Math.max(0, YPConfig.getInstance().spidey_dive_cooldown_ticks));
         } else {
            this.diveWindow.put(id, window);
         }
      }

      ServerWorld var10 = player.getEntityWorld();
      if (var10 instanceof ServerWorld && player.getEntityWorld().getServer() != null) {
         this.tickWebSwing(player, var10);
         this.tryAutomaticMlg(player, var10);
      }
   }

   private void tickTemporaryWebs() {
      Iterator<Entry<UUID, List<SpideyPower.TempWeb>>> ownerIterator = this.tempWebs.entrySet().iterator();

      while (ownerIterator.hasNext()) {
         List<SpideyPower.TempWeb> webs = ownerIterator.next().getValue();
         Iterator<SpideyPower.TempWeb> webIterator = webs.iterator();

         while (webIterator.hasNext()) {
            SpideyPower.TempWeb web = webIterator.next();
            if (web.ticks > 0) {
               web.ticks--;
            }

            if (web.ticks <= 0 && web.world.isChunkLoaded(ChunkPos.toLong(web.pos))) {
               if (web.world.getBlockState(web.pos).isOf(Blocks.COBWEB)) {
                  web.world.setBlockState(web.pos, Blocks.AIR.getDefaultState());
               }

               webIterator.remove();
            }
         }

         if (webs.isEmpty()) {
            ownerIterator.remove();
         }
      }
   }

   private void tryAutomaticMlg(ServerPlayerEntity player, ServerWorld world) {
      UUID id = player.getUuid();
      double previousY = this.lastY.getOrDefault(id, player.getY());
      double fallSpeed = previousY - player.getY();
      this.lastY.put(id, player.getY());
      if (this.mlgEnabled.getOrDefault(id, true)
         && !GraceManager.isActive(world.getServer())
         && !player.isOnGround()
         && !player.isCreative()
         && !player.isSpectator()
         && !(player.getVelocity().y >= -0.001)
         && !(player.fallDistance <= 3.0)) {
         double descentSpeed = Math.max(fallSpeed, Math.abs(player.getVelocity().y));
         int reach = (int)Math.max(4.0, Math.min(14.0, Math.ceil(descentSpeed * 3.0) + 2.0));
         BlockPos feet = player.getBlockPos();

         for (int dy = 0; dy <= reach; dy++) {
            BlockPos pos = feet.down(dy);
            BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.COBWEB)) {
               return;
            }

            if (!state.getFluidState().isEmpty()) {
               return;
            }

            if (!state.isAir() && !state.getCollisionShape(world, pos).isEmpty()) {
               if (dy == 0) {
                  return;
               }

               BlockPos webPos = pos.up();
               if (!world.isInBuildLimit(webPos)
                  || !world.getWorldBorder().contains(webPos)
                  || !TimeWarperDomainManager.mayModifyBlock(player, webPos)
                  || !world.getBlockState(webPos).isAir()) {
                  return;
               }

               if (world.setBlockState(webPos, Blocks.COBWEB.getDefaultState())) {
                  this.addTemporaryWeb(id, world, webPos);
                  world.spawnParticles(ParticleTypes.CLOUD, true, true, webPos.getX() + 0.5, webPos.getY() + 0.5, webPos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
                  world.playSound(null, webPos, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.8F, 1.6F);
               }

               return;
            }
         }
      }
   }

   private static void decrement(Map<UUID, Integer> map, UUID id) {
      int v = map.getOrDefault(id, 0);
      if (v > 0) {
         map.put(id, v - 1);
      }
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.clearTransient(player.getUuid());
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.clearTransient(player.getUuid());
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      this.clearTransient(id);
      this.clearCooldowns(id);
      this.mlgEnabled.remove(id);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.mlgEnabled.putIfAbsent(player.getUuid(), true);
      this.ensureWebSlingers(player);
   }

   private void clearTransient(UUID id) {
      this.rageTicks.remove(id);
      this.diveChain.remove(id);
      this.diveWindow.remove(id);
      this.webLaunchBoost.remove(id);
      this.webSwings.remove(id);
      this.lastY.remove(id);
   }

   private static final class TempWeb {
      final ServerWorld world;
      final BlockPos pos;
      int ticks;

      TempWeb(ServerWorld world, BlockPos pos, int ticks) {
         this.world = world;
         this.pos = pos.toImmutable();
         this.ticks = Math.max(1, ticks);
      }
   }

   private static final class WebProjectile extends SnowballEntity {
      WebProjectile(ServerWorld world, ServerPlayerEntity owner) {
         super(world, owner, new ItemStack(Items.COBWEB));
      }

      @Override
      protected void onCollision(HitResult hitResult) {
         if (this.getEntityWorld().isClient() || !SpideyPower.handleWebProjectileCollision(this, hitResult)) {
            super.onCollision(hitResult);
         }
      }
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      if (player.getEntityWorld().getServer() == null) return List.of();
      YPConfig cfg = YPConfig.getInstance();
      UUID id = player.getUuid();
      boolean mlgOn = this.mlgEnabled.getOrDefault(id, true);
      return List.of(
         HudPill.ability(AbilityKey.PRIMARY, "Web", 0, 1, true),
         HudPill.ability(AbilityKey.SECONDARY, "Grenade", this.grenadeCooldowns.getOrDefault(id, 0), cfg.spidey_web_grenade_cooldown_ticks, energy >= cfg.spidey_web_grenade_energy_cost),
         HudPill.ability(AbilityKey.TERTIARY, "Rage", this.rageCooldowns.getOrDefault(id, 0), cfg.spidey_rage_cooldown_ticks, energy >= cfg.spidey_rage_energy_cost),
         HudPill.ability(AbilityKey.QUATERNARY, "Dive", this.diveCooldowns.getOrDefault(id, 0), cfg.spidey_dive_cooldown_ticks, this.getDiveChain(id) > 0 || energy >= cfg.spidey_dive_energy_cost),
         HudPill.toggle(AbilityKey.ALTERNATE, "MLG", mlgOn)
      );
   }

   private static final class WebSwingState {
      final ServerWorld world;
      final Vec3d anchor;
      double ropeLength;
      int approachTicks = 10;
      int age;

      WebSwingState(ServerWorld world, Vec3d anchor, double ropeLength) {
         this.world = world;
         this.anchor = anchor;
         this.ropeLength = Math.max(3.0, ropeLength);
      }
   }
}
