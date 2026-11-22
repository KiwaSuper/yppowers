package com.yppowers.core;

import java.util.Locale;

/** Canonical identifiers, display names, and HUD accent colors for all 17 YP Powers. */
public enum PowerId {
    DIVINITY("divinity", "Divinity Shield", 0xFFE8D578),
    GOAT("goat", "The GOAT", 0xFFFFD046),
    TIME_WARPER("time_warper", "Time Warper", 0xFFB57BFF),
    GAMBIT("gambit", "Teleport Gambit", 0xFF49E6D8),
    PUNCH("punch", "Serious Punch", 0xFFFF6E40),
    POSEIDON("poseidon", "Poseidon", 0xFF33B5FF),
    SHADOW_ARCHER("shadow_archer", "Shadow Archer", 0xFF9C7CE8),
    BOOTS_SKY("boots_sky", "Boots of the Sky", 0xFF8BE9FD),
    DELETION("deletion", "Stick of Deletion", 0xFFFF5252),
    FIRE_GOD("fire_god", "Fire God", 0xFFFF7A1A),
    ICE_EMPEROR("ice_emperor", "Ice Emperor", 0xFF66E0FF),
    THOR("thor", "Thor", 0xFFFAD84B),
    FALLOUT_WARRIOR("fallout_warrior", "Fallout Warrior", 0xFF84FF4D),
    SPIDEY("spidey", "Spidey", 0xFFFF3B5C),
    SPIRIT_CHANNELER("spirit_channeler", "Spirit Channeler", 0xFF6CF0C2),
    ILLUSIONIST("illusionist", "Illusionist", 0xFFD06BFF),
    KITSUNE("kitsune", "Kitsune", 0xFF47C5FF);

    private final String id;
    private final String displayName;
    private final int accentColor;

    PowerId(String id, String displayName, int accentColor) {
        this.id = id;
        this.displayName = displayName;
        this.accentColor = accentColor;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** ARGB accent color used for HUD badges and styling. */
    public int accentColor() {
        return accentColor;
    }

    public static PowerId fromId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (PowerId power : values()) {
            if (power.id.equals(normalized)) return power;
        }
        return null;
    }
}
