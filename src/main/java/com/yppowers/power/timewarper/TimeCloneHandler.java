package com.yppowers.power.timewarper;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.PowerId;
import com.yppowers.item.ItemMarkerUtil;
import com.yppowers.system.SummonSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.UUID;

/** Handles Time Shard drops/pickups, Golden Time Shards, and Time Clone / Elite Clone lifecycle. */
public final class TimeCloneHandler {
    public static final String CLONE_OWNER_TAG_PREFIX = "yppowers_time_clone_owner_";
    public static final String CLONE_ELITE_TAG = "yppowers_time_clone_elite";
    private static final String CAT_CLONE = "time_warper_clone";
    private static final String NBT_SHARDS = "TimeShards";
    private static final String NBT_STORED_CLONES = "StoredClones";
    private static final String ITEM_TIME_SHARD = "YPTimeShard";
    private static final String ITEM_GOLDEN_SHARD = "YPGoldenTimeShard";

    public int getShardCount(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return 0;
        return Math.max(0, PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.TIME_WARPER).getInt(NBT_SHARDS, 0));
    }

    public void addShards(ServerPlayerEntity player, int delta) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        NbtCompound nbt = store.getPowerNbt(player.getUuid(), PowerId.TIME_WARPER);
        int next = Math.max(0, nbt.getInt(NBT_SHARDS, 0) + delta);
        nbt.putInt(NBT_SHARDS, next);
        store.markDirty();
    }

    void dropShardsOnMobKill(ServerWorld world, MobEntity victim) {
        if (SummonSystem.getOwnerUuid(victim) != null) return;
        PowerConfigs.TimeWarper cfg = YPConfig.get().powers.timeWarper;

        boolean boss = victim instanceof EnderDragonEntity || victim instanceof WitherEntity
                || victim instanceof WardenEntity || victim instanceof ElderGuardianEntity;
        if (boss && world.random.nextDouble() < cfg.goldenShardDropChance) {
            ItemStack golden = new ItemStack(Items.NETHER_STAR);
            golden.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Golden Time Shard").formatted(Formatting.GOLD, Formatting.BOLD));
            ItemMarkerUtil.setFlag(golden, ITEM_GOLDEN_SHARD, true);
            ItemEntity drop = new ItemEntity(world, victim.getX(), victim.getY() + 0.5, victim.getZ(), golden);
            drop.setPickupDelay(Integer.MAX_VALUE); // touch-pickup only by Time Warper
            world.spawnEntity(drop);
            return;
        }
        if (world.random.nextDouble() < cfg.timeShardDropChance) {
            ItemStack shard = new ItemStack(Items.AMETHYST_SHARD);
            shard.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Time Shard").formatted(Formatting.LIGHT_PURPLE));
            ItemMarkerUtil.setFlag(shard, ITEM_TIME_SHARD, true);
            ItemEntity drop = new ItemEntity(world, victim.getX(), victim.getY() + 0.5, victim.getZ(), shard);
            drop.setPickupDelay(Integer.MAX_VALUE);
            drop.setDespawnImmediately(); // replaced below by short lifetime via age
            drop.setCovetedItem();
            world.spawnEntity(drop);
        }
    }

    void collectNearbyShards(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        Box box = player.getBoundingBox().expand(1.8);
        for (ItemEntity item : world.getEntitiesByClass(ItemEntity.class, box, Entity -> Entity.isAlive())) {
            ItemStack stack = item.getStack();
            if (ItemMarkerUtil.hasFlag(stack, ITEM_TIME_SHARD)) {
                int count = stack.getCount();
                addShards(player, count);
                item.discard();
                VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 0.7f, 1.4f);
                player.sendMessage(Text.literal("+" + count + " Time Shard (Total: " + getShardCount(player) + ")").formatted(Formatting.LIGHT_PURPLE), true);
            } else if (ItemMarkerUtil.hasFlag(stack, ITEM_GOLDEN_SHARD)) {
                ItemStack copy = stack.copy();
                item.discard();
                if (!player.giveItemStack(copy)) player.dropItem(copy, false);
                VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.3f);
                player.sendMessage(Text.literal("Collected Golden Time Shard!").formatted(Formatting.GOLD, Formatting.BOLD), true);
            }
        }
    }

    boolean canSummon(ServerPlayerEntity player, boolean elite) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        PowerConfigs.TimeWarper cfg = YPConfig.get().powers.timeWarper;
        if (SummonSystem.countActive(server, player.getUuid(), CAT_CLONE) >= cfg.cloneMaxActive) {
            player.sendMessage(Text.literal("Maximum active Time Clones reached (" + cfg.cloneMaxActive + ").").formatted(Formatting.YELLOW), true);
            return false;
        }
        if (elite) {
            if (!hasGoldenShards(player, cfg.eliteCloneGoldenShardsCost)) {
                player.sendMessage(Text.literal("Requires " + cfg.eliteCloneGoldenShardsCost + " Golden Time Shard(s).").formatted(Formatting.GOLD), true);
                return false;
            }
        } else {
            if (getShardCount(player) < cfg.cloneShardsCost) {
                player.sendMessage(Text.literal("Requires " + cfg.cloneShardsCost + " Time Shards (you have " + getShardCount(player) + ").").formatted(Formatting.LIGHT_PURPLE), true);
                return false;
            }
        }
        return true;
    }

    void summonClone(ServerPlayerEntity owner, boolean elite) {
        PowerConfigs.TimeWarper cfg = YPConfig.get().powers.timeWarper;
        if (elite) {
            if (!consumeGoldenShards(owner, cfg.eliteCloneGoldenShardsCost)) return;
        } else {
            addShards(owner, -cfg.cloneShardsCost);
        }
        spawnCloneEntity(owner, elite, cfg.cloneLifespanTicks);
    }

    private void spawnCloneEntity(ServerPlayerEntity owner, boolean elite, int lifespanTicks) {
        if (!(owner.getEntityWorld() instanceof ServerWorld world)) return;
        ZombieEntity clone = EntityType.ZOMBIE.create(world, SpawnReason.TRIGGERED);
        if (clone == null) return;
        clone.refreshPositionAndAngles(owner.getX() + 1.2, owner.getY(), owner.getZ() + 1.2, owner.getYaw(), 0.0f);
        clone.setBaby(false);
        clone.setPersistent();
        clone.setCustomName(Text.literal("YPCLONE|" + owner.getUuid() + "|" + (elite ? "G" : "N")));
        clone.setCustomNameVisible(false);
        clone.addCommandTag(CLONE_OWNER_TAG_PREFIX + owner.getUuid());
        if (elite) clone.addCommandTag(CLONE_ELITE_TAG);

        double hp = elite ? 60.0 : 28.0;
        double dmg = elite ? 9.0 : 5.0;
        if (clone.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
            clone.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
        }
        clone.setHealth((float) hp);
        if (clone.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
            clone.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(dmg);
        }
        clone.equipStack(EquipmentSlot.MAINHAND, new ItemStack(elite ? Items.GOLDEN_SWORD : Items.IRON_SWORD));
        world.spawnEntity(clone);

        SummonSystem.register(clone, owner.getUuid(), CAT_CLONE, lifespanTicks, false);
        VfxSystem.risingHelix(world, elite ? ParticleTypes.WAX_ON : ParticleTypes.PORTAL, clone.getEntityPos(), 1.0, 2.2, 16);
        VfxSystem.playSound(world, clone.getEntityPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0f, elite ? 1.3f : 1.0f);
    }

    /** Suspend active clones into PlayerDataStore when owner logs out, pausing remaining lifespan. */
    void suspendClones(ServerPlayerEntity owner) {
        MinecraftServer server = owner.getEntityWorld().getServer();
        if (server == null) return;
        List<MobEntity> clones = SummonSystem.getOwnedMobs(server, owner.getUuid(), CAT_CLONE);
        if (clones.isEmpty()) return;

        NbtList stored = new NbtList();
        for (MobEntity mob : clones) {
            SummonSystem.TrackedSummon tracked = SummonSystem.getTracked(mob.getUuid());
            NbtCompound entry = new NbtCompound();
            entry.putBoolean("Elite", mob.getCommandTags().contains(CLONE_ELITE_TAG));
            entry.putInt("Remaining", tracked != null ? tracked.ticksRemaining : 600);
            stored.add(entry);
            SummonSystem.untrack(mob.getUuid());
            mob.discard();
        }
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(owner.getUuid(), PowerId.TIME_WARPER).put(NBT_STORED_CLONES, stored);
        store.markDirty();
    }

    /** Restore suspended clones when owner rejoins. */
    void restoreClones(ServerPlayerEntity owner) {
        MinecraftServer server = owner.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        NbtCompound nbt = store.getPowerNbt(owner.getUuid(), PowerId.TIME_WARPER);
        NbtList stored = nbt.getListOrEmpty(NBT_STORED_CLONES);
        if (stored.isEmpty()) return;
        for (int i = 0; i < stored.size(); i++) {
            NbtCompound entry = stored.getCompoundOrEmpty(i);
            boolean elite = entry.getBoolean("Elite", false);
            int remaining = Math.max(40, entry.getInt("Remaining", 600));
            spawnCloneEntity(owner, elite, remaining);
        }
        nbt.remove(NBT_STORED_CLONES);
        store.markDirty();
    }

    void discardAllClones(ServerPlayerEntity owner) {
        MinecraftServer server = owner.getEntityWorld().getServer();
        if (server != null) SummonSystem.discardCategory(server, owner.getUuid(), CAT_CLONE);
    }

    private static boolean hasGoldenShards(ServerPlayerEntity player, int needed) {
        int found = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (ItemMarkerUtil.hasFlag(s, ITEM_GOLDEN_SHARD)) found += s.getCount();
            if (found >= needed) return true;
        }
        return false;
    }

    private static boolean consumeGoldenShards(ServerPlayerEntity player, int needed) {
        if (!hasGoldenShards(player, needed)) return false;
        int left = needed;
        for (int i = 0; i < player.getInventory().size() && left > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (ItemMarkerUtil.hasFlag(s, ITEM_GOLDEN_SHARD)) {
                int take = Math.min(left, s.getCount());
                s.decrement(take);
                left -= take;
            }
        }
        return true;
    }
}
