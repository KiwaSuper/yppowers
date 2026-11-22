package com.yppowers.power.shadowarcher;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Unlockable special arrow variants for Shadow Archer. */
public enum ArrowType {
    SPLIT_SHOT("Split Shot", null, true),
    HOMING("Homing Missile", Items.ENDER_EYE, false),
    TNT("TNT Arrow", Items.TNT, false),
    LIGHTNING("Lightning Arrow", Items.LIGHTNING_ROD, false),
    ICE_PRISON("Ice Prison", Items.PACKED_ICE, false),
    RAILSHOT("Railshot", Items.AMETHYST_SHARD, false);

    private final String displayName;
    private final Item unlockCatalyst;
    private final boolean unlockedByDefault;

    ArrowType(String displayName, Item unlockCatalyst, boolean unlockedByDefault) {
        this.displayName = displayName;
        this.unlockCatalyst = unlockCatalyst;
        this.unlockedByDefault = unlockedByDefault;
    }

    public String displayName() {
        return displayName;
    }

    public Item unlockCatalyst() {
        return unlockCatalyst;
    }

    public boolean unlockedByDefault() {
        return unlockedByDefault;
    }
}
