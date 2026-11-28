package com.yppowers.client;

import com.yppowers.power.shadowarcher.ArrowType;
import com.yppowers.power.shadowarcher.ShadowArcherMorphRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShadowArcherMorphScreen extends Screen {
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Shadow Archer Menu");
   private static final int VISIBLE_SUGGESTIONS = 3;
   private TextFieldWidget mobName;
   private final List<ButtonWidget> suggestionButtons = new ArrayList<>();
   private List<String> filteredSuggestions = List.of();
   private int pageOffset;
   private ButtonWidget previousPage;
   private ButtonWidget nextPage;
   private ButtonWidget previousArrow;
   private ButtonWidget nextArrow;
   private boolean renderFailureHandled;

   public ShadowArcherMorphScreen() {
      super(Text.literal("Shadow Archer — Morph & Arrows"));
   }

   @Override
   protected void init() {
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      int compact = this.height < 280 ? 22 : 0;
      this.suggestionButtons.clear();
      this.mobName = new TextFieldWidget(this.textRenderer, centerX - 135, centerY - 75, 270, 20, Text.literal("Search mob name or ID"));
      this.mobName.setMaxLength(128);
      this.mobName.setPlaceholder(Text.literal("e.g. ender_dragon, minecraft:spider"));
      this.addDrawableChild(this.mobName);

      for (int i = 0; i < 3; i++) {
         int slot = i;
         ButtonWidget button = ButtonWidget.builder(Text.literal("—"), ignored -> this.chooseSuggestion(slot))
            .dimensions(centerX - 130, centerY - 51 + i * 20, 260, 19)
            .build();
         this.suggestionButtons.add(button);
         this.addDrawableChild(button);
      }

      this.previousPage = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Previous"), ignored -> this.changePage(-1)).dimensions(centerX - 130, centerY + 12 - compact, 80, 18).build()
      );
      this.nextPage = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Next"), ignored -> this.changePage(1)).dimensions(centerX + 50, centerY + 12 - compact, 80, 18).build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Morph"), ignored -> this.submit()).dimensions(centerX - 130, centerY + 35 - compact, 125, 20).build()
      );
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Human / Cancel"), ignored -> {
         YPClientNetworking.sendMorphRequest("human");
         this.close();
      }).dimensions(centerX + 5, centerY + 35 - compact, 125, 20).build());
      this.previousArrow = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Prev Arrow"), ignored -> this.chooseArrow(-1)).dimensions(centerX - 130, centerY + 61 - compact, 84, 20).build()
      );
      this.nextArrow = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Next Arrow"), ignored -> this.chooseArrow(1)).dimensions(centerX + 46, centerY + 61 - compact, 84, 20).build()
      );
      this.mobName.setChangedListener(ignored -> {
         this.pageOffset = 0;
         this.refreshSuggestions();
      });
      this.refreshSuggestions();
      this.refreshArrowStatus();
      YPClientNetworking.requestPowerMenuStatus();
   }

   @Override
   protected void setInitialFocus() {
      if (this.mobName != null) {
         this.setInitialFocus(this.mobName);
      }
   }

   private void refreshSuggestions() {
      String query = this.mobName == null ? "" : this.mobName.getText().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      this.filteredSuggestions = ShadowArcherMorphRegistry.getVanillaFormIds()
         .stream()
         .filter(
            idx -> query.isEmpty()
               || idx.contains(query)
               || ShadowArcherMorphRegistry.forId(idx).displayName().toLowerCase(Locale.ROOT).contains(query.replace('_', ' '))
         )
         .toList();
      int maxOffset = this.filteredSuggestions.isEmpty() ? 0 : (this.filteredSuggestions.size() - 1) / 3 * 3;
      this.pageOffset = Math.min(this.pageOffset, maxOffset);

      for (int i = 0; i < this.suggestionButtons.size(); i++) {
         int index = this.pageOffset + i;
         ButtonWidget button = this.suggestionButtons.get(i);
         if (index < this.filteredSuggestions.size()) {
            String id = this.filteredSuggestions.get(index);
            ShadowArcherMorphRegistry.Profile profile = ShadowArcherMorphRegistry.forId(id);
            boolean unlocked = ClientPowerData.unlockedShadowArcherMorphs.contains("minecraft:" + id)
               || ClientPowerData.unlockedShadowArcherMorphs.contains(id);
            button.setMessage(Text.literal((unlocked ? "[Unlocked] " : "[Locked] ") + profile.displayName() + "  [" + id + "]"));
            button.active = true;
         } else {
            button.setMessage(Text.literal("—"));
            button.active = false;
         }
      }

      if (this.previousPage != null) {
         this.previousPage.active = this.pageOffset > 0;
      }

      if (this.nextPage != null) {
         this.nextPage.active = this.pageOffset + 3 < this.filteredSuggestions.size();
      }
   }

   public void refreshUnlockStatus() {
      this.refreshSuggestions();
   }

   public void refreshArrowStatus() {
      boolean hasChoices = ClientPowerData.powerMenuDataReady && ClientPowerData.powerMenuAvailableArrows.size() > 1;
      if (this.previousArrow != null) {
         this.previousArrow.active = hasChoices;
      }

      if (this.nextArrow != null) {
         this.nextArrow.active = hasChoices;
      }
   }

   private void chooseSuggestion(int slot) {
      int index = this.pageOffset + slot;
      if (this.mobName != null && index >= 0 && index < this.filteredSuggestions.size()) {
         this.mobName.setText(this.filteredSuggestions.get(index));
         this.mobName.setCursorToEnd(false);
         this.mobName.setFocused(true);
      }
   }

   private void changePage(int direction) {
      int next = this.pageOffset + direction * 3;
      int maxOffset = this.filteredSuggestions.isEmpty() ? 0 : (this.filteredSuggestions.size() - 1) / 3 * 3;
      this.pageOffset = Math.max(0, Math.min(next, maxOffset));
      this.refreshSuggestions();
   }

   private void chooseArrow(int direction) {
      List<String> unlocked = ClientPowerData.powerMenuAvailableArrows;
      if (!unlocked.isEmpty()) {
         int index = unlocked.indexOf(ClientPowerData.powerMenuSelectedArrow);
         if (index < 0) {
            index = 0;
         }

         String next = unlocked.get(Math.floorMod(index + direction, unlocked.size()));
         YPClientNetworking.sendPowerMenuAction("arrow:" + next);
      }
   }

   private void submit() {
      if (this.mobName != null) {
         String value = this.mobName.getText().trim();
         if (!value.isEmpty()) {
            YPClientNetworking.sendMorphRequest(value);
            this.close();
         }
      }
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      if (this.mobName != null && this.mobName.isFocused() && input.isEnter()) {
         this.submit();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      if (this.renderFailureHandled) {
         this.renderRenderFailure(context);
      } else {
         try {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int compact = this.height < 280 ? 22 : 0;
            int panelLeft = Math.max(8, centerX - 166);
            int panelRight = Math.min(this.width - 8, centerX + 166);
            int panelTop = Math.max(8, centerY - 119);
            int panelBottom = Math.min(this.height - 8, centerY + 124 - compact);
            context.fill(0, 0, this.width, this.height, -804251612);
            context.fill(panelLeft, panelTop, panelRight, panelBottom, -400939208);
            context.fill(panelLeft, panelTop, panelRight, panelTop + 1, -10106881);
            context.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, -10106881);
            context.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, -10106881);
            context.fill(panelRight - 1, panelTop, panelRight, panelBottom, -10106881);
            super.render(context, mouseX, mouseY, deltaTicks);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 105, 16777215);
            context.drawCenteredTextWithShadow(
               this.textRenderer, Text.literal("Browse vanilla mob forms; each form must be unlocked by defeating it."), centerX, centerY - 89, 12115967
            );
            int page = this.filteredSuggestions.isEmpty() ? 0 : this.pageOffset / 3 + 1;
            int pages = Math.max(1, (this.filteredSuggestions.size() + 3 - 1) / 3);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Page " + page + "/" + pages), centerX, centerY + 15 - compact, 11184810);
            int arrowY = centerY + 65 - compact;
            ArrowType selected = null;

            try {
               selected = ArrowType.valueOf(ClientPowerData.powerMenuSelectedArrow);
            } catch (IllegalArgumentException var18) {
            }

            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Special Arrow"), centerX, centerY + 50 - compact, 12115967);
            String arrowTitle = !ClientPowerData.powerMenuDataReady
               ? "Waiting for server…"
               : (selected == null ? "No arrow unlocked" : selected.getDisplayName());
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(arrowTitle), centerX, arrowY, 16777215);
            context.drawCenteredTextWithShadow(
               this.textRenderer,
               Text.literal(
                  !ClientPowerData.powerMenuDataReady
                     ? "Unlocks and selection load from the server."
                     : (selected == null ? "Unlock arrow types to choose them." : selected.getDescription())
               ),
               centerX,
               centerY + 86 - compact,
               11130866
            );
            ShadowArcherMorphRegistry.Profile profile = this.mobName == null ? null : ShadowArcherMorphRegistry.findProfileForInput(this.mobName.getText());
            if (profile != null) {
               context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(profile.abilitySummary()), centerX, centerY + 103 - compact, 16777215);
               context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(profile.traitSummary()), centerX, centerY + 116 - compact, 10347263);
            } else {
               context.drawCenteredTextWithShadow(
                  this.textRenderer, Text.literal("G / H / R activate the selected form's abilities."), centerX, centerY + 103 - compact, 12115967
               );
            }
         } catch (LinkageError | RuntimeException var19) {
            if (!this.renderFailureHandled) {
               this.renderFailureHandled = true;
               LOGGER.error("Shadow Archer menu failed while rendering; keeping a diagnostic screen open", var19);
            }

            this.renderRenderFailure(context);
         }
      }
   }

   private void renderRenderFailure(DrawContext context) {
      context.fill(0, 0, this.width, this.height, -535816156);
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Shadow Archer Menu"), this.width / 2, this.height / 2 - 18, 16777215);
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("The menu encountered a rendering error."), this.width / 2, this.height / 2 + 2, 16758197
      );
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("See latest.log for the stack trace; press Esc to close."), this.width / 2, this.height / 2 + 18, 12115967
      );
   }

   @Override
   public boolean shouldPause() {
      return false;
   }
}
