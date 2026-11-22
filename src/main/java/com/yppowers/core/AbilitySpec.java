package com.yppowers.core;

/**
 * Declarative metadata for an ability slot exposed on the client HUD and energy dispatcher.
 *
 * @param slot        The input slot bound to this ability.
 * @param label       Short label displayed in HUD / feedback (e.g. "Dome", "Rewind").
 * @param cooldownKey Cooldown key in {@code CooldownSystem}, or empty if no cooldown bar.
 * @param isToggle    Whether this slot represents an on/off toggle rather than a one-shot cast.
 */
public record AbilitySpec(
        AbilitySlot slot,
        String label,
        String cooldownKey,
        boolean isToggle
) {
    public static AbilitySpec active(AbilitySlot slot, String label, String cooldownKey) {
        return new AbilitySpec(slot, label, cooldownKey, false);
    }

    public static AbilitySpec toggle(AbilitySlot slot, String label) {
        return new AbilitySpec(slot, label, "", true);
    }

    public static AbilitySpec utility(AbilitySlot slot, String label) {
        return new AbilitySpec(slot, label, "", false);
    }
}
