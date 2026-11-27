package com.yppowers.client;

import com.yppowers.config.YPConfig;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.ArrowType;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class PowerMenuScreen extends Screen {
   private static final String[] ANCHORS = {"BOTTOM_RIGHT", "TOP_CENTER", "TOP_RIGHT", "TOP_LEFT", "BOTTOM_LEFT"};

   private boolean hudTab = false;
   private ButtonWidget previousSelector;
   private ButtonWidget nextSelector;
   private ButtonWidget openArcherMenu;

   public PowerMenuScreen() {
      super(Text.literal("YP Powers — Power Menu"));
   }

   @Override
   protected void init() {
      this.clearChildren();
      int centerX = this.width / 2;
      int centerY = this.height / 2;

      ButtonWidget powerTabBtn = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Power & Rituals"), button -> {
            this.hudTab = false;
            this.init();
         }).dimensions(centerX - 155, centerY - 118, 115, 18).build()
      );
      powerTabBtn.active = this.hudTab;

      ButtonWidget hudTabBtn = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("HUD Settings"), button -> {
            this.hudTab = true;
            this.init();
         }).dimensions(centerX - 35, centerY - 118, 115, 18).build()
      );
      hudTabBtn.active = !this.hudTab;

      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Close"), button -> this.close()).dimensions(centerX + 88, centerY - 118, 67, 18).build()
      );

      if (this.hudTab) {
         this.previousSelector = null;
         this.nextSelector = null;
         this.openArcherMenu = null;
         this.initHudControls(centerX, centerY);
         return;
      }

      this.previousSelector = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Previous"), button -> this.cycleSelector(-1)).dimensions(centerX - 145, centerY - 7, 70, 20).build()
      );
      this.nextSelector = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Next"), button -> this.cycleSelector(1)).dimensions(centerX + 75, centerY - 7, 70, 20).build()
      );
      this.openArcherMenu = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Morph + Arrow Selector"), button -> this.openArcherSelector())
            .dimensions(centerX - 105, centerY + 20, 210, 20)
            .build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Refresh"), button -> YPClientNetworking.requestPowerMenuStatus())
            .dimensions(centerX - 105, centerY + 49, 210, 20)
            .build()
      );
      this.refreshStatus();
   }

   private void initHudControls(int centerX, int centerY) {
      YPConfig cfg = YPConfig.getInstance();
      int left = centerX - 155;

      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Energy & Power HUD: " + (cfg.energy_hud_enabled ? "ON" : "OFF")), button -> {
            cfg.energy_hud_enabled = !cfg.energy_hud_enabled;
            cfg.save();
            this.init();
         }).dimensions(left, centerY - 86, 310, 20).build()
      );

      int anchorY = centerY - 46;
      int btnW = 59;
      for (int i = 0; i < ANCHORS.length; i++) {
         String anchor = ANCHORS[i];
         String shortLabel = switch (anchor) {
            case "BOTTOM_RIGHT" -> "Bot R";
            case "TOP_CENTER" -> "Top C";
            case "TOP_RIGHT" -> "Top R";
            case "TOP_LEFT" -> "Top L";
            default -> "Bot L";
         };
         ButtonWidget btn = this.addDrawableChild(
            ButtonWidget.builder(Text.literal(shortLabel), button -> {
               cfg.energy_hud_anchor = anchor;
               cfg.validate();
               cfg.save();
               this.init();
            }).dimensions(left + i * (btnW + 4), anchorY, btnW, 20).build()
         );
         btn.active = !anchor.equals(cfg.energy_hud_anchor);
      }

      this.addOffsetRow(left, centerY - 8, () -> cfg.energy_hud_offset_x, v -> {
         cfg.energy_hud_offset_x = Math.max(0, Math.min(2000, v));
         cfg.save();
         this.init();
      });

      this.addOffsetRow(left, centerY + 18, () -> cfg.energy_hud_offset_y, v -> {
         cfg.energy_hud_offset_y = Math.max(0, Math.min(2000, v));
         cfg.save();
         this.init();
      });

      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Reset HUD Defaults (Bottom Right)"), button -> {
            cfg.energy_hud_enabled = true;
            cfg.energy_hud_anchor = "BOTTOM_RIGHT";
            cfg.energy_hud_offset_x = 8;
            cfg.energy_hud_offset_y = 8;
            cfg.save();
            this.init();
         }).dimensions(left, centerY + 48, 220, 20).build()
      );
   }

   private void addOffsetRow(int left, int y, IntSupplier getter, IntConsumer setter) {
      this.addDrawableChild(ButtonWidget.builder(Text.literal("-10"), b -> setter.accept(getter.getAsInt() - 10)).dimensions(left + 125, y, 38, 18).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("-2"), b -> setter.accept(getter.getAsInt() - 2)).dimensions(left + 167, y, 32, 18).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("+2"), b -> setter.accept(getter.getAsInt() + 2)).dimensions(left + 203, y, 32, 18).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("+10"), b -> setter.accept(getter.getAsInt() + 10)).dimensions(left + 239, y, 38, 18).build());
   }

   public void refreshStatus() {
      if (this.previousSelector != null) {
         String powerId = ClientPowerData.powerMenuPowerId;
         boolean ice = "ice_emperor".equals(powerId);
         boolean spirit = "spirit_channeler".equals(powerId);
         boolean archer = "shadow_archer".equals(powerId);
         this.previousSelector.visible = ice || spirit;
         this.nextSelector.visible = ice || spirit;
         this.previousSelector.active = ClientPowerData.powerMenuDataReady
            && (ice ? ClientPowerData.powerMenuAvailableIceForms.size() > 1 : spirit && ClientPowerData.powerMenuAvailableSpirits.size() > 1);
         this.nextSelector.active = this.previousSelector.active;
         this.previousSelector.setMessage(Text.literal(ice ? "Prev Ice" : (spirit ? "Prev Spirit" : "Previous")));
         this.nextSelector.setMessage(Text.literal(ice ? "Next Ice" : (spirit ? "Next Spirit" : "Next")));
         this.openArcherMenu.visible = archer;
         this.openArcherMenu.active = ClientPowerData.powerMenuDataReady && archer;
      }
   }

   private void cycleSelector(int direction) {
      String powerId = ClientPowerData.powerMenuPowerId;
      if ("ice_emperor".equals(powerId)) {
         this.sendNext("ice", ClientPowerData.powerMenuAvailableIceForms, ClientPowerData.powerMenuSelectedIceForm, direction);
      } else if ("spirit_channeler".equals(powerId)) {
         this.sendNext("spirit", ClientPowerData.powerMenuAvailableSpirits, ClientPowerData.powerMenuSelectedSpirit, direction);
      }
   }

   private void sendNext(String category, List<String> values, String selected, int direction) {
      if (values != null && !values.isEmpty()) {
         int index = values.indexOf(selected);
         if (index < 0) {
            index = 0;
         }

         String next = values.get(Math.floorMod(index + direction, values.size()));
         YPClientNetworking.sendPowerMenuAction(category + ":" + next);
      }
   }

   private void openArcherSelector() {
      if ("shadow_archer".equals(ClientPowerData.powerMenuPowerId)) {
         MinecraftClient.getInstance().setScreen(new ShadowArcherMorphScreen());
      }
   }

   private String selectorStatus() {
      if (!ClientPowerData.powerMenuDataReady) {
         return "Selectors: waiting for server status…";
      } else {
         String powerId = ClientPowerData.powerMenuPowerId;
         if ("ice_emperor".equals(powerId)) {
            return ClientPowerData.powerMenuAvailableIceForms.isEmpty()
               ? "Ice forms unavailable"
               : "Ice form: " + pretty(ClientPowerData.powerMenuSelectedIceForm) + " — " + iceDescription(ClientPowerData.powerMenuSelectedIceForm);
         } else if ("spirit_channeler".equals(powerId)) {
            return ClientPowerData.powerMenuAvailableSpirits.isEmpty()
               ? "No Spirit Forms unlocked"
               : "Spirit: " + pretty(ClientPowerData.powerMenuSelectedSpirit) + " — " + spiritDescription(ClientPowerData.powerMenuSelectedSpirit);
         } else if ("shadow_archer".equals(powerId)) {
            try {
               ArrowType arrow = ArrowType.valueOf(ClientPowerData.powerMenuSelectedArrow);
               return "Selected arrow: " + arrow.getDisplayName();
            } catch (IllegalArgumentException var3) {
               return "Special-arrow selector available in the morph menu.";
            }
         } else {
            return "No selector for this power.";
         }
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      int left = Math.max(8, centerX - 170);
      int right = Math.min(this.width - 8, centerX + 170);
      int top = Math.max(8, centerY - 126);
      int bottom = Math.min(this.height - 8, centerY + 78);
      // Keep dimmer light in HUD Settings tab so live HUD movement is visible behind the window!
      context.fill(0, 0, this.width, this.height, this.hudTab ? 0x440A0A12 : -804251612);
      context.fill(left, top, right, bottom, -300275912);
      context.fill(left, top, right, top + 1, -10106881);
      context.fill(left, bottom - 1, right, bottom, -10106881);
      context.fill(left, top, left + 1, bottom, -10106881);
      context.fill(right - 1, top, right, bottom, -10106881);
      super.render(context, mouseX, mouseY, deltaTicks);

      if (this.hudTab) {
         YPConfig cfg = YPConfig.getInstance();
         int labelX = centerX - 155;
         context.drawText(this.textRenderer, "HUD Anchor (live preview behind menu):", labelX, centerY - 58, 0xFFCCCCDD, true);
         context.drawText(this.textRenderer, "Offset X: " + cfg.energy_hud_offset_x + " px", labelX, centerY - 3, 0xFFE6E6F2, true);
         context.drawText(this.textRenderer, "Offset Y: " + cfg.energy_hud_offset_y + " px", labelX, centerY + 23, 0xFFE6E6F2, true);
         return;
      }

      PowerType type = PowerType.fromId(ClientPowerData.powerMenuPowerId);
      String powerName = type == null ? "No power assigned" : type.getDisplayName();
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Assigned: " + powerName), centerX, centerY - 90, 14151679);
      String ritual = ClientPowerData.powerMenuDataReady ? ClientPowerData.powerMenuRitualStatus : "Waiting for server status…";
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("Ritual: " + ritual), centerX, centerY - 74, ClientPowerData.powerMenuRitualComplete ? 16769152 : 13684944
      );
      String awakening = ClientPowerData.powerMenuAwakeningUnlocked ? "unlocked" : "locked";
      String grimoire = ClientPowerData.powerMenuCarriesGrimoire ? "carried" : "not carried";
      context.drawCenteredTextWithShadow(
         this.textRenderer,
         Text.literal("Awakening: " + awakening + "  •  Grimoire: " + grimoire),
         centerX,
         centerY - 56,
         ClientPowerData.powerMenuCarriesGrimoire ? 13168895 : 12040119
      );
      int cooldown = ClientPowerData.powerMenuAwakeningCooldownTicks;
      String cooldownText = cooldown <= 0 ? "Ready / no tracked cooldown" : formatCooldown(cooldown);
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("Awakening cooldown: " + cooldownText), centerX, centerY - 40, cooldown <= 0 ? 12052161 : 16758922
      );
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.selectorStatus()), centerX, centerY - 22, 13101567);
      if ("poseidon".equals(ClientPowerData.powerMenuPowerId)) {
         context.drawCenteredTextWithShadow(
            this.textRenderer, Text.literal("Press P to review and confirm the terrain strip."), centerX, centerY + 36, 11130866
         );
      }
   }

   private static String formatCooldown(int ticks) {
      long seconds = (Math.max(0L, (long)ticks) + 19L) / 20L;
      long days = seconds / 86400L;
      long hours = seconds % 86400L / 3600L;
      long minutes = seconds % 3600L / 60L;
      if (days > 0L) {
         return days + "d " + hours + "h remaining";
      } else if (hours > 0L) {
         return hours + "h " + minutes + "m remaining";
      } else {
         return minutes > 0L ? minutes + "m " + seconds % 60L + "s remaining" : seconds + "s remaining";
      }
   }

   private static String pretty(String id) {
      if (id != null && !id.isBlank()) {
         String lower = id.toLowerCase(Locale.ROOT);
         return Character.toUpperCase(lower.charAt(0)) + lower.substring(1).replace('_', ' ');
      } else {
         return "—";
      }
   }

   private static String iceDescription(String id) {
      String var1 = id == null ? "" : id;
      return switch (var1) {
         case "ARMOURED" -> "defence";
         case "STEALTH" -> "concealment";
         case "ASSASSIN" -> "mobility and melee";
         default -> "";
      };
   }

   private static String spiritDescription(String id) {
      String var1 = id == null ? "" : id;
      return switch (var1) {
         case "WOLF" -> "fast companion";
         case "GUARDIAN" -> "ranged laser support";
         case "WITHER_SKELETON" -> "wither striker";
         default -> "";
      };
   }
}
