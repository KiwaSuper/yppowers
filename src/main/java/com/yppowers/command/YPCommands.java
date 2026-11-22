package com.yppowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.PowerId;
import com.yppowers.item.YPItems;
import com.yppowers.network.YPNetworking;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.CombatTagSystem;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.EnergySystem;
import com.yppowers.system.GlobalHealthSystem;
import com.yppowers.system.GraceSystem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server commands for powers, alliances, grace, energy, health, cooldowns, and config reload. */
public final class YPCommands {
    private YPCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(YPCommands::registerCommands);
    }

    private static boolean isOp(ServerCommandSource source) {
        return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                         CommandRegistryAccess registryAccess,
                                         CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(CommandManager.literal("grace")
                .requires(YPCommands::isOp)
                .then(CommandManager.argument("minutes", IntegerArgumentType.integer(0, Integer.MAX_VALUE / 1200))
                        .executes(ctx -> {
                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                            MinecraftServer server = ctx.getSource().getServer();
                            GraceSystem grace = GraceSystem.get(server);
                            if (minutes == 0) {
                                boolean wasActive = grace.getTicksRemaining() > 0;
                                grace.setTicksRemaining(0);
                                if (wasActive) {
                                    server.getPlayerManager().broadcast(
                                            Text.literal("Grace period ended by an administrator. Abilities and PvP are enabled again.")
                                                    .formatted(Formatting.GREEN), false);
                                }
                                ctx.getSource().sendFeedback(() -> Text.literal(wasActive
                                        ? "Grace period disabled." : "No grace period was active."), true);
                                return 1;
                            }
                            grace.setTicksRemaining(Math.multiplyExact(minutes, 1200));
                            CombatTagSystem.clearAll();
                            server.getPlayerManager().broadcast(
                                    Text.literal("Grace period started for " + minutes
                                            + (minutes == 1 ? " minute" : " minutes")
                                            + ". Abilities and PvP are disabled.").formatted(Formatting.GOLD), false);
                            ctx.getSource().sendFeedback(() -> Text.literal("Grace period set to " + minutes + " minute(s)."), true);
                            return 1;
                        })
                )
        );

        dispatcher.register(CommandManager.literal("yp")
                .then(CommandManager.literal("guide")
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            YPNetworking.sendOpenGuide(player);
                            return 1;
                        })
                )
                .then(CommandManager.literal("power")
                        .requires(YPCommands::isOp)
                        .then(CommandManager.literal("assign")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .then(CommandManager.argument("power", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (PowerId id : PowerId.values()) builder.suggest(id.id());
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                    String raw = StringArgumentType.getString(ctx, "power");
                                                    PowerId id = PowerId.fromId(raw);
                                                    if (id == null) {
                                                        ctx.getSource().sendError(Text.literal("Unknown power: " + raw).formatted(Formatting.RED));
                                                        return 0;
                                                    }
                                                    PlayerDataStore.get(ctx.getSource().getServer()).assignPower(target, id);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Assigned " + id.displayName()
                                                            + " to " + target.getName().getString()).formatted(Formatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            PlayerDataStore.get(ctx.getSource().getServer()).clearPower(target);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Cleared power for "
                                                    + target.getName().getString()).formatted(Formatting.GREEN), true);
                                            return 1;
                                        })
                                )
                        )
                        .then(CommandManager.literal("check")
                                .executes(ctx -> {
                                    ServerPlayerEntity target = ctx.getSource().getPlayerOrThrow();
                                    return reportPower(ctx.getSource(), target);
                                })
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> reportPower(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                                )
                        )
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    List<String> names = new ArrayList<>();
                                    for (PowerId id : PowerId.values()) names.add(id.id() + " (" + id.displayName() + ")");
                                    ctx.getSource().sendFeedback(() -> Text.literal("Available powers (" + names.size() + "): "
                                            + String.join(", ", names)).formatted(Formatting.AQUA), false);
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("alliance")
                        .then(CommandManager.literal("invite")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> sendAllianceInvite(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                                )
                        )
                        .then(CommandManager.literal("add")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> sendAllianceInvite(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                                )
                        )
                        .then(CommandManager.literal("accept")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                                            if (blockAllianceInCombat(ctx.getSource(), self)) return 0;
                                            ServerPlayerEntity sender = EntityArgumentType.getPlayer(ctx, "player");
                                            MinecraftServer server = ctx.getSource().getServer();
                                            AllianceSystem alliances = AllianceSystem.get(server);
                                            if (!alliances.acceptInvite(self.getUuid(), sender.getUuid(), server.getTicks())) {
                                                ctx.getSource().sendError(Text.literal("No pending alliance invite from "
                                                        + sender.getName().getString() + ".").formatted(Formatting.RED));
                                                return 0;
                                            }
                                            alliances.rememberName(self.getUuid(), self.getName().getString());
                                            alliances.rememberName(sender.getUuid(), sender.getName().getString());
                                            ctx.getSource().sendFeedback(() -> Text.literal("You are now allied with "
                                                    + sender.getName().getString() + "!").formatted(Formatting.GREEN), false);
                                            sender.sendMessage(Text.literal(self.getName().getString()
                                                    + " accepted your alliance invite!").formatted(Formatting.GREEN));
                                            return 1;
                                        })
                                )
                        )
                        .then(CommandManager.literal("decline")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                                            ServerPlayerEntity sender = EntityArgumentType.getPlayer(ctx, "player");
                                            AllianceSystem alliances = AllianceSystem.get(ctx.getSource().getServer());
                                            if (!alliances.declineInvite(self.getUuid(), sender.getUuid())) {
                                                ctx.getSource().sendError(Text.literal("No pending alliance invite from "
                                                        + sender.getName().getString() + ".").formatted(Formatting.RED));
                                                return 0;
                                            }
                                            ctx.getSource().sendFeedback(() -> Text.literal("Declined alliance invite from "
                                                    + sender.getName().getString() + ".").formatted(Formatting.YELLOW), false);
                                            sender.sendMessage(Text.literal(self.getName().getString()
                                                    + " declined your alliance invite.").formatted(Formatting.YELLOW));
                                            return 1;
                                        })
                                )
                        )
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                                            if (blockAllianceInCombat(ctx.getSource(), self)) return 0;
                                            ServerPlayerEntity other = EntityArgumentType.getPlayer(ctx, "player");
                                            AllianceSystem.get(ctx.getSource().getServer()).removeAlliance(self.getUuid(), other.getUuid());
                                            ctx.getSource().sendFeedback(() -> Text.literal("Removed alliance with " + other.getName().getString())
                                                    .formatted(Formatting.YELLOW), false);
                                            other.sendMessage(Text.literal(self.getName().getString() + " removed their alliance with you.")
                                                    .formatted(Formatting.RED));
                                            return 1;
                                        })
                                )
                        )
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                                    MinecraftServer server = ctx.getSource().getServer();
                                    AllianceSystem alliances = AllianceSystem.get(server);
                                    Set<UUID> allyIds = alliances.getAllies(self.getUuid());
                                    if (allyIds.isEmpty()) {
                                        ctx.getSource().sendFeedback(() -> Text.literal("You have no allies.").formatted(Formatting.YELLOW), false);
                                        return 1;
                                    }
                                    List<String> names = new ArrayList<>(allyIds.size());
                                    for (UUID id : allyIds) {
                                        boolean online = server.getPlayerManager().getPlayer(id) != null;
                                        String display = alliances.getDisplayName(server, id);
                                        names.add(online ? display : display + " (offline)");
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.literal("Allies (" + names.size() + "): "
                                            + String.join(", ", names)).formatted(Formatting.GREEN), false);
                                    return 1;
                                })
                        )
                )
                .then(CommandManager.literal("energy")
                        .requires(YPCommands::isOp)
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0, 100000.0))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                                    double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                                    EnergySystem energy = EnergySystem.get(ctx.getSource().getServer());
                                                    energy.setEnergy(target.getUuid(), amount);
                                                    energy.clearBleeding(target.getUuid());
                                                    YPNetworking.syncHud(target);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Set " + target.getName().getString()
                                                            + "'s Energy to " + amount + ".").formatted(Formatting.GREEN), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(CommandManager.literal("get")
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            double val = EnergySystem.get(ctx.getSource().getServer()).getEnergy(target.getUuid());
                                            ctx.getSource().sendFeedback(() -> Text.literal(target.getName().getString()
                                                    + " Energy: " + String.format("%.1f / %.1f", val, YPConfig.get().energy.maxEnergy))
                                                    .formatted(Formatting.AQUA), false);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("combat")
                        .requires(YPCommands::isOp)
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            CombatTagSystem.clearTag(target.getUuid());
                                            YPNetworking.syncHud(target);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Cleared combat tag for "
                                                    + target.getName().getString() + ".").formatted(Formatting.GREEN), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("cooldown")
                        .requires(YPCommands::isOp)
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            CooldownSystem.get(ctx.getSource().getServer()).clearAll(target.getUuid());
                                            YPNetworking.syncHud(target);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Cleared all cooldowns for "
                                                    + target.getName().getString() + ".").formatted(Formatting.GREEN), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(CommandManager.literal("health")
                        .requires(YPCommands::isOp)
                        .then(CommandManager.literal("enable").executes(ctx -> {
                            YPConfig.get().general.globalHealthEnabled = true;
                            YPConfig.save();
                            GlobalHealthSystem.applyAll(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("Global player health enabled ("
                                    + YPConfig.get().general.globalPlayerHealth + " HP).").formatted(Formatting.GREEN), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("disable").executes(ctx -> {
                            YPConfig.get().general.globalHealthEnabled = false;
                            YPConfig.save();
                            GlobalHealthSystem.applyAll(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("Global player health disabled.").formatted(Formatting.YELLOW), true);
                            return 1;
                        }))
                        .then(CommandManager.literal("status").executes(ctx -> {
                            YPConfig cfg = YPConfig.get();
                            ctx.getSource().sendFeedback(() -> Text.literal("Global player health: "
                                    + (cfg.general.globalHealthEnabled ? "enabled (" + cfg.general.globalPlayerHealth + " HP)" : "disabled"))
                                    .formatted(Formatting.YELLOW), false);
                            return 1;
                        }))
                )
                .then(CommandManager.literal("config")
                        .requires(YPCommands::isOp)
                        .then(CommandManager.literal("reload").executes(ctx -> {
                            YPConfig.load();
                            GlobalHealthSystem.applyAll(ctx.getSource().getServer());
                            for (ServerPlayerEntity p : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                                YPNetworking.syncHud(p);
                            }
                            ctx.getSource().sendFeedback(() -> Text.literal("Reloaded config/yppowers.json.")
                                    .formatted(Formatting.GREEN), true);
                            return 1;
                        }))
                )
                .then(CommandManager.literal("web_slingers")
                        .requires(YPCommands::isOp)
                        .then(CommandManager.literal("give")
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            ItemStack stack = new ItemStack(YPItems.WEB_SLINGERS);
                                            stack.set(DataComponentTypes.CUSTOM_NAME,
                                                    Text.literal("Web Slingers").formatted(Formatting.AQUA, Formatting.BOLD));
                                            if (!target.giveItemStack(stack)) target.dropItem(stack, false);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Gave Web Slingers to "
                                                    + target.getName().getString()).formatted(Formatting.GREEN), true);
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }

    private static boolean blockAllianceInCombat(ServerCommandSource source, ServerPlayerEntity player) {
        if (YPConfig.get().combat.enabled && YPConfig.get().combat.blockAllianceChangesInCombat
                && CombatTagSystem.isTagged(player.getUuid())) {
            source.sendError(Text.literal("You cannot change alliances while in PvP combat ("
                    + CooldownSystem.formatSeconds(CombatTagSystem.getRemainingTicks(player.getUuid())) + " remaining).")
                    .formatted(Formatting.RED));
            return true;
        }
        return false;
    }

    private static int sendAllianceInvite(ServerCommandSource source, ServerPlayerEntity other)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity self = source.getPlayerOrThrow();
        if (blockAllianceInCombat(source, self)) return 0;
        MinecraftServer server = source.getServer();
        AllianceSystem alliances = AllianceSystem.get(server);
        AllianceSystem.InviteResult result = alliances.sendInvite(self.getUuid(), other.getUuid(), server.getTicks());
        switch (result) {
            case SELF_INVITE -> {
                source.sendError(Text.literal("You cannot ally with yourself.").formatted(Formatting.RED));
                return 0;
            }
            case ALREADY_ALLIED -> {
                source.sendError(Text.literal("You are already allied with " + other.getName().getString() + ".").formatted(Formatting.YELLOW));
                return 0;
            }
            case ALREADY_PENDING -> {
                source.sendError(Text.literal("You already have a pending invite to " + other.getName().getString() + ".").formatted(Formatting.YELLOW));
                return 0;
            }
            case SENT -> {
                String senderName = self.getName().getString();
                source.sendFeedback(() -> Text.literal("Sent alliance invite to " + other.getName().getString()
                        + " (expires in 60s).").formatted(Formatting.AQUA), false);

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

                other.sendMessage(Text.literal(senderName + " invited you to an alliance! ")
                        .formatted(Formatting.GOLD)
                        .append(acceptBtn)
                        .append(Text.literal(" "))
                        .append(declineBtn));
                return 1;
            }
        }
        return 0;
    }

    private static int reportPower(ServerCommandSource source, ServerPlayerEntity target) {
        PowerId id = PlayerDataStore.get(source.getServer()).getPowerId(target.getUuid());
        String display = id != null ? id.displayName() + " (" + id.id() + ")" : "None";
        source.sendFeedback(() -> Text.literal(target.getName().getString() + "'s Power: " + display)
                .formatted(Formatting.YELLOW), false);
        return 1;
    }
}
