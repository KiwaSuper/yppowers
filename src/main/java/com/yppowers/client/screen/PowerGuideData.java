package com.yppowers.client.screen;

import com.yppowers.core.AbilityKey;
import com.yppowers.power.PowerType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Complete in-game 17-power reference guide including 1.19.14 moves and Awakenings with live bound key labels. */
@Environment(EnvType.CLIENT)
public final class PowerGuideData {
   public record AbilityEntry(AbilityKey key, String customTrigger, String name, String description) {
      public static AbilityEntry key(AbilityKey key, String name, String desc) {
         return new AbilityEntry(key, "", name, desc);
      }

      public static AbilityEntry trigger(String trigger, String name, String desc) {
         return new AbilityEntry(null, trigger, name, desc);
      }
   }

   public record PowerEntry(PowerType type, String role, List<String> passives, List<AbilityEntry> abilities) {
   }

   private static final Map<PowerType, PowerEntry> ENTRIES = new EnumMap<>(PowerType.class);

   static {
      put(PowerType.DIVINITY, "Zone defense, damage absorption & reflect",
         List.of("Soulbound Unbreakable Shield; stores blocked/deflected damage.", "Awakening unlocks stronger dome & burst reflection."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Barrier Dome", "Repels enemies/projectiles and heals allies inside."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Damage Reflect", "Unleashes stored absorbed damage at enemies."),
            AbilityEntry.key(AbilityKey.AWAKENING, "Awakening", "Activates awakened Divinity power once unlocked.")));

      put(PowerType.GOAT, "Extreme speed, super jumps & Overdrive",
         List.of("+5 hearts, Jump Boost, ramping sprint speed with yellow wake."),
         List.of(
            AbilityEntry.key(AbilityKey.TERTIARY, "Overdrive", "Massive speed/jump/damage burst; defers damage to Debt."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Toggle Passives", "Free persistent toggle for GOAT passives."),
            AbilityEntry.key(AbilityKey.AWAKENING, "Ramming Awakening", "Break blocks at max speed (confirm with F6).")));

      put(PowerType.TIME_WARPER, "Time control, private domain & clones",
         List.of("Collect Time & Golden Shards; private Time Domain & Portable Chunks."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Time Stop / Clone", "TIME: freeze area. REALITY: summon Time Clone (3 Shards)."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Global Rewind", "Rewinds players 25s back in position, world, and HP."),
            AbilityEntry.key(AbilityKey.TERTIARY, "Black Hole", "Hold to sustain an item/terrain-charged singularity."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Switch Form", "Cycles TIME and REALITY forms."),
            AbilityEntry.key(AbilityKey.CHUNK_DUPING_DOMAIN, "Chunk Domain", "Opens Time Warper Chunk Duping Domain."),
            AbilityEntry.key(AbilityKey.PORTABLE_CHUNK_SWAP, "Swap Chunk", "Swaps stored portable chunk with target.")));

      put(PowerType.GAMBIT, "Portals, Pocket Domain & structure warps",
         List.of("Safe return tracking restores players cleanly from domains."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Gateway", "Forward End Portal launching 25-50 blocks safely."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Pocket Domain", "Pulls nearby players into your boosted arena dimension."),
            AbilityEntry.key(AbilityKey.AWAKENING, "Structure Warp", "Opens structure teleport catalog once awakened.")));

      put(PowerType.PUNCH, "Destructive melee & shockwave craters",
         List.of("Bare-fist hits on the same target stack Momentum."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Serious Punch", "3-way tunneling shockwave fan + Strength III."),
            AbilityEntry.key(AbilityKey.SECONDARY, "HELLDIVE", "Launches up and slams down into a crater blast."),
            AbilityEntry.key(AbilityKey.AWAKENING, "Awakened Blast", "Devastating shockwave explosion around you.")));

