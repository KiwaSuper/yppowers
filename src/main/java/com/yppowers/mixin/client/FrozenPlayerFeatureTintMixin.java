package com.yppowers.mixin.client;

import com.yppowers.client.render.FrozenPlayerRenderContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin({FeatureRenderer.class})
public abstract class FrozenPlayerFeatureTintMixin {
   @ModifyVariable(
      method = {"renderModel"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 1
   )
   private static int yppowers$grayFrozenPlayerFeatures(int originalColor) {
      if (!FrozenPlayerRenderContext.isFrozenPlayer()) {
         return originalColor;
      } else {
         int alpha = originalColor >>> 24 & 0xFF;
         if (originalColor == 0 || alpha == 0) {
            alpha = 255;
         }

         int red = (originalColor >>> 16 & 0xFF) * 179 / 255;
         int green = (originalColor >>> 8 & 0xFF) * 179 / 255;
         int blue = (originalColor & 0xFF) * 179 / 255;
         return alpha << 24 | red << 16 | green << 8 | blue;
      }
   }
}
