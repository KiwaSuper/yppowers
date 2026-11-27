package com.yppowers.alliance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public class AllianceManager extends PersistentState {
   public static final long INVITE_LIFETIME_TICKS = 1200L;

   public enum InviteResult {
      SENT,
      ALREADY_ALLIED,
      ALREADY_PENDING,
      SELF_INVITE
   }

   private final Map<UUID, Set<UUID>> alliances = new HashMap<>();
   private final Map<UUID, String> knownNames = new HashMap<>();
   private final Map<UUID, Map<UUID, Long>> pendingInvites = new HashMap<>();

   private static final Codec<AllianceManager> CODEC = Codec.PASSTHROUGH.xmap(dynamic -> {
      NbtElement element = (NbtElement)dynamic.convert(NbtOps.INSTANCE).getValue();
      return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
   }, manager -> new Dynamic<>(NbtOps.INSTANCE, manager.toNbt()));
   private static final PersistentStateType<AllianceManager> STATE_TYPE = new PersistentStateType<>("yppowers_alliances", AllianceManager::new, CODEC, null);

   public void rememberName(UUID uuid, String name) {
      if (uuid == null || name == null || name.isBlank()) {
         return;
      }
      if (!name.equals(this.knownNames.get(uuid))) {
         this.knownNames.put(uuid, name);
         this.markDirty();
      }
   }

   public String getDisplayName(MinecraftServer server, UUID uuid) {
      if (server != null) {
         ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
         if (online != null) {
            return online.getName().getString();
         }
      }
      return this.knownNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
   }

   public void addAlliance(UUID a, UUID b) {
      if (a == null || b == null || a.equals(b)) {
         return;
      }
      this.alliances.computeIfAbsent(a, k -> new HashSet<>()).add(b);
      this.alliances.computeIfAbsent(b, k -> new HashSet<>()).add(a);
      this.markDirty();
   }

   public void removeAlliance(UUID a, UUID b) {
      Set<UUID> aAllies = this.alliances.get(a);
      if (aAllies != null) {
         aAllies.remove(b);
         if (aAllies.isEmpty()) {
            this.alliances.remove(a);
         }
      }

      Set<UUID> bAllies = this.alliances.get(b);
      if (bAllies != null) {
         bAllies.remove(a);
         if (bAllies.isEmpty()) {
            this.alliances.remove(b);
         }
      }

      this.markDirty();
   }

   public boolean areAllied(UUID a, UUID b) {
      if (a == null || b == null) {
         return false;
      }
      if (a.equals(b)) {
         return true;
      }
      Set<UUID> aAllies = this.alliances.get(a);
      return aAllies != null && aAllies.contains(b);
   }

   public Set<UUID> getAllies(UUID player) {
      return new HashSet<>(this.alliances.getOrDefault(player, new HashSet<>()));
   }

   public boolean isAlly(ServerPlayerEntity p1, ServerPlayerEntity p2) {
      return p1 != null && p2 != null && (p1 == p2 || p1.getUuid().equals(p2.getUuid()) || this.areAllied(p1.getUuid(), p2.getUuid()));
   }

   public boolean isAlly(ServerPlayerEntity player, Entity entity) {
      return entity instanceof ServerPlayerEntity other && this.isAlly(player, other);
   }

   public InviteResult sendInvite(UUID sender, UUID target, long currentTick) {
      if (sender == null || target == null || sender.equals(target)) {
         return InviteResult.SELF_INVITE;
      }
      if (this.areAllied(sender, target)) {
         return InviteResult.ALREADY_ALLIED;
      }
      this.pruneExpiredInvites(target, currentTick);
      Map<UUID, Long> inbox = this.pendingInvites.computeIfAbsent(target, ignored -> new HashMap<>());
      if (inbox.containsKey(sender)) {
         return InviteResult.ALREADY_PENDING;
      }
      inbox.put(sender, currentTick + INVITE_LIFETIME_TICKS);
      return InviteResult.SENT;
   }

   public boolean hasPendingInvite(UUID sender, UUID target, long currentTick) {
      if (sender == null || target == null) {
         return false;
      }
      this.pruneExpiredInvites(target, currentTick);
      Map<UUID, Long> inbox = this.pendingInvites.get(target);
      return inbox != null && inbox.containsKey(sender);
   }

   public boolean acceptInvite(UUID target, UUID sender, long currentTick) {
      if (sender == null || target == null) {
         return false;
      }
      this.pruneExpiredInvites(target, currentTick);
      Map<UUID, Long> inbox = this.pendingInvites.get(target);
      if (inbox == null || inbox.remove(sender) == null) {
         return false;
      }
      if (inbox.isEmpty()) {
         this.pendingInvites.remove(target);
      }
      Map<UUID, Long> reverse = this.pendingInvites.get(sender);
      if (reverse != null) {
         reverse.remove(target);
         if (reverse.isEmpty()) {
            this.pendingInvites.remove(sender);
         }
      }
      this.addAlliance(sender, target);
      return true;
   }

   public boolean declineInvite(UUID target, UUID sender) {
      if (sender == null || target == null) {
         return false;
      }
      Map<UUID, Long> inbox = this.pendingInvites.get(target);
      if (inbox == null || inbox.remove(sender) == null) {
         return false;
      }
      if (inbox.isEmpty()) {
         this.pendingInvites.remove(target);
      }
      return true;
   }

   private void pruneExpiredInvites(UUID target, long currentTick) {
      Map<UUID, Long> inbox = this.pendingInvites.get(target);
      if (inbox == null) {
         return;
      }
      inbox.entrySet().removeIf(e -> currentTick >= e.getValue());
      if (inbox.isEmpty()) {
         this.pendingInvites.remove(target);
      }
   }

   public NbtCompound toNbt() {
      NbtCompound nbt = new NbtCompound();
      NbtCompound alliancesNbt = new NbtCompound();

      for (Entry<UUID, Set<UUID>> entry : this.alliances.entrySet()) {
         NbtList list = new NbtList();
         for (UUID ally : entry.getValue()) {
            list.add(NbtString.of(ally.toString()));
         }
         alliancesNbt.put(entry.getKey().toString(), list);
      }

      nbt.put("Alliances", alliancesNbt);

      NbtCompound namesNbt = new NbtCompound();
      for (Entry<UUID, String> entry : this.knownNames.entrySet()) {
         namesNbt.putString(entry.getKey().toString(), entry.getValue());
      }
      nbt.put("Names", namesNbt);
      return nbt;
   }

   public static AllianceManager fromNbt(NbtCompound nbt) {
      AllianceManager manager = new AllianceManager();
      NbtCompound alliancesNbt = nbt.getCompoundOrEmpty("Alliances");

      for (String key : alliancesNbt.getKeys()) {
         UUID player;
         try {
            player = UUID.fromString(key);
         } catch (IllegalArgumentException ignored) {
            continue;
         }

         NbtList list = alliancesNbt.getListOrEmpty(key);
         for (int i = 0; i < list.size(); i++) {
            String allyId = list.getString(i, "");
            try {
               UUID ally = UUID.fromString(allyId);
               if (!player.equals(ally)) {
                  manager.alliances.computeIfAbsent(player, k -> new HashSet<>()).add(ally);
                  manager.alliances.computeIfAbsent(ally, k -> new HashSet<>()).add(player);
               }
            } catch (IllegalArgumentException ignored) {
            }
         }
      }

      NbtCompound namesNbt = nbt.getCompoundOrEmpty("Names");
      for (String key : namesNbt.getKeys()) {
         try {
            UUID id = UUID.fromString(key);
            String name = namesNbt.getString(key, "");
            if (!name.isBlank()) {
               manager.knownNames.put(id, name);
            }
         } catch (IllegalArgumentException ignored) {
         }
      }

      return manager;
   }

   public static AllianceManager get(MinecraftServer server) {
      return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
   }
}
