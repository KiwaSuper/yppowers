package com.yppowers.client.render;

import com.yppowers.config.YPConfig;
import com.yppowers.network.YPNetworking;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class VfxManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/VFX");
   private static final int MAX_ACTIVE_EVENTS = 128;
   private static final int MAX_PARTICLES_PER_TICK = 120;
   private static final double RENDER_DISTANCE = 56.0;
   private static final List<VfxManager.ActiveEvent> ACTIVE = new ArrayList<>();
   private static final Set<String> LOGGED_FAILURES = new HashSet<>();
   private static ClientWorld lastWorld;
   private static int lastClientPlayerId = -1;
   private static int particlesThisTick;
   private static int lastLocalSource = -1;
   private static float lastCameraShake;
   private static float lastFovPunch;

   private VfxManager() {
   }

   public static void accept(YPNetworking.VfxEvent event) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (event != null && client.world != null && client.player != null && valid(event) && YPConfig.getInstance().power_visual_effects_enabled) {
         if (ACTIVE.size() >= 128) {
            ACTIVE.remove(0);
         }

         ACTIVE.add(new VfxManager.ActiveEvent(event));
      }
   }

   private static boolean valid(YPNetworking.VfxEvent event) {
      return event.powerId() != null
         && event.powerId().matches("[a-z0-9_]{1,32}")
         && event.effectId() != null
         && event.effectId().matches("[a-z0-9_.-]{1,64}")
         && Double.isFinite(event.x())
         && Double.isFinite(event.y())
         && Double.isFinite(event.z())
         && Math.abs(event.x()) <= 3.0E7
         && Math.abs(event.z()) <= 3.0E7
         && Float.isFinite(event.directionX())
         && Float.isFinite(event.directionY())
         && Float.isFinite(event.directionZ())
         && Float.isFinite(event.scale())
         && event.scale() > 0.0F
         && event.durationTicks() >= 1
         && event.durationTicks() <= 100
         && Float.isFinite(event.cameraShake())
         && event.cameraShake() >= 0.0F
         && event.cameraShake() <= 1.0F;
   }

   public static void tick(MinecraftClient client) {
      int playerId = client.player == null ? -1 : client.player.getId();
      if (client.world != lastWorld || playerId != lastClientPlayerId) {
         clear();
         lastWorld = client.world;
         lastClientPlayerId = playerId;
      }

      if (client.world != null && client.player != null && !client.player.isDead()) {
         particlesThisTick = 0;
         lastLocalSource = -1;
         lastCameraShake = 0.0F;
         lastFovPunch = 0.0F;
         Iterator<VfxManager.ActiveEvent> iterator = ACTIVE.iterator();

         while (iterator.hasNext()) {
            VfxManager.ActiveEvent active = iterator.next();
            if (active.age >= active.duration) {
               iterator.remove();
            } else {
               try {
                  renderTick(client, active);
               } catch (LinkageError | RuntimeException var6) {
                  String id = active.event.effectId();
                  if (LOGGED_FAILURES.add(id)) {
                     LOGGER.warn("Skipping YP Powers visual effect '{}' after a client rendering error", id, var6);
                  }

                  iterator.remove();
               }

               active.age++;
            }
         }
      } else {
         clear();
      }
   }

   public static void clear() {
      ACTIVE.clear();
      particlesThisTick = 0;
      lastLocalSource = -1;
      lastCameraShake = 0.0F;
      lastFovPunch = 0.0F;
   }

   public static void clearSource(int sourceEntityId) {
      if (sourceEntityId >= 0) {
         ACTIVE.removeIf(active -> active.event.sourceEntityId() == sourceEntityId);
         if (lastLocalSource == sourceEntityId) {
            lastLocalSource = -1;
            lastCameraShake = 0.0F;
            lastFovPunch = 0.0F;
         }
      }
   }

   public static float cameraShakeFor(int focusedEntityId) {
      return focusedEntityId == lastLocalSource ? lastCameraShake : 0.0F;
   }

   public static float fovPunchFor(int focusedEntityId) {
      return focusedEntityId == lastLocalSource ? lastFovPunch : 0.0F;
   }

   private static void renderTick(MinecraftClient client, VfxManager.ActiveEvent active) {
      YPNetworking.VfxEvent event = active.event;
      Vec3d origin = new Vec3d(event.x(), event.y(), event.z());
      double distanceSquared = client.player.getEntityPos().squaredDistanceTo(origin);
      if (!(distanceSquared > 3136.0)) {
         double distanceScale = distanceSquared > 1764.0 ? 0.35 : 1.0;
         float scale = Math.max(0.15F, Math.min(8.0F, event.scale()));
         float progress = (float)active.age / Math.max(1, active.duration);
         Random random = active.random;
         ParticleEffect particle = particleFor(event.powerId(), event.effectId());
         String effect = event.effectId().toLowerCase(Locale.ROOT);
         if (active.age == 0 && YPConfig.getInstance().power_visual_sound_layers_enabled) {
            playLayeredSound(client.world, event, effect, scale);
         }

         if (event.sourceEntityId() >= 0 && client.player.getId() == event.sourceEntityId()) {
            float envelope = (float)Math.sin(Math.PI * Math.min(1.0, (double)progress));
            lastLocalSource = event.sourceEntityId();
            if (YPConfig.getInstance().power_visual_screen_shake_enabled) {
               lastCameraShake = Math.max(lastCameraShake, event.cameraShake() * envelope);
            }

            if (YPConfig.getInstance().power_visual_fov_punch_enabled) {
               lastFovPunch = Math.max(lastFovPunch, event.cameraShake() * 0.12F * envelope);
            }
         }

         if (isBlackHole(effect)) {
            renderBlackHole(client.world, origin, particle, scale, active.age, random, distanceScale);
         } else if (!effect.contains("tsunami") && !effect.contains("water_wave") && !effect.contains("sink_ripple")) {
            if (!effect.contains("web_rope") && !effect.contains("grip_tether") && !effect.contains("beam")) {
               if (!effect.contains("domain_edge")
                  && !effect.contains("gateway")
                  && !effect.contains("telegraph")
                  && !effect.contains("charge")
                  && !effect.contains("windup")
                  && !effect.contains("warning")) {
                  if (isImpact(effect)) {
                     if (active.age == 0) {
                        renderGroundTelegraph(client.world, origin, particle, scale, 0, random, distanceScale);
                     }

                     if (active.age == 3) {
                        renderImpact(client.world, origin, particle, scale, random, distanceScale);
                     }

                     if (active.age == 9) {
                        renderAftermath(client.world, origin, particle, scale, random, distanceScale);
                     }
                  } else if (!effect.contains("trail")
                     && !effect.contains("wake")
                     && !effect.contains("tunnel")
                     && !effect.contains("arrival")
                     && !effect.contains("swap")
                     && !effect.contains("transition")
                     && !effect.contains("phase")
                     && !effect.contains("recruit")) {
                     if (!effect.contains("aura")
                        && !effect.contains("field")
                        && !effect.contains("pulse")
                        && !effect.contains("overdrive")
                        && !effect.contains("passive")
                        && !effect.contains("falling")) {
                        if (active.age == 0) {
                           renderBurst(client.world, origin, particle, scale, random, distanceScale);
                        }
                     } else {
                        if (active.age % 3 == 0) {
                           renderAura(client.world, origin, particle, scale, active.age, random, distanceScale);
                        }
                     }
                  } else {
                     if (active.age % 2 == 0) {
                        renderSpiral(client.world, origin, particle, scale, active.age, random, distanceScale);
                     }
                  }
               } else {
                  if (active.age % 3 == 0) {
                     renderGroundTelegraph(client.world, origin, particle, scale, active.age, random, distanceScale);
                  }

                  if (active.age == Math.max(2, active.duration / 2)) {
                     renderBurst(client.world, origin, particle, scale * 0.45F, random, distanceScale);
                  }
               }
            } else {
               if (active.age % 2 == 0) {
                  renderLine(client.world, origin, direction(event), scale, particle, random, distanceScale);
               }
            }
         } else {
            if (active.age % 2 == 0) {
               renderWaterWave(client.world, origin, direction(event), particle, scale, active.age, random, distanceScale);
            }
         }
      }
   }

   private static void renderGroundTelegraph(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, int age, Random random, double density) {
      int points = budget((int)(28.0 * density));
      if (points != 0) {
         double radius = Math.max(0.8, Math.min(24.0, scale * (1.0 + age * 0.06)));
         ParticleEffect rune = customOr(particle, VfxParticles.RUNE);

         for (int i = 0; i < points && canSpawn(); i++) {
            double angle = (Math.PI * 2) * i / points;
            double pulse = 1.0 + Math.sin(age * 0.4 + angle * 3.0) * 0.04;
            double x = center.x + Math.cos(angle) * radius * pulse;
            double z = center.z + Math.sin(angle) * radius * pulse;
            spawn(
               world,
               (ParticleEffect)(i % 2 == 0 ? rune : VfxParticles.DUST_RING),
               x,
               center.y + 0.08,
               z,
               -Math.cos(angle) * 0.02,
               0.025,
               -Math.sin(angle) * 0.02
            );
         }

         if (age % 6 == 0) {
            for (int i = 0; i < 4 && canSpawn(); i++) {
               double a = random.nextDouble() * Math.PI * 2.0;
               double r = radius * (0.25 + random.nextDouble() * 0.5);
               spawn(world, rune, center.x + Math.cos(a) * r, center.y + 0.12, center.z + Math.sin(a) * r, 0.0, 0.05, 0.0);
            }
         }
      }
   }

   private static void renderImpact(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, Random random, double density) {
      int points = budget((int)(36.0 * density * Math.min(2.0F, scale)));
      double radius = Math.max(1.0, Math.min(12.0, scale * 2.2));

      for (int i = 0; i < points && canSpawn(); i++) {
         double angle = (Math.PI * 2) * i / points;
         double speed = 0.1 + random.nextDouble() * 0.16;
         spawn(
            world,
            (ParticleEffect)(i % 3 == 0 ? VfxParticles.DUST_RING : particle),
            center.x + Math.cos(angle) * 0.35,
            center.y + 0.12,
            center.z + Math.sin(angle) * 0.35,
            Math.cos(angle) * speed,
            0.025 + random.nextDouble() * 0.14,
            Math.sin(angle) * speed
         );
      }

      renderGroundTelegraph(world, center, VfxParticles.RUNE, (float)radius, 4, random, density * 0.65);
      if (scale >= 1.2F) {
         renderColumn(world, center, VfxParticles.SPARK, scale, density);
      }
   }

   private static void renderAftermath(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, Random random, double density) {
      int count = budget((int)(12.0 * density));

      for (int i = 0; i < count && canSpawn(); i++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double radius = Math.max(0.5, (double)scale) * (0.4 + random.nextDouble() * 1.2);
         spawn(
            world,
            (ParticleEffect)(i % 2 == 0 ? particle : VfxParticles.SOUL_WISP),
            center.x + Math.cos(angle) * radius,
            center.y + 0.25 + random.nextDouble(),
            center.z + Math.sin(angle) * radius,
            Math.cos(angle) * 0.01,
            0.035 + random.nextDouble() * 0.06,
            Math.sin(angle) * 0.01
         );
      }
   }

   private static void renderBurst(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, Random random, double density) {
      int count = budget((int)(22.0 * density * Math.min(1.8F, Math.max(0.4F, scale))));

      for (int i = 0; i < count && canSpawn(); i++) {
         double y = random.nextDouble() * 2.0 - 1.0;
         double angle = random.nextDouble() * Math.PI * 2.0;
         double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
         double speed = 0.08 + random.nextDouble() * 0.18;
         spawn(
            world,
            (ParticleEffect)(i % 4 == 0 ? VfxParticles.SPARK : particle),
            center.x,
            center.y + 0.5,
            center.z,
            Math.cos(angle) * ring * speed,
            y * speed,
            Math.sin(angle) * ring * speed
         );
      }
   }

   private static void renderSpiral(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, int age, Random random, double density) {
      int count = budget((int)(10.0 * density));
      double radius = Math.max(0.35, Math.min(4.0, scale * 0.55));

      for (int i = 0; i < count && canSpawn(); i++) {
         double angle = age * 0.42 + (Math.PI * 2) * i / Math.max(1, count);
         double y = 0.15 + i % 4 * 0.26;
         spawn(
            world,
            (ParticleEffect)(i % 3 == 0 ? VfxParticles.SPARK : particle),
            center.x + Math.cos(angle) * radius,
            center.y + y,
            center.z + Math.sin(angle) * radius,
            -Math.sin(angle) * 0.025,
            0.05,
            Math.cos(angle) * 0.025
         );
      }
   }

   private static void renderAura(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, int age, Random random, double density) {
      int count = budget((int)(10.0 * density));
      double radius = Math.max(0.5, Math.min(5.0, (double)scale));

      for (int i = 0; i < count && canSpawn(); i++) {
         double angle = age * 0.16 + (Math.PI * 2) * i / Math.max(1, count);
         double y = 0.2 + i % 5 * 0.32;
         spawn(
            world,
            particle,
            center.x + Math.cos(angle) * radius,
            center.y + y,
            center.z + Math.sin(angle) * radius,
            -Math.sin(angle) * 0.01,
            0.025,
            Math.cos(angle) * 0.01
         );
      }
   }

   private static void renderBlackHole(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, int age, Random random, double density) {
      int count = budget((int)(28.0 * density));
      double coreRadius = Math.max(0.6, Math.min(5.0, scale * 0.7));

      for (int i = 0; i < count && canSpawn(); i++) {
         double angle = age * 0.24 + (Math.PI * 2) * i / Math.max(1, count);
         double radius = coreRadius * (0.8 + i % 3 * 0.36);
         double y = center.y + Math.sin(angle * 2.0) * 0.2;
         double x = center.x + Math.cos(angle) * radius;
         double z = center.z + Math.sin(angle) * radius;
         double dx = center.x - x;
         double dy = center.y + 0.25 - y;
         double dz = center.z - z;
         double length = Math.max(0.001, Math.sqrt(dx * dx + dy * dy + dz * dz));
         spawn(world, (ParticleEffect)(i % 3 == 0 ? VfxParticles.SOUL_WISP : particle), x, y, z, dx / length * 0.09, dy / length * 0.09, dz / length * 0.09);
      }

      if (age % 5 == 0) {
         renderGroundTelegraph(world, center, VfxParticles.RUNE, (float)(coreRadius * 1.8), age, random, density * 0.6);
      }
   }

   private static void renderWaterWave(
      ClientWorld world, Vec3d origin, Vec3d rawDirection, ParticleEffect particle, float scale, int age, Random random, double density
   ) {
      Vec3d direction = rawDirection.lengthSquared() < 1.0E-5 ? new Vec3d(0.0, 0.0, 1.0) : rawDirection.normalize();
      Vec3d side = new Vec3d(-direction.z, 0.0, direction.x);
      int count = budget((int)(24.0 * density));
      double radius = Math.max(1.0, Math.min(16.0, scale * (1.0 + age * 0.04)));

      for (int i = 0; i < count && canSpawn(); i++) {
         double f = (double)i / Math.max(1, count - 1);
         double across = (f - 0.5) * radius * 2.0;
         double height = Math.sin(f * Math.PI) * Math.min(8.0, scale * 0.7);
         double curl = Math.sin(age * 0.18 + f * Math.PI) * 0.7;
         double x = origin.x + side.x * across + direction.x * curl;
         double z = origin.z + side.z * across + direction.z * curl;
         spawn(
            world,
            (ParticleEffect)(i % 4 == 0 ? VfxParticles.TOXIC_BUBBLE : particle),
            x,
            origin.y + height,
            z,
            direction.x * 0.03,
            0.04 + random.nextDouble() * 0.06,
            direction.z * 0.03
         );
      }
   }

   private static void renderLine(ClientWorld world, Vec3d origin, Vec3d rawDirection, float length, ParticleEffect particle, Random random, double density) {
      Vec3d direction = rawDirection.lengthSquared() < 1.0E-5 ? new Vec3d(0.0, 0.0, 1.0) : rawDirection.normalize();
      int points = budget((int)Math.min(36.0, Math.max(8.0F, length * 4.0F) * density));
      double cappedLength = Math.max(2.0, Math.min(36.0, (double)length));

      for (int i = 0; i < points && canSpawn(); i++) {
         double f = (double)i / Math.max(1, points - 1);
         double wiggle = Math.sin(f * Math.PI * 5.0 + random.nextDouble() * 0.2) * 0.07;
         spawn(
            world,
            (ParticleEffect)(i % 3 == 0 ? VfxParticles.WEB_STRAND : particle),
            origin.x + direction.x * cappedLength * f,
            origin.y + direction.y * cappedLength * f + wiggle,
            origin.z + direction.z * cappedLength * f,
            -direction.x * 0.015,
            0.015,
            -direction.z * 0.015
         );
      }
   }

   private static void renderColumn(ClientWorld world, Vec3d center, ParticleEffect particle, float scale, double density) {
      int count = budget((int)(10.0 * density));
      double height = Math.max(2.0, Math.min(12.0, scale * 3.0));

      for (int i = 0; i < count && canSpawn(); i++) {
         double angle = (Math.PI * 2) * i / Math.max(1, count);
         spawn(
            world, particle, center.x + Math.cos(angle) * 0.35, center.y + height * i / Math.max(1, count), center.z + Math.sin(angle) * 0.35, 0.0, 0.09, 0.0
         );
      }
   }

   private static void playLayeredSound(ClientWorld world, YPNetworking.VfxEvent event, String effect, float scale) {
      float intensity = (float)Math.max(0.0, Math.min(2.0, YPConfig.getInstance().power_visual_effect_intensity));
      if (!(intensity <= 0.0F)) {
         double x = event.x();
         double y = event.y();
         double z = event.z();
         float volume = Math.min(1.0F, (0.35F + scale * 0.12F) * intensity);
         if (isImpact(effect)) {
            play(world, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), volume, 0.82F);
            play(world, x, y, z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, volume * 0.36F, 1.2F);
         } else if (effect.contains("swap") || effect.contains("teleport") || effect.contains("gate")) {
            play(world, x, y, z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, volume * 0.72F, 1.25F);
            play(world, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, volume * 0.42F, 1.6F);
         } else if (effect.contains("charge") || effect.contains("telegraph") || effect.contains("windup")) {
            play(world, x, y, z, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, volume * 0.52F, 0.8F);
            play(world, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, volume * 0.48F, 0.75F);
         } else if (isBlackHole(effect)) {
            play(world, x, y, z, SoundEvents.BLOCK_END_PORTAL_SPAWN, volume * 0.62F, 0.55F);
            play(world, x, y, z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, volume * 0.28F, 0.62F);
         } else if (!effect.contains("water") && !effect.contains("tsunami") && !effect.contains("wake")) {
            play(world, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, volume * 0.5F, 1.15F);
         } else {
            play(world, x, y, z, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), volume * 0.7F, 1.1F);
            play(world, x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, volume * 0.3F, 0.9F);
         }
      }
   }

   private static void play(ClientWorld world, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
      world.playSoundClient(x, y, z, sound, SoundCategory.PLAYERS, volume, pitch, true);
   }

   private static Vec3d direction(YPNetworking.VfxEvent event) {
      return new Vec3d(event.directionX(), event.directionY(), event.directionZ());
   }

   private static ParticleEffect particleFor(String powerId, String effectId) {
      String key = (powerId + "." + effectId).toLowerCase(Locale.ROOT);
      if (key.contains("frost") || key.contains("ice") || key.contains("meteor") || key.contains("heat_blast")) {
         return VfxParticles.FROST_SHARD;
      } else if (key.contains("fox") || key.contains("fire") || key.contains("blaze")) {
         return VfxParticles.FOXFIRE_WISP;
      } else if (key.contains("web") || key.contains("grip_tether")) {
         return VfxParticles.WEB_STRAND;
      } else if (key.contains("toxic") || key.contains("fallout") || key.contains("radiation")) {
         return VfxParticles.TOXIC_BUBBLE;
      } else if (key.contains("soul") || key.contains("spirit") || key.contains("time_warper") || key.contains("black_hole")) {
         return VfxParticles.SOUL_WISP;
      } else if (key.contains("rune") || key.contains("gate") || key.contains("teleport") || key.contains("divinity")) {
         return VfxParticles.RUNE;
      } else {
         return !key.contains("dust") && !key.contains("ram") && !key.contains("punch") && !key.contains("impact")
            ? VfxParticles.SPARK
            : VfxParticles.DUST_RING;
      }
   }

   private static ParticleEffect customOr(ParticleEffect preferred, ParticleEffect fallback) {
      return YPConfig.getInstance().power_visual_custom_particles_enabled ? preferred : fallback;
   }

   private static boolean isImpact(String effect) {
      return effect.contains("impact")
         || effect.contains("slam")
         || effect.contains("detonation")
         || effect.contains("burst")
         || effect.contains("meteor")
         || effect.contains("strike")
         || effect.contains("sonic_boom")
         || effect.contains("lightning_hit")
         || effect.contains("hit");
   }

   private static boolean isBlackHole(String effect) {
      return effect.contains("black_hole") || effect.contains("accretion");
   }

   private static int budget(int requested) {
      double intensity = Math.max(0.0, Math.min(2.0, YPConfig.getInstance().power_visual_effect_intensity));
      return Math.max(0, Math.min(48, (int)Math.round(requested * intensity)));
   }

   private static boolean canSpawn() {
      return particlesThisTick < 120;
   }

   private static void spawn(ClientWorld world, ParticleEffect type, double x, double y, double z, double vx, double vy, double vz) {
      if (particlesThisTick < 120) {
         if (!YPConfig.getInstance().power_visual_custom_particles_enabled
            && (
               type == VfxParticles.FROST_SHARD
                  || type == VfxParticles.FOXFIRE_WISP
                  || type == VfxParticles.RUNE
                  || type == VfxParticles.WEB_STRAND
                  || type == VfxParticles.SOUL_WISP
                  || type == VfxParticles.SPARK
                  || type == VfxParticles.TOXIC_BUBBLE
                  || type == VfxParticles.DUST_RING
            )) {
            type = ParticleTypes.END_ROD;
         }

         world.addParticleClient(type, true, true, x, y, z, vx, vy, vz);
         particlesThisTick++;
      }
   }

   private static final class ActiveEvent {
      private final YPNetworking.VfxEvent event;
      private final int duration;
      private final Random random;
      private int age;

      private ActiveEvent(YPNetworking.VfxEvent event) {
         this.event = event;
         this.duration = Math.max(6, Math.min(80, event.durationTicks()));
         this.random = new Random(event.seed());
      }
   }
}
