package com.yppowers.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.random.Random;

@Environment(EnvType.CLIENT)
public final class VfxParticleFactories {
   private VfxParticleFactories() {
   }

   public static void register() {
      ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();
      register(registry, VfxParticles.FROST_SHARD, 0.6F, 0.88F, 1.0F);
      register(registry, VfxParticles.FOXFIRE_WISP, 1.0F, 0.58F, 0.16F);
      register(registry, VfxParticles.RUNE, 0.85F, 0.72F, 1.0F);
      register(registry, VfxParticles.WEB_STRAND, 0.92F, 0.94F, 1.0F);
      register(registry, VfxParticles.SOUL_WISP, 0.55F, 0.94F, 0.88F);
      register(registry, VfxParticles.SPARK, 1.0F, 0.9F, 0.42F);
      register(registry, VfxParticles.TOXIC_BUBBLE, 0.42F, 1.0F, 0.28F);
      register(registry, VfxParticles.DUST_RING, 0.82F, 0.74F, 0.6F);
   }

   private static void register(ParticleFactoryRegistry registry, SimpleParticleType type, float red, float green, float blue) {
      registry.register(type, sprites -> new VfxParticleFactories.StyledFactory(sprites, red, green, blue));
   }

   private record StyledFactory(SpriteProvider sprites, float red, float green, float blue) implements ParticleFactory<SimpleParticleType> {
      public Particle createParticle(
         SimpleParticleType type, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random
      ) {
         return new VfxSpriteParticle(world, x, y, z, velocityX, velocityY, velocityZ, this.sprites, random, this.red, this.green, this.blue);
      }
   }
}
