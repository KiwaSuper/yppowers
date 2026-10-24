package com.yppowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.power.PowerManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.energy.GlobalHealthManager;
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
        dispatcher.register(CommandManager.literal("grace")
            .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
            .then(CommandManager.argument("minutes", IntegerArgumentType.integer(0, Integer.MAX_VALUE / 1200))
                .executes(context -> {
                    int minutes = IntegerArgumentType.getInteger(context, "minutes");
                    PowerManager manager = PowerManager.get(context.getSource().getServer());
                    if (minutes == 0) {
                        boolean wasActive = manager.isGracePeriodActive();
                        manager.setGraceTicksRemaining(0);
                        if (wasActive) {
                            context.getSource().getServer().getPlayerManager().broadcast(
                                    Text.literal("Grace period ended by an administrator. Abilities and PvP are enabled again.")
                                            .formatted(Formatting.GREEN), false);
                        }
                        context.getSource().sendFeedback(() -> Text.literal(wasActive
                                ? "Grace period disabled." : "No grace period was active."), true);
                        return 1;
                    }

                    int ticks = Math.multiplyExact(minutes, 1200);
                    manager.setGraceTicksRemaining(ticks);
                    context.getSource().getServer().getPlayerManager().broadcast(
                            Text.literal("Grace period started for " + minutes
                                    + (minutes == 1 ? " minute. Abilities and PvP are disabled." : " minutes. Abilities and PvP are disabled."))
                                    .formatted(Formatting.GOLD), false);
                    context.getSource().sendFeedback(() -> Text.literal("Grace period set to " + minutes + " minute(s)."), true);
                    return 1;
                })
            )
        );

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
            .then(CommandManager.literal("health")
                .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                .then(CommandManager.literal("enable").executes(context -> {
                    YPConfig config = YPConfig.getInstance();
                    config.global_health_enabled = true;
                    config.save();
                    GlobalHealthManager.applyAll(context.getSource().getServer());
                    context.getSource().sendFeedback(() -> Text.literal("Global player health enabled (target "
                            + config.global_player_health_value + " HP). Mobs are unchanged.").formatted(Formatting.GREEN), true);
                    return 1;
                }))
                .then(CommandManager.literal("disable").executes(context -> {
                    YPConfig config = YPConfig.getInstance();
                    config.global_health_enabled = false;
                    config.save();
                    GlobalHealthManager.applyAll(context.getSource().getServer());
                    context.getSource().sendFeedback(() -> Text.literal("Global player health disabled. Mobs are unchanged.").formatted(Formatting.YELLOW), true);
                    return 1;
                }))
                .then(CommandManager.literal("status").executes(context -> {
                    YPConfig config = YPConfig.getInstance();
                    context.getSource().sendFeedback(() -> Text.literal("Global player health: "
                            + (config.global_health_enabled ? "enabled, " + config.global_player_health_value + " HP" : "disabled")
                            + " (mobs unchanged).").formatted(Formatting.YELLOW), false);
                    return 1;
                }))
            )
            .then(CommandManager.literal("energy")
                .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0, 100000.0))
                            .executes(context -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
                                double amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount");
                                EnergyManager.get(context.getSource().getServer()).setEnergy(target.getUuid(), amount);
                                EnergyManager.get(context.getSource().getServer()).sync(target);
                                context.getSource().sendFeedback(() -> Text.literal("Set " + target.getName().getString()
                                        + " Energy to " + amount + ".").formatted(Formatting.GREEN), true);
                                return 1;
                            })
                        )
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
