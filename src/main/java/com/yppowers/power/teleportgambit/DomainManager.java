package com.yppowers.power.teleportgambit;

import com.yppowers.YPPowersMod;
import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.PowerId;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.TempBlockSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative manager for Teleport Gambit's persistent pocket Domain ({@code yppowers:tg_domain}). */
final class DomainManager {
    static final RegistryKey<World> DOMAIN_KEY = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of(YPPowersMod.MOD_ID, "tg_domain"));
    static final String CD_DOMAIN = "teleport_gambit_domain";
    private static final String NBT_RETURN = "PendingDomainReturn";

    private record SavedLocation(RegistryKey<World> dim, double x, double y, double z, float yaw, float pitch) {
        NbtCompound toNbt() {
            NbtCompound n = new NbtCompound();
            n.putString("Dim", dim.getValue().toString());
            n.putDouble("X", x);
            n.putDouble("Y", y);
            n.putDouble("Z", z);
            n.putFloat("Yaw", yaw);
            n.putFloat("Pitch", pitch);
            return n;
        }

        static SavedLocation fromNbt(NbtCompound n) {
            if (n == null || n.isEmpty()) return null;
            Identifier id = Identifier.tryParse(n.getString("Dim", "minecraft:overworld"));
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, id == null ? Identifier.ofVanilla("overworld") : id);
            return new SavedLocation(key, n.getDouble("X", 0), n.getDouble("Y", 80), n.getDouble("Z", 0),
                    n.getFloat("Yaw", 0), n.getFloat("Pitch", 0));
        }

        void teleport(ServerPlayerEntity player, MinecraftServer server) {
            ServerWorld dest = server.getWorld(dim);
            if (dest == null) dest = server.getOverworld();
            player.teleport(dest, x, y, z, Set.of(), yaw, pitch, true);
        }
    }

    private static final class Charge {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d center;
        final double pullRadius;
        int ticksRemaining;

        Charge(UUID ownerId, ServerWorld world, Vec3d center, double pullRadius, int ticksRemaining) {
            this.ownerId = ownerId;
            this.world = world;
            this.center = center;
            this.pullRadius = pullRadius;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final class DomainSession {
        final UUID ownerId;
        final Map<UUID, SavedLocation> origins = new HashMap<>();
        final Set<UUID> visitors = new HashSet<>();
        int ticksRemaining = 20 * 60; // 60 seconds default domain fight

        DomainSession(UUID ownerId) {
            this.ownerId = ownerId;
        }
    }

    private record BoostSpec(RegistryEntry<EntityAttribute> attr, Identifier modId) {}
    private static final List<BoostSpec> OWNER_BOOSTS = List.of(
            boost(EntityAttributes.MAX_HEALTH, "max_health"),
            boost(EntityAttributes.MOVEMENT_SPEED, "speed"),
            boost(EntityAttributes.ATTACK_DAMAGE, "damage"),
            boost(EntityAttributes.ATTACK_SPEED, "attack_speed"),
            boost(EntityAttributes.ARMOR, "armor"),
            boost(EntityAttributes.KNOCKBACK_RESISTANCE, "kb")
    );

    private static BoostSpec boost(RegistryEntry<EntityAttribute> attr, String name) {
        return new BoostSpec(attr, Identifier.of(YPPowersMod.MOD_ID, "tg_domain_boost_" + name));
    }

    private final Map<UUID, Charge> charges = new HashMap<>();
    private final Map<UUID, DomainSession> sessions = new HashMap<>();
    private final Map<UUID, UUID> sessionOwnerByMember = new HashMap<>();

    boolean isBusy(UUID playerId) {
        return charges.containsKey(playerId) || sessions.containsKey(playerId);
    }

    boolean isCharging(UUID playerId) {
        return charges.containsKey(playerId);
    }

    boolean isInDomainSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    void beginCharge(ServerPlayerEntity caster) {
        if (!(caster.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        ServerWorld domain = world.getServer().getWorld(DOMAIN_KEY);
        if (domain == null) {
            caster.sendMessage(Text.literal("Pocket Domain dimension is unavailable.").formatted(Formatting.RED), true);
            return;
        }
        PowerConfigs.TeleportGambit cfg = YPConfig.get().powers.teleportGambit;
        BlockPos feet = caster.getBlockPos();
        // Place protective charge frame ring using TempBlockSystem
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                TempBlockSystem.setTemporary(world, feet.add(dx, -1, dz), Blocks.CRYING_OBSIDIAN.getDefaultState(),
                        cfg.domainChargeTicks + 10, "tg_domain_frame", true, null);
            }
        }
        charges.put(caster.getUuid(), new Charge(caster.getUuid(), world, caster.getEntityPos(), cfg.domainPullRadius, cfg.domainChargeTicks));
        CooldownSystem.get(world.getServer()).setPersistent(caster.getUuid(), CD_DOMAIN, cfg.domainCooldownTicks);
        VfxSystem.playSound(world, caster.getEntityPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.3f, 0.7f);
        caster.sendMessage(Text.literal("DOMAIN EXPANSION CHARGING...").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    void serverTick(MinecraftServer server) {
        // Tick active charges
        Iterator<Map.Entry<UUID, Charge>> cIt = charges.entrySet().iterator();
        while (cIt.hasNext()) {
            Charge c = cIt.next().getValue();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(c.ownerId);
            if (owner == null || !owner.isAlive() || owner.getEntityWorld() != c.world) {
                cIt.remove();
                continue;
            }
            c.ticksRemaining--;
            if (c.ticksRemaining % 6 == 0) {
                VfxSystem.ring(c.world, ParticleTypes.PORTAL, c.center.add(0, 0.3, 0), c.pullRadius, 36, 0.05);
                VfxSystem.lightPillar(c.world, ParticleTypes.END_ROD, c.center, 12.0, 14);
            }
            if (c.ticksRemaining <= 0) {
                cIt.remove();
                openDomain(server, owner, c);
            }
        }

        // Tick active domain sessions
        Iterator<Map.Entry<UUID, DomainSession>> sIt = sessions.entrySet().iterator();
        while (sIt.hasNext()) {
            DomainSession s = sIt.next().getValue();
            s.ticksRemaining--;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(s.ownerId);
            if (owner == null || !owner.isAlive() || s.ticksRemaining <= 0) {
                sIt.remove();
                closeSession(server, s);
            }
        }
    }

    private void openDomain(MinecraftServer server, ServerPlayerEntity owner, Charge c) {
        ServerWorld domain = server.getWorld(DOMAIN_KEY);
        if (domain == null) return;
        DomainPlatformBuilder.ensureArenaBuilt(domain);

        DomainSession session = new DomainSession(owner.getUuid());
        SavedLocation ownerOrigin = capture(owner);
        session.origins.put(owner.getUuid(), ownerOrigin);
        savePendingReturn(server, owner.getUuid(), ownerOrigin);
        sessionOwnerByMember.put(owner.getUuid(), owner.getUuid());

        owner.teleport(domain, 0.5, DomainPlatformBuilder.FLOOR_Y + 1.0, 0.5, Set.of(), owner.getYaw(), owner.getPitch(), true);
        applyOwnerBoosts(owner);

        int idx = 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p == owner || !p.isAlive() || p.getEntityWorld() != c.world) continue;
            if (p.getEntityPos().distanceTo(c.center) > c.pullRadius) continue;
            SavedLocation loc = capture(p);
            session.origins.put(p.getUuid(), loc);
            session.visitors.add(p.getUuid());
            savePendingReturn(server, p.getUuid(), loc);
            sessionOwnerByMember.put(p.getUuid(), owner.getUuid());

            double angle = (Math.PI * 2.0 * idx++) / 6.0;
            double x = Math.cos(angle) * 14.0;
            double z = Math.sin(angle) * 14.0;
            p.teleport(domain, x + 0.5, DomainPlatformBuilder.FLOOR_Y + 1.0, z + 0.5, Set.of(), p.getYaw(), p.getPitch(), true);
            p.sendMessage(Text.literal("Pulled into " + owner.getName().getString() + "'s Pocket Domain!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
        }

        sessions.put(owner.getUuid(), session);
        VfxSystem.playSound(domain, new Vec3d(0, DomainPlatformBuilder.FLOOR_Y + 1, 0), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.0f, 0.8f);
    }

    private void closeSession(MinecraftServer server, DomainSession session) {
        for (Map.Entry<UUID, SavedLocation> entry : session.origins.entrySet()) {
            UUID id = entry.getKey();
            sessionOwnerByMember.remove(id);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null) {
                if (id.equals(session.ownerId)) removeOwnerBoosts(p);
                if (p.isAlive()) {
                    entry.getValue().teleport(p, server);
                    clearPendingReturn(server, id);
                    p.sendMessage(Text.literal("The Pocket Domain collapsed.").formatted(Formatting.AQUA), true);
                }
            }
        }
    }

    void onAnyPlayerDeath(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        if (sessions.containsKey(player.getUuid())) {
            DomainSession s = sessions.remove(player.getUuid());
            removeOwnerBoosts(player);
            closeSession(server, s);
        }
    }

    void onAnyPlayerRespawn(ServerPlayerEntity newPlayer) {
        MinecraftServer server = newPlayer.getEntityWorld().getServer();
        if (server == null) return;
        UUID ownerId = sessionOwnerByMember.get(newPlayer.getUuid());
        if (ownerId != null && sessions.containsKey(ownerId) && !ownerId.equals(newPlayer.getUuid())) {
            // Pull visitor back in if Domain is still active
            ServerWorld domain = server.getWorld(DOMAIN_KEY);
            if (domain != null) {
                newPlayer.teleport(domain, 10.5, DomainPlatformBuilder.FLOOR_Y + 1.0, 0.5, Set.of(), newPlayer.getYaw(), newPlayer.getPitch(), true);
            }
            return;
        }
        restorePendingIfOutsideSession(server, newPlayer);
    }

    void onAnyPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) restorePendingIfOutsideSession(server, player);
    }

    void shutdown(MinecraftServer server) {
        for (DomainSession s : sessions.values()) {
            closeSession(server, s);
        }
        sessions.clear();
        charges.clear();
        sessionOwnerByMember.clear();
    }

    private void restorePendingIfOutsideSession(MinecraftServer server, ServerPlayerEntity player) {
        if (sessionOwnerByMember.containsKey(player.getUuid())) return;
        SavedLocation pending = getPendingReturn(server, player.getUuid());
        if (pending != null) {
            removeOwnerBoosts(player);
            pending.teleport(player, server);
            clearPendingReturn(server, player.getUuid());
        }
    }

    private static SavedLocation capture(ServerPlayerEntity p) {
        return new SavedLocation(p.getEntityWorld().getRegistryKey(), p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch());
    }

    private static void savePendingReturn(MinecraftServer server, UUID uuid, SavedLocation loc) {
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(uuid, PowerId.GAMBIT).put(NBT_RETURN, loc.toNbt());
        store.markDirty();
    }

    private static SavedLocation getPendingReturn(MinecraftServer server, UUID uuid) {
        NbtCompound nbt = PlayerDataStore.get(server).getPowerNbt(uuid, PowerId.GAMBIT);
        return nbt.contains(NBT_RETURN) ? SavedLocation.fromNbt(nbt.getCompoundOrEmpty(NBT_RETURN)) : null;
    }

    private static void clearPendingReturn(MinecraftServer server, UUID uuid) {
        PlayerDataStore store = PlayerDataStore.get(server);
        NbtCompound nbt = store.getPowerNbt(uuid, PowerId.GAMBIT);
        if (nbt.contains(NBT_RETURN)) {
            nbt.remove(NBT_RETURN);
            store.markDirty();
        }
    }

    private static void applyOwnerBoosts(ServerPlayerEntity owner) {
        for (BoostSpec spec : OWNER_BOOSTS) {
            EntityAttributeInstance inst = owner.getAttributeInstance(spec.attr());
            if (inst == null) continue;
            if (inst.getModifier(spec.modId()) != null) inst.removeModifier(spec.modId());
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    spec.modId(), 1.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void removeOwnerBoosts(ServerPlayerEntity owner) {
        for (BoostSpec spec : OWNER_BOOSTS) {
            EntityAttributeInstance inst = owner.getAttributeInstance(spec.attr());
            if (inst != null && inst.getModifier(spec.modId()) != null) {
                inst.removeModifier(spec.modId());
            }
        }
        if (owner.getHealth() > owner.getMaxHealth()) owner.setHealth(owner.getMaxHealth());
    }
}
