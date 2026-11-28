package com.yppowers.item;
import com.yppowers.artifact.GauntletOfDarknessItem;

import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class YPItems {
   public static final Identifier WEB_SLINGERS_ID = Identifier.of("yppowers", "web_slingers");
   public static final Item WEB_SLINGERS = Registry.register(
      Registries.ITEM, WEB_SLINGERS_ID, new Item(new Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, WEB_SLINGERS_ID)).maxCount(1))
   );
   public static final Identifier GAUNTLET_OF_DARKNESS_ID = Identifier.of("yppowers", "gauntlet_of_darkness");
   public static final Item GAUNTLET_OF_DARKNESS = Registry.register(
      Registries.ITEM,
      GAUNTLET_OF_DARKNESS_ID,
      new GauntletOfDarknessItem(new Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, GAUNTLET_OF_DARKNESS_ID)).maxCount(1))
   );
   public static final Identifier GRIMOIRE_OF_LIGHT_ID = Identifier.of("yppowers", "grimoire_of_light");
   public static final Item GRIMOIRE_OF_LIGHT = Registry.register(
      Registries.ITEM,
      GRIMOIRE_OF_LIGHT_ID,
      new Item(new Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, GRIMOIRE_OF_LIGHT_ID)).maxCount(1)) {
         @Override
         public boolean canBeNested() {
            return false;
         }
      }
   );

   private YPItems() {
   }

   public static void initialize() {
   }
}
