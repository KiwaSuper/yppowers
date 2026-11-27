package com.yppowers.client.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.entity.EnchantingTableBlockEntityRenderer;
import net.minecraft.client.render.entity.model.BookModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.BookModel.BookModelState;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class GrimoireBookVisual {
   private static final int MAX_VISUAL_LIFETIME_TICKS = 40;
   private static final Map<Integer, GrimoireBookVisual.BookEffect> ACTIVE_BOOKS = new HashMap<>();
   private static BookModel model;

   private GrimoireBookVisual() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(GrimoireBookVisual::render);
   }

   public static void setActive(MinecraftClient client, int entityId, boolean active, int durationTicks) {
      if (client.player != null && client.world != null) {
         if (!active) {
            ACTIVE_BOOKS.remove(entityId);
         } else {
            int lifetime = MathHelper.clamp(durationTicks, 1, 40);
            ACTIVE_BOOKS.put(entityId, new GrimoireBookVisual.BookEffect(client.world, client.player.age + lifetime));
         }
      }
   }

   public static void clear() {
      ACTIVE_BOOKS.clear();
      model = null;
   }

   private static void render(WorldRenderContext context) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.world != null && client.player != null && !ACTIVE_BOOKS.isEmpty()) {
         int now = client.player.age;
         Iterator<Entry<Integer, GrimoireBookVisual.BookEffect>> iterator = ACTIVE_BOOKS.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<Integer, GrimoireBookVisual.BookEffect> entry = iterator.next();
            GrimoireBookVisual.BookEffect effect = entry.getValue();
            if (effect.world == client.world && now <= effect.expiresAtTick) {
               if (client.world.getEntityById(entry.getKey()) instanceof PlayerEntity wielder && wielder.isAlive()) {
                  Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
                  if (!(wielder.squaredDistanceTo(camera.x, camera.y, camera.z) > 4096.0)) {
                     renderBook(client, context, wielder, camera);
                  }
               }
            } else {
               iterator.remove();
            }
         }
      }
   }

   private static void renderBook(MinecraftClient client, WorldRenderContext context, PlayerEntity wielder, Vec3d camera) {
      if (model == null) {
         model = new BookModel(client.getLoadedEntityModels().getModelPart(EntityModelLayers.BOOK));
      }

      float age = wielder.age;
      Vec3d forward = wielder.getRotationVec(1.0F).normalize();
      Vec3d bookPosition = wielder.getLerpedPos(1.0F)
         .add(0.0, wielder.getStandingEyeHeight() * 0.78, 0.0)
         .add(forward.multiply(0.82))
         .add(0.0, Math.sin(age * 0.12) * 0.035, 0.0);
      MatrixStack matrices = context.matrices();
      if (matrices != null) {
         matrices.push();
         matrices.translate(bookPosition.x - camera.x, bookPosition.y - camera.y, bookPosition.z - camera.z);
         Vector3f facingTheWielder = new Vector3f((float)(-forward.x), (float)(-forward.y), (float)(-forward.z)).normalize();
         matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, 1.0F), facingTheWielder));
         matrices.scale(0.62F, 0.62F, 0.62F);
         float pageAngle = age * 0.035F;
         float leftFlip = MathHelper.clamp(MathHelper.fractionalPart(pageAngle + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
         float rightFlip = MathHelper.clamp(MathHelper.fractionalPart(pageAngle + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
         BookModelState animation = new BookModelState(age, leftFlip, rightFlip, 1.0F);
         SpriteIdentifier bookTexture = EnchantingTableBlockEntityRenderer.BOOK_TEXTURE;
         context.commandQueue()
            .submitModel(
               model,
               animation,
               matrices,
               bookTexture.getRenderLayer(RenderLayers::entitySolid),
               15728880,
               OverlayTexture.DEFAULT_UV,
               -1,
               client.getAtlasManager().getSprite(bookTexture),
               0,
               null
            );
         matrices.pop();
      }
   }

   private record BookEffect(ClientWorld world, int expiresAtTick) {
   }
}
