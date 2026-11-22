package com.yppowers.client.screen;

import com.yppowers.core.AbilitySlot;
import com.yppowers.core.PowerId;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Structured in-game reference descriptions for all 17 powers, dynamically referencing live bound keys. */
@Environment(EnvType.CLIENT)
public final class PowerGuideData {
    public record AbilityEntry(AbilitySlot slot, String customTrigger, String name, String description) {
        public static AbilityEntry key(AbilitySlot slot, String name, String desc) {
            return new AbilityEntry(slot, "", name, desc);
        }

        public static AbilityEntry trigger(String trigger, String name, String desc) {
            return new AbilityEntry(null, trigger, name, desc);
        }
    }

    public record PowerEntry(PowerId id, String role, List<String> passives, List<AbilityEntry> abilities) {}

    private static final Map<PowerId, PowerEntry> ENTRIES = new EnumMap<>(PowerId.class);

    static {
        put(PowerId.DIVINITY, "Defensive zone control & team support",
                List.of("Reflects 30% of incoming attacker damage.", "Soulbound Unbreakable shield kept on death."),
                List.of(AbilityEntry.key(AbilitySlot.PRIMARY, "Barrier Dome", "12-block dome repelling enemies/projectiles and healing allies inside.")));

        put(PowerId.GOAT, "High-speed mobility & burst Overdrive",
                List.of("+5 hearts, Jump Boost, boosted jumps with fall protection.", "Ramping sprint speed up to 5x with yellow wake; -10% melee dmg."),
                List.of(
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Overdrive", "Massive speed/jump/damage boost; defers health damage into Debt repaid later."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Toggle Passives", "Free persistent toggle for all GOAT passive traits.")));

