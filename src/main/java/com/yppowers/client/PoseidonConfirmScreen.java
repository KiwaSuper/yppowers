package com.yppowers.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class PoseidonConfirmScreen extends Screen {
   private final int radius;
   private final int loadedChunks;
   private ButtonWidget confirmButton;

   public PoseidonConfirmScreen(int radius, int loadedChunks) {
      super(Text.literal("Confirm Poseidon's Terrain Strip"));
      this.radius = Math.max(64, Math.min(512, radius));
      this.loadedChunks = Math.max(0, Math.min(4096, loadedChunks));
   }

   @Override
   protected void init() {
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      this.confirmButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm terrain strip"), button -> {
         YPClientNetworking.confirmPoseidonSink();
         this.close();
      }).dimensions(centerX - 112, centerY + 45, 132, 22).build());
      this.confirmButton.active = this.loadedChunks > 0;
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.close()).dimensions(centerX + 28, centerY + 45, 84, 22).build());
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      int left = Math.max(8, centerX - 170);
      int right = Math.min(this.width - 8, centerX + 170);
      int top = Math.max(8, centerY - 72);
      int bottom = Math.min(this.height - 8, centerY + 76);
      context.fill(0, 0, this.width, this.height, -535818216);
      context.fill(left, top, right, bottom, -265872348);
      context.fill(left, top, right, top + 2, -38049);
      context.fill(left, bottom - 2, right, bottom, -38049);
      super.render(context, mouseX, mouseY, deltaTicks);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, centerY - 61, -10800);
      context.drawCenteredTextWithShadow(
         this.textRenderer, Text.literal("Radius: " + this.radius + " blocks  •  loaded chunks: " + this.loadedChunks), centerX, centerY - 39, 16777215
      );
      int textWidth = Math.max(160, right - left - 24);
      if (this.loadedChunks == 0) {
         this.drawWrapped(
            context, "No eligible loaded chunks are available. This Awakening only works in the Overworld.", centerX, centerY - 21, textWidth, -14993
         );
      } else {
         this.drawWrapped(
            context, "Only chunks loaded when you confirm are eligible; unloaded terrain is left alone.", centerX, centerY - 21, textWidth, -2823950
         );
      }

      this.drawWrapped(
         context, "This destroys terrain and item drops in the affected area. The change cannot be undone.", centerX, centerY + 1, textWidth, -29309
      );
      context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Cancel or press Esc to leave the world unchanged."), centerX, centerY + 28, 13224393);
   }

   private void drawWrapped(DrawContext context, String value, int centerX, int startY, int maxWidth, int color) {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();

      for (String word : value.split("\\s+")) {
         String candidate = line.isEmpty() ? word : line + " " + word;
         if (!line.isEmpty() && this.textRenderer.getWidth(candidate) > maxWidth) {
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
         } else {
            if (!line.isEmpty()) {
               line.append(' ');
            }

            line.append(word);
         }
      }

      if (!line.isEmpty()) {
         lines.add(line.toString());
      }

      for (int index = 0; index < lines.size(); index++) {
         context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(lines.get(index)), centerX, startY + index * 11, color);
      }
   }

   @Override
   public boolean shouldPause() {
      return false;
   }
}
