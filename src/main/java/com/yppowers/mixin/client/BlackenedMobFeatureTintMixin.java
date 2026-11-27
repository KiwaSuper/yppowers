package com.yppowers.mixin.client;

import com.yppowers.client.render.BlackenedMobRenderContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin({FeatureRenderer.class})
public abstract class BlackenedMobFeatureTintMixin {
   @ModifyVariable(
      method = {"renderModel"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private static int yppowers$blackenMobFeatures(int originalColor) {
      if (!BlackenedMobRenderContext.isBlackened()) {
         return originalColor;
      } else {
         int alpha = originalColor >>> 24 & 0xFF;
         if (originalColor == 0 || alpha == 0) {
            alpha = 255;
         }

         return alpha << 24;
      }
   }
}
