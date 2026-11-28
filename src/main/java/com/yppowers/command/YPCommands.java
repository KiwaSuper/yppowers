package com.yppowers.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.permission.Permission.Level;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.command.ServerCommandSource;

/** Root command registrar delegating cleanly to Power, Alliance, and Admin command modules. */
public final class YPCommands {
   private YPCommands() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT.register(YPCommands::registerCommands);
   }

   static boolean isOp(ServerCommandSource source) {
      return source.getPermissions().hasPermission(new Level(PermissionLevel.GAMEMASTERS));
   }

   private static void registerCommands(
      CommandDispatcher<ServerCommandSource> dispatcher,
      CommandRegistryAccess registryAccess,
      RegistrationEnvironment environment
   ) {
      AdminCommands.registerGrace(dispatcher);

      LiteralArgumentBuilder<ServerCommandSource> yp = CommandManager.literal("yp");
      PowerCommands.attach(yp);
      AllianceCommands.attach(yp);
      AdminCommands.attach(yp);
      dispatcher.register(yp);
   }
}
