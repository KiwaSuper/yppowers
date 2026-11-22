package com.yppowers.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-persisted global Grace period that blocks PvP and power ability activations. */
public final class GraceSystem extends PersistentState {
    private static final long NOTICE_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> LAST_ABILITY_NOTICE = new HashMap<>();
    private static final Map<UUID, Long> LAST_PVP_NOTICE = new HashMap<>();

    private int ticksRemaining;

    private static final Codec<GraceSystem> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                GraceSystem sys = new GraceSystem();
                if (element instanceof NbtCompound compound) {
                    sys.ticksRemaining = Math.max(0, compound.getInt("TicksRemaining", 0));
                }
                return sys;
            },
            sys -> {
                NbtCompound nbt = new NbtCompound();
                nbt.putInt("TicksRemaining", Math.max(0, sys.ticksRemaining));
                return new Dynamic<>(NbtOps.INSTANCE, nbt);
            }
    );

    private static final PersistentStateType<GraceSystem> STATE_TYPE = new PersistentStateType<>(
            "yppowers_v2_grace", GraceSystem::new, CODEC, null
    );

    private GraceSystem() {}

    public static GraceSystem get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public static boolean isActive(MinecraftServer server) {
        return server != null && get(server).ticksRemaining > 0;
    }

    public int getTicksRemaining() {
        return Math.max(0, ticksRemaining);
    }

    public void setTicksRemaining(int ticks) {
        int clamped = Math.max(0, ticks);
        if (ticksRemaining != clamped) {
            ticksRemaining = clamped;
            markDirty();
        }
    }

    public static void serverTick(MinecraftServer server) {
        GraceSystem grace = get(server);
        if (grace.ticksRemaining <= 0) return;
        grace.ticksRemaining--;
        if (grace.ticksRemaining == 0 || grace.ticksRemaining % 20 == 0) {
            grace.markDirty();
        }
        if (grace.ticksRemaining == 0) {
            server.getPlayerManager().broadcast(
                    Text.literal("Grace period ended. Abilities and PvP are enabled again.").formatted(Formatting.GREEN),
                    false
            );
        }
    }

    public static boolean blockAbility(ServerPlayerEntity player) {
        if (player == null) return false;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (!isActive(server)) return false;
        sendThrottled(player, LAST_ABILITY_NOTICE,
                Text.literal("Abilities are disabled during the grace period.").formatted(Formatting.RED));
        return true;
    }

    public static void notifyPvpBlocked(ServerPlayerEntity player) {
        if (player == null) return;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (!isActive(server)) return;
        sendThrottled(player, LAST_PVP_NOTICE,
                Text.literal("PvP is disabled during the grace period.").formatted(Formatting.RED));
    }

    public static ServerPlayerEntity findPlayerAttacker(DamageSource source) {
        if (source == null) return null;
        ServerPlayerEntity direct = resolvePlayer(source.getAttacker());
        return direct != null ? direct : resolvePlayer(source.getSource());
    }

    private static ServerPlayerEntity resolvePlayer(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) return player;
        if (entity instanceof ProjectileEntity proj && proj.getOwner() instanceof ServerPlayerEntity owner) return owner;
        if (entity instanceof LightningEntity lightning) return lightning.getChanneler();
        return null;
    }

    private static void sendThrottled(ServerPlayerEntity player, Map<UUID, Long> cache, Text message) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        long now = server.getTicks();
        Long last = cache.get(player.getUuid());
        if (last == null || now - last >= NOTICE_INTERVAL_TICKS) {
            player.sendMessage(message, true);
            cache.put(player.getUuid(), now);
        }
        if (cache.size() > 512) {
            cache.entrySet().removeIf(e -> now - e.getValue() > 1200L);
        }
    }

    public static void clearRuntimeState() {
        LAST_ABILITY_NOTICE.clear();
        LAST_PVP_NOTICE.clear();
    }
}
