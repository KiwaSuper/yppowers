package com.yppowers.power.shadowarcher;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public enum ArrowType {
   SPLIT_SHOT("Split Shot", Items.SPECTRAL_ARROW, "Splits into a small spread of arrows."),
   HOMING("Homing Missile", Items.ENDER_EYE, "Tracks toward a target while in flight."),
   TNT("TNT Arrow", Items.TNT, "Creates a bounded explosion on impact."),
   LIGHTNING("Lightning Arrow", Items.LIGHTNING_ROD, "Calls cosmetic lightning at the impact point."),
   ICE_PRISON("Ice Prison", Items.PACKED_ICE, "Slows and blinds nearby enemies briefly."),
   RAILSHOT("Railshot", Items.NETHER_STAR, "Charge the bow for a fast piercing shot."),
   PINNING("Pinning Arrow", Items.LEAD, "Pins an enemy when it hits near a solid wall."),
   PHASE("Phase Arrow", Items.OBSIDIAN, "Passes through blocks while traveling."),
   TRACER("Tracer Arrow", Items.COMPASS, "Marks a struck enemy with glowing."),
   ECLIPSE("Eclipse Arrow", Items.BLACK_DYE, "Darkens nearby enemies and conceals you.");

   private final String displayName;
   private final Item craftItem;
   private final String description;

   private ArrowType(String displayName, Item craftItem, String description) {
      this.displayName = displayName;
      this.craftItem = craftItem;
      this.description = description;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public Item getCraftItem() {
      return this.craftItem;
   }

   public String getDescription() {
      return this.description;
   }
}
