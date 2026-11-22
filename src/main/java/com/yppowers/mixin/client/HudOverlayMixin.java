package com.yppowers.mixin.client;

import com.yppowers.client.hud.YPHudOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class HudOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void yppowers$renderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        YPHudOverlay.render(context);
    }
}
