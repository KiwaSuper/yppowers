package com.yppowers.mixin.client;

import com.yppowers.client.render.FrozenRenderContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(FeatureRenderer.class)
public abstract class FrozenPlayerFeatureTintMixin {
    @ModifyVariable(
            method = "renderModel(Lnet/minecraft/client/model/Model;Lnet/minecraft/util/Identifier;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;II)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1
    )
    private static int yppowers$tintFrozenFeature(int color) {
        if (!FrozenRenderContext.isFrozen()) return color;
        int a = (color >>> 24) & 0xFF;
        if (color == 0 || a == 0) a = 0xFF;
        int r = ((color >>> 16) & 0xFF) * 0xB3 / 0xFF;
        int g = ((color >>> 8) & 0xFF) * 0xB3 / 0xFF;
        int b = (color & 0xFF) * 0xB3 / 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
