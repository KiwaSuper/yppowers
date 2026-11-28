package com.yppowers.power.timewarper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.teleportgambit.TeleportGambitDomainManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TimeWarperDomainManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Time Warper Domain");
   public static final RegistryKey<World> TIME_DOMAIN_KEY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("yppowers", "time_domain"));
   private static final int FLOOR_Y = 64;
   private static final int ROOM_RADIUS = 12;
   private static final int ROOM_HEIGHT = 16;
   private static final int LOOP_OFFSET_X = 32;
   private static final int CELL_SPACING = 64;
   private static final int CELL_COLUMNS = 1024;
   private static final int MARKER_Y = 1;
   private static final int EFFECT_LEASE_TICKS = 40;
   private static final Map<UUID, TimeWarperDomainManager.DomainSession> SESSIONS = new HashMap<>();
   private static final Map<UUID, UUID> SESSION_OWNER_BY_MEMBER = new HashMap<>();
   private static final Codec<TimeWarperDomainManager.DomainState> CODEC = Codec.PASSTHROUGH.xmap(dynamic -> {
      NbtElement element = (NbtElement)dynamic.convert(NbtOps.INSTANCE).getValue();
      return TimeWarperDomainManager.DomainState.fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
   }, state -> new Dynamic(NbtOps.INSTANCE, state.toNbt()));
   private static final PersistentStateType<TimeWarperDomainManager.DomainState> STATE_TYPE = new PersistentStateType<>(
      "yppowers_time_warper_domain", TimeWarperDomainManager.DomainState::new, CODEC, null
   );

   private TimeWarperDomainManager() {
   }

   private static TimeWarperDomainManager.DomainState state(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
   }

   public static boolean isTimeDomain(ServerWorld world) {
      return world != null && world.getRegistryKey().equals(TIME_DOMAIN_KEY);
   }

   public static boolean hasActiveSession(UUID ownerId) {
      return ownerId != null && SESSIONS.containsKey(ownerId);
   }

   public static boolean isInActiveDomain(ServerPlayerEntity player) {
      if (player != null) {
         ServerWorld var2 = player.getEntityWorld();
         if (isTimeDomain(var2 instanceof ServerWorld ? var2 : null) && SESSION_OWNER_BY_MEMBER.containsKey(player.getUuid())) {
            return true;
         }
      }

      return false;
   }

   public static boolean open(ServerPlayerEntity owner, double captureRadius) {
      return openInternal(owner, captureRadius, false, null, null);
   }

   public static boolean openChunkDuping(ServerPlayerEntity owner, double captureRadius) {
      if (owner != null && owner.getEntityWorld().getServer() != null && owner.isAlive()) {
         ServerWorld server = owner.getEntityWorld();
         if (server instanceof ServerWorld && server.getRegistryKey().equals(World.OVERWORLD) && !SESSIONS.containsKey(owner.getUuid())) {
            if (SESSION_OWNER_BY_MEMBER.containsKey(owner.getUuid())) {
               owner.sendMessage(Text.literal("You are already part of an active Time Domain session."), true);
               return false;
            }

            MinecraftServer serverx = server.getServer();
            if (PortableChunkStorage.hasPendingRecovery(serverx)
               && !PortableChunkStorage.recoverPending(serverx, TimeWarperDomainManager::recordSwapTransferredPlayer)) {
               owner.sendMessage(Text.literal("A previous portable-chunk swap is still recovering; try again shortly."), true);
               return false;
            }

            ChunkPos entryTarget = owner.getChunkPos();
            boolean madeReplica = PortableChunkStorage.replicaCount(serverx, owner.getUuid()) < 20;
            if (madeReplica && !PortableChunkStorage.captureReplica(serverx, owner.getUuid(), server, entryTarget)) {
               owner.sendMessage(Text.literal("The Overworld chunk could not be safely captured; no Domain entry was made."), true);
               return false;
            }

            ChunkPos entryReplica = PortableChunkStorage.latestReplicaPosition(serverx, owner.getUuid());
            boolean opened = openInternal(owner, captureRadius, true, entryTarget, entryReplica);
            if (!opened && madeReplica) {
               PortableChunkStorage.rollbackLatestCapture(serverx, owner.getUuid());
            }

            if (opened) {
               int count = PortableChunkStorage.replicaCount(serverx, owner.getUuid());
               ChunkPos latest = PortableChunkStorage.latestReplicaPosition(serverx, owner.getUuid());
               if (madeReplica && latest != null && count <= 20) {
                  owner.sendMessage(
                     Text.literal(
                        "Replica "
                           + count
                           + "/20 is at chunk "
                           + latest.x
                           + ", "
                           + latest.z
                           + ". This session's swap target is entry chunk "
                           + entryTarget.x
                           + ", "
                           + entryTarget.z
                           + ". Press Z while standing in a replica; press P to return."
                     ),
                     true
                  );
               } else {
                  owner.sendMessage(
                     Text.literal(
                        "All 20 replicas are stored. You entered beside your latest replica at chunk "
                           + (latest == null ? "unavailable" : latest.x + ", " + latest.z)
                           + ". This session's swap target is entry chunk "
                           + entryTarget.x
                           + ", "
                           + entryTarget.z
                           + ". Press Z in a replica; press P to return."
                     ),
                     true
                  );
               }
            }

            return opened;
         }
      }

      if (owner != null) {
         owner.sendMessage(Text.literal("Chunk Duping entry is only available from the Overworld."), true);
      }

      return false;
   }

   private static boolean openInternal(ServerPlayerEntity owner, double captureRadius, boolean chunkDupingMode, ChunkPos entryTarget, ChunkPos entryReplica) {
      if (owner != null && owner.getEntityWorld().getServer() != null && owner.isAlive()) {
         ServerWorld server = owner.getEntityWorld();
         if (server instanceof ServerWorld) {
            ServerWorld source = server;
            if (!isTimeDomain(server)
               && !server.getRegistryKey().equals(TeleportGambitDomainManager.DOMAIN_KEY)
               && !SESSIONS.containsKey(owner.getUuid())
               && !SESSION_OWNER_BY_MEMBER.containsKey(owner.getUuid())) {
               MinecraftServer serverx = server.getServer();
               ServerWorld destination = serverx.getWorld(TIME_DOMAIN_KEY);
               if (destination == null) {
                  owner.sendMessage(Text.literal("The Time Domain dimension is unavailable on this server."), true);
                  return false;
               }

               TimeWarperDomainManager.DomainState data = state(serverx);
               TimeWarperDomainManager.DomainCell cell = data.getOrCreateCell(owner.getUuid());
               if (!cell.initialized) {
                  buildRoom(destination, cell);
                  cell.initialized = true;
                  data.markDirty();
               }

               if (!chunkDupingMode && !cell.loopArenaInitialized) {
                  buildLoopArena(destination, cell);
                  cell.loopArenaInitialized = true;
                  data.markDirty();
               }

               TimeWarperDomainManager.DomainSession session = new TimeWarperDomainManager.DomainSession(owner.getUuid(), cell, chunkDupingMode, entryTarget);
               List<ServerPlayerEntity> candidates = new ArrayList<>();
               double radius = Math.max(0.0, captureRadius);
               double radiusSquared = radius * radius;

               for (ServerPlayerEntity candidate : serverx.getPlayerManager().getPlayerList()) {
                  if (candidate.isAlive()
                     && !candidate.isSpectator()
                     && candidate.getEntityWorld() == source
                     && !(candidate.squaredDistanceTo(owner) > radiusSquared)
                     && !SESSION_OWNER_BY_MEMBER.containsKey(candidate.getUuid())) {
                     candidates.add(candidate);
                  }
               }

               if (!candidates.contains(owner)) {
                  candidates.add(owner);
               }

               for (ServerPlayerEntity candidatex : candidates) {
                  TimeWarperDomainManager.PlayerOrigin origin = TimeWarperDomainManager.PlayerOrigin.capture(candidatex);
                  session.origins.put(candidatex.getUuid(), origin);
                  data.pendingReturns.put(candidatex.getUuid(), origin);
               }

               data.markDirty();
               if (serverx.save(true, true, true)) {
                  SESSIONS.put(owner.getUuid(), session);
                  int slot = 0;

                  for (ServerPlayerEntity candidatex : candidates) {
                     double[] offset = landingOffset(slot++);
                     int landingCenterX = chunkDupingMode ? cell.centerX() : cell.loopCenterX();
                     double x = landingCenterX + offset[0] + 0.5;
                     double y = 65.0;
                     double z = cell.centerZ() + offset[1] + 0.5;
                     if (candidatex == owner && chunkDupingMode && entryReplica != null && entryTarget != null) {
                        x = entryReplica.getStartX() + (owner.getX() - entryTarget.getStartX());
                        y = owner.getY();
                        z = entryReplica.getStartZ() + (owner.getZ() - entryTarget.getStartZ());
                     }

                     if (!transfer(candidatex, destination, x, y, z)) {
                        if (candidatex == owner) {
                           endSession(serverx, owner.getUuid(), TimeWarperDomainManager.EndReason.OPEN_FAILED);
                           owner.sendMessage(Text.literal("The Time Domain could not open safely."), true);
                           return false;
                        }

                        session.origins.remove(candidatex.getUuid());
                        data.pendingReturns.remove(candidatex.getUuid());
                     } else {
                        SESSION_OWNER_BY_MEMBER.put(candidatex.getUuid(), owner.getUuid());
                        YPNetworking.syncTimeDomainView(candidatex, true, candidatex == owner);
                        candidatex.sendMessage(
                           Text.literal(
                              candidatex == owner
                                 ? (
                                    chunkDupingMode
                                       ? "CHUNK DUPING DOMAIN — your entry chunk is recorded for this session. Press P to return."
                                       : "TIME DOMAIN — walk off an edge to wrap smoothly to the opposite side. Press P to return."
                                 )
                                 : "The Time Warper's Domain has closed around you; its edges loop to the opposite side."
                           ),
                           true
                        );
                     }
                  }

                  data.markDirty();
                  destination.setTimeOfDay(6000L);
                  return true;
               }

               for (UUID memberId : session.origins.keySet()) {
                  data.pendingReturns.remove(memberId);
               }

               data.markDirty();
               serverx.save(true, true, true);
               LOGGER.error("Could not durably save Time Domain return origins; refusing to transfer players.");
               return false;
            }
         }
      }

      return false;
   }

   public static boolean swapPortableChunk(ServerPlayerEntity owner) {
      if (owner != null && owner.getEntityWorld().getServer() != null && owner.isAlive()) {
         UUID sessionOwnerId = SESSION_OWNER_BY_MEMBER.get(owner.getUuid());
         if (sessionOwnerId != null && sessionOwnerId.equals(owner.getUuid())) {
            TimeWarperDomainManager.DomainSession session = SESSIONS.get(sessionOwnerId);
            if (session != null && session.chunkDupingMode && session.entryTarget != null) {
               ServerWorld server = owner.getEntityWorld();
               if (server instanceof ServerWorld && isTimeDomain(server)) {
                  MinecraftServer serverx = server.getServer();
                  ChunkPos replica = owner.getChunkPos();
                  if (!PortableChunkStorage.isReplicaChunk(serverx, sessionOwnerId, replica)) {
                     owner.sendMessage(Text.literal("Stand inside one of your stored replica chunks before pressing Z."), true);
                     return false;
                  } else if (PortableChunkStorage.hasPendingRecovery(serverx)) {
                     owner.sendMessage(Text.literal("A portable-chunk swap is already recovering; wait before starting another."), true);
                     return false;
                  } else {
                     ServerWorld overworld = serverx.getOverworld();
                     owner.sendMessage(
                        Text.literal(
                           "Exchanging replica chunk "
                              + replica.x
                              + ", "
                              + replica.z
                              + " with saved entry chunk "
                              + session.entryTarget.x
                              + ", "
                              + session.entryTarget.z
                              + "..."
                        ),
                        true
                     );
                     boolean swapped = PortableChunkStorage.swapReplica(
                        serverx, sessionOwnerId, overworld, session.entryTarget, replica, TimeWarperDomainManager::recordSwapTransferredPlayer
                     );
                     if (swapped) {
                        ServerWorld var9 = owner.getEntityWorld();
                        session.ownerMovedWithSwap = !isTimeDomain(var9 instanceof ServerWorld ? var9 : null);
                        if (session.ownerMovedWithSwap) {
                           TimeWarperDomainManager.DomainState data = state(serverx);
                           for (UUID memberId : new ArrayList<>(session.origins.keySet())) {
                              if (!memberId.equals(sessionOwnerId)) {
                                 TimeWarperDomainManager.PlayerOrigin mOrigin = session.origins.remove(memberId);
                                 SESSION_OWNER_BY_MEMBER.remove(memberId);
                                 ServerPlayerEntity member = serverx.getPlayerManager().getPlayer(memberId);
                                 if (member != null && member.isAlive() && mOrigin != null && mOrigin.teleport(member, serverx)) {
                                    data.pendingReturns.remove(memberId);
                                    removeDomainVision(member);
                                    YPNetworking.syncTimeDomainView(member, false, false);
                                 }
                              }
                           }
                           data.markDirty();
                        }
                        owner.sendMessage(
                           Text.literal(
                              session.ownerMovedWithSwap
                                 ? "Chunk swap complete. You moved with the replica; press P to close the Domain and return to your saved entry point."
                                 : "Chunk swap complete. The Overworld chunk at your saved entry point has been exchanged."
                           ),
                           true
                        );
                     } else {
                        owner.sendMessage(
                           Text.literal("The chunk swap could not finish yet. Its journal is retained for safe recovery; do not edit the affected chunks."),
                           true
                        );
                     }

                     return swapped;
                  }
               } else {
                  owner.sendMessage(Text.literal("Your Chunk Duping session is not currently inside the Time Domain."), true);
                  return false;
               }
            } else {
               owner.sendMessage(Text.literal("This is a Normal Time Domain. Open Chunk Duping mode with M to use portable chunks."), true);
               return false;
            }
         } else {
            owner.sendMessage(Text.literal("Only the Time Warper who opened Chunk Duping mode can swap replicas."), true);
            return false;
         }
      } else {
         return false;
      }
   }

   private static void recordSwapTransferredPlayer(ServerPlayerEntity player, UUID ownerId) {
      if (player != null && ownerId != null && player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         TimeWarperDomainManager.DomainState data = state(server);
         TimeWarperDomainManager.DomainSession session = SESSIONS.get(ownerId);
         UUID activeOwner = SESSION_OWNER_BY_MEMBER.get(player.getUuid());
         if (activeOwner == null || !activeOwner.equals(ownerId)) {
            TimeWarperDomainManager.PlayerOrigin origin = TimeWarperDomainManager.PlayerOrigin.capture(player);
            data.pendingReturns.putIfAbsent(player.getUuid(), origin);
            if (session != null) {
               session.origins.putIfAbsent(player.getUuid(), origin);
               SESSION_OWNER_BY_MEMBER.put(player.getUuid(), ownerId);
               YPNetworking.syncTimeDomainView(player, true, false);
            }

            data.markDirty();
            server.getOverworld().getPersistentStateManager().save();
         }
      }
   }

   public static boolean closeByOwner(ServerPlayerEntity owner) {
      if (owner != null && owner.getEntityWorld().getServer() != null) {
         UUID sessionOwner = SESSION_OWNER_BY_MEMBER.get(owner.getUuid());
         if (sessionOwner != null && sessionOwner.equals(owner.getUuid())) {
            MinecraftServer server = owner.getEntityWorld().getServer();
            if (PortableChunkStorage.hasPendingRecovery(server)
               && !PortableChunkStorage.recoverPending(server, TimeWarperDomainManager::recordSwapTransferredPlayer)) {
               owner.sendMessage(Text.literal("The portable-chunk swap is still recovering; P will return you once it is safe."), true);
               return true;
            } else {
               endSession(server, sessionOwner, TimeWarperDomainManager.EndReason.MANUAL_CLOSE);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void serverTick(MinecraftServer server) {
      if (PortableChunkStorage.hasPendingRecovery(server) && server.getTicks() % 20 == 0) {
         if (!PortableChunkStorage.recoverPending(server, TimeWarperDomainManager::recordSwapTransferredPlayer)) {
            return;
         }

         TimeWarperDomainManager.DomainState recoveredState = state(server);

         for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (!SESSION_OWNER_BY_MEMBER.containsKey(online.getUuid()) && recoveredState.pendingReturns.containsKey(online.getUuid())) {
               restorePendingReturn(online);
            }
         }
      }

      if (!SESSIONS.isEmpty()) {
         for (TimeWarperDomainManager.DomainSession session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(session.ownerId);
            if (owner == null) {
               endSession(server, session.ownerId, TimeWarperDomainManager.EndReason.OWNER_DISCONNECTED);
            } else if (!owner.isAlive()) {
               endSession(server, session.ownerId, TimeWarperDomainManager.EndReason.OWNER_DIED);
            } else {
               PowerManager powers = PowerManager.get(server);
               if (powers.getAssignedPowerType(session.ownerId) == PowerType.TIME_WARPER && powers.isPowerEnabled(owner, PowerType.TIME_WARPER)) {
                  ServerWorld memberId = owner.getEntityWorld();
                  if (!isTimeDomain(memberId instanceof ServerWorld ? memberId : null)) {
                     if (!session.ownerMovedWithSwap) {
                        endSession(server, session.ownerId, TimeWarperDomainManager.EndReason.OWNER_LEFT_DIMENSION);
                     }
                  } else {
                     if (!session.chunkDupingMode) {
                        wrapLoopArena(owner, session.cell);
                     }

                     if (!insideSessionArea(owner, session)) {
                        endSession(server, session.ownerId, TimeWarperDomainManager.EndReason.OWNER_LEFT_CELL);
                     } else {
                        for (UUID memberIdx : new ArrayList<>(session.origins.keySet())) {
                           ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberIdx);
                           if (member != null) {
                              if (!member.isAlive()) {
                                 SESSION_OWNER_BY_MEMBER.remove(memberIdx);
                              } else {
                                 if (server.getTicks() % 10 == 0) {
                                    boolean friendlyVision = memberIdx.equals(session.ownerId)
                                       || member.isSpectator()
                                       || AllianceManager.get(server).areAllied(session.ownerId, memberIdx);
                                    maintainDomainVision(member, friendlyVision);
                                 }

                                 if (!session.ownerMovedWithSwap) {
                                    if (!session.chunkDupingMode) {
                                       ServerWorld offset = member.getEntityWorld();
                                       if (isTimeDomain(offset instanceof ServerWorld ? offset : null)) {
                                          wrapLoopArena(member, session.cell);
                                       }
                                    }

                                    ServerWorld var16 = member.getEntityWorld();
                                    if (!isTimeDomain(var16 instanceof ServerWorld ? var16 : null) || !insideSessionArea(member, session)) {
                                       double[] offset = landingOffset(Math.floorMod(memberIdx.hashCode(), 12));
                                       int returnCenterX = session.chunkDupingMode ? session.cell.centerX() : session.cell.loopCenterX();
                                       transfer(
                                          member,
                                          server.getWorld(TIME_DOMAIN_KEY),
                                          returnCenterX + offset[0] + 0.5,
                                          65.0,
                                          session.cell.centerZ() + offset[1] + 0.5
                                       );
                                       member.sendMessage(Text.literal("The Time Domain is sealed until its owner closes it."), true);
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               } else {
                  endSession(server, session.ownerId, TimeWarperDomainManager.EndReason.OWNER_POWER_REMOVED);
               }
            }
         }
      }

      if (server.getTicks() % 200 == 0) {
         ServerWorld dimension = server.getWorld(TIME_DOMAIN_KEY);
         if (dimension != null) {
            dimension.setTimeOfDay(6000L);
         }
      }
   }

   public static boolean mayModifyBlock(ServerPlayerEntity player, BlockPos pos) {
      if (player != null) {
         ServerWorld ownerId = player.getEntityWorld();
         if (ownerId instanceof ServerWorld && isTimeDomain(ownerId)) {
            UUID ownerIdx = SESSION_OWNER_BY_MEMBER.get(player.getUuid());
            if (ownerIdx != null && ownerIdx.equals(player.getUuid())) {
               TimeWarperDomainManager.DomainSession session = SESSIONS.get(ownerIdx);
               return session != null
                  && (
                     insideCell(pos, session.cell)
                        || session.chunkDupingMode && PortableChunkStorage.isReplicaChunk(ownerId.getServer(), ownerIdx, new ChunkPos(pos))
                  );
            }

            return false;
         }
      }

      return true;
   }

   public static void onPlayerDeath(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         UUID id = player.getUuid();
         UUID ownerId = SESSION_OWNER_BY_MEMBER.get(id);
         if (ownerId != null) {
            if (ownerId.equals(id)) {
               endSession(player.getEntityWorld().getServer(), ownerId, TimeWarperDomainManager.EndReason.OWNER_DIED);
            } else {
               SESSION_OWNER_BY_MEMBER.remove(id);
               TimeWarperDomainManager.DomainSession session = SESSIONS.get(ownerId);
               if (session != null) {
                  session.origins.remove(id);
               }
               TimeWarperDomainManager.DomainState data = state(player.getEntityWorld().getServer());
               if (data.pendingReturns.remove(id) != null) {
                  data.markDirty();
               }
            }
         }
      }
   }

   public static void onPlayerDisconnect(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         UUID id = player.getUuid();
         UUID ownerId = SESSION_OWNER_BY_MEMBER.get(id);
         if (ownerId != null) {
            if (ownerId.equals(id)) {
               endSession(player.getEntityWorld().getServer(), ownerId, TimeWarperDomainManager.EndReason.OWNER_DISCONNECTED);
            } else {
               SESSION_OWNER_BY_MEMBER.remove(id);
               TimeWarperDomainManager.DomainSession session = SESSIONS.get(ownerId);
               if (session != null) {
                  session.origins.remove(id);
               }

               state(player.getEntityWorld().getServer()).markDirty();
            }
         }
      }
   }

   public static void onPowerRemoved(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         if (SESSIONS.containsKey(player.getUuid())) {
            endSession(player.getEntityWorld().getServer(), player.getUuid(), TimeWarperDomainManager.EndReason.OWNER_POWER_REMOVED);
         }
      }
   }

   public static void onPlayerJoin(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         if (PortableChunkStorage.hasPendingRecovery(server)
            && !PortableChunkStorage.recoverPending(server, TimeWarperDomainManager::recordSwapTransferredPlayer)) {
            player.sendMessage(Text.literal("A portable-chunk recovery is still running; your saved return point will be restored when it is safe."), false);
            YPNetworking.syncTimeDomainView(player, false, false);
         } else {
            restorePendingReturn(player);
            YPNetworking.syncTimeDomainView(player, false, false);
         }
      }
   }

   public static void onPlayerRespawn(ServerPlayerEntity player) {
      onPlayerJoin(player);
   }

   public static void shutdown(MinecraftServer server) {
      if (PortableChunkStorage.hasPendingRecovery(server) && !PortableChunkStorage.recoverPending(server, TimeWarperDomainManager::recordSwapTransferredPlayer)
         )
       {
         LOGGER.error("A portable-chunk transaction remains journaled during shutdown; recovery will resume on next startup.");
      } else {
         for (UUID ownerId : new ArrayList<>(SESSIONS.keySet())) {
            endSession(server, ownerId, TimeWarperDomainManager.EndReason.SERVER_SHUTDOWN);
         }

         for (UUID id : new ArrayList<>(SESSION_OWNER_BY_MEMBER.keySet())) {
            SESSION_OWNER_BY_MEMBER.remove(id);
         }
      }
   }

   public static void clearRuntimeState() {
      SESSIONS.clear();
      SESSION_OWNER_BY_MEMBER.clear();
   }

   private static void restorePendingReturn(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         TimeWarperDomainManager.DomainState data = state(server);
         TimeWarperDomainManager.PlayerOrigin origin = data.pendingReturns.get(player.getUuid());
         if (origin != null && origin.teleport(player, server)) {
            data.pendingReturns.remove(player.getUuid());
            data.markDirty();
            server.save(true, true, true);
            removeDomainVision(player);
            player.sendMessage(Text.literal("You have been returned from the Time Domain."), false);
         } else {
            ServerWorld spawn = player.getEntityWorld();
            if (isTimeDomain(spawn instanceof ServerWorld ? spawn : null) && !SESSION_OWNER_BY_MEMBER.containsKey(player.getUuid())) {
               BlockPos spawnx = server.getOverworld().getSpawnPoint().getPos();
               transfer(player, server.getOverworld(), spawnx.getX() + 0.5, spawnx.getY() + 1.0, spawnx.getZ() + 0.5);
               removeDomainVision(player);
               player.sendMessage(Text.literal("An orphaned Time Domain session was recovered; you were moved to world spawn."), false);
            }
         }
      }
   }

   private static void endSession(MinecraftServer server, UUID ownerId, TimeWarperDomainManager.EndReason reason) {
      TimeWarperDomainManager.DomainSession session = SESSIONS.remove(ownerId);
      if (session != null) {
         TimeWarperDomainManager.DomainState data = state(server);

         for (Entry<UUID, TimeWarperDomainManager.PlayerOrigin> entry : new ArrayList<>(session.origins.entrySet())) {
            UUID memberId = entry.getKey();
            TimeWarperDomainManager.PlayerOrigin origin = entry.getValue();
            SESSION_OWNER_BY_MEMBER.remove(memberId);
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (memberId.equals(ownerId) && reason == TimeWarperDomainManager.EndReason.OWNER_LEFT_DIMENSION && member != null) {
               ServerWorld var11 = member.getEntityWorld();
               if (!isTimeDomain(var11 instanceof ServerWorld ? var11 : null)) {
                  data.pendingReturns.remove(memberId);
                  removeDomainVision(member);
                  YPNetworking.syncTimeDomainView(member, false, false);
                  continue;
               }
            }

            if (memberId.equals(ownerId) && reason == TimeWarperDomainManager.EndReason.OWNER_DIED) {
               data.pendingReturns.remove(memberId);
               if (member != null) {
                  removeDomainVision(member);
                  YPNetworking.syncTimeDomainView(member, false, false);
               }
               continue;
            }

            if (member != null && member.isAlive()) {
               if (origin.teleport(member, server)) {
                  data.pendingReturns.remove(memberId);
               } else {
                  data.pendingReturns.put(memberId, origin);
               }

               removeDomainVision(member);
               YPNetworking.syncTimeDomainView(member, false, false);
            } else {
               data.pendingReturns.put(memberId, origin);
               if (member != null) {
                  YPNetworking.syncTimeDomainView(member, false, false);
               }
            }
         }

         data.markDirty();
         if (!server.save(true, true, true)) {
            LOGGER.error("Could not flush Time Domain return-state changes after closing session {}.", ownerId);
         }

         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
         if (owner != null
            && reason != TimeWarperDomainManager.EndReason.OWNER_DIED
            && reason != TimeWarperDomainManager.EndReason.OWNER_DISCONNECTED
            && reason != TimeWarperDomainManager.EndReason.OWNER_LEFT_DIMENSION
            && owner.isAlive()) {
            owner.sendMessage(Text.literal("Time Domain closed. Your storage cell remains saved."), true);
         }

         session.origins.clear();
      }
   }

   private static void maintainDomainVision(ServerPlayerEntity player, boolean owner) {
      if (owner) {
         if (player.getStatusEffect(StatusEffects.NIGHT_VISION) == null || player.getStatusEffect(StatusEffects.NIGHT_VISION).getDuration() < 20) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 40, 0, false, false, false));
         }
      } else {
         if (player.getStatusEffect(StatusEffects.DARKNESS) == null || player.getStatusEffect(StatusEffects.DARKNESS).getDuration() < 20) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0, false, false, false));
         }
      }
   }

   private static void removeDomainVision(ServerPlayerEntity player) {
      StatusEffectInstance blindness = player.getStatusEffect(StatusEffects.BLINDNESS);
      if (blindness != null && blindness.getDuration() <= 40) {
         player.removeStatusEffect(StatusEffects.BLINDNESS);
      }

      StatusEffectInstance darkness = player.getStatusEffect(StatusEffects.DARKNESS);
      if (darkness != null && darkness.getDuration() <= 40) {
         player.removeStatusEffect(StatusEffects.DARKNESS);
      }

      StatusEffectInstance nightVision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
      if (nightVision != null && nightVision.getDuration() <= 40) {
         player.removeStatusEffect(StatusEffects.NIGHT_VISION);
      }
   }

   private static void buildRoom(ServerWorld world, TimeWarperDomainManager.DomainCell cell) {
      BlockPos marker = new BlockPos(cell.centerX(), 1, cell.centerZ());
      if (!world.getBlockState(marker).isOf(Blocks.CRYING_OBSIDIAN)) {
         for (int dx = -12; dx <= 12; dx++) {
            for (int dz = -12; dz <= 12; dz++) {
               BlockPos floor = new BlockPos(cell.centerX() + dx, 64, cell.centerZ() + dz);
               placeIfAir(world, floor, Blocks.BLACK_CONCRETE.getDefaultState());
               boolean edge = Math.abs(dx) == 12 || Math.abs(dz) == 12;
               if (edge) {
                  for (int dy = 1; dy <= 16; dy++) {
                     placeIfAir(world, floor.up(dy), Blocks.CRYING_OBSIDIAN.getDefaultState());
                  }
               }
            }
         }

         world.setBlockState(marker, Blocks.CRYING_OBSIDIAN.getDefaultState(), 2);
      }
   }

   private static void buildLoopArena(ServerWorld world, TimeWarperDomainManager.DomainCell cell) {
      for (int dx = -12; dx <= 12; dx++) {
         for (int dz = -12; dz <= 12; dz++) {
            placeIfAir(world, new BlockPos(cell.loopCenterX() + dx, 64, cell.centerZ() + dz), Blocks.BLACK_CONCRETE.getDefaultState());
         }
      }
   }

   private static void placeIfAir(ServerWorld world, BlockPos pos, BlockState state) {
      if (!world.isChunkLoaded(ChunkPos.toLong(pos))) {
         world.getChunk(pos);
      }

      if (world.getBlockState(pos).isAir()) {
         world.setBlockState(pos, state, 2);
      }
   }

   private static boolean insideCell(ServerPlayerEntity player, TimeWarperDomainManager.DomainCell cell) {
      ServerWorld var3 = player.getEntityWorld();
      return isTimeDomain(var3 instanceof ServerWorld ? var3 : null) && insideCell(player.getBlockPos(), cell);
   }

   private static boolean insideCell(BlockPos pos, TimeWarperDomainManager.DomainCell cell) {
      return Math.abs(pos.getX() - cell.centerX()) <= 11 && Math.abs(pos.getZ() - cell.centerZ()) <= 11 && pos.getY() >= 65 && pos.getY() < 92;
   }

   private static boolean insideSessionArea(ServerPlayerEntity player, TimeWarperDomainManager.DomainSession session) {
      if (player != null && session != null) {
         ServerWorld var3 = player.getEntityWorld();
         if (isTimeDomain(var3 instanceof ServerWorld ? var3 : null)) {
            if (!session.chunkDupingMode) {
               return insideLoopArena(player, session.cell);
            }

            if (insideCell(player.getBlockPos(), session.cell)) {
               return true;
            }

            return PortableChunkStorage.isReplicaChunk(player.getEntityWorld().getServer(), session.ownerId, player.getChunkPos());
         }
      }

      return false;
   }

   private static boolean insideLoopArena(ServerPlayerEntity player, TimeWarperDomainManager.DomainCell cell) {
      if (player != null) {
         ServerWorld var3 = player.getEntityWorld();
         if (isTimeDomain(var3 instanceof ServerWorld ? var3 : null)) {
            double half = 12.5;
            return Math.abs(player.getX() - cell.loopCenterX()) <= half
               && Math.abs(player.getZ() - cell.centerZ()) <= half
               && player.getY() >= 62.0
               && player.getY() < 320.0;
         }
      }

      return false;
   }

   private static void wrapLoopArena(ServerPlayerEntity player, TimeWarperDomainManager.DomainCell cell) {
      double half = 12.5;
      double width = half * 2.0;
      double x = wrapCoordinate(player.getX(), cell.loopCenterX(), half, width);
      double z = wrapCoordinate(player.getZ(), cell.centerZ(), half, width);
      double y = Math.max(65.0, player.getY());
      if (x != player.getX() || z != player.getZ() || y != player.getY()) {
         Vec3d velocity = player.getVelocity();
         ServerWorld world = player.getEntityWorld();
         if (player.teleport(world, x, y, z, Set.of(), player.getYaw(), player.getPitch(), false)) {
            player.setVelocity(velocity);
            ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
         }
      }
   }

   private static double wrapCoordinate(double value, double center, double half, double width) {
      double relative = value - center;
      if (!(relative >= half) && !(relative < -half)) {
         return value;
      } else {
         relative = ((relative + half) % width + width) % width - half;
         return center + relative;
      }
   }

   private static double[] landingOffset(int slot) {
      return switch (Math.floorMod(slot, 12)) {
         case 1 -> new double[]{2.0, 0.0};
         case 2 -> new double[]{-2.0, 0.0};
         case 3 -> new double[]{0.0, 2.0};
         case 4 -> new double[]{0.0, -2.0};
         case 5 -> new double[]{3.0, 3.0};
         case 6 -> new double[]{-3.0, 3.0};
         case 7 -> new double[]{3.0, -3.0};
         case 8 -> new double[]{-3.0, -3.0};
         case 9 -> new double[]{5.0, 0.0};
         case 10 -> new double[]{-5.0, 0.0};
         case 11 -> new double[]{0.0, 5.0};
         default -> new double[]{0.0, 0.0};
      };
   }

   private static boolean transfer(ServerPlayerEntity player, ServerWorld destination, double x, double y, double z) {
      return destination != null && player.teleport(destination, x, y, z, Set.of(), player.getYaw(), player.getPitch(), true);
   }

   private static final class DomainCell {
      final int gridX;
      final int gridZ;
      boolean initialized;
      boolean loopArenaInitialized;

      DomainCell(int gridX, int gridZ, boolean initialized) {
         this.gridX = gridX;
         this.gridZ = gridZ;
         this.initialized = initialized;
      }

      int centerX() {
         return this.gridX * 64 + 32;
      }

      int centerZ() {
         return this.gridZ * 64 + 32;
      }

      int loopCenterX() {
         return this.centerX() + 32;
      }
   }

   private static final class DomainSession {
      final UUID ownerId;
      final TimeWarperDomainManager.DomainCell cell;
      final Map<UUID, TimeWarperDomainManager.PlayerOrigin> origins = new HashMap<>();
      final boolean chunkDupingMode;
      final ChunkPos entryTarget;
      boolean ownerMovedWithSwap;

      DomainSession(UUID ownerId, TimeWarperDomainManager.DomainCell cell, boolean chunkDupingMode, ChunkPos entryTarget) {
         this.ownerId = ownerId;
         this.cell = cell;
         this.chunkDupingMode = chunkDupingMode;
         this.entryTarget = entryTarget;
      }
   }

   private static final class DomainState extends PersistentState {
      final Map<UUID, TimeWarperDomainManager.DomainCell> cells = new HashMap<>();
      final Map<UUID, TimeWarperDomainManager.PlayerOrigin> pendingReturns = new HashMap<>();
      private final Map<Long, UUID> ownersByGrid = new HashMap<>();

      TimeWarperDomainManager.DomainCell getOrCreateCell(UUID owner) {
         TimeWarperDomainManager.DomainCell existing = this.cells.get(owner);
         if (existing != null) {
            return existing;
         } else {
            int start = Math.floorMod(owner.hashCode(), 1048576);

            for (int attempt = 0; attempt < 1048576; attempt++) {
               int index = (start + attempt) % 1048576;
               int gridX = index % 1024;
               int gridZ = index / 1024;
               long key = gridKey(gridX, gridZ);
               if (!this.ownersByGrid.containsKey(key)) {
                  TimeWarperDomainManager.DomainCell cell = new TimeWarperDomainManager.DomainCell(gridX, gridZ, false);
                  this.cells.put(owner, cell);
                  this.ownersByGrid.put(key, owner);
                  this.markDirty();
                  return cell;
               }
            }

            throw new IllegalStateException("No free Time Warper Domain storage cells remain.");
         }
      }

      private NbtCompound toNbt() {
         NbtCompound root = new NbtCompound();
         NbtList cellList = new NbtList();

         for (Entry<UUID, TimeWarperDomainManager.DomainCell> entry : this.cells.entrySet()) {
            NbtCompound value = new NbtCompound();
            value.putString("Owner", entry.getKey().toString());
            value.putInt("GridX", entry.getValue().gridX);
            value.putInt("GridZ", entry.getValue().gridZ);
            value.putBoolean("Initialized", entry.getValue().initialized);
            value.putBoolean("LoopArenaInitialized", entry.getValue().loopArenaInitialized);
            cellList.add(value);
         }

         root.put("Cells", cellList);
         NbtList returns = new NbtList();

         for (Entry<UUID, TimeWarperDomainManager.PlayerOrigin> entry : this.pendingReturns.entrySet()) {
            NbtCompound value = entry.getValue().toNbt();
            value.putString("Player", entry.getKey().toString());
            returns.add(value);
         }

         root.put("PendingReturns", returns);
         return root;
      }

      private static TimeWarperDomainManager.DomainState fromNbt(NbtCompound root) {
         TimeWarperDomainManager.DomainState state = new TimeWarperDomainManager.DomainState();

         for (NbtElement element : root.getListOrEmpty("Cells")) {
            if (element instanceof NbtCompound value) {
               try {
                  UUID owner = UUID.fromString(value.getString("Owner", ""));
                  int gridX = value.getInt("GridX", -1);
                  int gridZ = value.getInt("GridZ", -1);
                  if (gridX >= 0 && gridX < 1024 && gridZ >= 0 && gridZ < 1024 && !state.cells.containsKey(owner)) {
                     long key = gridKey(gridX, gridZ);
                     if (!state.ownersByGrid.containsKey(key)) {
                        TimeWarperDomainManager.DomainCell cell = new TimeWarperDomainManager.DomainCell(gridX, gridZ, value.getBoolean("Initialized", false));
                        cell.loopArenaInitialized = value.getBoolean("LoopArenaInitialized", false);
                        state.cells.put(owner, cell);
                        state.ownersByGrid.put(key, owner);
                     }
                  }
               } catch (IllegalArgumentException var12) {
               }
            }
         }

         for (NbtElement elementx : root.getListOrEmpty("PendingReturns")) {
            if (elementx instanceof NbtCompound value) {
               try {
                  UUID player = UUID.fromString(value.getString("Player", ""));
                  TimeWarperDomainManager.PlayerOrigin origin = TimeWarperDomainManager.PlayerOrigin.fromNbt(value);
                  if (origin != null) {
                     state.pendingReturns.put(player, origin);
                  }
               } catch (IllegalArgumentException var11) {
               }
            }
         }

         return state;
      }

      private static long gridKey(int gridX, int gridZ) {
         return (long)gridX << 32 ^ gridZ & 4294967295L;
      }
   }

   private static enum EndReason {
      MANUAL_CLOSE,
      OWNER_DIED,
      OWNER_DISCONNECTED,
      OWNER_LEFT_DIMENSION,
      OWNER_LEFT_CELL,
      OWNER_POWER_REMOVED,
      SERVER_SHUTDOWN,
      OPEN_FAILED;
   }

   private record PlayerOrigin(String dimension, double x, double y, double z, float yaw, float pitch) {
      static TimeWarperDomainManager.PlayerOrigin capture(ServerPlayerEntity player) {
         return new TimeWarperDomainManager.PlayerOrigin(
            player.getEntityWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()
         );
      }

      NbtCompound toNbt() {
         NbtCompound nbt = new NbtCompound();
         nbt.putString("Dimension", this.dimension);
         nbt.putDouble("X", this.x);
         nbt.putDouble("Y", this.y);
         nbt.putDouble("Z", this.z);
         nbt.putFloat("Yaw", this.yaw);
         nbt.putFloat("Pitch", this.pitch);
         return nbt;
      }

      static TimeWarperDomainManager.PlayerOrigin fromNbt(NbtCompound nbt) {
         String dimension = nbt.getString("Dimension", "");
         if (Identifier.tryParse(dimension) != null && dimension.length() <= 128) {
            double x = nbt.getDouble("X", Double.NaN);
            double y = nbt.getDouble("Y", Double.NaN);
            double z = nbt.getDouble("Z", Double.NaN);
            float yaw = nbt.getFloat("Yaw", Float.NaN);
            float pitch = nbt.getFloat("Pitch", Float.NaN);
            return Double.isFinite(x)
                  && Double.isFinite(y)
                  && Double.isFinite(z)
                  && Float.isFinite(yaw)
                  && Float.isFinite(pitch)
                  && !(Math.abs(x) > 2.9999984E7)
                  && !(Math.abs(z) > 2.9999984E7)
                  && !(y < -2048.0)
                  && !(y > 4096.0)
               ? new TimeWarperDomainManager.PlayerOrigin(dimension, x, y, z, yaw, MathHelper.clamp(pitch, -90.0F, 90.0F))
               : null;
         } else {
            return null;
         }
      }

      boolean teleport(ServerPlayerEntity player, MinecraftServer server) {
         Identifier id = Identifier.tryParse(this.dimension);
         ServerWorld destination = id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
         if (destination == null) {
            destination = server.getOverworld();
         }

         if (player.teleport(destination, this.x, this.y, this.z, Set.of(), this.yaw, this.pitch, true)) {
            return true;
         } else {
            BlockPos spawn = server.getOverworld().getSpawnPoint().getPos();
            return player.teleport(server.getOverworld(), spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, Set.of(), this.yaw, this.pitch, true);
         }
      }
   }
}
