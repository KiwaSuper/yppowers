package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import com.yppowers.config.YPConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders the 10-spark transparent pixel-art Energy HUD below the vanilla status HUD. */
@Mixin(InGameHud.class)
public abstract class EnergyHudMixin {
    private static Identifier texture(String path) {
        return Identifier.of("yppowers", "textures/gui/energy/" + path + ".png");
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void yppowers$renderEnergySparks(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        double max = Math.max(1.0, ClientPowerData.maxEnergy);
        double energy = Math.max(0.0, Math.min(max, ClientPowerData.energy));
        boolean bleeding = ClientPowerData.bleedingTicks > 0;
        double threshold = Math.max(0.0, Math.min(1.0, YPConfig.getInstance().energy_low_threshold_percent));
        String color = bleeding ? "red" : (energy / max <= threshold ? "orange" : "purple");
        int x = Math.max(0, YPConfig.getInstance().energy_hud_x);
        int y = Math.max(0, context.getScaledWindowHeight() - Math.max(20, YPConfig.getInstance().energy_hud_bottom_offset));
        double perSpark = max / 10.0;
        for (int i = 0; i < 10; i++) {
            double fill = Math.max(0.0, Math.min(1.0, (energy - i * perSpark) / perSpark));
            Identifier sprite;
            if (fill >= 0.999) {
                sprite = texture("energy_spark_full_" + color);
            } else if (fill <= 0.001) {
                sprite = texture("energy_spark_empty");
            } else {
                int partial = Math.max(1, Math.min(9, (int) Math.ceil(fill * 9.0)));
                sprite = texture("energy_spark_partial_" + partial + "_" + color);
            }
            context.drawTexture(RenderPipelines.GUI_TEXTURED, sprite, x + i * 10, y, 0.0f, 0.0f, 9, 9, 9, 9);
        }
        if (bleeding) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture("bleeding_icon"), x + 102, y, 0.0f, 0.0f, 9, 9, 9, 9);
            context.drawText(client.textRenderer, "BLEEDING", x + 113, y + 1, 0xFFFF6677, true);
        }
    }
}