      put(PowerType.POSEIDON, "Water dominance, trident & ocean sink",
         List.of("Huge water buffs + soulbound Unbreakable Trident."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Trident Mode", "Switches Trident between Riptide III & Loyalty + Channeling."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Tsunami", "Expanding water wave damaging & stunning enemies."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Turbo Swim", "Toggles high-speed water propulsion."),
            AbilityEntry.key(AbilityKey.AWAKENING, "Poseidon Sink", "Opens terrain-sink confirmation menu once awakened.")));

      put(PowerType.SHADOW_ARCHER, "Special arrows, bow modes & mob morphs",
         List.of("Kill mobs to unlock morphs; unlock 9+ special arrow types."),
         List.of(
            AbilityEntry.key(AbilityKey.TERTIARY, "Morph Menu", "Searchable, filterable mob morph & arrow menu."),
            AbilityEntry.key(AbilityKey.QUATERNARY, "Cycle Arrow", "Cycles unlocked special arrows."),
            AbilityEntry.key(AbilityKey.SHADOW_ARCHER_SPECIAL_ARROWS, "Toggle Arrows", "Enable/disable special arrow firing."),
            AbilityEntry.key(AbilityKey.SHADOW_ARCHER_ARROW_SWAP, "Arrow Swap", "Teleport/swap with your latest fired arrow."),
            AbilityEntry.key(AbilityKey.SHADOW_ARCHER_BOW_MODE, "Bow Mode", "Swap bow between Infinity & Mending.")));

