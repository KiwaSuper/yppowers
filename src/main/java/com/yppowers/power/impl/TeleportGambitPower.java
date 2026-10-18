package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import com.yppowers.power.PowerManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TeleportGambitPower extends Power {
    private final Map<UUID, Integer> activePads = new HashMap<>();
    private final Map<UUID, Integer> gatewayCooldowns = new HashMap<>();
    private final Map<UUID, BlockPos> padPositions = new HashMap<>();
    private final Map<UUID, BlockState> originalBlocks = new HashMap<>();
    private final Map<UUID, RegistryKey<World>> padWorlds = new HashMap<>();
    /** Prevents a player from immediately chaining into another active gateway after arrival. */
    private final Map<UUID, Long> transferLockoutUntil = new HashMap<>();
    private static final int TRANSFER_LOCKOUT_TICKS = 20;

    @Override public PowerType getType() { return PowerType.GAMBIT; }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (activePads.containsKey(uuid)) {
            player.sendMessage(Text.literal("Your gateway is already active."), true);
            return;
        }
        int cooldown = gatewayCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            player.sendMessage(Text.literal("Gateway on cooldown: " + ((cooldown + 19) / 20) + "s"), true);
            return;
        }

        // Put the portal at the block under the player's feet. Keep the original block to restore it.
        BlockPos pos = player.getBlockPos().down().toImmutable();
        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        BlockState oldState = world.getBlockState(pos);
        if (oldState.hasBlockEntity() || oldState.getHardness(world, pos) < 0) {
            player.sendMessage(Text.literal("A gateway cannot replace this block."), true);
            return;
        }
        originalBlocks.put(uuid, oldState);
        padWorlds.put(uuid, world.getRegistryKey());
        padPositions.put(uuid, pos);
        // The owner is already standing on the pad, so the next server tick should trigger it
        // immediately instead of requiring an artificial step-off/re-entry.
        activePads.put(uuid, Math.max(1, YPConfig.getInstance().teleport_gambit_gateway_duration_ticks));
        world.setBlockState(pos, Blocks.END_PORTAL.getDefaultState(), 3);
        int configuredCooldown = Math.max(0, YPConfig.getInstance().teleport_gambit_gateway_cooldown_ticks);
        if (configuredCooldown > 0) gatewayCooldowns.put(uuid, configuredCooldown);
        world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 30, 0.35, 0.05, 0.35, 0.1);
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int cooldown = gatewayCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            cooldown--;
            if (cooldown <= 0) gatewayCooldowns.remove(uuid);
            else gatewayCooldowns.put(uuid, cooldown);
        }
        if (!activePads.containsKey(uuid)) return;

        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        BlockPos padPos = padPositions.get(uuid);
        int ticksLeft = activePads.get(uuid) - 1;
        if (padPos == null || !world.getRegistryKey().equals(padWorlds.get(uuid))) {
            removePad(player, true);
            return;
        }

        ServerPlayerEntity entrant = null;
        long now = world.getTime();
        for (ServerPlayerEntity candidatePlayer : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (candidatePlayer.getEntityWorld() != world || !isAtPad(candidatePlayer, padPos)) continue;

            // A successful arrival locks this player out of all active Gambit gateways for
            // one second, preventing gate-to-gate bounce loops.
            Long lockoutUntil = transferLockoutUntil.get(candidatePlayer.getUuid());
            if (lockoutUntil != null) {
                if (now < lockoutUntil) continue;
                transferLockoutUntil.remove(candidatePlayer.getUuid());
            }

            entrant = candidatePlayer;
            break;
        }
        if (entrant != null) {
            Vec3d look = entrant.getRotationVec(1.0F).normalize();
            BlockPos dest = null;
            // Prefer a safe landing 25-50 blocks down the look direction.
            for (int distance = 25; distance <= 50; distance++) {
                BlockPos candidate = BlockPos.ofFloored(entrant.getEyePos().add(look.multiply(distance)));
                if (isSafe(world, candidate, entrant)) { dest = candidate; break; }
            }
            // If the preferred band is empty, search outward then inward for the nearest safe landing.
            if (dest == null) {
                for (int distance = 51; distance <= 256 && dest == null; distance++) {
                    BlockPos candidate = BlockPos.ofFloored(entrant.getEyePos().add(look.multiply(distance)));
                    if (isSafe(world, candidate, entrant)) dest = candidate;
                }
                for (int distance = 24; distance >= 1 && dest == null; distance--) {
                    BlockPos candidate = BlockPos.ofFloored(entrant.getEyePos().add(look.multiply(distance)));
                    if (isSafe(world, candidate, entrant)) dest = candidate;
                }
            }
            if (dest != null) {
                world.spawnParticles(ParticleTypes.PORTAL, entrant.getX(), entrant.getY(), entrant.getZ(), 24, 0.5, 0.5, 0.5, 0.1);
                transferLockoutUntil.put(entrant.getUuid(), world.getTime() + TRANSFER_LOCKOUT_TICKS);
                entrant.teleport(world, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, Set.of(), entrant.getYaw(), entrant.getPitch(), false);
                entrant.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0));
                entrant.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1));
                world.spawnParticles(ParticleTypes.PORTAL, dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5, 24, 0.5, 0.8, 0.5, 0.1);
                world.playSound(null, dest, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                removePad(player, true);
                return;
            }
        }

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, padPos.getX() + 0.5, padPos.getY() + 0.1, padPos.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.02);
        if (ticksLeft <= 0) removePad(player, true);
        else activePads.put(uuid, ticksLeft);
    }

    private static boolean isAtPad(ServerPlayerEntity player, BlockPos padPos) {
        return player.getBlockPos().equals(padPos) || player.getBlockPos().down().equals(padPos);
    }

    private boolean isSafe(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
        if (!world.isInBuildLimit(pos) || !world.isInBuildLimit(pos.up())) return false;
        BlockState floor = world.getBlockState(pos.down());
        if (!floor.isOpaque() || !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return false;
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) return false;
        if (!world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.up()).isEmpty()) return false;
        return world.getOtherEntities(player, player.getBoundingBox().offset(pos.getX() + 0.5 - player.getX(), pos.getY() - player.getY(), pos.getZ() + 0.5 - player.getZ()), e -> true).isEmpty();
    }

    private void removePad(ServerPlayerEntity player, boolean restore) {
        UUID uuid = player.getUuid();
        BlockPos pos = padPositions.remove(uuid);
        BlockState old = originalBlocks.remove(uuid);
        RegistryKey<World> dimension = padWorlds.remove(uuid);
        activePads.remove(uuid);
        if (restore && pos != null && dimension != null && player.getEntityWorld().getServer() != null) {
            ServerWorld world = player.getEntityWorld().getServer().getWorld(dimension);
            if (world != null && world.getBlockState(pos).isOf(Blocks.END_PORTAL) && old != null) world.setBlockState(pos, old, 3);
        }
    }

    public static boolean isCustomGateway(ServerPlayerEntity player, BlockPos pos) {
        if (player.getEntityWorld().getServer() == null) return false;
        Power power = PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GAMBIT);
        if (!(power instanceof TeleportGambitPower gambit)) return false;
        RegistryKey<World> dimension = player.getEntityWorld().getRegistryKey();
        for (Map.Entry<UUID, BlockPos> entry : gambit.padPositions.entrySet()) {
            if (pos.equals(entry.getValue()) && gambit.activePads.containsKey(entry.getKey())
                    && dimension.equals(gambit.padWorlds.get(entry.getKey()))) return true;
        }
        return false;
    }

    public static void playerDisconnected(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        Power registered = PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GAMBIT);
        if (registered instanceof TeleportGambitPower gambit) {
            if (gambit.activePads.containsKey(player.getUuid())) gambit.removePad(player, true);
            gambit.gatewayCooldowns.remove(player.getUuid());
            gambit.transferLockoutUntil.remove(player.getUuid());
        }
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        removePad(player, true);
        gatewayCooldowns.remove(player.getUuid());
    }
}
