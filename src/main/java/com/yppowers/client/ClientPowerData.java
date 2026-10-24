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
    /** Server-authoritative dimensions used only to adjust the local Shadow Archer camera. */
    public static boolean shadowArcherMorphed = false;
    /** Network ID of the server-created disguise, used to hide it only in local first person. */
    public static int shadowArcherDisguiseEntityId = -1;
    public static float shadowArcherMorphEyeHeight = 1.62F;
    public static float shadowArcherMorphHeight = 1.8F;
    public static final Set<String> unlockedShadowArcherMorphs = new HashSet<>();
    /** Entity network IDs of players currently under Time Stop, used only for client-side rendering. */
    public static final Set<Integer> timeStoppedPlayerEntityIds = new HashSet<>();
    public static double energy = 100.0;
    public static double maxEnergy = 100.0;
    public static int bleedingTicks = 0;
    public static boolean globalHealthEnabled = true;
}
