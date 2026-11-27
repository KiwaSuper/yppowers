package com.yppowers.network;

import com.yppowers.combat.CombatTagManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.GrimoireAbilityManager;
import com.yppowers.item.GrimoireOfLightManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.RitualManager;
import com.yppowers.power.impl.AwakeningManager;
import com.yppowers.power.impl.BootsOfTheSkyPower;
import com.yppowers.power.impl.FireGodPower;
import com.yppowers.power.impl.IceEmperorPower;
import com.yppowers.power.impl.KitsunePower;
import com.yppowers.power.impl.ShadowArcherPower;
import com.yppowers.power.impl.SpiritChannelerPower;
import com.yppowers.power.impl.StickOfDeletionPower;
import com.yppowers.power.impl.TimeWarperPower;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YPNetworking {
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Networking");
   private static final int MAX_VFX_RECIPIENTS = 96;

   private YPNetworking() {
   }

   public static void registerServerReceivers() {
      PayloadTypeRegistry.playC2S().register(YPNetworking.KeybindPayload.ID, YPNetworking.KeybindPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.FireGodBeamPayload.ID, YPNetworking.FireGodBeamPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.GrimoireBeamPayload.ID, YPNetworking.GrimoireBeamPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.TimeWarperBlackHoleHoldPayload.ID, YPNetworking.TimeWarperBlackHoleHoldPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.MorphRequestPayload.ID, YPNetworking.MorphRequestPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.PowerMenuRequestPayload.ID, YPNetworking.PowerMenuRequestPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.PowerMenuActionPayload.ID, YPNetworking.PowerMenuActionPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.PoseidonSinkConfirmPayload.ID, YPNetworking.PoseidonSinkConfirmPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(YPNetworking.TeleportGambitStructureRequestPayload.ID, YPNetworking.TeleportGambitStructureRequestPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncFireGodPayload.ID, YPNetworking.SyncFireGodPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncWitherAscendancePayload.ID, YPNetworking.SyncWitherAscendancePayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncGrimoireModePayload.ID, YPNetworking.SyncGrimoireModePayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncGrimoireBookPayload.ID, YPNetworking.SyncGrimoireBookPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.OpenMorphMenuPayload.ID, YPNetworking.OpenMorphMenuPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncShadowArcherPayload.ID, YPNetworking.SyncShadowArcherPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncShadowArcherMorphCatalogPayload.ID, YPNetworking.SyncShadowArcherMorphCatalogPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncShadowArcherMorphViewPayload.ID, YPNetworking.SyncShadowArcherMorphViewPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncTimeStopVisualPayload.ID, YPNetworking.SyncTimeStopVisualPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncTimeDomainViewPayload.ID, YPNetworking.SyncTimeDomainViewPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncIceEmperorFormPayload.ID, YPNetworking.SyncIceEmperorFormPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncEnergyPayload.ID, YPNetworking.SyncEnergyPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncHudWarningPayload.ID, YPNetworking.SyncHudWarningPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncHudStatePayload.ID, YPNetworking.SyncHudStatePayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.OpenPowerMenuPayload.ID, YPNetworking.OpenPowerMenuPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.SyncPowerMenuPayload.ID, YPNetworking.SyncPowerMenuPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.VfxEvent.ID, YPNetworking.VfxEvent.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.VfxClearPayload.ID, YPNetworking.VfxClearPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.OpenPoseidonConfirmPayload.ID, YPNetworking.OpenPoseidonConfirmPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(YPNetworking.OpenTeleportGambitStructuresPayload.ID, YPNetworking.OpenTeleportGambitStructuresPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(
         YPNetworking.PowerMenuRequestPayload.ID, (payload, context) -> context.server().execute(() -> syncPowerMenu(context.player()))
      );
      ServerPlayNetworking.registerGlobalReceiver(YPNetworking.PowerMenuActionPayload.ID, (payload, context) -> context.server().execute(() -> {
         handlePowerMenuAction(context.player(), payload.action());
         syncPowerMenu(context.player());
      }));
      ServerPlayNetworking.registerGlobalReceiver(
         YPNetworking.PoseidonSinkConfirmPayload.ID,
         (payload, context) -> context.server().execute(() -> AwakeningManager.confirmPoseidonSink(context.player()))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         YPNetworking.TeleportGambitStructureRequestPayload.ID,
         (payload, context) -> context.server().execute(() -> AwakeningManager.teleportToStructure(context.player(), payload.structureId()))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         YPNetworking.FireGodBeamPayload.ID,
         (payload, context) -> context.server()
            .execute(
               () -> {
                  ServerPlayerEntity player = context.player();
                  PowerManager manager = PowerManager.get(context.server());
                  if (manager.getPowerInstance(PowerType.FIRE_GOD) instanceof FireGodPower fireGod) {
                     if (!payload.active()) {
                        fireGod.setBlazeBeamFiring(player, false);
                     } else if (manager.getPowerType(player.getUuid()) == PowerType.FIRE_GOD
                        && !manager.isGrimoireSkillsetEnabled(player.getUuid())
                        && manager.isPowerEnabled(player, PowerType.FIRE_GOD)
                        && !GraceManager.blockAbility(player)) {
                        fireGod.setBlazeBeamFiring(player, true);
                     }
                  }
               }
            )
      );
      ServerPlayNetworking.registerGlobalReceiver(YPNetworking.GrimoireBeamPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayerEntity player = context.player();
         PowerManager manager = PowerManager.get(context.server());
         if (!payload.active()) {
            GrimoireAbilityManager.setBeamFiring(player, false);
         } else if (manager.isGrimoireSkillsetEnabled(player.getUuid()) && GrimoireOfLightManager.hasGrimoire(player) && !GraceManager.blockAbility(player)) {
            GrimoireAbilityManager.setBeamFiring(player, true);
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(
         YPNetworking.TimeWarperBlackHoleHoldPayload.ID,
         (payload, context) -> context.server().execute(() -> TimeWarperPower.updateBlackHoleHold(context.player(), payload.held()))
      );
      ServerPlayNetworking.registerGlobalReceiver(
         YPNetworking.KeybindPayload.ID,
         (payload, context) -> {
            int keyType = payload.keyType();
            context.server()
               .execute(
                  () -> {
                     ServerPlayerEntity player = context.player();
                     if (keyType >= 0 && keyType <= 20) {
                        PowerManager manager = PowerManager.get(context.server());
                        if (keyType == 15) {
                           if (!GrimoireOfLightManager.hasGrimoire(player)) {
                              manager.setGrimoireSkillsetEnabled(player.getUuid(), false);
                              syncGrimoireMode(player, false);
                           } else {
                              boolean active = manager.toggleGrimoireSkillset(player.getUuid());
                              syncGrimoireMode(player, active);
                              if (!active) {
                                 GrimoireAbilityManager.setBeamFiring(player, false);
                              }

                              player.sendMessage(
                                 Text.literal(
                                       active ? "Grimoire skillset selected. Hold G to channel the Light Beam." : "Returned to your assigned power skillset."
                                    )
                                    .formatted(Formatting.GOLD),
                                 true
                              );
                           }
                        } else if (keyType == 19) {
                           AwakeningManager.confirmDestructiveAwakening(player);
                        } else if (!manager.isGrimoireSkillsetEnabled(player.getUuid())) {
                           if (keyType == 17) {
                              AwakeningManager.activateChunkDupingDomain(player);
                           } else if (keyType == 18) {
                              AwakeningManager.swapPortableChunk(player);
                           } else {
                              PowerType type = manager.getPowerType(player.getUuid());
                              Power power = type == null ? null : manager.getPowerInstance(type);
                              if (power != null) {
                                 if (keyType == 16) {
                                    AwakeningManager.activate(player);
                                 } else if (keyType == 11) {
                                    if (type == PowerType.SHADOW_ARCHER && power instanceof ShadowArcherPower archer) {
                                       archer.toggleSpecialArrows(player);
                                    }
                                 } else if (keyType == 13) {
                                    if (type == PowerType.SHADOW_ARCHER && power instanceof ShadowArcherPower archer) {
                                       archer.cycleBowEnchantMode(player);
                                    }
                                 } else if (!GraceManager.blockAbility(player)) {
                                    if (keyType == 20) {
                                       if (type == PowerType.FIRE_GOD
                                          && power instanceof FireGodPower fireGod
                                          && manager.isPowerEnabled(player, PowerType.FIRE_GOD)) {
                                          fireGod.fireWitherSkullVolley(player);
                                       }
                                    } else if (keyType == 8) {
                                       if (type == PowerType.DELETION && power instanceof StickOfDeletionPower deletion) {
                                          deletion.trySwapPlaces(player);
                                       }
                                    } else if (keyType == 12) {
                                       if (type == PowerType.SHADOW_ARCHER && power instanceof ShadowArcherPower archer) {
                                          archer.teleportToLatestArrow(player);
                                       }
                                    } else if (keyType == 10) {
                                       if (type == PowerType.KITSUNE
                                          && manager.isPowerEnabled(player, PowerType.KITSUNE)
                                          && power instanceof KitsunePower kitsune) {
                                          kitsune.useCarryAbility(player);
                                       }
                                    } else {
                                       EnergyManager energyManager = EnergyManager.get(context.server());
                                       if (type == PowerType.ICE_EMPEROR && keyType != 9 && energyManager.getIceHeatBlastLockoutTicks(player.getUuid()) > 0) {
                                          int remaining = energyManager.getIceHeatBlastLockoutTicks(player.getUuid());
                                          player.sendMessage(
                                             Text.literal("Ice Emperor abilities locked for " + (remaining + 19) / 20 + "s after Heat Blast.")
                                                .formatted(Formatting.YELLOW),
                                             true
                                          );
                                       } else if (keyType == 7) {
                                          if (type == PowerType.BOOTS_SKY && power instanceof BootsOfTheSkyPower boots) {
                                             boots.toggleFeatherFalling(player);
                                          }
                                       } else if (keyType == 9) {
                                          if (type == PowerType.ICE_EMPEROR
                                             && power instanceof IceEmperorPower ice
                                             && energyManager.tryActivateIceHeatBlast(player)) {
                                             ice.activateHeatBlast(player);
                                          }
                                       } else if (keyType == 6) {
                                          if (type == PowerType.ICE_EMPEROR && power instanceof IceEmperorPower iceEmperor) {
                                             iceEmperor.cycleForm(player);
                                          }
                                       } else if (keyType == 2 && power instanceof ShadowArcherPower) {
                                          LOGGER.info("Sending Shadow Archer morph menu request response to {}", player.getUuid().toString());
                                          ServerPlayNetworking.send(player, new YPNetworking.OpenMorphMenuPayload());
                                       } else if (keyType == 5) {
                                          if (type == PowerType.BOOTS_SKY && EnergyManager.get(context.server()).trySpendAbility(player, keyType)) {
                                             power.onPrimaryActivate(player);
                                          }
                                       } else if (hasAbilityHandler(power, keyType)) {
                                          if (power.canUseKey(player, keyType)) {
                                             if (keyType == 2 && type == PowerType.ICE_EMPEROR && power instanceof IceEmperorPower iceEmperor) {
                                                EnergyManager.IceGuardSummonResult result = energyManager.trySpendIceGuardSummonEnergy(player);
                                                if (result != EnergyManager.IceGuardSummonResult.DENIED) {
                                                   iceEmperor.onTertiaryActivateWithEnergyState(player, result == EnergyManager.IceGuardSummonResult.LOW_ENERGY);
                                                }
                                             } else if (keyType == 2 && type == PowerType.SPIRIT_CHANNELER && power instanceof SpiritChannelerPower spirit) {
                                                spirit.activateRestlessShadows(player);
                                             } else if (energyManager.trySpendAbility(player, keyType)) {
                                                switch (keyType) {
                                                   case 0:
                                                      power.onPrimaryActivate(player);
                                                      break;
                                                   case 1:
                                                      power.onSecondaryActivate(player);
                                                      break;
                                                   case 2:
                                                      power.onTertiaryActivate(player);
                                                      break;
                                                   case 3:
                                                      power.onQuaternaryActivate(player);
                                                      break;
                                                   case 4:
                                                      power.onAlternateActivate(player);
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               );
         }
      );
      ServerPlayNetworking.registerGlobalReceiver(YPNetworking.MorphRequestPayload.ID, (payload, context) -> {
         String requested = payload.requested();
         context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            PowerManager manager = PowerManager.get(context.server());
            if (manager.getPowerType(player.getUuid()) == PowerType.SHADOW_ARCHER) {
               if (!GraceManager.blockAbility(player)) {
                  if (EnergyManager.get(context.server()).trySpendAbility(player, 2)) {
                     if (manager.getPowerInstance(PowerType.SHADOW_ARCHER) instanceof ShadowArcherPower archer) {
                        archer.requestMorph(player, requested);
                     }
                  }
               }
            }
         });
      });
   }

   public static void sendVfxEvent(ServerWorld world, YPNetworking.VfxEvent payload) {
      if (world != null && payload != null) {
         double limitSquared = 9216.0;
         int recipients = 0;

         for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (recipients >= 96) {
               break;
            }

            double dx = viewer.getX() - payload.x();
            double dy = viewer.getY() - payload.y();
            double dz = viewer.getZ() - payload.z();
            if (!(dx * dx + dy * dy + dz * dz > limitSquared) && ServerPlayNetworking.canSend(viewer, YPNetworking.VfxEvent.ID)) {
               ServerPlayNetworking.send(viewer, payload);
               recipients++;
            }
         }
      }
   }

   public static void clearVfxForSource(ServerWorld world, int sourceEntityId) {
      if (world != null && sourceEntityId >= 0) {
         YPNetworking.VfxClearPayload payload = new YPNetworking.VfxClearPayload(sourceEntityId);
         int recipients = 0;

         for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (recipients >= 96) {
               break;
            }

            if (ServerPlayNetworking.canSend(viewer, YPNetworking.VfxClearPayload.ID)) {
               ServerPlayNetworking.send(viewer, payload);
               recipients++;
            }
         }
      }
   }

   public static void clearVfxForSource(ServerPlayerEntity source) {
      if (source != null) {
         ServerWorld var2 = source.getEntityWorld();
         if (var2 instanceof ServerWorld) {
            clearVfxForSource(var2, source.getId());
         }
      }
   }

   private static boolean hasAbilityHandler(Power power, int keyType) {
      String name = switch (keyType) {
         case 0 -> "onPrimaryActivate";
         case 1 -> "onSecondaryActivate";
         case 2 -> "onTertiaryActivate";
         case 3 -> "onQuaternaryActivate";
         case 4 -> "onAlternateActivate";
         default -> "";
      };
      if (name.isEmpty()) {
         return false;
      } else {
         try {
            return power.getClass().getMethod(name, ServerPlayerEntity.class).getDeclaringClass() != Power.class;
         } catch (ReflectiveOperationException var4) {
            return false;
         }
      }
   }

   private static void handlePowerMenuAction(ServerPlayerEntity player, String rawAction) {
      if (player != null && player.getEntityWorld().getServer() != null && rawAction != null && rawAction.length() <= 64) {
         String action = rawAction.trim();
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         PowerType type = manager.getPowerType(player.getUuid());
         Power power = type == null ? null : manager.getPowerInstance(type);
         if (power != null) {
            if (action.startsWith("arrow:") && type == PowerType.SHADOW_ARCHER && power instanceof ShadowArcherPower archer) {
               archer.selectArrowType(player, action.substring("arrow:".length()));
            } else if (action.startsWith("ice:") && type == PowerType.ICE_EMPEROR && power instanceof IceEmperorPower ice) {
               ice.selectFormFromMenu(player, action.substring("ice:".length()));
            } else if (action.startsWith("spirit:") && type == PowerType.SPIRIT_CHANNELER && power instanceof SpiritChannelerPower spirit) {
               spirit.selectSpiritForm(player, action.substring("spirit:".length()));
            }
         }
      }
   }

   public static void syncPowerMenu(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && ServerPlayNetworking.canSend(player, YPNetworking.SyncPowerMenuPayload.ID)) {
         MinecraftServer server = player.getEntityWorld().getServer();
         PowerManager manager = PowerManager.get(server);
         PowerType type = manager.getAssignedPowerType(player.getUuid());
         boolean complete = type != null && RitualManager.isComplete(manager, player.getUuid(), type);
         String ritualStatus;
         if (type == null) {
            ritualStatus = "No power assigned.";
         } else if (complete) {
            ritualStatus = "Complete";
         } else {
            String progress = RitualManager.progressSummary(server, manager, player.getUuid(), type);
            ritualStatus = "In progress" + (progress != null && !progress.isBlank() ? " " + progress.trim() : "");
         }

         String selectedArrow = "";
         String availableArrows = "";
         String selectedIceForm = "";
         String availableIceForms = "";
         String selectedSpirit = "";
         String availableSpirits = "";
         Power assigned = type == null ? null : manager.getPowerInstance(type);
         if (type == PowerType.SHADOW_ARCHER && assigned instanceof ShadowArcherPower archer) {
            selectedArrow = archer.getSelectedArrowType(player).name();
            availableArrows = String.join("\n", archer.getAvailableArrowTypes(player).stream().map(Enum::name).toList());
         } else if (type == PowerType.ICE_EMPEROR && assigned instanceof IceEmperorPower ice) {
            selectedIceForm = ice.getSelectedFormId(player);
            availableIceForms = String.join("\n", ice.getEnabledFormIds());
         } else if (type == PowerType.SPIRIT_CHANNELER && assigned instanceof SpiritChannelerPower spirit) {
            selectedSpirit = spirit.getSelectedSpiritForm(player);
            availableSpirits = String.join("\n", spirit.getUnlockedSpiritForms(player));
         }

         YPNetworking.SyncPowerMenuPayload snapshot = new YPNetworking.SyncPowerMenuPayload(
            type == null ? "" : type.getId(),
            ritualStatus,
            complete,
            AwakeningManager.isAwakened(manager, player.getUuid(), type),
            GrimoireOfLightManager.hasGrimoire(player),
            AwakeningManager.cooldownTicks(player, type),
            selectedArrow,
            availableArrows,
            selectedIceForm,
            availableIceForms,
            selectedSpirit,
            availableSpirits
         );
         ServerPlayNetworking.send(player, snapshot);
      }
   }

   public static void openPoseidonConfirmation(ServerPlayerEntity player, int radius, int loadedChunks) {
      if (player != null && ServerPlayNetworking.canSend(player, YPNetworking.OpenPoseidonConfirmPayload.ID)) {
         ServerPlayNetworking.send(
            player, new YPNetworking.OpenPoseidonConfirmPayload(Math.max(64, Math.min(512, radius)), Math.max(0, Math.min(4096, loadedChunks)))
         );
      }
   }

   public static void openTeleportGambitStructureMenu(ServerPlayerEntity player, String structureIds, int cooldownTicks) {
      if (player != null && structureIds != null && ServerPlayNetworking.canSend(player, YPNetworking.OpenTeleportGambitStructuresPayload.ID)) {
         String boundedIds = structureIds.length() > 30000 ? structureIds.substring(0, 30000) : structureIds;
         ServerPlayNetworking.send(player, new YPNetworking.OpenTeleportGambitStructuresPayload(boundedIds, Math.max(0, Math.min(63072000, cooldownTicks))));
      }
   }

   public static void syncIceEmperorForm(MinecraftServer server, UUID playerId, String form, boolean active) {
      if (server != null && playerId != null) {
         YPNetworking.SyncIceEmperorFormPayload payload = new YPNetworking.SyncIceEmperorFormPayload(
            playerId.toString(), form == null ? "ARMOURED" : form, active
         );

         for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(recipient, YPNetworking.SyncIceEmperorFormPayload.ID)) {
               ServerPlayNetworking.send(recipient, payload);
            }
         }
      }
   }

   public static void syncIceEmperorFormsTo(ServerPlayerEntity recipient) {
      if (recipient != null
         && recipient.getEntityWorld().getServer() != null
         && ServerPlayNetworking.canSend(recipient, YPNetworking.SyncIceEmperorFormPayload.ID)) {
         PowerManager manager = PowerManager.get(recipient.getEntityWorld().getServer());

         for (ServerPlayerEntity online : recipient.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            boolean active = YPConfig.getInstance().ice_emperor_forms_enabled
               && !GraceManager.isActive(recipient.getEntityWorld().getServer())
               && manager.getPowerType(online.getUuid()) == PowerType.ICE_EMPEROR
               && manager.isPowerEnabled(online, PowerType.ICE_EMPEROR);
            String form = manager.getIceEmperorForm(online.getUuid());
            ServerPlayNetworking.send(recipient, new YPNetworking.SyncIceEmperorFormPayload(online.getUuid().toString(), form, active));
         }
      }
   }

   public static void syncFireGod(ServerPlayerEntity player, boolean hasFireGod) {
      if (player != null) {
         ServerPlayNetworking.send(player, new YPNetworking.SyncFireGodPayload(hasFireGod));
      }
   }

   public static void syncWitherAscendance(ServerPlayerEntity player, boolean active) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         YPNetworking.SyncWitherAscendancePayload payload = new YPNetworking.SyncWitherAscendancePayload(player.getId(), active);

         for (ServerPlayerEntity recipient : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(recipient, YPNetworking.SyncWitherAscendancePayload.ID)) {
               ServerPlayNetworking.send(recipient, payload);
            }
         }
      }
   }

   public static void syncWitherAscendanceTo(ServerPlayerEntity recipient) {
      if (recipient != null
         && recipient.getEntityWorld().getServer() != null
         && ServerPlayNetworking.canSend(recipient, YPNetworking.SyncWitherAscendancePayload.ID)) {
         for (ServerPlayerEntity online : recipient.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(recipient, new YPNetworking.SyncWitherAscendancePayload(online.getId(), AwakeningManager.isWitherForm(online.getUuid())));
         }
      }
   }

   public static void syncGrimoireMode(ServerPlayerEntity player, boolean active) {
      if (player != null) {
         ServerPlayNetworking.send(player, new YPNetworking.SyncGrimoireModePayload(active));
      }
   }

   public static void syncGrimoireBookVisual(ServerPlayerEntity wielder, boolean active, int durationTicks) {
      if (wielder != null) {
         ServerWorld payload = wielder.getEntityWorld();
         if (payload instanceof ServerWorld) {
            YPNetworking.SyncGrimoireBookPayload payloadx = new YPNetworking.SyncGrimoireBookPayload(
               wielder.getId(), active, active ? Math.max(1, Math.min(40, durationTicks)) : 0
            );

            for (ServerPlayerEntity recipient : payload.getPlayers()) {
               if (!(recipient.squaredDistanceTo(wielder) > 4096.0) && ServerPlayNetworking.canSend(recipient, YPNetworking.SyncGrimoireBookPayload.ID)) {
                  ServerPlayNetworking.send(recipient, payloadx);
               }
            }

            return;
         }
      }
   }

   public static void syncShadowArcher(ServerPlayerEntity player, boolean hasShadowArcher) {
      if (player != null) {
         ServerPlayNetworking.send(player, new YPNetworking.SyncShadowArcherPayload(hasShadowArcher));
      }
   }

   public static void syncShadowArcherMorphCatalog(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         Set<String> ids = PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherKills(player.getUuid());
         ServerPlayNetworking.send(player, new YPNetworking.SyncShadowArcherMorphCatalogPayload(String.join("\n", ids)));
      }
   }

   public static void syncShadowArcherMorphView(ServerPlayerEntity player, boolean morphed, int disguiseEntityId, float eyeHeight, float entityHeight) {
      if (player != null) {
         float safeEyeHeight = Float.isFinite(eyeHeight) ? Math.max(0.1F, Math.min(8.0F, eyeHeight)) : 1.62F;
         float safeEntityHeight = Float.isFinite(entityHeight) ? Math.max(0.1F, Math.min(16.0F, entityHeight)) : 1.8F;
         int safeEntityId = morphed ? Math.max(0, disguiseEntityId) : -1;
         ServerPlayNetworking.send(player, new YPNetworking.SyncShadowArcherMorphViewPayload(morphed, safeEntityId, safeEyeHeight, safeEntityHeight));
      }
   }

   public static void syncTimeDomainView(ServerPlayerEntity player, boolean active, boolean owner) {
      if (player != null && player.networkHandler != null) {
         ServerPlayNetworking.send(player, new YPNetworking.SyncTimeDomainViewPayload(active, active && owner));
      }
   }

   public static void syncHudWarning(ServerPlayerEntity player, String text, int ticks) {
      if (player != null) {
         ServerPlayNetworking.send(player, new YPNetworking.SyncHudWarningPayload(text == null ? "" : text, Math.max(0, ticks)));
      }
   }

   public static void broadcastTimeStopVisual(MinecraftServer server, int entityId, boolean frozen) {
      if (server != null) {
         YPNetworking.SyncTimeStopVisualPayload payload = new YPNetworking.SyncTimeStopVisualPayload(entityId, frozen);

         for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(recipient, payload);
         }
      }
   }

   public static void syncTimeStopVisualTo(ServerPlayerEntity recipient, int entityId) {
      if (recipient != null) {
         ServerPlayNetworking.send(recipient, new YPNetworking.SyncTimeStopVisualPayload(entityId, true));
      }
   }

   public static void syncEnergy(ServerPlayerEntity player, double energy, double maxEnergy, int bleedingTicks, boolean globalHealthEnabled) {
      if (player != null) {
         ServerPlayNetworking.send(player, new YPNetworking.SyncEnergyPayload(energy, maxEnergy, Math.max(0, bleedingTicks), globalHealthEnabled));
      }
   }

   public static void syncHudState(ServerPlayerEntity player) {
      if (player == null || player.networkHandler == null || player.getEntityWorld().getServer() == null) {
         return;
      }
      if (!ServerPlayNetworking.canSend(player, YPNetworking.SyncHudStatePayload.ID)) {
         return;
      }
      MinecraftServer server = player.getEntityWorld().getServer();
      PowerType assigned = PowerManager.get(server).getAssignedPowerType(player.getUuid());
      String powerId = assigned == null ? "" : assigned.getId();
      boolean grace = GraceManager.isActive(server);
      int combatTicks = CombatTagManager.getRemainingTicks(player.getUuid());
      int maxCombatTicks = CombatTagManager.maxDurationTicks();
      ServerPlayNetworking.send(player, new YPNetworking.SyncHudStatePayload(powerId, grace, combatTicks, maxCombatTicks));
   }

   public static void sendOpenPowerMenu(ServerPlayerEntity player) {
      if (player != null && player.networkHandler != null && ServerPlayNetworking.canSend(player, YPNetworking.OpenPowerMenuPayload.ID)) {
         ServerPlayNetworking.send(player, new YPNetworking.OpenPowerMenuPayload());
      }
   }

   public record OpenPowerMenuPayload() implements CustomPayload {
      public static final Id<YPNetworking.OpenPowerMenuPayload> ID = new Id<>(Identifier.of("yppowers", "open_power_menu"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.OpenPowerMenuPayload> CODEC = PacketCodec.unit(new YPNetworking.OpenPowerMenuPayload());

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncHudStatePayload(String powerId, boolean graceActive, int combatTicks, int maxCombatTicks) implements CustomPayload {
      public static final Id<YPNetworking.SyncHudStatePayload> ID = new Id<>(Identifier.of("yppowers", "sync_hud_state"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncHudStatePayload> CODEC = PacketCodec.of(
         (payload, buf) -> {
            buf.writeString(payload.powerId(), 32);
            buf.writeBoolean(payload.graceActive());
            buf.writeVarInt(payload.combatTicks());
            buf.writeVarInt(payload.maxCombatTicks());
         },
         buf -> new YPNetworking.SyncHudStatePayload(buf.readString(32), buf.readBoolean(), buf.readVarInt(), buf.readVarInt())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record FireGodBeamPayload(boolean active) implements CustomPayload {
      public static final Id<YPNetworking.FireGodBeamPayload> ID = new Id<>(Identifier.of("yppowers", "fire_god_beam"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.FireGodBeamPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeBoolean(payload.active()), buf -> new YPNetworking.FireGodBeamPayload(buf.readBoolean())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record GrimoireBeamPayload(boolean active) implements CustomPayload {
      public static final Id<YPNetworking.GrimoireBeamPayload> ID = new Id<>(Identifier.of("yppowers", "grimoire_beam"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.GrimoireBeamPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeBoolean(payload.active()), buf -> new YPNetworking.GrimoireBeamPayload(buf.readBoolean())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record KeybindPayload(int keyType) implements CustomPayload {
      public static final Id<YPNetworking.KeybindPayload> ID = new Id<>(Identifier.of("yppowers", "keybind"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.KeybindPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeVarInt(payload.keyType()), buf -> new YPNetworking.KeybindPayload(buf.readVarInt())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record MorphRequestPayload(String requested) implements CustomPayload {
      public static final Id<YPNetworking.MorphRequestPayload> ID = new Id<>(Identifier.of("yppowers", "morph_request"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.MorphRequestPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeString(payload.requested(), 128), buf -> new YPNetworking.MorphRequestPayload(buf.readString(128))
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record OpenMorphMenuPayload() implements CustomPayload {
      public static final Id<YPNetworking.OpenMorphMenuPayload> ID = new Id<>(Identifier.of("yppowers", "open_morph_menu"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.OpenMorphMenuPayload> CODEC = PacketCodec.unit(new YPNetworking.OpenMorphMenuPayload());

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record OpenPoseidonConfirmPayload(int radius, int loadedChunks) implements CustomPayload {
      public static final Id<YPNetworking.OpenPoseidonConfirmPayload> ID = new Id<>(Identifier.of("yppowers", "open_poseidon_confirm"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.OpenPoseidonConfirmPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeVarInt(payload.radius());
         buf.writeVarInt(payload.loadedChunks());
      }, buf -> new YPNetworking.OpenPoseidonConfirmPayload(buf.readVarInt(), buf.readVarInt()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record OpenTeleportGambitStructuresPayload(String structureIds, int cooldownTicks) implements CustomPayload {
      public static final Id<YPNetworking.OpenTeleportGambitStructuresPayload> ID = new Id<>(Identifier.of("yppowers", "open_gambit_structures"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.OpenTeleportGambitStructuresPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeString(payload.structureIds(), 30000);
         buf.writeVarInt(payload.cooldownTicks());
      }, buf -> new YPNetworking.OpenTeleportGambitStructuresPayload(buf.readString(30000), buf.readVarInt()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record PoseidonSinkConfirmPayload() implements CustomPayload {
      public static final Id<YPNetworking.PoseidonSinkConfirmPayload> ID = new Id<>(Identifier.of("yppowers", "poseidon_sink_confirm"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.PoseidonSinkConfirmPayload> CODEC = PacketCodec.unit(
         new YPNetworking.PoseidonSinkConfirmPayload()
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record PowerMenuActionPayload(String action) implements CustomPayload {
      public static final Id<YPNetworking.PowerMenuActionPayload> ID = new Id<>(Identifier.of("yppowers", "power_menu_action"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.PowerMenuActionPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeString(payload.action(), 64), buf -> new YPNetworking.PowerMenuActionPayload(buf.readString(64))
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record PowerMenuRequestPayload() implements CustomPayload {
      public static final Id<YPNetworking.PowerMenuRequestPayload> ID = new Id<>(Identifier.of("yppowers", "power_menu_request"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.PowerMenuRequestPayload> CODEC = PacketCodec.unit(
         new YPNetworking.PowerMenuRequestPayload()
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncEnergyPayload(double energy, double maxEnergy, int bleedingTicks, boolean globalHealthEnabled) implements CustomPayload {
      public static final Id<YPNetworking.SyncEnergyPayload> ID = new Id<>(Identifier.of("yppowers", "sync_energy"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncEnergyPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeDouble(payload.energy());
         buf.writeDouble(payload.maxEnergy());
         buf.writeVarInt(payload.bleedingTicks());
         buf.writeBoolean(payload.globalHealthEnabled());
      }, buf -> new YPNetworking.SyncEnergyPayload(buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readBoolean()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncFireGodPayload(boolean enabled) implements CustomPayload {
      public static final Id<YPNetworking.SyncFireGodPayload> ID = new Id<>(Identifier.of("yppowers", "sync_fire_god"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncFireGodPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeBoolean(payload.enabled()), buf -> new YPNetworking.SyncFireGodPayload(buf.readBoolean())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncGrimoireBookPayload(int entityId, boolean active, int durationTicks) implements CustomPayload {
      public static final Id<YPNetworking.SyncGrimoireBookPayload> ID = new Id<>(Identifier.of("yppowers", "sync_grimoire_book"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncGrimoireBookPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeVarInt(payload.entityId());
         buf.writeBoolean(payload.active());
         buf.writeVarInt(payload.durationTicks());
      }, buf -> new YPNetworking.SyncGrimoireBookPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncGrimoireModePayload(boolean active) implements CustomPayload {
      public static final Id<YPNetworking.SyncGrimoireModePayload> ID = new Id<>(Identifier.of("yppowers", "sync_grimoire_mode"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncGrimoireModePayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeBoolean(payload.active()), buf -> new YPNetworking.SyncGrimoireModePayload(buf.readBoolean())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncHudWarningPayload(String text, int ticks) implements CustomPayload {
      public static final Id<YPNetworking.SyncHudWarningPayload> ID = new Id<>(Identifier.of("yppowers", "sync_hud_warning"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncHudWarningPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeString(payload.text(), 128);
         buf.writeVarInt(payload.ticks());
      }, buf -> new YPNetworking.SyncHudWarningPayload(buf.readString(128), buf.readVarInt()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncIceEmperorFormPayload(String playerUuid, String form, boolean active) implements CustomPayload {
      public static final Id<YPNetworking.SyncIceEmperorFormPayload> ID = new Id<>(Identifier.of("yppowers", "sync_ice_emperor_form"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncIceEmperorFormPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeString(payload.playerUuid(), 36);
         buf.writeString(payload.form(), 16);
         buf.writeBoolean(payload.active());
      }, buf -> new YPNetworking.SyncIceEmperorFormPayload(buf.readString(36), buf.readString(16), buf.readBoolean()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncPowerMenuPayload(
      String powerId,
      String ritualStatus,
      boolean ritualComplete,
      boolean awakeningUnlocked,
      boolean carriesGrimoire,
      int awakeningCooldownTicks,
      String selectedArrow,
      String availableArrows,
      String selectedIceForm,
      String availableIceForms,
      String selectedSpirit,
      String availableSpirits
   ) implements CustomPayload {
      public static final Id<YPNetworking.SyncPowerMenuPayload> ID = new Id<>(Identifier.of("yppowers", "sync_power_menu"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncPowerMenuPayload> CODEC = PacketCodec.of(
         (payload, buf) -> {
            buf.writeString(payload.powerId(), 32);
            buf.writeString(payload.ritualStatus(), 128);
            buf.writeBoolean(payload.ritualComplete());
            buf.writeBoolean(payload.awakeningUnlocked());
            buf.writeBoolean(payload.carriesGrimoire());
            buf.writeVarInt(payload.awakeningCooldownTicks());
            buf.writeString(payload.selectedArrow(), 32);
            buf.writeString(payload.availableArrows(), 256);
            buf.writeString(payload.selectedIceForm(), 24);
            buf.writeString(payload.availableIceForms(), 128);
            buf.writeString(payload.selectedSpirit(), 24);
            buf.writeString(payload.availableSpirits(), 128);
         },
         buf -> new YPNetworking.SyncPowerMenuPayload(
            buf.readString(32),
            buf.readString(128),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readVarInt(),
            buf.readString(32),
            buf.readString(256),
            buf.readString(24),
            buf.readString(128),
            buf.readString(24),
            buf.readString(128)
         )
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncShadowArcherMorphCatalogPayload(String encodedIds) implements CustomPayload {
      public static final Id<YPNetworking.SyncShadowArcherMorphCatalogPayload> ID = new Id<>(Identifier.of("yppowers", "sync_shadow_archer_morph_catalog"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncShadowArcherMorphCatalogPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeString(payload.encodedIds(), 32767), buf -> new YPNetworking.SyncShadowArcherMorphCatalogPayload(buf.readString(32767))
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncShadowArcherMorphViewPayload(boolean morphed, int disguiseEntityId, float eyeHeight, float entityHeight) implements CustomPayload {
      public static final Id<YPNetworking.SyncShadowArcherMorphViewPayload> ID = new Id<>(Identifier.of("yppowers", "sync_shadow_archer_morph_view"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncShadowArcherMorphViewPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeBoolean(payload.morphed());
         buf.writeVarInt(payload.disguiseEntityId());
         buf.writeFloat(payload.eyeHeight());
         buf.writeFloat(payload.entityHeight());
      }, buf -> new YPNetworking.SyncShadowArcherMorphViewPayload(buf.readBoolean(), buf.readVarInt(), buf.readFloat(), buf.readFloat()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncShadowArcherPayload(boolean enabled) implements CustomPayload {
      public static final Id<YPNetworking.SyncShadowArcherPayload> ID = new Id<>(Identifier.of("yppowers", "sync_shadow_archer"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncShadowArcherPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeBoolean(payload.enabled()), buf -> new YPNetworking.SyncShadowArcherPayload(buf.readBoolean())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncTimeDomainViewPayload(boolean active, boolean owner) implements CustomPayload {
      public static final Id<YPNetworking.SyncTimeDomainViewPayload> ID = new Id<>(Identifier.of("yppowers", "sync_time_domain_view"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncTimeDomainViewPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeBoolean(payload.active());
         buf.writeBoolean(payload.owner());
      }, buf -> new YPNetworking.SyncTimeDomainViewPayload(buf.readBoolean(), buf.readBoolean()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncTimeStopVisualPayload(int entityId, boolean frozen) implements CustomPayload {
      public static final Id<YPNetworking.SyncTimeStopVisualPayload> ID = new Id<>(Identifier.of("yppowers", "sync_time_stop_visual"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncTimeStopVisualPayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeVarInt(payload.entityId());
         buf.writeBoolean(payload.frozen());
      }, buf -> new YPNetworking.SyncTimeStopVisualPayload(buf.readVarInt(), buf.readBoolean()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record SyncWitherAscendancePayload(int entityId, boolean active) implements CustomPayload {
      public static final Id<YPNetworking.SyncWitherAscendancePayload> ID = new Id<>(Identifier.of("yppowers", "sync_wither_ascendance"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.SyncWitherAscendancePayload> CODEC = PacketCodec.of((payload, buf) -> {
         buf.writeVarInt(payload.entityId());
         buf.writeBoolean(payload.active());
      }, buf -> new YPNetworking.SyncWitherAscendancePayload(buf.readVarInt(), buf.readBoolean()));

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record TeleportGambitStructureRequestPayload(String structureId) implements CustomPayload {
      public static final Id<YPNetworking.TeleportGambitStructureRequestPayload> ID = new Id<>(Identifier.of("yppowers", "gambit_structure_request"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.TeleportGambitStructureRequestPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeString(payload.structureId(), 128), buf -> new YPNetworking.TeleportGambitStructureRequestPayload(buf.readString(128))
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record TimeWarperBlackHoleHoldPayload(boolean held) implements CustomPayload {
      public static final Id<YPNetworking.TimeWarperBlackHoleHoldPayload> ID = new Id<>(Identifier.of("yppowers", "time_warper_black_hole_hold"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.TimeWarperBlackHoleHoldPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeBoolean(payload.held()), buf -> new YPNetworking.TimeWarperBlackHoleHoldPayload(buf.readBoolean())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record VfxClearPayload(int sourceEntityId) implements CustomPayload {
      public static final Id<YPNetworking.VfxClearPayload> ID = new Id<>(Identifier.of("yppowers", "vfx_clear_v1"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.VfxClearPayload> CODEC = PacketCodec.of(
         (payload, buf) -> buf.writeVarInt(payload.sourceEntityId()), buf -> new YPNetworking.VfxClearPayload(buf.readVarInt())
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }

   public record VfxEvent(
      String powerId,
      String effectId,
      int sourceEntityId,
      double x,
      double y,
      double z,
      float directionX,
      float directionY,
      float directionZ,
      float scale,
      int durationTicks,
      float cameraShake,
      long seed
   ) implements CustomPayload {
      public static final Id<YPNetworking.VfxEvent> ID = new Id<>(Identifier.of("yppowers", "vfx_event_v1"));
      public static final PacketCodec<RegistryByteBuf, YPNetworking.VfxEvent> CODEC = PacketCodec.of(
         (payload, buf) -> {
            buf.writeString(payload.powerId(), 32);
            buf.writeString(payload.effectId(), 64);
            buf.writeVarInt(payload.sourceEntityId());
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
            buf.writeFloat(payload.directionX());
            buf.writeFloat(payload.directionY());
            buf.writeFloat(payload.directionZ());
            buf.writeFloat(payload.scale());
            buf.writeVarInt(payload.durationTicks());
            buf.writeFloat(payload.cameraShake());
            buf.writeLong(payload.seed());
         },
         buf -> new YPNetworking.VfxEvent(
            buf.readString(32),
            buf.readString(64),
            buf.readVarInt(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readVarInt(),
            buf.readFloat(),
            buf.readLong()
         )
      );

      @Override
      public Id<? extends CustomPayload> getId() {
         return ID;
      }
   }
}
