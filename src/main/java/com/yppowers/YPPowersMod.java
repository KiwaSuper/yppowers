package com.yppowers;

import com.yppowers.command.YPCommands;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerRegistry;
import com.yppowers.item.YPItems;
import com.yppowers.network.YPNetworking;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.CombatTagSystem;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.EnergySystem;
import com.yppowers.system.FreezeSystem;
import com.yppowers.system.GlobalHealthSystem;
import com.yppowers.system.GraceSystem;
import com.yppowers.system.SummonSystem;
import com.yppowers.system.TempBlockSystem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thin composition root wiring Fabric lifecycle events to core subsystems and registered powers. */
public final class YPPowersMod implements ModInitializer {
    public static final String MOD_ID = "yppowers";
    public static final Logger LOGGER = LoggerFactory.getLogger("YP Powers");

    @Override
    public void onInitialize() {
        YPConfig.load();
        YPItems.initialize();
        registerPowers();
        YPNetworking.registerServerReceivers();
        YPCommands.register();
        registerEvents();
        LOGGER.info("YP Powers 2.0 initialized ({} powers registered).", PowerRegistry.all().size());
    }

    private static void registerPowers() {
        PowerRegistry.register(new com.yppowers.power.divinity.DivinityShieldPower());
        PowerRegistry.register(new com.yppowers.power.goat.GoatPower());
        PowerRegistry.register(new com.yppowers.power.timewarper.TimeWarperPower());
        PowerRegistry.register(new com.yppowers.power.teleportgambit.TeleportGambitPower());
        PowerRegistry.register(new com.yppowers.power.punch.SeriousPunchPower());
        PowerRegistry.register(new com.yppowers.power.poseidon.PoseidonPower());
        PowerRegistry.register(new com.yppowers.power.shadowarcher.ShadowArcherPower());
        PowerRegistry.register(new com.yppowers.power.bootssky.BootsOfTheSkyPower());
        PowerRegistry.register(new com.yppowers.power.deletion.StickOfDeletionPower());
        PowerRegistry.register(new com.yppowers.power.firegod.FireGodPower());
        PowerRegistry.register(new com.yppowers.power.iceemperor.IceEmperorPower());
        PowerRegistry.register(new com.yppowers.power.thor.ThorPower());
        PowerRegistry.register(new com.yppowers.power.fallout.FalloutWarriorPower());
        PowerRegistry.register(new com.yppowers.power.spidey.SpideyPower());
        PowerRegistry.register(new com.yppowers.power.spiritchanneler.SpiritChannelerPower());
        PowerRegistry.register(new com.yppowers.power.illusionist.IllusionistPower());
        PowerRegistry.register(new com.yppowers.power.kitsune.KitsunePower());
    }

    private static void registerEvents() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || world.getServer() == null) {
                return ActionResult.PASS;
            }
            Power power = PlayerDataStore.get(world.getServer()).getPower(sp);
            return power == null ? ActionResult.PASS : power.onUseItem(sp, hand);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || world.getServer() == null) {
                return ActionResult.PASS;
            }
            Power power = PlayerDataStore.get(world.getServer()).getPower(sp);
            return power == null ? ActionResult.PASS : power.onUseBlock(sp, hand, hit.getBlockPos());
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || world.getServer() == null) {
                return ActionResult.PASS;
            }
            Power power = PlayerDataStore.get(world.getServer()).getPower(sp);
            return power == null ? ActionResult.PASS : power.onAttackEntity(sp, hand, entity);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp) || world.getServer() == null) {
                return ActionResult.PASS;
            }
            Power power = PlayerDataStore.get(world.getServer()).getPower(sp);
            return power == null ? ActionResult.PASS : power.onUseEntity(sp, hand, entity);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            GraceSystem.serverTick(server);
            CombatTagSystem.serverTick(server);
            CooldownSystem.get(server).tickAll(server.getTicks());
            EnergySystem.serverTick(server);
            FreezeSystem.serverTick(server);
            SummonSystem.serverTick(server);
            TempBlockSystem.serverTick(server);

            for (Power power : PowerRegistry.all()) {
                power.onServerTick(server);
            }

            PlayerDataStore store = PlayerDataStore.get(server);
            boolean syncHud = server.getTicks() % 5 == 0;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Power power = store.getPower(player);
                if (power != null) power.onPlayerTick(player);
                if (syncHud) YPNetworking.syncHud(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            AllianceSystem.get(server).rememberName(player.getUuid(), player.getName().getString());
            GlobalHealthSystem.apply(player);
            Power power = PlayerDataStore.get(server).getPower(player);
            if (power != null) power.onJoin(player);
            for (Power p : PowerRegistry.all()) p.onAnyPlayerJoin(player);
            FreezeSystem.syncVisualsTo(player);
            YPNetworking.syncPlayerFull(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            CombatTagSystem.onPlayerDisconnect(player);
            Power power = PlayerDataStore.get(server).getPower(player);
            if (power != null) power.onDisconnect(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            MinecraftServer server = newPlayer.getEntityWorld().getServer();
            if (server == null) return;
            GlobalHealthSystem.apply(newPlayer);
            Power power = PlayerDataStore.get(server).getPower(newPlayer);
            if (power != null) power.onRespawn(newPlayer, oldPlayer);
            for (Power p : PowerRegistry.all()) p.onAnyPlayerRespawn(newPlayer, oldPlayer);
            YPNetworking.syncPlayerFull(newPlayer);
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            MinecraftServer server = entity.getEntityWorld().getServer();
            if (server == null) return;
            if (entity instanceof ServerPlayerEntity deadPlayer) {
                CombatTagSystem.onPlayerDeath(deadPlayer);
                Power power = PlayerDataStore.get(server).getPower(deadPlayer);
                if (power != null) power.onDeath(deadPlayer);
                for (Power p : PowerRegistry.all()) p.onAnyPlayerDeath(deadPlayer);
                return;
            }
            if (entity instanceof MobEntity mob) {
                if (mob.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                    for (Power p : PowerRegistry.all()) p.onAnyMobKilled(sw, mob, damageSource);
                }
                SummonSystem.untrack(mob.getUuid());
                Entity attacker = damageSource.getAttacker();
                if (attacker instanceof ProjectileEntity proj && proj.getOwner() != null) {
                    attacker = proj.getOwner();
                }
                ServerPlayerEntity killer = attacker instanceof ServerPlayerEntity sp ? sp : null;
                if (killer != null) {
                    Power killerPower = PlayerDataStore.get(server).getPower(killer);
                    if (killerPower != null) killerPower.onMobKilled(killer, mob);
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Power power : PowerRegistry.all()) {
                power.onServerStopping(server);
            }
            TempBlockSystem.restoreAll(server);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            for (Power power : PowerRegistry.all()) {
                power.onServerStopped(server);
            }
            FreezeSystem.clearAll();
            CombatTagSystem.clearAll();
            SummonSystem.clearRuntimeState();
            GraceSystem.clearRuntimeState();
        });
    }
}
