package com.yppowers;

import com.yppowers.command.GraceManager;
import com.yppowers.command.YPCommands;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.energy.GlobalHealthManager;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;

public class YPPowersMod implements ModInitializer {
    @Override
    public void onInitialize() {
        YPConfig.getInstance().load();
        com.yppowers.item.YPItems.initialize();

        PowerManager.registerPower(new DivinityShieldPower());
        PowerManager.registerPower(new GoatPower());
        PowerManager.registerPower(new TimeWarperPower());
        PowerManager.registerPower(new TeleportGambitPower());
        PowerManager.registerPower(new SeriousPunchPower());
        PowerManager.registerPower(new PoseidonPower());
        PowerManager.registerPower(new ShadowArcherPower());
        PowerManager.registerPower(new BootsOfTheSkyPower());
        PowerManager.registerPower(new StickOfDeletionPower());
        PowerManager.registerPower(new FireGodPower());
        PowerManager.registerPower(new IceEmperorPower());
        PowerManager.registerPower(new ThorPower());
        PowerManager.registerPower(new FalloutWarriorPower());
        PowerManager.registerPower(new SpideyPower());
        PowerManager.registerPower(new SpiritChannelerPower());
        PowerManager.registerPower(new IllusionistPower());
        PowerManager.registerPower(new KitsunePower());

        YPNetworking.registerServerReceivers();

        // The custom Web Slingers item uses the same server-authoritative traversal mechanic as Spidey's G key.
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || world.isClient()
                    || !(player instanceof ServerPlayerEntity serverPlayer) || world.getServer() == null) {
                return net.minecraft.util.ActionResult.PASS;
            }
            if (!serverPlayer.getMainHandStack().isOf(com.yppowers.item.YPItems.WEB_SLINGERS)) return net.minecraft.util.ActionResult.PASS;
            PowerManager manager = PowerManager.get(world.getServer());
            if (manager.getPowerType(serverPlayer.getUuid()) != PowerType.SPIDEY) return net.minecraft.util.ActionResult.PASS;
            if (GraceManager.blockAbility(serverPlayer)) return net.minecraft.util.ActionResult.SUCCESS;
            Power power = manager.getPowerInstance(PowerType.SPIDEY);
            if (power instanceof SpideyPower spidey) {
                spidey.useWebSling(serverPlayer);
                return net.minecraft.util.ActionResult.SUCCESS;
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // Spatial Erasure is deliberately armed first; ordinary block interactions are untouched unless
        // the assigned Illusionist explicitly armed the ability.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)
                    || world.getServer() == null) return net.minecraft.util.ActionResult.PASS;
            PowerManager manager = PowerManager.get(world.getServer());
            if (manager.getPowerType(serverPlayer.getUuid()) != PowerType.ILLUSIONIST) return net.minecraft.util.ActionResult.PASS;
            Power power = manager.getPowerInstance(PowerType.ILLUSIONIST);
            if (power instanceof IllusionistPower illusionist && !GraceManager.blockAbility(serverPlayer)
                    && illusionist.trySpatialErasure(serverPlayer, hit.getBlockPos())) return net.minecraft.util.ActionResult.SUCCESS;
            return net.minecraft.util.ActionResult.PASS;
        });

        YPCommands.register();

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            TimeWarperPower.clearTimeStopVisualState();
            TimeWarperPower.clearTimeShardRuntimeState();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EnergyManager.endOverdrivesForShutdown(server);
            TeleportGambitDomainManager.shutdown(server);
            IllusionistPower.shutdown(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // PersistentState lookup is cheap but unnecessary three times per tick. Reuse the
            // same world-scoped manager for countdowns and player power dispatch.
            PowerManager manager = PowerManager.get(server);
            manager.tickGracePeriod(server);
            manager.tickTimeWarperFormCooldowns();
            EnergyManager.serverTick(server);
            IllusionistPower.serverTick(server);
            manager.tickTeleportGambitDomainCooldowns();
            TeleportGambitDomainManager.serverTick(server);
            TimeWarperPower.serverTick(server);
            SeriousPunchPower.serverTick(server);
            IceEmperorPower.serverTick(server);
            BootsOfTheSkyPower.serverTick(server);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                PowerType type = manager.getPowerType(player.getUuid());
                if (type != null) {
                    Power power = manager.getPowerInstance(type);
                    if (power != null) {
                        power.tick(player);
                    }
                }
            }
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            GlobalHealthManager.apply(newPlayer);
            if (newPlayer.getEntityWorld().getServer() != null) EnergyManager.get(newPlayer.getEntityWorld().getServer()).onJoin(newPlayer);
            PowerManager manager = PowerManager.get(newPlayer.getEntityWorld().getServer());
            PowerType type = manager.getPowerType(newPlayer.getUuid());
            if (type != null) {
                Power power = manager.getPowerInstance(type);
                if (power != null) {
                    power.onPlayerRespawn(newPlayer, oldPlayer);
                }
            }
            // If the Domain owner died, restore the owner's saved pre-Domain location after respawn.
            // If a trapped visitor died while the Domain remains active, this also pulls them back in.
            TeleportGambitDomainManager.onPlayerRespawn(newPlayer);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            EnergyManager.get(server).onPlayerDisconnect(player);
            TeleportGambitPower.playerDisconnected(player);
            DivinityShieldPower.playerDisconnected(player);
            PowerManager manager = PowerManager.get(server);
            PowerType assignedType = manager.getPowerType(player.getUuid());
            Power assignedPower = assignedType == null ? null : manager.getPowerInstance(assignedType);
            if (assignedPower != null) assignedPower.onPlayerDisconnect(player);
            // Clear charge/temporary state even if the player's power changed immediately before logout.
            if (assignedType != PowerType.SHADOW_ARCHER) {
                Power archerPower = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
                if (archerPower instanceof ShadowArcherPower archer) archer.onPlayerDisconnect(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PowerManager manager = PowerManager.get(server);
            PowerType type = manager.getPowerType(player.getUuid());
            if (type != null) {
                Power power = manager.getPowerInstance(type);
                if (power != null) power.onPlayerJoin(player);
            }
            TeleportGambitDomainManager.onPlayerJoin(player);
            EnergyManager.get(server).onJoin(player);
            GlobalHealthManager.apply(player);
            YPNetworking.syncFireGod(player, type == PowerType.FIRE_GOD);
            YPNetworking.syncShadowArcher(player, type == PowerType.SHADOW_ARCHER);
            YPNetworking.syncIceEmperorFormsTo(player);
            TimeWarperPower.syncTimeStopVisualsTo(player);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity deadPlayer) {
                if (deadPlayer.getEntityWorld().getServer() != null) {
                    PowerManager manager = PowerManager.get(deadPlayer.getEntityWorld().getServer());
                    TeleportGambitDomainManager.onPlayerDeath(deadPlayer);
                    EnergyManager.get(deadPlayer.getEntityWorld().getServer()).onPlayerDeath(deadPlayer);
                    PowerType type = manager.getPowerType(deadPlayer.getUuid());
                    Power power = type == null ? null : manager.getPowerInstance(type);
                    if (power != null) power.onPlayerDeath(deadPlayer);
                }
                return;
            }
            if (!(entity instanceof MobEntity)) return;
            if (entity.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld deadWorld) {
                TimeWarperPower.dropTimeShards(deadWorld, entity);
            }
            Entity attacker = damageSource.getAttacker();
            if (attacker instanceof ProjectileEntity projectile && projectile.getOwner() != null) attacker = projectile.getOwner();
            if (attacker instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
                PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
                PowerType assignedType = manager.getPowerType(player.getUuid());
                if (assignedType == PowerType.SHADOW_ARCHER) {
                    Power archer = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
                    if (archer instanceof ShadowArcherPower shadowArcher) shadowArcher.recordKill(player, entity.getType());
                }
                if (assignedType == PowerType.SPIRIT_CHANNELER) {
                    Power spirit = manager.getPowerInstance(PowerType.SPIRIT_CHANNELER);
                    if (spirit instanceof SpiritChannelerPower channeler) channeler.recordKill(player, entity);
                }
            }
        });
    }
}
