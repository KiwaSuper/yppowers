package com.yppowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.yppowers.artifact.ArtifactSpawnManager;
import com.yppowers.artifact.GauntletOfDarknessManager;
import com.yppowers.artifact.GrimoireOfLightManager;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PowerRegistry;
import com.yppowers.energy.EnergyManager;
import com.yppowers.energy.GlobalHealthManager;
import com.yppowers.item.YPItems;
import com.yppowers.network.HudSyncService;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.progression.AwakeningManager;
import com.yppowers.system.CombatTagSystem;
import java.util.Locale;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/** Operator commands: /grace, combat, cooldown, energy, health, test, artifact, web_slingers, config reload. */
final class AdminCommands {
   private AdminCommands() {
   }

   static void registerGrace(CommandDispatcher<ServerCommandSource> dispatcher) {
      dispatcher.register(
         CommandManager.literal("grace")
            .requires(YPCommands::isOp)
            .then(
               CommandManager.argument("minutes", IntegerArgumentType.integer(0, 1789569))
                  .executes(ctx -> {
                     int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
                     MinecraftServer server = ctx.getSource().getServer();
                     PowerManager manager = PowerManager.get(server);
                     if (minutes == 0) {
                        boolean wasActive = manager.isGracePeriodActive();
                        manager.setGraceTicksRemaining(0);
                        HudSyncService.syncAll(server);
                        if (wasActive) {
                           server.getPlayerManager().broadcast(
                              Text.literal("Grace period ended by an administrator. Abilities and PvP are enabled again.").formatted(Formatting.GREEN),
                              false
                           );
                        }
                        ctx.getSource().sendFeedback(() -> Text.literal(wasActive ? "Grace period disabled." : "No grace period was active."), true);
                        return 1;
                     }
                     manager.setGraceTicksRemaining(Math.multiplyExact(minutes, 1200));
                     CombatTagSystem.clearAllAndSync(server);
                     HudSyncService.syncAll(server);
                     server.getPlayerManager().broadcast(
                        Text.literal("Grace period started for " + minutes + (minutes == 1 ? " minute" : " minutes") + ". Abilities and PvP are disabled.")
                           .formatted(Formatting.GOLD),
                        false
                     );
                     ctx.getSource().sendFeedback(() -> Text.literal("Grace period set to " + minutes + " minute(s)."), true);
                     return 1;
                  })
            )
      );
   }

