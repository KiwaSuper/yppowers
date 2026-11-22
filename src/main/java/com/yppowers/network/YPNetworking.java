package com.yppowers.network;

import com.yppowers.YPPowersMod;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.EnergySystem;
import com.yppowers.system.GraceSystem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

/** Clean typed CustomPayload networking for YP Powers 2.0. */
public final class YPNetworking {
    private static BiConsumer<ServerPlayerEntity, String> morphRequestHandler;

    private YPNetworking() {}

    public static void setMorphRequestHandler(BiConsumer<ServerPlayerEntity, String> handler) {
        morphRequestHandler = handler;
    }

    // -------------------------------------------------------------------------
    // C2S Payloads
    // -------------------------------------------------------------------------

    public record AbilityTriggerPayload(int slotWireId) implements CustomPayload {
        public static final Id<AbilityTriggerPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "ability_trigger"));
        public static final PacketCodec<RegistryByteBuf, AbilityTriggerPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeVarInt(p.slotWireId()),
                buf -> new AbilityTriggerPayload(buf.readVarInt())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record MorphRequestPayload(String mobId) implements CustomPayload {
        public static final Id<MorphRequestPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "morph_request"));
        public static final PacketCodec<RegistryByteBuf, MorphRequestPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeString(p.mobId(), 128),
                buf -> new MorphRequestPayload(buf.readString(128))
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // -------------------------------------------------------------------------
    // S2C Payloads
    // -------------------------------------------------------------------------

    public record SyncHudPayload(HudSnapshot snapshot) implements CustomPayload {
        public static final Id<SyncHudPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "sync_hud"));
        public static final PacketCodec<RegistryByteBuf, SyncHudPayload> CODEC = PacketCodec.of(
                (p, buf) -> HudSnapshot.write(p.snapshot(), buf),
                buf -> new SyncHudPayload(HudSnapshot.read(buf))
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncHudWarningPayload(String text, int ticks) implements CustomPayload {
        public static final Id<SyncHudWarningPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "sync_hud_warning"));
        public static final PacketCodec<RegistryByteBuf, SyncHudWarningPayload> CODEC = PacketCodec.of(
                (p, buf) -> {
                    buf.writeString(p.text(), 128);
                    buf.writeVarInt(p.ticks());
                },
                buf -> new SyncHudWarningPayload(buf.readString(128), buf.readVarInt())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncTimeStopVisualPayload(int entityId, boolean frozen) implements CustomPayload {
        public static final Id<SyncTimeStopVisualPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "sync_time_stop_visual"));
        public static final PacketCodec<RegistryByteBuf, SyncTimeStopVisualPayload> CODEC = PacketCodec.of(
                (p, buf) -> {
                    buf.writeVarInt(p.entityId());
                    buf.writeBoolean(p.frozen());
                },
                buf -> new SyncTimeStopVisualPayload(buf.readVarInt(), buf.readBoolean())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncMorphCatalogPayload(String encodedIds) implements CustomPayload {
        public static final Id<SyncMorphCatalogPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "sync_morph_catalog"));
        public static final PacketCodec<RegistryByteBuf, SyncMorphCatalogPayload> CODEC = PacketCodec.of(
                (p, buf) -> buf.writeString(p.encodedIds(), 32767),
                buf -> new SyncMorphCatalogPayload(buf.readString(32767))
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncMorphViewPayload(boolean morphed, int disguiseEntityId, float eyeHeight, float entityHeight, String mobId) implements CustomPayload {
        public static final Id<SyncMorphViewPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "sync_morph_view"));
        public static final PacketCodec<RegistryByteBuf, SyncMorphViewPayload> CODEC = PacketCodec.of(
                (p, buf) -> {
                    buf.writeBoolean(p.morphed());
                    buf.writeVarInt(p.disguiseEntityId());
                    buf.writeFloat(p.eyeHeight());
                    buf.writeFloat(p.entityHeight());
                    buf.writeString(p.mobId(), 128);
                },
                buf -> new SyncMorphViewPayload(
                        buf.readBoolean(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readString(128))
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SyncIceEmperorFormPayload(String playerUuid, String form, boolean active) implements CustomPayload {
        public static final Id<SyncIceEmperorFormPayload> ID = new Id<>(Identifier.of(YPPowersMod.MOD_ID, "sync_ice_emperor_form"));
        public static final PacketCodec<RegistryByteBuf, SyncIceEmperorFormPayload> CODEC = PacketCodec.of(
                (p, buf) -> {
                    buf.writeString(p.playerUuid(), 36);
                    buf.writeString(p.form(), 16);
                    buf.writeBoolean(p.active());
                },
                buf -> new SyncIceEmperorFormPayload(buf.readString(36), buf.readString(16), buf.readBoolean())
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // -------------------------------------------------------------------------
    // Registration & Dispatch
    // -------------------------------------------------------------------------

    public static void registerServerReceivers() {
        PayloadTypeRegistry.playC2S().register(AbilityTriggerPayload.ID, AbilityTriggerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MorphRequestPayload.ID, MorphRequestPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncHudPayload.ID, SyncHudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncHudWarningPayload.ID, SyncHudWarningPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncTimeStopVisualPayload.ID, SyncTimeStopVisualPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncMorphCatalogPayload.ID, SyncMorphCatalogPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncMorphViewPayload.ID, SyncMorphViewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncIceEmperorFormPayload.ID, SyncIceEmperorFormPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityTriggerPayload.ID, (payload, context) -> {
            AbilitySlot slot = AbilitySlot.fromWireId(payload.slotWireId());
            if (slot == null) return;
            context.server().execute(() -> handleAbilityTrigger(context.player(), slot));
        });

        ServerPlayNetworking.registerGlobalReceiver(MorphRequestPayload.ID, (payload, context) -> {
            String mobId = payload.mobId();
            context.server().execute(() -> {
                if (morphRequestHandler != null) {
                    morphRequestHandler.accept(context.player(), mobId);
                }
            });
        });
    }

    private static void handleAbilityTrigger(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        Power power = PlayerDataStore.get(server).getPower(player);
        if (power == null || !power.supportsSlot(player, slot)) return;
        if (GraceSystem.blockAbility(player)) return;

        int lockout = power.abilityLockoutTicks(player, slot);
        if (lockout > 0) {
            player.sendMessage(Text.literal(power.id().displayName() + " abilities locked for "
                    + CooldownSystem.formatSeconds(lockout) + ".").formatted(Formatting.YELLOW), true);
            return;
        }

        if (!power.canActivate(player, slot)) return;

        if (!power.handlesOwnEnergy(player, slot)) {
            double cost = power.energyCost(player, slot);
            if (cost > 0.0) {
                String label = power.hudAbilities(player).stream()
                        .filter(spec -> spec.slot() == slot)
                        .map(AbilitySpec::label)
                        .findFirst()
                        .orElse(slot.name());
                if (!EnergySystem.get(server).trySpend(player, cost, label)) {
                    return;
                }
            } else if (EnergySystem.get(server).isBleeding(player.getUuid())) {
                // Bleeding blocks even zero-cost active abilities except pure passive toggles
                boolean isToggle = power.hudAbilities(player).stream()
                        .anyMatch(spec -> spec.slot() == slot && spec.isToggle());
                if (!isToggle) {
                    player.sendMessage(Text.literal("You are Bleeding. Abilities are disabled until the effect ends.")
                            .formatted(Formatting.RED), true);
                    return;
                }
            }
        }

        power.activate(player, slot);
        syncHud(player);
    }

    // -------------------------------------------------------------------------
    // Server -> Client Sync Helpers
    // -------------------------------------------------------------------------

    public static void syncPlayerFull(ServerPlayerEntity player) {
        syncHud(player);
    }

    public static void syncHud(ServerPlayerEntity player) {
        if (player == null || player.getEntityWorld().getServer() == null) return;
        MinecraftServer server = player.getEntityWorld().getServer();
        PlayerDataStore store = PlayerDataStore.get(server);
        EnergySystem energySys = EnergySystem.get(server);
        CooldownSystem cooldowns = CooldownSystem.get(server);
        PowerId powerId = store.getPowerId(player.getUuid());
        Power power = store.getPower(player);

        double energy = energySys.getEnergy(player.getUuid());
        double maxEnergy = Math.max(1.0, YPConfig.get().energy.maxEnergy);
        int bleeding = energySys.getBleedingTicks(player.getUuid());
        boolean grace = GraceSystem.isActive(server);
        int lockout = power == null ? 0 : power.abilityLockoutTicks(player, AbilitySlot.PRIMARY);
        String chip = power == null ? "" : power.hudStatusChip(player);

        List<HudSnapshot.SlotEntry> entries = new ArrayList<>();
        if (power != null) {
            for (AbilitySpec spec : power.hudAbilities(player)) {
                int cd = spec.cooldownKey().isEmpty() ? 0 : cooldowns.getRemaining(player.getUuid(), spec.cooldownKey());
                boolean toggleOn = spec.isToggle() && power.isToggleActive(player, spec.slot());
                double cost = power.handlesOwnEnergy(player, spec.slot()) ? 0.0 : power.energyCost(player, spec.slot());
                boolean canAfford = cost <= 0.0 || energy + 1.0e-6 >= cost;
                entries.add(new HudSnapshot.SlotEntry(
                        spec.slot().wireId(), spec.label(), cd, spec.isToggle(), toggleOn, canAfford
                ));
            }
        }

        HudSnapshot snapshot = new HudSnapshot(
                powerId == null ? "" : powerId.id(),
                chip == null ? "" : chip,
                energy,
                maxEnergy,
                bleeding,
                grace,
                lockout,
                entries
        );
        ServerPlayNetworking.send(player, new SyncHudPayload(snapshot));
    }

    public static void sendHudWarning(ServerPlayerEntity player, String text, int ticks) {
        if (player == null) return;
        ServerPlayNetworking.send(player, new SyncHudWarningPayload(text == null ? "" : text, Math.max(0, ticks)));
    }

    public static void sendTimeStopVisual(ServerPlayerEntity player, int entityId, boolean frozen) {
        if (player == null) return;
        ServerPlayNetworking.send(player, new SyncTimeStopVisualPayload(entityId, frozen));
    }

    public static void broadcastTimeStopVisual(MinecraftServer server, int entityId, boolean frozen) {
        if (server == null) return;
        SyncTimeStopVisualPayload payload = new SyncTimeStopVisualPayload(entityId, frozen);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void sendMorphCatalog(ServerPlayerEntity player, Collection<String> unlockedIds) {
        if (player == null) return;
        String encoded = String.join(",", unlockedIds);
        ServerPlayNetworking.send(player, new SyncMorphCatalogPayload(encoded));
    }

    public static void sendMorphView(ServerPlayerEntity player, boolean morphed, int disguiseEntityId,
                                     float eyeHeight, float entityHeight, String mobId) {
        if (player == null) return;
        ServerPlayNetworking.send(player, new SyncMorphViewPayload(morphed, disguiseEntityId, eyeHeight, entityHeight, mobId == null ? "" : mobId));
    }

    public static void broadcastIceEmperorForm(MinecraftServer server, ServerPlayerEntity owner, String form, boolean active) {
        if (server == null || owner == null) return;
        SyncIceEmperorFormPayload payload = new SyncIceEmperorFormPayload(owner.getUuid().toString(), form == null ? "" : form, active);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    public static void sendIceEmperorForm(ServerPlayerEntity target, String ownerUuid, String form, boolean active) {
        if (target == null) return;
        ServerPlayNetworking.send(target, new SyncIceEmperorFormPayload(ownerUuid, form == null ? "" : form, active));
    }
}
