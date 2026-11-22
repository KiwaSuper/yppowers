package com.yppowers.network;

import net.minecraft.network.RegistryByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Compact server-to-client snapshot powering the upgraded YP Powers HUD. */
public record HudSnapshot(
        String powerId,
        String statusChip,
        double energy,
        double maxEnergy,
        int bleedingTicks,
        boolean graceActive,
        int lockoutTicks,
        int combatTicks,
        int maxCombatTicks,
        List<SlotEntry> slots
) {
    public record SlotEntry(
            int slotWireId,
            String label,
            int cooldownTicks,
            int maxCooldownTicks,
            boolean isToggle,
            boolean toggleActive,
            boolean canAfford
    ) {}

    public static HudSnapshot empty() {
        return new HudSnapshot("", "", 100.0, 100.0, 0, false, 0, 0, 600, List.of());
    }

    public static void write(HudSnapshot s, RegistryByteBuf buf) {
        buf.writeString(s.powerId(), 32);
        buf.writeString(s.statusChip(), 64);
        buf.writeDouble(s.energy());
        buf.writeDouble(s.maxEnergy());
        buf.writeVarInt(s.bleedingTicks());
        buf.writeBoolean(s.graceActive());
        buf.writeVarInt(s.lockoutTicks());
        buf.writeVarInt(s.combatTicks());
        buf.writeVarInt(s.maxCombatTicks());
        buf.writeVarInt(s.slots().size());
        for (SlotEntry entry : s.slots()) {
            buf.writeVarInt(entry.slotWireId());
            buf.writeString(entry.label(), 24);
            buf.writeVarInt(entry.cooldownTicks());
            buf.writeVarInt(entry.maxCooldownTicks());
            buf.writeBoolean(entry.isToggle());
            buf.writeBoolean(entry.toggleActive());
            buf.writeBoolean(entry.canAfford());
        }
    }

    public static HudSnapshot read(RegistryByteBuf buf) {
        String powerId = buf.readString(32);
        String statusChip = buf.readString(64);
        double energy = buf.readDouble();
        double maxEnergy = buf.readDouble();
        int bleedingTicks = buf.readVarInt();
        boolean graceActive = buf.readBoolean();
        int lockoutTicks = buf.readVarInt();
        int combatTicks = buf.readVarInt();
        int maxCombatTicks = buf.readVarInt();
        int count = Math.max(0, Math.min(16, buf.readVarInt()));
        List<SlotEntry> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots.add(new SlotEntry(
                    buf.readVarInt(),
                    buf.readString(24),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            ));
        }
        return new HudSnapshot(powerId, statusChip, energy, maxEnergy, bleedingTicks, graceActive, lockoutTicks, combatTicks, maxCombatTicks, slots);
    }
}
