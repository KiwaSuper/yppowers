package com.yppowers.mixin.client;

import com.yppowers.client.render.FrozenPlayerRenderContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies the same neutral tint to armor, held-wearable feature models, and player cosmetics. */
@Environment(EnvType.CLIENT)
@Mixin(FeatureRenderer.class)
public abstract class FrozenPlayerFeatureTintMixin {
    @ModifyVariable(
            method = "renderModel(Lnet/minecraft/client/model/Model;Lnet/minecraft/util/Identifier;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;II)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1
    )
    private static int yppowers$grayFrozenPlayerFeatures(int originalColor) {
        if (!FrozenPlayerRenderContext.isFrozenPlayer()) return originalColor;
        int alpha = (originalColor >>> 24) & 0xFF;
        if (originalColor == 0 || alpha == 0) alpha = 0xFF;
        int red = ((originalColor >>> 16) & 0xFF) * 0xB3 / 0xFF;
        int green = ((originalColor >>> 8) & 0xFF) * 0xB3 / 0xFF;
        int blue = (originalColor & 0xFF) * 0xB3 / 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
