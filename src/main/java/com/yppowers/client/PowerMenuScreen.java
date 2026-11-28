package com.yppowers.client;

import com.yppowers.client.screen.PowerGuideData;
import com.yppowers.config.YPConfig;
import com.yppowers.keybind.YPKeybinds;
import com.yppowers.power.PowerType;
import com.yppowers.power.shadowarcher.ArrowType;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * 3-tab in-game screen (default key: U, or {@code /yp guide}):
 * 1. Power & Rituals (assigned power, ritual progress, Awakening, Grimoire, Ice/Spirit/Archer selectors)
 * 2. Powers Guide (all 17 powers, passives, moves & Awakenings with live bound key labels)
 * 3. HUD Settings (live toggle Energy HUD & Cooldown Pills, anchor presets, X/Y offset preview)
 */
public final class PowerMenuScreen extends Screen {
   private enum Tab { POWER, GUIDE, HUD }

   private static final String[] ANCHORS = {"BOTTOM_RIGHT", "TOP_CENTER", "TOP_RIGHT", "TOP_LEFT", "BOTTOM_LEFT"};
   private static final int SIDEBAR_PER_PAGE = 9;

   private Tab activeTab = Tab.POWER;
   private PowerType selectedGuidePower;
   private int sidebarPage = 0;

   private ButtonWidget previousSelector;
   private ButtonWidget nextSelector;
   private ButtonWidget openArcherMenu;

   public PowerMenuScreen() {
      super(Text.literal("YP Powers — Menu"));
      PowerType assigned = PowerType.fromId(ClientPowerData.assignedPowerId);
      this.selectedGuidePower = assigned != null ? assigned : PowerType.DIVINITY;
      this.sidebarPage = this.selectedGuidePower.ordinal() / SIDEBAR_PER_PAGE;
   }

