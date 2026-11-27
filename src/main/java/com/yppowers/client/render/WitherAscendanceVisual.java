package com.yppowers.client.render;

import com.yppowers.client.ClientPowerData;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.state.WitherEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.WitherEntity;

@Environment(EnvType.CLIENT)
public final class WitherAscendanceVisual {
   private static ClientWorld cachedWorld;
   private static final Map<Integer, WitherEntity> DISGUISES = new HashMap<>();

   private WitherAscendanceVisual() {
   }

   public static boolean renderAsWither(
      EntityRenderManager renderer,
      EntityRenderState originalState,
      CameraRenderState cameraState,
      double x,
      double y,
      double z,
      MatrixStack matrices,
      OrderedRenderCommandQueue queue
   ) {
      if (originalState instanceof PlayerEntityRenderState playerState && ClientPowerData.witherAscendantEntityIds.contains(playerState.id)) {
         MinecraftClient client = MinecraftClient.getInstance();
         ClientWorld world = client.world;
         if (world == null) {
            return false;
         } else {
            if (cachedWorld != world) {
               DISGUISES.clear();
               cachedWorld = world;
            }

            if (world.getEntityById(playerState.id) instanceof AbstractClientPlayerEntity player) {
               WitherEntity wither = DISGUISES.computeIfAbsent(playerState.id, ignored -> EntityType.WITHER.create(world, SpawnReason.LOAD));
               if (wither == null) {
                  return false;
               } else {
                  wither.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getBodyYaw(), player.getPitch());
                  wither.setHeadYaw(player.getHeadYaw());
                  if (renderer.getAndUpdateRenderState(wither, 1.0F) instanceof WitherEntityRenderState witherState) {
                     witherState.bodyYaw = playerState.bodyYaw;
                     witherState.relativeHeadYaw = playerState.relativeHeadYaw;
                     witherState.pitch = playerState.pitch;
                     witherState.limbSwingAnimationProgress = playerState.limbSwingAnimationProgress;
                     witherState.limbSwingAmplitude = playerState.limbSwingAmplitude;
                     witherState.age = playerState.age;
                     witherState.hurt = playerState.hurt;
                     witherState.invisible = playerState.invisible;
                     witherState.sneaking = playerState.sneaking;
                     witherState.onFire = playerState.onFire;
                     witherState.light = playerState.light;
                     witherState.squaredDistanceToCamera = playerState.squaredDistanceToCamera;
                     renderer.render(witherState, cameraState, x, y, z, matrices, queue);
                     return true;
                  } else {
                     return false;
                  }
               }
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static void clear() {
      DISGUISES.clear();
      cachedWorld = null;
   }

   public static void clear(int playerEntityId) {
      DISGUISES.remove(playerEntityId);
   }
}
