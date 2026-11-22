package com.yppowers.client.screen;

import com.yppowers.client.ClientState;
import com.yppowers.client.YPKeybinds;
import com.yppowers.config.HudConfig;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PowerId;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Two-tab in-game client screen (default key: P, or {@code /yp guide}):
 * 1. Powers Guide: browse all 17 powers with dynamic bound key labels.
 * 2. HUD Settings: live-adjust HUD visibility, anchor corner, and X/Y offsets without editing JSON.
 */
@Environment(EnvType.CLIENT)
public final class YPPowersGuideScreen extends Screen {
    private static final String[] ANCHORS = {"TOP_LEFT", "TOP_RIGHT", "TOP_CENTER", "BOTTOM_LEFT", "BOTTOM_RIGHT"};
    private static final int SIDEBAR_PER_PAGE = 9;

    private boolean hudTab = false;
    private PowerId selectedPower;
    private int sidebarPage = 0;

    public YPPowersGuideScreen() {
        super(Text.literal("YP Powers — Guide & HUD Settings"));
        PowerId assigned = ClientState.currentPower();
        this.selectedPower = assigned != null ? assigned : PowerId.DIVINITY;
        this.sidebarPage = this.selectedPower.ordinal() / SIDEBAR_PER_PAGE;
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();

        int panelW = Math.min(480, this.width - 24);
        int left = (this.width - panelW) / 2;
        int top = Math.max(18, (this.height - 248) / 2);

        // Top mode tabs
        int tabW = 130;
        ButtonWidget guideTabBtn = ButtonWidget.builder(Text.literal("Powers Guide (17)"), b -> {
            this.hudTab = false;
            this.init();
        }).dimensions(left, top, tabW, 20).build();
        guideTabBtn.active = this.hudTab;
        this.addDrawableChild(guideTabBtn);

        ButtonWidget hudTabBtn = ButtonWidget.builder(Text.literal("HUD Settings"), b -> {
            this.hudTab = true;
            this.init();
        }).dimensions(left + tabW + 6, top, tabW, 20).build();
        hudTabBtn.active = !this.hudTab;
        this.addDrawableChild(hudTabBtn);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(left + panelW - 64, top, 64, 20).build());

