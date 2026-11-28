package com.yppowers.power;
import com.yppowers.progression.RitualManager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.yppowers.core.PowerRegistry;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.shadowarcher.ArrowType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public class PowerManager extends PersistentState {
   private final Map<UUID, PowerType> playerPowers = new HashMap<>();
   private final Map<UUID, Set<String>> shadowArcherKills = new HashMap<>();
   private final Map<UUID, Set<String>> shadowArcherUnlockedArrows = new HashMap<>();
   private final Set<UUID> shadowArcherSpecialArrowsDisabled = new HashSet<>();
   private final Map<UUID, String> shadowArcherBowModes = new HashMap<>();
   private final Map<UUID, Set<String>> spiritChannelerUnlocks = new HashMap<>();
   private final Map<UUID, Integer> timeShardCounts = new HashMap<>();
   private final Map<UUID, List<NbtCompound>> storedTimeClones = new HashMap<>();
   private final Map<UUID, String> timeWarperForms = new HashMap<>();
   private final Map<UUID, Integer> timeWarperFormCooldowns = new HashMap<>();
   private final Map<UUID, String> iceEmperorForms = new HashMap<>();
   private final Map<UUID, Set<String>> disabledPowers = new HashMap<>();
   private final Set<UUID> bootsSkyFeatherFallingDisabled = new HashSet<>();
   private final Map<UUID, NbtCompound> thorHammerStates = new HashMap<>();
   private final Set<UUID> grimoireSkillsetPlayers = new HashSet<>();
   private final Map<UUID, NbtCompound> ritualProgress = new HashMap<>();
   private int graceTicksRemaining;
   private boolean gauntletSiteGenerated;
   private boolean gauntletSitePending;
   private int gauntletSiteX;
   private int gauntletSiteY;
   private int gauntletSiteZ;
   private long gauntletLastTouchedTime = -1L;
   private int gauntletGeneration;
   private boolean grimoireSiteGenerated;
   private boolean grimoireSitePending;
   private int grimoireSiteX;
   private int grimoireSiteY;
   private int grimoireSiteZ;
   private long grimoireLastTouchedTime = -1L;
   private int grimoireGeneration;
   private final Map<UUID, Integer> teleportGambitDomainCooldowns = new HashMap<>();
   private final Map<UUID, NbtCompound> teleportGambitPendingReturns = new HashMap<>();
   private static final Map<PowerType, Power> powerInstances = new HashMap<>();
   private static final Codec<PowerManager> CODEC = Codec.PASSTHROUGH.xmap(dynamic -> {
      NbtElement element = (NbtElement)dynamic.convert(NbtOps.INSTANCE).getValue();
      return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
   }, manager -> new Dynamic(NbtOps.INSTANCE, manager.toNbt()));
   private static final PersistentStateType<PowerManager> STATE_TYPE = new PersistentStateType<>("yppowers_data", PowerManager::new, CODEC, null);

   public static void registerPower(Power power) {
      powerInstances.put(power.getType(), power);
      PowerRegistry.register(power);
   }

   public void assignPower(ServerPlayerEntity player, PowerType type) {
      PowerType baseType = this.playerPowers.get(player.getUuid());
      PowerType oldType = this.getPowerType(player.getUuid());
      YPNetworking.clearVfxForSource(player);
      RitualManager.clearPlayerRuntime(player.getUuid());
      if (oldType != null) {
         Power oldPower = powerInstances.get(oldType);
         if (oldPower != null) {
            oldPower.onRemove(player);
         }
      }

      if (baseType != null && baseType != oldType) {
         Power basePower = powerInstances.get(baseType);
         if (basePower != null) {
            basePower.onRemove(player);
         }
      }

      this.setGrimoireSkillsetEnabled(player.getUuid(), false);
      if (oldType != null) {
         this.setPowerEnabled(player.getUuid(), oldType, true);
      }

      if (baseType != null) {
         this.setPowerEnabled(player.getUuid(), baseType, true);
      }

      if (type != null) {
         this.setPowerEnabled(player.getUuid(), type, true);
      }

      this.playerPowers.put(player.getUuid(), type);
      if (type != null) {
         Power newPower = powerInstances.get(type);
         if (newPower != null) {
            newPower.onAssign(player);
         }
      }

      YPNetworking.syncFireGod(player, type == PowerType.FIRE_GOD);
      YPNetworking.syncShadowArcher(player, type == PowerType.SHADOW_ARCHER);
      YPNetworking.syncGrimoireMode(player, false);
      this.markDirty();
   }

   public void clearPower(ServerPlayerEntity player) {
      PowerType baseType = this.playerPowers.get(player.getUuid());
      PowerType oldType = this.getPowerType(player.getUuid());
      YPNetworking.clearVfxForSource(player);
      RitualManager.clearPlayerRuntime(player.getUuid());
      if (oldType != null) {
         Power oldPower = powerInstances.get(oldType);
         if (oldPower != null) {
            oldPower.onRemove(player);
         }
      }

      if (baseType != null && baseType != oldType) {
         Power basePower = powerInstances.get(baseType);
         if (basePower != null) {
            basePower.onRemove(player);
         }
      }

      this.playerPowers.remove(player.getUuid());
      this.setGrimoireSkillsetEnabled(player.getUuid(), false);
      this.disabledPowers.remove(player.getUuid());
      YPNetworking.syncFireGod(player, false);
      YPNetworking.syncShadowArcher(player, false);
      YPNetworking.syncGrimoireMode(player, false);
      this.markDirty();
   }

   public boolean isPowerEnabled(UUID playerId, PowerType type) {
      return type == null ? false : !this.disabledPowers.getOrDefault(playerId, Collections.emptySet()).contains(type.getId());
   }

   public boolean isPowerEnabled(ServerPlayerEntity player, PowerType type) {
      return this.isPowerEnabled(player.getUuid(), type);
   }

   public boolean isBootsSkyFeatherFallingEnabled(UUID playerId) {
      return !this.bootsSkyFeatherFallingDisabled.contains(playerId);
   }

   public boolean toggleBootsSkyFeatherFalling(ServerPlayerEntity player) {
      UUID playerId = player.getUuid();
      boolean enabled;
      if (this.bootsSkyFeatherFallingDisabled.remove(playerId)) {
         enabled = true;
      } else {
         this.bootsSkyFeatherFallingDisabled.add(playerId);
         enabled = false;
      }

      this.markDirty();
      return enabled;
   }

   public boolean togglePowerEnabled(ServerPlayerEntity player, PowerType type) {
      Set<String> disabled = this.disabledPowers.computeIfAbsent(player.getUuid(), ignored -> new HashSet<>());
      boolean enabled;
      if (disabled.remove(type.getId())) {
         enabled = true;
      } else {
         disabled.add(type.getId());
         enabled = false;
      }

      if (disabled.isEmpty()) {
         this.disabledPowers.remove(player.getUuid());
      }

      this.markDirty();
      return enabled;
   }

   private void setPowerEnabled(UUID playerId, PowerType type, boolean enabled) {
      if (type != null) {
         Set<String> disabled = this.disabledPowers.computeIfAbsent(playerId, ignored -> new HashSet<>());
         boolean changed = enabled ? disabled.remove(type.getId()) : disabled.add(type.getId());
         if (disabled.isEmpty()) {
            this.disabledPowers.remove(playerId);
         }

         if (changed) {
            this.markDirty();
         }
      }
   }

   public PowerType getPowerType(UUID uuid) {
      return this.grimoireSkillsetPlayers.contains(uuid) ? null : this.playerPowers.get(uuid);
   }

   public PowerType getAssignedPowerType(UUID uuid) {
      return this.playerPowers.get(uuid);
   }

   public boolean isGrimoireSkillsetEnabled(UUID playerId) {
      return this.grimoireSkillsetPlayers.contains(playerId);
   }

   public boolean setGrimoireSkillsetEnabled(UUID playerId, boolean enabled) {
      if (playerId == null) {
         return false;
      } else {
         boolean changed = enabled ? this.grimoireSkillsetPlayers.add(playerId) : this.grimoireSkillsetPlayers.remove(playerId);
         if (changed) {
            this.markDirty();
         }

         return this.grimoireSkillsetPlayers.contains(playerId);
      }
   }

   public boolean toggleGrimoireSkillset(UUID playerId) {
      return this.setGrimoireSkillsetEnabled(playerId, !this.isGrimoireSkillsetEnabled(playerId));
   }

   public void saveThorHammerState(UUID playerId, NbtCompound state) {
      if (state != null && !state.isEmpty()) {
         this.thorHammerStates.put(playerId, state.copy());
         this.markDirty();
      } else {
         if (this.thorHammerStates.remove(playerId) != null) {
            this.markDirty();
         }
      }
   }

   public NbtCompound getThorHammerState(UUID playerId) {
      NbtCompound state = this.thorHammerStates.get(playerId);
      return state == null ? null : state.copy();
   }

   public Map<UUID, NbtCompound> getThorHammerStates() {
      Map<UUID, NbtCompound> copy = new HashMap<>();
      this.thorHammerStates.forEach((id, state) -> copy.put(id, state.copy()));
      return copy;
   }

   public void clearThorHammerState(UUID playerId) {
      if (this.thorHammerStates.remove(playerId) != null) {
         this.markDirty();
      }
   }

   public boolean isGracePeriodActive() {
      return this.graceTicksRemaining > 0;
   }

   public int getGraceTicksRemaining() {
      return Math.max(0, this.graceTicksRemaining);
   }

   public void setGraceTicksRemaining(int ticks) {
      int clamped = Math.max(0, ticks);
      if (this.graceTicksRemaining != clamped) {
         this.graceTicksRemaining = clamped;
         this.markDirty();
      }
   }

   public void tickGracePeriod(MinecraftServer server) {
      if (this.graceTicksRemaining > 0) {
         this.graceTicksRemaining--;
         if (this.graceTicksRemaining == 0 || this.graceTicksRemaining % 20 == 0) {
            this.markDirty();
         }

         if (this.graceTicksRemaining == 0) {
            server.getPlayerManager().broadcast(Text.literal("Grace period ended. Abilities and PvP are enabled again."), false);
         }
      }
   }

   public int getTeleportGambitDomainCooldownTicks(UUID playerId) {
      return Math.max(0, this.teleportGambitDomainCooldowns.getOrDefault(playerId, 0));
   }

   public void setTeleportGambitDomainCooldownTicks(UUID playerId, int ticks) {
      int clamped = Math.max(0, ticks);
      if (clamped == 0) {
         this.teleportGambitDomainCooldowns.remove(playerId);
      } else {
         this.teleportGambitDomainCooldowns.put(playerId, clamped);
      }

      this.markDirty();
   }

   public void tickTeleportGambitDomainCooldowns() {
      if (!this.teleportGambitDomainCooldowns.isEmpty()) {
         boolean shouldSave = false;
         Iterator<Entry<UUID, Integer>> iterator = this.teleportGambitDomainCooldowns.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<UUID, Integer> entry = iterator.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
               iterator.remove();
               shouldSave = true;
            } else {
               entry.setValue(next);
               if (next % 20 == 0) {
                  shouldSave = true;
               }
            }
         }

         if (shouldSave) {
            this.markDirty();
         }
      }
   }

   public void saveTeleportGambitPendingReturn(UUID playerId, NbtCompound location) {
      if (location != null && !location.isEmpty()) {
         this.teleportGambitPendingReturns.put(playerId, location.copy());
         this.markDirty();
      }
   }

   public NbtCompound getTeleportGambitPendingReturn(UUID playerId) {
      NbtCompound data = this.teleportGambitPendingReturns.get(playerId);
      return data == null ? null : data.copy();
   }

   public void clearTeleportGambitPendingReturn(UUID playerId) {
      if (this.teleportGambitPendingReturns.remove(playerId) != null) {
         this.markDirty();
      }
   }

   public Power getPowerInstance(PowerType type) {
      return powerInstances.get(type);
   }

   public boolean hasPower(ServerPlayerEntity player, PowerType type) {
      return this.playerPowers.get(player.getUuid()) == type;
   }

   public NbtCompound getRitualProgress(UUID playerId) {
      NbtCompound progress = this.ritualProgress.get(playerId);
      return progress == null ? new NbtCompound() : progress.copy();
   }

   public void setRitualProgress(UUID playerId, NbtCompound progress) {
      if (playerId != null) {
         if (progress != null && !progress.isEmpty()) {
            this.ritualProgress.put(playerId, progress.copy());
         } else {
            this.ritualProgress.remove(playerId);
         }

         this.markDirty();
      }
   }

   public void recordShadowArcherKill(UUID playerId, EntityType<?> entityType) {
      Identifier id = Registries.ENTITY_TYPE.getId(entityType);
      if (id != null && this.shadowArcherKills.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(id.toString())) {
         this.markDirty();
      }
   }

   public Set<String> getShadowArcherKills(UUID playerId) {
      return new HashSet<>(this.shadowArcherKills.getOrDefault(playerId, Collections.emptySet()));
   }

   public void recordShadowArcherArrowUnlock(UUID playerId, ArrowType arrowType) {
      if (arrowType != null && this.shadowArcherUnlockedArrows.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(arrowType.name())) {
         this.markDirty();
      }
   }

   public Set<ArrowType> getShadowArcherUnlockedArrows(UUID playerId) {
      Set<ArrowType> result = new HashSet<>();

      for (String raw : this.shadowArcherUnlockedArrows.getOrDefault(playerId, Collections.emptySet())) {
         try {
            result.add(ArrowType.valueOf(raw));
         } catch (IllegalArgumentException var6) {
         }
      }

      return result;
   }

   public boolean areShadowArcherSpecialArrowsDisabled(UUID playerId) {
      return this.shadowArcherSpecialArrowsDisabled.contains(playerId);
   }

   public boolean setShadowArcherSpecialArrowsEnabled(UUID playerId, boolean enabled) {
      boolean changed = enabled ? this.shadowArcherSpecialArrowsDisabled.remove(playerId) : this.shadowArcherSpecialArrowsDisabled.add(playerId);
      if (changed) {
         this.markDirty();
      }

      return enabled;
   }

   public String getShadowArcherBowMode(UUID playerId) {
      String mode = this.shadowArcherBowModes.get(playerId);
      return "MENDING".equals(mode) ? "MENDING" : "INFINITY";
   }

   public void setShadowArcherBowMode(UUID playerId, String mode) {
      if (playerId != null && mode != null) {
         String normalized = "MENDING".equalsIgnoreCase(mode) ? "MENDING" : "INFINITY";
         if (!normalized.equals(this.shadowArcherBowModes.put(playerId, normalized))) {
            this.markDirty();
         }
      }
   }

   public void clearCooldownsFor(UUID playerId) {
      boolean changed = this.teleportGambitDomainCooldowns.remove(playerId) != null;
      changed |= this.timeWarperFormCooldowns.remove(playerId) != null;
      if (changed) {
         this.markDirty();
      }
   }

   public boolean unlockSpiritForm(UUID playerId, String form) {
      if (form != null && Set.of("WOLF", "GUARDIAN", "WRAITH").contains(form)) {
         boolean changed = this.spiritChannelerUnlocks.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(form);
         if (changed) {
            this.markDirty();
         }

         return changed;
      } else {
         return false;
      }
   }

   public Set<String> getSpiritChannelerUnlocks(UUID playerId) {
      return new HashSet<>(this.spiritChannelerUnlocks.getOrDefault(playerId, Collections.emptySet()));
   }

   public int getTimeShards(UUID playerId) {
      return Math.max(0, this.timeShardCounts.getOrDefault(playerId, 0));
   }

   public void addTimeShards(UUID playerId, int amount) {
      if (amount > 0) {
         int old = this.getTimeShards(playerId);
         int next = (int)Math.min(2147483647L, (long)old + amount);
         this.timeShardCounts.put(playerId, next);
         this.markDirty();
      }
   }

   public boolean spendTimeShards(UUID playerId, int amount) {
      int cost = Math.max(0, amount);
      int current = this.getTimeShards(playerId);
      if (current < cost) {
         return false;
      } else {
         this.timeShardCounts.put(playerId, current - cost);
         this.markDirty();
         return true;
      }
   }

   public void saveStoredTimeClones(UUID owner, List<NbtCompound> snapshots) {
      List<NbtCompound> copy = new ArrayList<>();
      if (snapshots != null) {
         for (NbtCompound nbt : snapshots) {
            if (nbt != null) {
               copy.add(nbt.copy());
            }
         }
      }

      if (copy.isEmpty()) {
         this.storedTimeClones.remove(owner);
      } else {
         this.storedTimeClones.put(owner, copy);
      }

      this.markDirty();
   }

   public List<NbtCompound> takeStoredTimeClones(UUID owner) {
      List<NbtCompound> saved = this.storedTimeClones.remove(owner);
      if (saved == null) {
         return new ArrayList<>();
      } else {
         this.markDirty();
         List<NbtCompound> copy = new ArrayList<>();

         for (NbtCompound nbt : saved) {
            copy.add(nbt.copy());
         }

         return copy;
      }
   }

   public boolean isGauntletSiteGenerated() {
      return this.gauntletSiteGenerated;
   }

   public boolean isGauntletSitePending() {
      return this.gauntletSitePending;
   }

   public int getGauntletGeneration() {
      return Math.max(1, this.gauntletGeneration);
   }

   public long getGauntletLastTouchedTime() {
      return this.gauntletLastTouchedTime;
   }

   public void planGauntletSite(int x, int z, long now, boolean relocation) {
      this.gauntletGeneration = this.gauntletGeneration <= 0 ? (relocation ? 2 : 1) : this.gauntletGeneration + (relocation ? 1 : 0);
      this.gauntletSiteGenerated = false;
      this.gauntletSitePending = true;
      this.gauntletSiteX = x;
      this.gauntletSiteY = 0;
      this.gauntletSiteZ = z;
      this.gauntletLastTouchedTime = now;
      this.markDirty();
   }

   public void markGauntletSiteGenerated(int y) {
      if (this.gauntletSitePending || !this.gauntletSiteGenerated) {
         this.gauntletSiteY = y;
         this.gauntletSitePending = false;
         this.gauntletSiteGenerated = true;
         this.markDirty();
      }
   }

   public void touchGauntlet(long now) {
      if (this.gauntletLastTouchedTime < 0L || now - this.gauntletLastTouchedTime >= 1200L) {
         this.gauntletLastTouchedTime = now;
         this.markDirty();
      }
   }

   public void setGauntletSiteGenerated(int x, int y, int z) {
      this.gauntletSiteX = x;
      this.gauntletSiteY = y;
      this.gauntletSiteZ = z;
      this.gauntletSiteGenerated = true;
      this.gauntletSitePending = false;
      if (this.gauntletGeneration <= 0) {
         this.gauntletGeneration = 1;
      }

      this.markDirty();
   }

   public int getGauntletSiteX() {
      return this.gauntletSiteX;
   }

   public int getGauntletSiteY() {
      return this.gauntletSiteY;
   }

   public int getGauntletSiteZ() {
      return this.gauntletSiteZ;
   }

   public boolean isGrimoireSiteGenerated() {
      return this.grimoireSiteGenerated;
   }

   public boolean isGrimoireSitePending() {
      return this.grimoireSitePending;
   }

   public int getGrimoireSiteX() {
      return this.grimoireSiteX;
   }

   public int getGrimoireSiteY() {
      return this.grimoireSiteY;
   }

   public int getGrimoireSiteZ() {
      return this.grimoireSiteZ;
   }

   public int getGrimoireGeneration() {
      return Math.max(1, this.grimoireGeneration);
   }

   public long getGrimoireLastTouchedTime() {
      return this.grimoireLastTouchedTime;
   }

   public void planGrimoireSite(int x, int z, long now, boolean relocation) {
      this.grimoireGeneration = this.grimoireGeneration <= 0 ? (relocation ? 2 : 1) : this.grimoireGeneration + (relocation ? 1 : 0);
      this.grimoireSiteGenerated = false;
      this.grimoireSitePending = true;
      this.grimoireSiteX = x;
      this.grimoireSiteY = 0;
      this.grimoireSiteZ = z;
      this.grimoireLastTouchedTime = now;
      this.markDirty();
   }

   public void markGrimoireSiteGenerated(int y) {
      if (this.grimoireSitePending || !this.grimoireSiteGenerated) {
         this.grimoireSiteY = y;
         this.grimoireSitePending = false;
         this.grimoireSiteGenerated = true;
         this.markDirty();
      }
   }

   public void touchGrimoire(long now) {
      if (this.grimoireLastTouchedTime < 0L || now - this.grimoireLastTouchedTime >= 1200L) {
         this.grimoireLastTouchedTime = now;
         this.markDirty();
      }
   }

   public String getTimeWarperForm(UUID owner) {
      return this.timeWarperForms.getOrDefault(owner, "TIME");
   }

   public void setTimeWarperForm(UUID owner, String form) {
      if ("TIME".equals(form) || "REALITY".equals(form)) {
         this.timeWarperForms.put(owner, form);
         this.markDirty();
      }
   }

   public String getIceEmperorForm(UUID playerId) {
      String form = this.iceEmperorForms.get(playerId);
      return form != null && Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(form) ? form : "ARMOURED";
   }

   public boolean hasIceEmperorForm(UUID playerId) {
      return this.iceEmperorForms.containsKey(playerId);
   }

   public void setIceEmperorForm(UUID playerId, String form) {
      if (playerId != null && form != null && Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(form)) {
         if (!form.equals(this.iceEmperorForms.put(playerId, form))) {
            this.markDirty();
         }
      }
   }

   public void clearIceEmperorForm(UUID playerId) {
      if (this.iceEmperorForms.remove(playerId) != null) {
         this.markDirty();
      }
   }

   public int getTimeWarperFormCooldownTicks(UUID owner) {
      return Math.max(0, this.timeWarperFormCooldowns.getOrDefault(owner, 0));
   }

   public void setTimeWarperFormCooldownTicks(UUID owner, int ticks) {
      int value = Math.max(0, ticks);
      if (value == 0) {
         this.timeWarperFormCooldowns.remove(owner);
      } else {
         this.timeWarperFormCooldowns.put(owner, value);
      }

      this.markDirty();
   }

   public void tickTimeWarperFormCooldowns() {
      if (!this.timeWarperFormCooldowns.isEmpty()) {
         boolean changed = false;
         Iterator<Entry<UUID, Integer>> it = this.timeWarperFormCooldowns.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> e = it.next();
            int value = e.getValue() - 1;
            if (value <= 0) {
               it.remove();
               changed = true;
            } else {
               e.setValue(value);
               if (value % 20 == 0) {
                  changed = true;
               }
            }
         }

         if (changed) {
            this.markDirty();
         }
      }
   }

   private NbtCompound toNbt() {
      NbtCompound nbt = new NbtCompound();
      NbtCompound powersNbt = new NbtCompound();

      for (Entry<UUID, PowerType> entry : this.playerPowers.entrySet()) {
         powersNbt.putString(entry.getKey().toString(), entry.getValue().getId());
      }

      nbt.put("PlayerPowers", powersNbt);
      NbtCompound killsNbt = new NbtCompound();

      for (Entry<UUID, Set<String>> entry : this.shadowArcherKills.entrySet()) {
         NbtList ids = new NbtList();

         for (String id : entry.getValue()) {
            ids.add(NbtString.of(id));
         }

         killsNbt.put(entry.getKey().toString(), ids);
      }

      nbt.put("ShadowArcherKills", killsNbt);
      NbtCompound unlockedArrowsNbt = new NbtCompound();

      for (Entry<UUID, Set<String>> entry : this.shadowArcherUnlockedArrows.entrySet()) {
         NbtList ids = new NbtList();

         for (String id : entry.getValue()) {
            ids.add(NbtString.of(id));
         }

         unlockedArrowsNbt.put(entry.getKey().toString(), ids);
      }

      nbt.put("ShadowArcherUnlockedArrows", unlockedArrowsNbt);
      NbtList specialArrowsDisabledNbt = new NbtList();

      for (UUID playerId : this.shadowArcherSpecialArrowsDisabled) {
         specialArrowsDisabledNbt.add(NbtString.of(playerId.toString()));
      }

      nbt.put("ShadowArcherSpecialArrowsDisabled", specialArrowsDisabledNbt);
      NbtCompound bowModesNbt = new NbtCompound();

      for (Entry<UUID, String> entry : this.shadowArcherBowModes.entrySet()) {
         bowModesNbt.putString(entry.getKey().toString(), entry.getValue());
      }

      nbt.put("ShadowArcherBowModes", bowModesNbt);
      NbtCompound spiritUnlocksNbt = new NbtCompound();

      for (Entry<UUID, Set<String>> entry : this.spiritChannelerUnlocks.entrySet()) {
         NbtList forms = new NbtList();

         for (String form : entry.getValue()) {
            forms.add(NbtString.of(form));
         }

         spiritUnlocksNbt.put(entry.getKey().toString(), forms);
      }

      nbt.put("SpiritChannelerUnlocks", spiritUnlocksNbt);
      NbtCompound timeShardsNbt = new NbtCompound();

      for (Entry<UUID, Integer> entry : this.timeShardCounts.entrySet()) {
         int count = Math.max(0, entry.getValue());
         if (count > 0) {
            timeShardsNbt.putInt(entry.getKey().toString(), count);
         }
      }

      nbt.put("TimeShardCounts", timeShardsNbt);
      NbtCompound storedClonesNbt = new NbtCompound();

      for (Entry<UUID, List<NbtCompound>> entryx : this.storedTimeClones.entrySet()) {
         NbtList list = new NbtList();

         for (NbtCompound clone : entryx.getValue()) {
            list.add(clone.copy());
         }

         storedClonesNbt.put(entryx.getKey().toString(), list);
      }

      nbt.put("StoredTimeClones", storedClonesNbt);
      NbtCompound formsNbt = new NbtCompound();

      for (Entry<UUID, String> entryx : this.timeWarperForms.entrySet()) {
         formsNbt.putString(entryx.getKey().toString(), entryx.getValue());
      }

      nbt.put("TimeWarperForms", formsNbt);
      NbtCompound formCooldownsNbt = new NbtCompound();

      for (Entry<UUID, Integer> entryx : this.timeWarperFormCooldowns.entrySet()) {
         if (entryx.getValue() > 0) {
            formCooldownsNbt.putInt(entryx.getKey().toString(), entryx.getValue());
         }
      }

      nbt.put("TimeWarperFormCooldowns", formCooldownsNbt);
      NbtCompound iceFormsNbt = new NbtCompound();

      for (Entry<UUID, String> entryxx : this.iceEmperorForms.entrySet()) {
         if (Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(entryxx.getValue())) {
            iceFormsNbt.putString(entryxx.getKey().toString(), entryxx.getValue());
         }
      }

      nbt.put("IceEmperorForms", iceFormsNbt);
      NbtCompound disabledPowersNbt = new NbtCompound();

      for (Entry<UUID, Set<String>> entryxxx : this.disabledPowers.entrySet()) {
         NbtList ids = new NbtList();

         for (String id : entryxxx.getValue()) {
            ids.add(NbtString.of(id));
         }

         disabledPowersNbt.put(entryxxx.getKey().toString(), ids);
      }

      nbt.put("DisabledPowers", disabledPowersNbt);
      NbtList bootsFeatherDisabled = new NbtList();

      for (UUID playerId : this.bootsSkyFeatherFallingDisabled) {
         bootsFeatherDisabled.add(NbtString.of(playerId.toString()));
      }

      nbt.put("BootsSkyFeatherFallingDisabled", bootsFeatherDisabled);
      NbtCompound thorHammersNbt = new NbtCompound();

      for (Entry<UUID, NbtCompound> entryxxx : this.thorHammerStates.entrySet()) {
         thorHammersNbt.put(entryxxx.getKey().toString(), entryxxx.getValue().copy());
      }

      nbt.put("ThorHammerStates", thorHammersNbt);
      nbt.putInt("GraceTicksRemaining", Math.max(0, this.graceTicksRemaining));
      NbtList grimoireSkillsetsNbt = new NbtList();

      for (UUID playerId : this.grimoireSkillsetPlayers) {
         grimoireSkillsetsNbt.add(NbtString.of(playerId.toString()));
      }

      nbt.put("GrimoireSkillsetPlayers", grimoireSkillsetsNbt);
      NbtCompound ritualProgressNbt = new NbtCompound();

      for (Entry<UUID, NbtCompound> entryxxx : this.ritualProgress.entrySet()) {
         ritualProgressNbt.put(entryxxx.getKey().toString(), entryxxx.getValue().copy());
      }

      nbt.put("RitualProgress", ritualProgressNbt);
      NbtCompound tgCooldownsNbt = new NbtCompound();

      for (Entry<UUID, Integer> entryxxx : this.teleportGambitDomainCooldowns.entrySet()) {
         if (entryxxx.getValue() > 0) {
            tgCooldownsNbt.putInt(entryxxx.getKey().toString(), entryxxx.getValue());
         }
      }

      nbt.put("TeleportGambitDomainCooldowns", tgCooldownsNbt);
      NbtCompound tgPendingReturnsNbt = new NbtCompound();

      for (Entry<UUID, NbtCompound> entryxxxx : this.teleportGambitPendingReturns.entrySet()) {
         tgPendingReturnsNbt.put(entryxxxx.getKey().toString(), entryxxxx.getValue().copy());
      }

      nbt.put("TeleportGambitPendingReturns", tgPendingReturnsNbt);
      NbtCompound gauntletSite = new NbtCompound();
      gauntletSite.putBoolean("Generated", this.gauntletSiteGenerated);
      gauntletSite.putBoolean("Pending", this.gauntletSitePending);
      gauntletSite.putLong("LastTouched", this.gauntletLastTouchedTime);
      gauntletSite.putInt("Generation", Math.max(1, this.gauntletGeneration));
      if (this.gauntletSiteGenerated || this.gauntletSitePending) {
         gauntletSite.putInt("X", this.gauntletSiteX);
         gauntletSite.putInt("Y", this.gauntletSiteY);
         gauntletSite.putInt("Z", this.gauntletSiteZ);
      }

      nbt.put("GauntletOfDarknessSite", gauntletSite);
      NbtCompound grimoireSite = new NbtCompound();
      grimoireSite.putBoolean("Generated", this.grimoireSiteGenerated);
      grimoireSite.putBoolean("Pending", this.grimoireSitePending);
      grimoireSite.putLong("LastTouched", this.grimoireLastTouchedTime);
      grimoireSite.putInt("Generation", Math.max(1, this.grimoireGeneration));
      if (this.grimoireSiteGenerated || this.grimoireSitePending) {
         grimoireSite.putInt("X", this.grimoireSiteX);
         grimoireSite.putInt("Y", this.grimoireSiteY);
         grimoireSite.putInt("Z", this.grimoireSiteZ);
      }

      nbt.put("GrimoireOfLightSite", grimoireSite);
      return nbt;
   }

   public static PowerManager fromNbt(NbtCompound nbt) {
      PowerManager manager = new PowerManager();
      NbtCompound powersNbt = nbt.getCompoundOrEmpty("PlayerPowers");

      for (String key : powersNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            PowerType type = PowerType.fromId(powersNbt.getString(key, ""));
            if (type != null) {
               manager.playerPowers.put(uuid, type);
            }
         } catch (IllegalArgumentException var38) {
         }
      }

      NbtList grimoireSkillsetsNbt = nbt.getListOrEmpty("GrimoireSkillsetPlayers");

      for (int i = 0; i < grimoireSkillsetsNbt.size(); i++) {
         try {
            manager.grimoireSkillsetPlayers.add(UUID.fromString(grimoireSkillsetsNbt.getString(i, "")));
         } catch (IllegalArgumentException var37) {
         }
      }

      NbtCompound ritualProgressNbt = nbt.getCompoundOrEmpty("RitualProgress");

      for (String key : ritualProgressNbt.getKeys()) {
         try {
            UUID playerId = UUID.fromString(key);
            NbtCompound progress = ritualProgressNbt.getCompoundOrEmpty(key);
            if (!progress.isEmpty()) {
               manager.ritualProgress.put(playerId, progress.copy());
            }
         } catch (IllegalArgumentException var36) {
         }
      }

      NbtCompound killsNbt = nbt.getCompoundOrEmpty("ShadowArcherKills");

      for (String key : killsNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            NbtList ids = killsNbt.getListOrEmpty(key);
            Set<String> set = new HashSet<>();

            for (int i = 0; i < ids.size(); i++) {
               String value = ids.getString(i, "");
               if (Identifier.tryParse(value) != null) {
                  set.add(value);
               }
            }

            if (!set.isEmpty()) {
               manager.shadowArcherKills.put(uuid, set);
            }
         } catch (IllegalArgumentException var44) {
         }
      }

      NbtCompound unlockedArrowsNbt = nbt.getCompoundOrEmpty("ShadowArcherUnlockedArrows");

      for (String key : unlockedArrowsNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            NbtList ids = unlockedArrowsNbt.getListOrEmpty(key);
            Set<String> set = new HashSet<>();

            for (int ix = 0; ix < ids.size(); ix++) {
               String value = ids.getString(ix, "");

               try {
                  ArrowType.valueOf(value);
                  set.add(value);
               } catch (IllegalArgumentException var35) {
               }
            }

            if (!set.isEmpty()) {
               manager.shadowArcherUnlockedArrows.put(uuid, set);
            }
         } catch (IllegalArgumentException var43) {
         }
      }

      NbtCompound spiritUnlocksNbt = nbt.getCompoundOrEmpty("SpiritChannelerUnlocks");

      for (String key : spiritUnlocksNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            NbtList forms = spiritUnlocksNbt.getListOrEmpty(key);
            Set<String> set = new HashSet<>();

            for (int ix = 0; ix < forms.size(); ix++) {
               String form = forms.getString(ix, "");
               if (Set.of("WOLF", "GUARDIAN", "WRAITH").contains(form)) {
                  set.add(form);
               }
            }

            if (!set.isEmpty()) {
               manager.spiritChannelerUnlocks.put(uuid, set);
            }
         } catch (IllegalArgumentException var42) {
         }
      }

      NbtList specialArrowsDisabledNbt = nbt.getListOrEmpty("ShadowArcherSpecialArrowsDisabled");

      for (int ixx = 0; ixx < specialArrowsDisabledNbt.size(); ixx++) {
         try {
            manager.shadowArcherSpecialArrowsDisabled.add(UUID.fromString(specialArrowsDisabledNbt.getString(ixx, "")));
         } catch (IllegalArgumentException var34) {
         }
      }

      NbtCompound bowModesNbt = nbt.getCompoundOrEmpty("ShadowArcherBowModes");

      for (String key : bowModesNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            String mode = bowModesNbt.getString(key, "INFINITY");
            manager.shadowArcherBowModes.put(uuid, "MENDING".equals(mode) ? "MENDING" : "INFINITY");
         } catch (IllegalArgumentException var33) {
         }
      }

      NbtCompound timeShardsNbt = nbt.getCompoundOrEmpty("TimeShardCounts");

      for (String key : timeShardsNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            int count = Math.max(0, timeShardsNbt.getInt(key, 0));
            if (count > 0) {
               manager.timeShardCounts.put(uuid, count);
            }
         } catch (IllegalArgumentException var32) {
         }
      }

      NbtCompound storedClonesNbt = nbt.getCompoundOrEmpty("StoredTimeClones");

      for (String key : storedClonesNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            NbtList list = storedClonesNbt.getListOrEmpty(key);
            List<NbtCompound> snapshots = new ArrayList<>();

            for (int ixx = 0; ixx < list.size(); ixx++) {
               NbtCompound clone = list.getCompoundOrEmpty(ixx);
               String variant = clone.getString("Variant", "");
               int ticks = Math.max(0, clone.getInt("Ticks", 0));
               if ((variant.equals("ORDINARY") || variant.equals("ELITE")) && ticks > 0) {
                  snapshots.add(clone.copy());
               }
            }

            if (!snapshots.isEmpty()) {
               manager.storedTimeClones.put(uuid, snapshots);
            }
         } catch (IllegalArgumentException var41) {
         }
      }

      NbtCompound formsNbt = nbt.getCompoundOrEmpty("TimeWarperForms");

      for (String key : formsNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            String form = formsNbt.getString(key, "TIME");
            if (form.equals("TIME") || form.equals("REALITY")) {
               manager.timeWarperForms.put(uuid, form);
            }
         } catch (IllegalArgumentException var40) {
         }
      }

      NbtCompound formCooldownsNbt = nbt.getCompoundOrEmpty("TimeWarperFormCooldowns");

      for (String key : formCooldownsNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            int ticks = Math.max(0, formCooldownsNbt.getInt(key, 0));
            if (ticks > 0) {
               manager.timeWarperFormCooldowns.put(uuid, ticks);
            }
         } catch (IllegalArgumentException var31) {
         }
      }

      NbtCompound iceFormsNbt = nbt.getCompoundOrEmpty("IceEmperorForms");

      for (String key : iceFormsNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            String form = iceFormsNbt.getString(key, "ARMOURED");
            if (Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(form)) {
               manager.iceEmperorForms.put(uuid, form);
            }
         } catch (IllegalArgumentException var30) {
         }
      }

      NbtCompound disabledPowersNbt = nbt.getCompoundOrEmpty("DisabledPowers");

      for (String key : disabledPowersNbt.getKeys()) {
         try {
            UUID uuid = UUID.fromString(key);
            NbtList ids = disabledPowersNbt.getListOrEmpty(key);
            Set<String> set = new HashSet<>();

            for (int ixxx = 0; ixxx < ids.size(); ixxx++) {
               String id = ids.getString(ixxx, "");
               PowerType type = PowerType.fromId(id);
               if (type == PowerType.GOAT || type == PowerType.ICE_EMPEROR) {
                  set.add(id);
               }
            }

            if (!set.isEmpty()) {
               manager.disabledPowers.put(uuid, set);
            }
         } catch (IllegalArgumentException var39) {
         }
      }

      NbtList bootsFeatherDisabled = nbt.getListOrEmpty("BootsSkyFeatherFallingDisabled");

      for (int ixxxx = 0; ixxxx < bootsFeatherDisabled.size(); ixxxx++) {
         try {
            manager.bootsSkyFeatherFallingDisabled.add(UUID.fromString(bootsFeatherDisabled.getString(ixxxx, "")));
         } catch (IllegalArgumentException var29) {
         }
      }

      manager.graceTicksRemaining = Math.max(0, nbt.getInt("GraceTicksRemaining", 0));
      NbtCompound tgCooldownsNbt = nbt.getCompoundOrEmpty("TeleportGambitDomainCooldowns");

      for (String key : tgCooldownsNbt.getKeys()) {
         try {
            UUID playerId = UUID.fromString(key);
            int remaining = Math.max(0, tgCooldownsNbt.getInt(key, 0));
            if (remaining > 0) {
               manager.teleportGambitDomainCooldowns.put(playerId, remaining);
            }
         } catch (IllegalArgumentException var28) {
         }
      }

      NbtCompound tgPendingReturnsNbt = nbt.getCompoundOrEmpty("TeleportGambitPendingReturns");

      for (String key : tgPendingReturnsNbt.getKeys()) {
         try {
            UUID playerId = UUID.fromString(key);
            NbtCompound location = tgPendingReturnsNbt.getCompoundOrEmpty(key);
            if (Identifier.tryParse(location.getString("Dimension", "")) != null) {
               manager.teleportGambitPendingReturns.put(playerId, location.copy());
            }
         } catch (IllegalArgumentException var27) {
         }
      }

      NbtCompound gauntletSite = nbt.getCompoundOrEmpty("GauntletOfDarknessSite");
      manager.gauntletSiteGenerated = gauntletSite.getBoolean("Generated", false);
      manager.gauntletSitePending = gauntletSite.getBoolean("Pending", false);
      manager.gauntletLastTouchedTime = gauntletSite.getLong("LastTouched", -1L);
      manager.gauntletGeneration = Math.max(0, gauntletSite.getInt("Generation", 0));
      if (manager.gauntletSiteGenerated || manager.gauntletSitePending) {
         manager.gauntletSiteX = gauntletSite.getInt("X", 0);
         manager.gauntletSiteY = gauntletSite.getInt("Y", 0);
         manager.gauntletSiteZ = gauntletSite.getInt("Z", 0);
      }

      NbtCompound grimoireSite = nbt.getCompoundOrEmpty("GrimoireOfLightSite");
      manager.grimoireSiteGenerated = grimoireSite.getBoolean("Generated", false);
      manager.grimoireSitePending = grimoireSite.getBoolean("Pending", false);
      manager.grimoireLastTouchedTime = grimoireSite.getLong("LastTouched", -1L);
      manager.grimoireGeneration = Math.max(0, grimoireSite.getInt("Generation", 0));
      if (manager.grimoireSiteGenerated || manager.grimoireSitePending) {
         manager.grimoireSiteX = grimoireSite.getInt("X", 0);
         manager.grimoireSiteY = grimoireSite.getInt("Y", 0);
         manager.grimoireSiteZ = grimoireSite.getInt("Z", 0);
      }

      NbtCompound thorHammersNbt = nbt.getCompoundOrEmpty("ThorHammerStates");

      for (String key : thorHammersNbt.getKeys()) {
         try {
            UUID playerId = UUID.fromString(key);
            NbtCompound state = thorHammersNbt.getCompoundOrEmpty(key);
            UUID.fromString(state.getString("EntityId", ""));
            if (Identifier.tryParse(state.getString("Dimension", "")) != null) {
               manager.thorHammerStates.put(playerId, state.copy());
            }
         } catch (IllegalArgumentException var26) {
         }
      }

      return manager;
   }

   public static PowerManager get(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
   }
}
