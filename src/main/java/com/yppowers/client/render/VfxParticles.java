package com.yppowers.client.render;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class VfxParticles {
   public static final SimpleParticleType FROST_SHARD = register("frost_shard");
   public static final SimpleParticleType FOXFIRE_WISP = register("foxfire_wisp");
   public static final SimpleParticleType RUNE = register("rune");
   public static final SimpleParticleType WEB_STRAND = register("web_strand");
   public static final SimpleParticleType SOUL_WISP = register("soul_wisp");
   public static final SimpleParticleType SPARK = register("spark");
   public static final SimpleParticleType TOXIC_BUBBLE = register("toxic_bubble");
   public static final SimpleParticleType DUST_RING = register("dust_ring");

   private VfxParticles() {
   }

   private static SimpleParticleType register(String path) {
      return Registry.register(Registries.PARTICLE_TYPE, Identifier.of("yppowers", path), FabricParticleTypes.simple(true));
   }

   public static void initialize() {
   }
}