   @Override
   protected void init() {
      this.clearChildren();
      this.previousSelector = null;
      this.nextSelector = null;
      this.openArcherMenu = null;

      int panelW = Math.min(480, this.width - 20);
      int left = (this.width - panelW) / 2;
      int top = Math.max(16, (this.height - 256) / 2);

      ButtonWidget powerTabBtn = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Power & Rituals"), b -> {
            this.activeTab = Tab.POWER;
            this.init();
         }).dimensions(left, top, 126, 20).build()
      );
      powerTabBtn.active = this.activeTab != Tab.POWER;

      ButtonWidget guideTabBtn = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Powers Guide (17)"), b -> {
            this.activeTab = Tab.GUIDE;
            PowerType assigned = PowerType.fromId(ClientPowerData.assignedPowerId);
            if (assigned != null && this.selectedGuidePower == null) {
               this.selectedGuidePower = assigned;
            }
            this.init();
         }).dimensions(left + 130, top, 134, 20).build()
      );
      guideTabBtn.active = this.activeTab != Tab.GUIDE;

      ButtonWidget hudTabBtn = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("HUD Settings"), b -> {
            this.activeTab = Tab.HUD;
            this.init();
         }).dimensions(left + 268, top, 108, 20).build()
      );
      hudTabBtn.active = this.activeTab != Tab.HUD;

      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Close"), b -> this.close())
            .dimensions(left + panelW - 60, top, 60, 20)
            .build()
      );

      switch (this.activeTab) {
         case POWER -> this.initPowerTab(this.width / 2, top + 128);
         case GUIDE -> this.initGuideTab(left, top + 28);
         case HUD -> this.initHudTab(left, top + 30, panelW);
      }
   }

   private void initPowerTab(int centerX, int centerY) {
      this.previousSelector = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Previous"), button -> this.cycleSelector(-1)).dimensions(centerX - 155, centerY + 12, 74, 20).build()
      );
      this.nextSelector = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Next"), button -> this.cycleSelector(1)).dimensions(centerX + 81, centerY + 12, 74, 20).build()
      );
      this.openArcherMenu = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Morph + Arrow Selector"), button -> this.openArcherSelector())
            .dimensions(centerX - 105, centerY + 38, 210, 20)
            .build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Refresh Status"), button -> YPClientNetworking.requestPowerMenuStatus())
            .dimensions(centerX - 105, centerY + 66, 210, 20)
            .build()
      );
      this.refreshStatus();
   }

   private void initGuideTab(int left, int startY) {
      PowerType[] all = PowerType.values();
      PowerType assigned = PowerType.fromId(ClientPowerData.assignedPowerId);
      int maxPage = (all.length - 1) / SIDEBAR_PER_PAGE;
      if (this.sidebarPage > maxPage) {
         this.sidebarPage = maxPage;
      }

      int start = this.sidebarPage * SIDEBAR_PER_PAGE;
      int end = Math.min(all.length, start + SIDEBAR_PER_PAGE);
      for (int i = start; i < end; i++) {
         PowerType pt = all[i];
         int row = i - start;
         String label = (pt == assigned ? "* " : "") + pt.getDisplayName();
         ButtonWidget btn = this.addDrawableChild(
            ButtonWidget.builder(Text.literal(label), b -> {
               this.selectedGuidePower = pt;
               this.init();
            }).dimensions(left, startY + row * 21, 134, 19).build()
         );
         btn.active = pt != this.selectedGuidePower;
      }

      int navY = startY + SIDEBAR_PER_PAGE * 21 + 4;
      ButtonWidget prev = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("< Prev"), b -> {
            if (this.sidebarPage > 0) {
               this.sidebarPage--;
               this.init();
            }
         }).dimensions(left, navY, 64, 18).build()
      );
      prev.active = this.sidebarPage > 0;

      ButtonWidget next = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Next >"), b -> {
            if (this.sidebarPage < maxPage) {
               this.sidebarPage++;
               this.init();
            }
         }).dimensions(left + 70, navY, 64, 18).build()
      );
      next.active = this.sidebarPage < maxPage;
   }

   private void initHudTab(int left, int startY, int panelW) {
      YPConfig cfg = YPConfig.getInstance();
      int colW = (panelW - 10) / 2;

      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Energy & Power HUD: " + (cfg.energy_hud_enabled ? "ON" : "OFF")), b -> {
            cfg.energy_hud_enabled = !cfg.energy_hud_enabled;
            cfg.save();
            this.init();
         }).dimensions(left, startY, colW, 20).build()
      );

      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Cooldown Pills: " + (cfg.energy_hud_show_cooldown_pills ? "ON" : "OFF")), b -> {
            cfg.energy_hud_show_cooldown_pills = !cfg.energy_hud_show_cooldown_pills;
            cfg.save();
            this.init();
         }).dimensions(left + colW + 10, startY, colW, 20).build()
      );

      int anchorY = startY + 38;
      int btnW = (panelW - (ANCHORS.length - 1) * 4) / ANCHORS.length;
      for (int i = 0; i < ANCHORS.length; i++) {
         String anchor = ANCHORS[i];
         String pretty = anchor.replace('_', ' ');
         ButtonWidget btn = this.addDrawableChild(
            ButtonWidget.builder(Text.literal(pretty), b -> {
               cfg.energy_hud_anchor = anchor;
               cfg.validate();
               cfg.save();
               this.init();
            }).dimensions(left + i * (btnW + 4), anchorY, btnW, 20).build()
         );
         btn.active = !anchor.equals(cfg.energy_hud_anchor);
      }

      int offXY = anchorY + 40;
      this.addOffsetRow(left, offXY, () -> cfg.energy_hud_offset_x, v -> {
         cfg.energy_hud_offset_x = Math.max(0, Math.min(2000, v));
         cfg.save();
         this.init();
      });

      int offYY = offXY + 28;
      this.addOffsetRow(left, offYY, () -> cfg.energy_hud_offset_y, v -> {
         cfg.energy_hud_offset_y = Math.max(0, Math.min(2000, v));
         cfg.save();
         this.init();
      });

      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Reset HUD Defaults (Bottom Right)"), b -> {
            cfg.energy_hud_enabled = true;
            cfg.energy_hud_show_cooldown_pills = true;
            cfg.energy_hud_anchor = "BOTTOM_RIGHT";
            cfg.energy_hud_offset_x = 8;
            cfg.energy_hud_offset_y = 8;
            cfg.save();
            this.init();
         }).dimensions(left, offYY + 36, 220, 20).build()
      );
   }

   private void addOffsetRow(int left, int y, IntSupplier getter, IntConsumer setter) {
      this.addDrawableChild(ButtonWidget.builder(Text.literal("-10"), b -> setter.accept(getter.getAsInt() - 10)).dimensions(left + 120, y, 38, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("-2"), b -> setter.accept(getter.getAsInt() - 2)).dimensions(left + 162, y, 32, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("+2"), b -> setter.accept(getter.getAsInt() + 2)).dimensions(left + 198, y, 32, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("+10"), b -> setter.accept(getter.getAsInt() + 10)).dimensions(left + 234, y, 38, 20).build());
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
         if (index < 0) index = 0;
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
      }
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
      }
      return "No selector for this power.";
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      int panelW = Math.min(480, this.width - 20);
      int left = (this.width - panelW) / 2;
      int top = Math.max(16, (this.height - 256) / 2);
      int panelH = 250;

      context.fill(0, 0, this.width, this.height, this.activeTab == Tab.HUD ? 0x440A0A12 : 0x990A0A12);
      context.fill(left - 8, top - 8, left + panelW + 8, top + panelH + 8, 0xCC12121C);
      context.fill(left - 8, top - 8, left + panelW + 8, top - 6, 0xFF6B5CE7);

      super.render(context, mouseX, mouseY, deltaTicks);

      switch (this.activeTab) {
         case POWER -> this.renderPowerTab(context, this.width / 2, top + 128);
         case GUIDE -> this.renderGuideDetail(context, left + 144, top + 28, panelW - 144, panelH - 30);
         case HUD -> this.renderHudTabLabels(context, left, top + 30);
      }
   }

   private void renderPowerTab(DrawContext context, int centerX, int centerY) {
      PowerType type = PowerType.fromId(ClientPowerData.powerMenuPowerId);
      String powerName = type == null ? "No power assigned" : type.getDisplayName();
      int accent = type == null ? 14151679 : type.getAccentColor();
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Assigned Power: " + powerName), centerX, centerY - 82, accent);
      String ritual = ClientPowerData.powerMenuDataReady ? ClientPowerData.powerMenuRitualStatus : "Waiting for server status…";
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("Ritual: " + ritual), centerX, centerY - 62, ClientPowerData.powerMenuRitualComplete ? 16769152 : 13684944
      );
      String awakening = ClientPowerData.powerMenuAwakeningUnlocked ? "unlocked" : "locked";
      String grimoire = ClientPowerData.powerMenuCarriesGrimoire ? "carried" : "not carried";
      context.drawCenteredTextWithShadow(
         this.textRenderer,
         Text.literal("Awakening: " + awakening + "  •  Grimoire: " + grimoire),
         centerX,
         centerY - 44,
         ClientPowerData.powerMenuCarriesGrimoire ? 13168895 : 12040119
      );
      int cooldown = ClientPowerData.powerMenuAwakeningCooldownTicks;
      String cooldownText = cooldown <= 0 ? "Ready / no tracked cooldown" : formatCooldown(cooldown);
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("Awakening cooldown: " + cooldownText), centerX, centerY - 26, cooldown <= 0 ? 12052161 : 16758922
      );
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.selectorStatus()), centerX, centerY - 6, 13101567);
      if ("poseidon".equals(ClientPowerData.powerMenuPowerId)) {
         context.drawCenteredTextWithShadow(
            this.textRenderer, Text.literal("Press P to review and confirm the terrain strip."), centerX, centerY + 52, 11130866
         );
      }
   }

   private void renderGuideDetail(DrawContext context, int x, int y, int w, int h) {
      PowerGuideData.PowerEntry entry = PowerGuideData.get(this.selectedGuidePower);
      if (entry == null) return;
      int accent = this.selectedGuidePower.getAccentColor();

      context.fill(x, y, x + w, y + h, 0xAA0C0C14);
      context.fill(x, y, x + 3, y + h, accent);

      int cy = y + 7;
      context.drawText(this.textRenderer, this.selectedGuidePower.getDisplayName() + "  (" + this.selectedGuidePower.getId() + ")", x + 10, cy, accent, true);
      cy += 12;
      context.drawText(this.textRenderer, entry.role(), x + 10, cy, 0xFFB0B8CC, false);
      cy += 14;

      if (!entry.passives().isEmpty()) {
         context.drawText(this.textRenderer, "Passives:", x + 10, cy, 0xFF8CE6B0, true);
         cy += 11;
         for (String p : entry.passives()) {
            for (OrderedText line : this.textRenderer.wrapLines(Text.literal("• " + p), w - 18)) {
               if (cy > y + h - 12) return;
               context.drawText(this.textRenderer, line, x + 12, cy, 0xFFDADAE6, false);
               cy += 10;
            }
         }
         cy += 4;
      }

      context.drawText(this.textRenderer, "Abilities (your live keybinds):", x + 10, cy, 0xFFFFD566, true);
      cy += 12;
      for (PowerGuideData.AbilityEntry ab : entry.abilities()) {
         if (cy > y + h - 12) return;
         String trigger = ab.key() != null ? "[" + YPKeybinds.getBoundKeyLabel(ab.key()) + "]" : "[" + ab.customTrigger() + "]";
         String full = trigger + " " + ab.name() + ": " + ab.description();
         for (OrderedText line : this.textRenderer.wrapLines(Text.literal(full), w - 18)) {
            if (cy > y + h - 12) return;
            context.drawText(this.textRenderer, line, x + 12, cy, 0xFFEAEAF5, false);
            cy += 10;
         }
         cy += 2;
      }
   }

   private void renderHudTabLabels(DrawContext context, int left, int startY) {
      YPConfig cfg = YPConfig.getInstance();
      context.drawText(this.textRenderer, "HUD Anchor (changes preview live behind this window):", left, startY + 26, 0xFFCCCCDD, true);
      context.drawText(this.textRenderer, "Offset X: " + cfg.energy_hud_offset_x + " px", left, startY + 84, 0xFFE6E6F2, true);
      context.drawText(this.textRenderer, "Offset Y: " + cfg.energy_hud_offset_y + " px", left, startY + 112, 0xFFE6E6F2, true);
      context.drawText(this.textRenderer, "Saves automatically to config/yppowers_config.json.", left, startY + 178, 0xFF8899AA, true);
   }

   private static String formatCooldown(int ticks) {
      long seconds = (Math.max(0L, (long)ticks) + 19L) / 20L;
      long days = seconds / 86400L;
      long hours = seconds % 86400L / 3600L;
      long minutes = seconds % 3600L / 60L;
      if (days > 0L) return days + "d " + hours + "h remaining";
      if (hours > 0L) return hours + "h " + minutes + "m remaining";
      return minutes > 0L ? minutes + "m " + seconds % 60L + "s remaining" : seconds + "s remaining";
   }

   private static String pretty(String id) {
      if (id != null && !id.isBlank()) {
         String lower = id.toLowerCase(Locale.ROOT);
         return Character.toUpperCase(lower.charAt(0)) + lower.substring(1).replace('_', ' ');
      }
      return "—";
   }

   private static String iceDescription(String id) {
      return switch (id == null ? "" : id) {
         case "ARMOURED" -> "defence";
         case "STEALTH" -> "concealment";
         case "ASSASSIN" -> "mobility and melee";
         default -> "";
      };
   }

   private static String spiritDescription(String id) {
      return switch (id == null ? "" : id) {
         case "WOLF" -> "fast companion";
         case "GUARDIAN" -> "ranged laser support";
         case "WITHER_SKELETON" -> "wither striker";
         default -> "";
      };
   }
}
