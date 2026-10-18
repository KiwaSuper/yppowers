package com.yppowers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class YPConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static YPConfig instance;
    private static final int CURRENT_CONFIG_VERSION = 2;
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("yppowers_config.json").toFile();

    // Incremented when config migrations change previously generated defaults.
    public int config_version = CURRENT_CONFIG_VERSION;

    // Defaults intentionally match the original YP Powers specification.
    public int divinity_heal_radius = 12;
    public double divinity_heal_amount = 0.5;
    public int divinity_forcefield_duration_ticks = 300;
    public int divinity_forcefield_cooldown_ticks = 600;
    public double goat_jump_multiplier = 2.4;
    public double goat_max_speed = 2.2;
    public int time_stop_max_duration_ticks = 120;
    public int time_stop_cooldown_ticks = 600;
    /** Cooldown for Time Warp's global rewind ability; 0 disables it. */
    public int time_warp_rewind_cooldown_ticks = 600; // 30 seconds
    /** Cooldown after creating a Gambit gateway; 0 disables it. */
    public int teleport_gambit_gateway_cooldown_ticks = 300; // 15 seconds
    public int teleport_gambit_gateway_duration_ticks = 100;
    public int serious_punch_cooldown_ticks = 200;
    public int poseidon_trident_swap_cooldown_ticks = 60;
    public int shadow_archer_morph_cooldown_ticks = 200;
    public int stick_of_deletion_cooldown_ticks = 80;
    public int fire_god_cannon_cooldown_ticks = 240;
    public double punch_raycast_range = 60;
    public int boots_flight_duration_ticks = 6000;
    public int boots_recharge_duration_ticks = 3600;
    public double ice_emperor_aura_radius = 4.0;
    /** Freeze-type damage dealt by Glacial Aura to nearby hostile mobs. */
    public double ice_emperor_aura_freeze_damage = 1.0;
    /** Delay between Glacial Aura freeze-damage hits per target, in ticks (40 = 2 seconds). */
    public int ice_emperor_aura_freeze_damage_interval_ticks = 40;
    public int ice_emperor_freeze_duration_ticks = 50;
    public int ice_emperor_cooldown_ticks = 600;

    public static synchronized YPConfig getInstance() {
        if (instance == null) instance = new YPConfig();
        return instance;
    }

    public synchronized void load() {
        YPConfig defaults = new YPConfig();
        if (FILE.isFile()) {
            try (FileReader reader = new FileReader(FILE)) {
                JsonElement root = JsonParser.parseReader(reader);
                YPConfig loaded = GSON.fromJson(root, YPConfig.class);
                if (loaded != null) {
                    JsonObject json = root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
                    int loadedVersion = json.has("config_version") && json.get("config_version").isJsonPrimitive()
                            ? json.get("config_version").getAsInt() : 0;

                    // v9 wrote these two settings as 0 (disabled) into every generated config.
                    // Migrate that old default once so existing installations receive the newly
                    // requested cooldowns. On later launches, an explicit 0 remains disabled.
                    if (loadedVersion < 2) {
                        if (loaded.time_warp_rewind_cooldown_ticks == 0) {
                            loaded.time_warp_rewind_cooldown_ticks = 600;
                        }
                        if (loaded.teleport_gambit_gateway_cooldown_ticks == 0) {
                            loaded.teleport_gambit_gateway_cooldown_ticks = 300;
                        }
                    }
                    loaded.config_version = CURRENT_CONFIG_VERSION;
                    defaults = loaded;
                }
            } catch (IOException | RuntimeException ex) {
                System.err.println("[YP Powers] Could not read config; using default values: " + ex.getMessage());
            }
        }
        defaults.config_version = CURRENT_CONFIG_VERSION;
        instance = defaults;
        // Persist the loaded instance so new/missing keys receive defaults without overwriting
        // the user's existing values with the stale object that called load().
        defaults.save();
    }

    public synchronized void save() {
        File parent = FILE.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            System.err.println("[YP Powers] Could not create config directory: " + parent);
            return;
        }
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException ex) {
            System.err.println("[YP Powers] Could not save config: " + ex.getMessage());
        }
    }
}
