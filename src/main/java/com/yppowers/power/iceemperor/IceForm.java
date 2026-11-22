package com.yppowers.power.iceemperor;

import java.util.Locale;

/** Ice Emperor selectable combat stances. */
public enum IceForm {
    ARMOURED("Armoured"),
    STEALTH("Stealth"),
    ASSASSIN("Assassin");

    private final String displayName;

    IceForm(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static IceForm parse(String raw) {
        if (raw == null || raw.isBlank()) return ARMOURED;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ARMOURED;
        }
    }
}
