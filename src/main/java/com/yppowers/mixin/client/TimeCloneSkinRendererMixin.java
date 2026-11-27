package com.yppowers.mixin.client;

import com.yppowers.client.render.TimeCloneRenderStateAccess;
import com.yppowers.client.render.TimeCloneSkinManager;
import java.util.UUID;
import net.minecraft.client.render.entity.ZombieBaseEntityRenderer;
import net.minecraft.client.render.entity.state.ZombieEntityRenderState;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ZombieBaseEntityRenderer.class})
public abstract class TimeCloneSkinRendererMixin {
   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/mob/ZombieEntity;Lnet/minecraft/client/render/entity/state/ZombieEntityRenderState;F)V"},
      at = {@At("TAIL")}
   )
   private void yppowers$readCloneSkinMetadata(ZombieEntity entity, ZombieEntityRenderState state, float tickProgress, CallbackInfo ci) {
      UUID ownerId = null;
      boolean golden = false;
      Text customName = entity.getCustomName();
      if (customName != null) {
         String metadata = customName.getString();
         if (metadata.startsWith("YPCLONE|")) {
            String[] parts = metadata.split("\\|", 3);
            if (parts.length == 3) {
               try {
                  ownerId = UUID.fromString(parts[1]);
                  golden = parts[2].equals("G");
               } catch (IllegalArgumentException var11) {
                  ownerId = null;
               }
            }
         }
      }

      ((TimeCloneRenderStateAccess)state).yppowers$setCloneSkin(ownerId, golden);
   }

   @Inject(
      method = {"getTexture(Lnet/minecraft/client/render/entity/state/ZombieEntityRenderState;)Lnet/minecraft/util/Identifier;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$useOwnerSkin(ZombieEntityRenderState state, CallbackInfoReturnable<Identifier> cir) {
      TimeCloneRenderStateAccess access = (TimeCloneRenderStateAccess)state;
      UUID ownerId = access.yppowers$getCloneOwner();
      if (ownerId != null) {
         cir.setReturnValue(TimeCloneSkinManager.getTexture(ownerId, access.yppowers$isGoldenClone()));
      }
   }
}
