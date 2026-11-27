package com.yppowers.power.impl;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.command.TestMode;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.GrimoireOfLightManager;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.RitualManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import net.minecraft.world.gen.structure.Structure;

public final class AwakeningManager {
   private static final int MAX_SHADOW_MINIONS_PER_SCAN = 16;
   private static final int SHADOW_SCAN_INTERVAL_TICKS = 20;
   private static final Map<UUID, Integer> shadowScanClocks = new HashMap<>();
   private static final Set<UUID> shadowAwakeUsers = new HashSet<>();
   private static final Map<UUID, Integer> punchCooldowns = new HashMap<>();
   private static final Map<UUID, Integer> falloutCooldowns = new HashMap<>();
   private static final Map<UUID, Integer> poseidonCooldowns = new HashMap<>();
   private static final Map<UUID, Integer> timeDomainCooldowns = new HashMap<>();
   private static final Map<UUID, AwakeningManager.PendingPoseidonConfirmation> poseidonConfirmations = new HashMap<>();
   private static final Map<UUID, AwakeningManager.PendingDestructiveConfirmation> destructiveConfirmations = new HashMap<>();
   private static final Map<UUID, Long> goatRamConfirmedUntil = new HashMap<>();
   private static final Map<UUID, AwakeningManager.PoseidonSinkTask> poseidonSinkTasks = new HashMap<>();
   private static final Set<UUID> witherForms = new HashSet<>();
   private static final Map<UUID, AwakeningManager.FlightSnapshot> witherFlightSnapshots = new HashMap<>();
   private static final String OWNER_PREFIX = "yppowers_owner_";
   private static final String FALLOUT_TAG = "yppowers_fallout_minion";
   private static final String FALLOUT_BOOST_TAG = "yppowers_fallout_awakening_boosted";
   private static final Codec<AwakeningManager.GambitCooldownState> GAMBIT_COOLDOWN_CODEC = Codec.PASSTHROUGH.xmap(dynamic -> {
      NbtElement element = (NbtElement)dynamic.convert(NbtOps.INSTANCE).getValue();
      return AwakeningManager.GambitCooldownState.fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
   }, state -> new Dynamic(NbtOps.INSTANCE, state.toNbt()));
   private static final PersistentStateType<AwakeningManager.GambitCooldownState> GAMBIT_COOLDOWN_TYPE = new PersistentStateType<>(
      "yppowers_teleport_gambit_awakening_cooldowns", AwakeningManager.GambitCooldownState::new, GAMBIT_COOLDOWN_CODEC, null
   );

   private AwakeningManager() {
   }

   public static boolean isAwakened(PowerManager manager, UUID playerId, PowerType power) {
      return manager != null && playerId != null && power != null && RitualManager.isAwakeningUnlocked(manager, playerId, power);
   }

   private static boolean ensureAwakeningUnlocked(ServerPlayerEntity player, PowerType power) {
      if (player != null && power != null && player.getEntityWorld().getServer() != null) {
         UUID playerId = player.getUuid();
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (!RitualManager.isComplete(manager, playerId, power)) {
            player.sendMessage(Text.literal("Complete your " + power.getDisplayName() + " ritual to unlock its Awakening.").formatted(Formatting.YELLOW), true);
            return false;
         } else if (isAwakened(manager, playerId, power)) {
            return true;
         } else if (!GrimoireOfLightManager.hasGrimoire(player)) {
            player.sendMessage(
               Text.literal("Carry the Grimoire of Light and activate this Awakening once to unlock it permanently.").formatted(Formatting.GOLD), true
            );
            return false;
         } else {
            RitualManager.unlockAwakening(manager, playerId, power);
            player.sendMessage(
               Text.literal(power.getDisplayName() + " Awakening permanently unlocked. You no longer need to carry the Grimoire for its moves.")
                  .formatted(Formatting.AQUA),
               false
            );
            return true;
         }
      } else {
         return false;
      }
   }

