package com.yppowers.client;

import com.yppowers.power.impl.ShadowArcherMorphRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable selector for Shadow Archer's kill-unlocked mob-form catalogue. */
public final class ShadowArcherMorphScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Shadow Archer Menu");
    private static final int VISIBLE_SUGGESTIONS = 5;

    private TextFieldWidget mobName;
    private final List<ButtonWidget> suggestionButtons = new ArrayList<>();
    private List<String> filteredSuggestions = List.of();
    private int pageOffset;
    private ButtonWidget previousPage;
    private ButtonWidget nextPage;
    private boolean renderFailureHandled;

    public ShadowArcherMorphScreen() {
        super(Text.literal("Shadow Archer — Morph"));
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        suggestionButtons.clear();

        mobName = new TextFieldWidget(textRenderer, centerX - 135, centerY - 76, 270, 20, Text.literal("Search mob name or ID"));
        mobName.setMaxLength(128);
        mobName.setPlaceholder(Text.literal("e.g. ender_dragon, minecraft:spider"));
        addDrawableChild(mobName);

        for (int i = 0; i < VISIBLE_SUGGESTIONS; i++) {
            final int slot = i;
            ButtonWidget button = ButtonWidget.builder(Text.literal("—"), ignored -> chooseSuggestion(slot))
                    .dimensions(centerX - 130, centerY - 48 + i * 21, 260, 19).build();
            suggestionButtons.add(button);
            addDrawableChild(button);
        }
        previousPage = addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), ignored -> changePage(-1))
                .dimensions(centerX - 130, centerY + 59, 80, 18).build());
        nextPage = addDrawableChild(ButtonWidget.builder(Text.literal("Next"), ignored -> changePage(1))
                .dimensions(centerX + 50, centerY + 59, 80, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Morph (G/H/R abilities)"), ignored -> submit())
                .dimensions(centerX - 130, centerY + 82, 145, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Human / Cancel"), ignored -> {
            YPClientNetworking.sendMorphRequest("human");
            close();
        }).dimensions(centerX + 20, centerY + 82, 110, 20).build());

        mobName.setChangedListener(ignored -> {
            pageOffset = 0;
            refreshSuggestions();
        });
        refreshSuggestions();
    }

    @Override
    protected void setInitialFocus() {
        if (mobName != null) setInitialFocus(mobName);
    }

    private void refreshSuggestions() {
        String query = mobName == null ? "" : mobName.getText().trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        filteredSuggestions = ShadowArcherMorphRegistry.getVanillaFormIds().stream()
                .filter(id -> query.isEmpty() || id.contains(query)
                        || ShadowArcherMorphRegistry.forId(id).displayName().toLowerCase(Locale.ROOT).contains(query.replace('_', ' ')))
                .toList();
        int maxOffset = Math.max(0, ((filteredSuggestions.size() - 1) / VISIBLE_SUGGESTIONS) * VISIBLE_SUGGESTIONS);
        pageOffset = Math.min(pageOffset, maxOffset);
        for (int i = 0; i < suggestionButtons.size(); i++) {
            int index = pageOffset + i;
            ButtonWidget button = suggestionButtons.get(i);
            if (index < filteredSuggestions.size()) {
                String id = filteredSuggestions.get(index);
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
        if (previousPage != null) previousPage.active = pageOffset > 0;
        if (nextPage != null) nextPage.active = pageOffset + VISIBLE_SUGGESTIONS < filteredSuggestions.size();
    }

    /** Called after the server synchronizes new kill-unlocked forms. */
    public void refreshUnlockStatus() {
        refreshSuggestions();
    }

    private void chooseSuggestion(int slot) {
        int index = pageOffset + slot;
        if (mobName == null || index < 0 || index >= filteredSuggestions.size()) return;
        mobName.setText(filteredSuggestions.get(index));
        mobName.setCursorToEnd(false);
        mobName.setFocused(true);
    }

    private void changePage(int direction) {
        int next = pageOffset + direction * VISIBLE_SUGGESTIONS;
        int maxOffset = Math.max(0, ((filteredSuggestions.size() - 1) / VISIBLE_SUGGESTIONS) * VISIBLE_SUGGESTIONS);
        pageOffset = Math.max(0, Math.min(next, maxOffset));
        refreshSuggestions();
    }

    private void submit() {
        if (mobName == null) return;
        String value = mobName.getText().trim();
        if (!value.isEmpty()) {
            YPClientNetworking.sendMorphRequest(value);
            close();
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (mobName != null && mobName.isFocused() && input.isEnter()) {
            submit();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (renderFailureHandled) {
            renderRenderFailure(context);
            return;
        }
        try {
            // Avoid Screen.renderBackground here: on 1.21.11 it applies the frame blur,
            // and some launch/render pipelines have already blurred this frame. Calling
            // it again throws "Can only blur once per frame" before the widgets render.
            // Use a self-contained dark backdrop and outlined panel instead.
            int centerX = width / 2;
            int centerY = height / 2;
            context.fill(0, 0, width, height, 0xD0101824);
            int panelLeft = Math.max(8, centerX - 166);
            int panelRight = Math.min(width - 8, centerX + 166);
            int panelTop = Math.max(8, centerY - 128);
            int panelBottom = Math.min(height - 8, centerY + 145);
            context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE81A2738);
            context.fill(panelLeft, panelTop, panelRight, panelTop + 1, 0xFF65C7FF);
            context.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, 0xFF65C7FF);
            context.fill(panelLeft, panelTop, panelLeft + 1, panelBottom, 0xFF65C7FF);
            context.fill(panelRight - 1, panelTop, panelRight, panelBottom, 0xFF65C7FF);
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(textRenderer, title, centerX, centerY - 105, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Search or browse vanilla 1.21.11 mob forms. Each form must be unlocked by defeating it."),
                    centerX, centerY - 91, 0xB8DFFF);
            int page = filteredSuggestions.isEmpty() ? 0 : pageOffset / VISIBLE_SUGGESTIONS + 1;
            int pages = Math.max(1, (filteredSuggestions.size() + VISIBLE_SUGGESTIONS - 1) / VISIBLE_SUGGESTIONS);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(filteredSuggestions.size() + " matches  •  page " + page + "/" + pages),
                    centerX, centerY + 80, 0xAAAAAA);

            ShadowArcherMorphRegistry.Profile profile = mobName == null ? null
                    : ShadowArcherMorphRegistry.findProfileForInput(mobName.getText());
            if (profile != null) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(profile.abilitySummary()),
                        centerX, centerY + 108, 0xFFFFFF);
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(profile.traitSummary()),
                        centerX, centerY + 121, 0x9DE2FF);
            } else {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("G / H / R activate the selected form's abilities."), centerX, centerY + 108, 0xB8DFFF);
            }
        } catch (RuntimeException | LinkageError exception) {
            if (!renderFailureHandled) {
                renderFailureHandled = true;
                LOGGER.error("Shadow Archer morph menu failed while rendering; keeping a diagnostic screen open", exception);
            }
            // Keep the screen visible instead of making it flash closed. This also tells the
            // player where to find the useful stack trace if an unexpected client issue remains.
            renderRenderFailure(context);
        }
    }

    private void renderRenderFailure(DrawContext context) {
        context.fill(0, 0, width, height, 0xE0101824);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Shadow Archer Menu"), width / 2, height / 2 - 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("The menu encountered a rendering error."), width / 2, height / 2 + 2, 0xFFB5B5);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("See latest.log for the stack trace; press Esc to close."), width / 2, height / 2 + 18, 0xB8DFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
