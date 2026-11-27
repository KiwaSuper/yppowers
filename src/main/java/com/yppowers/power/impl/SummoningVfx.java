package com.yppowers.power.impl;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

public final class SummoningVfx {
   private SummoningVfx() {
   }

   public static boolean isSummonedClassEntity(Entity entity) {
      return entity == null
         ? false
         : entity.getCommandTags()
            .stream()
            .anyMatch(
               tag -> tag.equals("yppowers_fallout_minion")
                  || tag.equals("yppowers_fire_god_summon")
                  || tag.equals("yppowers_time_clone")
                  || tag.equals("yppowers_kitsune_avatar")
                  || tag.equals("yppowers_spirit")
                  || tag.equals("yppowers_restless_shadow")
                  || tag.startsWith("yppowers_ice_guard_owner:")
                  || tag.startsWith("yppowers_ice_guard_owner_")
            );
   }

   public static void play(ServerWorld world, Entity entity) {
      if (world != null && entity != null && !world.isClient()) {
         double x = entity.getX();
         double y = entity.getY();
         double z = entity.getZ();
         ParticleEffect main = ParticleTypes.REVERSE_PORTAL;
         ParticleEffect accent = ParticleTypes.END_ROD;
         if (hasTag(entity, "yppowers_fire_god_summon")) {
            main = ParticleTypes.FLAME;
            accent = ParticleTypes.SOUL_FIRE_FLAME;
         } else if (hasTag(entity, "yppowers_fallout_minion")) {
            main = ParticleTypes.ELECTRIC_SPARK;
            accent = ParticleTypes.HAPPY_VILLAGER;
         } else if (hasTagPrefix(entity, "yppowers_ice_guard_owner:") || hasTagPrefix(entity, "yppowers_ice_guard_owner_")) {
            main = ParticleTypes.SNOWFLAKE;
            accent = ParticleTypes.CLOUD;
         } else if (hasTag(entity, "yppowers_spirit") || hasTag(entity, "yppowers_restless_shadow")) {
            main = ParticleTypes.SOUL;
            accent = ParticleTypes.SOUL_FIRE_FLAME;
         } else if (hasTag(entity, "yppowers_kitsune_avatar")) {
            main = ParticleTypes.FLAME;
            accent = ParticleTypes.END_ROD;
         }

         int points = 20;

         for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2) * i / 20.0;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            world.spawnParticles(main, true, true, x + dx * 1.05, y + 0.08, z + dz * 1.05, 0, dx * 0.1, 0.025, dz * 0.1, 0.12);
         }

         EpicVfx.helix(world, accent, x, y + 0.05, z, 1.9, 0.72, 1.6, 18, 0.09);
         world.spawnParticles(accent, true, true, x, y + 0.9, z, 16, 0.45, 0.55, 0.45, 0.035);
         world.spawnParticles(main, true, true, x, y + 0.7, z, 12, 0.28, 0.4, 0.28, 0.025);
      }
   }

   private static boolean hasTag(Entity entity, String tag) {
      return entity.getCommandTags().contains(tag);
   }

   private static boolean hasTagPrefix(Entity entity, String prefix) {
      return entity.getCommandTags().stream().anyMatch(tag -> tag.startsWith(prefix));
   }
}