   public static void tick(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         UUID id = player.getUuid();
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         PowerType type = manager.getAssignedPowerType(id);
         decrement(punchCooldowns, id);
         decrement(falloutCooldowns, id);
         decrement(poseidonCooldowns, id);
         decrement(timeDomainCooldowns, id);
         Long goatArmedUntil = goatRamConfirmedUntil.get(id);
         if (goatArmedUntil != null && goatArmedUntil <= player.getEntityWorld().getServer().getTicks()) {
            goatRamConfirmedUntil.remove(id);
         }

         AwakeningManager.PendingDestructiveConfirmation pendingDestructive = destructiveConfirmations.get(id);
         if (pendingDestructive != null) {
            if (player.isAlive() && pendingDestructive.ticksRemaining() > 1 && player.getEntityWorld().getRegistryKey().equals(pendingDestructive.worldKey())) {
               destructiveConfirmations.put(
                  id,
                  new AwakeningManager.PendingDestructiveConfirmation(
                     pendingDestructive.worldKey(), pendingDestructive.action(), pendingDestructive.ticksRemaining() - 1
                  )
               );
            } else {
               destructiveConfirmations.remove(id);
            }
         }

         AwakeningManager.PendingPoseidonConfirmation pendingConfirmation = poseidonConfirmations.get(id);
         if (pendingConfirmation != null) {
            if (player.isAlive() && player.getEntityWorld().getRegistryKey().equals(pendingConfirmation.worldKey()) && pendingConfirmation.ticksRemaining() > 1
               )
             {
               poseidonConfirmations.put(
                  id, new AwakeningManager.PendingPoseidonConfirmation(pendingConfirmation.worldKey(), pendingConfirmation.ticksRemaining() - 1)
               );
            } else {
               poseidonConfirmations.remove(id);
            }
         }

         AwakeningManager.PoseidonSinkTask sinkTask = poseidonSinkTasks.get(id);
         if (sinkTask != null) {
            if (player.isAlive()
               && type == PowerType.POSEIDON
               && manager.isPowerEnabled(player, PowerType.POSEIDON)
               && isAwakened(manager, id, PowerType.POSEIDON)
               && player.getEntityWorld() == sinkTask.world
               && !GraceManager.isActive(player.getEntityWorld().getServer())) {
               tickPoseidonSink(player, sinkTask);
            } else {
               poseidonSinkTasks.remove(id);
               if (player.isAlive()) {
                  player.sendMessage(Text.literal("Poseidon's terrain stripping stopped."), true);
               }
            }
         }

         if (type == PowerType.SPIDEY && isAwakened(manager, id, type) && manager.isPowerEnabled(player, type)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 1, false, false, true));
            if (player.horizontalCollision && player.isSprinting() && !player.isTouchingWater()) {
               Vec3d velocity = player.getVelocity();
               double climb = Math.max(0.34, YPConfig.getInstance().spidey_awakening_climb_velocity);
               player.setVelocity(velocity.x, Math.min(climb, Math.max(velocity.y, climb)), velocity.z);
               if (player.age % 6 == 0) {
                  ServerWorld var12 = player.getEntityWorld();
                  if (var12 instanceof ServerWorld) {
                     var12.spawnParticles(ParticleTypes.CRIT, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 3, 0.18, 0.2, 0.18, 0.02);
                  }
               }
            }
         }

         if (type == PowerType.SHADOW_ARCHER && isAwakened(manager, id, type) && manager.isPowerEnabled(player, type)) {
            shadowAwakeUsers.add(id);
            int clock = shadowScanClocks.getOrDefault(id, 0) + 1;
            if (clock >= 20) {
               shadowScanClocks.put(id, 0);
               convertNearbyMobs(player);
            } else {
               shadowScanClocks.put(id, clock);
            }
         } else {
            shadowScanClocks.remove(id);
            if (shadowAwakeUsers.remove(id)) {
               releaseShadowMinions(player);
            }
         }

         if (type == PowerType.FALLOUT_WARRIOR && isAwakened(manager, id, type) && manager.isPowerEnabled(player, type) && player.age % 20 == 0) {
            ServerWorld var14 = player.getEntityWorld();
            if (var14 instanceof ServerWorld) {
               strengthenNearbyFalloutMinions(player, var14);
            }
         }

         if (type == PowerType.FIRE_GOD && witherForms.contains(id) && manager.isPowerEnabled(player, type) && isAwakened(manager, id, type)) {
            maintainWitherForm(player);
         } else if (witherForms.contains(id)) {
            endWitherForm(player);
         }
      }
   }

   public static void activate(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isAlive()) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         PowerType type = manager.getAssignedPowerType(player.getUuid());
         if (type != null && manager.isPowerEnabled(player, type)) {
            if (type != PowerType.TIME_WARPER || !TimeWarperDomainManager.closeByOwner(player)) {
               if (!RitualManager.isComplete(manager, player.getUuid(), type)) {
                  player.sendMessage(
                     Text.literal("Complete your " + type.getDisplayName() + " ritual to unlock its Awakening.").formatted(Formatting.YELLOW), true
                  );
               } else if (!GraceManager.blockAbility(player) || type == PowerType.FIRE_GOD && witherForms.contains(player.getUuid())) {
                  if (ensureAwakeningUnlocked(player, type)) {
                     switch (type) {
                        case PUNCH:
                           requestDestructiveConfirmation(player, AwakeningManager.DestructiveAction.OMNI_PUNCH);
                           break;
                        case SHADOW_ARCHER:
                           mountLookedAtMob(player);
                           break;
                        case FALLOUT_WARRIOR:
                           toxicBomb(player);
                           break;
                        case FIRE_GOD:
                           toggleWitherForm(player);
                           break;
                        case SPIDEY:
                           player.sendMessage(
                              Text.literal("Spider Awakening is passive: Regeneration II and rapid wall climbing are active.")
                                 .formatted(Formatting.LIGHT_PURPLE),
                              true
                           );
                           break;
                        case POSEIDON:
                           requestPoseidonConfirmation(player);
                           break;
                        case TIME_WARPER:
                           toggleTimeWarperDomain(player);
                           break;
                        case GOAT:
                           player.sendMessage(
                              Text.literal("GOAT Awakening is passive: after holding max speed, charge through eligible blocks.").formatted(Formatting.GOLD),
                              true
                           );
                           break;
                        case GAMBIT:
                           openGambitStructureSelector(player);
                           break;
                        case THOR:
                           player.sendMessage(Text.literal("This Awakening has not been implemented yet.").formatted(Formatting.GRAY), true);
                           break;
                        default:
                           player.sendMessage(Text.literal("No Awakening is defined for this power yet.").formatted(Formatting.GRAY), true);
                     }
                  }
               }
            }
         }
      }
   }

   private static void toggleTimeWarperDomain(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (!TimeWarperDomainManager.closeByOwner(player)) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getAssignedPowerType(id) == PowerType.TIME_WARPER
            && manager.isPowerEnabled(player, PowerType.TIME_WARPER)
            && isAwakened(manager, id, PowerType.TIME_WARPER)) {
            if (!GraceManager.blockAbility(player)) {
               int cooldown = timeDomainCooldowns.getOrDefault(id, 0);
               if (!TestMode.isEnabled(id) && cooldown > 0) {
                  player.sendMessage(Text.literal("Time Domain recharging: " + (cooldown + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
               } else {
                  YPConfig config = YPConfig.getInstance();
                  EnergyManager energy = EnergyManager.get(player.getEntityWorld().getServer());
                  double previousEnergy = energy.getEnergy(id);
                  if (energy.trySpendAwakening(player, config.time_warper_domain_energy_cost)) {
                     if (!TimeWarperDomainManager.open(player, config.time_warper_domain_capture_radius)) {
                        energy.setEnergy(id, previousEnergy);
                     } else {
                        if (!TestMode.isEnabled(id)) {
                           timeDomainCooldowns.put(id, Math.max(0, config.time_warper_domain_cooldown_ticks));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void activateChunkDupingDomain(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isAlive()) {
         UUID id = player.getUuid();
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getAssignedPowerType(id) != PowerType.TIME_WARPER || !manager.isPowerEnabled(player, PowerType.TIME_WARPER)) {
            player.sendMessage(Text.literal("Chunk Duping requires the Time Warper Awakening."), true);
         } else if (!GraceManager.blockAbility(player)) {
            if (ensureAwakeningUnlocked(player, PowerType.TIME_WARPER)) {
               if (TimeWarperDomainManager.hasActiveSession(id)) {
                  player.sendMessage(Text.literal("Use P to return from your active Time Domain before opening another mode."), true);
               } else {
                  int cooldown = timeDomainCooldowns.getOrDefault(id, 0);
                  if (!TestMode.isEnabled(id) && cooldown > 0) {
                     player.sendMessage(Text.literal("Time Domain recharging: " + (cooldown + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
                  } else {
                     YPConfig config = YPConfig.getInstance();
                     EnergyManager energy = EnergyManager.get(player.getEntityWorld().getServer());
                     double previousEnergy = energy.getEnergy(id);
                     if (energy.trySpendAwakening(player, config.time_warper_domain_energy_cost)) {
                        if (!TimeWarperDomainManager.openChunkDuping(player, config.time_warper_domain_capture_radius)) {
                           energy.setEnergy(id, previousEnergy);
                        } else {
                           if (!TestMode.isEnabled(id)) {
                              timeDomainCooldowns.put(id, Math.max(0, config.time_warper_domain_cooldown_ticks));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static void swapPortableChunk(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isAlive()) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getAssignedPowerType(player.getUuid()) != PowerType.TIME_WARPER || !manager.isPowerEnabled(player, PowerType.TIME_WARPER)) {
            player.sendMessage(Text.literal("Portable chunk swapping requires the Time Warper Awakening."), true);
         } else if (!GraceManager.blockAbility(player)) {
            if (ensureAwakeningUnlocked(player, PowerType.TIME_WARPER)) {
               TimeWarperDomainManager.swapPortableChunk(player);
            }
         }
      }
   }

   private static void requestDestructiveConfirmation(ServerPlayerEntity player, AwakeningManager.DestructiveAction action) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isAlive()) {
         UUID id = player.getUuid();
         AwakeningManager.PendingDestructiveConfirmation current = destructiveConfirmations.get(id);
         if (current == null || current.action() != action || current.ticksRemaining() <= 0) {
            destructiveConfirmations.put(id, new AwakeningManager.PendingDestructiveConfirmation(player.getEntityWorld().getRegistryKey(), action, 600));

            String ability = switch (action) {
               case OMNI_PUNCH -> "Omni-Directional Punch";
               case BLACK_HOLE -> "Black Hole terrain destruction";
               case GOAT_RAM -> "GOAT terrain ram";
            };
            player.sendMessage(
               Text.literal(ability + " can destroy terrain. Press Confirm Terrain Action (default F6) to proceed.").formatted(Formatting.GOLD), true
            );
         }
      }
   }

   public static void requestBlackHoleTerrainConfirmation(ServerPlayerEntity player) {
      requestDestructiveConfirmation(player, AwakeningManager.DestructiveAction.BLACK_HOLE);
   }

   public static void requestGoatRamConfirmation(ServerPlayerEntity player) {
      requestDestructiveConfirmation(player, AwakeningManager.DestructiveAction.GOAT_RAM);
   }

   public static boolean isGoatRamConfirmed(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         Long until = goatRamConfirmedUntil.get(player.getUuid());
         return until != null && until > player.getEntityWorld().getServer().getTicks();
      } else {
         return false;
      }
   }

   public static void confirmDestructiveAwakening(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isAlive()) {
         AwakeningManager.PendingDestructiveConfirmation pending = destructiveConfirmations.remove(player.getUuid());
         if (pending != null && pending.ticksRemaining() > 0 && pending.worldKey().equals(player.getEntityWorld().getRegistryKey())) {
            MinecraftServer server = player.getEntityWorld().getServer();
            PowerManager manager = PowerManager.get(server);

            PowerType expected = switch (pending.action()) {
               case OMNI_PUNCH -> PowerType.PUNCH;
               case BLACK_HOLE -> PowerType.TIME_WARPER;
               case GOAT_RAM -> PowerType.GOAT;
            };
            if (manager.getAssignedPowerType(player.getUuid()) == expected
               && manager.isPowerEnabled(player, expected)
               && !GraceManager.blockAbility(player)
               && (pending.action() == AwakeningManager.DestructiveAction.BLACK_HOLE || isAwakened(manager, player.getUuid(), expected))) {
               switch (pending.action()) {
                  case OMNI_PUNCH:
                     omniDirectionalPunch(player);
                     break;
                  case BLACK_HOLE:
                     TimeWarperPower.confirmBlackHoleTerrain(player);
                     break;
                  case GOAT_RAM:
                     goatRamConfirmedUntil.put(player.getUuid(), server.getTicks() + 200L);
                     player.sendMessage(
                        Text.literal("GOAT terrain ram confirmed for 10 seconds; your next charged impacts may break eligible blocks.")
                           .formatted(Formatting.GOLD),
                        true
                     );
               }
            } else {
               player.sendMessage(Text.literal("The destructive Awakening is no longer available.").formatted(Formatting.YELLOW), true);
            }
         } else {
            player.sendMessage(Text.literal("No pending destructive action to confirm.").formatted(Formatting.YELLOW), true);
         }
      }
   }

   public static void requestPoseidonConfirmation(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         poseidonConfirmations.put(player.getUuid(), new AwakeningManager.PendingPoseidonConfirmation(player.getEntityWorld().getRegistryKey(), 600));
         int radius = Math.max(64, YPConfig.getInstance().poseidon_awakening_sink_radius);
         int loadedChunks = 0;
         ServerWorld var4 = player.getEntityWorld();
         if (var4 instanceof ServerWorld && var4.getRegistryKey().equals(World.OVERWORLD)) {
            loadedChunks = snapshotLoadedChunks(var4, player.getBlockX(), player.getBlockZ(), radius).size();
         }

         YPNetworking.openPoseidonConfirmation(player, radius, loadedChunks);
      }
   }

   public static void confirmPoseidonSink(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isAlive()) {
         AwakeningManager.PendingPoseidonConfirmation confirmation = poseidonConfirmations.remove(player.getUuid());
         if (confirmation != null && confirmation.ticksRemaining() > 0 && confirmation.worldKey().equals(player.getEntityWorld().getRegistryKey())) {
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            if (manager.getAssignedPowerType(player.getUuid()) == PowerType.POSEIDON
               && manager.isPowerEnabled(player, PowerType.POSEIDON)
               && isAwakened(manager, player.getUuid(), PowerType.POSEIDON)
               && !GraceManager.blockAbility(player)) {
               startPoseidonSink(player);
            } else {
               player.sendMessage(Text.literal("Poseidon's terrain stripping is no longer available.").formatted(Formatting.YELLOW), true);
            }
         } else {
            player.sendMessage(Text.literal("Poseidon's confirmation expired; press P to request it again.").formatted(Formatting.YELLOW), true);
         }
      }
   }

   public static int cooldownTicks(ServerPlayerEntity player, PowerType type) {
      if (player != null && type != null) {
         UUID playerId = player.getUuid();

         return switch (type) {
            case PUNCH -> Math.max(0, punchCooldowns.getOrDefault(playerId, 0));
            default -> 0;
            case FALLOUT_WARRIOR -> Math.max(0, falloutCooldowns.getOrDefault(playerId, 0));
            case POSEIDON -> Math.max(0, poseidonCooldowns.getOrDefault(playerId, 0));
            case TIME_WARPER -> Math.max(0, timeDomainCooldowns.getOrDefault(playerId, 0));
            case GAMBIT -> remainingGambitCooldown(player.getEntityWorld().getServer(), playerId);
         };
      } else {
         return 0;
      }
   }

   private static void openGambitStructureSelector(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      int cooldown = remainingGambitCooldown(player.getEntityWorld().getServer(), id);
      if (!TestMode.isEnabled(id) && cooldown > 0) {
         player.sendMessage(Text.literal("Teleport Gambit Awakening recharging: " + formatCooldown(cooldown)).formatted(Formatting.YELLOW), true);
      } else {
         ServerWorld config = player.getEntityWorld();
         if (config instanceof ServerWorld) {
            YPConfig configx = YPConfig.getInstance();
            Set blacklist = Set.copyOf(configx.teleport_gambit_structure_blacklist);
            Registry<?> registry = config.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            StringBuilder encoded = new StringBuilder();

            for (String structureId : registry.getKeys()
               .stream()
               .map(key -> key.getValue().toString())
               .filter(value -> !blacklist.contains(value))
               .sorted()
               .toList()) {
               int extra = structureId.length() + (encoded.isEmpty() ? 0 : 1);
               if (encoded.length() + extra > 30000) {
                  break;
               }

               if (!encoded.isEmpty()) {
                  encoded.append('\n');
               }

               encoded.append(structureId);
            }

            YPNetworking.openTeleportGambitStructureMenu(player, encoded.toString(), cooldown);
         }
      }
   }

   public static void teleportToStructure(ServerPlayerEntity player, String requestedStructureId) {
      if (player != null && player.isAlive() && player.getEntityWorld().getServer() != null) {
         ServerWorld id = player.getEntityWorld();
         if (id instanceof ServerWorld) {
            UUID var16 = player.getUuid();
            PowerManager manager = PowerManager.get(id.getServer());
            if (manager.getAssignedPowerType(var16) == PowerType.GAMBIT
               && manager.isPowerEnabled(player, PowerType.GAMBIT)
               && !GraceManager.blockAbility(player)) {
               if (!ensureAwakeningUnlocked(player, PowerType.GAMBIT)) {
                  return;
               }

               int cooldown = remainingGambitCooldown(id.getServer(), var16);
               if (!TestMode.isEnabled(var16) && cooldown > 0) {
                  player.sendMessage(Text.literal("Teleport Gambit Awakening recharging: " + formatCooldown(cooldown)).formatted(Formatting.YELLOW), true);
                  return;
               }

               Identifier structureId = requestedStructureId == null ? null : Identifier.tryParse(requestedStructureId.trim());
               if (structureId == null) {
                  player.sendMessage(Text.literal("That structure ID is invalid."), true);
                  return;
               }

               YPConfig config = YPConfig.getInstance();
               if (config.teleport_gambit_structure_blacklist.contains(structureId.toString())) {
                  player.sendMessage(Text.literal("That structure is blacklisted by this server."), true);
                  return;
               }

               Registry<Structure> registry = id.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
               RegistryEntry<Structure> structure = registry.getEntry(structureId).orElse(null);
               if (structure == null) {
                  player.sendMessage(Text.literal("That structure is not registered on this server."), true);
                  return;
               }

               int radius = Math.max(8, config.teleport_gambit_structure_search_radius_chunks);
               Pair<BlockPos, RegistryEntry<Structure>> result = id.getChunkManager()
                  .getChunkGenerator()
                  .locateStructure(id, RegistryEntryList.of(structure), player.getBlockPos(), radius, false);
               if (result != null && result.getFirst() != null) {
                  BlockPos safeArrival = findSafeStructureArrival(id, (BlockPos)result.getFirst());
                  if (safeArrival == null) {
                     player.sendMessage(Text.literal("A safe arrival spot could not be found near that structure."), true);
                     return;
                  }

                  EnergyManager energy = EnergyManager.get(id.getServer());
                  double previousEnergy = energy.getEnergy(var16);
                  if (!energy.trySpendAwakening(player, config.teleport_gambit_awakening_energy_cost)) {
                     return;
                  }

                  if (!player.teleport(
                     id, safeArrival.getX() + 0.5, safeArrival.getY(), safeArrival.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch(), false
                  )) {
                     energy.setEnergy(var16, previousEnergy);
                     player.sendMessage(Text.literal("Teleport failed; no Energy or cooldown was consumed."), true);
                     return;
                  }

                  if (!TestMode.isEnabled(var16)) {
                     storeGambitCooldown(id.getServer(), var16, config.teleport_gambit_awakening_cooldown_ticks);
                  }

                  id.playSound(null, safeArrival, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.8F);
                  id.spawnParticles(ParticleTypes.PORTAL, true, true, safeArrival.getX() + 0.5, safeArrival.getY() + 0.8, safeArrival.getZ() + 0.5, 48, 0.5, 0.8, 0.5, 0.15);
                  player.sendMessage(
                     Text.literal("Teleport Gambit — arrived near " + structureId + ".").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true
                  );
                  return;
               }

               player.sendMessage(Text.literal("No " + structureId + " was found within " + radius * 16 + " blocks in this dimension."), true);
               return;
            }

            player.sendMessage(Text.literal("Teleport Gambit's Awakening is no longer available.").formatted(Formatting.YELLOW), true);
            return;
         }
      }
   }

   private static BlockPos findSafeStructureArrival(ServerWorld world, BlockPos structurePos) {
      for (int radius = 0; radius <= 8; radius++) {
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                  int x = structurePos.getX() + dx;
                  int z = structurePos.getZ() + dz;
                  BlockPos feet = world.getTopPosition(Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, world.getBottomY(), z));
                  int y = feet.getY();
                  if (y > world.getBottomY() && y + 1 < world.getBottomY() + world.getHeight()) {
                     BlockPos head = feet.up();
                     BlockPos floor = feet.down();
                     BlockState feetState = world.getBlockState(feet);
                     BlockState headState = world.getBlockState(head);
                     BlockState floorState = world.getBlockState(floor);
                     boolean safeFloor = floorState.isSolidBlock(world, floor) || floorState.getFluidState().isIn(FluidTags.WATER);
                     if (safeFloor
                        && feetState.isAir()
                        && headState.isAir()
                        && !isHazardousArrivalBlock(floorState)
                        && !isHazardousArrivalBlock(feetState)
                        && !isHazardousArrivalBlock(headState)) {
                        return feet.toImmutable();
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   private static boolean isHazardousArrivalBlock(BlockState state) {
      return state.isOf(Blocks.MAGMA_BLOCK)
         || state.isOf(Blocks.CACTUS)
         || state.isOf(Blocks.CAMPFIRE)
         || state.isOf(Blocks.SOUL_CAMPFIRE)
         || state.isOf(Blocks.SWEET_BERRY_BUSH)
         || state.isOf(Blocks.POWDER_SNOW);
   }

   private static String formatCooldown(int ticks) {
      long totalMinutes = Math.max(0, ticks) / 1200L;
      long days = totalMinutes / 1440L;
      long hours = totalMinutes % 1440L / 60L;
      long minutes = totalMinutes % 60L;
      return days > 0L ? days + "d " + hours + "h" : hours + "h " + minutes + "m";
   }

   private static AwakeningManager.GambitCooldownState gambitCooldownState(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(GAMBIT_COOLDOWN_TYPE);
   }

   private static int remainingGambitCooldown(MinecraftServer server, UUID playerId) {
      if (server != null && playerId != null) {
         AwakeningManager.GambitCooldownState state = gambitCooldownState(server);
         Long readyAt = state.readyAtGameTick.get(playerId);
         if (readyAt == null) {
            return 0;
         } else {
            long remaining = readyAt - server.getOverworld().getTime();
            if (remaining <= 0L) {
               state.readyAtGameTick.remove(playerId);
               state.markDirty();
               return 0;
            } else {
               return (int)Math.min(2147483647L, remaining);
            }
         }
      } else {
         return 0;
      }
   }

   private static void storeGambitCooldown(MinecraftServer server, UUID playerId, int cooldownTicks) {
      if (server != null && playerId != null && cooldownTicks > 0) {
         AwakeningManager.GambitCooldownState state = gambitCooldownState(server);
         state.readyAtGameTick.put(playerId, server.getOverworld().getTime() + cooldownTicks);
         state.markDirty();
         server.getOverworld().getPersistentStateManager().save();
      }
   }

   private static void omniDirectionalPunch(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      int cooldown = punchCooldowns.getOrDefault(id, 0);
      if (!TestMode.isEnabled(id) && cooldown > 0) {
         player.sendMessage(Text.literal("Omni-Directional Punch recharging: " + (cooldown + 19) / 20 + "s").formatted(Formatting.RED), true);
      } else {
         YPConfig cfg = YPConfig.getInstance();
         if (EnergyManager.get(player.getEntityWorld().getServer()).trySpendAwakening(player, cfg.awakening_punch_energy_cost)) {
            ServerWorld radius = player.getEntityWorld();
            if (radius instanceof ServerWorld) {
               ServerWorld world = radius;
               double var18 = Math.max(2.0, cfg.awakening_punch_radius);
               float damage = (float)Math.max(0.0, cfg.awakening_punch_damage);
               Box area = player.getBoundingBox().expand(var18);
               AllianceManager alliances = AllianceManager.get(radius.getServer());
               int hits = 0;

               for (LivingEntity target : radius.getEntitiesByClass(
                  LivingEntity.class, area, entity -> entity != player && entity.isAlive() && !entity.isRemoved()
               )) {
                  Vec3d offset = target.getBoundingBox().getCenter().subtract(player.getEntityPos());
                  double distance = offset.length();
                  if (!(distance > var18)
                     && PowerCombatUtil.canHarm(player, target)
                     && !(target instanceof ServerPlayerEntity other && alliances.isAlly(player, other))) {
                     if (damage > 0.0F) {
                        target.damage(world, world.getDamageSources().playerAttack(player), damage);
                     }

                     Vec3d direction = distance < 0.01 ? new Vec3d(0.0, 0.0, 1.0) : offset.normalize();
                     target.setVelocity(direction.multiply(1.35).add(0.0, 0.5, 0.0));
                     hits++;
                  }
               }

               int maxBlocks = Math.max(0, cfg.awakening_punch_max_blocks);
               int broken = 0;
               BlockPos center = player.getBlockPos();
               int blockRadius = Math.max(1, (int)Math.ceil(var18 * 0.45));

               for (BlockPos pos : BlockPos.iterateOutwards(center, blockRadius, blockRadius, blockRadius)) {
                  if (broken >= maxBlocks) {
                     break;
                  }

                  if (!(pos.getSquaredDistance(center) > blockRadius * blockRadius)) {
                     BlockState state = world.getBlockState(pos);
                     if (!player.getBoundingBox().intersects(new Box(pos))
                        && !state.isAir()
                        && state.getFluidState().isEmpty()
                        && !state.hasBlockEntity()
                        && !(state.getHardness(world, pos) < 0.0F)
                        && !protectedBlock(state)
                        && world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3)) {
                        broken++;
                     }
                  }
               }

               world.spawnParticles(ParticleTypes.EXPLOSION, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 3, 0.5, 0.5, 0.5, 0.0);
               world.spawnParticles(ParticleTypes.CLOUD, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 90, var18 * 0.45, 0.8, var18 * 0.45, 0.08);
               world.spawnParticles(ParticleTypes.CRIT, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 64, var18 * 0.5, 1.0, var18 * 0.5, 0.7);
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.2F, 0.65F);
               if (!TestMode.isEnabled(id)) {
                  punchCooldowns.put(id, Math.max(0, cfg.awakening_punch_cooldown_ticks));
               }

               player.sendMessage(
                  Text.literal("OMNI-DIRECTIONAL PUNCH — " + hits + " target(s), " + broken + " block(s) shattered.")
                     .formatted(Formatting.RED, Formatting.BOLD),
                  true
               );
            }
         }
      }
   }

   private static boolean protectedBlock(BlockState state) {
      return state.isOf(Blocks.BEDROCK)
         || state.isOf(Blocks.BARRIER)
         || state.isOf(Blocks.STRUCTURE_VOID)
         || state.isOf(Blocks.END_PORTAL)
         || state.isOf(Blocks.END_GATEWAY)
         || state.isOf(Blocks.END_PORTAL_FRAME);
   }

   private static void mountLookedAtMob(ServerPlayerEntity player) {
      ServerWorld range = player.getEntityWorld();
      if (range instanceof ServerWorld) {
         double var18 = Math.max(3.0, YPConfig.getInstance().shadow_awakening_mount_range);
         Vec3d start = player.getEyePos();
         Vec3d end = start.add(player.getRotationVec(1.0F).normalize().multiply(var18));
         HitResult obstruction = range.raycast(new RaycastContext(start, end, ShapeType.COLLIDER, FluidHandling.NONE, player));
         double unobstructedDistance = obstruction.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK
            ? start.squaredDistanceTo(obstruction.getPos())
            : var18 * var18;
         Box search = player.getBoundingBox().stretch(end.subtract(start)).expand(1.0);
         MobEntity best = null;
         double bestDistance = var18 * var18;

         for (MobEntity mob : range.getEntitiesByClass(MobEntity.class, search, entity -> entity.isAlive() && !entity.isRemoved())) {
            Optional<Vec3d> hit = mob.getBoundingBox().expand(0.3).raycast(start, end);
            if (!hit.isEmpty()) {
               double distance = start.squaredDistanceTo(hit.get());
               if (!(distance > unobstructedDistance) && distance < bestDistance) {
                  bestDistance = distance;
                  best = mob;
               }
            }
         }

         if (best == null) {
            player.sendMessage(Text.literal("Look directly at a mob to ride it.").formatted(Formatting.YELLOW), true);
         } else {
            if (player.hasVehicle()) {
               player.stopRiding();
            }

            if (!player.startRiding(best, true, true)) {
               player.sendMessage(Text.literal("That mob cannot carry a rider.").formatted(Formatting.YELLOW), true);
            } else {
               range.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_HORSE_SADDLE.value(), SoundCategory.PLAYERS, 0.8F, 0.8F);
               player.sendMessage(Text.literal("Riding " + best.getName().getString() + ". Press sneak to dismount.").formatted(Formatting.DARK_PURPLE), true);
            }
         }
      }
   }

   private static void convertNearbyMobs(ServerPlayerEntity owner) {
      ServerWorld radius = owner.getEntityWorld();
      if (radius instanceof ServerWorld) {
         double var12 = Math.max(16.0, YPConfig.getInstance().shadow_awakening_minion_radius);
         Box area = owner.getBoundingBox().expand(var12);
         int changed = 0;
         String ownTag = "yppowers_owner_" + owner.getUuid();

         for (MobEntity mob : radius.getEntitiesByClass(MobEntity.class, area, entity -> entity.isAlive() && !entity.isRemoved())) {
            if (changed >= 16) {
               break;
            }

            if (!mob.isTeammate(owner)) {
               boolean otherOwner = mob.getCommandTags().stream().anyMatch(tag -> tag.startsWith("yppowers_owner_") && !tag.equals(ownTag));
               if (!otherOwner) {
                  if (!mob.getCommandTags().contains(ownTag)) {
                     mob.addCommandTag(ownTag);
                     changed++;
                  }

                  LivingEntity threat = owner.getAttacker();
                  if (threat == null || !threat.isAlive() || !PowerCombatUtil.canHarm(owner, threat)) {
                     threat = PowerCombatUtil.pickDefenseTarget(owner, 24.0);
                  }

                  LivingEntity currentTarget = mob.getTarget();
                  if (currentTarget == owner || currentTarget != null && !PowerCombatUtil.canHarm(owner, currentTarget)) {
                     mob.setTarget(null);
                     currentTarget = null;
                  }

                  if (threat != null && PowerCombatUtil.canHarm(owner, threat)) {
                     mob.setTarget(threat);
                     mob.getNavigation().stop();
                  } else if (currentTarget == null && mob.squaredDistanceTo(owner) > 25.0) {
                     mob.getNavigation().startMovingTo(owner, 1.15);
                  } else if (currentTarget == null) {
                     mob.getNavigation().stop();
                  }
               }
            }
         }

         if (changed > 0 && owner.age % 100 == 0) {
            radius.spawnParticles(ParticleTypes.SOUL, true, true, owner.getX(), owner.getBodyY(0.6), owner.getZ(), 12, 0.8, 0.5, 0.8, 0.02);
         }
      }
   }

   private static void releaseShadowMinions(ServerPlayerEntity owner) {
      if (owner != null) {
         ServerWorld ownTag = owner.getEntityWorld();
         if (ownTag instanceof ServerWorld) {
            String var6 = "yppowers_owner_" + owner.getUuid();
            Box area = owner.getBoundingBox().expand(Math.max(128.0, YPConfig.getInstance().shadow_awakening_minion_radius));

            for (MobEntity mob : ownTag.getEntitiesByClass(MobEntity.class, area, entity -> entity.getCommandTags().contains(var6))) {
               mob.removeCommandTag(var6);
               if (mob.getTarget() == owner) {
                  mob.setTarget(null);
               }
            }

            return;
         }
      }
   }

   public static void onPlayerDisconnect(ServerPlayerEntity player) {
      if (player != null) {
         UUID id = player.getUuid();
         poseidonSinkTasks.remove(id);
         poseidonConfirmations.remove(id);
         shadowAwakeUsers.remove(id);
         shadowScanClocks.remove(id);
         releaseShadowMinions(player);
         endWitherForm(player);
      }
   }

   private static void strengthenNearbyFalloutMinions(ServerPlayerEntity owner, ServerWorld world) {
      Box area = owner.getBoundingBox().expand(96.0);
      String ownerTag = "yppowers_owner_" + owner.getUuid();

      for (MobEntity mob : world.getEntitiesByClass(
         MobEntity.class,
         area,
         entity -> entity.isAlive()
            && entity.getCommandTags().contains("yppowers_fallout_minion")
            && entity.getCommandTags().contains(ownerTag)
            && !entity.getCommandTags().contains("yppowers_fallout_awakening_boosted")
      )) {
         strengthenFalloutMinion(owner, mob);
      }
   }

   public static void strengthenFalloutMinion(ServerPlayerEntity owner, MobEntity mob) {
      if (owner != null
         && mob != null
         && owner.getEntityWorld().getServer() != null
         && mob.getCommandTags().contains("yppowers_fallout_minion")
         && !mob.getCommandTags().contains("yppowers_fallout_awakening_boosted")) {
         PowerManager manager = PowerManager.get(owner.getEntityWorld().getServer());
         if (isAwakened(manager, owner.getUuid(), PowerType.FALLOUT_WARRIOR)) {
            YPConfig cfg = YPConfig.getInstance();
            EntityAttributeInstance health = mob.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (health != null) {
               health.setBaseValue(Math.min(2048.0, health.getBaseValue() * cfg.fallout_awakening_health_multiplier));
               mob.setHealth(mob.getMaxHealth());
            }

            EntityAttributeInstance damage = mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
            if (damage != null) {
               damage.setBaseValue(Math.min(2048.0, damage.getBaseValue() * cfg.fallout_awakening_damage_multiplier));
            }

            EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            if (speed != null) {
               speed.setBaseValue(Math.min(2.0, speed.getBaseValue() * cfg.fallout_awakening_speed_multiplier));
            }

            mob.addCommandTag("yppowers_fallout_awakening_boosted");
            if (mob.getEntityWorld() instanceof ServerWorld world) {
               world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, true, true, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 10, 0.4, 0.6, 0.4, 0.05);
            }
         }
      }
   }

   private static void toxicBomb(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      int cooldown = falloutCooldowns.getOrDefault(id, 0);
      if (!TestMode.isEnabled(id) && cooldown > 0) {
         player.sendMessage(Text.literal("Toxic Bomb recharging: " + (cooldown + 19) / 20 + "s").formatted(Formatting.GREEN), true);
      } else {
         YPConfig cfg = YPConfig.getInstance();
         if (EnergyManager.get(player.getEntityWorld().getServer()).trySpendAwakening(player, cfg.fallout_awakening_bomb_energy_cost)) {
            ServerWorld radius = player.getEntityWorld();
            if (radius instanceof ServerWorld) {
               ServerWorld world = radius;
               double var11 = Math.max(2.0, cfg.fallout_awakening_bomb_radius);
               Box area = player.getBoundingBox().expand(var11);
               int victims = 0;

               for (LivingEntity target : radius.getEntitiesByClass(
                  LivingEntity.class, area, entity -> entity != player && entity.isAlive() && !entity.isRemoved()
               )) {
                  if (!(target.getEntityPos().distanceTo(player.getEntityPos()) > var11) && PowerCombatUtil.canHarm(player, target)) {
                     target.damage(world, world.getDamageSources().playerAttack(player), (float)Math.max(0.0, cfg.fallout_awakening_bomb_damage));
                     target.addStatusEffect(
                        new StatusEffectInstance(
                           StatusEffects.POISON,
                           Math.max(20, cfg.fallout_awakening_bomb_poison_ticks),
                           Math.max(0, cfg.fallout_awakening_bomb_poison_amplifier),
                           false,
                           true,
                           true
                        )
                     );
                     target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, true, true));
                     victims++;
                  }
               }

               world.spawnParticles(
                  ParticleTypes.CAMPFIRE_COSY_SMOKE, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 160, var11 * 0.55, 1.2, var11 * 0.55, 0.035
               );
               world.spawnParticles(
                  ParticleTypes.SPORE_BLOSSOM_AIR, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 70, var11 * 0.45, 1.0, var11 * 0.45, 0.02
               );
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.PLAYERS, 1.8F, 0.7F);
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.3F, 0.6F);
               if (!TestMode.isEnabled(id)) {
                  falloutCooldowns.put(id, Math.max(0, cfg.fallout_awakening_bomb_cooldown_ticks));
               }

               player.sendMessage(
                  Text.literal("Toxic Bomb released — " + victims + " target(s) caught in the cloud.").formatted(Formatting.DARK_GREEN, Formatting.BOLD), true
               );
            }
         }
      }
   }

   private static void startPoseidonSink(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld && id.getRegistryKey().equals(World.OVERWORLD)) {
         UUID idx = player.getUuid();
         if (poseidonSinkTasks.containsKey(idx)) {
            player.sendMessage(Text.literal("Your Poseidon terrain stripping is already in progress.").formatted(Formatting.AQUA), true);
         } else if (!poseidonSinkTasks.isEmpty()) {
            player.sendMessage(Text.literal("Another Poseidon terrain-stripping operation is already active.").formatted(Formatting.YELLOW), true);
         } else {
            int cooldown = poseidonCooldowns.getOrDefault(idx, 0);
            if (!TestMode.isEnabled(idx) && cooldown > 0) {
               player.sendMessage(Text.literal("Poseidon's terrain stripping recharging: " + (cooldown + 19) / 20 + "s").formatted(Formatting.RED), true);
            } else {
               YPConfig cfg = YPConfig.getInstance();
               int radius = Math.max(64, cfg.poseidon_awakening_sink_radius);
               int centerX = player.getBlockX();
               int centerZ = player.getBlockZ();
               List<ChunkPos> loadedChunks = snapshotLoadedChunks(id, centerX, centerZ, radius);
               if (loadedChunks.isEmpty()) {
                  player.sendMessage(Text.literal("No loaded terrain was found in Poseidon's radius.").formatted(Formatting.YELLOW), true);
               } else {
                  int startY = id.getBottomY() + id.getHeight() - 1;
                  int seaLevel = id.getSeaLevel();
                  if (startY <= seaLevel) {
                     player.sendMessage(Text.literal("This world's height range does not extend above sea level.").formatted(Formatting.YELLOW), true);
                  } else if (EnergyManager.get(id.getServer()).trySpendAwakening(player, cfg.poseidon_awakening_sink_energy_cost)) {
                     AwakeningManager.PoseidonSinkTask task = new AwakeningManager.PoseidonSinkTask(
                        id, centerX, centerZ, radius, seaLevel, startY, loadedChunks
                     );
                     poseidonSinkTasks.put(idx, task);
                     if (!TestMode.isEnabled(idx)) {
                        poseidonCooldowns.put(idx, Math.max(0, cfg.poseidon_awakening_sink_cooldown_ticks));
                     }

                     id.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.4F, 0.55F);
                     id.spawnParticles(ParticleTypes.SPLASH, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 36, 1.2, 0.8, 1.2, 0.08);
                     player.sendMessage(
                        Text.literal(
                              "POSEIDON — the loaded land is being stripped layer by layer toward sea level. "
                                 + loadedChunks.size()
                                 + " loaded chunks in a "
                                 + radius
                                 + "-block radius; unloaded chunks are left untouched."
                           )
                           .formatted(Formatting.AQUA, Formatting.BOLD),
                        true
                     );
                  }
               }
            }
         }
      } else {
         player.sendMessage(Text.literal("Poseidon's terrain stripping can only be started in the Overworld.").formatted(Formatting.YELLOW), true);
      }
   }

   private static List<ChunkPos> snapshotLoadedChunks(ServerWorld world, int centerX, int centerZ, int radius) {
      List<ChunkPos> result = new ArrayList<>();
      int minChunkX = Math.floorDiv(centerX - radius, 16);
      int maxChunkX = Math.floorDiv(centerX + radius, 16);
      int minChunkZ = Math.floorDiv(centerZ - radius, 16);
      int maxChunkZ = Math.floorDiv(centerZ + radius, 16);
      long radiusSquared = (long)radius * radius;

      for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
         for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            if (world.isChunkLoaded(chunkX, chunkZ)) {
               ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
               int closestX = Math.max(chunk.getStartX(), Math.min(centerX, chunk.getStartX() + 15));
               int closestZ = Math.max(chunk.getStartZ(), Math.min(centerZ, chunk.getStartZ() + 15));
               long dx = (long)closestX - centerX;
               long dz = (long)closestZ - centerZ;
               if (dx * dx + dz * dz <= radiusSquared) {
                  result.add(chunk);
               }
            }
         }
      }

      return result;
   }

   private static void tickPoseidonSink(ServerPlayerEntity player, AwakeningManager.PoseidonSinkTask task) {
      YPConfig cfg = YPConfig.getInstance();
      task.ticks++;
      if (!task.terrainComplete) {
         int totalColumns = task.chunks.size() * 256;
         int checks = 0;
         int budget = Math.max(512, cfg.poseidon_awakening_sink_columns_per_tick);
         Mutable pos = new Mutable();

         while (checks < budget && task.layerY >= task.seaLevel && task.columnCursor < totalColumns) {
            int cursor = task.columnCursor++;
            checks++;
            ChunkPos chunk = task.chunks.get(cursor >>> 8);
            if (task.world.isChunkLoaded(chunk.x, chunk.z)) {
               int blockX = chunk.getStartX() + (cursor & 15);
               int blockZ = chunk.getStartZ() + (cursor >>> 4 & 15);
               long dx = (long)blockX - task.centerX;
               long dz = (long)blockZ - task.centerZ;
               if (dx * dx + dz * dz <= (long)task.radius * task.radius) {
                  pos.set(blockX, task.layerY, blockZ);
                  BlockState state = task.world.getBlockState(pos);
                  if (!state.isAir()
                     && state.getFluidState().isEmpty()
                     && !state.hasBlockEntity()
                     && !(state.getHardness(task.world, pos) < 0.0F)
                     && !protectedBlock(state)
                     && task.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3)) {
                     task.removedBlocks++;
                     task.lastWorkPos = pos.toImmutable();
                  }
               }
            }
         }

         if (task.columnCursor >= totalColumns) {
            task.columnCursor = 0;
            task.layerY--;
            if (task.layerY < task.seaLevel) {
               task.terrainComplete = true;
               task.itemChunkCursor = 0;
               task.itemSweepComplete = false;
            }
         }
      }

      int cleanupInterval = Math.max(1, cfg.poseidon_awakening_sink_item_cleanup_interval_ticks);
      if (task.ticks % cleanupInterval == 0 && !task.chunks.isEmpty()) {
         int chunksToSweep = Math.min(32, task.chunks.size());

         for (int i = 0; i < chunksToSweep; i++) {
            if (task.itemChunkCursor >= task.chunks.size()) {
               task.itemSweepComplete = true;
               task.itemChunkCursor = 0;
            }

            ChunkPos chunk = task.chunks.get(task.itemChunkCursor++);
            if (task.world.isChunkLoaded(chunk.x, chunk.z)) {
               Box chunkBox = new Box(
                  chunk.getStartX(), task.world.getBottomY(), chunk.getStartZ(), chunk.getStartX() + 16, task.startY + 1, chunk.getStartZ() + 16
               );

               for (ItemEntity item : task.world.getEntitiesByClass(ItemEntity.class, chunkBox, Entity::isAlive)) {
                  long dx = (long)item.getBlockX() - task.centerX;
                  long dz = (long)item.getBlockZ() - task.centerZ;
                  if (dx * dx + dz * dz <= (long)task.radius * task.radius) {
                     item.discard();
                  }
               }
            }
         }

         if (task.itemChunkCursor >= task.chunks.size()) {
            task.itemSweepComplete = true;
         }
      }

      if (task.lastWorkPos != null && task.ticks % 10 == 0) {
         BlockPos p = task.lastWorkPos;
         task.world.spawnParticles(ParticleTypes.CLOUD, true, true, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 5, 0.35, 0.25, 0.35, 0.015);
         task.world.spawnParticles(ParticleTypes.BUBBLE, true, true, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, 3, 0.25, 0.15, 0.25, 0.03);
      }

      if (task.ticks % 200 == 0 && !task.terrainComplete) {
         player.sendMessage(
            Text.literal("Poseidon's sink is stripping Y=" + task.layerY + " — " + task.removedBlocks + " blocks removed so far.").formatted(Formatting.AQUA),
            true
         );
      }

      if (task.terrainComplete && task.itemSweepComplete) {
         poseidonSinkTasks.remove(player.getUuid());
         task.world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
         player.sendMessage(
            Text.literal(
                  "Poseidon's terrain stripping is complete — "
                     + task.removedBlocks
                     + " blocks removed. Only chunks loaded when the ability began were affected."
               )
               .formatted(Formatting.AQUA, Formatting.BOLD),
            true
         );
      }
   }

   private static void toggleWitherForm(ServerPlayerEntity player) {
      if (witherForms.contains(player.getUuid())) {
         endWitherForm(player);
         player.sendMessage(Text.literal("Wither Ascendance ended.").formatted(Formatting.GRAY), true);
      } else {
         UUID id = player.getUuid();
         witherFlightSnapshots.put(id, new AwakeningManager.FlightSnapshot(player.getAbilities().allowFlying, player.getAbilities().flying));
         witherForms.add(id);
         YPNetworking.syncWitherAscendance(player, true);
         player.getAbilities().allowFlying = true;
         player.getAbilities().flying = true;
         player.sendAbilitiesUpdate();
         maintainWitherForm(player);
         player.sendMessage(
            Text.literal("WITHER ASCENDANCE — you are a Wither; flight, Wither strength, and your full Fire God skillset are active.")
               .formatted(Formatting.DARK_PURPLE, Formatting.BOLD),
            true
         );
         ServerWorld var3 = player.getEntityWorld();
         if (var3 instanceof ServerWorld) {
            var3.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.5F, 0.75F);
            var3.spawnParticles(ParticleTypes.SOUL, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 70, 1.0, 1.0, 1.0, 0.08);
         }
      }
   }

   private static void maintainWitherForm(ServerPlayerEntity player) {
      if (!player.getAbilities().allowFlying) {
         player.getAbilities().allowFlying = true;
      }

      player.sendAbilitiesUpdate();
      player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 40, 2, false, false, true));
      player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1, false, false, true));
      player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
      ServerWorld var2 = player.getEntityWorld();
      if (var2 instanceof ServerWorld && player.age % 4 == 0) {
         var2.spawnParticles(ParticleTypes.SOUL, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 5, 0.45, 0.7, 0.45, 0.01);
      }
   }

   public static void endWitherForm(ServerPlayerEntity player) {
      if (player != null) {
         UUID id = player.getUuid();
         if (witherForms.remove(id)) {
            YPNetworking.syncWitherAscendance(player, false);
            AwakeningManager.FlightSnapshot snapshot = witherFlightSnapshots.remove(id);
            if (snapshot != null) {
               player.getAbilities().allowFlying = snapshot.allowFlying();
               player.getAbilities().flying = snapshot.flying() && snapshot.allowFlying();
               player.sendAbilitiesUpdate();
            }

            removeOwnedEffect(player, StatusEffects.STRENGTH, 2);
            removeOwnedEffect(player, StatusEffects.RESISTANCE, 1);
            removeOwnedEffect(player, StatusEffects.FIRE_RESISTANCE, 0);
         }
      }
   }

   public static boolean isWitherForm(UUID playerId) {
      return playerId != null && witherForms.contains(playerId);
   }

   private static void removeOwnedEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, int amplifier) {
      StatusEffectInstance current = player.getStatusEffect(type);
      if (current != null && current.getAmplifier() == amplifier && current.getDuration() <= 40) {
         player.removeStatusEffect(type);
      }
   }

   public static void clearCooldowns(UUID id) {
      if (id != null) {
         punchCooldowns.remove(id);
         falloutCooldowns.remove(id);
         poseidonCooldowns.remove(id);
         timeDomainCooldowns.remove(id);
      }
   }

   public static void clearRuntimeState() {
      shadowScanClocks.clear();
      punchCooldowns.clear();
      falloutCooldowns.clear();
      poseidonCooldowns.clear();
      timeDomainCooldowns.clear();
      poseidonConfirmations.clear();
      destructiveConfirmations.clear();
      goatRamConfirmedUntil.clear();
      poseidonSinkTasks.clear();
      witherForms.clear();
      witherFlightSnapshots.clear();
   }

   private static void decrement(Map<UUID, Integer> map, UUID id) {
      int value = map.getOrDefault(id, 0);
      if (value <= 1) {
         map.remove(id);
      } else {
         map.put(id, value - 1);
      }
   }

   private static enum DestructiveAction {
      OMNI_PUNCH,
      BLACK_HOLE,
      GOAT_RAM;
   }

   private record FlightSnapshot(boolean allowFlying, boolean flying) {
   }

   private static final class GambitCooldownState extends PersistentState {
      final Map<UUID, Long> readyAtGameTick = new HashMap<>();

      NbtCompound toNbt() {
         NbtCompound root = new NbtCompound();
         NbtList entries = new NbtList();

         for (Entry<UUID, Long> entry : this.readyAtGameTick.entrySet()) {
            NbtCompound value = new NbtCompound();
            value.putString("Player", entry.getKey().toString());
            value.putLong("ReadyAt", entry.getValue());
            entries.add(value);
         }

         root.put("Cooldowns", entries);
         return root;
      }

      static AwakeningManager.GambitCooldownState fromNbt(NbtCompound root) {
         AwakeningManager.GambitCooldownState state = new AwakeningManager.GambitCooldownState();

         for (NbtElement element : root.getListOrEmpty("Cooldowns")) {
            if (element instanceof NbtCompound value) {
               try {
                  UUID player = UUID.fromString(value.getString("Player", ""));
                  long readyAt = value.getLong("ReadyAt", 0L);
                  if (readyAt > 0L) {
                     state.readyAtGameTick.put(player, readyAt);
                  }
               } catch (IllegalArgumentException var8) {
               }
            }
         }

         return state;
      }
   }

   private record PendingDestructiveConfirmation(RegistryKey<World> worldKey, AwakeningManager.DestructiveAction action, int ticksRemaining) {
   }

   private record PendingPoseidonConfirmation(RegistryKey<World> worldKey, int ticksRemaining) {
   }

   private static final class PoseidonSinkTask {
      final ServerWorld world;
      final int centerX;
      final int centerZ;
      final int radius;
      final int seaLevel;
      final int startY;
      final List<ChunkPos> chunks;
      int layerY;
      int columnCursor;
      int ticks;
      int itemChunkCursor;
      long removedBlocks;
      BlockPos lastWorkPos;
      boolean terrainComplete;
      boolean itemSweepComplete;

      PoseidonSinkTask(ServerWorld world, int centerX, int centerZ, int radius, int seaLevel, int startY, List<ChunkPos> chunks) {
         this.world = world;
         this.centerX = centerX;
         this.centerZ = centerZ;
         this.radius = radius;
         this.seaLevel = seaLevel;
         this.startY = startY;
         this.layerY = startY;
         this.chunks = chunks;
      }
   }
}
