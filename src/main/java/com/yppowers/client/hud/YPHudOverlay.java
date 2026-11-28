package com.yppowers.client.hud;

import com.yppowers.client.ClientPowerData;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.keybind.YPKeybinds;
import com.yppowers.network.HudSnapshot;
import com.yppowers.power.PowerType;
import com.yppowers.system.CombatTagSystem;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * Upgraded anchorable YP Powers HUD (always visible even under F3):
 * 1. Power Badge + Status Chip (Form / Mode / Shards / Flight / Grace / Grimoire / Lockout)
 * 2. Framed 10-spark Energy Panel (with Bleeding indicator)
 * 3. Live Ability Pills with dynamic bound keys and smooth draining cooldown progress bars
 * 4. Red PvP Combat Timer banner (COMBAT <time> — DO NOT LOG OUT)
 * 5. Urgent HUD Warning text
 */
@Environment(EnvType.CLIENT)
public final class YPHudOverlay {
   private static final int PANEL_WIDTH = 118;
   private static final int PANEL_HEIGHT = 30;
   private static final int MAX_PILL_ROW_WIDTH = 240;

   private record PillView(String text, int width, int textColor, int underlineColor, float cooldownRatio) {
   }

   private YPHudOverlay() {
   }

   private static Identifier texture(String path) {
      return Identifier.of("yppowers", "textures/gui/energy/" + path + ".png");
   }

   public static void render(DrawContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null || client.world == null || client.options.hudHidden) {
         return;
      }

      YPConfig cfg = YPConfig.getInstance();
      HudSnapshot snap = ClientPowerData.hudSnapshot;
      TextRenderer tr = client.textRenderer;
      int screenW = context.getScaledWindowWidth();
      int screenH = context.getScaledWindowHeight();

      PowerType powerType = PowerType.fromId(snap.powerId().isEmpty() ? ClientPowerData.assignedPowerId : snap.powerId());
      boolean showEnergy = cfg.energy_hud_enabled;
      boolean showBadge = showEnergy && (powerType != null || snap.grimoireActive() || snap.graceActive());
      boolean showPills = showEnergy && cfg.energy_hud_show_cooldown_pills && !snap.pills().isEmpty();
      int combatTicks = Math.max(snap.combatTicks(), ClientPowerData.combatTicks);
      int maxCombatTicks = Math.max(combatTicks, Math.max(1, snap.maxCombatTicks()));
      boolean showCombat = combatTicks > 0;
      boolean showWarning = ClientPowerData.hudWarningTicks > 0 && !ClientPowerData.hudWarningText.isBlank();

      if (!showEnergy && !showCombat && !showWarning) {
         return;
      }

      int accent = powerType != null ? powerType.getAccentColor() : 0xFF8A6BFF;
      String headerTitle = powerType != null ? powerType.getDisplayName() : "YP Powers";
      String headerChip = snap.statusChip();
      int headerW = Math.max(PANEL_WIDTH, tr.getWidth(headerTitle) + (headerChip.isEmpty() ? 10 : tr.getWidth("  |  " + headerChip) + 10));

      List<List<PillView>> pillRows = new ArrayList<>();
      if (showPills) {
         List<PillView> currentRow = new ArrayList<>();
         int rowW = 0;
         for (HudPill pill : snap.pills()) {
            AbilityKey key = AbilityKey.fromWireId(pill.keyWireId());
            String keyLabel = YPKeybinds.getBoundKeyLabel(key);
            String suffix;
            int textColor;
            float cdRatio = 0.0F;
            if (pill.cooldownTicks() > 0) {
               suffix = " " + CombatTagSystem.formatSeconds(pill.cooldownTicks());
               textColor = 0xFFFFCC77;
               int denom = Math.max(pill.cooldownTicks(), Math.max(1, pill.maxCooldownTicks()));
               cdRatio = Math.max(0.0F, Math.min(1.0F, (float)pill.cooldownTicks() / (float)denom));
            } else if (pill.isToggle()) {
               suffix = pill.toggleActive() ? " ON" : " OFF";
               textColor = pill.toggleActive() ? 0xFF66FF88 : 0xFF999999;
            } else if (snap.graceActive() || snap.bleedingTicks() > 0 || snap.lockoutTicks() > 0 || !pill.canAfford()) {
               suffix = "";
               textColor = 0xFF888888;
            } else {
               suffix = "";
               textColor = 0xFFFFFFFF;
            }
            String text = "[" + keyLabel + "] " + pill.label() + suffix;
            int w = tr.getWidth(text) + 6;
            int underline = pill.cooldownTicks() > 0 ? 0xCCFF9933 : (accent & 0x00FFFFFF) | 0xCC000000;
            if (!currentRow.isEmpty() && rowW + 3 + w > MAX_PILL_ROW_WIDTH) {
               pillRows.add(currentRow);
               currentRow = new ArrayList<>();
               rowW = 0;
            }
            currentRow.add(new PillView(text, w, textColor, underline, cdRatio));
            rowW += (currentRow.size() > 1 ? 3 : 0) + w;
         }
         if (!currentRow.isEmpty()) {
            pillRows.add(currentRow);
         }
      }

