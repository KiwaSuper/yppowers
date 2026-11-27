package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import com.yppowers.combat.CombatTagManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.PowerType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InGameHud.class})
public abstract class EnergyHudMixin {
   private static final int PANEL_WIDTH = 118;
   private static final int PANEL_HEIGHT = 30;

   private static Identifier texture(String path) {
      return Identifier.of("yppowers", "textures/gui/energy/" + path + ".png");
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void yppowers$renderEnergyPanel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null || client.world == null || client.options.hudHidden) {
         return;
      }

      YPConfig cfg = YPConfig.getInstance();
      boolean showEnergy = cfg.energy_hud_enabled;
      boolean showCombat = ClientPowerData.combatTicks > 0;
      boolean showWarning = ClientPowerData.hudWarningTicks > 0 && !ClientPowerData.hudWarningText.isBlank();
      PowerType assigned = PowerType.fromId(ClientPowerData.assignedPowerId);
      boolean showPowerBadge = showEnergy && (assigned != null || ClientPowerData.grimoireSkillsetEnabled || ClientPowerData.graceActive);

      if (!showEnergy && !showCombat && !showWarning) {
         return;
      }

      int screenWidth = context.getScaledWindowWidth();
      int screenHeight = context.getScaledWindowHeight();
      String anchor = cfg.energy_hud_anchor == null ? "BOTTOM_RIGHT" : cfg.energy_hud_anchor;
      boolean bottom = "BOTTOM_LEFT".equals(anchor) || "BOTTOM_RIGHT".equals(anchor);
      boolean right = "TOP_RIGHT".equals(anchor) || "BOTTOM_RIGHT".equals(anchor);
      boolean center = "TOP_CENTER".equals(anchor);
      int offX = Math.max(0, cfg.energy_hud_offset_x);
      int offY = Math.max(0, cfg.energy_hud_offset_y);

      int totalHeight = 0;
      if (showPowerBadge) totalHeight += 14;
      if (showEnergy) totalHeight += PANEL_HEIGHT + 3;
      if (showCombat) totalHeight += 14;
      if (showWarning) totalHeight += 12;

      int cursorY = bottom ? Math.max(4, screenHeight - offY - totalHeight) : Math.max(4, offY);

      // 1. Power Badge + Grace / Grimoire chip
      if (showPowerBadge) {
         String title = assigned != null ? assigned.getDisplayName() : "YP Powers";
         String chip = ClientPowerData.graceActive ? "GRACE" : (ClientPowerData.grimoireSkillsetEnabled ? "GRIMOIRE" : "");
         int badgeW = Math.max(PANEL_WIDTH, client.textRenderer.getWidth(title) + (chip.isEmpty() ? 10 : client.textRenderer.getWidth(" | " + chip) + 10));
         int x = alignX(screenWidth, badgeW, offX, right, center);
         context.fill(x, cursorY, x + badgeW, cursorY + 12, 0xCC101018);
         context.fill(x, cursorY, x + 2, cursorY + 12, 0xFF8A6BFF);
         context.drawText(client.textRenderer, title, x + 5, cursorY + 2, 0xFFE6DCFF, true);
         if (!chip.isEmpty()) {
            int chipX = x + 5 + client.textRenderer.getWidth(title);
            context.drawText(client.textRenderer, " | ", chipX, cursorY + 2, 0xFF666677, false);
            int chipColor = ClientPowerData.graceActive ? 0xFFFFD54F : 0xFFFFE57F;
            context.drawText(client.textRenderer, chip, chipX + client.textRenderer.getWidth(" | "), cursorY + 2, chipColor, true);
         }
         cursorY += 14;
      }

