package com.yppowers.client;

import java.util.HashSet;
import java.util.Set;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ClientPowerData {
    public static boolean hasFireGodPower = false;
    /** Server-synchronized ownership used to open the Shadow Archer UI without a round-trip. */
    public static boolean hasShadowArcherPower = false;
    public static final Set<String> unlockedShadowArcherMorphs = new HashSet<>();
}
