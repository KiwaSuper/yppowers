package com.yppowers.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.particle.BillboardParticle.RenderType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.random.Random;

@Environment(EnvType.CLIENT)
final class VfxSpriteParticle extends BillboardParticle {
   VfxSpriteParticle(
      ClientWorld world,
      double x,
      double y,
      double z,
      double velocityX,
      double velocityY,
      double velocityZ,
      SpriteProvider sprites,
      Random random,
      float red,
      float green,
      float blue
   ) {
      super(world, x, y, z, velocityX, velocityY, velocityZ, sprites.getSprite(random));
      this.maxAge = 12 + random.nextInt(12);
      this.scale = 0.12F + random.nextFloat() * 0.12F;
      this.gravityStrength = 0.0F;
      this.velocityMultiplier = 0.94F;
      this.collidesWithWorld = false;
      this.zRotation = random.nextFloat() * (float) (Math.PI * 2);
      this.setColor(red, green, blue);
      this.setAlpha(0.95F);
   }

   @Override
   public void tick() {
      super.tick();
      if (this.isAlive()) {
         float progress = Math.min(1.0F, (float)this.age / Math.max(1, this.maxAge));
         this.alpha = 0.95F * (1.0F - progress);
         this.scale *= 0.985F;
      }
   }

   @Override
   public ParticleTextureSheet textureSheet() {
      return ParticleTextureSheet.SINGLE_QUADS;
   }

   @Override
   protected RenderType getRenderType() {
      return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
   }
}
