package com.yppowers.network;

import com.yppowers.core.HudPill;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Full client HUD snapshot synced from server to client. */
public record HudSnapshot(
   String powerId,
   String statusChip,
   double energy,
   double maxEnergy,
   int bleedingTicks,
   int lockoutTicks,
   boolean graceActive,
   boolean grimoireActive,
   int combatTicks,
   int maxCombatTicks,
   List<HudPill> pills
) implements CustomPayload {
   public static final Id<HudSnapshot> ID = new Id<>(Identifier.of("yppowers", "hud_snapshot"));

   public static final PacketCodec<RegistryByteBuf, HudSnapshot> CODEC = PacketCodec.of(
      (s, buf) -> {
         buf.writeString(s.powerId(), 32);
         buf.writeString(s.statusChip(), 64);
         buf.writeDouble(s.energy());
         buf.writeDouble(s.maxEnergy());
         buf.writeVarInt(s.bleedingTicks());
         buf.writeVarInt(s.lockoutTicks());
         buf.writeBoolean(s.graceActive());
         buf.writeBoolean(s.grimoireActive());
         buf.writeVarInt(s.combatTicks());
         buf.writeVarInt(s.maxCombatTicks());
         List<HudPill> list = s.pills();
         buf.writeVarInt(list.size());
         for (HudPill p : list) {
            buf.writeVarInt(p.keyWireId());
            buf.writeString(p.label(), 48);
            buf.writeVarInt(p.cooldownTicks());
            buf.writeVarInt(p.maxCooldownTicks());
            buf.writeBoolean(p.isToggle());
            buf.writeBoolean(p.toggleActive());
            buf.writeBoolean(p.canAfford());
         }
      },
      buf -> {
         String powerId = buf.readString(32);
         String statusChip = buf.readString(64);
         double energy = buf.readDouble();
         double maxEnergy = buf.readDouble();
         int bleedingTicks = buf.readVarInt();
         int lockoutTicks = buf.readVarInt();
         boolean graceActive = buf.readBoolean();
         boolean grimoireActive = buf.readBoolean();
         int combatTicks = buf.readVarInt();
         int maxCombatTicks = buf.readVarInt();
         int count = Math.max(0, Math.min(24, buf.readVarInt()));
         List<HudPill> pills = new ArrayList<>(count);
         for (int i = 0; i < count; i++) {
            pills.add(new HudPill(
               buf.readVarInt(),
               buf.readString(48),
               buf.readVarInt(),
               buf.readVarInt(),
               buf.readBoolean(),
               buf.readBoolean(),
               buf.readBoolean()
            ));
         }
         return new HudSnapshot(
            powerId, statusChip, energy, maxEnergy, bleedingTicks, lockoutTicks,
            graceActive, grimoireActive, combatTicks, maxCombatTicks, List.copyOf(pills)
         );
      }
   );

   public static HudSnapshot empty() {
      return new HudSnapshot("", "", 100.0, 100.0, 0, 0, false, false, 0, 600, List.of());
   }

   @Override
   public Id<? extends CustomPayload> getId() {
      return ID;
   }
}
