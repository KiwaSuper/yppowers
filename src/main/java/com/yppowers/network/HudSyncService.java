package com.yppowers.network;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.core.PowerRegistry;
import com.yppowers.energy.EnergyManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.progression.AwakeningManager;
import com.yppowers.progression.RitualManager;
import com.yppowers.system.CombatTagSystem;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Builds and dispatches unified HUD snapshots (power badge, chip, energy, cooldown pills, combat timer). */
public final class HudSyncService {
   private HudSyncService() {
   }

   public static void sync(ServerPlayerEntity player) {
      if (player == null || player.networkHandler == null || player.getEntityWorld().getServer() == null) {
         return;
      }
      if (!ServerPlayNetworking.canSend(player, HudSnapshot.ID)) {
         return;
      }
      MinecraftServer server = player.getEntityWorld().getServer();
      PowerManager powers = PowerManager.get(server);
      EnergyManager energyMgr = EnergyManager.get(server);
      PowerType assigned = powers.getAssignedPowerType(player.getUuid());
      Power power = assigned == null ? null : PowerRegistry.get(assigned);

      double energy = energyMgr.getEnergy(player.getUuid());
      double maxEnergy = Math.max(1.0, YPConfig.getInstance().energy_max);
      int bleedingTicks = Math.max(0, energyMgr.getBleedingTicks(player.getUuid()));
      int lockoutTicks = assigned == PowerType.ICE_EMPEROR ? Math.max(0, energyMgr.getIceHeatBlastLockoutTicks(player.getUuid())) : 0;
      boolean grace = GraceManager.isActive(server);
      boolean grimoire = powers.isGrimoireSkillsetEnabled(player.getUuid());
      int combatTicks = CombatTagSystem.getRemainingTicks(player.getUuid());
      int maxCombatTicks = CombatTagSystem.maxDurationTicks();

      String chip = "";
      List<HudPill> pills = new ArrayList<>();
      if (grimoire) {
         chip = "GRIMOIRE";
         pills.add(HudPill.ability(AbilityKey.PRIMARY, "Light Beam", 0, 1, true));
         pills.add(HudPill.toggle(AbilityKey.GRIMOIRE_TOGGLE, "Grimoire", true));
      } else if (power != null) {
         String rawChip = power.hudStatusChip(player);
         if (rawChip != null && !rawChip.isBlank()) {
            chip = rawChip;
         }
         pills.addAll(power.hudPills(player, energy));
         if (RitualManager.isAwakeningUnlocked(powers, player.getUuid(), assigned)) {
            int awCd = AwakeningManager.cooldownTicks(player, assigned);
            int awMax = awakeningMaxCooldown(assigned);
            pills.add(HudPill.ability(AbilityKey.AWAKENING, "Awaken", awCd, awMax, true));
         }
      }

      if (grace) {
         chip = chip.isEmpty() ? "GRACE" : "GRACE | " + chip;
      } else if (lockoutTicks > 0) {
         String lock = "LOCKED " + CombatTagSystem.formatSeconds(lockoutTicks);
         chip = chip.isEmpty() ? lock : lock + " | " + chip;
      }

      HudSnapshot snapshot = new HudSnapshot(
         assigned == null ? "" : assigned.getId(),
         chip,
         energy,
         maxEnergy,
         bleedingTicks,
         lockoutTicks,
         grace,
         grimoire,
         combatTicks,
         maxCombatTicks,
         pills
      );
      ServerPlayNetworking.send(player, snapshot);
   }

   public static void syncAll(MinecraftServer server) {
      if (server == null) {
         return;
      }
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         sync(player);
      }
   }

   private static int awakeningMaxCooldown(PowerType type) {
      YPConfig cfg = YPConfig.getInstance();
      return switch (type) {
         case PUNCH -> Math.max(1, cfg.awakening_punch_cooldown_ticks);
         case FALLOUT_WARRIOR -> Math.max(1, cfg.fallout_awakening_bomb_cooldown_ticks);
         case POSEIDON -> Math.max(1, cfg.poseidon_awakening_sink_cooldown_ticks);
         case GAMBIT -> Math.max(1, cfg.teleport_gambit_awakening_cooldown_ticks);
         case TIME_WARPER -> Math.max(1, cfg.time_warper_domain_cooldown_ticks);
         default -> 1200;
      };
   }
}
