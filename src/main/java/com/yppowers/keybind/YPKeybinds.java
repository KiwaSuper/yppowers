package com.yppowers.keybind;

import com.yppowers.client.YPClientNetworking;
import com.yppowers.client.ClientPowerData;
import com.yppowers.config.YPConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class YPKeybinds {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(Identifier.of("yppowers", "powers"));
    private static KeyBinding KEY_PRIMARY;
    private static KeyBinding KEY_SECONDARY;
    private static KeyBinding KEY_TERTIARY;
    private static KeyBinding KEY_QUATERNARY;
    private static KeyBinding KEY_ALTERNATE;
    private static KeyBinding KEY_ICE_EMPEROR_FORM;
    private static KeyBinding KEY_BOOTS_FEATHER_FALLING;
    private static KeyBinding KEY_ICE_HEAT_BLAST;
    private static long lastJumpPressMs;

    public static void register() {
        KEY_PRIMARY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.primary",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));
        KEY_SECONDARY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.secondary",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));
        KEY_TERTIARY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.tertiary",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));
        KEY_QUATERNARY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.quaternary",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));
        KEY_ALTERNATE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.alternate",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                CATEGORY
        ));
        KEY_ICE_EMPEROR_FORM = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.ice_emperor_form",
                InputUtil.Type.KEYSYM,
                YPConfig.getInstance().ice_emperor_form_switch_keycode,
                CATEGORY
        ));
        KEY_BOOTS_FEATHER_FALLING = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.boots_feather_falling",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
        KEY_ICE_HEAT_BLAST = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yppowers.ice_heat_blast",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                CATEGORY
        ));
    }

    public static void tick() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        boolean canActivate = client.player != null && client.world != null && client.currentScreen == null;
        if (!canActivate) {
            lastJumpPressMs = 0L;
            // Consume presses while menus/chat are open so they do not fire unexpectedly
            // when the player closes the screen.
            drain(KEY_PRIMARY);
            drain(KEY_SECONDARY);
            drain(KEY_TERTIARY);
            drain(KEY_QUATERNARY);
            drain(KEY_ALTERNATE);
            drain(KEY_ICE_EMPEROR_FORM);
            drain(KEY_BOOTS_FEATHER_FALLING);
            drain(KEY_ICE_HEAT_BLAST);
            while (client.options.jumpKey.wasPressed()) { /* discard stale jump input */ }
            return;
        }

        // Alternate activation for Boots of the Sky: double-tapping jump sends a guarded
        // request; the server only accepts it when this player actually owns that power.
        while (client.options.jumpKey.wasPressed()) {
            long now = System.currentTimeMillis();
            if (lastJumpPressMs != 0L && now - lastJumpPressMs <= 350L) {
                YPClientNetworking.sendKeybindPacket(5);
                lastJumpPressMs = 0L;
            } else {
                lastJumpPressMs = now;
            }
        }
        while (KEY_PRIMARY.wasPressed()) YPClientNetworking.sendKeybindPacket(0);
        while (KEY_SECONDARY.wasPressed()) YPClientNetworking.sendKeybindPacket(1);
        while (KEY_TERTIARY.wasPressed()) {
            if (ClientPowerData.hasShadowArcherPower) {
                // The morph menu is client UI: open immediately and report GUI initialization
                // failures rather than silently flashing closed.
                YPClientNetworking.openMorphMenu();
            } else {
                YPClientNetworking.sendKeybindPacket(2);
            }
        }
        while (KEY_QUATERNARY.wasPressed()) YPClientNetworking.sendKeybindPacket(3);
        while (KEY_ALTERNATE.wasPressed()) YPClientNetworking.sendKeybindPacket(4);
        while (KEY_ICE_EMPEROR_FORM.wasPressed()) YPClientNetworking.sendKeybindPacket(6);
        while (KEY_BOOTS_FEATHER_FALLING.wasPressed()) YPClientNetworking.sendKeybindPacket(7);
        while (KEY_ICE_HEAT_BLAST.wasPressed()) YPClientNetworking.sendKeybindPacket(9);
    }

    private static void drain(KeyBinding keyBinding) {
        if (keyBinding == null) return;
        while (keyBinding.wasPressed()) { /* discard stale input */ }
    }
}
