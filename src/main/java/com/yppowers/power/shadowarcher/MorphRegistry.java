package com.yppowers.power.shadowarcher;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured catalog of Shadow Archer mob morphs, categories, movement traits,
 * camera heights, and form-specific ability descriptions.
 */
public final class MorphRegistry {
    public enum Category {
        ALL("All"),
        FLYING("Flying"),
        AQUATIC("Aquatic"),
        NETHER("Nether"),
        OVERWORLD("Overworld"),
        BOSS("Boss");

        public final String label;
        Category(String label) { this.label = label; }
    }

    public record MorphProfile(
            String entityId,
            String displayName,
            Category category,
            boolean canFly,
            float flightSpeed,
            boolean spiderClimb,
            boolean aquatic,
            boolean lavaStrider,
            float eyeHeight,
            float entityHeight,
            String primaryDesc,
            String secondaryDesc
    ) {}

    private static final Map<String, MorphProfile> PROFILES = new LinkedHashMap<>();

    static {
        // Flying
        add("minecraft:blaze", "Blaze", Category.FLYING, true, 0.06f, false, false, false, 1.5f, 1.8f, "Fireball Volley", "Flame Dash");
        add("minecraft:ghast", "Ghast", Category.FLYING, true, 0.055f, false, false, false, 2.4f, 4.0f, "Ghast Fireball", "Wail Knockback");
        add("minecraft:phantom", "Phantom", Category.FLYING, true, 0.075f, false, false, false, 0.6f, 0.9f, "Swoop Strike", "Night Vision");
        add("minecraft:bee", "Bee", Category.FLYING, true, 0.06f, false, false, false, 0.5f, 0.6f, "Poison Sting", "Pollen Heal");
        add("minecraft:bat", "Bat", Category.FLYING, true, 0.065f, false, false, false, 0.5f, 0.9f, "Sonic Screech", "Night Vision");
        add("minecraft:allay", "Allay", Category.FLYING, true, 0.065f, false, false, false, 0.5f, 0.6f, "Heal Note", "Speed Pulse");
        add("minecraft:vex", "Vex", Category.FLYING, true, 0.07f, false, false, false, 0.6f, 0.8f, "Phase Dash", "Spectral Slash");
        add("minecraft:parrot", "Parrot", Category.FLYING, true, 0.06f, false, false, false, 0.6f, 0.9f, "Mimic Pulse", "Feather Leap");
        add("minecraft:breeze", "Breeze", Category.FLYING, true, 0.065f, false, false, false, 1.3f, 1.77f, "Wind Charge", "Gale Leap");

        // Aquatic
        add("minecraft:dolphin", "Dolphin", Category.AQUATIC, false, 0f, false, true, false, 0.5f, 0.6f, "Hydro Dash", "Grace Aura");
        add("minecraft:guardian", "Guardian", Category.AQUATIC, false, 0f, false, true, false, 0.7f, 0.85f, "Laser Beam", "Spike Burst");
        add("minecraft:axolotl", "Axolotl", Category.AQUATIC, false, 0f, false, true, false, 0.4f, 0.5f, "Play Dead Heal", "Hydro Dart");
        add("minecraft:squid", "Squid", Category.AQUATIC, false, 0f, false, true, false, 0.6f, 0.8f, "Ink Cloud", "Jet Boost");
        add("minecraft:glow_squid", "Glow Squid", Category.AQUATIC, false, 0f, false, true, false, 0.6f, 0.8f, "Dazzle Flash", "Jet Boost");
        add("minecraft:drowned", "Drowned", Category.AQUATIC, false, 0f, false, true, false, 1.6f, 1.95f, "Trident Surge", "Riptide Lunge");
        add("minecraft:turtle", "Turtle", Category.AQUATIC, false, 0f, false, true, false, 0.4f, 0.5f, "Shell Guard", "Swim Burst");

        // Nether
        add("minecraft:wither_skeleton", "Wither Skeleton", Category.NETHER, false, 0f, false, false, false, 2.0f, 2.4f, "Wither Slash", "Bone Shatter");
        add("minecraft:piglin_brute", "Piglin Brute", Category.NETHER, false, 0f, false, false, false, 1.6f, 1.95f, "Gold Cleave", "War Cry");
        add("minecraft:hoglin", "Hoglin", Category.NETHER, false, 0f, false, false, false, 1.1f, 1.4f, "Tusk Charge", "Ground Slam");
        add("minecraft:magma_cube", "Magma Cube", Category.NETHER, false, 0f, false, false, false, 1.2f, 2.0f, "Magma Slam", "Fire Ring");
        add("minecraft:strider", "Strider", Category.NETHER, false, 0f, false, false, true, 1.4f, 1.7f, "Lava Sprint", "Heat Wave");

        // Overworld
        add("minecraft:enderman", "Enderman", Category.OVERWORLD, false, 0f, false, false, false, 2.4f, 2.9f, "Blink Teleport", "Void Scream");
        add("minecraft:creeper", "Creeper", Category.OVERWORLD, false, 0f, false, false, false, 1.4f, 1.7f, "Controlled Blast", "Charge Shield");
        add("minecraft:spider", "Spider", Category.OVERWORLD, false, 0f, true, false, false, 0.65f, 0.9f, "Web Shot", "Pounce Leap");
        add("minecraft:cave_spider", "Cave Spider", Category.OVERWORLD, false, 0f, true, false, false, 0.45f, 0.5f, "Venom Fang", "Web Leap");
        add("minecraft:skeleton", "Skeleton", Category.OVERWORLD, false, 0f, false, false, false, 1.6f, 1.99f, "Rapid Volley", "Backstep");
        add("minecraft:zombie", "Zombie", Category.OVERWORLD, false, 0f, false, false, false, 1.6f, 1.95f, "Relentless Slam", "Undead Resilience");
        add("minecraft:iron_golem", "Iron Golem", Category.OVERWORLD, false, 0f, false, false, false, 2.3f, 2.7f, "Skyward Toss", "Iron Fortress");
        add("minecraft:ravager", "Ravager", Category.OVERWORLD, false, 0f, false, false, false, 1.8f, 2.2f, "Ravager Roar", "Horn Charge");
        add("minecraft:evoker", "Evoker", Category.OVERWORLD, false, 0f, false, false, false, 1.6f, 1.95f, "Fang Line", "Arcane Ward");
        add("minecraft:shulker", "Shulker", Category.OVERWORLD, false, 0f, false, false, false, 0.7f, 1.0f, "Levitation Bullet", "Shell Armor");
        add("minecraft:slime", "Slime", Category.OVERWORLD, false, 0f, false, false, false, 1.2f, 2.0f, "Bounce Slam", "Gel Cushion");
        add("minecraft:wolf", "Wolf", Category.OVERWORLD, false, 0f, false, false, false, 0.68f, 0.85f, "Pack Howl", "Bite Lunge");
        add("minecraft:fox", "Fox", Category.OVERWORLD, false, 0f, false, false, false, 0.45f, 0.7f, "Swift Pounce", "Silent Step");
        add("minecraft:witch", "Witch", Category.OVERWORLD, false, 0f, false, false, false, 1.6f, 1.95f, "Harming Brew", "Healing Brew");

        // Bosses
        add("minecraft:warden", "Warden", Category.BOSS, false, 0f, false, false, false, 2.4f, 2.9f, "Sonic Boom", "Darkness Pulse");
        add("minecraft:wither", "Wither", Category.BOSS, true, 0.08f, false, false, false, 2.4f, 3.5f, "Wither Skull", "Wither Nova");
        add("minecraft:elder_guardian", "Elder Guardian", Category.BOSS, false, 0f, false, true, false, 1.6f, 2.0f, "Elder Beam", "Fatigue Curse");
        add("minecraft:ender_dragon", "Ender Dragon", Category.BOSS, true, 0.09f, false, false, false, 2.4f, 4.0f, "Dragon Breath", "Wing Gust");
    }