        put(PowerId.TIME_WARPER, "Battlefield control, rewinds & clones",
                List.of("Collect Time Shards from mob kills; Golden Shards drop from bosses.", "Clones pause lifespan while you are offline."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Time Stop / Time Clone", "TIME: freeze entities/projectiles (50% dmg). REALITY: summon Time Clone (3 Shards)."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Global Rewind", "Rewinds online players 25s back in position, dimension, and health."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Black Hole", "Sacrifices 50% HP to summon an item/terrain-charged singularity."),
                        AbilityEntry.key(AbilitySlot.QUATERNARY, "Elite Clone", "Summons a gold-skinned Elite Clone (1 Golden Shard)."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Switch Form", "Cycles TIME and REALITY forms (10m persistent cooldown).")));

        put(PowerId.GAMBIT, "Portal mobility & pocket-dimension capture",
                List.of("Safe return tracking restores players cleanly on domain end, death, or reconnect."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Gateway", "Places a short-lived End Portal launching you 25-50 blocks forward safely."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Pocket Domain", "Channels a ritual pulling nearby players into your boosted arena dimension.")));

        put(PowerId.PUNCH, "Destructive melee & shockwave pressure",
                List.of("Bare-fist hits on the same target stack Momentum (+8% melee per stack)."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Serious Punch", "3-way tunneling shockwave fan; damages, weakens, and stuns enemies + Strength III."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "HELLDIVE", "Launches up then slams down into a crater blast with no fall damage.")));

        put(PowerId.POSEIDON, "Water dominance, sustain & trident utility",
                List.of("In water: Water Breathing, Dolphin's Grace, Regen V, +20% dmg, no hunger drain.", "On land: -10% speed and Weakness. Soulbound Unbreakable Trident."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Trident Mode", "Switches Trident between Riptide III and Loyalty III + Channeling."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Tsunami", "Requires nearby water; unleashes expanding wave damaging & stunning enemies."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Swap Mode", "Alternate key to toggle Trident enchantment mode.")));

        put(PowerId.SHADOW_ARCHER, "Special arrows & mob morphing",
                List.of("Carry 8 arrows + catalyst (Eye of Ender, TNT, Lightning Rod, Packed Ice, Amethyst) to unlock arrows.", "Kill any mob to permanently unlock its morph."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Morph Primary", "Triggers your active morph's primary skill (Blaze fireball, Enderman blink, etc.)."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Morph Secondary", "Triggers your active morph's secondary buff/skill."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Morph Menu", "Opens the searchable, category-filtered mob morph catalog."),
                        AbilityEntry.key(AbilitySlot.QUATERNARY, "Cycle Arrow", "Cycles unlocked arrows (Split Shot, Homing, TNT, Lightning, Ice Prison, Railshot)."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Unmorph", "Returns immediately to human form.")));

        put(PowerId.BOOTS_SKY, "Survival flight & orbital thunder strikes",
                List.of("Soulbound Unbreakable Netherite Boots; flight drains tiny Energy upkeep & pauses regen.", "Automatic Slow Falling if flight stops in mid-air."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Flight Toggle", "Toggle survival flight while wearing Boots of the Sky (or double-tap Jump)."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Raigo", "Charges a sky thunder orb at your look target for heavy lightning strikes."),
                        AbilityEntry.key(AbilitySlot.FEATHER_FALLING_TOGGLE, "Feather Fall Toggle", "Free persistent toggle for Feather Falling IV on the boots.")));

        put(PowerId.DELETION, "Extreme knockback & wall-impact punishment",
                List.of("No Energy cost — powered purely by vanilla Sticks."),
                List.of(AbilityEntry.trigger("Attack w/ Stick", "Stick Launch", "Launches enemies across the map; slamming into a wall triggers bonus explosion damage.")));

        put(PowerId.FIRE_GOD, "Fire immunity, Nether truce & fire summons",
                List.of("Immune to fire/lava + clear lava vision; Nether mobs ignore you.", "Soulbound Ember of the Undying Flame burns forever as furnace fuel."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Ghast Cannon", "Fires an explosive Ghast fireball."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Ember Guard", "Summons an allied Blaze bodyguard."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Cinder Sentinel", "Summons an allied Wither Skeleton bodyguard.")));

        put(PowerId.ICE_EMPEROR, "Area freeze, stances & frost bodyguards",
                List.of("Glacial Aura freezes water, chills mobs, leaves ice trail, +20% dmg on ice.", "Immune to freezing; +30% fire damage taken & Weakness in Nether."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Absolute Zero", "10-block frost wave freezing enemies in temporary packed-ice prisons."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Ice Meteor", "Calls down a freezing meteor strike at your look point."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Frost Guards", "Summons Polar Bear guards (low Energy spawns weakened/possibly rogue bears)."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Glacial Field", "Free persistent toggle for aura & ice trail."),
                        AbilityEntry.key(AbilitySlot.FORM_SWITCH, "Cycle Form", "Switches between Armoured, Stealth, and Assassin stances."),
                        AbilityEntry.key(AbilitySlot.HEAT_BLAST, "Heat Blast", "Dumps all Energy for a fiery blast; locks Ice abilities for 60s.")));

        put(PowerId.THOR, "Storm mobility & lightning melee",
                List.of("Permanent Speed II and complete lightning immunity (even during Grace).", "Soulbound Unbreakable enchanted Mace (Density V, Wind Burst I, Fire Aspect II)."),
                List.of(
                        AbilityEntry.trigger("Hold RMB (Mace)", "Riptide Anywhere", "Hold & release right-click to launch like Riptide III without water or cooldown."),
                        AbilityEntry.trigger("Melee (Mace)", "Lightning Strike", "Melee hits summon a lightning bolt on non-allied targets.")));

        put(PowerId.FALLOUT_WARRIOR, "Radioactive support aura & minion squad",
                List.of("All summons and zones respect alliances automatically."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Rad Squad", "Deploys Healer, Defender, and fast Vindicator Assassin minions."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Toxic Pool", "Creates a 10-block poison/slow zone for 15 seconds."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Rad Aura", "Free toggle: heals nearby allies and poisons enemies.")));

        put(PowerId.SPIDEY, "Grappling traversal, rage & aerial dive chains",
                List.of("Soulbound Web Slingers item triggers Web Sling on right-click."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Web Sling", "Free grapple pull toward aimed blocks; resets fall distance."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Web Grenade", "Deploys a slowing/poisoning web cloud at look target."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Rage Mode", "Boosts speed, web pull, & melee (+30%); causes Heart Palpitations after."),
                        AbilityEntry.key(AbilitySlot.QUATERNARY, "Spider Dive", "Aerial lunge that rebounds on hit for up to a 4-hit combo."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Auto-MLG", "Free toggle: automatically places temporary cobwebs to break big falls.")));

        put(PowerId.SPIRIT_CHANNELER, "Unlockable spirit summons & shadow ultimate",
                List.of("Kill Wolf+Bone, Guardian+Prismarine Shard, or Wither Skeleton+Phantom Membrane to unlock spirits."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Summon Spirit", "Calls your selected unlocked spirit (max 3 active)."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Shadow Step", "Short-range blink teleport along your look line."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Restless Shadows", "Invisible Darkness shadow; casting under 75 Energy unleashes a hazardous rogue shadow!"),
                        AbilityEntry.key(AbilitySlot.QUATERNARY, "Cycle Spirit", "Cycles through unlocked spirit forms (also Alternate key).")));

        put(PowerId.ILLUSIONIST, "Spatial erasure, telekinesis & gravity well",
                List.of("Passively attracts nearby dropped items and XP orbs."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Spatial Erasure", "Arms next block right-click to temporarily erase a 3x3x3 region (damages on restore)."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Psychokinetic Grip", "Grabs an entity/item in front of you and holds it mid-air."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Celestial Pull", "Spawns a hollow moon, makes enemies weightless, then crashes down in a crater blast."),
                        AbilityEntry.key(AbilitySlot.QUATERNARY, "Throw Grip", "Launches the gripped target forward (left-click also throws)."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Grip Distance", "Cycles telekinetic hold distance between 2 and 10 blocks.")));

        put(PowerId.KITSUNE, "Foxfire DoT & Giant Kitsune transformation",
                List.of("While transformed, right-clicking air with a block places it up to 16 blocks away.", "Foxfire burns afflicted targets with glowing blue soul-fire."),
                List.of(
                        AbilityEntry.key(AbilitySlot.PRIMARY, "Claw Swipe", "Giant Fox only: sweeping melee knockback applying Foxfire."),
                        AbilityEntry.key(AbilitySlot.SECONDARY, "Foxfire Burst", "Giant Fox only: long-range supernatural flame beam applying Foxfire."),
                        AbilityEntry.key(AbilitySlot.TERTIARY, "Spirit Pounce", "Giant Fox only: targeted leap strike applying Foxfire."),
                        AbilityEntry.key(AbilitySlot.QUATERNARY, "Foxfire Detonation", "Explodes all targets currently burning with Foxfire."),
                        AbilityEntry.key(AbilitySlot.ALTERNATE, "Giant Kitsune", "Transforms into a fast giant fox with soul-fire trails.")));
    }

    private PowerGuideData() {}

    private static void put(PowerId id, String role, List<String> passives, List<AbilityEntry> abilities) {
        ENTRIES.put(id, new PowerEntry(id, role, passives, abilities));
    }

    public static PowerEntry get(PowerId id) {
        return ENTRIES.get(id);
    }
}
