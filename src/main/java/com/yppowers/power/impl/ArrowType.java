package com.yppowers.power.impl;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public enum ArrowType {
    SPLIT_SHOT("Split Shot", Items.SPECTRAL_ARROW),
    HOMING("Homing Missile", Items.ENDER_EYE),
    TNT("TNT Arrow", Items.TNT),
    LIGHTNING("Lightning Arrow", Items.LIGHTNING_ROD),
    ICE_PRISON("Ice Prison", Items.PACKED_ICE),
    RAILSHOT("Railshot", Items.NETHER_STAR);
    
    private final String displayName;
    private final Item craftItem;

    ArrowType(String displayName, Item craftItem) {
        this.displayName = displayName;
        this.craftItem = craftItem;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Item getCraftItem() {
        return craftItem;
    }
}
