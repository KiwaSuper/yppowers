package com.yppowers.system;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified minion/summon lifecycle tracker across Fire God, Ice Emperor, Fallout Warrior,
 * Spirit Channeler, and Time Warper.
 */
public final class SummonSystem {
    public static final String OWNER_TAG_PREFIX = "yppowers_owner_";
    public static final String CATEGORY_TAG_PREFIX = "yppowers_cat_";
    public static final String ROGUE_TAG = "yppowers_rogue";

    public static final class TrackedSummon {
        public final UUID entityId;
        public final UUID ownerId;
        public final String category;
        public int ticksRemaining;
        public final boolean rogue;

        TrackedSummon(UUID entityId, UUID ownerId, String category, int ticksRemaining, boolean rogue) {
            this.entityId = entityId;
            this.ownerId = ownerId;
            this.category = category;
            this.ticksRemaining = ticksRemaining;
            this.rogue = rogue;
        }
    }

    private static final Map<UUID, TrackedSummon> ACTIVE = new HashMap<>();

    private SummonSystem() {}

    public static void register(MobEntity mob, UUID ownerId, String category, int lifespanTicks, boolean rogue) {
        if (mob == null || ownerId == null) return;
        mob.addCommandTag(OWNER_TAG_PREFIX + ownerId);
        if (category != null && !category.isEmpty()) {
            mob.addCommandTag(CATEGORY_TAG_PREFIX + category);
        }
        if (rogue) {
            mob.addCommandTag(ROGUE_TAG);
        }
        ACTIVE.put(mob.getUuid(), new TrackedSummon(mob.getUuid(), ownerId, category == null ? "" : category, lifespanTicks, rogue));
    }

    public static UUID getOwnerUuid(Entity entity) {
        if (entity == null) return null;
        TrackedSummon tracked = ACTIVE.get(entity.getUuid());
        if (tracked != null) return tracked.ownerId;
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(OWNER_TAG_PREFIX)) {
                try {
                    return UUID.fromString(tag.substring(OWNER_TAG_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    public static boolean isRogue(Entity entity) {
        if (entity == null) return false;
        TrackedSummon tracked = ACTIVE.get(entity.getUuid());
        return (tracked != null && tracked.rogue) || entity.getCommandTags().contains(ROGUE_TAG);
    }

    public static boolean hasCategory(Entity entity, String category) {
        if (entity == null || category == null) return false;
        TrackedSummon tracked = ACTIVE.get(entity.getUuid());
        if (tracked != null) return category.equals(tracked.category);
        return entity.getCommandTags().contains(CATEGORY_TAG_PREFIX + category);
    }

    public static int countActive(MinecraftServer server, UUID ownerId, String category) {
        int count = 0;
        for (TrackedSummon s : ACTIVE.values()) {
            if (s.ownerId.equals(ownerId) && s.category.equals(category) && findEntity(server, s.entityId) != null) {
                count++;
            }
        }
        return count;
    }

    public static List<MobEntity> getOwnedMobs(MinecraftServer server, UUID ownerId, String category) {
        List<MobEntity> result = new ArrayList<>();
        for (TrackedSummon s : ACTIVE.values()) {
            if (s.ownerId.equals(ownerId) && (category == null || s.category.equals(category))) {
                Entity e = findEntity(server, s.entityId);
                if (e instanceof MobEntity mob && mob.isAlive()) {
                    result.add(mob);
                }
            }
        }
        return result;
    }

    public static void discardCategory(MinecraftServer server, UUID ownerId, String category) {
        Iterator<Map.Entry<UUID, TrackedSummon>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            TrackedSummon s = it.next().getValue();
            if (s.ownerId.equals(ownerId) && (category == null || s.category.equals(category))) {
                Entity e = findEntity(server, s.entityId);
                if (e != null && !e.isRemoved()) e.discard();
                it.remove();
            }
        }
    }

    public static void untrack(UUID entityId) {
        ACTIVE.remove(entityId);
    }

    public static TrackedSummon getTracked(UUID entityId) {
        return ACTIVE.get(entityId);
    }

    public static void serverTick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, TrackedSummon>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            TrackedSummon s = it.next().getValue();
            Entity entity = findEntity(server, s.entityId);
            if (!(entity instanceof MobEntity mob) || !mob.isAlive() || mob.isRemoved()) {
                it.remove();
                continue;
            }

            if (s.ticksRemaining > 0) {
                s.ticksRemaining--;
                if (s.ticksRemaining == 0) {
                    mob.discard();
                    it.remove();
                    continue;
                }
            }

            // Periodically refresh target respecting alliances and Grace
            if (server.getTicks() % 10 == 0) {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(s.ownerId);
                if (GraceSystem.isActive(server)) {
                    mob.setTarget(null);
                    continue;
                }
                if (s.rogue) {
                    if (owner != null && owner.isAlive() && mob.getEntityWorld() == owner.getEntityWorld()) {
                        mob.setTarget(owner);
                    }
                    continue;
                }
                if (owner != null && owner.isAlive()) {
                    LivingEntity currentTarget = mob.getTarget();
                    if (currentTarget != null && (!currentTarget.isAlive() || !CombatUtil.canHarm(owner, currentTarget))) {
                        mob.setTarget(null);
                        currentTarget = null;
                    }
                    if (currentTarget == null) {
                        LivingEntity next = CombatUtil.pickDefenseTarget(owner, 24.0);
                        if (next != null) mob.setTarget(next);
                    }
                    // Teleport closer if far behind owner in same world
                    if (mob.getEntityWorld() == owner.getEntityWorld() && mob.squaredDistanceTo(owner) > 36.0 * 36.0) {
                        mob.refreshPositionAndAngles(owner.getX(), owner.getY(), owner.getZ(), mob.getYaw(), mob.getPitch());
                    }
                }
            }
        }
    }

    public static Entity findEntity(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) return null;
        for (ServerWorld world : server.getWorlds()) {
            Entity e = world.getEntity(entityId);
            if (e != null) return e;
        }
        return null;
    }

    public static void clearRuntimeState() {
        ACTIVE.clear();
    }
}
