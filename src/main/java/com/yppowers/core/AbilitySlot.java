package com.yppowers.core;

/** Typed ability input slots replacing legacy magic integers. */
public enum AbilitySlot {
    PRIMARY(0, "G"),
    SECONDARY(1, "H"),
    TERTIARY(2, "V"),
    QUATERNARY(3, "B"),
    ALTERNATE(4, "R"),
    DOUBLE_JUMP(5, "2xSpace"),
    FORM_SWITCH(6, "K"),
    FEATHER_FALLING_TOGGLE(7, "N"),
    HEAT_BLAST(8, "L");

    private final int wireId;
    private final String defaultKeyLabel;

    AbilitySlot(int wireId, String defaultKeyLabel) {
        this.wireId = wireId;
        this.defaultKeyLabel = defaultKeyLabel;
    }

    public int wireId() {
        return wireId;
    }

    public String defaultKeyLabel() {
        return defaultKeyLabel;
    }

    public static AbilitySlot fromWireId(int wireId) {
        for (AbilitySlot slot : values()) {
            if (slot.wireId == wireId) return slot;
        }
        return null;
    }
}
