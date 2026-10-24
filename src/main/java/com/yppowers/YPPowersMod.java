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

        YPNetworking.registerServerReceivers();

        // Thor's free Riptide-style launch is triggered by right-clicking his soulbound mace.
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer) || world.getServer() == null) {
                return net.minecraft.util.ActionResult.PASS;
            }
            PowerManager manager = PowerManager.get(world.getServer());
            if (manager.getPowerType(serverPlayer.getUuid()) != PowerType.THOR) {
                return net.minecraft.util.ActionResult.PASS;
            }
            Power power = manager.getPowerInstance(PowerType.THOR);
            if (power instanceof ThorPower thor && com.yppowers.item.YPItemData.hasFlag(serverPlayer.getMainHandStack(), "YPThorMace")) {
                if (GraceManager.blockAbility(serverPlayer)) return net.minecraft.util.ActionResult.SUCCESS;
                thor.beginRiptideLaunch(serverPlayer);
                return net.minecraft.util.ActionResult.SUCCESS;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        YPCommands.register();

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TimeWarperPower.clearTimeStopVisualState());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            EnergyManager.endOverdrivesForShutdown(server);
            TeleportGambitDomainManager.shutdown(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PowerManager.get(server).tickGracePeriod(server);
            EnergyManager.serverTick(server);
            PowerManager.get(server).tickTeleportGambitDomainCooldowns();
            TeleportGambitDomainManager.serverTick(server);
            TimeWarperPower.serverTick(server);
            SeriousPunchPower.serverTick(server);
            IceEmperorPower.serverTick(server);
            ThorPower.serverTick(server);
            BootsOfTheSkyPower.serverTick(server);
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
            TeleportGambitDomainManager.onPlayerJoin(player);
            EnergyManager.get(server).onJoin(player);
            GlobalHealthManager.apply(player);
            YPNetworking.syncFireGod(player, type == PowerType.FIRE_GOD);
            YPNetworking.syncShadowArcher(player, type == PowerType.SHADOW_ARCHER);
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
                    // Even if an administrator reassigned Thor's owner while Mjölnir was
                    // away, that owner's death must still remove the tracked Heavy Core.
                    if (type != PowerType.THOR) {
                        Power thor = manager.getPowerInstance(PowerType.THOR);
                        if (thor instanceof ThorPower thorPower) thorPower.onPlayerDeath(deadPlayer);
                    }
                }
                return;
            }
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
