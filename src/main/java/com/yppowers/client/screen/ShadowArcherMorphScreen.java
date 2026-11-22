package com.yppowers.client.screen;

import com.yppowers.client.ClientState;
import com.yppowers.client.YPClientNetworking;
import com.yppowers.power.shadowarcher.MorphRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Polished Shadow Archer Morph Menu featuring category filter tabs, live search,
 * unlock progress indicator, active-form highlighting, and instant human return.
 */
@Environment(EnvType.CLIENT)
public final class ShadowArcherMorphScreen extends Screen {
    private static final int COLS = 3;
    private static final int ROWS_PER_PAGE = 5;

    private TextFieldWidget searchField;
    private MorphRegistry.Category activeCategory = MorphRegistry.Category.ALL;
    private String searchQuery = "";
    private int page = 0;
    private final List<ButtonWidget> entryButtons = new ArrayList<>();
    private ButtonWidget prevBtn;
    private ButtonWidget nextBtn;

    public ShadowArcherMorphScreen() {
        super(Text.literal("Shadow Archer — Morph Catalog"));
    }

    @Override
    protected void init() {
        super.init();
        int panelW = Math.min(440, this.width - 24);
        int left = (this.width - panelW) / 2;

        // Search box
        this.searchField = new TextFieldWidget(this.textRenderer, left, 32, panelW - 110, 18, Text.literal("Search mobs..."));
        this.searchField.setText(searchQuery);
        this.searchField.setChangedListener(text -> {
            this.searchQuery = text;
            this.page = 0;
            rebuildGrid();
        });
        this.addDrawableChild(this.searchField);
        this.setInitialFocus(this.searchField);

        // Return to Human button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Human Form"), btn -> {
            YPClientNetworking.sendMorphRequest("human");
            this.close();
        }).dimensions(left + panelW - 104, 31, 104, 20).build());

        // Category tabs
        MorphRegistry.Category[] cats = MorphRegistry.Category.values();
        int tabW = (panelW - (cats.length - 1) * 3) / cats.length;
        for (int i = 0; i < cats.length; i++) {
            MorphRegistry.Category cat = cats[i];
            int tx = left + i * (tabW + 3);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(cat.label), btn -> {
                this.activeCategory = cat;
                this.page = 0;
                rebuildGrid();
            }).dimensions(tx, 56, tabW, 18).build());
        }

        int footerY = this.height - 28;
        this.prevBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"), btn -> {
            if (page > 0) {
                page--;
                rebuildGrid();
            }
        }).dimensions(left, footerY, 70, 20).build());

        this.nextBtn = this.addDrawableChild(ButtonWidget.builder(Text.literal("Next >"), btn -> {
            page++;
            rebuildGrid();
        }).dimensions(left + panelW - 70, footerY, 70, 20).build());

        rebuildGrid();
    }

    private void rebuildGrid() {
        for (ButtonWidget b : entryButtons) this.remove(b);
        entryButtons.clear();

        List<MorphRegistry.MorphProfile> filtered = MorphRegistry.filter(activeCategory, searchQuery, ClientState.unlockedMorphs);
        // Sort unlocked forms first for fast combat picking
        filtered.sort((a, b) -> {
            boolean ua = ClientState.unlockedMorphs.contains(a.entityId());
            boolean ub = ClientState.unlockedMorphs.contains(b.entityId());
            if (ua != ub) return ua ? -1 : 1;
            return a.displayName().compareToIgnoreCase(b.displayName());
        });

        int perPage = COLS * ROWS_PER_PAGE;
        int maxPage = Math.max(0, (filtered.size() - 1) / perPage);
        if (page > maxPage) page = maxPage;

        int panelW = Math.min(440, this.width - 24);
        int left = (this.width - panelW) / 2;
        int cellW = (panelW - (COLS - 1) * 6) / COLS;
        int startIdx = page * perPage;
        int endIdx = Math.min(filtered.size(), startIdx + perPage);

        for (int i = startIdx; i < endIdx; i++) {
            MorphRegistry.MorphProfile profile = filtered.get(i);
            int slot = i - startIdx;
            int col = slot % COLS;
            int row = slot / COLS;
            int bx = left + col * (cellW + 6);
            int by = 82 + row * 24;

            boolean unlocked = ClientState.unlockedMorphs.contains(profile.entityId());
            boolean active = ClientState.shadowArcherMorphed && profile.entityId().equals(ClientState.shadowArcherMorphMobId);
            String label = (active ? "* " : unlocked ? "" : "[Locked] ") + profile.displayName();

            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
                YPClientNetworking.sendMorphRequest(profile.entityId());
                this.close();
            }).dimensions(bx, by, cellW, 20).build();
            btn.active = unlocked;
            this.addDrawableChild(btn);
            entryButtons.add(btn);
        }

        if (prevBtn != null) prevBtn.active = page > 0;
        if (nextBtn != null) nextBtn.active = page < maxPage;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xAA0C0B14);
        super.render(context, mouseX, mouseY, delta);
        String header = "Shadow Archer Morphs  •  " + ClientState.unlockedMorphs.size() + " Unlocked  •  [" + activeCategory.label + "]";
        context.drawCenteredTextWithShadow(this.textRenderer, header, this.width / 2, 12, 0xFFD4B5FF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