      put(PowerType.BOOTS_SKY, "Survival flight, elytra safety & Raigo",
         List.of("Soulbound Netherite Flight Boots + Elytra wall-crash immunity."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Flight Toggle", "Toggle flight (or double-tap Jump)."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Raigo", "Charges a sky thunder orb for heavy lightning strikes."),
            AbilityEntry.key(AbilityKey.BOOTS_FEATHER_FALLING, "Feather Fall", "Free persistent Feather Falling IV toggle.")));

      put(PowerType.DELETION, "Extreme knockback & place swapping",
         List.of("Vanilla Sticks launch enemies with wall-impact explosions."),
         List.of(
            AbilityEntry.trigger("Attack w/ Stick", "Stick Launch", "Launches enemies across the map into walls."),
            AbilityEntry.key(AbilityKey.DELETION_SWAP, "Swap Places", "Instantly swaps positions with your aimed target.")));

      put(PowerType.FIRE_GOD, "Fire/lava immunity, Blaze beam & Wither Ascendance",
         List.of("Fire/lava immune + clear lava vision; Nether mob truce; soulbound Ember."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Ghast Cannon", "Fires an explosive Ghast fireball."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Ember Guard", "Summons allied Blaze bodyguards."),
            AbilityEntry.key(AbilityKey.TERTIARY, "Cinder Sentinel", "Summons allied Wither Skeleton guards."),
            AbilityEntry.key(AbilityKey.QUATERNARY, "Blaze Barrage", "Hold to fire a continuous stream of fireballs."),
            AbilityEntry.key(AbilityKey.WITHER_ASCENDANCE_SKULL, "Wither Skulls", "In Wither Ascendance: fires explosive Wither Skull volley.")));

      put(PowerType.ICE_EMPEROR, "Freeze prisons, 3 stances & Heat Blast",
         List.of("Glacial Aura freezes water/mobs, +20% dmg on ice."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Absolute Zero", "Freezes nearby enemies in temporary packed-ice prisons."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Ice Meteor", "Calls down a freezing meteor at your look point."),
            AbilityEntry.key(AbilityKey.TERTIARY, "Frost Guards", "Summons Polar Bear bodyguards."),
            AbilityEntry.key(AbilityKey.ICE_EMPEROR_FORM, "Cycle Form", "Switches Armoured, Stealth, and Assassin stances."),
            AbilityEntry.key(AbilityKey.ICE_HEAT_BLAST, "Heat Blast", "Dumps all Energy into a fiery blast (60s Ice lockout).")));

      put(PowerType.THOR, "Storm mobility & lightning melee",
         List.of("Permanent Speed II, total lightning immunity, soulbound enchanted Mace."),
         List.of(
            AbilityEntry.trigger("Hold RMB (Mace)", "Riptide Anywhere", "Hold & release right-click to launch without water."),
            AbilityEntry.trigger("Melee (Mace)", "Lightning Strike", "Melee hits strike non-allied targets with lightning.")));

      put(PowerType.FALLOUT_WARRIOR, "Radioactive squad, pools & aura",
         List.of("All summons and zones respect alliances automatically."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Rad Squad", "Deploys Healer, Defender & Assassin minions."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Toxic Pool", "Creates a poison/slow zone."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Rad Aura", "Free toggle healing allies & poisoning enemies."),
            AbilityEntry.key(AbilityKey.AWAKENING, "Fallout Bomb", "Detonates a toxic blast & empowers minions.")));

      put(PowerType.SPIDEY, "Web grappling, Rage & dive combos",
         List.of("Soulbound Web Slingers (RMB sling, Sneak+RMB web shot)."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Web Sling", "Grapple pull toward aimed blocks; resets fall damage."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Web Grenade", "Slows & poisons targets in a web cloud."),
            AbilityEntry.key(AbilityKey.TERTIARY, "Rage Mode", "Boosts speed, web pull, & melee (+30%)."),
            AbilityEntry.key(AbilityKey.QUATERNARY, "Spider Dive", "Aerial lunge chaining up to 4 rebounds."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Auto-MLG", "Free toggle auto-placing temporary cobwebs on falls.")));

      put(PowerType.SPIRIT_CHANNELER, "Unlockable spirit summons & shadow step",
         List.of("Unlock Wolf, Guardian & Wither Skeleton spirits (max 6 active)."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Summon Spirit", "Calls your selected spirit companion."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Shadow Step", "Short-range blink along your look line."),
            AbilityEntry.key(AbilityKey.TERTIARY, "Restless Shadows", "Darkness shadow (rogue hazard if cast under 75 Energy)."),
            AbilityEntry.key(AbilityKey.QUATERNARY, "Cycle Spirit", "Cycles unlocked spirit forms.")));

      put(PowerType.ILLUSIONIST, "Spatial erasure, telekinesis & Celestial Pull",
         List.of("Passively attracts nearby dropped items & XP."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Spatial Erasure", "Arms next block RMB to temporarily erase a 3x3x3 area."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Psychokinetic Grip", "Grabs and holds entities/items mid-air."),
            AbilityEntry.key(AbilityKey.TERTIARY, "Celestial Pull", "Spawns a hollow moon & crashes it in a crater blast."),
            AbilityEntry.key(AbilityKey.QUATERNARY, "Throw Grip", "Launches gripped target forward."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Cloud Flight", "Toggles Illusionist cloud flight / grip distance.")));

      put(PowerType.KITSUNE, "Giant Kitsune form, Foxfire & Carry/Tunnel",
         List.of("Giant Fox places blocks up to 16 blocks away in air."),
         List.of(
            AbilityEntry.key(AbilityKey.PRIMARY, "Claw Swipe", "Giant Fox: sweeping knockback applying Foxfire."),
            AbilityEntry.key(AbilityKey.SECONDARY, "Foxfire Burst", "Giant Fox: long-range blue soul-fire beam."),
            AbilityEntry.key(AbilityKey.TERTIARY, "Spirit Pounce", "Giant Fox: targeted leap strike."),
            AbilityEntry.key(AbilityKey.QUATERNARY, "Detonation", "Explodes all enemies burning with Foxfire."),
            AbilityEntry.key(AbilityKey.ALTERNATE, "Giant Kitsune", "Transforms into a fast giant fox."),
            AbilityEntry.key(AbilityKey.KITSUNE_CARRY, "Carry / Tunnel", "Carry allies/enemies, mount, or dig tunnels.")));
   }

   private PowerGuideData() {
   }

   private static void put(PowerType type, String role, List<String> passives, List<AbilityEntry> abilities) {
      ENTRIES.put(type, new PowerEntry(type, role, passives, abilities));
   }

   public static PowerEntry get(PowerType type) {
      return ENTRIES.get(type);
   }
}
