package com.yppowers.client.render;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;
import net.minecraft.util.AssetInfo.SkinAssetInfo;
import net.minecraft.util.AssetInfo.TextureAsset;

public final class TimeCloneSkinManager {
   private static final Map<Identifier, Identifier> GOLD_TEXTURES = new ConcurrentHashMap<>();
   private static final Set<Identifier> LOADING = ConcurrentHashMap.newKeySet();
   private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();
   private static volatile int generation;

   private TimeCloneSkinManager() {
   }

   public static Identifier getTexture(UUID ownerId, boolean golden) {
      MinecraftClient client = MinecraftClient.getInstance();
      PlayerListEntry entry = client.getNetworkHandler() == null ? null : client.getNetworkHandler().getPlayerListEntry(ownerId);
      SkinTextures skin = entry == null ? null : entry.getSkinTextures();
      if (skin == null) {
         skin = DefaultSkinHelper.getSteve();
      }

      Identifier base = skin.body().texturePath();
      if (!golden) {
         return base;
      } else {
         Identifier tinted = GOLD_TEXTURES.get(base);
         if (tinted != null) {
            return tinted;
         } else {
            startGoldTextureLoad(client, skin.body());
            if (!base.equals(DefaultSkinHelper.getSteve().body().texturePath())) {
               Identifier steveBase = DefaultSkinHelper.getSteve().body().texturePath();
               Identifier steveGold = GOLD_TEXTURES.get(steveBase);
               if (steveGold != null) {
                  return steveGold;
               }

               startGoldTextureLoad(client, DefaultSkinHelper.getSteve().body());
            }

            return base;
         }
      }
   }

   private static void startGoldTextureLoad(MinecraftClient client, TextureAsset asset) {
      Identifier base = asset.texturePath();
      if (!GOLD_TEXTURES.containsKey(base) && !FAILED.contains(base) && LOADING.add(base)) {
         int requestGeneration = generation;
         String url = asset instanceof SkinAssetInfo skinAsset ? skinAsset.url() : null;
         if (url != null && !url.isBlank()) {
            CompletableFuture.runAsync(() -> {
               NativeImage image = null;

               try {
                  URLConnection connection = URI.create(url).toURL().openConnection();
                  connection.setConnectTimeout(4000);
                  connection.setReadTimeout(6000);

                  try (InputStream stream = connection.getInputStream()) {
                     image = NativeImage.read(stream);
                  }

                  if (image == null) {
                     throw new IllegalStateException("Skin image was empty");
                  }

                  tintGold(image);
                  NativeImage ready = image;
                  image = null;
                  client.execute(() -> registerGoldTexture(client, base, ready, requestGeneration));
               } catch (Exception var11) {
                  if (image != null) {
                     image.close();
                  }

                  if (requestGeneration == generation) {
                     LOADING.remove(base);
                     FAILED.add(base);
                  }
               }
            });
         } else {
            client.execute(() -> {
               if (requestGeneration != generation) {
                  LOADING.remove(base);
               } else {
                  try (InputStream resource = client.getResourceManager().getResource(base).orElseThrow().getInputStream()) {
                     registerGoldTexture(client, base, NativeImage.read(resource), requestGeneration);
                  } catch (Exception var8) {
                     LOADING.remove(base);
                     FAILED.add(base);
                  }
               }
            });
         }
      }
   }

   private static void registerGoldTexture(MinecraftClient client, Identifier base, NativeImage image, int requestGeneration) {
      if (requestGeneration != generation) {
         image.close();
         LOADING.remove(base);
      } else {
         Identifier goldId = Identifier.of("yppowers", "time_clone/gold/" + base.getNamespace() + "/" + base.getPath());
         NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "YP Time Clone Gold Skin", image);
         client.getTextureManager().registerTexture(goldId, texture);
         GOLD_TEXTURES.put(base, goldId);
         LOADING.remove(base);
      }
   }

   private static void tintGold(NativeImage image) {
      for (int y = 0; y < image.getHeight(); y++) {
         for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getColorArgb(x, y);
            int alpha = argb >>> 24 & 0xFF;
            if (alpha != 0) {
               int red = argb >>> 16 & 0xFF;
               int green = argb >>> 8 & 0xFF;
               int blue = argb & 0xFF;
               double luminance = (red * 0.299 + green * 0.587 + blue * 0.114) / 255.0;
               int goldRed = (int)Math.round(65.0 + 185.0 * luminance);
               int goldGreen = (int)Math.round(35.0 + 145.0 * luminance);
               int goldBlue = (int)Math.round(5.0 + 55.0 * luminance);
               image.setColorArgb(x, y, alpha << 24 | goldRed << 16 | goldGreen << 8 | goldBlue);
            }
         }
      }
   }

   public static void clear() {
      generation++;
      MinecraftClient client = MinecraftClient.getInstance();

      for (Identifier texture : GOLD_TEXTURES.values()) {
         client.getTextureManager().destroyTexture(texture);
      }

      GOLD_TEXTURES.clear();
      LOADING.clear();
      FAILED.clear();
   }
}
