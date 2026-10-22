package com.yppowers.power;

public enum PowerType {
    DIVINITY("divinity", "Divinity Shield"),
    GOAT("goat", "The GOAT"),
    TIME_WARPER("time_warper", "Time Warper"),
    GAMBIT("gambit", "Teleport Gambit"),
    PUNCH("punch", "Serious Punch"),
    POSEIDON("poseidon", "Poseidon"),
    SHADOW_ARCHER("shadow_archer", "Shadow Archer"),
    BOOTS_SKY("boots_sky", "Boots of the Sky"),
    DELETION("deletion", "Stick of Deletion"),
    FIRE_GOD("fire_god", "Fire God"),
    ICE_EMPEROR("ice_emperor", "Ice Emperor"),
    THOR("thor", "Thor");

    private final String id;
    private final String displayName;

    PowerType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PowerType fromId(String id) {
        for (PowerType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
