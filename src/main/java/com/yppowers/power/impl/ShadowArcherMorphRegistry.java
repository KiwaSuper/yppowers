package com.yppowers.power.impl;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data-driven catalogue for Shadow Archer's player-compatible mob forms.
 * The player remains a player; profiles supply the disguise and copied gameplay traits.
 * String IDs are intentional so this can include the full 1.21.11 roster without hard-linking
 * every new entity to a Java constant that may not exist in older mapping builds.
 */
public final class ShadowArcherMorphRegistry {
    public enum Trait {
        FLIGHT("Flight"), WALL_CLIMB("Wall climbing"), FIRE_RESISTANCE("Fire resistance"),
        AQUATIC("Water breathing and faster swimming"), SPEED("Enhanced speed"),
        STRENGTH("Enhanced melee strength"), JUMP("Enhanced jumping"), RESISTANCE("Damage resistance"),
        SLOW_FALLING("Slow falling"), NIGHT_VISION("Night vision"), REGENERATION("Slow regeneration"),
        POISON_RESISTANCE("Poison resistance"), BOUNCY("Bouncy movement"),
        LAVA_ADAPTED("Lava adaptation");

        private final String displayName;
        Trait(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    public enum Ability {
        NONE("None"), HEADBUTT("Headbutt"),
        DRAGON_BREATH("Dragon Breath"), DRAGON_ROAR("Dragon Roar"), WITHER_BARRAGE("Wither Barrage"),
        WITHER_BLAST("Wither Blast"), SONIC_BOOM("Sonic Boom"), GROUND_SLAM("Ground Slam"),
        GUARD("Guard"), BEAM("Guardian Beam"), FIRE_BLAST("Fire Blast"), FIRE_BURST("Flame Burst"),
        MAGIC_FANGS("Evoker Fangs"), WITCH_BREW("Witch's Brew"), TELEPORT("Teleport"),
        DARK_BURST("Shadow Burst"), WEB_SNARE("Web Snare"), POISON_BITE("Poison Bite"),
        PHASE_DASH("Phase Dash"), AIR_DASH("Air Dash"), GUST("Gust"), CHARGE("Charge"),
        CREEPER_BLAST("Controlled Blast"), BOUNCE("Super Bounce"), SPORE_CLOUD("Spore Cloud"),
        AQUA_DASH("Aquatic Dash"), SNOWBURST("Snowburst"), FROG_LEAP("Frog Leap"),
        FOX_DASH("Fox Dash"), HOWL("Pack Howl"), EQUINE_DASH("Equine Dash"),
        UTILITY_PULSE("Utility Pulse"), SHULKER_LIFT("Levitation Shot"), RANGED_SHOT("Ranged Shot"),
        SWORD_SLASH("Melee Slash"), ROOT_PULSE("Creaking Root Pulse"), HEALING_PULSE("Healing Pulse"),
        PUFFER_BURST("Puffer Burst"), ARMADILLO_ROLL("Armadillo Roll"), DEEP_DASH("Deep Dash"),
        TRADE_WINDS("Trade Winds"), LAVA_DASH("Lava Dash"), BEE_STING("Venom Sting"), SLIME_SLAM("Slime Slam"),
        ECHO_PULSE("Echo Pulse"), INFECTION_BITE("Infection Bite"), SAND_SHOT("Sand Shot");

        private final String displayName;
        Ability(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    public record Profile(String id, String displayName, String summary,
                          Ability primary, Ability secondary, Ability alternate, Set<Trait> traits) {
        public Profile {
            traits = Collections.unmodifiableSet(new LinkedHashSet<>(traits));
        }

        public String abilitySummary() {
            return "G: " + primary.displayName() + "  |  H: " + secondary.displayName()
                    + "  |  R: " + alternate.displayName();
        }

        public String traitSummary() {
            if (traits.isEmpty()) return "Traits: standard player movement";
            return "Traits: " + String.join(", ", traits.stream().map(Trait::displayName).toList());
        }
    }

    private static final Map<String, Profile> PROFILES = new LinkedHashMap<>();
    private static final List<String> VANILLA_MOB_IDS = new ArrayList<>();

    static {
        // Bosses and major threats.
        add("ender_dragon", Ability.DRAGON_BREATH, Ability.DRAGON_ROAR, Ability.AIR_DASH,
                Trait.FLIGHT, Trait.RESISTANCE, Trait.SLOW_FALLING);
        add("wither", Ability.WITHER_BARRAGE, Ability.WITHER_BLAST, Ability.AIR_DASH,
                Trait.FLIGHT, Trait.RESISTANCE, Trait.FIRE_RESISTANCE);
        add("warden", Ability.SONIC_BOOM, Ability.GROUND_SLAM, Ability.ECHO_PULSE,
                Trait.STRENGTH, Trait.RESISTANCE, Trait.NIGHT_VISION);
        add("elder_guardian", Ability.BEAM, Ability.SPORE_CLOUD, Ability.AQUA_DASH,
                Trait.AQUATIC, Trait.RESISTANCE);
        add("ravager", Ability.CHARGE, Ability.GROUND_SLAM, Ability.GUARD,
                Trait.STRENGTH, Trait.RESISTANCE);
        add("iron_golem", Ability.GROUND_SLAM, Ability.GUARD, Ability.CHARGE,
                Trait.STRENGTH, Trait.RESISTANCE);
        add("giant", Ability.GROUND_SLAM, Ability.CHARGE, Ability.GUARD,
                Trait.STRENGTH, Trait.RESISTANCE);

        // Ranged and magical forms.
        add("blaze", Ability.FIRE_BLAST, Ability.FIRE_BURST, Ability.AIR_DASH,
                Trait.FLIGHT, Trait.FIRE_RESISTANCE);
        add("ghast", Ability.FIRE_BLAST, Ability.GUST, Ability.AIR_DASH,
                Trait.FLIGHT, Trait.FIRE_RESISTANCE, Trait.SLOW_FALLING);
        add("happy_ghast ghastling", Ability.GUST, Ability.AIR_DASH, Ability.HEALING_PULSE,
                Trait.FLIGHT, Trait.SLOW_FALLING);
        add("evoker", Ability.MAGIC_FANGS, Ability.WITCH_BREW, Ability.UTILITY_PULSE,
                Trait.RESISTANCE);
        add("witch", Ability.WITCH_BREW, Ability.SPORE_CLOUD, Ability.UTILITY_PULSE);
        add("guardian", Ability.BEAM, Ability.POISON_BITE, Ability.AQUA_DASH,
                Trait.AQUATIC, Trait.RESISTANCE);
        add("breeze", Ability.GUST, Ability.CHARGE, Ability.AIR_DASH,
                Trait.SLOW_FALLING, Trait.JUMP);
        add("skeleton", Ability.RANGED_SHOT, Ability.SWORD_SLASH, Ability.FOX_DASH);
        add("stray", Ability.SNOWBURST, Ability.RANGED_SHOT, Ability.FOX_DASH,
                Trait.SLOW_FALLING);
        add("bogged", Ability.SPORE_CLOUD, Ability.RANGED_SHOT, Ability.FOX_DASH);
        add("parched", Ability.SAND_SHOT, Ability.RANGED_SHOT, Ability.CHARGE,
                Trait.SLOW_FALLING);
        add("wither_skeleton", Ability.WITHER_BARRAGE, Ability.SWORD_SLASH, Ability.CHARGE,
                Trait.FIRE_RESISTANCE, Trait.STRENGTH);
        add("pillager", Ability.RANGED_SHOT, Ability.SWORD_SLASH, Ability.CHARGE);
        add("vindicator", Ability.SWORD_SLASH, Ability.GROUND_SLAM, Ability.CHARGE,
                Trait.STRENGTH);
        add("piglin_brute", Ability.SWORD_SLASH, Ability.GROUND_SLAM, Ability.GUARD,
                Trait.STRENGTH, Trait.RESISTANCE);
        add("piglin", Ability.RANGED_SHOT, Ability.SWORD_SLASH, Ability.CHARGE,
                Trait.FIRE_RESISTANCE);
        add("drowned", Ability.RANGED_SHOT, Ability.POISON_BITE, Ability.AQUA_DASH,
                Trait.AQUATIC);
        add("shulker", Ability.SHULKER_LIFT, Ability.GUARD, Ability.PHASE_DASH,
                Trait.RESISTANCE);
        add("creeper", Ability.CREEPER_BLAST, Ability.GUST, Ability.CHARGE,
                Trait.RESISTANCE);

        // Teleportation, flight, climbing, and evasion.
        add("enderman", Ability.TELEPORT, Ability.DARK_BURST, Ability.PHASE_DASH,
                Trait.STRENGTH, Trait.RESISTANCE, Trait.NIGHT_VISION);
        add("phantom", Ability.CHARGE, Ability.DARK_BURST, Ability.AIR_DASH,
                Trait.FLIGHT, Trait.SLOW_FALLING, Trait.NIGHT_VISION);
        add("bat", Ability.ECHO_PULSE, Ability.AIR_DASH, Ability.PHASE_DASH,
                Trait.FLIGHT, Trait.NIGHT_VISION, Trait.SLOW_FALLING);
        add("spider", Ability.WEB_SNARE, Ability.POISON_BITE, Ability.CHARGE,
                Trait.WALL_CLIMB, Trait.JUMP);
        add("cave_spider", Ability.POISON_BITE, Ability.WEB_SNARE, Ability.CHARGE,
                Trait.WALL_CLIMB, Trait.JUMP, Trait.POISON_RESISTANCE);
        add("vex", Ability.PHASE_DASH, Ability.SWORD_SLASH, Ability.AIR_DASH,
                Trait.FLIGHT, Trait.SLOW_FALLING);
        add("allay", Ability.AIR_DASH, Ability.HEALING_PULSE, Ability.UTILITY_PULSE,
                Trait.FLIGHT, Trait.SLOW_FALLING);
        add("bee", Ability.BEE_STING, Ability.GUST, Ability.AIR_DASH,
                Trait.FLIGHT, Trait.SLOW_FALLING);
        add("parrot", Ability.AIR_DASH, Ability.GUST, Ability.ECHO_PULSE,
                Trait.FLIGHT, Trait.SLOW_FALLING);

        // Heavy chargers and body forms.
        add("goat", Ability.HEADBUTT, Ability.CHARGE, Ability.FROG_LEAP,
                Trait.JUMP, Trait.RESISTANCE);
        add("hoglin zoglin", Ability.CHARGE, Ability.SWORD_SLASH, Ability.GROUND_SLAM,
                Trait.STRENGTH, Trait.RESISTANCE);
        add("slime", Ability.BOUNCE, Ability.SLIME_SLAM, Ability.CHARGE,
                Trait.BOUNCY, Trait.JUMP, Trait.RESISTANCE);
        add("magma_cube", Ability.BOUNCE, Ability.FIRE_BURST, Ability.SLIME_SLAM,
                Trait.BOUNCY, Trait.JUMP, Trait.FIRE_RESISTANCE);
        add("creaking", Ability.ROOT_PULSE, Ability.GROUND_SLAM, Ability.GUARD,
                Trait.RESISTANCE);
        add("armadillo", Ability.ARMADILLO_ROLL, Ability.GUARD, Ability.CHARGE,
                Trait.RESISTANCE);
        add("panda", Ability.GROUND_SLAM, Ability.GUARD, Ability.CHARGE,
                Trait.STRENGTH, Trait.RESISTANCE);
        add("polar_bear", Ability.CHARGE, Ability.SWORD_SLASH, Ability.GUARD,
                Trait.STRENGTH, Trait.RESISTANCE);
        add("wolf", Ability.SWORD_SLASH, Ability.HOWL, Ability.CHARGE,
                Trait.SPEED, Trait.STRENGTH);
        add("zombie husk zombie_villager", Ability.INFECTION_BITE, Ability.SWORD_SLASH, Ability.CHARGE,
                Trait.STRENGTH);
        add("zombified_piglin", Ability.SWORD_SLASH, Ability.CHARGE, Ability.GROUND_SLAM,
                Trait.FIRE_RESISTANCE, Trait.STRENGTH);

        // Aquatic, lava, snow, and movement-focused forms.
        add("dolphin", Ability.AQUA_DASH, Ability.HEALING_PULSE, Ability.CHARGE,
                Trait.AQUATIC, Trait.SPEED);
        add("axolotl", Ability.HEALING_PULSE, Ability.POISON_BITE, Ability.AQUA_DASH,
                Trait.AQUATIC, Trait.REGENERATION);
        add("turtle", Ability.GUARD, Ability.AQUA_DASH, Ability.GROUND_SLAM,
                Trait.AQUATIC, Trait.RESISTANCE);
        add("nautilus", Ability.AQUA_DASH, Ability.CHARGE, Ability.GUARD,
                Trait.AQUATIC, Trait.RESISTANCE);
        add("zombie_nautilus", Ability.AQUA_DASH, Ability.POISON_BITE, Ability.CHARGE,
                Trait.AQUATIC, Trait.STRENGTH);
        add("strider", Ability.LAVA_DASH, Ability.GUARD, Ability.CHARGE,
                Trait.FIRE_RESISTANCE, Trait.LAVA_ADAPTED);
        add("snow_golem", Ability.SNOWBURST, Ability.SPORE_CLOUD, Ability.GUST,
                Trait.SLOW_FALLING);
        add("frog", Ability.FROG_LEAP, Ability.WEB_SNARE, Ability.CHARGE,
                Trait.JUMP, Trait.AQUATIC);
        add("camel camel_husk", Ability.EQUINE_DASH, Ability.GROUND_SLAM, Ability.GUARD,
                Trait.JUMP, Trait.RESISTANCE);
        add("horse donkey mule skeleton_horse", Ability.EQUINE_DASH,
                Ability.CHARGE, Ability.GUARD, Trait.SPEED, Trait.JUMP);
        add("fox", Ability.FOX_DASH, Ability.POISON_BITE, Ability.CHARGE,
                Trait.SPEED, Trait.SLOW_FALLING);
        add("cat ocelot", Ability.FOX_DASH, Ability.CHARGE, Ability.UTILITY_PULSE,
                Trait.SPEED, Trait.JUMP, Trait.SLOW_FALLING);
        // Small or passive mobs still receive purposeful utility/support forms.
        add("copper_golem", Ability.UTILITY_PULSE, Ability.GUARD, Ability.CHARGE,
                Trait.RESISTANCE);
        add("villager wandering_trader", Ability.TRADE_WINDS, Ability.HEALING_PULSE,
                Ability.UTILITY_PULSE, Trait.REGENERATION);
        add("trader_llama llama", Ability.GUST, Ability.CHARGE, Ability.GUARD,
                Trait.RESISTANCE);
        add("glow_squid squid", Ability.ECHO_PULSE, Ability.GUST, Ability.AQUA_DASH,
                Trait.AQUATIC, Trait.NIGHT_VISION);
        add("cod salmon tropical_fish", Ability.AQUA_DASH, Ability.GUST, Ability.UTILITY_PULSE,
                Trait.AQUATIC, Trait.SPEED);
        add("pufferfish", Ability.PUFFER_BURST, Ability.AQUA_DASH, Ability.GUARD,
                Trait.AQUATIC, Trait.POISON_RESISTANCE);
        add("chicken", Ability.FROG_LEAP, Ability.GUST, Ability.FOX_DASH,
                Trait.JUMP, Trait.SLOW_FALLING);
        add("cow mooshroom sheep pig", Ability.GROUND_SLAM, Ability.HEALING_PULSE,
                Ability.CHARGE, Trait.RESISTANCE);
        add("rabbit", Ability.FOX_DASH, Ability.FROG_LEAP, Ability.CHARGE,
                Trait.SPEED, Trait.JUMP);
        add("sniffer", Ability.ROOT_PULSE, Ability.UTILITY_PULSE, Ability.GROUND_SLAM,
                Trait.RESISTANCE);
        add("silverfish endermite", Ability.POISON_BITE, Ability.PHASE_DASH, Ability.CHARGE,
                Trait.SPEED);
        add("illusioner", Ability.PHASE_DASH, Ability.RANGED_SHOT, Ability.DARK_BURST,
                Trait.SLOW_FALLING, Trait.NIGHT_VISION);
        add("tadpole", Ability.AQUA_DASH, Ability.GUST, Ability.UTILITY_PULSE,
                Trait.AQUATIC, Trait.SPEED);
        add("zombie_horse", Ability.EQUINE_DASH, Ability.INFECTION_BITE, Ability.GUARD,
                Trait.SPEED, Trait.JUMP);

        // Build the picker from Minecraft's actual runtime registry instead of relying on a
        // manually maintained list. This automatically includes all vanilla mob entity types
        // present in 1.21.11 and excludes fake IDs or entities from other namespaces.
        Set<String> registeredVanillaMobs = new LinkedHashSet<>();
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            if (id == null || !"minecraft".equals(id.getNamespace())) continue;
            if (!MobEntity.class.isAssignableFrom(type.getBaseClass())) continue;
            registeredVanillaMobs.add(id.getPath());
        }
        VANILLA_MOB_IDS.clear();
        VANILLA_MOB_IDS.addAll(registeredVanillaMobs);
        VANILLA_MOB_IDS.sort(String.CASE_INSENSITIVE_ORDER);
        PROFILES.keySet().retainAll(registeredVanillaMobs);
        for (String id : VANILLA_MOB_IDS) {
            PROFILES.putIfAbsent(id, genericProfile(id));
        }
    }

    private ShadowArcherMorphRegistry() {}

    private static void add(String ids, Ability primary, Ability secondary, Ability alternate, Trait... traits) {
        for (String id : ids.trim().split("\\s+")) {
            if (id.isBlank()) continue;
            PROFILES.put(id, new Profile(id, titleCase(id), "Shadow Archer form: " + titleCase(id),
                    primary, secondary, alternate, new LinkedHashSet<>(Arrays.asList(traits))));
        }
    }

    public static Profile forEntityType(EntityType<?> type) {
        Identifier id = Registries.ENTITY_TYPE.getId(type);
        if (id == null) return genericProfile("unknown_mob");
        return forId(id.getPath());
    }

    public static Profile forId(String rawId) {
        String id = normalize(rawId);
        Profile profile = PROFILES.get(id);
        return profile != null ? profile : genericProfile(id.isBlank() ? "unknown_mob" : id);
    }

    /** Returns a profile only when user input precisely identifies a roster item. */
    public static Profile findProfileForInput(String rawInput) {
        String normalized = normalize(rawInput);
        if (normalized.isBlank()) return null;
        Profile exact = PROFILES.get(normalized);
        if (exact != null) return exact;
        for (Profile profile : PROFILES.values()) {
            if (profile.displayName().equalsIgnoreCase(rawInput.trim())) return profile;
        }
        return null;
    }

    public static List<String> getVanillaFormIds() {
        return List.copyOf(VANILLA_MOB_IDS);
    }

    public static List<String> getProfileIds() {
        return List.copyOf(PROFILES.keySet());
    }

    private static Profile genericProfile(String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (id.contains("fish") || id.contains("squid") || id.contains("nautilus")
                || id.contains("axolotl") || id.contains("dolphin") || id.contains("turtle")) {
            return new Profile(id, titleCase(id), "Aquatic adaptation", Ability.AQUA_DASH,
                    Ability.GUST, Ability.HEALING_PULSE, Set.of(Trait.AQUATIC, Trait.SPEED));
        }
        if (id.contains("horse") || id.contains("camel") || id.contains("llama")) {
            return new Profile(id, titleCase(id), "Mount-inspired movement", Ability.EQUINE_DASH,
                    Ability.CHARGE, Ability.GUARD, Set.of(Trait.SPEED, Trait.JUMP));
        }
        if (id.contains("skeleton") || id.contains("pillager") || id.contains("archer")) {
            return new Profile(id, titleCase(id), "Ranged combat adaptation", Ability.RANGED_SHOT,
                    Ability.SWORD_SLASH, Ability.FOX_DASH, Set.of(Trait.SLOW_FALLING));
        }
        if (id.contains("spider") || id.contains("arthropod")) {
            return new Profile(id, titleCase(id), "Agile crawler adaptation", Ability.POISON_BITE,
                    Ability.WEB_SNARE, Ability.CHARGE, Set.of(Trait.WALL_CLIMB, Trait.SPEED));
        }
        return new Profile(id, titleCase(id), "Adapted mob form", Ability.SWORD_SLASH,
                Ability.GUARD, Ability.CHARGE, Set.of(Trait.RESISTANCE));
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String text = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (text.startsWith("minecraft:")) text = text.substring("minecraft:".length());
        return text;
    }

    private static String titleCase(String id) {
        StringBuilder result = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) result.append(part.substring(1));
        }
        return result.toString();
    }
}
