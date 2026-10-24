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
    private static final int CURRENT_CONFIG_VERSION = 5;
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("yppowers_config.json").toFile();

    // Incremented when config migrations change previously generated defaults.
    public int config_version = CURRENT_CONFIG_VERSION;

    // Defaults intentionally match the original YP Powers specification.
    public int divinity_heal_radius = 12;
    public double divinity_heal_amount = 0.5;
    public int divinity_forcefield_duration_ticks = 300;
    public int divinity_forcefield_cooldown_ticks = 600;
    public double goat_jump_multiplier = 2.2;
    public double goat_max_speed = 2.2;
    public int time_stop_max_duration_ticks = 120;
    public int time_stop_cooldown_ticks = 600;
    /** Cooldown for Time Warp's global rewind ability; 0 disables it. */
    public int time_warp_rewind_cooldown_ticks = 600; // 30 seconds
    /** Cooldown after creating a Gambit gateway; 0 disables it. */
    public int teleport_gambit_gateway_cooldown_ticks = 300; // 15 seconds
    public int teleport_gambit_gateway_duration_ticks = 100;
    /** Charge duration for Teleport Gambit's Domain ability (160 ticks = 8 seconds). */
    public int teleport_gambit_domain_charge_ticks = 160;
    /** Radius from the caster at the start of the Domain charge, in blocks. */
    public double teleport_gambit_domain_pull_radius = 25.0;
    /** Cooldown for Teleport Gambit's persistent pocket-domain ability (18,000 ticks = 15 minutes). */
    public int teleport_gambit_domain_cooldown_ticks = 18000;
    public int serious_punch_cooldown_ticks = 200;
    public int poseidon_trident_swap_cooldown_ticks = 60;
    public int thor_ancient_rage_cooldown_ticks = 400;
    public double thor_ancient_rage_radius = 12.0;
    public int thor_ancient_rage_strength_duration_ticks = 200;
    public int thor_ancient_rage_strength_amplifier = 2;
    public int thor_ancient_rage_particle_count = 50;
    public double thor_ancient_rage_particle_spread_multiplier = 0.3;
    public double thor_ancient_rage_particle_vertical_spread = 1.0;
    public double thor_ancient_rage_particle_speed = 0.2;
    public double thor_ancient_rage_sound_volume = 1.3;
    public double thor_ancient_rage_sound_pitch = 0.9;

    public int thor_mace_swap_cooldown_ticks = 10;
    public int thor_mace_unbreaking_level = 3;
    public int thor_mace_mending_level = 1;
    public int thor_mace_vanishing_curse_level = 1;
    public int thor_mace_fire_aspect_level = 2;
    public int thor_mace_wind_burst_level = 3;
    public int thor_mace_density_level = 5;
    public int thor_mace_breach_level = 4;
    public int thor_mace_sharpness_level = 5;
    public int thor_mace_smite_level = 5;
    public int thor_mace_bane_of_arthropods_level = 5;
    public boolean thor_mace_lightning_on_hit = true;
    public double thor_mace_swap_sound_volume = 0.8;
    public double thor_mace_swap_sound_pitch = 1.2;

    public int thor_riptide_windup_ticks = 12;
    public int thor_riptide_ready_window_ticks = 6;
    public int thor_riptide_cooldown_ticks = 30;
    public double thor_riptide_launch_forward_velocity = 2.35;
    public double thor_riptide_launch_upward_velocity = 0.65;
    public int thor_riptide_launch_particle_count = 30;
    public double thor_riptide_launch_particle_spread = 0.45;
    public double thor_riptide_launch_particle_speed = 0.2;
    public double thor_riptide_launch_sound_volume = 1.2;
    public double thor_riptide_launch_sound_pitch = 0.8;
    public double thor_riptide_windup_sound_volume = 0.8;
    public double thor_riptide_windup_sound_pitch = 0.7;

    public int thor_hammer_throw_cooldown_ticks = 100;
    public double thor_hammer_throw_velocity = 1.65;
    public double thor_hammer_throw_upward_velocity = 0.12;
    public int thor_hammer_impact_timeout_ticks = 100;
    public int thor_hammer_impact_lightning_count = 4;
    public double thor_hammer_impact_near_offset = 1.5;
    public double thor_hammer_impact_far_offset = 3.0;
    public int thor_hammer_impact_particle_count = 12;
    public double thor_hammer_impact_particle_spread = 1.0;
    public double thor_hammer_impact_particle_vertical_spread = 0.4;
    public double thor_hammer_impact_particle_speed = 0.1;
    public double thor_hammer_throw_sound_volume = 1.0;
    public double thor_hammer_throw_sound_pitch = 0.8;
    public double thor_hammer_impact_sound_volume = 2.0;
    public double thor_hammer_impact_sound_pitch = 0.7;

    public double thor_recall_stun_radius = 6.0;
    public int thor_recall_stun_duration_ticks = 60;
    public int thor_recall_slowness_amplifier = 10;
    public int thor_recall_weakness_amplifier = 2;
    public int thor_recall_particle_count = 45;
    public double thor_recall_particle_spread_multiplier = 0.35;
    public double thor_recall_particle_vertical_spread = 1.0;
    public double thor_recall_particle_speed = 0.15;
    public double thor_recall_sound_volume = 1.5;
    public double thor_recall_sound_pitch = 0.65;
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
    /** Cooldown for Ice Meteor, activated with the secondary ability key (H). */
    public int ice_emperor_meteor_cooldown_ticks = 1200;
    /** Ice Meteor impact radius in blocks; default is 50. */
    public double ice_emperor_meteor_radius = 50.0;
    /** Damage dealt by the Ice Meteor impact. */
    public double ice_emperor_meteor_damage = 12.0;
    /** Duration of the meteor's freeze, slowness, and blindness effects. */
    public int ice_emperor_meteor_effect_duration_ticks = 160;
    /** Duration of the meteor's temporary ice cover. */
    public int ice_emperor_meteor_ice_duration_ticks = 1200;

    // Global player health and shared Energy system.
    public boolean global_health_enabled = true;
    public double global_player_health_value = 40.0;
    public double energy_max = 100.0;
    public double energy_regen_per_second = 5.0;
    public double energy_cost_primary = 10.0;
    public double energy_cost_secondary = 15.0;
    public double energy_cost_tertiary = 20.0;
    public double energy_cost_quaternary = 20.0;
    public double energy_cost_alternate = 5.0;
    /** Energy cost when a Shadow Archer arrow is actually spawned; split-shot child arrows are free. */
    public double energy_cost_shadow_archer_arrow = 5.0;
    public int energy_bleeding_initial_ticks = 80;
    public int energy_bleeding_max_ticks = 160;
    public double energy_bleeding_damage_per_second = 1.0;
    public double energy_low_threshold_percent = 0.25;
    public int energy_hud_x = 10;
    public int energy_hud_bottom_offset = 61;

    // GOAT Overdrive and gradual deferred-damage repayment.
    public int goat_overdrive_duration_ticks = 300;
    public int goat_overdrive_cooldown_ticks = 1800;
    public double goat_overdrive_energy_cost = 60.0;
    public double goat_overdrive_movement_multiplier = 3.0;
    public double goat_overdrive_jump_multiplier = 4.0;
    public double goat_overdrive_knockback_resistance_bonus = 0.6;
    public double goat_overdrive_max_damage_debt = 200.0;
    public int goat_overdrive_debt_release_interval_ticks = 10;
    public double goat_overdrive_debt_release_per_hit = 1.0;

    // Time Warper Black Hole ultimate.
    public int time_warper_black_hole_charge_ticks = 40;
    public int time_warper_black_hole_pull_ticks = 80;
    public double time_warper_black_hole_entity_radius = 20.0;
    public double time_warper_black_hole_terrain_radius = 12.0;
    public double time_warper_black_hole_implosion_radius = 12.0;
    public double time_warper_black_hole_max_damage = 10.0;
    public int time_warper_black_hole_cooldown_ticks = 6000;
    public double time_warper_black_hole_health_sacrifice_percent = 0.5;
    public double time_warper_black_hole_energy_cost = 80.0;
    public boolean time_warper_black_hole_terrain_damage = true;
    public boolean time_warper_black_hole_affect_players = true;
    public boolean time_warper_black_hole_affect_mobs = true;
    public boolean time_warper_black_hole_affect_items = true;
    public int time_warper_black_hole_max_blocks_per_tick = 64;

    // Boots of the Sky: Raigo ultimate.
    public int boots_raigo_charge_ticks = 60;
    public double boots_raigo_range = 80.0;
    public double boots_raigo_radius = 20.0;
    public double boots_raigo_max_damage = 16.0;
    public int boots_raigo_cooldown_ticks = 3600;
    public double boots_raigo_energy_cost = 75.0;
    public boolean boots_raigo_terrain_damage = false;
    public int boots_raigo_max_blocks_per_tick = 64;

    // Thor airborne launch steering/chaining and input buffer.
    public int thor_riptide_input_buffer_ticks = 10;
    public int thor_riptide_chain_delay_ticks = 6;
    public int thor_riptide_steering_ticks = 15;
    public double thor_riptide_air_steering = 0.15;
    public double thor_riptide_max_velocity = 3.2;
    public double thor_riptide_energy_cost = 10.0;

    /** Clamp user-edited values to safe, finite ranges before they can reach server logic. */
    public void validate() {
        energy_max = clamp(energy_max, 1.0, 100000.0, 100.0);
        energy_regen_per_second = clamp(energy_regen_per_second, 0.0, 1000.0, 5.0);
        energy_cost_primary = clamp(energy_cost_primary, 0.0, 100000.0, 10.0);
        energy_cost_secondary = clamp(energy_cost_secondary, 0.0, 100000.0, 15.0);
        energy_cost_tertiary = clamp(energy_cost_tertiary, 0.0, 100000.0, 20.0);
        energy_cost_quaternary = clamp(energy_cost_quaternary, 0.0, 100000.0, 20.0);
        energy_cost_alternate = clamp(energy_cost_alternate, 0.0, 100000.0, 5.0);
        energy_cost_shadow_archer_arrow = clamp(energy_cost_shadow_archer_arrow, 0.0, 100000.0, 5.0);
        global_player_health_value = clamp(global_player_health_value, 1.0, 2048.0, 40.0);
        energy_bleeding_initial_ticks = clamp(energy_bleeding_initial_ticks, 1, 12000, 80);
        energy_bleeding_max_ticks = clamp(energy_bleeding_max_ticks, energy_bleeding_initial_ticks, 12000, 160);
        energy_bleeding_damage_per_second = clamp(energy_bleeding_damage_per_second, 0.0, 100.0, 1.0);
        energy_low_threshold_percent = clamp(energy_low_threshold_percent, 0.0, 1.0, 0.25);
        energy_hud_x = clamp(energy_hud_x, 0, 10000, 10);
        energy_hud_bottom_offset = clamp(energy_hud_bottom_offset, 20, 1000, 61);
        goat_overdrive_duration_ticks = clamp(goat_overdrive_duration_ticks, 1, 12000, 300);
        goat_overdrive_cooldown_ticks = clamp(goat_overdrive_cooldown_ticks, 0, 120000, 1800);
        goat_overdrive_energy_cost = clamp(goat_overdrive_energy_cost, 0.0, 100000.0, 60.0);
        goat_overdrive_movement_multiplier = clamp(goat_overdrive_movement_multiplier, 1.0, 10.0, 3.0);
        goat_overdrive_jump_multiplier = clamp(goat_overdrive_jump_multiplier, 1.0, 10.0, 4.0);
        goat_overdrive_knockback_resistance_bonus = clamp(goat_overdrive_knockback_resistance_bonus, 0.0, 1.0, 0.6);
        goat_overdrive_max_damage_debt = clamp(goat_overdrive_max_damage_debt, 1.0, 10000.0, 200.0);
        goat_overdrive_debt_release_interval_ticks = clamp(goat_overdrive_debt_release_interval_ticks, 1, 1200, 10);
        goat_overdrive_debt_release_per_hit = clamp(goat_overdrive_debt_release_per_hit, 0.1, 100.0, 1.0);
        time_warper_black_hole_charge_ticks = clamp(time_warper_black_hole_charge_ticks, 1, 1200, 40);
        time_warper_black_hole_pull_ticks = clamp(time_warper_black_hole_pull_ticks, 1, 1200, 80);
        time_warper_black_hole_entity_radius = clamp(time_warper_black_hole_entity_radius, 1.0, 128.0, 20.0);
        time_warper_black_hole_terrain_radius = clamp(time_warper_black_hole_terrain_radius, 0.0, 32.0, 12.0);
        time_warper_black_hole_implosion_radius = clamp(time_warper_black_hole_implosion_radius, 1.0, 64.0, 12.0);
        time_warper_black_hole_max_damage = clamp(time_warper_black_hole_max_damage, 0.0, 100.0, 10.0);
        time_warper_black_hole_cooldown_ticks = clamp(time_warper_black_hole_cooldown_ticks, 0, 1200000, 6000);
        time_warper_black_hole_health_sacrifice_percent = clamp(time_warper_black_hole_health_sacrifice_percent, 0.0, 0.95, 0.5);
        time_warper_black_hole_energy_cost = clamp(time_warper_black_hole_energy_cost, 0.0, 100000.0, 80.0);
        time_warper_black_hole_max_blocks_per_tick = clamp(time_warper_black_hole_max_blocks_per_tick, 0, 512, 64);
        boots_raigo_charge_ticks = clamp(boots_raigo_charge_ticks, 1, 1200, 60);
        boots_raigo_range = clamp(boots_raigo_range, 1.0, 256.0, 80.0);
        boots_raigo_radius = clamp(boots_raigo_radius, 1.0, 128.0, 20.0);
        boots_raigo_max_damage = clamp(boots_raigo_max_damage, 0.0, 100.0, 16.0);
        boots_raigo_cooldown_ticks = clamp(boots_raigo_cooldown_ticks, 0, 1200000, 3600);
        boots_raigo_energy_cost = clamp(boots_raigo_energy_cost, 0.0, 100000.0, 75.0);
        thor_riptide_input_buffer_ticks = clamp(thor_riptide_input_buffer_ticks, 0, 100, 10);
        thor_riptide_chain_delay_ticks = clamp(thor_riptide_chain_delay_ticks, 0, 1200, 6);
        thor_riptide_steering_ticks = clamp(thor_riptide_steering_ticks, 0, 1200, 15);
        thor_riptide_air_steering = clamp(thor_riptide_air_steering, 0.0, 1.0, 0.15);
        thor_riptide_max_velocity = clamp(thor_riptide_max_velocity, 0.1, 10.0, 3.2);
        thor_riptide_energy_cost = clamp(thor_riptide_energy_cost, 0.0, 100000.0, 10.0);
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max, int fallback) {
        return Math.max(min, Math.min(max, value));
    }

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
        defaults.validate();
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
