package com.yppowers.power;

public enum PowerType {
   DIVINITY("divinity", "Divinity Shield", 0xFFFFD54F),
   GOAT("goat", "The GOAT", 0xFFFFEB3B),
   TIME_WARPER("time_warper", "Time Warper", 0xFFB388FF),
   GAMBIT("gambit", "Teleport Gambit", 0xFF7C4DFF),
   PUNCH("punch", "Serious Punch", 0xFFFF5252),
   POSEIDON("poseidon", "Poseidon", 0xFF29B6F6),
   SHADOW_ARCHER("shadow_archer", "Shadow Archer", 0xFF9E9E9E),
   BOOTS_SKY("boots_sky", "Boots of the Sky", 0xFF80D8FF),
   DELETION("deletion", "Stick of Deletion", 0xFFFF80AB),
   FIRE_GOD("fire_god", "Fire God", 0xFFFF6D00),
   ICE_EMPEROR("ice_emperor", "Ice Emperor", 0xFF84FFFF),
   THOR("thor", "Thor", 0xFF40C4FF),
   FALLOUT_WARRIOR("fallout_warrior", "Fallout Warrior", 0xFF76FF03),
   SPIDEY("spidey", "Spidey", 0xFFFF1744),
   SPIRIT_CHANNELER("spirit_channeler", "Spirit Channeler", 0xFF64FFDA),
   ILLUSIONIST("illusionist", "Illusionist", 0xFFE040FB),
   KITSUNE("kitsune", "Kitsune", 0xFFFF9100);

   private final String id;
   private final String displayName;
   private final int accentColor;

   private PowerType(String id, String displayName, int accentColor) {
      this.id = id;
      this.displayName = displayName;
      this.accentColor = accentColor;
   }

   public String getId() {
      return this.id;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public int getAccentColor() {
      return this.accentColor;
   }

   public static PowerType fromId(String id) {
      if (id == null) {
         return null;
      }
      for (PowerType type : values()) {
         if (type.id.equals(id)) {
            return type;
         }
      }
      return null;
   }
}
