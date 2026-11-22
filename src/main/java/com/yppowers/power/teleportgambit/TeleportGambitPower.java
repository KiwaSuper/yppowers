package com.yppowers.power.teleportgambit;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.TempBlockSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Teleport Gambit: short-lived forward End Portal Gateway (G) and Pocket Domain Expansion (H). */
public final class TeleportGambitPower extends Power {
    private static final String CD_GATEWAY = "teleport_gambit_gateway";

    private record ActiveGateway(ServerWorld world, BlockPos pos, Vec3d lookDir, int expiresAtTick) {}

    private final Map<UUID, ActiveGateway> activeGateways = new HashMap<>();
    private final DomainManager domainManager = new DomainManager();

    @Override
    public PowerId id() {
        return PowerId.GAMBIT;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Gateway", CD_GATEWAY),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Pocket Domain", DomainManager.CD_DOMAIN)
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        if (domainManager.isInDomainSession(player.getUuid())) return "IN DOMAIN";
        if (domainManager.isCharging(player.getUuid())) return "Charging Domain...";
        return activeGateways.containsKey(player.getUuid()) ? "Gateway Open" : "";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY;
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        if (slot == AbilitySlot.PRIMARY) {
            return cds.checkReady(player, CD_GATEWAY, "Gateway");
        }
        if (slot == AbilitySlot.SECONDARY) {
            if (domainManager.isBusy(player.getUuid())) {
                player.sendMessage(Text.literal("Domain is already active or charging.").formatted(Formatting.YELLOW), true);
                return false;
            }
            return cds.checkReady(player, DomainManager.CD_DOMAIN, "Pocket Domain");
        }
        return false;
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.PRIMARY) openGateway(player);
        else if (slot == AbilitySlot.SECONDARY) domainManager.beginCharge(player);
    }

    private void openGateway(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.TeleportGambit cfg = YPConfig.get().powers.teleportGambit;
        Vec3d look = CombatUtil.safeLook(player);
        BlockPos portalPos = BlockPos.ofFloored(player.getEntityPos().add(look.x * 2.0, 0.0, look.z * 2.0));
        if (!world.getBlockState(portalPos).isAir()) portalPos = portalPos.up();

        if (!TempBlockSystem.setTemporary(world, portalPos, Blocks.END_PORTAL.getDefaultState(),
                cfg.gatewayDurationTicks, "gambit_gateway", true, null)) {
            player.sendMessage(Text.literal("Cannot place Gateway here!").formatted(Formatting.RED), true);
            return;
        }

        activeGateways.put(player.getUuid(), new ActiveGateway(
                world, portalPos.toImmutable(), look, world.getServer().getTicks() + cfg.gatewayDurationTicks));
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_GATEWAY, cfg.gatewayCooldownTicks);
        VfxSystem.ring(world, ParticleTypes.PORTAL, Vec3d.ofCenter(portalPos), 1.4, 20, 0.1);
        VfxSystem.playSound(world, Vec3d.ofCenter(portalPos), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, 1.2f);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        ActiveGateway gw = activeGateways.get(player.getUuid());
        if (gw == null || player.getEntityWorld().getServer() == null) return;
        if (player.getEntityWorld().getServer().getTicks() >= gw.expiresAtTick()) {
            activeGateways.remove(player.getUuid());
            return;
        }
        if (player.getEntityWorld() == gw.world() && player.getBlockPos().isWithinDistance(gw.pos(), 1.35)) {
            activeGateways.remove(player.getUuid());
            TempBlockSystem.restoreNow(gw.world(), gw.pos());
            teleportThroughGateway(player, gw.world(), gw.lookDir());
        }
    }

    private static void teleportThroughGateway(ServerPlayerEntity player, ServerWorld world, Vec3d look) {
        double dist = 25.0 + world.random.nextDouble() * 25.0; // 25–50 blocks forward
        Vec3d horiz = new Vec3d(look.x, 0.0, look.z);
        horiz = horiz.lengthSquared() < 1.0e-4 ? new Vec3d(0, 0, 1) : horiz.normalize();
        BlockPos dest = BlockPos.ofFloored(player.getX() + horiz.x * dist, player.getY(), player.getZ() + horiz.z * dist);
        BlockPos safe = findSafeLanding(world, dest);

        player.requestTeleport(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1));
        VfxSystem.burst(world, ParticleTypes.PORTAL, Vec3d.ofCenter(safe), 30, 0.6, 0.15);
        VfxSystem.playSound(world, Vec3d.ofCenter(safe), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static BlockPos findSafeLanding(ServerWorld world, BlockPos approx) {
        int startY = Math.max(world.getBottomY() + 2, Math.min(world.getTopYInclusive() - 3, approx.getY()));
        for (int dy = 0; dy <= 24; dy++) {
            BlockPos up = new BlockPos(approx.getX(), Math.min(world.getTopYInclusive() - 2, startY + dy), approx.getZ());
            if (isSafeStand(world, up)) return up;
            BlockPos down = new BlockPos(approx.getX(), Math.max(world.getBottomY() + 1, startY - dy), approx.getZ());
            if (isSafeStand(world, down)) return down;
        }
        return new BlockPos(approx.getX(), startY + 2, approx.getZ());
    }

    private static boolean isSafeStand(ServerWorld world, BlockPos feet) {
        return !world.getBlockState(feet.down()).isAir()
                && world.getBlockState(feet).isAir()
                && world.getBlockState(feet.up()).isAir();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        domainManager.serverTick(server);
    }

    @Override
    public void onAnyPlayerJoin(ServerPlayerEntity player) {
        domainManager.onAnyPlayerJoin(player);
    }

    @Override
    public void onAnyPlayerDeath(ServerPlayerEntity player) {
        domainManager.onAnyPlayerDeath(player);
    }

    @Override
    public void onAnyPlayerRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        domainManager.onAnyPlayerRespawn(newPlayer);
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        activeGateways.remove(player.getUuid());
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        domainManager.shutdown(server);
        activeGateways.clear();
    }
}
