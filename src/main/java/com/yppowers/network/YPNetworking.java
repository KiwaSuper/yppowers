package com.yppowers.network;

import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.ShadowArcherPower;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Custom-payload networking for Minecraft 1.21.11. */
public final class YPNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Networking");
    public record KeybindPayload(int keyType) implements CustomPayload {
        public static final Id<KeybindPayload> ID = new Id<>(Identifier.of("yppowers", "keybind"));
        public static final PacketCodec<RegistryByteBuf, KeybindPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeVarInt(payload.keyType()),
                buf -> new KeybindPayload(buf.readVarInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record MorphRequestPayload(String requested) implements CustomPayload {
        public static final Id<MorphRequestPayload> ID = new Id<>(Identifier.of("yppowers", "morph_request"));
        public static final PacketCodec<RegistryByteBuf, MorphRequestPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeString(payload.requested(), 128),
                buf -> new MorphRequestPayload(buf.readString(128)));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncFireGodPayload(boolean enabled) implements CustomPayload {
        public static final Id<SyncFireGodPayload> ID = new Id<>(Identifier.of("yppowers", "sync_fire_god"));
        public static final PacketCodec<RegistryByteBuf, SyncFireGodPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeBoolean(payload.enabled()),
                buf -> new SyncFireGodPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record OpenMorphMenuPayload() implements CustomPayload {
        public static final Id<OpenMorphMenuPayload> ID = new Id<>(Identifier.of("yppowers", "open_morph_menu"));
        public static final PacketCodec<RegistryByteBuf, OpenMorphMenuPayload> CODEC = PacketCodec.unit(new OpenMorphMenuPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncShadowArcherPayload(boolean enabled) implements CustomPayload {
        public static final Id<SyncShadowArcherPayload> ID = new Id<>(Identifier.of("yppowers", "sync_shadow_archer"));
        public static final PacketCodec<RegistryByteBuf, SyncShadowArcherPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeBoolean(payload.enabled()),
                buf -> new SyncShadowArcherPayload(buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncShadowArcherMorphCatalogPayload(String encodedIds) implements CustomPayload {
        public static final Id<SyncShadowArcherMorphCatalogPayload> ID = new Id<>(Identifier.of("yppowers", "sync_shadow_archer_morph_catalog"));
        public static final PacketCodec<RegistryByteBuf, SyncShadowArcherMorphCatalogPayload> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeString(payload.encodedIds(), 32767),
                buf -> new SyncShadowArcherMorphCatalogPayload(buf.readString(32767)));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    private YPNetworking() {}

    public static void registerServerReceivers() {
        PayloadTypeRegistry.playC2S().register(KeybindPayload.ID, KeybindPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MorphRequestPayload.ID, MorphRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncFireGodPayload.ID, SyncFireGodPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMorphMenuPayload.ID, OpenMorphMenuPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncShadowArcherPayload.ID, SyncShadowArcherPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncShadowArcherMorphCatalogPayload.ID, SyncShadowArcherMorphCatalogPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(KeybindPayload.ID, (payload, context) -> {
            int keyType = payload.keyType();
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                PowerManager manager = PowerManager.get(context.server());
                PowerType type = manager.getPowerType(player.getUuid());
                Power power = type == null ? null : manager.getPowerInstance(type);
                if (power == null) return;
                if (keyType == 2 && power instanceof ShadowArcherPower) {
                    // The request itself arrived over YP Powers' registered keybind payload,
                    // so this is a modded client. Send the paired screen payload directly;
                    // relying on canSend here can suppress the menu-open action on otherwise
                    // compatible 1.21.11 connections.
                    LOGGER.info("Sending Shadow Archer morph menu request response to {}", player.getUuid().toString());
                    ServerPlayNetworking.send(player, new OpenMorphMenuPayload());
                    return;
                }
                if (keyType == 5) {
                    if (type == PowerType.BOOTS_SKY) power.onPrimaryActivate(player);
                    return;
                }
                switch (keyType) {
                    case 0 -> power.onPrimaryActivate(player);
                    case 1 -> power.onSecondaryActivate(player);
                    case 2 -> power.onTertiaryActivate(player);
                    case 3 -> power.onQuaternaryActivate(player);
                    case 4 -> power.onAlternateActivate(player);
                    default -> { }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MorphRequestPayload.ID, (payload, context) -> {
            String requested = payload.requested();
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                PowerManager manager = PowerManager.get(context.server());
                if (manager.getPowerType(player.getUuid()) != PowerType.SHADOW_ARCHER) return;
                Power power = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
                if (power instanceof ShadowArcherPower archer) archer.requestMorph(player, requested);
            });
        });
    }

    public static void syncFireGod(ServerPlayerEntity player, boolean hasFireGod) {
        if (player != null) ServerPlayNetworking.send(player, new SyncFireGodPayload(hasFireGod));
    }

    public static void syncShadowArcher(ServerPlayerEntity player, boolean hasShadowArcher) {
        if (player != null) ServerPlayNetworking.send(player, new SyncShadowArcherPayload(hasShadowArcher));
    }

    public static void syncShadowArcherMorphCatalog(ServerPlayerEntity player) {
        if (player == null || player.getEntityWorld().getServer() == null) return;
        java.util.Set<String> ids = PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherKills(player.getUuid());
        ServerPlayNetworking.send(player, new SyncShadowArcherMorphCatalogPayload(String.join("\n", ids)));
    }
}
