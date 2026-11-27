package com.yppowers.energy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.BootsOfTheSkyPower;
import com.yppowers.power.impl.GoatPower;
import com.yppowers.power.impl.IllusionistPower;
import com.yppowers.power.impl.SpideyPower;
import com.yppowers.power.impl.TeleportGambitRandomPortalManager;
import com.yppowers.power.impl.TimeWarperPower;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class EnergyManager extends PersistentState {
   private final Map<UUID, EnergyManager.PlayerState> players = new HashMap<>();
   private static final Codec<EnergyManager> CODEC = Codec.PASSTHROUGH.xmap(dynamic -> {
      NbtElement element = (NbtElement)dynamic.convert(NbtOps.INSTANCE).getValue();
      return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
   }, manager -> new Dynamic(NbtOps.INSTANCE, manager.toNbt()));
   private static final PersistentStateType<EnergyManager> STATE_TYPE = new PersistentStateType<>("yppowers_energy", EnergyManager::new, CODEC, null);
   private final Map<UUID, Long> lastNoticeTick = new HashMap<>();
   private long lastTickSave = -20L;

   private EnergyManager() {
   }

   public static EnergyManager get(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
   }

   private EnergyManager.PlayerState state(UUID id) {
      return this.players.computeIfAbsent(id, ignored -> {
         EnergyManager.PlayerState state = new EnergyManager.PlayerState();
         state.energy = YPConfig.getInstance().energy_max;
         return state;
      });
   }

   public double getEnergy(UUID playerId) {
      return this.clampEnergy(this.state(playerId).energy);
   }

   public int getBleedingTicks(UUID playerId) {
      return Math.max(0, this.state(playerId).bleedingTicks);
   }

   public int getOverdriveTicks(UUID playerId) {
      return Math.max(0, this.state(playerId).overdriveTicks);
   }

   public int getGoatCooldownTicks(UUID playerId) {
      return Math.max(0, this.state(playerId).overdriveCooldownTicks);
   }

   public double getGoatDamageDebt(UUID playerId) {
      return Math.max(0.0, this.state(playerId).goatDamageDebt);
   }

   public int getBlackHoleCooldownTicks(UUID playerId) {
      return Math.max(0, this.state(playerId).blackHoleCooldownTicks);
   }

   public int getRaigoCooldownTicks(UUID playerId) {
      return Math.max(0, this.state(playerId).raigoCooldownTicks);
   }

   public int getIceHeatBlastLockoutTicks(UUID playerId) {
      return Math.max(0, this.state(playerId).iceHeatBlastLockoutTicks);
   }

   public boolean isOverdriveActive(UUID playerId) {
      return this.getOverdriveTicks(playerId) > 0;
   }

   public void setEnergy(UUID playerId, double value) {
      EnergyManager.PlayerState state = this.state(playerId);
      state.energy = this.clampEnergy(value);
      this.markDirty();
   }

   public void clearCooldownsFor(UUID playerId) {
      EnergyManager.PlayerState state = this.state(playerId);
      boolean changed = false;
      if (state.overdriveCooldownTicks != 0) {
         state.overdriveCooldownTicks = 0;
         changed = true;
      }

      if (state.blackHoleCooldownTicks != 0) {
         state.blackHoleCooldownTicks = 0;
         changed = true;
      }

      if (state.raigoCooldownTicks != 0) {
         state.raigoCooldownTicks = 0;
         changed = true;
      }

      if (state.iceHeatBlastLockoutTicks != 0) {
         state.iceHeatBlastLockoutTicks = 0;
         changed = true;
      }

      if (changed) {
         this.markDirty();
      }
   }

   private double clampEnergy(double value) {
      return Math.max(0.0, Math.min(Math.max(1.0, YPConfig.getInstance().energy_max), value));
   }

   public EnergyManager.IceGuardSummonResult trySpendIceGuardSummonEnergy(ServerPlayerEntity player) {
      if (player == null || player.getEntityWorld().getServer() == null) {
         return EnergyManager.IceGuardSummonResult.DENIED;
      } else if (GraceManager.blockAbility(player)) {
         return EnergyManager.IceGuardSummonResult.DENIED;
      } else {
         EnergyManager.PlayerState state = this.state(player.getUuid());
         if (state.bleedingTicks > 0) {
            this.notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
            return EnergyManager.IceGuardSummonResult.DENIED;
         } else if (PowerManager.get(player.getEntityWorld().getServer()).getPowerType(player.getUuid()) == PowerType.ICE_EMPEROR
            && state.iceHeatBlastLockoutTicks > 0) {
            this.notice(player, cooldownText("Ice Emperor abilities", state.iceHeatBlastLockoutTicks), Formatting.YELLOW);
            return EnergyManager.IceGuardSummonResult.DENIED;
         } else {
            double cost = Math.max(0.0, YPConfig.getInstance().energy_cost_tertiary);
            if (state.energy + 1.0E-6 >= cost) {
               state.energy = this.clampEnergy(state.energy - cost);
               this.markDirty();
               this.sync(player);
               return EnergyManager.IceGuardSummonResult.FULL_STRENGTH;
            } else {
               state.energy = 0.0;
               this.markDirty();
               this.sync(player);
               this.notice(player, "Insufficient Energy! The Frost Guard answers, but your bears will be weaker and may turn hostile.", Formatting.YELLOW);
               return EnergyManager.IceGuardSummonResult.LOW_ENERGY;
            }
         }
      }
   }

   public boolean tryActivateIceHeatBlast(ServerPlayerEntity player) {
      if (player == null || player.isRemoved() || !player.isAlive() || player.getEntityWorld().getServer() == null) {
         return false;
      } else if (GraceManager.blockAbility(player)) {
         return false;
      } else {
         PowerManager powers = PowerManager.get(player.getEntityWorld().getServer());
         if (powers.getPowerType(player.getUuid()) == PowerType.ICE_EMPEROR && powers.isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            EnergyManager.PlayerState state = this.state(player.getUuid());
            if (state.bleedingTicks > 0) {
               this.notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
               return false;
            } else if (state.iceHeatBlastLockoutTicks > 0) {
               this.notice(player, cooldownText("Heat Blast lockout", state.iceHeatBlastLockoutTicks), Formatting.YELLOW);
               return false;
            } else {
               double minimum = YPConfig.getInstance().ice_emperor_heat_blast_min_energy;
               if (state.energy + 1.0E-6 < minimum) {
                  this.notice(player, "Insufficient Energy for Heat Blast. Requires " + (int)Math.ceil(minimum) + " Energy.", Formatting.RED);
                  return false;
               } else {
                  state.energy = 0.0;
                  state.iceHeatBlastLockoutTicks = 1200;
                  this.markDirty();
                  this.sync(player);
                  player.sendMessage(
                     Text.literal("HEAT BLAST — all remaining Energy consumed. Ice Emperor abilities locked for 60 seconds.").formatted(Formatting.GOLD), true
                  );
                  return true;
               }
            }
         } else {
            return false;
         }
      }
   }

   public boolean trySpendAbility(ServerPlayerEntity player, int keyType) {
      if (player == null || player.getEntityWorld().getServer() == null) {
         return false;
      } else if (GraceManager.blockAbility(player)) {
         return false;
      } else {
         EnergyManager.PlayerState state = this.state(player.getUuid());
         if (state.bleedingTicks > 0) {
            this.notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
            return false;
         } else {
            PowerManager powerManager = PowerManager.get(player.getEntityWorld().getServer());
            if (powerManager.getPowerType(player.getUuid()) == PowerType.ICE_EMPEROR && state.iceHeatBlastLockoutTicks > 0) {
               this.notice(player, cooldownText("Ice Emperor abilities", state.iceHeatBlastLockoutTicks), Formatting.YELLOW);
               return false;
            } else {
               PowerType power = powerManager.getPowerType(player.getUuid());
               YPConfig config = YPConfig.getInstance();
               double cost;
               if (keyType == 2 && power == PowerType.GOAT) {
                  if (state.overdriveTicks > 0 || state.overdriveCooldownTicks > 0) {
                     this.notice(
                        player,
                        state.overdriveTicks > 0 ? "GOAT Overdrive is already active." : cooldownText("GOAT Overdrive", state.overdriveCooldownTicks),
                        Formatting.YELLOW
                     );
                     return false;
                  }

                  cost = config.goat_overdrive_energy_cost;
               } else if (keyType == 2 && power == PowerType.TIME_WARPER) {
                  if (powerManager.getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower tw && tw.isBlackHoleCharging(player.getUuid())) {
                     this.notice(player, "A Black Hole is already charging.", Formatting.YELLOW);
                     return false;
                  }

                  if (state.blackHoleCooldownTicks > 0) {
                     this.notice(player, cooldownText("Black Hole", state.blackHoleCooldownTicks), Formatting.YELLOW);
                     return false;
                  }

                  cost = config.time_warper_black_hole_energy_cost;
               } else if (keyType == 4 && power == PowerType.BOOTS_SKY) {
                  if (powerManager.getPowerInstance(PowerType.BOOTS_SKY) instanceof BootsOfTheSkyPower sky && sky.isRaigoCharging(player.getUuid())) {
                     this.notice(player, "Raigo is already charging.", Formatting.YELLOW);
                     return false;
                  }

                  if (state.raigoCooldownTicks > 0) {
                     this.notice(player, cooldownText("Raigo", state.raigoCooldownTicks), Formatting.YELLOW);
                     return false;
                  }

                  cost = config.boots_raigo_energy_cost;
               } else if (power != PowerType.BOOTS_SKY || keyType != 0 && keyType != 5) {
                  if (keyType != 4 || power != PowerType.GOAT && power != PowerType.ICE_EMPEROR) {
                     if (power == PowerType.FALLOUT_WARRIOR && keyType == 0) {
                        cost = config.fallout_minion_energy_cost;
                     } else if (power == PowerType.FALLOUT_WARRIOR && keyType == 1) {
                        cost = config.fallout_pool_energy_cost;
                     } else if (power == PowerType.FALLOUT_WARRIOR && keyType == 4) {
                        cost = 0.0;
                     } else if (power == PowerType.FIRE_GOD && keyType == 1) {
                        cost = config.fire_god_blaze_cost_energy;
                     } else if (power == PowerType.FIRE_GOD && keyType == 2) {
                        cost = config.fire_god_wither_skeleton_cost_energy;
                     } else if (power == PowerType.SPIDEY && keyType == 0) {
                        cost = 0.0;
                     } else if (power == PowerType.SPIDEY && keyType == 1) {
                        cost = config.spidey_web_grenade_energy_cost;
                     } else if (power == PowerType.SPIDEY && keyType == 2) {
                        cost = config.spidey_rage_energy_cost;
                     } else if (power == PowerType.SPIDEY && keyType == 3) {
                        cost = powerManager.getPowerInstance(PowerType.SPIDEY) instanceof SpideyPower spider && spider.getDiveChain(player.getUuid()) > 0
                           ? 0.0
                           : config.spidey_dive_energy_cost;
                     } else if (power == PowerType.SPIDEY && keyType == 4) {
                        cost = 0.0;
                     } else if (power == PowerType.SPIRIT_CHANNELER && keyType == 0) {
                        cost = config.spirit_summon_energy_cost;
                     } else if (power == PowerType.SPIRIT_CHANNELER && keyType == 1) {
                        cost = config.spirit_shadow_step_energy_cost;
                     } else if (power != PowerType.SPIRIT_CHANNELER || keyType != 3 && keyType != 4) {
                        if (power == PowerType.GAMBIT && keyType == 2) {
                           cost = TeleportGambitRandomPortalManager.hasReturnGroup(player.getUuid()) ? 0.0 : config.teleport_gambit_random_portal_energy_cost;
                        } else if (power == PowerType.POSEIDON && keyType == 2) {
                           cost = 0.0;
                        } else if (power == PowerType.DIVINITY && keyType == 1) {
                           cost = 0.0;
                        } else if (power == PowerType.ILLUSIONIST && keyType == 2) {
                           cost = powerManager.getPowerInstance(PowerType.ILLUSIONIST) instanceof IllusionistPower illusionistPower
                                 && illusionistPower.isCloudFlightActive(player.getUuid())
                              ? 0.0
                              : config.illusionist_cloud_flight_energy_cost;
                        } else if (power != PowerType.ILLUSIONIST || keyType != 0 && keyType != 1 && keyType != 3 && keyType != 4) {
                           if (power == PowerType.KITSUNE && keyType == 0) {
                              cost = config.kitsune_claw_energy_cost;
                           } else if (power == PowerType.KITSUNE && keyType == 1) {
                              cost = config.kitsune_burst_energy_cost;
                           } else if (power == PowerType.KITSUNE && keyType == 2) {
                              cost = config.kitsune_pounce_energy_cost;
                           } else if (power == PowerType.PUNCH && keyType == 1) {
                              cost = config.serious_punch_helldive_energy_cost;
                           } else if (power == PowerType.KITSUNE && keyType == 3) {
                              cost = config.kitsune_detonation_energy_cost;
                           } else if (power == PowerType.KITSUNE && keyType == 4) {
                              cost = 0.0;
                           } else if (power == PowerType.TIME_WARPER && keyType == 4) {
                              cost = 0.0;
                           } else if (power == PowerType.TIME_WARPER
                              && keyType == 0
                              && powerManager.getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower tw
                              && tw.isRealityForm(player)) {
                              cost = config.time_warper_clone_energy_cost;
                           } else if (power == PowerType.TIME_WARPER && keyType == 3) {
                              cost = config.time_warper_elite_clone_energy_cost;
                           } else {
                              cost = switch (keyType) {
                                 case 0, 5 -> config.energy_cost_primary;
                                 case 1 -> config.energy_cost_secondary;
                                 case 2 -> config.energy_cost_tertiary;
                                 case 3 -> config.energy_cost_quaternary;
                                 case 4 -> config.energy_cost_alternate;
                                 default -> 0.0;
                              };
                           }
                        } else {
                           cost = 0.0;
                        }
                     } else {
                        cost = 0.0;
                     }
                  } else {
                     cost = 0.0;
                  }
               } else {
                  cost = 0.0;
               }

               cost = Math.max(0.0, cost);
               if (state.energy + 1.0E-6 < cost) {
                  if (state.bleedingTicks <= 0) {
                     state.bleedingTicks = Math.min(Math.max(1, config.energy_bleeding_max_ticks), Math.max(1, config.energy_bleeding_initial_ticks));
                     state.bleedDamageClock = 0;
                  }

                  this.notice(player, "Not enough Energy! You are Bleeding and cannot use abilities.", Formatting.RED);
                  this.markDirty();
                  this.sync(player);
                  return false;
               } else {
                  state.energy = this.clampEnergy(state.energy - cost);
                  this.markDirty();
                  this.sync(player);
                  return true;
               }
            }
         }
      }
   }

   public boolean trySpendAwakening(ServerPlayerEntity player, double requestedCost) {
      if (player == null || player.getEntityWorld().getServer() == null || !player.isAlive()) {
         return false;
      } else if (GraceManager.blockAbility(player)) {
         return false;
      } else {
         EnergyManager.PlayerState state = this.state(player.getUuid());
         if (state.bleedingTicks > 0) {
            this.notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
            return false;
         } else {
            double cost = Math.max(0.0, Double.isFinite(requestedCost) ? requestedCost : 0.0);
            if (state.energy + 1.0E-6 < cost) {
               this.notice(player, "Not enough Energy for this Awakening.", Formatting.RED);
               return false;
            } else {
               state.energy = this.clampEnergy(state.energy - cost);
               this.markDirty();
               this.sync(player);
               return true;
            }
         }
      }
   }

   public boolean trySpendFlightEnergy(ServerPlayerEntity player, double amount) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         double cost = Math.max(0.0, amount);
         if (cost <= 1.0E-9) {
            return true;
         } else {
            EnergyManager.PlayerState state = this.state(player.getUuid());
            if (state.energy + 1.0E-6 < cost) {
               return false;
            } else {
               state.energy = this.clampEnergy(state.energy - cost);
               this.markDirty();
               this.sync(player);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public boolean trySpendDirect(ServerPlayerEntity player, double amount, String abilityName) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         EnergyManager.PlayerState state = this.state(player.getUuid());
         if (GraceManager.blockAbility(player)) {
            return false;
         } else if (state.bleedingTicks > 0) {
            this.notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
            return false;
         } else {
            double cost = Math.max(0.0, amount);
            if (state.energy + 1.0E-6 < cost) {
               if (state.bleedingTicks <= 0) {
                  YPConfig config = YPConfig.getInstance();
                  state.bleedingTicks = Math.min(Math.max(1, config.energy_bleeding_max_ticks), Math.max(1, config.energy_bleeding_initial_ticks));
                  state.bleedDamageClock = 0;
               }

               this.notice(player, "Not enough Energy for " + abilityName + "! Bleeding prevents ability use.", Formatting.RED);
               this.markDirty();
               this.sync(player);
               return false;
            } else {
               state.energy = this.clampEnergy(state.energy - cost);
               this.markDirty();
               this.sync(player);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   public void startGoatOverdrive(ServerPlayerEntity player) {
      EnergyManager.PlayerState state = this.state(player.getUuid());
      YPConfig config = YPConfig.getInstance();
      state.overdriveTicks = Math.max(1, config.goat_overdrive_duration_ticks);
      state.overdriveCooldownTicks = Math.max(state.overdriveCooldownTicks, config.goat_overdrive_cooldown_ticks);
      if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT) instanceof GoatPower goatPower) {
         goatPower.applyOverdrive(player);
      }

      this.markDirty();
      this.sync(player);
   }

   public void startBlackHoleCooldown(ServerPlayerEntity player) {
      EnergyManager.PlayerState state = this.state(player.getUuid());
      state.blackHoleCooldownTicks = Math.max(state.blackHoleCooldownTicks, YPConfig.getInstance().time_warper_black_hole_cooldown_ticks);
      this.markDirty();
      this.sync(player);
   }

   public void startRaigoCooldown(ServerPlayerEntity player) {
      EnergyManager.PlayerState state = this.state(player.getUuid());
      state.raigoCooldownTicks = Math.max(state.raigoCooldownTicks, YPConfig.getInstance().boots_raigo_cooldown_ticks);
      this.markDirty();
      this.sync(player);
   }

   public double addGoatDamageDebt(ServerPlayerEntity player, double damage) {
      if (player != null && !(damage <= 0.0) && Double.isFinite(damage)) {
         EnergyManager.PlayerState state = this.state(player.getUuid());
         double cap = Math.max(1.0, YPConfig.getInstance().goat_overdrive_max_damage_debt);
         double deferred = Math.min(damage, Math.max(0.0, cap - state.goatDamageDebt));
         if (deferred > 0.0) {
            state.goatDamageDebt += deferred;
            this.markDirty();
         }

         return deferred;
      } else {
         return 0.0;
      }
   }

   public void endGoatOverdrive(ServerPlayerEntity player) {
      EnergyManager.PlayerState state = this.state(player.getUuid());
      if (state.overdriveTicks > 0) {
         state.overdriveTicks = 0;
      }

      if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT) instanceof GoatPower goatPower) {
         goatPower.removeOverdrive(player);
      }

      this.markDirty();
      this.sync(player);
   }

   public void onPlayerDeath(ServerPlayerEntity player) {
      EnergyManager.PlayerState state = this.state(player.getUuid());
      state.overdriveTicks = 0;
      if ((player.getEntityWorld().getServer() == null ? null : PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT)) instanceof GoatPower goatPower
         )
       {
         goatPower.removeOverdrive(player);
      }

      this.markDirty();
   }

   public void onPlayerDisconnect(ServerPlayerEntity player) {
      EnergyManager.PlayerState state = this.state(player.getUuid());
      state.overdriveTicks = 0;
      MinecraftServer server = player.getEntityWorld().getServer();
      if ((server == null ? null : PowerManager.get(server).getPowerInstance(PowerType.GOAT)) instanceof GoatPower goatPower) {
         goatPower.removeOverdrive(player);
      }

      this.markDirty();
   }

   public static void endOverdrivesForShutdown(MinecraftServer server) {
      EnergyManager manager = get(server);
      Power goat = PowerManager.get(server).getPowerInstance(PowerType.GOAT);
      boolean changed = false;

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         EnergyManager.PlayerState state = manager.state(player.getUuid());
         if (state.overdriveTicks > 0) {
            state.overdriveTicks = 0;
            if (goat instanceof GoatPower goatPower) {
               goatPower.removeOverdrive(player);
            }

            changed = true;
         }
      }

      if (changed) {
         manager.markDirty();
      }
   }

   public void onJoin(ServerPlayerEntity player) {
      this.applyPlayerOnlyState(player);
      this.sync(player);
   }

   private void applyPlayerOnlyState(ServerPlayerEntity player) {
      GlobalHealthManager.apply(player);
      EnergyManager.PlayerState state = this.state(player.getUuid());
      if ((player.getEntityWorld().getServer() == null ? null : PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT)) instanceof GoatPower goatPower
         )
       {
         if (state.overdriveTicks > 0) {
            goatPower.applyOverdrive(player);
         } else {
            goatPower.removeOverdrive(player);
         }
      }
   }

   public void sync(ServerPlayerEntity player) {
      if (player != null) {
         EnergyManager.PlayerState state = this.state(player.getUuid());
         YPNetworking.syncEnergy(
            player,
            this.clampEnergy(state.energy),
            Math.max(1.0, YPConfig.getInstance().energy_max),
            state.bleedingTicks,
            YPConfig.getInstance().global_health_enabled
         );
      }
   }

   private void notice(ServerPlayerEntity player, String message, Formatting formatting) {
      MinecraftServer server = player.getEntityWorld().getServer();
      long tick = server == null ? 0L : server.getTicks();
      Long last = this.lastNoticeTick.get(player.getUuid());
      if (last == null || tick - last >= 20L) {
         player.sendMessage(Text.literal(message).formatted(formatting), true);
         this.lastNoticeTick.put(player.getUuid(), tick);
      }
   }

   private static String cooldownText(String name, int ticks) {
      return name + " cooldown: " + (Math.max(0, ticks) + 19) / 20 + "s.";
   }

   public static void serverTick(MinecraftServer server) {
      EnergyManager manager = get(server);
      YPConfig config = YPConfig.getInstance();
      boolean changed = false;
      Iterator<Entry<UUID, EnergyManager.PlayerState>> entries = manager.players.entrySet().iterator();

      while (entries.hasNext()) {
         EnergyManager.PlayerState state = entries.next().getValue();
         if (state.overdriveCooldownTicks > 0) {
            state.overdriveCooldownTicks--;
            changed = true;
         }

         if (state.blackHoleCooldownTicks > 0) {
            state.blackHoleCooldownTicks--;
            changed = true;
         }

         if (state.raigoCooldownTicks > 0) {
            state.raigoCooldownTicks--;
            changed = true;
         }

         if (state.iceHeatBlastLockoutTicks > 0) {
            state.iceHeatBlastLockoutTicks--;
            changed = true;
         }
      }

      BootsOfTheSkyPower skyPower = PowerManager.get(server).getPowerInstance(PowerType.BOOTS_SKY) instanceof BootsOfTheSkyPower boots ? boots : null;

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         EnergyManager.PlayerState statex = manager.state(player.getUuid());
         if (statex.energy <= 1.0E-9) {
            IllusionistPower.onOwnerEnergyDepleted(player);
         }

         double previousEnergy = statex.energy;
         boolean activelyFlying = PowerManager.get(server).getPowerType(player.getUuid()) == PowerType.BOOTS_SKY
            && skyPower != null
            && skyPower.isFlightActive(player.getUuid())
            && player.getAbilities().flying;
         if (!activelyFlying) {
            statex.energy = manager.clampEnergy(statex.energy + Math.max(0.0, config.energy_regen_per_second) / 20.0);
         }

         if (Math.abs(statex.energy - previousEnergy) > 1.0E-7) {
            changed = true;
         }

         if (statex.overdriveTicks > 0) {
            Power goat = PowerManager.get(server).getPowerInstance(PowerType.GOAT);
            if (GraceManager.isActive(server)) {
               statex.overdriveTicks = 0;
               if (goat instanceof GoatPower goatPower) {
                  goatPower.removeOverdrive(player);
               }

               player.sendMessage(
                  Text.literal("GOAT Overdrive ended because the grace period began; deferred damage remains.").formatted(Formatting.YELLOW), true
               );
            } else {
               statex.overdriveTicks--;
               if (statex.overdriveTicks == 0) {
                  if (goat instanceof GoatPower goatPower) {
                     goatPower.removeOverdrive(player);
                  }

                  player.sendMessage(Text.literal("GOAT Overdrive ended. Deferred damage is catching up.").formatted(Formatting.GOLD), true);
               } else if (goat instanceof GoatPower goatPower) {
                  goatPower.tickOverdrive(player);
               }
            }

            changed = true;
         }

         if (statex.bleedingTicks > 0) {
            statex.bleedingTicks--;
            statex.bleedDamageClock++;
            changed = true;
            if (statex.bleedDamageClock >= 20) {
               ServerWorld var20 = player.getEntityWorld();
               if (var20 instanceof ServerWorld) {
                  statex.bleedDamageClock = 0;
                  player.damage(var20, var20.getDamageSources().magic(), (float)Math.max(0.0, config.energy_bleeding_damage_per_second));
               }
            }
         }

         if (statex.overdriveTicks <= 0
            && statex.goatDamageDebt > 0.0
            && player.isAlive()
            && server.getTicks() % Math.max(1, config.goat_overdrive_debt_release_interval_ticks) == 0) {
            ServerWorld var21 = player.getEntityWorld();
            if (var21 instanceof ServerWorld) {
               double release = Math.min(statex.goatDamageDebt, Math.max(0.1, config.goat_overdrive_debt_release_per_hit));
               statex.goatDamageDebt = Math.max(0.0, statex.goatDamageDebt - release);
               changed = true;
               player.damage(var21, var21.getDamageSources().magic(), (float)release);
            }
         }

         if (server.getTicks() % 5 == 0) {
            manager.sync(player);
         }
      }

      if (changed && server.getTicks() - manager.lastTickSave >= 20L) {
         manager.lastTickSave = server.getTicks();
         manager.markDirty();
      }
   }

   private NbtCompound toNbt() {
      NbtCompound root = new NbtCompound();
      NbtCompound all = new NbtCompound();

      for (Entry<UUID, EnergyManager.PlayerState> entry : this.players.entrySet()) {
         EnergyManager.PlayerState s = entry.getValue();
         NbtCompound n = new NbtCompound();
         n.putDouble("Energy", s.energy);
         n.putInt("BleedingTicks", Math.max(0, s.bleedingTicks));
         n.putInt("BleedDamageClock", Math.max(0, s.bleedDamageClock));
         n.putInt("OverdriveTicks", Math.max(0, s.overdriveTicks));
         n.putInt("OverdriveCooldownTicks", Math.max(0, s.overdriveCooldownTicks));
         n.putDouble("GoatDamageDebt", Math.max(0.0, s.goatDamageDebt));
         n.putInt("BlackHoleCooldownTicks", Math.max(0, s.blackHoleCooldownTicks));
         n.putInt("RaigoCooldownTicks", Math.max(0, s.raigoCooldownTicks));
         n.putInt("IceHeatBlastLockoutTicks", Math.max(0, s.iceHeatBlastLockoutTicks));
         all.put(entry.getKey().toString(), n);
      }

      root.put("Players", all);
      return root;
   }

   public static EnergyManager fromNbt(NbtCompound root) {
      EnergyManager manager = new EnergyManager();
      NbtCompound all = root.getCompoundOrEmpty("Players");

      for (String key : all.getKeys()) {
         try {
            UUID id = UUID.fromString(key);
            NbtCompound n = all.getCompoundOrEmpty(key);
            EnergyManager.PlayerState s = new EnergyManager.PlayerState();
            s.energy = Math.max(0.0, Math.min(100000.0, n.getDouble("Energy", 100.0)));
            s.bleedingTicks = Math.max(0, n.getInt("BleedingTicks", 0));
            s.bleedDamageClock = Math.max(0, Math.min(19, n.getInt("BleedDamageClock", 0)));
            s.overdriveTicks = Math.max(0, n.getInt("OverdriveTicks", 0));
            s.overdriveCooldownTicks = Math.max(0, n.getInt("OverdriveCooldownTicks", 0));
            s.goatDamageDebt = Math.max(0.0, Math.min(10000.0, n.getDouble("GoatDamageDebt", 0.0)));
            s.blackHoleCooldownTicks = Math.max(0, n.getInt("BlackHoleCooldownTicks", 0));
            s.raigoCooldownTicks = Math.max(0, n.getInt("RaigoCooldownTicks", 0));
            s.iceHeatBlastLockoutTicks = Math.max(0, Math.min(1200, n.getInt("IceHeatBlastLockoutTicks", 0)));
            manager.players.put(id, s);
         } catch (IllegalArgumentException var8) {
         }
      }

      return manager;
   }

   public static enum IceGuardSummonResult {
      FULL_STRENGTH,
      LOW_ENERGY,
      DENIED;
   }

   private static final class PlayerState {
      double energy = 100.0;
      int bleedingTicks;
      int overdriveTicks;
      int overdriveCooldownTicks;
      double goatDamageDebt;
      int blackHoleCooldownTicks;
      int raigoCooldownTicks;
      int iceHeatBlastLockoutTicks;
      int bleedDamageClock;
   }
}
