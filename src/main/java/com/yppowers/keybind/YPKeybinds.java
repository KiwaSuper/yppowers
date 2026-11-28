package com.yppowers.keybind;

import com.yppowers.client.ClientPowerData;
import com.yppowers.client.PoseidonConfirmScreen;
import com.yppowers.client.YPClientNetworking;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilityKey;
import com.yppowers.item.YPItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class YPKeybinds {
   private static final Category CATEGORY = Category.create(Identifier.of("yppowers", "powers"));
   private static KeyBinding KEY_PRIMARY;
   private static KeyBinding KEY_SECONDARY;
   private static KeyBinding KEY_TERTIARY;
   private static KeyBinding KEY_QUATERNARY;
   private static KeyBinding KEY_ALTERNATE;
   private static KeyBinding KEY_ICE_EMPEROR_FORM;
   private static KeyBinding KEY_BOOTS_FEATHER_FALLING;
   private static KeyBinding KEY_ICE_HEAT_BLAST;
   private static KeyBinding KEY_KITSUNE_CARRY;
   private static KeyBinding KEY_DELETION_SWAP;
   private static KeyBinding KEY_SHADOW_ARCHER_SPECIAL_ARROWS;
   private static KeyBinding KEY_SHADOW_ARCHER_ARROW_SWAP;
   private static KeyBinding KEY_SHADOW_ARCHER_BOW_MODE;
   private static KeyBinding KEY_GRIMOIRE_TOGGLE;
   private static KeyBinding KEY_AWAKENING;
   private static KeyBinding KEY_POWER_MENU;
   private static KeyBinding KEY_CHUNK_DUPING_DOMAIN;
   private static KeyBinding KEY_PORTABLE_CHUNK_SWAP;
   private static KeyBinding KEY_CONFIRM_TERRAIN_ACTION;
   private static KeyBinding KEY_FIRE_GOD_WITHER_SKULL;
   private static long lastJumpPressMs;
   private static boolean fireGodBeamWasHeld;
   private static boolean grimoireBeamWasHeld;
   private static boolean timeWarperBlackHoleWasHeld;
   private static int timeWarperBlackHoleHeartbeatTicks;

   public static void register() {
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         fireGodBeamWasHeld = false;
         grimoireBeamWasHeld = false;
         timeWarperBlackHoleWasHeld = false;
         timeWarperBlackHoleHeartbeatTicks = 0;
      });
      KEY_PRIMARY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.primary", Type.KEYSYM, 71, CATEGORY));
      KEY_SECONDARY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.secondary", Type.KEYSYM, 72, CATEGORY));
      KEY_TERTIARY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.tertiary", Type.KEYSYM, 86, CATEGORY));
      KEY_QUATERNARY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.quaternary", Type.KEYSYM, 66, CATEGORY));
      KEY_ALTERNATE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.alternate", Type.KEYSYM, 82, CATEGORY));
      KEY_ICE_EMPEROR_FORM = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.yppowers.ice_emperor_form", Type.KEYSYM, YPConfig.getInstance().ice_emperor_form_switch_keycode, CATEGORY)
      );
      KEY_BOOTS_FEATHER_FALLING = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.boots_feather_falling", Type.KEYSYM, 78, CATEGORY));
      KEY_ICE_HEAT_BLAST = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.ice_heat_blast", Type.KEYSYM, 76, CATEGORY));
      KEY_KITSUNE_CARRY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.kitsune_carry", Type.KEYSYM, 67, CATEGORY));
      KEY_DELETION_SWAP = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.deletion_swap", Type.KEYSYM, 88, CATEGORY));
      KEY_SHADOW_ARCHER_SPECIAL_ARROWS = KeyBindingHelper.registerKeyBinding(
         new KeyBinding("key.yppowers.shadow_archer_special_arrows", Type.KEYSYM, 74, CATEGORY)
      );
      KEY_SHADOW_ARCHER_ARROW_SWAP = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.shadow_archer_arrow_swap", Type.KEYSYM, 89, CATEGORY));
      KEY_SHADOW_ARCHER_BOW_MODE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.shadow_archer_bow_mode", Type.KEYSYM, 79, CATEGORY));
      KEY_GRIMOIRE_TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.grimoire_toggle", Type.KEYSYM, 74, CATEGORY));
      KEY_AWAKENING = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.awakening", Type.KEYSYM, 80, CATEGORY));
      KEY_POWER_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.power_menu", Type.KEYSYM, 85, CATEGORY));
      KEY_CHUNK_DUPING_DOMAIN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.chunk_duping_domain", Type.KEYSYM, 77, CATEGORY));
      KEY_PORTABLE_CHUNK_SWAP = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.portable_chunk_swap", Type.KEYSYM, 90, CATEGORY));
      KEY_CONFIRM_TERRAIN_ACTION = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.confirm_terrain_action", Type.KEYSYM, 295, CATEGORY));
      KEY_FIRE_GOD_WITHER_SKULL = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.yppowers.fire_god_wither_skull", Type.KEYSYM, 73, CATEGORY));
   }

   public static void tick() {
      MinecraftClient client = MinecraftClient.getInstance();
      boolean canActivate = client.player != null && client.world != null && client.currentScreen == null;
      boolean shouldHoldFireGodBeam = canActivate && ClientPowerData.hasFireGodPower && !ClientPowerData.grimoireSkillsetEnabled && KEY_QUATERNARY.isPressed();
      if (shouldHoldFireGodBeam != fireGodBeamWasHeld && YPClientNetworking.sendFireGodBeamState(shouldHoldFireGodBeam)) {
         fireGodBeamWasHeld = shouldHoldFireGodBeam;
      }

      boolean shouldHoldGrimoireBeam = canActivate && ClientPowerData.grimoireSkillsetEnabled && clientCarriesGrimoire(client) && KEY_PRIMARY.isPressed();
      if (shouldHoldGrimoireBeam != grimoireBeamWasHeld && YPClientNetworking.sendGrimoireBeamState(shouldHoldGrimoireBeam)) {
         grimoireBeamWasHeld = shouldHoldGrimoireBeam;
      }

      boolean shouldHoldTimeWarperBlackHole = canActivate && KEY_TERTIARY.isPressed();
      if (shouldHoldTimeWarperBlackHole) {
         timeWarperBlackHoleHeartbeatTicks++;
         if ((!timeWarperBlackHoleWasHeld || timeWarperBlackHoleHeartbeatTicks >= 10) && YPClientNetworking.sendTimeWarperBlackHoleHoldState(true)) {
            timeWarperBlackHoleWasHeld = true;
            timeWarperBlackHoleHeartbeatTicks = 0;
         }
      } else {
         timeWarperBlackHoleHeartbeatTicks = 0;
         if (timeWarperBlackHoleWasHeld && YPClientNetworking.sendTimeWarperBlackHoleHoldState(false)) {
            timeWarperBlackHoleWasHeld = false;
         }
      }

      if (!canActivate) {
         lastJumpPressMs = 0L;
         drain(KEY_PRIMARY);
         drain(KEY_SECONDARY);
         drain(KEY_TERTIARY);
         drain(KEY_QUATERNARY);
         drain(KEY_ALTERNATE);
         drain(KEY_ICE_EMPEROR_FORM);
         drain(KEY_BOOTS_FEATHER_FALLING);
         drain(KEY_ICE_HEAT_BLAST);
         drain(KEY_KITSUNE_CARRY);
         drain(KEY_DELETION_SWAP);
         drain(KEY_SHADOW_ARCHER_SPECIAL_ARROWS);
         drain(KEY_SHADOW_ARCHER_ARROW_SWAP);
         drain(KEY_SHADOW_ARCHER_BOW_MODE);
         drain(KEY_GRIMOIRE_TOGGLE);
         drain(KEY_AWAKENING);
         drain(KEY_POWER_MENU);
         drain(KEY_CHUNK_DUPING_DOMAIN);
         drain(KEY_PORTABLE_CHUNK_SWAP);
         drain(KEY_FIRE_GOD_WITHER_SKULL);
         if (client.currentScreen instanceof PoseidonConfirmScreen) {
            while (KEY_CONFIRM_TERRAIN_ACTION.wasPressed()) {
               YPClientNetworking.confirmPoseidonSink();
               client.setScreen(null);
            }
         } else {
            drain(KEY_CONFIRM_TERRAIN_ACTION);
         }

         while (client.options.jumpKey.wasPressed()) {
         }

         if (!canActivate) {
            fireGodBeamWasHeld = false;
         }

         if (!canActivate) {
            grimoireBeamWasHeld = false;
         }

         if (!canActivate) {
            timeWarperBlackHoleWasHeld = false;
         }
      } else {
         while (client.options.jumpKey.wasPressed()) {
            long now = System.currentTimeMillis();
            if (lastJumpPressMs != 0L && now - lastJumpPressMs <= 350L) {
               YPClientNetworking.sendKeybindPacket(5);
               lastJumpPressMs = 0L;
            } else {
               lastJumpPressMs = now;
            }
         }

         while (KEY_PRIMARY.wasPressed()) {
            if (!ClientPowerData.grimoireSkillsetEnabled) {
               YPClientNetworking.sendKeybindPacket(0);
            }
         }

         while (KEY_SECONDARY.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(1);
         }

         while (KEY_TERTIARY.wasPressed()) {
            if (ClientPowerData.hasShadowArcherPower) {
               YPClientNetworking.openMorphMenu();
            } else {
               YPClientNetworking.sendKeybindPacket(2);
            }
         }

         while (KEY_QUATERNARY.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(3);
         }

         while (KEY_ALTERNATE.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(4);
         }

         while (KEY_ICE_EMPEROR_FORM.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(6);
         }

         while (KEY_BOOTS_FEATHER_FALLING.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(7);
         }

         while (KEY_ICE_HEAT_BLAST.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(9);
         }

         while (KEY_KITSUNE_CARRY.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(10);
         }

         while (KEY_DELETION_SWAP.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(8);
         }

         boolean grimoireTogglePressed = consumePresses(KEY_GRIMOIRE_TOGGLE);
         boolean shadowArrowTogglePressed = consumePresses(KEY_SHADOW_ARCHER_SPECIAL_ARROWS);
         boolean hasGrimoire = clientCarriesGrimoire(client);
         if (grimoireTogglePressed && hasGrimoire) {
            YPClientNetworking.sendKeybindPacket(15);
         } else if (shadowArrowTogglePressed) {
            YPClientNetworking.sendKeybindPacket(11);
         }

         while (KEY_SHADOW_ARCHER_ARROW_SWAP.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(12);
         }

         while (KEY_SHADOW_ARCHER_BOW_MODE.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(13);
         }

         while (KEY_AWAKENING.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(16);
         }

         while (KEY_POWER_MENU.wasPressed()) {
            YPClientNetworking.openPowerMenu();
         }

         while (KEY_CHUNK_DUPING_DOMAIN.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(17);
         }

         while (KEY_PORTABLE_CHUNK_SWAP.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(18);
         }

         while (KEY_CONFIRM_TERRAIN_ACTION.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(19);
         }

         while (KEY_FIRE_GOD_WITHER_SKULL.wasPressed()) {
            YPClientNetworking.sendKeybindPacket(20);
         }
      }
   }

   private static boolean consumePresses(KeyBinding keyBinding) {
      boolean pressed = false;

      while (keyBinding.wasPressed()) {
         pressed = true;
      }

      return pressed;
   }

   private static boolean clientCarriesGrimoire(MinecraftClient client) {
      if (client.player == null) {
         return false;
      } else {
         for (int slot = 0; slot < client.player.getInventory().size(); slot++) {
            if (client.player.getInventory().getStack(slot).isOf(YPItems.GRIMOIRE_OF_LIGHT)) {
               return true;
            }
         }

         return false;
      }
   }

   private static void drain(KeyBinding keyBinding) {
      if (keyBinding != null) {
         while (keyBinding.wasPressed()) {
         }
      }
   }

   /** Returns the player's actual bound key label from Minecraft Controls for dynamic HUD & Power Guide rendering. */
   public static String getBoundKeyLabel(AbilityKey key) {
      if (key == null) {
         return "?";
      }
      KeyBinding binding = switch (key) {
         case PRIMARY -> KEY_PRIMARY;
         case SECONDARY -> KEY_SECONDARY;
         case TERTIARY -> KEY_TERTIARY;
         case QUATERNARY -> KEY_QUATERNARY;
         case ALTERNATE -> KEY_ALTERNATE;
         case ICE_EMPEROR_FORM -> KEY_ICE_EMPEROR_FORM;
         case BOOTS_FEATHER_FALLING -> KEY_BOOTS_FEATHER_FALLING;
         case DELETION_SWAP -> KEY_DELETION_SWAP;
         case ICE_HEAT_BLAST -> KEY_ICE_HEAT_BLAST;
         case KITSUNE_CARRY -> KEY_KITSUNE_CARRY;
         case SHADOW_ARCHER_SPECIAL_ARROWS -> KEY_SHADOW_ARCHER_SPECIAL_ARROWS;
         case SHADOW_ARCHER_ARROW_SWAP -> KEY_SHADOW_ARCHER_ARROW_SWAP;
         case SHADOW_ARCHER_BOW_MODE -> KEY_SHADOW_ARCHER_BOW_MODE;
         case GRIMOIRE_TOGGLE -> KEY_GRIMOIRE_TOGGLE;
         case AWAKENING -> KEY_AWAKENING;
         case CHUNK_DUPING_DOMAIN -> KEY_CHUNK_DUPING_DOMAIN;
         case PORTABLE_CHUNK_SWAP -> KEY_PORTABLE_CHUNK_SWAP;
         case CONFIRM_TERRAIN_ACTION -> KEY_CONFIRM_TERRAIN_ACTION;
         case WITHER_ASCENDANCE_SKULL -> KEY_FIRE_GOD_WITHER_SKULL;
         default -> null;
      };
      if (binding == null) {
         return key.defaultLabel();
      }
      String localized = binding.getBoundKeyLocalizedText().getString();
      if (localized == null || localized.isBlank()) {
         return key.defaultLabel();
      }
      return localized.length() > 8 ? localized.substring(0, 8) : localized;
   }
}
