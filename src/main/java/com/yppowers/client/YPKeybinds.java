package com.yppowers.client;

import com.yppowers.YPPowersMod;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.PowerId;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client keybindings cleanly mapped to typed {@link AbilitySlot} values with dynamic label lookup. */
@Environment(EnvType.CLIENT)
public final class YPKeybinds {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of(YPPowersMod.MOD_ID, "powers"));
    private static KeyBinding KEY_PRIMARY;
    private static KeyBinding KEY_SECONDARY;
    private static KeyBinding KEY_TERTIARY;
    private static KeyBinding KEY_QUATERNARY;
    private static KeyBinding KEY_ALTERNATE;
    private static KeyBinding KEY_FORM_SWITCH;
    private static KeyBinding KEY_FEATHER_FALLING;
    private static KeyBinding KEY_HEAT_BLAST;
    private static KeyBinding KEY_GUIDE;
    private static long lastJumpPressMs;

    private YPKeybinds() {}

    public static void register() {
        KEY_PRIMARY = reg("key.yppowers.primary", GLFW.GLFW_KEY_G);
        KEY_SECONDARY = reg("key.yppowers.secondary", GLFW.GLFW_KEY_H);
        KEY_TERTIARY = reg("key.yppowers.tertiary", GLFW.GLFW_KEY_V);
        KEY_QUATERNARY = reg("key.yppowers.quaternary", GLFW.GLFW_KEY_B);
        KEY_ALTERNATE = reg("key.yppowers.alternate", GLFW.GLFW_KEY_R);
        KEY_FORM_SWITCH = reg("key.yppowers.ice_emperor_form", GLFW.GLFW_KEY_K);
        KEY_FEATHER_FALLING = reg("key.yppowers.boots_feather_falling", GLFW.GLFW_KEY_N);
        KEY_HEAT_BLAST = reg("key.yppowers.ice_heat_blast", GLFW.GLFW_KEY_L);
        KEY_GUIDE = reg("key.yppowers.guide", GLFW.GLFW_KEY_P);
    }

    private static KeyBinding reg(String translationKey, int glfwKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, InputUtil.Type.KEYSYM, glfwKey, CATEGORY));
    }

    /** Returns the player's actual bound key label for {@code slot}, shortened cleanly for HUD pills. */
    public static String getBoundKeyLabel(AbilitySlot slot) {
        if (slot == null) return "?";
        if (slot == AbilitySlot.DOUBLE_JUMP) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null && client.options.jumpKey != null) {
                return "2x" + compactKeyName(client.options.jumpKey.getBoundKeyLocalizedText().getString());
            }
            return "2xSpace";
        }
        KeyBinding kb = switch (slot) {
            case PRIMARY -> KEY_PRIMARY;
            case SECONDARY -> KEY_SECONDARY;
            case TERTIARY -> KEY_TERTIARY;
            case QUATERNARY -> KEY_QUATERNARY;
            case ALTERNATE -> KEY_ALTERNATE;
            case FORM_SWITCH -> KEY_FORM_SWITCH;
            case FEATHER_FALLING_TOGGLE -> KEY_FEATHER_FALLING;
            case HEAT_BLAST -> KEY_HEAT_BLAST;
            default -> null;
        };
        if (kb == null || kb.isUnbound()) return slot.defaultKeyLabel();
        return compactKeyName(kb.getBoundKeyLocalizedText().getString());
    }

    private static String compactKeyName(String raw) {
        if (raw == null || raw.isBlank()) return "?";
        String s = raw.trim();
        return switch (s) {
            case "Left Shift" -> "LShift";
            case "Right Shift" -> "RShift";
            case "Left Control" -> "LCtrl";
            case "Right Control" -> "RCtrl";
            case "Left Alt" -> "LAlt";
            case "Right Alt" -> "RAlt";
            case "Left Button" -> "LMB";
            case "Right Button" -> "RMB";
            case "Middle Button" -> "MMB";
            default -> s.startsWith("Button ") ? "MB" + s.substring("Button ".length()) : s;
        };
    }

    public static void tick() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean active = client.player != null && client.world != null && client.currentScreen == null;
        if (!active) {
            lastJumpPressMs = 0L;
            drain(KEY_PRIMARY);
            drain(KEY_SECONDARY);
            drain(KEY_TERTIARY);
            drain(KEY_QUATERNARY);
            drain(KEY_ALTERNATE);
            drain(KEY_FORM_SWITCH);
            drain(KEY_FEATHER_FALLING);
            drain(KEY_HEAT_BLAST);
            drain(KEY_GUIDE);
            while (client.options.jumpKey.wasPressed()) {}
            return;
        }

        while (KEY_GUIDE.wasPressed()) {
            YPClientNetworking.openGuideScreen();
            return;
        }

        while (client.options.jumpKey.wasPressed()) {
            long now = System.currentTimeMillis();
            if (lastJumpPressMs != 0L && now - lastJumpPressMs <= 350L) {
                if (ClientState.hasPower(PowerId.BOOTS_SKY)) {
                    YPClientNetworking.sendTrigger(AbilitySlot.DOUBLE_JUMP);
                }
                lastJumpPressMs = 0L;
            } else {
                lastJumpPressMs = now;
            }
        }

        while (KEY_PRIMARY.wasPressed()) YPClientNetworking.sendTrigger(AbilitySlot.PRIMARY);
        while (KEY_SECONDARY.wasPressed()) YPClientNetworking.sendTrigger(AbilitySlot.SECONDARY);
        while (KEY_TERTIARY.wasPressed()) {
            if (ClientState.hasPower(PowerId.SHADOW_ARCHER)) {
                YPClientNetworking.openMorphScreen();
            } else {
                YPClientNetworking.sendTrigger(AbilitySlot.TERTIARY);
            }
        }
        while (KEY_QUATERNARY.wasPressed()) YPClientNetworking.sendTrigger(AbilitySlot.QUATERNARY);
        while (KEY_ALTERNATE.wasPressed()) YPClientNetworking.sendTrigger(AbilitySlot.ALTERNATE);
        while (KEY_FORM_SWITCH.wasPressed()) YPClientNetworking.sendTrigger(AbilitySlot.FORM_SWITCH);
        while (KEY_FEATHER_FALLING.wasPressed()) YPClientNetworking.sendTrigger(AbilitySlot.FEATHER_FALLING_TOGGLE);
        while (KEY_HEAT_BLAST.wasPressed()) YPClientNetworking.sendTrigger(AbilitySlot.HEAT_BLAST);
    }

    private static void drain(KeyBinding key) {
        if (key != null) {
            while (key.wasPressed()) {}
        }
    }
}
