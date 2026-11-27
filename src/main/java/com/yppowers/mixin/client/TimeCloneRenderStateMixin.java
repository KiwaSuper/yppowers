package com.yppowers.mixin.client;

import com.yppowers.client.render.TimeCloneRenderStateAccess;
import java.util.UUID;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({ZombieEntityRenderState.class})
public abstract class TimeCloneRenderStateMixin implements TimeCloneRenderStateAccess {
   @Unique
   private UUID yppowers$cloneOwner;
   @Unique
   private boolean yppowers$goldenClone;

   @Override
   public UUID yppowers$getCloneOwner() {
      return this.yppowers$cloneOwner;
   }

   @Override
   public boolean yppowers$isGoldenClone() {
      return this.yppowers$goldenClone;
   }

   @Override
   public void yppowers$setCloneSkin(UUID owner, boolean golden) {
      this.yppowers$cloneOwner = owner;
      this.yppowers$goldenClone = golden;
   }
}
