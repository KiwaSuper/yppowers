package com.yppowers.item;

import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;

public final class GauntletOfDarknessItem extends Item {
   public GauntletOfDarknessItem(Settings settings) {
      super(settings);
   }

   @Override
   public boolean canBeNested() {
      return false;
   }
}
