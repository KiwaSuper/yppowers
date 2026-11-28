package com.yppowers.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.network.HudSyncService;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.progression.RitualManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Player & operator power, ritual, status, help, and guide commands. */
final class PowerCommands {
   private PowerCommands() {
   }

   static void attach(LiteralArgumentBuilder<ServerCommandSource> yp) {
      yp.then(CommandManager.literal("help").executes(ctx -> showHelp(ctx.getSource())));
      yp.then(CommandManager.literal("guide").executes(ctx -> {
         YPNetworking.sendOpenPowerMenu(ctx.getSource().getPlayerOrThrow());
         return 1;
      }));
      yp.then(CommandManager.literal("status").executes(ctx -> showStatus(ctx.getSource(), ctx.getSource().getPlayerOrThrow())));

      yp.then(
         CommandManager.literal("power")
            .then(
               CommandManager.literal("assign")
                  .requires(YPCommands::isOp)
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .then(
                           CommandManager.argument("power", StringArgumentType.word())
                              .suggests((ctx, builder) -> {
                                 for (PowerType type : PowerType.values()) {
                                    builder.suggest(type.getId());
                                 }
                                 return builder.buildFuture();
                              })
                              .executes(ctx -> {
                                 ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                 String powerId = StringArgumentType.getString(ctx, "power");
                                 PowerType type = PowerType.fromId(powerId);
                                 if (type == null) {
                                    ctx.getSource().sendError(Text.literal("Unknown power: " + powerId).formatted(Formatting.RED));
                                    return 0;
                                 }
                                 PowerManager.get(ctx.getSource().getServer()).assignPower(target, type);
                                 HudSyncService.sync(target);
                                 ctx.getSource().sendFeedback(
                                    () -> Text.literal("Assigned power " + type.getDisplayName() + " to " + target.getName().getString()).formatted(Formatting.GREEN),
                                    true
                                 );
                                 return 1;
                              })
                        )
                  )
            )
            .then(
               CommandManager.literal("clear")
                  .requires(YPCommands::isOp)
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                           ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                           PowerManager.get(ctx.getSource().getServer()).clearPower(target);
                           HudSyncService.sync(target);
                           ctx.getSource().sendFeedback(
                              () -> Text.literal("Cleared power for " + target.getName().getString()).formatted(Formatting.GREEN),
                              true
                           );
                           return 1;
                        })
                  )
            )
            .then(
               CommandManager.literal("list")
                  .executes(ctx -> {
                     List<String> names = new ArrayList<>();
                     for (PowerType pt : PowerType.values()) {
                        names.add(pt.getId() + " (" + pt.getDisplayName() + ")");
                     }
                     ctx.getSource().sendFeedback(
                        () -> Text.literal("Available powers (" + names.size() + "): " + String.join(", ", names)).formatted(Formatting.AQUA),
                        false
                     );
                     return 1;
                  })
            )
            .then(
               CommandManager.literal("check")
                  .executes(ctx -> checkPower(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .requires(YPCommands::isOp)
                        .executes(ctx -> checkPower(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
                  )
            )
      );

      yp.then(
         CommandManager.literal("reset")
            .executes(ctx -> resetGear(ctx.getSource(), ctx.getSource().getPlayerOrThrow(), true))
            .then(
               CommandManager.argument("player", EntityArgumentType.player())
                  .requires(YPCommands::isOp)
                  .executes(ctx -> resetGear(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), false))
            )
      );

      yp.then(
         CommandManager.literal("rituals")
            .executes(ctx -> showRitualStatus(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
            .then(
               CommandManager.argument("player", EntityArgumentType.player())
                  .requires(YPCommands::isOp)
                  .executes(ctx -> showRitualStatus(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))
            )
      );
   }

   private static int checkPower(ServerCommandSource source, ServerPlayerEntity target) {
      PowerType type = PowerManager.get(source.getServer()).getAssignedPowerType(target.getUuid());
      String powerName = type != null ? type.getDisplayName() : "None";
      source.sendFeedback(() -> Text.literal(target.getName().getString() + "'s Power: " + powerName).formatted(Formatting.YELLOW), false);
      return 1;
   }

   private static int showHelp(ServerCommandSource source) {
      source.sendFeedback(() -> Text.literal("YP Powers — player commands").formatted(Formatting.GOLD, Formatting.BOLD), false);
      source.sendFeedback(() -> Text.literal("/yp guide — open Power Menu, 17-Power Guide & HUD Settings (default key: U)"), false);
      source.sendFeedback(() -> Text.literal("/yp status — view your power, Energy, and current mode"), false);
      source.sendFeedback(() -> Text.literal("/yp power check|list — view your assigned power or list all powers"), false);
      source.sendFeedback(() -> Text.literal("/yp rituals — view shared ritual progress"), false);
      source.sendFeedback(() -> Text.literal("/yp alliance invite|accept|decline|remove|list — manage consent alliances"), false);
      source.sendFeedback(() -> Text.literal("/yp reset — check or restore your power gear"), false);
      if (YPCommands.isOp(source)) {
         source.sendFeedback(
            () -> Text.literal("Operator commands: /yp power assign|clear, /yp cooldown clear, /yp combat clear, /yp config reload, /yp test, /yp energy, /yp health, /yp artifact, /grace."),
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

   private static int resetGear(ServerCommandSource source, ServerPlayerEntity player, boolean self) {
      PowerManager manager = PowerManager.get(source.getServer());
      PowerType type = manager.getAssignedPowerType(player.getUuid());
      String who = self ? "You" : player.getName().getString();
      if (type == null) {
         source.sendFeedback(() -> Text.literal(who + (self ? " do" : " does") + " not have a power assigned.").formatted(Formatting.YELLOW), false);
         return 0;
      }
      Power power = manager.getPowerInstance(type);
      List<String> restored = power == null ? List.of() : power.ensureSpecialGear(player);
      if (restored.isEmpty()) {
         source.sendFeedback(
            () -> Text.literal(who + (self ? " already have" : " already has") + " all " + type.getDisplayName() + " gear.").formatted(Formatting.GREEN),
            false
         );
         return 1;
      }
      String items = String.join(", ", restored);
      source.sendFeedback(
         () -> Text.literal("Restored " + type.getDisplayName() + " gear for " + (self ? "you" : player.getName().getString()) + ": " + items).formatted(Formatting.GOLD),
         false
      );
      if (!self) {
         player.sendMessage(Text.literal("An operator restored your " + type.getDisplayName() + " gear: " + items).formatted(Formatting.GOLD));
      }
      return restored.size();
   }

   private static int showRitualStatus(ServerCommandSource source, ServerPlayerEntity player) {
      PowerManager manager = PowerManager.get(source.getServer());
      int complete = 0;
      int awakened = 0;
      for (PowerType type : PowerType.values()) {
         if (RitualManager.isComplete(manager, player.getUuid(), type)) complete++;
         if (RitualManager.isAwakeningUnlocked(manager, player.getUuid(), type)) awakened++;
      }
      int completedCount = complete;
      int awakenedCount = awakened;
      source.sendFeedback(
         () -> Text.literal(
               player.getName().getString()
                  + " — rituals completed: " + completedCount + "/" + PowerType.values().length
                  + "; permanent Awakening unlocks: " + awakenedCount + "."
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
