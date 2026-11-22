package com.yppowers.client.render;

import com.yppowers.YPPowersMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Async player-skin and gold-tinted elite-skin cache for Time Warper Clones. */
@Environment(EnvType.CLIENT)
public final class TimeCloneSkinManager {
    private static final Map<Identifier, Identifier> GOLD_TEXTURES = new ConcurrentHashMap<>();
    private static final Set<Identifier> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<Identifier> FAILED = ConcurrentHashMap.newKeySet();
    private static volatile int generation;

    private TimeCloneSkinManager() {}

    public static Identifier getTexture(UUID ownerId, boolean golden) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerListEntry entry = client.getNetworkHandler() == null ? null
                : client.getNetworkHandler().getPlayerListEntry(ownerId);
        SkinTextures skin = entry == null ? null : entry.getSkinTextures();
        if (skin == null) skin = DefaultSkinHelper.getSteve();
        Identifier base = skin.body().texturePath();
        if (!golden) return base;

        Identifier tinted = GOLD_TEXTURES.get(base);
        if (tinted != null) return tinted;
        startGoldLoad(client, skin.body());
        return base;
    }

    private static void startGoldLoad(MinecraftClient client, AssetInfo.TextureAsset asset) {
        Identifier base = asset.texturePath();
        if (GOLD_TEXTURES.containsKey(base) || FAILED.contains(base) || !LOADING.add(base)) return;
        int gen = generation;
        String url = asset instanceof AssetInfo.SkinAssetInfo skinAsset ? skinAsset.url() : null;

        if (url == null || url.isBlank()) {
            client.execute(() -> {
                if (gen != generation) { LOADING.remove(base); return; }
                try (var stream = client.getResourceManager().getResource(base).orElseThrow().getInputStream()) {
                    NativeImage img = NativeImage.read(stream);
                    tintGold(img);
                    registerGold(client, base, img, gen);
                } catch (Exception ex) {
                    LOADING.remove(base);
                    FAILED.add(base);
                }
            });
            return;
        }

        CompletableFuture.runAsync(() -> {
            NativeImage image = null;
            try {
                URLConnection conn = URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(6000);
                try (InputStream stream = conn.getInputStream()) {
                    image = NativeImage.read(stream);
                }
                if (image == null) throw new IllegalStateException("Empty skin image");
                tintGold(image);
                NativeImage ready = image;
                image = null;
                client.execute(() -> registerGold(client, base, ready, gen));
            } catch (Exception ex) {
                if (image != null) image.close();
                if (gen == generation) {
                    LOADING.remove(base);
                    FAILED.add(base);
                }
            }
        });
    }

    private static void registerGold(MinecraftClient client, Identifier base, NativeImage image, int gen) {
        if (gen != generation) {
            image.close();
            LOADING.remove(base);
            return;
        }
        Identifier goldId = Identifier.of(YPPowersMod.MOD_ID, "time_clone/gold/" + base.getNamespace() + "/" + base.getPath());
        client.getTextureManager().registerTexture(goldId, new NativeImageBackedTexture(() -> "YP Time Clone Gold", image));
        GOLD_TEXTURES.put(base, goldId);
        LOADING.remove(base);
    }

    private static void tintGold(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getColorArgb(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) continue;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                double lum = (r * 0.299 + g * 0.587 + b * 0.114) / 255.0;
                int gr = (int) Math.round(65 + 185 * lum);
                int gg = (int) Math.round(35 + 145 * lum);
                int gb = (int) Math.round(5 + 55 * lum);
                image.setColorArgb(x, y, (alpha << 24) | (gr << 16) | (gg << 8) | gb);
            }
        }
    }

    public static void clear() {
        generation++;
        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier id : GOLD_TEXTURES.values()) client.getTextureManager().destroyTexture(id);
        GOLD_TEXTURES.clear();
        LOADING.clear();
        FAILED.clear();
    }
}
