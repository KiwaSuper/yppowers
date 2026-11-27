package com.yppowers;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.client.render.VfxParticles;
import com.yppowers.combat.CombatTagManager;
import com.yppowers.command.GraceManager;
import com.yppowers.command.TestMode;
import com.yppowers.command.YPCommands;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.energy.GlobalHealthManager;
import com.yppowers.item.ArtifactSpawnManager;
import com.yppowers.item.GauntletOfDarknessManager;
import com.yppowers.item.GrimoireAbilityManager;
import com.yppowers.item.GrimoireFlightManager;
import com.yppowers.item.GrimoireOfLightManager;
import com.yppowers.item.YPItems;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.RitualManager;
import com.yppowers.power.impl.AwakeningManager;
import com.yppowers.power.impl.BootsOfTheSkyPower;
import com.yppowers.power.impl.DivinityShieldPower;
import com.yppowers.power.impl.FalloutWarriorPower;
import com.yppowers.power.impl.FireGodPower;
import com.yppowers.power.impl.GoatPower;
import com.yppowers.power.impl.IceEmperorPower;
import com.yppowers.power.impl.IllusionistPower;
import com.yppowers.power.impl.KitsunePower;
import com.yppowers.power.impl.PoseidonPower;
import com.yppowers.power.impl.SeriousPunchPower;
import com.yppowers.power.impl.ShadowArcherPower;
import com.yppowers.power.impl.SpideyPower;
import com.yppowers.power.impl.SpiritChannelerPower;
import com.yppowers.power.impl.StickOfDeletionPower;
import com.yppowers.power.impl.TeleportGambitDomainManager;
import com.yppowers.power.impl.TeleportGambitPower;
import com.yppowers.power.impl.TeleportGambitRandomPortalManager;
import com.yppowers.power.impl.ThorPower;
import com.yppowers.power.impl.TimeWarperDomainManager;
import com.yppowers.power.impl.TimeWarperPower;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AfterEntityChange;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AfterDeath;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AfterRespawn;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopped;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class YPPowersMod implements ModInitializer {
   @Override
   public void onInitialize() {
      YPConfig.getInstance().load();
      VfxParticles.initialize();
      YPItems.initialize();
      ServerLifecycleEvents.SERVER_STARTED.register(GauntletOfDarknessManager::serverStarted);
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
      UseEntityCallback.EVENT
         .register(
            (UseEntityCallback)(player, world, hand, entity, hitResult) -> {
               if (!world.isClient() && entity instanceof ItemFrameEntity) {
                  ItemStack held = player.getStackInHand(hand);
                  return (ActionResult)(!GauntletOfDarknessManager.isGauntlet(held) && !GrimoireOfLightManager.isGrimoire(held)
                     ? ActionResult.PASS
                     : ActionResult.FAIL);
               } else {
                  return ActionResult.PASS;
               }
            }
         );
      UseItemCallback.EVENT.register((UseItemCallback)(player, world, hand) -> {
         if (hand != Hand.MAIN_HAND || !(!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) || world.getServer() == null) {
            return ActionResult.PASS;
         } else if (!serverPlayer.getMainHandStack().isOf(YPItems.WEB_SLINGERS)) {
            return ActionResult.PASS;
         } else {
            PowerManager manager = PowerManager.get(world.getServer());
            if (manager.getPowerType(serverPlayer.getUuid()) != PowerType.SPIDEY) {
               return ActionResult.PASS;
            } else if (GraceManager.blockAbility(serverPlayer)) {
               return ActionResult.SUCCESS;
            } else if (manager.getPowerInstance(PowerType.SPIDEY) instanceof SpideyPower spidey) {
               if (serverPlayer.isSneaking()) {
                  spidey.fireWebProjectile(serverPlayer);
               } else {
                  spidey.useWebSling(serverPlayer);
               }

               return ActionResult.SUCCESS;
            } else {
               return ActionResult.PASS;
            }
         }
      });
      AttackEntityCallback.EVENT
         .register(
            (AttackEntityCallback)(player, world, hand, entity, hitResult) -> {
               if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer && world.getServer() != null) {
                  PowerManager manager = PowerManager.get(world.getServer());
                  if (manager.getPowerType(serverPlayer.getUuid()) == PowerType.SHADOW_ARCHER
                     && manager.getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer
                     && archer.isOwnDisguise(serverPlayer, entity)) {
                     archer.redirectDisguiseClick(serverPlayer, hand, true);
                     return ActionResult.FAIL;
                  } else {
                     return ActionResult.PASS;
                  }
               } else {
                  return ActionResult.PASS;
               }
            }
         );
      UseEntityCallback.EVENT
         .register(
            (UseEntityCallback)(player, world, hand, entity, hitResult) -> {
               if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer && world.getServer() != null) {
                  PowerManager manager = PowerManager.get(world.getServer());
                  if (manager.getPowerType(serverPlayer.getUuid()) == PowerType.SHADOW_ARCHER
                     && manager.getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer
                     && archer.isOwnDisguise(serverPlayer, entity)) {
                     archer.redirectDisguiseClick(serverPlayer, hand, false);
                     return ActionResult.SUCCESS;
                  } else {
                     return ActionResult.PASS;
                  }
               } else {
                  return ActionResult.PASS;
               }
            }
         );
      UseItemCallback.EVENT
         .register(
            (UseItemCallback)(player, world, hand) -> {
               if (hand != Hand.MAIN_HAND || !(!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) || world.getServer() == null) {
                  return ActionResult.PASS;
               } else if (!(serverPlayer.getMainHandStack().getItem() instanceof BlockItem)) {
                  return ActionResult.PASS;
               } else {
                  PowerManager manager = PowerManager.get(world.getServer());
                  if (manager.getPowerType(serverPlayer.getUuid()) != PowerType.KITSUNE) {
                     return ActionResult.PASS;
                  } else {
                     return (ActionResult)(manager.getPowerInstance(PowerType.KITSUNE) instanceof KitsunePower kitsune
                           && kitsune.tryFoxPlaceBlock(serverPlayer)
                        ? ActionResult.SUCCESS
                        : ActionResult.PASS);
                  }
               }
            }
         );
      UseBlockCallback.EVENT
         .register(
            (UseBlockCallback)(player, world, hand, hit) -> (ActionResult)(!world.isClient()
                  && player instanceof ServerPlayerEntity serverPlayer
                  && !TimeWarperDomainManager.mayModifyBlock(serverPlayer, hit.getBlockPos())
               ? ActionResult.FAIL
               : ActionResult.PASS)
         );
      UseBlockCallback.EVENT.register((UseBlockCallback)(player, world, hand, hit) -> {
         if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
            ArtifactSpawnManager.onContainerInteract(serverPlayer, serverWorld, hit.getBlockPos());
            return ActionResult.PASS;
         } else {
            return ActionResult.PASS;
         }
      });
      UseBlockCallback.EVENT
         .register(
            (UseBlockCallback)(player, world, hand, hit) -> {
               if (hand == Hand.MAIN_HAND && !world.isClient() && player instanceof ServerPlayerEntity serverPlayer && world.getServer() != null) {
                  PowerManager manager = PowerManager.get(world.getServer());
                  if (manager.getPowerType(serverPlayer.getUuid()) != PowerType.ILLUSIONIST) {
                     return ActionResult.PASS;
                  } else {
                     return (ActionResult)(manager.getPowerInstance(PowerType.ILLUSIONIST) instanceof IllusionistPower illusionist
                           && !GraceManager.blockAbility(serverPlayer)
                           && illusionist.trySpatialErasure(serverPlayer, hit.getBlockPos())
                        ? ActionResult.SUCCESS
                        : ActionResult.PASS);
                  }
               } else {
                  return ActionResult.PASS;
               }
            }
         );
      YPCommands.register();
      ServerLifecycleEvents.SERVER_STOPPED.register((ServerStopped)server -> {
         CombatTagManager.clearAll();
         TimeWarperPower.clearTimeStopVisualState();
         TimeWarperPower.clearTimeShardRuntimeState();
         TimeWarperDomainManager.clearRuntimeState();
         TestMode.clearAll();
         GrimoireFlightManager.clearRuntimeState();
         AwakeningManager.clearRuntimeState();
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         TimeWarperPower.clearBlackHoleRuntimeState(server);

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            AwakeningManager.onPlayerDisconnect(player);
         }

         EnergyManager.endOverdrivesForShutdown(server);
         TeleportGambitDomainManager.shutdown(server);
         TimeWarperDomainManager.shutdown(server);
         TeleportGambitRandomPortalManager.shutdown(server);
         IllusionistPower.shutdown(server);
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         ArtifactSpawnManager.serverTick(server);
         GauntletOfDarknessManager.serverTick(server);
         GrimoireOfLightManager.serverTick(server);
         GrimoireFlightManager.serverTick(server);
         GrimoireAbilityManager.serverTick(server);
         PowerManager manager = PowerManager.get(server);
         manager.tickGracePeriod(server);
         CombatTagManager.serverTick(server);
         manager.tickTimeWarperFormCooldowns();
         EnergyManager.serverTick(server);
         IllusionistPower.serverTick(server);
         manager.tickTeleportGambitDomainCooldowns();
         TeleportGambitDomainManager.serverTick(server);
         TimeWarperDomainManager.serverTick(server);
         TeleportGambitRandomPortalManager.serverTick(server);
         TimeWarperPower.serverTick(server);
         TestMode.serverTick(server);
         SeriousPunchPower.serverTick(server);
         IceEmperorPower.serverTick(server);
         BootsOfTheSkyPower.serverTick(server);
         SpideyPower.serverTick(server);

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (manager.isGrimoireSkillsetEnabled(player.getUuid()) && !GrimoireOfLightManager.hasGrimoire(player)) {
               manager.setGrimoireSkillsetEnabled(player.getUuid(), false);
               YPNetworking.syncGrimoireMode(player, false);
               GrimoireAbilityManager.setBeamFiring(player, false);
            }

            RitualManager.tickPlayer(player);
            RitualManager.tickDivinity(player, Math.max(1, YPConfig.getInstance().divinity_shield_radius));
            PowerType type = manager.getPowerType(player.getUuid());
            if (type != null) {
               Power power = manager.getPowerInstance(type);
               if (power != null) {
                  power.tick(player);
               }
            }

            AwakeningManager.tick(player);
            if (server.getTicks() % 10 == 0) {
               YPNetworking.syncHudState(player);
            }
         }
      });
      ServerPlayerEvents.AFTER_RESPAWN.register((AfterRespawn)(oldPlayer, newPlayer, alive) -> {
         GlobalHealthManager.apply(newPlayer);
         if (newPlayer.getEntityWorld().getServer() != null) {
            EnergyManager.get(newPlayer.getEntityWorld().getServer()).onJoin(newPlayer);
         }

         PowerManager manager = PowerManager.get(newPlayer.getEntityWorld().getServer());
         PowerType type = manager.getAssignedPowerType(newPlayer.getUuid());
         if (manager.isGrimoireSkillsetEnabled(newPlayer.getUuid()) && !GrimoireOfLightManager.hasGrimoire(newPlayer)) {
            manager.setGrimoireSkillsetEnabled(newPlayer.getUuid(), false);
         }

         YPNetworking.syncGrimoireMode(newPlayer, manager.isGrimoireSkillsetEnabled(newPlayer.getUuid()));
         if (type != null) {
            Power power = manager.getPowerInstance(type);
            if (power != null) {
               power.onPlayerRespawn(newPlayer, oldPlayer);
            }
         }

         YPNetworking.syncFireGod(newPlayer, type == PowerType.FIRE_GOD);
         YPNetworking.syncShadowArcher(newPlayer, type == PowerType.SHADOW_ARCHER);
         YPNetworking.syncIceEmperorFormsTo(newPlayer);
         TeleportGambitDomainManager.onPlayerRespawn(newPlayer);
         TimeWarperDomainManager.onPlayerRespawn(newPlayer);
         TeleportGambitRandomPortalManager.restorePendingReturn(newPlayer);
         YPNetworking.syncHudState(newPlayer);
      });
      ServerEntityWorldChangeEvents.AFTER_ENTITY_CHANGE_WORLD.register((AfterEntityChange)(oldEntity, newEntity, origin, destination) -> {
         if (oldEntity instanceof ServerPlayerEntity) {
            YPNetworking.clearVfxForSource(origin, oldEntity.getId());
            if (newEntity instanceof ServerPlayerEntity newPlayer) {
               YPNetworking.syncWitherAscendance((ServerPlayerEntity)oldEntity, false);
               if (AwakeningManager.isWitherForm(newPlayer.getUuid())) {
                  YPNetworking.syncWitherAscendance(newPlayer, true);
               }
            }
         }
      });
      ServerPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, server) -> {
         ServerPlayerEntity player = handler.getPlayer();
         CombatTagManager.onPlayerDisconnect(player);
         YPNetworking.clearVfxForSource(player);
         EnergyManager.get(server).onPlayerDisconnect(player);
         TimeWarperDomainManager.onPlayerDisconnect(player);
         TeleportGambitPower.playerDisconnected(player);
         DivinityShieldPower.playerDisconnected(player);
         GrimoireFlightManager.onPlayerDisconnect(player, server);
         GrimoireAbilityManager.onDisconnect(player);
         PowerManager manager = PowerManager.get(server);
         RitualManager.clearPlayerRuntime(player);
         PowerType assignedType = manager.getAssignedPowerType(player.getUuid());
         Power assignedPower = assignedType == null ? null : manager.getPowerInstance(assignedType);
         if (assignedPower != null) {
            assignedPower.onPlayerDisconnect(player);
         }

         AwakeningManager.onPlayerDisconnect(player);
         if (assignedType != PowerType.SHADOW_ARCHER && manager.getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer) {
            archer.onPlayerDisconnect(player);
         }
      });
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         ServerPlayerEntity player = handler.getPlayer();
         AllianceManager.get(server).rememberName(player.getUuid(), player.getName().getString());
         PowerManager manager = PowerManager.get(server);
         if (manager.isGrimoireSkillsetEnabled(player.getUuid()) && !GrimoireOfLightManager.hasGrimoire(player)) {
            manager.setGrimoireSkillsetEnabled(player.getUuid(), false);
         }

         YPNetworking.syncGrimoireMode(player, manager.isGrimoireSkillsetEnabled(player.getUuid()));
         PowerType type = manager.getAssignedPowerType(player.getUuid());
         if (type != null) {
            Power power = manager.getPowerInstance(type);
            if (power != null) {
               power.onPlayerJoin(player);
            }
         }

         TeleportGambitDomainManager.onPlayerJoin(player);
         TimeWarperDomainManager.onPlayerJoin(player);
         TeleportGambitRandomPortalManager.restorePendingReturn(player);
         EnergyManager.get(server).onJoin(player);
         GlobalHealthManager.apply(player);
         YPNetworking.syncFireGod(player, type == PowerType.FIRE_GOD);
         YPNetworking.syncShadowArcher(player, type == PowerType.SHADOW_ARCHER);
         YPNetworking.syncWitherAscendanceTo(player);
         YPNetworking.syncIceEmperorFormsTo(player);
         TimeWarperPower.syncTimeStopVisualsTo(player);
         YPNetworking.syncHudState(player);
      });
      ServerLivingEntityEvents.AFTER_DEATH.register((AfterDeath)(entity, damageSource) -> {
         RitualManager.onEntityDeath(entity, damageSource);
         if (entity instanceof ServerPlayerEntity dead) {
            RitualManager.clearPlayerRuntime(dead);
         }

         if (entity instanceof ServerPlayerEntity deadPlayer) {
            CombatTagManager.onPlayerDeath(deadPlayer);
            if (deadPlayer.getEntityWorld().getServer() != null) {
               YPNetworking.clearVfxForSource(deadPlayer);
               PowerManager manager = PowerManager.get(deadPlayer.getEntityWorld().getServer());
               manager.setGrimoireSkillsetEnabled(deadPlayer.getUuid(), false);
               YPNetworking.syncGrimoireMode(deadPlayer, false);
               GrimoireAbilityManager.setBeamFiring(deadPlayer, false);
               TeleportGambitDomainManager.onPlayerDeath(deadPlayer);
               TimeWarperDomainManager.onPlayerDeath(deadPlayer);
               TimeWarperPower.clearBlackHoleForPlayerDeath(deadPlayer);
               EnergyManager.get(deadPlayer.getEntityWorld().getServer()).onPlayerDeath(deadPlayer);
               PowerType type = manager.getAssignedPowerType(deadPlayer.getUuid());
               Power power = type == null ? null : manager.getPowerInstance(type);
               if (power != null) {
                  power.onPlayerDeath(deadPlayer);
               }

               UUID killerId = playerKillerUuid(damageSource.getSource(), damageSource.getAttacker());
               if (killerId != null && !killerId.equals(deadPlayer.getUuid()) && type == PowerType.THOR) {
                  grantThorPvpDeathAdvancement(deadPlayer.getEntityWorld().getServer(), deadPlayer);
               }
            }
         } else if (entity instanceof MobEntity) {
            if (entity.getEntityWorld() instanceof ServerWorld deadWorld) {
               TimeWarperPower.dropTimeShards(deadWorld, entity);
            }
         }
      });
   }

   private static void grantThorPvpDeathAdvancement(MinecraftServer server, ServerPlayerEntity player) {
      AdvancementEntry advancement = server.getAdvancementLoader().get(Identifier.of("yppowers", "balright_bro"));
      if (advancement != null) {
         player.getAdvancementTracker().grantCriterion(advancement, "balright_bro");
      }
   }

   private static UUID playerKillerUuid(Entity source, Entity attacker) {
      if (source instanceof ProjectileEntity projectile) {
         if (projectile.getOwner() instanceof ServerPlayerEntity player) {
            return player.getUuid();
         } else {
            String prefix = "yppowers_player_projectile_owner:";

            for (String tag : projectile.getCommandTags()) {
               if (tag.startsWith(prefix)) {
                  try {
                     return UUID.fromString(tag.substring(prefix.length()));
                  } catch (IllegalArgumentException var8) {
                     return null;
                  }
               }
            }

            return null;
         }
      } else if (attacker instanceof ServerPlayerEntity player) {
         return player.getUuid();
      } else {
         return attacker instanceof ProjectileEntity projectilex ? playerKillerUuid(projectilex, projectilex.getOwner()) : null;
      }
   }
}
