package com.yppowers.client;

import com.yppowers.core.PowerId;
import com.yppowers.network.HudSnapshot;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Clean client-side mirror of server-synchronized power, HUD, morph, and visual state. */
@Environment(EnvType.CLIENT)
public final class ClientState {
    public static HudSnapshot hud = HudSnapshot.empty();
    public static String hudWarningText = "";
    public static int hudWarningTicks = 0;

    public static final Set<Integer> timeStoppedPlayerIds = new HashSet<>();
    public static final Map<UUID, String> iceEmperorForms = new HashMap<>();

    public static boolean shadowArcherMorphed = false;
    public static int shadowArcherDisguiseEntityId = -1;
    public static float shadowArcherMorphEyeHeight = 1.62f;
    public static float shadowArcherMorphHeight = 1.8f;
    public static String shadowArcherMorphMobId = "";
    public static final Set<String> unlockedMorphs = new HashSet<>();

    private ClientState() {}

    public static PowerId currentPower() {
        return PowerId.fromId(hud.powerId());
    }

    public static boolean hasPower(PowerId id) {
        return currentPower() == id;
    }

    public static void tickClient() {
        if (hudWarningTicks > 0) hudWarningTicks--;
        else hudWarningText = "";
    }

    public static void reset() {
        hud = HudSnapshot.empty();
        hudWarningText = "";
        hudWarningTicks = 0;
        timeStoppedPlayerIds.clear();
        iceEmperorForms.clear();
        shadowArcherMorphed = false;
        shadowArcherDisguiseEntityId = -1;
        shadowArcherMorphEyeHeight = 1.62f;
        shadowArcherMorphHeight = 1.8f;
        shadowArcherMorphMobId = "";
        unlockedMorphs.clear();
    }
}
