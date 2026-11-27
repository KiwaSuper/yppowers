package com.yppowers.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public final class TeleportGambitStructureScreen extends Screen {
   private static final int VISIBLE_CHOICES = 5;
   private final List<String> structures;
   private final int cooldownTicks;
   private final List<ButtonWidget> choiceButtons = new ArrayList<>();
   private List<String> filtered = List.of();
   private TextFieldWidget searchField;
   private ButtonWidget previousPage;
   private ButtonWidget nextPage;
   private int pageOffset;

   public TeleportGambitStructureScreen(String encodedIds, int cooldownTicks) {
      super(Text.literal("Teleport Gambit — Choose a Structure"));
      this.structures = encodedIds != null && !encodedIds.isBlank()
         ? Arrays.stream(encodedIds.split("\\n"))
            .map(String::trim)
            .filter(id -> id.length() <= 128 && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            .distinct()
            .sorted()
            .toList()
         : List.of();
      this.cooldownTicks = Math.max(0, cooldownTicks);
   }

   @Override
   protected void init() {
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      int top = centerY - 88;
      this.searchField = new TextFieldWidget(this.textRenderer, centerX - 145, top, 290, 20, Text.literal("Search structures"));
      this.searchField.setMaxLength(128);
      this.searchField.setPlaceholder(Text.literal("e.g. minecraft:village_plains"));
      this.addDrawableChild(this.searchField);
      this.choiceButtons.clear();

      for (int i = 0; i < 5; i++) {
         int slot = i;
         this.choiceButtons
            .add(
               this.addDrawableChild(
                  ButtonWidget.builder(Text.literal("—"), button -> this.select(slot)).dimensions(centerX - 140, top + 26 + i * 21, 280, 20).build()
               )
            );
      }

      this.previousPage = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Previous"), button -> this.changePage(-1)).dimensions(centerX - 140, top + 139, 90, 20).build()
      );
      this.nextPage = this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Next"), button -> this.changePage(1)).dimensions(centerX + 50, top + 139, 90, 20).build()
      );
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.close()).dimensions(centerX - 45, top + 164, 90, 20).build());
      this.searchField.setChangedListener(value -> {
         this.pageOffset = 0;
         this.refreshChoices();
      });
      this.refreshChoices();
      this.setInitialFocus(this.searchField);
   }

   private void refreshChoices() {
      String query = this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
      this.filtered = this.structures.stream().filter(id -> query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)).toList();
      int maxOffset = this.filtered.isEmpty() ? 0 : (this.filtered.size() - 1) / 5 * 5;
      this.pageOffset = Math.min(this.pageOffset, maxOffset);

      for (int i = 0; i < this.choiceButtons.size(); i++) {
         int index = this.pageOffset + i;
         ButtonWidget button = this.choiceButtons.get(i);
         if (index < this.filtered.size()) {
            button.setMessage(Text.literal(this.filtered.get(index)));
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
         this.nextPage.active = this.pageOffset + 5 < this.filtered.size();
      }
   }

   private void select(int slot) {
      int index = this.pageOffset + slot;
      if (index >= 0 && index < this.filtered.size()) {
         if (YPClientNetworking.sendTeleportGambitStructure(this.filtered.get(index))) {
            this.close();
         }
      }
   }

   private void changePage(int direction) {
      int maxOffset = this.filtered.isEmpty() ? 0 : (this.filtered.size() - 1) / 5 * 5;
      this.pageOffset = Math.max(0, Math.min(maxOffset, this.pageOffset + direction * 5));
      this.refreshChoices();
   }

   @Override
   public boolean keyPressed(KeyInput input) {
      if (this.searchField != null && this.searchField.isFocused() && input.isEnter() && !this.filtered.isEmpty()) {
         this.select(0);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      int top = centerY - 105;
      int left = Math.max(6, centerX - 166);
      int right = Math.min(this.width - 6, centerX + 166);
      context.fill(0, 0, this.width, this.height, -804251612);
      context.fill(left, top, right, Math.min(this.height - 6, centerY + 101), -400939208);
      context.fill(left, top, right, top + 1, -13988);
      context.fill(left, Math.min(this.height - 7, centerY + 100), right, Math.min(this.height - 6, centerY + 101), -13988);
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 100, 16777215);
      int page = this.filtered.isEmpty() ? 0 : this.pageOffset / 5 + 1;
      int pages = Math.max(1, (this.filtered.size() + 5 - 1) / 5);
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("Page " + page + "/" + pages + " · " + this.structures.size() + " allowed"), centerX, centerY + 60, 12115967
      );
      String cooldown = this.cooldownTicks > 0 ? "Cooldown: " + formatDuration(this.cooldownTicks) : "Cooldown begins only after a successful teleport";
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(cooldown), centerX, centerY + 79, 11184810);
      if (this.structures.isEmpty()) {
         context.drawCenteredTextWithShadow(
            this.textRenderer, Text.literal("No structures are available in this server's registry."), centerX, centerY + 24, 16744576
         );
      } else if (this.filtered.isEmpty()) {
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No matching structure IDs."), centerX, centerY + 24, 16744576);
      }
   }

   private static String formatDuration(int ticks) {
      long seconds = Math.max(0, ticks) / 20L;
      long hours = seconds / 3600L;
      long minutes = seconds % 3600L / 60L;
      return hours + "h " + minutes + "m";
   }
}
