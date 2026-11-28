package com.yppowers.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.system.CombatTagSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Consent-based alliance commands with combat-tag protection and offline name support. */
final class AllianceCommands {
   private AllianceCommands() {
   }

   static void attach(LiteralArgumentBuilder<ServerCommandSource> yp) {
      yp.then(
         CommandManager.literal("alliance")
            .then(
               CommandManager.literal("invite")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> sendInvite(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                  )
            )
            .then(
               CommandManager.literal("add")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> sendInvite(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                  )
            )
            .then(
               CommandManager.literal("accept")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                           ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                           if (blockInCombat(ctx.getSource(), self)) return 0;
                           ServerPlayerEntity sender = EntityArgumentType.getPlayer(ctx, "player");
                           MinecraftServer server = ctx.getSource().getServer();
                           AllianceManager alliances = AllianceManager.get(server);
                           if (!alliances.acceptInvite(self.getUuid(), sender.getUuid(), server.getTicks())) {
                              ctx.getSource().sendError(Text.literal("No pending alliance invite from " + sender.getName().getString() + ".").formatted(Formatting.RED));
                              return 0;
                           }
                           alliances.rememberName(self.getUuid(), self.getName().getString());
                           alliances.rememberName(sender.getUuid(), sender.getName().getString());
                           ctx.getSource().sendFeedback(() -> Text.literal("You are now allied with " + sender.getName().getString() + "!").formatted(Formatting.GREEN), false);
                           sender.sendMessage(Text.literal(self.getName().getString() + " accepted your alliance invite!").formatted(Formatting.GREEN));
                           return 1;
                        })
                  )
            )
            .then(
               CommandManager.literal("decline")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                           ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                           ServerPlayerEntity sender = EntityArgumentType.getPlayer(ctx, "player");
                           AllianceManager alliances = AllianceManager.get(ctx.getSource().getServer());
                           if (!alliances.declineInvite(self.getUuid(), sender.getUuid())) {
                              ctx.getSource().sendError(Text.literal("No pending alliance invite from " + sender.getName().getString() + ".").formatted(Formatting.RED));
                              return 0;
                           }
                           ctx.getSource().sendFeedback(() -> Text.literal("Declined alliance invite from " + sender.getName().getString() + ".").formatted(Formatting.YELLOW), false);
                           sender.sendMessage(Text.literal(self.getName().getString() + " declined your alliance invite.").formatted(Formatting.YELLOW));
                           return 1;
                        })
                  )
            )
            .then(
               CommandManager.literal("remove")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                           ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                           if (blockInCombat(ctx.getSource(), self)) return 0;
                           ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                           AllianceManager.get(ctx.getSource().getServer()).removeAlliance(self.getUuid(), target.getUuid());
                           ctx.getSource().sendFeedback(() -> Text.literal("Removed alliance with " + target.getName().getString()).formatted(Formatting.YELLOW), false);
                           target.sendMessage(Text.literal(self.getName().getString() + " removed their alliance with you.").formatted(Formatting.RED));
                           return 1;
                        })
                  )
            )
            .then(
               CommandManager.literal("list")
                  .executes(ctx -> {
                     ServerPlayerEntity self = ctx.getSource().getPlayerOrThrow();
                     MinecraftServer server = ctx.getSource().getServer();
                     AllianceManager alliances = AllianceManager.get(server);
                     Set<UUID> allies = alliances.getAllies(self.getUuid());
                     if (allies.isEmpty()) {
                        ctx.getSource().sendFeedback(() -> Text.literal("You have no allies.").formatted(Formatting.YELLOW), false);
                        return 1;
                     }
                     List<String> names = new ArrayList<>(allies.size());
                     for (UUID allyId : allies) {
                        boolean online = server.getPlayerManager().getPlayer(allyId) != null;
                        String display = alliances.getDisplayName(server, allyId);
                        names.add(online ? display : display + " (offline)");
                     }
                     ctx.getSource().sendFeedback(() -> Text.literal("Allies (" + names.size() + "): " + String.join(", ", names)).formatted(Formatting.GREEN), false);
                     return 1;
                  })
            )
      );
   }

   private static boolean blockInCombat(ServerCommandSource source, ServerPlayerEntity player) {
      YPConfig cfg = YPConfig.getInstance();
      if (cfg.combat_tag_enabled && cfg.combat_tag_block_alliance_changes && CombatTagSystem.isTagged(player.getUuid())) {
         source.sendError(
            Text.literal("You cannot change alliances while in PvP combat (" + CombatTagSystem.formatSeconds(CombatTagSystem.getRemainingTicks(player.getUuid())) + " remaining).")
               .formatted(Formatting.RED)
         );
         return true;
      }
      return false;
   }

   private static int sendInvite(ServerCommandSource source, ServerPlayerEntity other) throws CommandSyntaxException {
      ServerPlayerEntity self = source.getPlayerOrThrow();
      if (blockInCombat(source, self)) return 0;
      MinecraftServer server = source.getServer();
      AllianceManager alliances = AllianceManager.get(server);
      return switch (alliances.sendInvite(self.getUuid(), other.getUuid(), server.getTicks())) {
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
            source.sendFeedback(() -> Text.literal("Sent alliance invite to " + other.getName().getString() + " (expires in 60s).").formatted(Formatting.AQUA), false);
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
}
