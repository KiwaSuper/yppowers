package com.yppowers;

import com.yppowers.command.YPCommands;
import com.yppowers.config.YPConfig;
import com.yppowers.entity.YPEntities;
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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class YPPowersMod implements ModInitializer {
    @Override
    public void onInitialize() {
        YPConfig.getInstance().load();

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

        YPEntities.register();
        YPNetworking.registerServerReceivers();
        YPCommands.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            TimeWarperPower.serverTick(server);
            SeriousPunchPower.serverTick(server);
            IceEmperorPower.serverTick(server);
            PowerManager manager = PowerManager.get(server);
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
            PowerManager manager = PowerManager.get(newPlayer.getEntityWorld().getServer());
            PowerType type = manager.getPowerType(newPlayer.getUuid());
            if (type != null) {
                Power power = manager.getPowerInstance(type);
                if (power != null) {
                    power.onPlayerRespawn(newPlayer, oldPlayer);
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            TeleportGambitPower.playerDisconnected(player);
            DivinityShieldPower.playerDisconnected(player);
            PowerManager manager = PowerManager.get(server);
            Power archerPower = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
            if (archerPower instanceof ShadowArcherPower archer) archer.onPlayerDisconnect(player);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            PowerManager manager = PowerManager.get(server);
            PowerType type = manager.getPowerType(player.getUuid());
            if (type != null) {
                Power power = manager.getPowerInstance(type);
                if (power != null) power.onPlayerJoin(player);
            }
            YPNetworking.syncFireGod(player, type == PowerType.FIRE_GOD);
            YPNetworking.syncShadowArcher(player, type == PowerType.SHADOW_ARCHER);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof MobEntity)) return;
            Entity attacker = damageSource.getAttacker();
            if (attacker instanceof ProjectileEntity projectile && projectile.getOwner() != null) attacker = projectile.getOwner();
            if (attacker instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
                PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
                if (manager.getPowerType(player.getUuid()) == PowerType.SHADOW_ARCHER) {
                    Power archer = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
                    if (archer instanceof ShadowArcherPower shadowArcher) shadowArcher.recordKill(player, entity.getType());
                }
            }
        });
    }
}
