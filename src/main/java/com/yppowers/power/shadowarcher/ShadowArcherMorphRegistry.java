package com.yppowers.power.shadowarcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShadowArcherMorphRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Shadow Archer Catalog");
   private static final Map<String, ShadowArcherMorphRegistry.Profile> PROFILES = new ConcurrentHashMap<>();
   private static volatile List<String> vanillaMobIds = List.of();
   private static volatile boolean vanillaRosterInitialized;
   private static boolean registryScanWarningLogged;

   private ShadowArcherMorphRegistry() {
   }

   private static synchronized void ensureVanillaRosterInitialized() {
      if (!vanillaRosterInitialized) {
         Set<String> registeredVanillaMobs = new LinkedHashSet<>();

         try {
            for (EntityType<?> type : Registries.ENTITY_TYPE) {
               try {
                  Identifier id = Registries.ENTITY_TYPE.getId(type);
                  if (id != null && "minecraft".equals(id.getNamespace()) && MobEntity.class.isAssignableFrom(type.getBaseClass())) {
                     registeredVanillaMobs.add(id.getPath());
                  }
               } catch (LinkageError | RuntimeException var4) {
                  if (!registryScanWarningLogged) {
                     LOGGER.warn("Skipping one entity type while building the Shadow Archer menu", var4);
                     registryScanWarningLogged = true;
                  }
               }
            }
         } catch (LinkageError | RuntimeException var5) {
            if (!registryScanWarningLogged) {
               LOGGER.error("Could not fully read the entity registry for the Shadow Archer menu; using its safe fallback roster", var5);
               registryScanWarningLogged = true;
            }
         }

         if (!registeredVanillaMobs.isEmpty()) {
            List<String> sorted = new ArrayList<>(registeredVanillaMobs);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            PROFILES.keySet().retainAll(registeredVanillaMobs);

            for (String idx : sorted) {
               PROFILES.putIfAbsent(idx, genericProfile(idx));
            }

            vanillaMobIds = List.copyOf(sorted);
            vanillaRosterInitialized = true;
         } else {
            if (vanillaMobIds.isEmpty()) {
               List<String> fallback = new ArrayList<>(PROFILES.keySet());
               fallback.sort(String.CASE_INSENSITIVE_ORDER);
               vanillaMobIds = List.copyOf(fallback);
            }

            vanillaRosterInitialized = false;
         }
      }
   }

   private static void add(
      String ids,
      ShadowArcherMorphRegistry.Ability primary,
      ShadowArcherMorphRegistry.Ability secondary,
      ShadowArcherMorphRegistry.Ability alternate,
      ShadowArcherMorphRegistry.Trait... traits
   ) {
      for (String id : ids.trim().split("\\s+")) {
         if (!id.isBlank()) {
            PROFILES.put(
               id,
               new ShadowArcherMorphRegistry.Profile(
                  id, titleCase(id), "Shadow Archer form: " + titleCase(id), primary, secondary, alternate, new LinkedHashSet<>(Arrays.asList(traits))
               )
            );
         }
      }
   }

   public static ShadowArcherMorphRegistry.Profile forEntityType(EntityType<?> type) {
      Identifier id = Registries.ENTITY_TYPE.getId(type);
      return id == null ? genericProfile("unknown_mob") : forId(id.getPath());
   }

   public static ShadowArcherMorphRegistry.Profile forId(String rawId) {
      String id = normalize(rawId);
      ShadowArcherMorphRegistry.Profile profile = PROFILES.get(id);
      return profile != null ? profile : genericProfile(id.isBlank() ? "unknown_mob" : id);
   }

   public static ShadowArcherMorphRegistry.Profile findProfileForInput(String rawInput) {
      String normalized = normalize(rawInput);
      if (normalized.isBlank()) {
         return null;
      } else {
         ShadowArcherMorphRegistry.Profile exact = PROFILES.get(normalized);
         if (exact != null) {
            return exact;
         } else {
            for (ShadowArcherMorphRegistry.Profile profile : PROFILES.values()) {
               if (profile.displayName().equalsIgnoreCase(rawInput.trim())) {
                  return profile;
               }
            }

            return null;
         }
      }
   }

   public static List<String> getVanillaFormIds() {
      ensureVanillaRosterInitialized();
      return vanillaMobIds;
   }

   public static List<String> getProfileIds() {
      ensureVanillaRosterInitialized();
      return List.copyOf(PROFILES.keySet());
   }

   private static ShadowArcherMorphRegistry.Profile genericProfile(String rawId) {
      String id = rawId.toLowerCase(Locale.ROOT).replace(' ', '_');
      if (id.contains("fish") || id.contains("squid") || id.contains("nautilus") || id.contains("axolotl") || id.contains("dolphin") || id.contains("turtle")) {
         return new ShadowArcherMorphRegistry.Profile(
            id,
            titleCase(id),
            "Aquatic adaptation",
            ShadowArcherMorphRegistry.Ability.AQUA_DASH,
            ShadowArcherMorphRegistry.Ability.GUST,
            ShadowArcherMorphRegistry.Ability.HEALING_PULSE,
            Set.of(ShadowArcherMorphRegistry.Trait.AQUATIC, ShadowArcherMorphRegistry.Trait.SPEED)
         );
      } else if (id.contains("horse") || id.contains("camel") || id.contains("llama")) {
         return new ShadowArcherMorphRegistry.Profile(
            id,
            titleCase(id),
            "Mount-inspired movement",
            ShadowArcherMorphRegistry.Ability.EQUINE_DASH,
            ShadowArcherMorphRegistry.Ability.CHARGE,
            ShadowArcherMorphRegistry.Ability.GUARD,
            Set.of(ShadowArcherMorphRegistry.Trait.SPEED, ShadowArcherMorphRegistry.Trait.JUMP)
         );
      } else if (id.contains("skeleton") || id.contains("pillager") || id.contains("archer")) {
         return new ShadowArcherMorphRegistry.Profile(
            id,
            titleCase(id),
            "Ranged combat adaptation",
            ShadowArcherMorphRegistry.Ability.RANGED_SHOT,
            ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
            ShadowArcherMorphRegistry.Ability.FOX_DASH,
            Set.of(ShadowArcherMorphRegistry.Trait.SLOW_FALLING)
         );
      } else {
         return !id.contains("spider") && !id.contains("arthropod")
            ? new ShadowArcherMorphRegistry.Profile(
               id,
               titleCase(id),
               "Adapted mob form",
               ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
               ShadowArcherMorphRegistry.Ability.GUARD,
               ShadowArcherMorphRegistry.Ability.CHARGE,
               Set.of(ShadowArcherMorphRegistry.Trait.RESISTANCE)
            )
            : new ShadowArcherMorphRegistry.Profile(
               id,
               titleCase(id),
               "Agile crawler adaptation",
               ShadowArcherMorphRegistry.Ability.POISON_BITE,
               ShadowArcherMorphRegistry.Ability.WEB_SNARE,
               ShadowArcherMorphRegistry.Ability.CHARGE,
               Set.of(ShadowArcherMorphRegistry.Trait.WALL_CLIMB, ShadowArcherMorphRegistry.Trait.SPEED)
            );
      }
   }

   private static String normalize(String raw) {
      if (raw == null) {
         return "";
      } else {
         String text = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
         if (text.startsWith("minecraft:")) {
            text = text.substring("minecraft:".length());
         }

         return text;
      }
   }

   private static String titleCase(String id) {
      StringBuilder result = new StringBuilder();

      for (String part : id.split("_")) {
         if (!part.isBlank()) {
            if (!result.isEmpty()) {
               result.append(' ');
            }

            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
               result.append(part.substring(1));
            }
         }
      }

      return result.toString();
   }

   static {
      add(
         "ender_dragon",
         ShadowArcherMorphRegistry.Ability.DRAGON_BREATH,
         ShadowArcherMorphRegistry.Ability.DRAGON_ROAR,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.RESISTANCE,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "wither",
         ShadowArcherMorphRegistry.Ability.WITHER_BARRAGE,
         ShadowArcherMorphRegistry.Ability.WITHER_BLAST,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.RESISTANCE,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE
      );
      add(
         "warden",
         ShadowArcherMorphRegistry.Ability.SONIC_BOOM,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.ECHO_PULSE,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE,
         ShadowArcherMorphRegistry.Trait.NIGHT_VISION
      );
      add(
         "elder_guardian",
         ShadowArcherMorphRegistry.Ability.BEAM,
         ShadowArcherMorphRegistry.Ability.SPORE_CLOUD,
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "ravager",
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "iron_golem",
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "giant",
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "blaze",
         ShadowArcherMorphRegistry.Ability.FIRE_BLAST,
         ShadowArcherMorphRegistry.Ability.FIRE_BURST,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE
      );
      add(
         "ghast",
         ShadowArcherMorphRegistry.Ability.FIRE_BLAST,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "happy_ghast ghastling",
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Ability.HEALING_PULSE,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "evoker",
         ShadowArcherMorphRegistry.Ability.MAGIC_FANGS,
         ShadowArcherMorphRegistry.Ability.WITCH_BREW,
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add("witch", ShadowArcherMorphRegistry.Ability.WITCH_BREW, ShadowArcherMorphRegistry.Ability.SPORE_CLOUD, ShadowArcherMorphRegistry.Ability.UTILITY_PULSE);
      add(
         "guardian",
         ShadowArcherMorphRegistry.Ability.BEAM,
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "breeze",
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING,
         ShadowArcherMorphRegistry.Trait.JUMP
      );
      add("skeleton", ShadowArcherMorphRegistry.Ability.RANGED_SHOT, ShadowArcherMorphRegistry.Ability.SWORD_SLASH, ShadowArcherMorphRegistry.Ability.FOX_DASH);
      add(
         "stray",
         ShadowArcherMorphRegistry.Ability.SNOWBURST,
         ShadowArcherMorphRegistry.Ability.RANGED_SHOT,
         ShadowArcherMorphRegistry.Ability.FOX_DASH,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add("bogged", ShadowArcherMorphRegistry.Ability.SPORE_CLOUD, ShadowArcherMorphRegistry.Ability.RANGED_SHOT, ShadowArcherMorphRegistry.Ability.FOX_DASH);
      add(
         "parched",
         ShadowArcherMorphRegistry.Ability.SAND_SHOT,
         ShadowArcherMorphRegistry.Ability.RANGED_SHOT,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "wither_skeleton",
         ShadowArcherMorphRegistry.Ability.WITHER_BARRAGE,
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE,
         ShadowArcherMorphRegistry.Trait.STRENGTH
      );
      add("pillager", ShadowArcherMorphRegistry.Ability.RANGED_SHOT, ShadowArcherMorphRegistry.Ability.SWORD_SLASH, ShadowArcherMorphRegistry.Ability.CHARGE);
      add(
         "vindicator",
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.STRENGTH
      );
      add(
         "piglin_brute",
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "piglin",
         ShadowArcherMorphRegistry.Ability.RANGED_SHOT,
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE
      );
      add(
         "drowned",
         ShadowArcherMorphRegistry.Ability.RANGED_SHOT,
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Trait.AQUATIC
      );
      add(
         "shulker",
         ShadowArcherMorphRegistry.Ability.SHULKER_LIFT,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Ability.PHASE_DASH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "creeper",
         ShadowArcherMorphRegistry.Ability.CREEPER_BLAST,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "enderman",
         ShadowArcherMorphRegistry.Ability.TELEPORT,
         ShadowArcherMorphRegistry.Ability.DARK_BURST,
         ShadowArcherMorphRegistry.Ability.PHASE_DASH,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE,
         ShadowArcherMorphRegistry.Trait.NIGHT_VISION
      );
      add(
         "phantom",
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.DARK_BURST,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING,
         ShadowArcherMorphRegistry.Trait.NIGHT_VISION
      );
      add(
         "bat",
         ShadowArcherMorphRegistry.Ability.ECHO_PULSE,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Ability.PHASE_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.NIGHT_VISION,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "spider",
         ShadowArcherMorphRegistry.Ability.WEB_SNARE,
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.WALL_CLIMB,
         ShadowArcherMorphRegistry.Trait.JUMP
      );
      add(
         "cave_spider",
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.WEB_SNARE,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.WALL_CLIMB,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.POISON_RESISTANCE
      );
      add(
         "vex",
         ShadowArcherMorphRegistry.Ability.PHASE_DASH,
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "allay",
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Ability.HEALING_PULSE,
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "bee",
         ShadowArcherMorphRegistry.Ability.BEE_STING,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "parrot",
         ShadowArcherMorphRegistry.Ability.AIR_DASH,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.ECHO_PULSE,
         ShadowArcherMorphRegistry.Trait.FLIGHT,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "goat",
         ShadowArcherMorphRegistry.Ability.HEADBUTT,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.FROG_LEAP,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "hoglin zoglin",
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "slime",
         ShadowArcherMorphRegistry.Ability.BOUNCE,
         ShadowArcherMorphRegistry.Ability.SLIME_SLAM,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.BOUNCY,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "magma_cube",
         ShadowArcherMorphRegistry.Ability.BOUNCE,
         ShadowArcherMorphRegistry.Ability.FIRE_BURST,
         ShadowArcherMorphRegistry.Ability.SLIME_SLAM,
         ShadowArcherMorphRegistry.Trait.BOUNCY,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE
      );
      add(
         "creaking",
         ShadowArcherMorphRegistry.Ability.ROOT_PULSE,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "armadillo",
         ShadowArcherMorphRegistry.Ability.ARMADILLO_ROLL,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "panda",
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "polar_bear",
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.STRENGTH,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "wolf",
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.HOWL,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.SPEED,
         ShadowArcherMorphRegistry.Trait.STRENGTH
      );
      add(
         "zombie husk zombie_villager",
         ShadowArcherMorphRegistry.Ability.INFECTION_BITE,
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.STRENGTH
      );
      add(
         "zombified_piglin",
         ShadowArcherMorphRegistry.Ability.SWORD_SLASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE,
         ShadowArcherMorphRegistry.Trait.STRENGTH
      );
      add(
         "dolphin",
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Ability.HEALING_PULSE,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.SPEED
      );
      add(
         "axolotl",
         ShadowArcherMorphRegistry.Ability.HEALING_PULSE,
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.REGENERATION
      );
      add(
         "turtle",
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "nautilus",
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "zombie_nautilus",
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.STRENGTH
      );
      add(
         "strider",
         ShadowArcherMorphRegistry.Ability.LAVA_DASH,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE,
         ShadowArcherMorphRegistry.Trait.LAVA_ADAPTED
      );
      add(
         "snow_golem",
         ShadowArcherMorphRegistry.Ability.SNOWBURST,
         ShadowArcherMorphRegistry.Ability.SPORE_CLOUD,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "frog",
         ShadowArcherMorphRegistry.Ability.FROG_LEAP,
         ShadowArcherMorphRegistry.Ability.WEB_SNARE,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.AQUATIC
      );
      add(
         "camel camel_husk",
         ShadowArcherMorphRegistry.Ability.EQUINE_DASH,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "horse donkey mule skeleton_horse",
         ShadowArcherMorphRegistry.Ability.EQUINE_DASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.SPEED,
         ShadowArcherMorphRegistry.Trait.JUMP
      );
      add(
         "fox",
         ShadowArcherMorphRegistry.Ability.FOX_DASH,
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.SPEED,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "cat ocelot",
         ShadowArcherMorphRegistry.Ability.FOX_DASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Trait.SPEED,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "copper_golem",
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "villager wandering_trader",
         ShadowArcherMorphRegistry.Ability.TRADE_WINDS,
         ShadowArcherMorphRegistry.Ability.HEALING_PULSE,
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Trait.REGENERATION
      );
      add(
         "trader_llama llama",
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "glow_squid squid",
         ShadowArcherMorphRegistry.Ability.ECHO_PULSE,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.NIGHT_VISION
      );
      add(
         "cod salmon tropical_fish",
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.SPEED
      );
      add(
         "pufferfish",
         ShadowArcherMorphRegistry.Ability.PUFFER_BURST,
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.POISON_RESISTANCE
      );
      add(
         "chicken",
         ShadowArcherMorphRegistry.Ability.FROG_LEAP,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.FOX_DASH,
         ShadowArcherMorphRegistry.Trait.JUMP,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING
      );
      add(
         "cow mooshroom sheep pig",
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Ability.HEALING_PULSE,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "rabbit",
         ShadowArcherMorphRegistry.Ability.FOX_DASH,
         ShadowArcherMorphRegistry.Ability.FROG_LEAP,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.SPEED,
         ShadowArcherMorphRegistry.Trait.JUMP
      );
      add(
         "sniffer",
         ShadowArcherMorphRegistry.Ability.ROOT_PULSE,
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Ability.GROUND_SLAM,
         ShadowArcherMorphRegistry.Trait.RESISTANCE
      );
      add(
         "silverfish endermite",
         ShadowArcherMorphRegistry.Ability.POISON_BITE,
         ShadowArcherMorphRegistry.Ability.PHASE_DASH,
         ShadowArcherMorphRegistry.Ability.CHARGE,
         ShadowArcherMorphRegistry.Trait.SPEED
      );
      add(
         "illusioner",
         ShadowArcherMorphRegistry.Ability.PHASE_DASH,
         ShadowArcherMorphRegistry.Ability.RANGED_SHOT,
         ShadowArcherMorphRegistry.Ability.DARK_BURST,
         ShadowArcherMorphRegistry.Trait.SLOW_FALLING,
         ShadowArcherMorphRegistry.Trait.NIGHT_VISION
      );
      add(
         "tadpole",
         ShadowArcherMorphRegistry.Ability.AQUA_DASH,
         ShadowArcherMorphRegistry.Ability.GUST,
         ShadowArcherMorphRegistry.Ability.UTILITY_PULSE,
         ShadowArcherMorphRegistry.Trait.AQUATIC,
         ShadowArcherMorphRegistry.Trait.SPEED
      );
      add(
         "zombie_horse",
         ShadowArcherMorphRegistry.Ability.EQUINE_DASH,
         ShadowArcherMorphRegistry.Ability.INFECTION_BITE,
         ShadowArcherMorphRegistry.Ability.GUARD,
         ShadowArcherMorphRegistry.Trait.SPEED,
         ShadowArcherMorphRegistry.Trait.JUMP
      );
   }

   public static enum Ability {
      NONE("None"),
      HEADBUTT("Headbutt"),
      DRAGON_BREATH("Dragon Breath"),
      DRAGON_ROAR("Dragon Roar"),
      WITHER_BARRAGE("Wither Barrage"),
      WITHER_BLAST("Wither Blast"),
      SONIC_BOOM("Sonic Boom"),
      GROUND_SLAM("Ground Slam"),
      GUARD("Guard"),
      BEAM("Guardian Beam"),
      FIRE_BLAST("Fire Blast"),
      FIRE_BURST("Flame Burst"),
      MAGIC_FANGS("Evoker Fangs"),
      WITCH_BREW("Witch's Brew"),
      TELEPORT("Teleport"),
      DARK_BURST("Shadow Burst"),
      WEB_SNARE("Web Snare"),
      POISON_BITE("Poison Bite"),
      PHASE_DASH("Phase Dash"),
      AIR_DASH("Air Dash"),
      GUST("Gust"),
      CHARGE("Charge"),
      CREEPER_BLAST("Controlled Blast"),
      BOUNCE("Super Bounce"),
      SPORE_CLOUD("Spore Cloud"),
      AQUA_DASH("Aquatic Dash"),
      SNOWBURST("Snowburst"),
      FROG_LEAP("Frog Leap"),
      FOX_DASH("Fox Dash"),
      HOWL("Pack Howl"),
      EQUINE_DASH("Equine Dash"),
      UTILITY_PULSE("Utility Pulse"),
      SHULKER_LIFT("Levitation Shot"),
      RANGED_SHOT("Ranged Shot"),
      SWORD_SLASH("Melee Slash"),
      ROOT_PULSE("Creaking Root Pulse"),
      HEALING_PULSE("Healing Pulse"),
      PUFFER_BURST("Puffer Burst"),
      ARMADILLO_ROLL("Armadillo Roll"),
      DEEP_DASH("Deep Dash"),
      TRADE_WINDS("Trade Winds"),
      LAVA_DASH("Lava Dash"),
      BEE_STING("Venom Sting"),
      SLIME_SLAM("Slime Slam"),
      ECHO_PULSE("Echo Pulse"),
      INFECTION_BITE("Infection Bite"),
      SAND_SHOT("Sand Shot");

      private final String displayName;

      private Ability(String displayName) {
         this.displayName = displayName;
      }

      public String displayName() {
         return this.displayName;
      }
   }

   public record Profile(
      String id,
      String displayName,
      String summary,
      ShadowArcherMorphRegistry.Ability primary,
      ShadowArcherMorphRegistry.Ability secondary,
      ShadowArcherMorphRegistry.Ability alternate,
      Set<ShadowArcherMorphRegistry.Trait> traits
   ) {
      public Profile(
         String id,
         String displayName,
         String summary,
         ShadowArcherMorphRegistry.Ability primary,
         ShadowArcherMorphRegistry.Ability secondary,
         ShadowArcherMorphRegistry.Ability alternate,
         Set<ShadowArcherMorphRegistry.Trait> traits
      ) {
         traits = Collections.unmodifiableSet(new LinkedHashSet<>(traits));
         this.id = id;
         this.displayName = displayName;
         this.summary = summary;
         this.primary = primary;
         this.secondary = secondary;
         this.alternate = alternate;
         this.traits = traits;
      }

      public String abilitySummary() {
         return "G: " + this.primary.displayName() + "  |  H: " + this.secondary.displayName() + "  |  R: " + this.alternate.displayName();
      }

      public String traitSummary() {
         return this.traits.isEmpty()
            ? "Traits: standard player movement"
            : "Traits: " + String.join(", ", this.traits.stream().map(ShadowArcherMorphRegistry.Trait::displayName).toList());
      }
   }

   public static enum Trait {
      FLIGHT("Flight"),
      WALL_CLIMB("Wall climbing"),
      FIRE_RESISTANCE("Fire resistance"),
      AQUATIC("Water breathing and faster swimming"),
      SPEED("Enhanced speed"),
      STRENGTH("Enhanced melee strength"),
      JUMP("Enhanced jumping"),
      RESISTANCE("Damage resistance"),
      SLOW_FALLING("Slow falling"),
      NIGHT_VISION("Night vision"),
      REGENERATION("Slow regeneration"),
      POISON_RESISTANCE("Poison resistance"),
      BOUNCY("Bouncy movement"),
      LAVA_ADAPTED("Lava adaptation");

      private final String displayName;

      private Trait(String displayName) {
         this.displayName = displayName;
      }

      public String displayName() {
         return this.displayName;
      }
   }
}
