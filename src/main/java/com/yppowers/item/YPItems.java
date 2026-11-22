package com.yppowers.item;

import com.yppowers.YPPowersMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/** Custom items added by YP Powers. */
public final class YPItems {
    public static final Identifier WEB_SLINGERS_ID = Identifier.of(YPPowersMod.MOD_ID, "web_slingers");
    public static final Item WEB_SLINGERS = Registry.register(
            Registries.ITEM,
            WEB_SLINGERS_ID,
            new Item(new Item.Settings()
                    .registryKey(RegistryKey.of(RegistryKeys.ITEM, WEB_SLINGERS_ID))
                    .maxCount(1))
    );

    private YPItems() {}

    public static void initialize() {
        // Trigger static registration during mod initialization.
    }
}
