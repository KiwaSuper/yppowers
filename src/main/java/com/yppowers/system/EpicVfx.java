package com.yppowers.system;

import com.yppowers.config.YPConfig;
import com.yppowers.network.YPNetworking;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public final class EpicVfx {
   private EpicVfx() {
   }

   public static void cue(
      ServerWorld world,
      int sourceEntityId,
      String powerId,
      String effectId,
      double x,
      double y,
      double z,
      Vec3d direction,
      float scale,
      int durationTicks,
      float cameraShake
   ) {
      if (world != null
         && enabled()
         && powerId != null
         && effectId != null
         && powerId.matches("[a-z0-9_]{1,32}")
         && effectId.matches("[a-z0-9_.-]{1,64}")
         && Double.isFinite(x)
         && Double.isFinite(y)
         && Double.isFinite(z)) {
         Vec3d safeDirection = direction != null && !(direction.lengthSquared() < 1.0E-8) ? direction.normalize() : Vec3d.ZERO;
         float intensity = (float)Math.max(0.0, Math.min(2.0, YPConfig.getInstance().power_visual_effect_intensity));
         float safeScale = Math.max(0.1F, Math.min(8.0F, scale * intensity));
         int safeDuration = Math.max(1, Math.min(100, durationTicks));
         float safeShake = Math.max(0.0F, Math.min(1.0F, cameraShake));
         long seed = world.getTime() * -7046029254386353131L
            ^ (long)sourceEntityId << 32
            ^ effectId.hashCode()
            ^ Double.doubleToLongBits(x)
            ^ Long.rotateLeft(Double.doubleToLongBits(z), 17);
         YPNetworking.sendVfxEvent(
            world,
            new YPNetworking.VfxEvent(
               powerId,
               effectId,
               sourceEntityId,
               x,
               y,
               z,
               (float)safeDirection.x,
               (float)safeDirection.y,
               (float)safeDirection.z,
               safeScale,
               safeDuration,
               safeShake,
               seed
            )
         );
      }
   }

   private static boolean enabled() {
      YPConfig config = YPConfig.getInstance();
      return config.power_visual_effects_enabled && config.power_visual_effect_intensity > 0.0;
   }

   private static int particleBudget(int requested) {
      return requested > 0 && enabled() ? Math.max(1, (int)Math.round(requested * YPConfig.getInstance().power_visual_effect_intensity)) : 0;
   }

   public static void ring(ServerWorld world, ParticleEffect type, double x, double y, double z, double startRadius, double speed, int points, double lift) {
      int count = particleBudget(points);
      if (count != 0) {
         for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) * i / count;
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            world.spawnParticles(type, true, true, x + c * startRadius, y, z + s * startRadius, 0, c, lift, s, speed);
         }
      }
   }

   public static void shockwave(ServerWorld world, double x, double y, double z, double radius) {
      if (enabled()) {
         double r = Math.max(1.0, Math.min(24.0, radius));
         int points = (int)Math.max(16.0, Math.min(36.0, 16.0 + r * 4.0));
         ring(world, ParticleTypes.CLOUD, x, y + 0.1, z, 0.4, r * 0.045, points, 0.03);
         ring(world, ParticleTypes.ELECTRIC_SPARK, x, y + 0.25, z, 0.3, r * 0.07, points / 2, 0.05);
         ring(world, ParticleTypes.END_ROD, x, y + 0.4, z, 0.3, r * 0.08, points / 2, 0.12);
      }
   }

   public static void implosion(ServerWorld world, ParticleEffect type, double x, double y, double z, double radius, int points, double speed) {
      int count = particleBudget(points);
      if (count != 0) {
         for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2) * i / count;
            double c = Math.cos(angle);
            double s = Math.sin(angle);
            world.spawnParticles(type, true, true, x + c * radius, y, z + s * radius, 0, -c, 0.0, -s, speed);
         }
      }
   }

   public static void sphereBurst(ServerWorld world, ParticleEffect type, double x, double y, double z, int count, double speed) {
      int n = particleBudget(Math.max(1, Math.min(160, count)));
      if (n != 0) {
         for (int i = 0; i < n; i++) {
            double t = (i + 0.5) / n;
            double phi = Math.acos(1.0 - 2.0 * t);
            double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
            world.spawnParticles(type, true, true, x, y, z, 0, Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta), speed);
         }
      }
   }

   public static void darkOrb(ServerWorld world, double x, double y, double z, double radius, int outerPoints) {
      if (enabled()) {
         double r = Math.max(0.25, Math.min(12.0, radius));
         int outerCount = particleBudget(Math.max(24, Math.min(120, outerPoints)));
         int innerCount = particleBudget(Math.max(12, Math.min(60, outerPoints / 2)));
         spawnDustShell(world, x, y, z, r, outerCount, 2364975, 4.0F);
         spawnDustShell(world, x, y, z, r * 0.64, innerCount, 525836, 3.6F);
      }
   }

   private static void spawnDustShell(ServerWorld world, double x, double y, double z, double radius, int count, int color, float scale) {
      if (count > 0) {
         DustParticleEffect dust = new DustParticleEffect(color, Math.max(0.1F, Math.min(4.0F, scale)));

         for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            double yUnit = 1.0 - 2.0 * t;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - yUnit * yUnit));
            double angle = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
            world.spawnParticles(dust, true, true, x + Math.cos(angle) * ring * radius, y + yUnit * radius, z + Math.sin(angle) * ring * radius, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   public static void helix(
      ServerWorld world, ParticleEffect type, double x, double y, double z, double height, double radius, double turns, int points, double upSpeed
   ) {
      int count = particleBudget(points);
      if (count != 0) {
         for (int i = 0; i < count; i++) {
            double f = (double)i / count;
            double angle = (Math.PI * 2) * turns * f;
            world.spawnParticles(type, true, true, x + Math.cos(angle) * radius, y + height * f, z + Math.sin(angle) * radius, 0, 0.0, 1.0, 0.0, upSpeed);
         }
      }
   }

   public static void column(ServerWorld world, ParticleEffect type, double x, double y, double z, double height, int points) {
      int count = particleBudget(points);
      if (count != 0) {
         for (int i = 0; i < count; i++) {
            world.spawnParticles(type, true, true, x, y + height * i / Math.max(1, count), z, 0, 0.0, 1.0, 0.0, 0.18);
         }
      }
   }

   public static void beam(ServerWorld world, ParticleEffect type, Vec3d from, Vec3d to, double step) {
      Vec3d line = to.subtract(from);
      int points = particleBudget((int)Math.max(1.0, Math.min(72.0, line.length() / Math.max(0.15, step))));
      if (points != 0) {
         for (int i = 0; i < points; i++) {
            double fraction = points <= 1 ? 0.0 : (double)i / (points - 1);
            Vec3d p = from.add(line.multiply(fraction));
            world.spawnParticles(type, true, true, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
         }
      }
   }

   public static void flash(ServerWorld world, double x, double y, double z) {
      if (enabled()) {
         world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 16777215), true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   public static void impact(ServerWorld world, double x, double y, double z, double scale) {
      if (enabled()) {
         double s = Math.max(0.35, Math.min(2.5, scale));
         world.spawnParticles(ParticleTypes.END_ROD, true, true, x, y + 0.35 * s, z, particleBudget((int)(12.0 * s)), 0.45 * s, 0.25 * s, 0.45 * s, 0.035 * s);
         world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x, y + 0.2 * s, z, particleBudget((int)(16.0 * s)), 0.7 * s, 0.22 * s, 0.7 * s, 0.055 * s);
         world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, x, y + 0.15 * s, z, particleBudget((int)(10.0 * s)), 0.55 * s, 0.3 * s, 0.55 * s, 0.025 * s);
         world.spawnParticles(ParticleTypes.CLOUD, true, true, x, y + 0.05 * s, z, particleBudget((int)(8.0 * s)), 0.65 * s, 0.08 * s, 0.65 * s, 0.025 * s);
         shockwave(world, x, y, z, 2.0 + 3.0 * s);
         sphereBurst(world, ParticleTypes.END_ROD, x, y + 0.4 * s, z, (int)(14.0 * s), 0.22 * s);
         if (s >= 1.2) {
            flash(world, x, y + 0.5, z);
            world.spawnParticles(ParticleTypes.EXPLOSION, true, true, x, y + 0.5, z, 1, 0.0, 0.0, 0.0, 0.0);
         }

         if (s >= 1.6) {
            column(world, ParticleTypes.END_ROD, x, y, z, 3.0 * s, 10);
         }
      }
   }

   public static void activation(ServerWorld world, double x, double y, double z, double scale) {
      if (enabled()) {
         double s = Math.max(0.35, Math.min(2.0, scale));
         world.spawnParticles(ParticleTypes.END_ROD, true, true, x, y + 0.7 * s, z, particleBudget((int)(14.0 * s)), 0.28 * s, 0.65 * s, 0.28 * s, 0.035 * s);
         world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x, y + 0.6 * s, z, particleBudget((int)(18.0 * s)), 0.5 * s, 0.55 * s, 0.5 * s, 0.045 * s);
         world.spawnParticles(ParticleTypes.ENCHANT, true, true, x, y + 0.8 * s, z, particleBudget((int)(12.0 * s)), 0.45 * s, 0.7 * s, 0.45 * s, 0.25 * s);
         implosion(world, ParticleTypes.END_ROD, x, y + 0.2, z, 2.2 * s, 14, 0.18 * s);
         helix(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 2.2 * s, 0.8 * s, 1.5, 18, 0.1);
         ring(world, ParticleTypes.CLOUD, x, y + 0.1, z, 0.3, 0.12 * s, 16, 0.0);
      }
   }
}
