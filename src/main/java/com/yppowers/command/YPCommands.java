package com.yppowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Set;
import java.util.UUID;

public class YPCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register(YPCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("yp")
            .then(CommandManager.literal("power")
                .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                .then(CommandManager.literal("assign")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("power", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (PowerType type : PowerType.values()) {
                                    builder.suggest(type.getId());
                                }
                                return builder.buildFuture();
                            })
                            .executes(context -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                String powerId = StringArgumentType.getString(context, "power");
                                PowerType type = PowerType.fromId(powerId);
                                
                                if (type == null) {
                                    context.getSource().sendError(Text.literal("Unknown power: " + powerId).formatted(Formatting.RED));
                                    return 0;
                                }
                                
                                PowerManager.get(context.getSource().getServer()).assignPower(target, type);
                                context.getSource().sendFeedback(() -> Text.literal("Assigned power " + type.getDisplayName() + " to " + target.getName().getString()).formatted(Formatting.GREEN), true);
                                return 1;
                            })
                        )
                    )
                )
                .then(CommandManager.literal("clear")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                            PowerManager.get(context.getSource().getServer()).clearPower(target);
                            context.getSource().sendFeedback(() -> Text.literal("Cleared power for " + target.getName().getString()).formatted(Formatting.GREEN), true);
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("check")
                    .executes(context -> {
                        ServerPlayerEntity target = context.getSource().getPlayerOrThrow();
                        PowerType type = PowerManager.get(context.getSource().getServer()).getPowerType(target.getUuid());
                        String powerName = type != null ? type.getDisplayName() : "None";
                        context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + "'s Power: " + powerName).formatted(Formatting.YELLOW), false);
                        return 1;
                    })
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                            PowerType type = PowerManager.get(context.getSource().getServer()).getPowerType(target.getUuid());
                            String powerName = type != null ? type.getDisplayName() : "None";
                            context.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + "'s Power: " + powerName).formatted(Formatting.YELLOW), false);
                            return 1;
                        })
                    )
                )
            )
            .then(CommandManager.literal("alliance")
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerPlayerEntity sourcePlayer = context.getSource().getPlayerOrThrow();
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                            if (sourcePlayer == target) {
                                context.getSource().sendError(Text.literal("Cannot ally with yourself!").formatted(Formatting.RED));
                                return 0;
                            }
                            AllianceManager.get(context.getSource().getServer()).addAlliance(sourcePlayer.getUuid(), target.getUuid());
                            context.getSource().sendFeedback(() -> Text.literal("Allied with " + target.getName().getString()).formatted(Formatting.GREEN), false);
                            target.sendMessage(Text.literal(sourcePlayer.getName().getString() + " has allied with you!").formatted(Formatting.GREEN));
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            ServerPlayerEntity sourcePlayer = context.getSource().getPlayerOrThrow();
                            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                            AllianceManager.get(context.getSource().getServer()).removeAlliance(sourcePlayer.getUuid(), target.getUuid());
                            context.getSource().sendFeedback(() -> Text.literal("Removed alliance with " + target.getName().getString()).formatted(Formatting.GREEN), false);
                            target.sendMessage(Text.literal(sourcePlayer.getName().getString() + " has removed their alliance with you.").formatted(Formatting.RED));
                            return 1;
                        })
                    )
                )
                .then(CommandManager.literal("list")
                    .executes(context -> {
                        ServerPlayerEntity sourcePlayer = context.getSource().getPlayerOrThrow();
                        Set<UUID> allies = AllianceManager.get(context.getSource().getServer()).getAllies(sourcePlayer.getUuid());
                        if (allies.isEmpty()) {
                            context.getSource().sendFeedback(() -> Text.literal("You have no allies.").formatted(Formatting.YELLOW), false);
                        } else {
                            StringBuilder allyNames = new StringBuilder();
                            for (UUID allyId : allies) {
                                ServerPlayerEntity ally = context.getSource().getServer().getPlayerManager().getPlayer(allyId);
                                if (ally != null) {
                                    allyNames.append(ally.getName().getString()).append(", ");
                                }
                            }
                            context.getSource().sendFeedback(() -> Text.literal("Allies: " + allyNames.toString()).formatted(Formatting.GREEN), false);
                        }
                        return 1;
                    })
                )
            )
        );
    }
}