   static void attach(LiteralArgumentBuilder<ServerCommandSource> yp) {
      yp.then(
         CommandManager.literal("combat")
            .requires(YPCommands::isOp)
            .then(
               CommandManager.literal("clear")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                           ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                           CombatTagSystem.clearTag(target.getUuid());
                           HudSyncService.sync(target);
                           ctx.getSource().sendFeedback(() -> Text.literal("Cleared combat tag for " + target.getName().getString() + ".").formatted(Formatting.GREEN), true);
                           return 1;
                        })
                  )
            )
      );

      yp.then(
         CommandManager.literal("cooldown")
            .requires(YPCommands::isOp)
            .then(
               CommandManager.literal("clear")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                           ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                           PowerManager pm = PowerManager.get(ctx.getSource().getServer());
                           for (Power power : PowerRegistry.all()) {
                              power.clearCooldowns(target.getUuid());
                           }
                           AwakeningManager.clearCooldowns(ctx.getSource().getServer(), target.getUuid());
                           EnergyManager.get(ctx.getSource().getServer()).clearCooldownsFor(target.getUuid());
                           pm.setTimeWarperFormCooldownTicks(target.getUuid(), 0);
                           pm.setTeleportGambitDomainCooldownTicks(target.getUuid(), 0);
                           HudSyncService.sync(target);
                           ctx.getSource().sendFeedback(() -> Text.literal("Cleared all cooldowns for " + target.getName().getString() + ".").formatted(Formatting.GREEN), true);
                           return 1;
                        })
                  )
            )
      );

      yp.then(
         CommandManager.literal("config")
            .requires(YPCommands::isOp)
            .then(
               CommandManager.literal("reload")
                  .executes(ctx -> {
                     YPConfig.getInstance().load();
                     MinecraftServer server = ctx.getSource().getServer();
                     GlobalHealthManager.applyAll(server);
                     HudSyncService.syncAll(server);
                     ctx.getSource().sendFeedback(() -> Text.literal("Reloaded config/yppowers_config.json.").formatted(Formatting.GREEN), true);
                     return 1;
                  })
            )
      );

      yp.then(
         CommandManager.literal("health")
            .requires(YPCommands::isOp)
            .then(CommandManager.literal("enable").executes(ctx -> {
               YPConfig config = YPConfig.getInstance();
               config.global_health_enabled = true;
               config.save();
               GlobalHealthManager.applyAll(ctx.getSource().getServer());
               ctx.getSource().sendFeedback(() -> Text.literal("Global player health enabled (target " + config.global_player_health_value + " HP). Mobs are unchanged.").formatted(Formatting.GREEN), true);
               return 1;
            }))
            .then(CommandManager.literal("disable").executes(ctx -> {
               YPConfig config = YPConfig.getInstance();
               config.global_health_enabled = false;
               config.save();
               GlobalHealthManager.applyAll(ctx.getSource().getServer());
               ctx.getSource().sendFeedback(() -> Text.literal("Global player health disabled. Mobs are unchanged.").formatted(Formatting.YELLOW), true);
               return 1;
            }))
            .then(CommandManager.literal("status").executes(ctx -> {
               YPConfig config = YPConfig.getInstance();
               ctx.getSource().sendFeedback(() -> Text.literal("Global player health: " + (config.global_health_enabled ? "enabled, " + config.global_player_health_value + " HP" : "disabled") + " (mobs unchanged).").formatted(Formatting.YELLOW), false);
               return 1;
            }))
      );

      yp.then(
         CommandManager.literal("energy")
            .requires(YPCommands::isOp)
            .then(
               CommandManager.argument("player", EntityArgumentType.player())
                  .then(
                     CommandManager.literal("set")
                        .then(
                           CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0, 100000.0))
                              .executes(ctx -> {
                                 ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                 double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                 EnergyManager mgr = EnergyManager.get(ctx.getSource().getServer());
                                 mgr.setEnergy(target.getUuid(), amount);
                                 mgr.sync(target);
                                 HudSyncService.sync(target);
                                 ctx.getSource().sendFeedback(() -> Text.literal("Set " + target.getName().getString() + " Energy to " + amount + ".").formatted(Formatting.GREEN), true);
                                 return 1;
                              })
                        )
                  )
            )
      );

      yp.then(
         CommandManager.literal("web_slingers")
            .requires(YPCommands::isOp)
            .then(
               CommandManager.literal("give")
                  .then(
                     CommandManager.argument("player", EntityArgumentType.player())
                        .executes(ctx -> {
                           ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                           ItemStack stack = new ItemStack(YPItems.WEB_SLINGERS);
                           stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Web Slingers").formatted(Formatting.AQUA, Formatting.BOLD));
                           if (!target.giveItemStack(stack)) target.dropItem(stack, false);
                           ctx.getSource().sendFeedback(() -> Text.literal("Gave Web Slingers to " + target.getName().getString()).formatted(Formatting.GREEN), true);
                           return 1;
                        })
                  )
            )
      );

      yp.then(
         CommandManager.literal("test")
            .requires(YPCommands::isOp)
            .then(
               CommandManager.argument("target", EntityArgumentType.player())
                  .executes(ctx -> toggleTestMode(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), null))
                  .then(CommandManager.literal("on").executes(ctx -> toggleTestMode(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), true)))
                  .then(CommandManager.literal("off").executes(ctx -> toggleTestMode(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "target"), false)))
            )
      );

      yp.then(
         CommandManager.literal("artifacts")
            .requires(YPCommands::isOp)
            .executes(ctx -> reportArtifactLocations(ctx.getSource()))
      );

      yp.then(
         CommandManager.literal("artifact")
            .requires(YPCommands::isOp)
            .then(CommandManager.literal("locate").executes(ctx -> reportArtifactLocations(ctx.getSource())))
            .then(
               CommandManager.literal("disable")
                  .then(
                     CommandManager.argument("artifact", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                           builder.suggest("gauntlet");
                           builder.suggest("grimoire");
                           return builder.buildFuture();
                        })
                        .executes(ctx -> setArtifactSpawning(ctx.getSource(), StringArgumentType.getString(ctx, "artifact"), false))
                  )
            )
            .then(
               CommandManager.literal("enable")
                  .then(
                     CommandManager.argument("artifact", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                           builder.suggest("gauntlet");
                           builder.suggest("grimoire");
                           return builder.buildFuture();
                        })
                        .executes(ctx -> setArtifactSpawning(ctx.getSource(), StringArgumentType.getString(ctx, "artifact"), true))
                  )
            )
      );
   }

   private static int toggleTestMode(ServerCommandSource source, ServerPlayerEntity target, Boolean forced) {
      boolean enabled = forced == null ? TestMode.toggle(target) : forced;
      if (forced != null) TestMode.set(target, forced);
      String name = target.getName().getString();
      source.sendFeedback(
         () -> Text.literal("Test mode " + (enabled ? "ENABLED" : "disabled") + " for " + name + (enabled ? " — cooldowns are cleared and Energy is refilled every tick (session only)." : "."))
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

   private static int setArtifactSpawning(ServerCommandSource source, String rawArtifact, boolean enabled) {
      String artifact = rawArtifact.toLowerCase(Locale.ROOT);
      YPConfig config = YPConfig.getInstance();
      PowerManager data = PowerManager.get(source.getServer());
      if (!artifact.equals("gauntlet") && !artifact.equals("grimoire")) {
         source.sendError(Text.literal("Unknown artifact: " + rawArtifact + ". Use gauntlet or grimoire."));
         return 0;
      }
      if (enabled && artifact.equals("grimoire") && !data.isGauntletSiteGenerated() && !data.isGauntletSitePending()) {
         source.sendError(Text.literal("Enable Gauntlet spawning first; the Grimoire site is placed exactly 6,000 blocks from its site."));
         return 0;
      }
      if (artifact.equals("gauntlet")) config.gauntlet_spawn_enabled = enabled;
      else config.grimoire_spawn_enabled = enabled;
      config.save();
      if (enabled) {
         try {
            ArtifactSpawnManager.requestSpawn(source.getServer(), artifact);
         } catch (IllegalStateException ex) {
            source.sendError(Text.literal(ex.getMessage()));
            return 0;
         }
      }
      source.sendFeedback(
         () -> Text.literal(
               (enabled ? "Enabled" : "Disabled") + " future " + (artifact.equals("gauntlet") ? "Gauntlet of Darkness" : "Grimoire of Light")
                  + " spawning. " + (enabled ? "The site will be placed only when its unloaded chunk loads normally." : "Existing artifacts and structures are not removed.")
            )
            .formatted(enabled ? Formatting.GREEN : Formatting.YELLOW),
         true
      );
      return 1;
   }

   private static int reportArtifactLocations(ServerCommandSource source) {
      PowerManager data = PowerManager.get(source.getServer());
      YPConfig config = YPConfig.getInstance();
      for (ServerPlayerEntity online : source.getServer().getPlayerManager().getPlayerList()) {
         boolean carriesGauntlet = false;
         boolean carriesGrimoire = false;
         for (int slot = 0; slot < online.getInventory().size(); slot++) {
            ItemStack stack = online.getInventory().getStack(slot);
            if (GauntletOfDarknessManager.isGauntlet(stack)) carriesGauntlet = true;
            if (GrimoireOfLightManager.isGrimoire(stack)) carriesGrimoire = true;
         }
         if (carriesGauntlet || carriesGrimoire) {
            BlockPos pos = online.getBlockPos();
            String coord = online.getEntityWorld().getRegistryKey().getValue() + " at X=" + pos.getX() + ", Y=" + pos.getY() + ", Z=" + pos.getZ();
            String name = online.getName().getString();
            if (carriesGauntlet) source.sendFeedback(() -> Text.literal("Current Gauntlet carrier: " + name + " — " + coord).formatted(Formatting.DARK_PURPLE), false);
            if (carriesGrimoire) source.sendFeedback(() -> Text.literal("Current Grimoire carrier: " + name + " — " + coord).formatted(Formatting.GOLD), false);
         }
      }
      if (!data.isGauntletSiteGenerated() && !data.isGauntletSitePending()) {
         source.sendFeedback(() -> Text.literal("Gauntlet: no site scheduled/generated; spawning " + (config.gauntlet_spawn_enabled ? "enabled" : "disabled") + ".").formatted(Formatting.YELLOW), false);
      } else {
         String state = data.isGauntletSiteGenerated() ? "generated" : "queued; waiting for the unloaded chunk to load";
         int y = data.isGauntletSiteGenerated() ? data.getGauntletSiteY() + 1 : -1;
         source.sendFeedback(
            () -> Text.literal("Gauntlet vault " + state + " at minecraft:overworld X=" + data.getGauntletSiteX() + (y >= 0 ? ", Y=" + y : ", Y=unknown")
               + ", Z=" + data.getGauntletSiteZ() + " (generation " + data.getGauntletGeneration() + ", spawning " + (config.gauntlet_spawn_enabled ? "enabled" : "disabled") + ").")
               .formatted(Formatting.DARK_PURPLE),
            false
         );
      }
      if (!data.isGrimoireSiteGenerated() && !data.isGrimoireSitePending()) {
         source.sendFeedback(
            () -> Text.literal("Grimoire: no site scheduled/generated; spawning " + (config.grimoire_spawn_enabled ? "enabled" : "disabled")
               + (!data.isGauntletSiteGenerated() && !data.isGauntletSitePending() ? "; schedule the Gauntlet first." : ".")).formatted(Formatting.YELLOW),
            false
         );
      } else {
         String state = data.isGrimoireSiteGenerated() ? "generated" : "queued; waiting for the unloaded chunk to load";
         int y = data.isGrimoireSiteGenerated() ? data.getGrimoireSiteY() : -1;
         source.sendFeedback(
            () -> Text.literal("Grimoire chest " + state + " at minecraft:overworld X=" + data.getGrimoireSiteX() + (y >= 0 ? ", Y=" + y : ", Y=unknown")
               + ", Z=" + data.getGrimoireSiteZ() + " (exactly 6,000 blocks from the Gauntlet; generation " + data.getGrimoireGeneration()
               + ", spawning " + (config.grimoire_spawn_enabled ? "enabled" : "disabled") + ").").formatted(Formatting.GOLD),
            false
         );
      }
      return 1;
   }
}