      int totalH = 0;
      if (showBadge) totalH += 14;
      if (showEnergy) totalH += PANEL_HEIGHT + 3;
      totalH += pillRows.size() * 14;
      if (showCombat) totalH += 14;
      if (showWarning) totalH += 12;

      String anchor = cfg.energy_hud_anchor == null ? "BOTTOM_RIGHT" : cfg.energy_hud_anchor;
      boolean bottom = "BOTTOM_LEFT".equals(anchor) || "BOTTOM_RIGHT".equals(anchor);
      boolean right = "TOP_RIGHT".equals(anchor) || "BOTTOM_RIGHT".equals(anchor);
      boolean center = "TOP_CENTER".equals(anchor);
      int offX = Math.max(0, cfg.energy_hud_offset_x);
      int offY = Math.max(0, cfg.energy_hud_offset_y);

      int cursorY = bottom ? Math.max(4, screenH - offY - totalH) : Math.max(4, offY);

      // 1. Power Badge + Status Chip
      if (showBadge) {
         int x = alignX(screenW, headerW, offX, right, center);
         context.fill(x, cursorY, x + headerW, cursorY + 12, 0xCC101018);
         context.fill(x, cursorY, x + 2, cursorY + 12, accent);
         context.drawText(tr, headerTitle, x + 5, cursorY + 2, accent, true);
         if (!headerChip.isEmpty()) {
            int chipX = x + 5 + tr.getWidth(headerTitle);
            context.drawText(tr, "  |  ", chipX, cursorY + 2, 0xFF666677, false);
            int chipColor = snap.graceActive() ? 0xFFFFD54F : (snap.lockoutTicks() > 0 ? 0xFFFF6666 : 0xFFE6E6F2);
            context.drawText(tr, headerChip, chipX + tr.getWidth("  |  "), cursorY + 2, chipColor, true);
         }
         cursorY += 14;
      }