      // 2. Framed 10-spark Energy Panel
      if (showEnergy) {
         double max = Math.max(1.0, ClientPowerData.maxEnergy);
         double energy = Math.max(0.0, Math.min(max, ClientPowerData.energy));
         double fraction = energy / max;
         boolean bleeding = ClientPowerData.bleedingTicks > 0;
         double threshold = Math.max(0.0, Math.min(1.0, cfg.energy_low_threshold_percent));
         String color = bleeding ? "red" : (fraction <= threshold ? "orange" : "purple");
         int x = alignX(screenWidth, PANEL_WIDTH, offX, right, center);
         int frame = bleeding ? -1619101 : (fraction <= threshold ? -1861061 : -7775803);
         context.fill(x, cursorY, x + PANEL_WIDTH, cursorY + PANEL_HEIGHT, frame);
         context.fill(x + 1, cursorY + 1, x + PANEL_WIDTH - 1, cursorY + PANEL_HEIGHT - 1, -435154661);
         if (bleeding) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture("bleeding_icon"), x + 5, cursorY + 4, 0.0F, 0.0F, 9, 9, 9, 9);
            context.drawText(client.textRenderer, Text.literal("BLEEDING").formatted(Formatting.RED, Formatting.BOLD), x + 17, cursorY + 4, -35195, true);
         } else {
            context.drawText(client.textRenderer, Text.literal("ENERGY").formatted(Formatting.GRAY), x + 6, cursorY + 4, -2172950, true);
         }

         String percentage = Math.round(fraction * 100.0) + "%";
         int percentageColor = bleeding ? -35195 : (fraction <= threshold ? -16021 : -2639361);
         context.drawText(client.textRenderer, percentage, x + PANEL_WIDTH - 6 - client.textRenderer.getWidth(percentage), cursorY + 4, percentageColor, true);
         double perSpark = max / 10.0;

         for (int i = 0; i < 10; i++) {
            double fill = Math.max(0.0, Math.min(1.0, (energy - i * perSpark) / perSpark));
            Identifier sprite;
            if (fill >= 0.999) {
               sprite = texture("energy_spark_full_" + color);
            } else if (fill <= 0.001) {
               sprite = texture("energy_spark_empty");
            } else {
               int partial = Math.max(1, Math.min(9, (int)Math.ceil(fill * 9.0)));
               sprite = texture("energy_spark_partial_" + partial + "_" + color);
            }

            context.drawTexture(RenderPipelines.GUI_TEXTURED, sprite, x + 7 + i * 10, cursorY + 16, 0.0F, 0.0F, 9, 9, 9, 9);
         }
         cursorY += PANEL_HEIGHT + 3;
      }

      // 3. PvP Combat Timer Banner (draining red progress bar)
      if (showCombat) {
         String combatLabel = "COMBAT " + CombatTagManager.formatSeconds(ClientPowerData.combatTicks) + " — DO NOT LOG OUT";
         int cw = client.textRenderer.getWidth(combatLabel) + 8;
         int cx = alignX(screenWidth, cw, offX, right, center);
         float ratio = Math.max(0.0F, Math.min(1.0F,
            (float)ClientPowerData.combatTicks / (float)Math.max(ClientPowerData.combatTicks, Math.max(1, ClientPowerData.maxCombatTicks))));
         int fillW = Math.max(2, Math.round(cw * ratio));
         context.fill(cx, cursorY, cx + cw, cursorY + 11, 0xCC1E0A0E);
         context.fill(cx, cursorY, cx + fillW, cursorY + 10, 0x66EE2233);
         context.fill(cx, cursorY + 10, cx + fillW, cursorY + 11, 0xFFFF3344);
         context.drawText(client.textRenderer, combatLabel, cx + 4, cursorY + 2, 0xFFFF7788, true);
         cursorY += 14;
      }

      // 4. Urgent HUD Warning
      if (showWarning) {
         String warning = client.textRenderer.trimToWidth(ClientPowerData.hudWarningText, Math.max(12, screenWidth - 12));
         int w = client.textRenderer.getWidth(warning);
         int wx = alignX(screenWidth, w, offX, right, center);
         context.drawText(client.textRenderer, Text.literal(warning).formatted(Formatting.RED, Formatting.BOLD), wx, cursorY, -48831, true);
      }
   }

   private static int alignX(int screenW, int elemW, int offsetX, boolean right, boolean center) {
      if (center) {
         return Math.max(4, (screenW - elemW) / 2);
      }
      if (right) {
         return Math.max(4, screenW - offsetX - elemW);
      }
      return Math.max(4, Math.min(screenW - elemW - 4, offsetX));
   }
}
