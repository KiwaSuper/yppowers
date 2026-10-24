package com.yppowers.network;

import com.yppowers.command.GraceManager;
import com.yppowers.energy.EnergyManager;
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

    /** Local Shadow Archer camera state derived from the active disguise entity. */
    public record SyncShadowArcherMorphViewPayload(boolean morphed, int disguiseEntityId, float eyeHeight, float entityHeight) implements CustomPayload {
        public static final Id<SyncShadowArcherMorphViewPayload> ID = new Id<>(Identifier.of("yppowers", "sync_shadow_archer_morph_view"));
        public static final PacketCodec<RegistryByteBuf, SyncShadowArcherMorphViewPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeBoolean(payload.morphed());
                    buf.writeVarInt(payload.disguiseEntityId());
                    buf.writeFloat(payload.eyeHeight());
                    buf.writeFloat(payload.entityHeight());
                },
                buf -> new SyncShadowArcherMorphViewPayload(buf.readBoolean(), buf.readVarInt(), buf.readFloat(), buf.readFloat()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Server-authoritative visual state for the muted appearance applied to players caught in Time Stop. */
    public record SyncTimeStopVisualPayload(int entityId, boolean frozen) implements CustomPayload {
        public static final Id<SyncTimeStopVisualPayload> ID = new Id<>(Identifier.of("yppowers", "sync_time_stop_visual"));
        public static final PacketCodec<RegistryByteBuf, SyncTimeStopVisualPayload> CODEC = PacketCodec.of(
                (payload, buf) -> { buf.writeVarInt(payload.entityId()); buf.writeBoolean(payload.frozen()); },
                buf -> new SyncTimeStopVisualPayload(buf.readVarInt(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Server-authoritative Energy HUD snapshot for the local player. */
    public record SyncEnergyPayload(double energy, double maxEnergy, int bleedingTicks, boolean globalHealthEnabled) implements CustomPayload {
        public static final Id<SyncEnergyPayload> ID = new Id<>(Identifier.of("yppowers", "sync_energy"));
        public static final PacketCodec<RegistryByteBuf, SyncEnergyPayload> CODEC = PacketCodec.of(
                (payload, buf) -> {
                    buf.writeDouble(payload.energy());
                    buf.writeDouble(payload.maxEnergy());
                    buf.writeVarInt(payload.bleedingTicks());
                    buf.writeBoolean(payload.globalHealthEnabled());
                },
                buf -> new SyncEnergyPayload(buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readBoolean()));
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
        PayloadTypeRegistry.playS2C().register(SyncShadowArcherMorphViewPayload.ID, SyncShadowArcherMorphViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncTimeStopVisualPayload.ID, SyncTimeStopVisualPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncEnergyPayload.ID, SyncEnergyPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(KeybindPayload.ID, (payload, context) -> {
            int keyType = payload.keyType();
            context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                PowerManager manager = PowerManager.get(context.server());
                PowerType type = manager.getPowerType(player.getUuid());
                Power power = type == null ? null : manager.getPowerInstance(type);
                if (power == null || keyType < 0 || keyType > 5) return;
                if (GraceManager.blockAbility(player)) return;
                if (keyType == 2 && power instanceof ShadowArcherPower) {
                    // Opening the morph selector is UI, not a morph/ability activation. Charge
                    // Energy only when a specific form is requested by MorphRequestPayload.
                    LOGGER.info("Sending Shadow Archer morph menu request response to {}", player.getUuid().toString());
                    ServerPlayNetworking.send(player, new OpenMorphMenuPayload());
                    return;
                }
                // The client's jump double-tap detector sends keyType 5 regardless of power.
                // Unsupported powers and non-Boots owners must not lose Energy for that signal.
                if (keyType == 5) {
                    if (type == PowerType.BOOTS_SKY
                            && EnergyManager.get(context.server()).trySpendAbility(player, keyType)) {
                        power.onPrimaryActivate(player);
                    }
                    return;
                }
                if (!hasAbilityHandler(power, keyType)) return;
                if (!EnergyManager.get(context.server()).trySpendAbility(player, keyType)) return;
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
                if (GraceManager.blockAbility(player)) return;
                if (!EnergyManager.get(context.server()).trySpendAbility(player, 2)) return;
                Power power = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
                if (power instanceof ShadowArcherPower archer) archer.requestMorph(player, requested);
            });
        });
    }

    private static boolean hasAbilityHandler(Power power, int keyType) {
        String name = switch (keyType) {
            case 0 -> "onPrimaryActivate";
            case 1 -> "onSecondaryActivate";
            case 2 -> "onTertiaryActivate";
            case 3 -> "onQuaternaryActivate";
            case 4 -> "onAlternateActivate";
            default -> "";
        };
        if (name.isEmpty()) return false;
        try {
            return power.getClass().getMethod(name, ServerPlayerEntity.class).getDeclaringClass() != Power.class;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
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

    public static void syncShadowArcherMorphView(ServerPlayerEntity player, boolean morphed, int disguiseEntityId,
                                                  float eyeHeight, float entityHeight) {
        if (player == null) return;
        float safeEyeHeight = Float.isFinite(eyeHeight) ? Math.max(0.1F, Math.min(8.0F, eyeHeight)) : 1.62F;
        float safeEntityHeight = Float.isFinite(entityHeight) ? Math.max(0.1F, Math.min(16.0F, entityHeight)) : 1.8F;
        int safeEntityId = morphed ? Math.max(0, disguiseEntityId) : -1;
        ServerPlayNetworking.send(player, new SyncShadowArcherMorphViewPayload(morphed, safeEntityId, safeEyeHeight, safeEntityHeight));
    }

    public static void broadcastTimeStopVisual(net.minecraft.server.MinecraftServer server, int entityId, boolean frozen) {
        if (server == null) return;
        SyncTimeStopVisualPayload payload = new SyncTimeStopVisualPayload(entityId, frozen);
        for (ServerPlayerEntity recipient : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    public static void syncTimeStopVisualTo(ServerPlayerEntity recipient, int entityId) {
        if (recipient != null) ServerPlayNetworking.send(recipient, new SyncTimeStopVisualPayload(entityId, true));
    }

    public static void syncEnergy(ServerPlayerEntity player, double energy, double maxEnergy, int bleedingTicks, boolean globalHealthEnabled) {
        if (player != null) ServerPlayNetworking.send(player,
                new SyncEnergyPayload(energy, maxEnergy, Math.max(0, bleedingTicks), globalHealthEnabled));
    }
}
