package com.yppowers.mixin.client;

import com.yppowers.client.hud.YPHudOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thin mixin delegating HUD rendering to {@link YPHudOverlay}. */
@Mixin({InGameHud.class})
public abstract class EnergyHudMixin {
   @Inject(method = {"render"}, at = {@At("TAIL")})
   private void yppowers$renderEnergyPanel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      YPHudOverlay.render(context);
   }
}
