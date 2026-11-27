package com.yppowers.mixin.client;

import com.yppowers.client.render.BlackenedMobRenderStateAccess;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({LivingEntityRenderState.class})
public abstract class BlackenedMobRenderStateMixin implements BlackenedMobRenderStateAccess {
   @Unique
   private boolean yppowers$blackenedByDarkness;

   @Override
   public boolean yppowers$isBlackenedByDarkness() {
      return this.yppowers$blackenedByDarkness;
   }

   @Override
   public void yppowers$setBlackenedByDarkness(boolean blackened) {
      this.yppowers$blackenedByDarkness = blackened;
   }
}
