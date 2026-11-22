package com.yppowers.client.hud;

import com.yppowers.YPPowersMod;
import com.yppowers.client.ClientState;
import com.yppowers.client.YPKeybinds;
import com.yppowers.config.HudConfig;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.PowerId;
import com.yppowers.network.HudSnapshot;
import com.yppowers.system.CooldownSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Upgraded YP Powers HUD:
 * - Anchored to TOP_LEFT by default (configurable via in-game HUD Settings or config/yppowers.json).
 * - Displays actual rebound key labels dynamically via {@link YPKeybinds#getBoundKeyLabel(AbilitySlot)}.
 * - Renders smooth draining cooldown progress bars inside cooling-down ability pills.
 */
@Environment(EnvType.CLIENT)
public final class YPHudOverlay {
    private static final int MAX_PILL_ROW_WIDTH = 300;

    private record PillData(String text, int width, int textColor, int underlineColor, float cooldownRatio) {}

    private YPHudOverlay() {}

    private static Identifier sparkTexture(String name) {
        return Identifier.of(YPPowersMod.MOD_ID, "textures/gui/energy/" + name + ".png");
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        HudConfig cfg = YPConfig.get().hud;
        HudSnapshot snap = ClientState.hud;
        PowerId powerId = ClientState.currentPower();
        TextRenderer tr = client.textRenderer;
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        boolean showHeader = cfg.showPowerPanel && powerId != null;
        String headerTitle = showHeader ? powerId.displayName() : "";
        String headerChip = showHeader
                ? (snap.graceActive() ? "GRACE ACTIVE"
                : (snap.lockoutTicks() > 0 ? "LOCKED " + CooldownSystem.formatSeconds(snap.lockoutTicks()) : snap.statusChip()))
                : "";
        int headerWidth = showHeader
                ? tr.getWidth(headerChip.isEmpty() ? headerTitle : headerTitle + "  |  " + headerChip) + 10
                : 0;

        List<List<PillData>> pillRows = new ArrayList<>();
        if (showHeader && !snap.slots().isEmpty()) {
            int accent = powerId.accentColor();
            List<PillData> currentRow = new ArrayList<>();
            int rowW = 0;
            for (HudSnapshot.SlotEntry slot : snap.slots()) {
                AbilitySlot abilitySlot = AbilitySlot.fromWireId(slot.slotWireId());
                String key = YPKeybinds.getBoundKeyLabel(abilitySlot);
                String suffix;
                int textColor;
                float cdRatio = 0.0f;
                if (slot.cooldownTicks() > 0) {
                    suffix = " " + CooldownSystem.formatSeconds(slot.cooldownTicks());
                    textColor = 0xFFFFCC77;
                    int denom = Math.max(slot.cooldownTicks(), Math.max(1, slot.maxCooldownTicks()));
                    cdRatio = Math.max(0.0f, Math.min(1.0f, (float) slot.cooldownTicks() / (float) denom));
                } else if (slot.isToggle()) {
                    suffix = slot.toggleActive() ? " ON" : " OFF";
                    textColor = slot.toggleActive() ? 0xFF66FF88 : 0xFF999999;
                } else if (snap.graceActive() || snap.bleedingTicks() > 0 || snap.lockoutTicks() > 0 || !slot.canAfford()) {
                    suffix = "";
                    textColor = 0xFF888888;
                } else {
                    suffix = "";
                    textColor = 0xFFFFFFFF;
                }
                String text = "[" + key + "] " + slot.label() + suffix;
                int w = tr.getWidth(text) + 6;
                int underline = slot.cooldownTicks() > 0 ? 0xCCFF9933 : (accent & 0x00FFFFFF) | 0xCC000000;
                if (!currentRow.isEmpty() && rowW + 3 + w > MAX_PILL_ROW_WIDTH) {
                    pillRows.add(currentRow);
                    currentRow = new ArrayList<>();
                    rowW = 0;
                }
                currentRow.add(new PillData(text, w, textColor, underline, cdRatio));
                rowW += (currentRow.size() > 1 ? 3 : 0) + w;
            }
            if (!currentRow.isEmpty()) pillRows.add(currentRow);
        }

        boolean showEnergy = cfg.showEnergyBar;
        boolean showCombat = snap.combatTicks() > 0;
        boolean showWarning = ClientState.hudWarningTicks > 0 && !ClientState.hudWarningText.isBlank();

        int totalHeight = 0;
        if (showHeader) totalHeight += 15;
        if (showEnergy) totalHeight += 13;
        totalHeight += pillRows.size() * 14;
        if (showCombat) totalHeight += 14;
        if (showWarning) totalHeight += 13;
        if (totalHeight <= 0) return;

        boolean bottom = "BOTTOM_LEFT".equals(cfg.anchor) || "BOTTOM_RIGHT".equals(cfg.anchor);
        boolean right = "TOP_RIGHT".equals(cfg.anchor) || "BOTTOM_RIGHT".equals(cfg.anchor);
        boolean center = "TOP_CENTER".equals(cfg.anchor);

        int cursorY = bottom ? Math.max(4, screenH - cfg.offsetY - totalHeight) : cfg.offsetY;

        // 1. Power Badge + Status Chip
        if (showHeader) {
            int accent = powerId.accentColor();
            int x = alignX(cfg, screenW, headerWidth, right, center);
            context.fill(x, cursorY, x + headerWidth, cursorY + 12, 0xBB101018);
            context.fill(x, cursorY, x + 2, cursorY + 12, accent);
            context.drawText(tr, headerTitle, x + 5, cursorY + 2, accent, true);
            if (!headerChip.isEmpty()) {
                int chipX = x + 5 + tr.getWidth(headerTitle);
                context.drawText(tr, "  |  ", chipX, cursorY + 2, 0xFF666677, false);
                int chipColor = snap.graceActive() ? 0xFFFFD54F : (snap.lockoutTicks() > 0 ? 0xFFFF6666 : 0xFFE6E6F2);
                context.drawText(tr, headerChip, chipX + tr.getWidth("  |  "), cursorY + 2, chipColor, true);
            }
            cursorY += 15;
        }

        // 2. 10-Spark Energy Bar
        if (showEnergy) {
            double max = Math.max(1.0, snap.maxEnergy());
            double energy = Math.max(0.0, Math.min(max, snap.energy()));
            boolean bleeding = snap.bleedingTicks() > 0;
            String color = bleeding ? "red" : (energy / max <= cfg.energyLowThresholdPercent ? "orange" : "purple");
            String statusText = bleeding
                    ? "BLEEDING " + CooldownSystem.formatSeconds(snap.bleedingTicks())
                    : (int) Math.floor(energy) + "/" + (int) Math.round(max);
            int statusColor = bleeding ? 0xFFFF6677 : 0xFFD0C4E8;
            int barWidth = (bleeding ? 113 : 104) + tr.getWidth(statusText);
            int x = alignX(cfg, screenW, barWidth, right, center);

            double perSpark = max / 10.0;
            for (int i = 0; i < 10; i++) {
                double fill = Math.max(0.0, Math.min(1.0, (energy - i * perSpark) / perSpark));
                Identifier sprite;
                if (fill >= 0.999) sprite = sparkTexture("energy_spark_full_" + color);
                else if (fill <= 0.001) sprite = sparkTexture("energy_spark_empty");
                else {
                    int partial = Math.max(1, Math.min(9, (int) Math.ceil(fill * 9.0)));
                    sprite = sparkTexture("energy_spark_partial_" + partial + "_" + color);
                }
                context.drawTexture(RenderPipelines.GUI_TEXTURED, sprite, x + i * 10, cursorY, 0.0f, 0.0f, 9, 9, 9, 9);
            }
            if (bleeding) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, sparkTexture("bleeding_icon"), x + 102, cursorY, 0.0f, 0.0f, 9, 9, 9, 9);
                context.drawText(tr, statusText, x + 113, cursorY + 1, statusColor, true);
            } else {
                context.drawText(tr, statusText, x + 104, cursorY + 1, statusColor, true);
            }
            cursorY += 13;
        }

        // 3. Ability Cooldown / Toggle Pills with live draining progress bars
        for (List<PillData> row : pillRows) {
            int rowW = 0;
            for (int i = 0; i < row.size(); i++) {
                rowW += row.get(i).width() + (i > 0 ? 3 : 0);
            }
            int cursorX = alignX(cfg, screenW, rowW, right, center);
            for (PillData pill : row) {
                context.fill(cursorX, cursorY, cursorX + pill.width(), cursorY + 11, 0xAA14141C);
                if (pill.cooldownRatio() > 0.0f) {
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

        // 4. PvP Combat Timer Badge (always visible when tagged so players don't accidentally combat-log)
        if (showCombat) {
            String combatLabel = "COMBAT " + CooldownSystem.formatSeconds(snap.combatTicks()) + " — DO NOT LOG OUT";
            int cw = tr.getWidth(combatLabel) + 8;
            int cx = alignX(cfg, screenW, cw, right, center);
            float ratio = Math.max(0.0f, Math.min(1.0f,
                    (float) snap.combatTicks() / (float) Math.max(snap.combatTicks(), Math.max(1, snap.maxCombatTicks()))));
            int fillW = Math.max(2, Math.round(cw * ratio));
            context.fill(cx, cursorY, cx + cw, cursorY + 11, 0xCC1E0A0E);
            context.fill(cx, cursorY, cx + fillW, cursorY + 10, 0x66EE2233);
            context.fill(cx, cursorY + 10, cx + fillW, cursorY + 11, 0xFFFF3344);
            context.drawText(tr, combatLabel, cx + 4, cursorY + 2, 0xFFFF7788, true);
            cursorY += 14;
        }

        // 5. Urgent HUD Warning
        if (showWarning) {
            Text warning = Text.literal(ClientState.hudWarningText).formatted(Formatting.RED, Formatting.BOLD);
            int w = tr.getWidth(warning);
            int x = alignX(cfg, screenW, w, right, center);
            context.drawText(tr, warning, x, cursorY + 1, 0xFFFF2222, true);
        }
    }

    private static int alignX(HudConfig cfg, int screenW, int elemW, boolean right, boolean center) {
        if (center) return Math.max(4, (screenW - elemW) / 2);
        if (right) return Math.max(4, screenW - cfg.offsetX - elemW);
        return cfg.offsetX;
    }
}
