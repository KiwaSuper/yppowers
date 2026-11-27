package com.yppowers.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ClientPowerData {
   public static final Map<UUID, String> iceEmperorForms = new HashMap<>();
   public static boolean hasFireGodPower = false;
   public static final Set<Integer> witherAscendantEntityIds = new HashSet<>();
   public static boolean hasShadowArcherPower = false;
   public static boolean grimoireSkillsetEnabled = false;
   public static boolean timeDomainActive = false;
   public static boolean timeDomainOwner = false;
   public static boolean shadowArcherMorphed = false;
   public static int shadowArcherDisguiseEntityId = -1;
   public static float shadowArcherMorphEyeHeight = 1.62F;
   public static float shadowArcherMorphHeight = 1.8F;
   public static final Set<String> unlockedShadowArcherMorphs = new HashSet<>();
   public static final Set<Integer> timeStoppedPlayerEntityIds = new HashSet<>();
   public static double energy = 100.0;
   public static double maxEnergy = 100.0;
   public static int bleedingTicks = 0;
   public static boolean globalHealthEnabled = true;
   public static String hudWarningText = "";
   public static int hudWarningTicks = 0;
   public static String assignedPowerId = "";
   public static boolean graceActive = false;
   public static int combatTicks = 0;
   public static int maxCombatTicks = 600;
   public static boolean powerMenuDataReady = false;
   public static String powerMenuPowerId = "";
   public static String powerMenuRitualStatus = "";
   public static boolean powerMenuRitualComplete = false;
   public static boolean powerMenuAwakeningUnlocked = false;
   public static boolean powerMenuCarriesGrimoire = false;
   public static int powerMenuAwakeningCooldownTicks = 0;
   public static String powerMenuSelectedArrow = "";
   public static List<String> powerMenuAvailableArrows = List.of();
   public static String powerMenuSelectedIceForm = "";
   public static List<String> powerMenuAvailableIceForms = List.of();
   public static String powerMenuSelectedSpirit = "";
   public static List<String> powerMenuAvailableSpirits = List.of();
}
