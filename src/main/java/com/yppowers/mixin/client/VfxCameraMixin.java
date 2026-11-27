package com.yppowers.mixin.client;

import com.yppowers.client.render.VfxManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
public final class VfxCameraMixin {
   private VfxCameraMixin() {
   }

   @Mixin({Camera.class})
   public abstract static class CameraShake {
      @Shadow
      protected abstract void setPos(Vec3d var1);

      @Shadow
      protected abstract void setRotation(float var1, float var2);

      @Shadow
      public abstract Vec3d getCameraPos();

      @Shadow
      public abstract float getYaw();

      @Shadow
      public abstract float getPitch();

      @Inject(
         method = {"update"},
         at = {@At("TAIL")}
      )
      private void yppowers$applyCosmeticShake(World world, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player != null && focusedEntity == client.player) {
            float strength = VfxManager.cameraShakeFor(client.player.getId());
            if (!(strength <= 0.001F)) {
               float phase = client.player.age * 2.7F + tickDelta * 2.7F;
               Vec3d offset = new Vec3d(
                  Math.sin(phase) * strength * 0.045, Math.cos(phase * 1.31F) * strength * 0.025, Math.sin(phase * 0.71F) * strength * 0.015
               );
               this.setPos(this.getCameraPos().add(offset));
               this.setRotation(this.getYaw() + (float)Math.sin(phase * 0.8F) * strength * 1.3F, this.getPitch() + (float)Math.cos(phase) * strength * 0.8F);
            }
         }
      }
   }

   @Mixin({GameRenderer.class})
   public abstract static class FovPunch {
      @Inject(
         method = {"getFov"},
         at = {@At("RETURN")},
         cancellable = true
      )
      private void yppowers$applyCosmeticFovPunch(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client.player != null && camera.getFocusedEntity() == client.player) {
            float punch = VfxManager.fovPunchFor(client.player.getId());
            if (punch > 0.001F) {
               cir.setReturnValue(Math.max(30.0F, Math.min(120.0F, (Float)cir.getReturnValue() * (1.0F + punch))));
            }
         }
      }
   }
}
