package com.yppowers.core;

/** Typed ability key slots matching the existing network wire IDs (0..20). */
public enum AbilityKey {
   PRIMARY(0, "G", "key.yppowers.primary"),
   SECONDARY(1, "H", "key.yppowers.secondary"),
   TERTIARY(2, "V", "key.yppowers.tertiary"),
   QUATERNARY(3, "B", "key.yppowers.quaternary"),
   ALTERNATE(4, "R", "key.yppowers.alternate"),
   DOUBLE_JUMP(5, "2xSpace", "key.jump"),
   ICE_EMPEROR_FORM(6, "K", "key.yppowers.ice_emperor_form"),
   BOOTS_FEATHER_FALLING(7, "N", "key.yppowers.boots_feather_falling"),
   DELETION_SWAP(8, "X", "key.yppowers.deletion_swap"),
   ICE_HEAT_BLAST(9, "L", "key.yppowers.ice_heat_blast"),
   KITSUNE_CARRY(10, "C", "key.yppowers.kitsune_carry"),
   SHADOW_ARCHER_SPECIAL_ARROWS(11, "J", "key.yppowers.shadow_archer_special_arrows"),
   SHADOW_ARCHER_ARROW_SWAP(12, "Y", "key.yppowers.shadow_archer_arrow_swap"),
   SHADOW_ARCHER_BOW_MODE(13, "O", "key.yppowers.shadow_archer_bow_mode"),
   GRIMOIRE_TOGGLE(15, "J", "key.yppowers.grimoire_toggle"),
   AWAKENING(16, "P", "key.yppowers.awakening"),
   CHUNK_DUPING_DOMAIN(17, "M", "key.yppowers.chunk_duping_domain"),
   PORTABLE_CHUNK_SWAP(18, "Z", "key.yppowers.portable_chunk_swap"),
   CONFIRM_TERRAIN_ACTION(19, "F6", "key.yppowers.confirm_terrain_action"),
   WITHER_ASCENDANCE_SKULL(20, "I", "key.yppowers.fire_god_wither_skull");

   private final int wireId;
   private final String defaultLabel;
   private final String translationKey;

   AbilityKey(int wireId, String defaultLabel, String translationKey) {
      this.wireId = wireId;
      this.defaultLabel = defaultLabel;
      this.translationKey = translationKey;
   }

   public int wireId() {
      return this.wireId;
   }

   public String defaultLabel() {
      return this.defaultLabel;
   }

   public String translationKey() {
      return this.translationKey;
   }

   public static AbilityKey fromWireId(int id) {
      for (AbilityKey key : values()) {
         if (key.wireId == id) {
            return key;
         }
      }
      return null;
   }
}
