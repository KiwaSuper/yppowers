package com.yppowers.core;

/** Immutable server-to-client snapshot of a single ability pill on the player HUD. */
public record HudPill(
   int keyWireId,
   String label,
   int cooldownTicks,
   int maxCooldownTicks,
   boolean isToggle,
   boolean toggleActive,
   boolean canAfford
) {
   public static HudPill ability(AbilityKey key, String label, int cooldownTicks, int maxCooldownTicks, boolean canAfford) {
      int cd = Math.max(0, cooldownTicks);
      int maxCd = Math.max(cd, Math.max(1, maxCooldownTicks));
      return new HudPill(key.wireId(), label, cd, maxCd, false, false, canAfford);
   }

   public static HudPill toggle(AbilityKey key, String label, boolean active) {
      return new HudPill(key.wireId(), label, 0, 1, true, active, true);
   }
}