    private MorphRegistry() {}

    private static void add(String id, String name, Category cat, boolean fly, float flySpeed,
                            boolean climb, boolean aquatic, boolean strider, float eyeH, float entH,
                            String gDesc, String hDesc) {
        PROFILES.put(id, new MorphProfile(id, name, cat, fly, flySpeed, climb, aquatic, strider, eyeH, entH, gDesc, hDesc));
    }

    public static Collection<MorphProfile> all() {
        return PROFILES.values();
    }

    public static MorphProfile get(String entityId) {
        if (entityId == null || entityId.isBlank()) return null;
        MorphProfile direct = PROFILES.get(entityId);
        if (direct != null) return direct;
        // Dynamic fallback so ANY killed vanilla mob can still be morphed smoothly!
        Identifier id = Identifier.tryParse(entityId);
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) return null;
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        float h = Math.max(0.5f, Math.min(3.5f, type.getHeight()));
        float eye = Math.max(0.45f, Math.min(2.4f, h * 0.85f));
        String pretty = formatName(id.getPath());
        return new MorphProfile(entityId, pretty, Category.OVERWORLD, false, 0f, false, false, false, eye, h, "Feral Lunge", "Adaptive Surge");
    }

    public static List<MorphProfile> filter(Category category, String query, Collection<String> unlockedIds) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<MorphProfile> out = new ArrayList<>();
        Map<String, MorphProfile> combined = new LinkedHashMap<>(PROFILES);
        if (unlockedIds != null) {
            for (String uid : unlockedIds) {
                if (!combined.containsKey(uid)) {
                    MorphProfile p = get(uid);
                    if (p != null) combined.put(uid, p);
                }
            }
        }
        for (MorphProfile p : combined.values()) {
            if (category != Category.ALL && p.category() != category) continue;
            if (!q.isEmpty() && !p.displayName().toLowerCase().contains(q) && !p.entityId().toLowerCase().contains(q)) continue;
            out.add(p);
        }
        return out;
    }

    private static String formatName(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
