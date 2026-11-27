package com.yppowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.combat.CombatTagManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.energy.GlobalHealthManager;
import com.yppowers.item.ArtifactSpawnManager;
import com.yppowers.item.GauntletOfDarknessManager;
import com.yppowers.item.GrimoireOfLightManager;
import com.yppowers.item.YPItems;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.RitualManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.command.permission.Permission.Level;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class YPCommands {
   public static void register() {
      CommandRegistrationCallback.EVENT.register(YPCommands::registerCommands);
   }

   private static void registerCommands(
      CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment environment
   ) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("grace")
               .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
            .then(
               CommandManager.argument("minutes", IntegerArgumentType.integer(0, 1789569))
                  .executes(
                     context -> {
                        int minutes = IntegerArgumentType.getInteger(context, "minutes");
                        PowerManager manager = PowerManager.get(((ServerCommandSource)context.getSource()).getServer());
                        if (minutes == 0) {
                           boolean wasActive = manager.isGracePeriodActive();
                           manager.setGraceTicksRemaining(0);
                           if (wasActive) {
                              ((ServerCommandSource)context.getSource())
                                 .getServer()
                                 .getPlayerManager()
                                 .broadcast(
                                    Text.literal("Grace period ended by an administrator. Abilities and PvP are enabled again.").formatted(Formatting.GREEN),
                                    false
                                 );
                           }

                           ((ServerCommandSource)context.getSource())
                              .sendFeedback(() -> Text.literal(wasActive ? "Grace period disabled." : "No grace period was active."), true);
                           return 1;
                        } else {
                           int ticks = Math.multiplyExact(minutes, 1200);
                           manager.setGraceTicksRemaining(ticks);
                           CombatTagManager.clearAllAndSync(((ServerCommandSource)context.getSource()).getServer());
                           for (ServerPlayerEntity online : ((ServerCommandSource)context.getSource()).getServer().getPlayerManager().getPlayerList()) {
                              YPNetworking.syncHudState(online);
                           }
                           ((ServerCommandSource)context.getSource())
                              .getServer()
                              .getPlayerManager()
                              .broadcast(
                                 Text.literal(
                                       "Grace period started for "
                                          + minutes
                                          + (minutes == 1 ? " minute. Abilities and PvP are disabled." : " minutes. Abilities and PvP are disabled.")
                                    )
                                    .formatted(Formatting.GOLD),
                                 false
                              );
                           ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("Grace period set to " + minutes + " minute(s)."), true);
                           return 1;
                        }
                     }
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(
                                                "yp"
                                             )
                                             .then(CommandManager.literal("help").executes(context -> showHelp((ServerCommandSource)context.getSource())))
                                             .then(CommandManager.literal("guide").executes(context -> {
                                                YPNetworking.sendOpenPowerMenu(((ServerCommandSource)context.getSource()).getPlayerOrThrow());
                                                return 1;
                                             }))
                                             .then(
                                                ((LiteralArgumentBuilder)CommandManager.literal("combat")
                                                      .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                                   .then(
                                                      CommandManager.literal("clear")
                                                         .then(
                                                            CommandManager.argument("player", EntityArgumentType.player())
                                                               .executes(context -> {
                                                                  ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                                  CombatTagManager.clearTag(target.getUuid());
                                                                  YPNetworking.syncHudState(target);
                                                                  ((ServerCommandSource)context.getSource())
                                                                     .sendFeedback(
                                                                        () -> Text.literal("Cleared combat tag for " + target.getName().getString() + ".")
                                                                           .formatted(Formatting.GREEN),
                                                                        true
                                                                     );
                                                                  return 1;
                                                               })
                                                         )
                                                   )
                                             )
                                             .then(
                                                ((LiteralArgumentBuilder)CommandManager.literal("config")
                                                      .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                                   .then(
                                                      CommandManager.literal("reload")
                                                         .executes(context -> {
                                                            YPConfig.getInstance().load();
                                                            MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
                                                            GlobalHealthManager.applyAll(server);
                                                            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                                                               YPNetworking.syncHudState(online);
                                                            }
                                                            ((ServerCommandSource)context.getSource())
                                                               .sendFeedback(
                                                                  () -> Text.literal("Reloaded config/yppowers_config.json.").formatted(Formatting.GREEN), true
                                                               );
                                                            return 1;
                                                         })
                                                   )
                                             ))
                                          .then(
                                             CommandManager.literal("status")
                                                .executes(
                                                   context -> showStatus(
                                                      (ServerCommandSource)context.getSource(), ((ServerCommandSource)context.getSource()).getPlayerOrThrow()
                                                   )
                                                )
                                          ))
                                       .then(
                                          ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("power")
                                                   .then(
                                                      ((LiteralArgumentBuilder)CommandManager.literal("assign")
                                                            .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                                         .then(
                                                            CommandManager.argument("player", EntityArgumentType.player())
                                                               .then(
                                                                  CommandManager.argument("power", StringArgumentType.word())
                                                                     .suggests((context, builder) -> {
                                                                        for (PowerType type : PowerType.values()) {
                                                                           builder.suggest(type.getId());
                                                                        }

                                                                        return builder.buildFuture();
                                                                     })
                                                                     .executes(
                                                                        context -> {
                                                                           ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                                           String powerId = StringArgumentType.getString(context, "power");
                                                                           PowerType type = PowerType.fromId(powerId);
                                                                           if (type == null) {
                                                                              ((ServerCommandSource)context.getSource())
                                                                                 .sendError(Text.literal("Unknown power: " + powerId).formatted(Formatting.RED));
                                                                              return 0;
                                                                           } else {
                                                                              PowerManager.get(((ServerCommandSource)context.getSource()).getServer())
                                                                                 .assignPower(target, type);
                                                                              YPNetworking.syncHudState(target);
                                                                              ((ServerCommandSource)context.getSource())
                                                                                 .sendFeedback(
                                                                                    () -> Text.literal(
                                                                                          "Assigned power "
                                                                                             + type.getDisplayName()
                                                                                             + " to "
                                                                                             + target.getName().getString()
                                                                                       )
                                                                                       .formatted(Formatting.GREEN),
                                                                                    true
                                                                                 );
                                                                              return 1;
                                                                           }
                                                                        }
                                                                     )
                                                               )
                                                         )
                                                   ))
                                                .then(
                                                   ((LiteralArgumentBuilder)CommandManager.literal("clear")
                                                         .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                                      .then(
                                                         CommandManager.argument("player", EntityArgumentType.player())
                                                            .executes(
                                                               context -> {
                                                                  ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                                  PowerManager.get(((ServerCommandSource)context.getSource()).getServer()).clearPower(target);
                                                                  YPNetworking.syncHudState(target);
                                                                  ((ServerCommandSource)context.getSource())
                                                                     .sendFeedback(
                                                                        () -> Text.literal("Cleared power for " + target.getName().getString())
                                                                           .formatted(Formatting.GREEN),
                                                                        true
                                                                     );
                                                                  return 1;
                                                               }
                                                            )
                                                      )
                                                ))
                                             .then(
                                                CommandManager.literal("list")
                                                   .executes(context -> {
                                                      List<String> names = new ArrayList<>();
                                                      for (PowerType pt : PowerType.values()) {
                                                         names.add(pt.getId() + " (" + pt.getDisplayName() + ")");
                                                      }
                                                      ((ServerCommandSource)context.getSource())
                                                         .sendFeedback(
                                                            () -> Text.literal("Available powers (" + names.size() + "): " + String.join(", ", names))
                                                               .formatted(Formatting.AQUA),
                                                            false
                                                         );
                                                      return 1;
                                                   })
                                             )
                                             .then(
                                                ((LiteralArgumentBuilder)CommandManager.literal("check")
                                                      .executes(
                                                         context -> {
                                                            ServerPlayerEntity target = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                                                            PowerType type = PowerManager.get(((ServerCommandSource)context.getSource()).getServer())
                                                               .getAssignedPowerType(target.getUuid());
                                                            String powerName = type != null ? type.getDisplayName() : "None";
                                                            ((ServerCommandSource)context.getSource())
                                                               .sendFeedback(
                                                                  () -> Text.literal(target.getName().getString() + "'s Power: " + powerName)
                                                                     .formatted(Formatting.YELLOW),
                                                                  false
                                                               );
                                                            return 1;
                                                         }
                                                      ))
                                                   .then(
                                                      ((RequiredArgumentBuilder)CommandManager.argument("player", EntityArgumentType.player())
                                                            .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                                         .executes(
                                                            context -> {
                                                               ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                               PowerType type = PowerManager.get(((ServerCommandSource)context.getSource()).getServer())
                                                                  .getAssignedPowerType(target.getUuid());
                                                               String powerName = type != null ? type.getDisplayName() : "None";
                                                               ((ServerCommandSource)context.getSource())
                                                                  .sendFeedback(
                                                                     () -> Text.literal(target.getName().getString() + "'s Power: " + powerName)
                                                                        .formatted(Formatting.YELLOW),
                                                                     false
                                                                  );
                                                               return 1;
                                                            }
                                                         )
                                                   )
                                             )
                                       ))
                                    .then(
                                       ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("health")
                                                   .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                                .then(
                                                   CommandManager.literal("enable")
                                                      .executes(
                                                         context -> {
                                                            YPConfig config = YPConfig.getInstance();
                                                            config.global_health_enabled = true;
                                                            config.save();
                                                            GlobalHealthManager.applyAll(((ServerCommandSource)context.getSource()).getServer());
                                                            ((ServerCommandSource)context.getSource())
                                                               .sendFeedback(
                                                                  () -> Text.literal(
                                                                        "Global player health enabled (target "
                                                                           + config.global_player_health_value
                                                                           + " HP). Mobs are unchanged."
                                                                     )
                                                                     .formatted(Formatting.GREEN),
                                                                  true
                                                               );
                                                            return 1;
                                                         }
                                                      )
                                                ))
                                             .then(
                                                CommandManager.literal("disable")
                                                   .executes(
                                                      context -> {
                                                         YPConfig config = YPConfig.getInstance();
                                                         config.global_health_enabled = false;
                                                         config.save();
                                                         GlobalHealthManager.applyAll(((ServerCommandSource)context.getSource()).getServer());
                                                         ((ServerCommandSource)context.getSource())
                                                            .sendFeedback(
                                                               () -> Text.literal("Global player health disabled. Mobs are unchanged.")
                                                                  .formatted(Formatting.YELLOW),
                                                               true
                                                            );
                                                         return 1;
                                                      }
                                                   )
                                             ))
                                          .then(
                                             CommandManager.literal("status")
                                                .executes(
                                                   context -> {
                                                      YPConfig config = YPConfig.getInstance();
                                                      ((ServerCommandSource)context.getSource())
                                                         .sendFeedback(
                                                            () -> Text.literal(
                                                                  "Global player health: "
                                                                     + (
                                                                        config.global_health_enabled
                                                                           ? "enabled, " + config.global_player_health_value + " HP"
                                                                           : "disabled"
                                                                     )
                                                                     + " (mobs unchanged)."
                                                               )
                                                               .formatted(Formatting.YELLOW),
                                                            false
                                                         );
                                                      return 1;
                                                   }
                                                )
                                          )
                                    ))
                                 .then(
                                    ((LiteralArgumentBuilder)CommandManager.literal("energy")
                                          .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                       .then(
                                          CommandManager.argument("player", EntityArgumentType.player())
                                             .then(
                                                CommandManager.literal("set")
                                                   .then(
                                                      CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0, 100000.0))
                                                         .executes(
                                                            context -> {
                                                               ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                               double amount = DoubleArgumentType.getDouble(context, "amount");
                                                               EnergyManager.get(((ServerCommandSource)context.getSource()).getServer())
                                                                  .setEnergy(target.getUuid(), amount);
                                                               EnergyManager.get(((ServerCommandSource)context.getSource()).getServer()).sync(target);
                                                               ((ServerCommandSource)context.getSource())
                                                                  .sendFeedback(
                                                                     () -> Text.literal("Set " + target.getName().getString() + " Energy to " + amount + ".")
                                                                        .formatted(Formatting.GREEN),
                                                                     true
                                                                  );
                                                               return 1;
                                                            }
                                                         )
                                                   )
                                             )
                                       )
                                 ))
                              .then(
                                 CommandManager.literal("alliance")
                                    .then(
                                       CommandManager.literal("invite")
                                          .then(
                                             CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(context -> sendAllianceInvite((ServerCommandSource)context.getSource(), EntityArgumentType.getPlayer(context, "player")))
                                          )
                                    )
                                    .then(
                                       CommandManager.literal("add")
                                          .then(
                                             CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(context -> sendAllianceInvite((ServerCommandSource)context.getSource(), EntityArgumentType.getPlayer(context, "player")))
                                          )
                                    )
                                    .then(
                                       CommandManager.literal("accept")
                                          .then(
                                             CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(context -> {
                                                   ServerPlayerEntity self = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                                                   if (blockAllianceInCombat((ServerCommandSource)context.getSource(), self)) {
                                                      return 0;
                                                   }
                                                   ServerPlayerEntity sender = EntityArgumentType.getPlayer(context, "player");
                                                   MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
                                                   AllianceManager alliances = AllianceManager.get(server);
                                                   if (!alliances.acceptInvite(self.getUuid(), sender.getUuid(), server.getTicks())) {
                                                      ((ServerCommandSource)context.getSource())
                                                         .sendError(Text.literal("No pending alliance invite from " + sender.getName().getString() + ".").formatted(Formatting.RED));
                                                      return 0;
                                                   }
                                                   alliances.rememberName(self.getUuid(), self.getName().getString());
                                                   alliances.rememberName(sender.getUuid(), sender.getName().getString());
                                                   ((ServerCommandSource)context.getSource())
                                                      .sendFeedback(() -> Text.literal("You are now allied with " + sender.getName().getString() + "!").formatted(Formatting.GREEN), false);
                                                   sender.sendMessage(Text.literal(self.getName().getString() + " accepted your alliance invite!").formatted(Formatting.GREEN));
                                                   return 1;
                                                })
                                          )
                                    )
                                    .then(
                                       CommandManager.literal("decline")
                                          .then(
                                             CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(context -> {
                                                   ServerPlayerEntity self = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                                                   ServerPlayerEntity sender = EntityArgumentType.getPlayer(context, "player");
                                                   AllianceManager alliances = AllianceManager.get(((ServerCommandSource)context.getSource()).getServer());
                                                   if (!alliances.declineInvite(self.getUuid(), sender.getUuid())) {
                                                      ((ServerCommandSource)context.getSource())
                                                         .sendError(Text.literal("No pending alliance invite from " + sender.getName().getString() + ".").formatted(Formatting.RED));
                                                      return 0;
                                                   }
                                                   ((ServerCommandSource)context.getSource())
                                                      .sendFeedback(() -> Text.literal("Declined alliance invite from " + sender.getName().getString() + ".").formatted(Formatting.YELLOW), false);
                                                   sender.sendMessage(Text.literal(self.getName().getString() + " declined your alliance invite.").formatted(Formatting.YELLOW));
                                                   return 1;
                                                })
                                          )
                                    )
                                    .then(
                                       CommandManager.literal("remove")
                                          .then(
                                             CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(context -> {
                                                   ServerPlayerEntity sourcePlayer = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                                                   if (blockAllianceInCombat((ServerCommandSource)context.getSource(), sourcePlayer)) {
                                                      return 0;
                                                   }
                                                   ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                   AllianceManager.get(((ServerCommandSource)context.getSource()).getServer())
                                                      .removeAlliance(sourcePlayer.getUuid(), target.getUuid());
                                                   ((ServerCommandSource)context.getSource())
                                                      .sendFeedback(
                                                         () -> Text.literal("Removed alliance with " + target.getName().getString()).formatted(Formatting.YELLOW),
                                                         false
                                                      );
                                                   target.sendMessage(
                                                      Text.literal(sourcePlayer.getName().getString() + " removed their alliance with you.").formatted(Formatting.RED)
                                                   );
                                                   return 1;
                                                })
                                          )
                                    )
                                    .then(
                                       CommandManager.literal("list")
                                          .executes(context -> {
                                             ServerPlayerEntity sourcePlayer = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                                             MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
                                             AllianceManager alliances = AllianceManager.get(server);
                                             Set<UUID> allies = alliances.getAllies(sourcePlayer.getUuid());
                                             if (allies.isEmpty()) {
                                                ((ServerCommandSource)context.getSource())
                                                   .sendFeedback(() -> Text.literal("You have no allies.").formatted(Formatting.YELLOW), false);
                                             } else {
                                                List<String> names = new ArrayList<>(allies.size());
                                                for (UUID allyId : allies) {
                                                   boolean online = server.getPlayerManager().getPlayer(allyId) != null;
                                                   String display = alliances.getDisplayName(server, allyId);
                                                   names.add(online ? display : display + " (offline)");
                                                }
                                                ((ServerCommandSource)context.getSource())
                                                   .sendFeedback(() -> Text.literal("Allies (" + names.size() + "): " + String.join(", ", names)).formatted(Formatting.GREEN), false);
                                             }
                                             return 1;
                                          })
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)CommandManager.literal("web_slingers")
                                    .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                 .then(
                                    CommandManager.literal("give")
                                       .then(
                                          CommandManager.argument("player", EntityArgumentType.player())
                                             .executes(
                                                context -> {
                                                   ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                                   ItemStack stack = new ItemStack(YPItems.WEB_SLINGERS);
                                                   stack.set(
                                                      DataComponentTypes.CUSTOM_NAME, Text.literal("Web Slingers").formatted(Formatting.AQUA, Formatting.BOLD)
                                                   );
                                                   if (!target.giveItemStack(stack)) {
                                                      target.dropItem(stack, false);
                                                   }

                                                   ((ServerCommandSource)context.getSource())
                                                      .sendFeedback(
                                                         () -> Text.literal("Gave Web Slingers to " + target.getName().getString()).formatted(Formatting.GREEN),
                                                         true
                                                      );
                                                   return 1;
                                                }
                                             )
                                       )
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)CommandManager.literal("reset")
                                 .executes(
                                    context -> resetGear(
                                       (ServerCommandSource)context.getSource(), ((ServerCommandSource)context.getSource()).getPlayerOrThrow(), true
                                    )
                                 ))
                              .then(
                                 ((RequiredArgumentBuilder)CommandManager.argument("player", EntityArgumentType.player())
                                       .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                                    .executes(
                                       context -> resetGear((ServerCommandSource)context.getSource(), EntityArgumentType.getPlayer(context, "player"), false)
                                    )
                              )
                        ))
                     .then(
                        ((LiteralArgumentBuilder)CommandManager.literal("test")
                              .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                           .then(
                              ((RequiredArgumentBuilder)((RequiredArgumentBuilder)CommandManager.argument("target", EntityArgumentType.player())
                                       .executes(
                                          context -> toggleTestMode(
                                             (ServerCommandSource)context.getSource(), EntityArgumentType.getPlayer(context, "target"), null
                                          )
                                       ))
                                    .then(
                                       CommandManager.literal("on")
                                          .executes(
                                             context -> toggleTestMode(
                                                (ServerCommandSource)context.getSource(), EntityArgumentType.getPlayer(context, "target"), true
                                             )
                                          )
                                    ))
                                 .then(
                                    CommandManager.literal("off")
                                       .executes(
                                          context -> toggleTestMode(
                                             (ServerCommandSource)context.getSource(), EntityArgumentType.getPlayer(context, "target"), false
                                          )
                                       )
                                 )
                           )
                     ))
                  .then(
                     ((LiteralArgumentBuilder)CommandManager.literal("rituals")
                           .executes(
                              context -> showRitualStatus(
                                 (ServerCommandSource)context.getSource(), ((ServerCommandSource)context.getSource()).getPlayerOrThrow()
                              )
                           ))
                        .then(
                           ((RequiredArgumentBuilder)CommandManager.argument("player", EntityArgumentType.player())
                                 .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                              .executes(context -> showRitualStatus((ServerCommandSource)context.getSource(), EntityArgumentType.getPlayer(context, "player")))
                        )
                  ))
               .then(
                  ((LiteralArgumentBuilder)CommandManager.literal("artifacts")
                        .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                     .executes(context -> reportArtifactLocations((ServerCommandSource)context.getSource()))
               ))
            .then(
               ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("artifact")
                           .requires(source -> source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))))
                        .then(CommandManager.literal("locate").executes(context -> reportArtifactLocations((ServerCommandSource)context.getSource()))))
                     .then(
                        CommandManager.literal("disable").then(CommandManager.argument("artifact", StringArgumentType.word()).suggests((context, builder) -> {
                           builder.suggest("gauntlet");
                           builder.suggest("grimoire");
                           return builder.buildFuture();
                        }).executes(
                           context -> setArtifactSpawning((ServerCommandSource)context.getSource(), StringArgumentType.getString(context, "artifact"), false)
                        ))
                     ))
                  .then(CommandManager.literal("enable").then(CommandManager.argument("artifact", StringArgumentType.word()).suggests((context, builder) -> {
                     builder.suggest("gauntlet");
                     builder.suggest("grimoire");
                     return builder.buildFuture();
                  }).executes(context -> setArtifactSpawning((ServerCommandSource)context.getSource(), StringArgumentType.getString(context, "artifact"), true))))
            )
      );
   }

   private static boolean blockAllianceInCombat(ServerCommandSource source, ServerPlayerEntity player) {
      YPConfig cfg = YPConfig.getInstance();
      if (cfg.combat_tag_enabled && cfg.combat_tag_block_alliance_changes && CombatTagManager.isTagged(player.getUuid())) {
         source.sendError(
            Text.literal(
                  "You cannot change alliances while in PvP combat ("
                     + CombatTagManager.formatSeconds(CombatTagManager.getRemainingTicks(player.getUuid()))
                     + " remaining)."
               )
               .formatted(Formatting.RED)
         );
         return true;
      }
      return false;
   }

   private static int sendAllianceInvite(ServerCommandSource source, ServerPlayerEntity other)
      throws com.mojang.brigadier.exceptions.CommandSyntaxException {
      ServerPlayerEntity self = source.getPlayerOrThrow();
      if (blockAllianceInCombat(source, self)) {
         return 0;
      }
      MinecraftServer server = source.getServer();
      AllianceManager alliances = AllianceManager.get(server);
      AllianceManager.InviteResult result = alliances.sendInvite(self.getUuid(), other.getUuid(), server.getTicks());
      return switch (result) {
         case SELF_INVITE -> {
            source.sendError(Text.literal("You cannot ally with yourself.").formatted(Formatting.RED));
            yield 0;
         }
         case ALREADY_ALLIED -> {
            source.sendError(Text.literal("You are already allied with " + other.getName().getString() + ".").formatted(Formatting.YELLOW));
            yield 0;
         }
         case ALREADY_PENDING -> {
            source.sendError(Text.literal("You already have a pending invite to " + other.getName().getString() + ".").formatted(Formatting.YELLOW));
            yield 0;
         }
         case SENT -> {
            String senderName = self.getName().getString();
            source.sendFeedback(
               () -> Text.literal("Sent alliance invite to " + other.getName().getString() + " (expires in 60s).").formatted(Formatting.AQUA), false
            );
            MutableText acceptBtn = Text.literal("[Accept]")
               .formatted(Formatting.GREEN, Formatting.BOLD)
               .styled(s -> s
                  .withClickEvent(new ClickEvent.RunCommand("/yp alliance accept " + senderName))
                  .withHoverEvent(new HoverEvent.ShowText(Text.literal("Accept alliance with " + senderName))));
            MutableText declineBtn = Text.literal("[Decline]")
               .formatted(Formatting.RED, Formatting.BOLD)
               .styled(s -> s
                  .withClickEvent(new ClickEvent.RunCommand("/yp alliance decline " + senderName))
                  .withHoverEvent(new HoverEvent.ShowText(Text.literal("Decline alliance invite"))));
            other.sendMessage(
               Text.literal(senderName + " invited you to an alliance! ")
                  .formatted(Formatting.GOLD)
                  .append(acceptBtn)
                  .append(Text.literal(" "))
                  .append(declineBtn)
            );
            yield 1;
         }
      };
   }

   private static int showHelp(ServerCommandSource source) {
      source.sendFeedback(() -> Text.literal("YP Powers — player commands").formatted(Formatting.GOLD, Formatting.BOLD), false);
      source.sendFeedback(() -> Text.literal("/yp guide — open Power Menu & HUD Settings (default key: U)"), false);
      source.sendFeedback(() -> Text.literal("/yp status — view your power, Energy, and current mode"), false);
      source.sendFeedback(() -> Text.literal("/yp power check|list — view your assigned power or list all powers"), false);
      source.sendFeedback(() -> Text.literal("/yp rituals — view shared ritual progress"), false);
      source.sendFeedback(() -> Text.literal("/yp alliance invite|accept|decline|remove|list — manage consent alliances"), false);
      source.sendFeedback(() -> Text.literal("/yp reset — check or restore your power gear"), false);
      if (source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS))) {
         source.sendFeedback(
            () -> Text.literal("Operator commands: /yp power assign|clear, /yp combat clear, /yp config reload, /yp test, /yp energy, /yp health, /yp artifact, /grace."),
            false
         );
      }

      return 1;
   }

   private static int showStatus(ServerCommandSource source, ServerPlayerEntity player) {
      PowerManager powers = PowerManager.get(source.getServer());
      EnergyManager energy = EnergyManager.get(source.getServer());
      PowerType assigned = powers.getAssignedPowerType(player.getUuid());
      String powerName = assigned == null ? "None" : assigned.getDisplayName();
      double currentEnergy = energy.getEnergy(player.getUuid());
      double maxEnergy = Math.max(1.0, YPConfig.getInstance().energy_max);
      int bleedingTicks = energy.getBleedingTicks(player.getUuid());
      source.sendFeedback(() -> Text.literal("Your YP Powers status").formatted(Formatting.GOLD, Formatting.BOLD), false);
      source.sendFeedback(
         () -> Text.literal("Power: " + powerName + (powers.isGrimoireSkillsetEnabled(player.getUuid()) ? " (Grimoire skillset active)" : ""))
            .formatted(Formatting.YELLOW),
         false
      );
      source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT, "Energy: %.1f / %.1f", currentEnergy, maxEnergy)).formatted(Formatting.AQUA), false);
      if (bleedingTicks > 0) {
         int seconds = (bleedingTicks + 19) / 20;
         source.sendFeedback(() -> Text.literal("Bleeding: " + seconds + "s remaining; abilities are disabled.").formatted(Formatting.RED), false);
      }

      if (TestMode.isEnabled(player)) {
         source.sendFeedback(() -> Text.literal("Test mode is active for this session.").formatted(Formatting.LIGHT_PURPLE), false);
      }

      return 1;
   }

   private static int setArtifactSpawning(ServerCommandSource source, String rawArtifact, boolean enabled) {
      String artifact = rawArtifact.toLowerCase(Locale.ROOT);
      YPConfig config = YPConfig.getInstance();
      PowerManager data = PowerManager.get(source.getServer());
      if (!artifact.equals("gauntlet") && !artifact.equals("grimoire")) {
         source.sendError(Text.literal("Unknown artifact: " + rawArtifact + ". Use gauntlet or grimoire."));
         return 0;
      } else if (enabled && artifact.equals("grimoire") && !data.isGauntletSiteGenerated() && !data.isGauntletSitePending()) {
         source.sendError(Text.literal("Enable Gauntlet spawning first; the Grimoire site is placed exactly 6,000 blocks from its site."));
         return 0;
      } else {
         if (artifact.equals("gauntlet")) {
            config.gauntlet_spawn_enabled = enabled;
         } else {
            config.grimoire_spawn_enabled = enabled;
         }

         config.save();
         if (enabled) {
            try {
               ArtifactSpawnManager.requestSpawn(source.getServer(), artifact);
            } catch (IllegalStateException var7) {
               source.sendError(Text.literal(var7.getMessage()));
               return 0;
            }
         }

         source.sendFeedback(
            () -> Text.literal(
                  (enabled ? "Enabled" : "Disabled")
                     + " future "
                     + (artifact.equals("gauntlet") ? "Gauntlet of Darkness" : "Grimoire of Light")
                     + " spawning. "
                     + (enabled ? "The site will be placed only when its unloaded chunk loads normally." : "Existing artifacts and structures are not removed.")
               )
               .formatted(enabled ? Formatting.GREEN : Formatting.YELLOW),
            true
         );
         return 1;
      }
   }

   private static int reportArtifactLocations(ServerCommandSource source) {
      PowerManager data = PowerManager.get(source.getServer());
      YPConfig config = YPConfig.getInstance();

      for (ServerPlayerEntity online : source.getServer().getPlayerManager().getPlayerList()) {
         boolean carriesGauntlet = false;
         boolean carriesGrimoire = false;

         for (int slot = 0; slot < online.getInventory().size(); slot++) {
            ItemStack stack = online.getInventory().getStack(slot);
            if (GauntletOfDarknessManager.isGauntlet(stack)) {
               carriesGauntlet = true;
            }

            if (GrimoireOfLightManager.isGrimoire(stack)) {
               carriesGrimoire = true;
            }
         }

         if (carriesGauntlet || carriesGrimoire) {
            BlockPos position = online.getBlockPos();
            String coordinate = online.getEntityWorld().getRegistryKey().getValue()
               + " at X="
               + position.getX()
               + ", Y="
               + position.getY()
               + ", Z="
               + position.getZ();
            String name = online.getName().getString();
            if (carriesGauntlet) {
               source.sendFeedback(() -> Text.literal("Current Gauntlet carrier: " + name + " — " + coordinate).formatted(Formatting.DARK_PURPLE), false);
            }

            if (carriesGrimoire) {
               source.sendFeedback(() -> Text.literal("Current Grimoire carrier: " + name + " — " + coordinate).formatted(Formatting.GOLD), false);
            }
         }
      }

      if (!data.isGauntletSiteGenerated() && !data.isGauntletSitePending()) {
         source.sendFeedback(
            () -> Text.literal("Gauntlet: no site scheduled/generated; spawning " + (config.gauntlet_spawn_enabled ? "enabled" : "disabled") + ".")
               .formatted(Formatting.YELLOW),
            false
         );
      } else {
         String state = data.isGauntletSiteGenerated() ? "generated" : "queued; waiting for the unloaded chunk to load";
         int y = data.isGauntletSiteGenerated() ? data.getGauntletSiteY() + 1 : -1;
         source.sendFeedback(
            () -> Text.literal(
                  "Gauntlet vault "
                     + state
                     + " at minecraft:overworld X="
                     + data.getGauntletSiteX()
                     + (y >= 0 ? ", Y=" + y : ", Y=unknown")
                     + ", Z="
                     + data.getGauntletSiteZ()
                     + " (generation "
                     + data.getGauntletGeneration()
                     + ", spawning "
                     + (config.gauntlet_spawn_enabled ? "enabled" : "disabled")
                     + ")."
               )
               .formatted(Formatting.DARK_PURPLE),
            false
         );
      }

      if (!data.isGrimoireSiteGenerated() && !data.isGrimoireSitePending()) {
         source.sendFeedback(
            () -> Text.literal(
                  "Grimoire: no site scheduled/generated; spawning "
                     + (config.grimoire_spawn_enabled ? "enabled" : "disabled")
                     + (!data.isGauntletSiteGenerated() && !data.isGauntletSitePending() ? "; schedule the Gauntlet first." : ".")
               )
               .formatted(Formatting.YELLOW),
            false
         );
      } else {
         String state = data.isGrimoireSiteGenerated() ? "generated" : "queued; waiting for the unloaded chunk to load";
         int y = data.isGrimoireSiteGenerated() ? data.getGrimoireSiteY() : -1;
         source.sendFeedback(
            () -> Text.literal(
                  "Grimoire chest "
                     + state
                     + " at minecraft:overworld X="
                     + data.getGrimoireSiteX()
                     + (y >= 0 ? ", Y=" + y : ", Y=unknown")
                     + ", Z="
                     + data.getGrimoireSiteZ()
                     + " (exactly 6,000 blocks from the Gauntlet; generation "
                     + data.getGrimoireGeneration()
                     + ", spawning "
                     + (config.grimoire_spawn_enabled ? "enabled" : "disabled")
                     + ")."
               )
               .formatted(Formatting.GOLD),
            false
         );
      }

      return 1;
   }

   private static int resetGear(ServerCommandSource source, ServerPlayerEntity player, boolean self) {
      PowerManager manager = PowerManager.get(source.getServer());
      PowerType type = manager.getAssignedPowerType(player.getUuid());
      String who = self ? "You" : player.getName().getString();
      if (type == null) {
         source.sendFeedback(() -> Text.literal(who + (self ? " do" : " does") + " not have a power assigned.").formatted(Formatting.YELLOW), false);
         return 0;
      } else {
         Power power = manager.getPowerInstance(type);
         List<String> restored = power == null ? List.of() : power.ensureSpecialGear(player);
         if (restored.isEmpty()) {
            source.sendFeedback(
               () -> Text.literal(who + (self ? " already have" : " already has") + " all " + type.getDisplayName() + " gear.").formatted(Formatting.GREEN),
               false
            );
            return 1;
         } else {
            String items = String.join(", ", restored);
            source.sendFeedback(
               () -> Text.literal("Restored " + type.getDisplayName() + " gear for " + (self ? "you" : player.getName().getString()) + ": " + items)
                  .formatted(Formatting.GOLD),
               false
            );
            if (!self) {
               player.sendMessage(Text.literal("An operator restored your " + type.getDisplayName() + " gear: " + items).formatted(Formatting.GOLD));
            }

            return restored.size();
         }
      }
   }

   private static int toggleTestMode(ServerCommandSource source, ServerPlayerEntity target, Boolean forced) {
      boolean enabled;
      if (forced == null) {
         enabled = TestMode.toggle(target);
      } else {
         TestMode.set(target, forced);
         enabled = forced;
      }

      String name = target.getName().getString();
      source.sendFeedback(
         () -> Text.literal(
               "Test mode "
                  + (enabled ? "ENABLED" : "disabled")
                  + " for "
                  + name
                  + (enabled ? " — cooldowns are cleared and Energy is refilled every tick (session only)." : ".")
            )
            .formatted(enabled ? Formatting.GREEN : Formatting.YELLOW),
         true
      );
      target.sendMessage(
         Text.literal("YP test mode " + (enabled ? "enabled" : "disabled") + (enabled ? " by an operator. Cooldowns are disabled for this session." : "."))
            .formatted(enabled ? Formatting.GREEN : Formatting.YELLOW),
         false
      );
      return enabled ? 1 : 0;
   }

   private static int showRitualStatus(ServerCommandSource source, ServerPlayerEntity player) {
      PowerManager manager = PowerManager.get(source.getServer());
      int complete = 0;
      int awakened = 0;

      for (PowerType type : PowerType.values()) {
         if (RitualManager.isComplete(manager, player.getUuid(), type)) {
            complete++;
         }

         if (RitualManager.isAwakeningUnlocked(manager, player.getUuid(), type)) {
            awakened++;
         }
      }

      int completedCount = complete;
      int awakenedCount = awakened;
      source.sendFeedback(
         () -> Text.literal(
               player.getName().getString()
                  + " — rituals completed: "
                  + completedCount
                  + "/"
                  + PowerType.values().length
                  + "; permanent Awakening unlocks: "
                  + awakenedCount
                  + "."
            )
            .formatted(Formatting.GOLD),
         false
      );

      for (PowerType type : PowerType.values()) {
         boolean done = RitualManager.isComplete(manager, player.getUuid(), type);
         boolean unlocked = RitualManager.isAwakeningUnlocked(manager, player.getUuid(), type);
         String status = done ? (unlocked ? "[Awakening unlocked] " : "[Ritual complete; carry Grimoire and press P] ") : "[Ritual incomplete] ";
         source.sendFeedback(
            () -> Text.literal(status + type.getDisplayName() + RitualManager.progressSummary(source.getServer(), manager, player.getUuid(), type)), false
         );
      }

      return 1;
   }
}