      // 2. Framed 10-Spark Energy Panel
      if (showEnergy) {
         double max = Math.max(1.0, ClientPowerData.maxEnergy);
         double energy = Math.max(0.0, Math.min(max, ClientPowerData.energy));
         double fraction = energy / max;
         boolean bleeding = ClientPowerData.bleedingTicks > 0;
         double threshold = Math.max(0.0, Math.min(1.0, cfg.energy_low_threshold_percent));
         String color = bleeding ? "red" : (fraction <= threshold ? "orange" : "purple");
         int x = alignX(screenW, PANEL_WIDTH, offX, right, center);
         int frame = bleeding ? -1619101 : (fraction <= threshold ? -1861061 : -7775803);
         context.fill(x, cursorY, x + PANEL_WIDTH, cursorY + PANEL_HEIGHT, frame);
         context.fill(x + 1, cursorY + 1, x + PANEL_WIDTH - 1, cursorY + PANEL_HEIGHT - 1, -435154661);
         if (bleeding) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture("bleeding_icon"), x + 5, cursorY + 4, 0.0F, 0.0F, 9, 9, 9, 9);
            context.drawText(tr, Text.literal("BLEEDING").formatted(Formatting.RED, Formatting.BOLD), x + 17, cursorY + 4, -35195, true);
         } else {
            context.drawText(tr, Text.literal("ENERGY").formatted(Formatting.GRAY), x + 6, cursorY + 4, -2172950, true);
         }

         String pct = Math.round(fraction * 100.0) + "%";
         int pctColor = bleeding ? -35195 : (fraction <= threshold ? -16021 : -2639361);
         context.drawText(tr, pct, x + PANEL_WIDTH - 6 - tr.getWidth(pct), cursorY + 4, pctColor, true);
         double perSpark = max / 10.0;
         for (int i = 0; i < 10; i++) {
            double fill = Math.max(0.0, Math.min(1.0, (energy - i * perSpark) / perSpark));
            Identifier sprite;
            if (fill >= 0.999) sprite = texture("energy_spark_full_" + color);
            else if (fill <= 0.001) sprite = texture("energy_spark_empty");
            else sprite = texture("energy_spark_partial_" + Math.max(1, Math.min(9, (int)Math.ceil(fill * 9.0))) + "_" + color);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, sprite, x + 7 + i * 10, cursorY + 16, 0.0F, 0.0F, 9, 9, 9, 9);
         }
         cursorY += PANEL_HEIGHT + 3;
      }

      // 3. Live Ability Cooldown & Toggle Pills with draining progress bars
      for (List<PillView> row : pillRows) {
         int rowW = 0;
         for (int i = 0; i < row.size(); i++) {
            rowW += row.get(i).width() + (i > 0 ? 3 : 0);
         }
         int cursorX = alignX(screenW, rowW, offX, right, center);
         for (PillView pill : row) {
            context.fill(cursorX, cursorY, cursorX + pill.width(), cursorY + 11, 0xBB14141C);
            if (pill.cooldownRatio() > 0.0F) {
               int fillW = Math.max(1, Math.round(pill.width() * pill.cooldownRatio()));
               context.fill(cursorX, cursorY, cursorX + fillW, cursorY + 10, 0x55FF8822);
               context.fill(cursorX, cursorY + 10, cursorX + fillW, cursorY + 11, pill.underlineColor());
            } else {
               context.fill(cursorX, cursorY + 10, cursorX + pill.width(), cursorY + 11, pill.underlineColor());
            }
            context.drawText(tr, pill.text(), cursorX + 3, cursorY + 2, pill.textColor(), true);
            cursorX += pill.width() + 3;
         }
         cursorY += 14;
      }

      // 4. Red PvP Combat Timer Banner
      if (showCombat) {
         String combatLabel = "COMBAT " + CombatTagSystem.formatSeconds(combatTicks) + " — DO NOT LOG OUT";
         int cw = tr.getWidth(combatLabel) + 8;
         int cx = alignX(screenW, cw, offX, right, center);
         float ratio = Math.max(0.0F, Math.min(1.0F, (float)combatTicks / (float)maxCombatTicks));
         int fillW = Math.max(2, Math.round(cw * ratio));
         context.fill(cx, cursorY, cx + cw, cursorY + 11, 0xCC1E0A0E);
         context.fill(cx, cursorY, cx + fillW, cursorY + 10, 0x66EE2233);
         context.fill(cx, cursorY + 10, cx + fillW, cursorY + 11, 0xFFFF3344);
         context.drawText(tr, combatLabel, cx + 4, cursorY + 2, 0xFFFF7788, true);
         cursorY += 14;
      }

      // 5. Urgent HUD Warning
      if (showWarning) {
         String warning = tr.trimToWidth(ClientPowerData.hudWarningText, Math.max(12, screenW - 12));
         int w = tr.getWidth(warning);
         int wx = alignX(screenW, w, offX, right, center);
         context.drawText(tr, Text.literal(warning).formatted(Formatting.RED, Formatting.BOLD), wx, cursorY, -48831, true);
      }
   }

   private static int alignX(int screenW, int elemW, int offsetX, boolean right, boolean center) {
      if (center) return Math.max(4, (screenW - elemW) / 2);
      if (right) return Math.max(4, screenW - offsetX - elemW);
      return Math.max(4, Math.min(screenW - elemW - 4, offsetX));
   }
}