        if (this.hudTab) {
            initHudControls(left, top + 30, panelW);
        } else {
            initGuideSidebar(left, top + 28);
        }
    }

    private void initGuideSidebar(int left, int startY) {
        PowerId[] all = PowerId.values();
        PowerId assigned = ClientState.currentPower();
        int maxPage = (all.length - 1) / SIDEBAR_PER_PAGE;
        if (sidebarPage > maxPage) sidebarPage = maxPage;

        int start = sidebarPage * SIDEBAR_PER_PAGE;
        int end = Math.min(all.length, start + SIDEBAR_PER_PAGE);
        for (int i = start; i < end; i++) {
            PowerId id = all[i];
            int row = i - start;
            String label = (id == assigned ? "* " : "") + id.displayName();
            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
                this.selectedPower = id;
                this.init();
            }).dimensions(left, startY + row * 21, 134, 19).build();
            btn.active = (id != this.selectedPower);
            this.addDrawableChild(btn);
        }

        int navY = startY + SIDEBAR_PER_PAGE * 21 + 4;
        ButtonWidget prev = ButtonWidget.builder(Text.literal("< Prev"), b -> {
            if (sidebarPage > 0) {
                sidebarPage--;
                this.init();
            }
        }).dimensions(left, navY, 64, 18).build();
        prev.active = sidebarPage > 0;
        this.addDrawableChild(prev);

        ButtonWidget next = ButtonWidget.builder(Text.literal("Next >"), b -> {
            if (sidebarPage < maxPage) {
                sidebarPage++;
                this.init();
            }
        }).dimensions(left + 70, navY, 64, 18).build();
        next.active = sidebarPage < maxPage;
        this.addDrawableChild(next);
    }

    private void initHudControls(int left, int startY, int panelW) {
        HudConfig cfg = YPConfig.get().hud;
        int colW = (panelW - 12) / 2;

        // Visibility toggles
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Energy Bar: " + (cfg.showEnergyBar ? "ON" : "OFF")), b -> {
                    cfg.showEnergyBar = !cfg.showEnergyBar;
                    YPConfig.save();
                    this.init();
                }).dimensions(left, startY, colW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Power & Cooldown Panel: " + (cfg.showPowerPanel ? "ON" : "OFF")), b -> {
                    cfg.showPowerPanel = !cfg.showPowerPanel;
                    YPConfig.save();
                    this.init();
                }).dimensions(left + colW + 12, startY, colW, 20).build());

        // Anchor buttons row
        int anchorY = startY + 38;
        int btnW = (panelW - (ANCHORS.length - 1) * 4) / ANCHORS.length;
        for (int i = 0; i < ANCHORS.length; i++) {
            String anchor = ANCHORS[i];
            String pretty = anchor.replace('_', ' ');
            ButtonWidget btn = ButtonWidget.builder(Text.literal(pretty), b -> {
                cfg.anchor = anchor;
                cfg.validate();
                YPConfig.save();
                this.init();
            }).dimensions(left + i * (btnW + 4), anchorY, btnW, 20).build();
            btn.active = !anchor.equals(cfg.anchor);
            this.addDrawableChild(btn);
        }

        // Offset X controls
        int offXY = anchorY + 40;
        addOffsetRow(left, offXY, "X", () -> cfg.offsetX, v -> {
            cfg.offsetX = Math.max(0, Math.min(2000, v));
            YPConfig.save();
            this.init();
        });

        // Offset Y controls
        int offYY = offXY + 28;
        addOffsetRow(left, offYY, "Y", () -> cfg.offsetY, v -> {
            cfg.offsetY = Math.max(0, Math.min(2000, v));
            YPConfig.save();
            this.init();
        });

        // Reset defaults
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset HUD Defaults"), b -> {
            cfg.showEnergyBar = true;
            cfg.showPowerPanel = true;
            cfg.anchor = "TOP_LEFT";
            cfg.offsetX = 8;
            cfg.offsetY = 8;
            YPConfig.save();
            this.init();
        }).dimensions(left, offYY + 36, 150, 20).build());
    }

    private void addOffsetRow(int left, int y, String axis, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-10"), b -> setter.accept(getter.getAsInt() - 10))
                .dimensions(left + 110, y, 36, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("-2"), b -> setter.accept(getter.getAsInt() - 2))
                .dimensions(left + 150, y, 30, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+2"), b -> setter.accept(getter.getAsInt() + 2))
                .dimensions(left + 184, y, 30, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("+10"), b -> setter.accept(getter.getAsInt() + 10))
                .dimensions(left + 218, y, 36, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = Math.min(480, this.width - 24);
        int left = (this.width - panelW) / 2;
        int top = Math.max(18, (this.height - 248) / 2);
        int panelH = 246;

        // Keep background light in HUD Settings tab so user can preview live HUD placement behind it!
        context.fill(0, 0, this.width, this.height, this.hudTab ? 0x440A0A12 : 0x990A0A12);
        context.fill(left - 8, top - 8, left + panelW + 8, top + panelH + 8, 0xCC12121C);
        context.fill(left - 8, top - 8, left + panelW + 8, top - 6, 0xFF6B5CE7);

        super.render(context, mouseX, mouseY, delta);

        if (this.hudTab) {
            renderHudTabLabels(context, left, top + 30);
        } else {
            renderPowerDetail(context, left + 144, top + 28, panelW - 144, panelH - 32);
        }
    }

    private void renderHudTabLabels(DrawContext context, int left, int startY) {
        HudConfig cfg = YPConfig.get().hud;
        context.drawText(this.textRenderer, "HUD Anchor (changes preview live behind this window):", left, startY + 26, 0xFFCCCCDD, true);
        context.drawText(this.textRenderer, "Offset X: " + cfg.offsetX + " px", left, startY + 84, 0xFFE6E6F2, true);
        context.drawText(this.textRenderer, "Offset Y: " + cfg.offsetY + " px", left, startY + 112, 0xFFE6E6F2, true);
        context.drawText(this.textRenderer, "Settings save automatically to config/yppowers.json.", left, startY + 178, 0xFF8899AA, true);
    }

    private void renderPowerDetail(DrawContext context, int x, int y, int w, int h) {
        PowerGuideData.PowerEntry entry = PowerGuideData.get(this.selectedPower);
        if (entry == null) return;
        int accent = this.selectedPower.accentColor();

        context.fill(x, y, x + w, y + h, 0xAA0C0C14);
        context.fill(x, y, x + 3, y + h, accent);

        int cy = y + 7;
        String header = this.selectedPower.displayName() + "  (" + this.selectedPower.id() + ")";
        context.drawText(this.textRenderer, header, x + 10, cy, accent, true);
        cy += 12;
        context.drawText(this.textRenderer, entry.role(), x + 10, cy, 0xFFB0B8CC, false);
        cy += 14;

        // Passives
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

        // Abilities with live bound keys
        context.drawText(this.textRenderer, "Abilities (your current keybinds):", x + 10, cy, 0xFFFFD566, true);
        cy += 12;
        for (PowerGuideData.AbilityEntry ab : entry.abilities()) {
            if (cy > y + h - 12) return;
            String keyLabel = ab.slot() != null ? YPKeybinds.getBoundKeyLabel(ab.slot()) : ab.customTrigger();
            String full = "[" + keyLabel + "] " + ab.name() + " — " + ab.description();
            for (OrderedText line : this.textRenderer.wrapLines(Text.literal(full), w - 18)) {
                if (cy > y + h - 12) return;
                context.drawText(this.textRenderer, line, x + 12, cy, 0xFFE8E8F4, false);
                cy += 10;
            }
            cy += 2;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
