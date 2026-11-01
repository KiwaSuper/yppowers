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
    private static final int CURRENT_CONFIG_VERSION = 12;
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("yppowers_config.json").toFile();

    // Incremented when config migrations change previously generated defaults.
    public int config_version = CURRENT_CONFIG_VERSION;

    // Defaults intentionally match the original YP Powers specification.
    public int divinity_heal_radius = 12;
    public double divinity_heal_amount = 0.5;
    public int divinity_forcefield_duration_ticks = 300;
    public int divinity_forcefield_cooldown_ticks = 600;
    /** Extra multiplier applied to the vanilla jump impulse after GOAT's configured Jump Boost. */
    public double goat_jump_multiplier = 1.25;
    /** Effective sprint multiplier cap relative to a normal sprint. */
    public double goat_max_speed = 5.0;
    /** Effective sprint multiplier at the start of a sprint. */
    public double goat_sprint_start_multiplier = 2.0;
    /** Ticks of continuous sprint required to reach goat_max_speed. */
    public int goat_sprint_acceleration_ticks_to_max = 100;
    /** Ticks to wait after stopping before clearing accumulated sprint acceleration. */
    public int goat_sprint_reset_delay_ticks = 10;
    public boolean goat_sprint_preserve_acceleration_while_jumping = true;
    public boolean goat_sprint_enabled = true;
    public boolean goat_sprint_particles_enabled = false;
    public boolean goat_sprint_sounds_enabled = false;
    /** GOAT's managed permanent Jump Boost amplifier (0 = Jump Boost I). */
    public int goat_jump_boost_amplifier = 0;
    /** Fully protect only jumps actually initiated through GOAT's jump handler. */
    public boolean goat_jump_fall_protection_enabled = true;
    /** Damage multiplier during a tracked GOAT jump; 0 means no fall damage. */
    public double goat_jump_fall_damage_multiplier = 0.0;
    /** Preserve the previous passive fall-damage reduction for unrelated falls. */
    public boolean goat_passive_fall_protection_enabled = true;
    public double goat_passive_fall_damage_multiplier = 0.4;
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
    /** Regeneration amplifier while Poseidon meets the configured water condition (4 = Regeneration V). */
    public int poseidon_water_regeneration_amplifier = 4;
    /** Enable the new Haste, Resistance, and Strength water buffs. Existing water passives remain separate. */
    public boolean poseidon_water_buffs_enabled = true;
    public int poseidon_water_haste_amplifier = 0;
    public int poseidon_water_resistance_amplifier = 0;
    public int poseidon_water_strength_amplifier = 0;
    /** Freeze HungerManager's depletion update while Poseidon meets the water condition. */
    public boolean poseidon_water_hunger_protection_enabled = true;
    /** TOUCHING (default), SUBMERGED, or SWIMMING_OR_SUBMERGED. */
    public String poseidon_water_condition = "TOUCHING";
    public int poseidon_tsunami_cooldown_ticks = 2400;
    public double poseidon_tsunami_search_radius = 100.0;
    public double poseidon_tsunami_wave_radius = 18.0;
    /** Active wave lifetime (36 ticks = 1.8 seconds); the visual crest expands throughout this time. */
    public int poseidon_tsunami_wave_duration_ticks = 36;
    public double poseidon_tsunami_damage = 8.0;
    public int poseidon_tsunami_stun_ticks = 50;
    public int poseidon_tsunami_buff_ticks = 600;
    public int poseidon_tsunami_buff_regeneration_amplifier = 2;
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
    public int thor_mace_wind_burst_level = 1;
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
    // New Mjölnir throw/return controls requested for the Thor rework.
    public int thor_riptide_min_charge_ticks = 10;
    public double thor_riptide_strength = 3.0;
    public double thor_throw_speed = 1.65;
    public boolean thor_crater_enabled = true;
    public int thor_crater_radius = 4;
    public int thor_crater_depth = 3;
    public double thor_impact_damage = 12.0;
    public double thor_impact_radius = 5.0;
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

    // Fallout Warrior: radiation field, poison pool and three-role pillager squad.
    public boolean fallout_aura_enabled_by_default = true;
    public double fallout_aura_radius = 8.0;
    public double fallout_aura_heal_amount = 1.0;
    public double fallout_aura_damage_amount = 0.5;
    public int fallout_aura_interval_ticks = 40;
    public int fallout_pool_radius = 10;
    public int fallout_pool_duration_ticks = 300;
    public double fallout_pool_damage = 1.0;
    public double fallout_pool_energy_cost = 20.0;
    public int fallout_pool_cooldown_ticks = 1200;
    public int fallout_minions_per_cast = 3;
    public int fallout_active_minion_cap = 6;
    public double fallout_healer_health = 16.0;
    public double fallout_defender_health = 40.0;
    public double fallout_assassin_health = 24.0;
    public double fallout_assassin_speed_multiplier = 1.5;
    public int fallout_minion_lifespan_ticks = 2400;
    public int fallout_minion_summon_cooldown_ticks = 2400;
    public double fallout_minion_energy_cost = 40.0;

    // Fire God summons.
    public int fire_god_blaze_cost_energy = 20;
    public int fire_god_blaze_max_active = 3;
    public int fire_god_blaze_cooldown_ticks = 200;
    public int fire_god_blaze_lifespan_ticks = 1200;
    public double fire_god_blaze_max_health = 20.0;
    public double fire_god_blaze_attack_damage = 6.0;
    public int fire_god_wither_skeleton_cost_energy = 25;
    public int fire_god_wither_skeleton_max_active = 3;
    public int fire_god_wither_skeleton_cooldown_ticks = 200;
    public int fire_god_wither_skeleton_lifespan_ticks = 1200;
    public double fire_god_wither_skeleton_max_health = 28.0;
    public double fire_god_wither_skeleton_attack_damage = 8.0;

    // Serious Punch Momentum and HELLDIVE.
    public int serious_punch_momentum_max = 8;
    public int serious_punch_momentum_gain_per_hit = 1;
    public int serious_punch_momentum_decay_ticks = 80;
    public double serious_punch_momentum_damage_per_stack = 0.08;
    public double serious_punch_helldive_energy_cost = 30.0;
    public double serious_punch_helldive_damage = 10.0;
    public double serious_punch_helldive_radius = 7.0;
    public int serious_punch_helldive_crater_radius = 4;
    public int serious_punch_helldive_crater_depth = 2;
    public double serious_punch_helldive_launch_velocity = 1.2;
    public int serious_punch_helldive_cooldown_ticks = 300;

    // Shadow Archer Railshot. It is intentionally separate from the instant-charge arrow types.
    public int shadow_archer_railshot_base_draw_ticks = 4;
    public int shadow_archer_railshot_max_draw_ticks = 20;
    public int shadow_archer_railshot_draw_gain_ticks = 2;
    public int shadow_archer_railshot_charge_decay_ticks = 100;
    public double shadow_archer_railshot_speed_multiplier_per_charge = 0.08;
    public double shadow_archer_railshot_damage_multiplier_per_charge = 0.12;
    public double shadow_archer_railshot_inaccuracy_multiplier_per_charge = 0.08;

    // Spidey combat and traversal abilities.
    public double spidey_web_sling_range = 32.0;
    public double spidey_web_sling_velocity = 1.5;
    public int spidey_mlg_fall_threshold = 8;
    public int spidey_mlg_search_radius = 3;
    public int spidey_mlg_cooldown_ticks = 40;
    public double spidey_web_grenade_radius = 6.0;
    public int spidey_web_grenade_duration_ticks = 100;
    public double spidey_web_grenade_damage = 1.0;
    public int spidey_web_grenade_cooldown_ticks = 240;
    public double spidey_web_grenade_energy_cost = 20.0;
    public int spidey_rage_duration_ticks = 300;
    public int spidey_rage_cooldown_ticks = 1800;
    public int spidey_rage_debuff_ticks = 200;
    public double spidey_rage_energy_cost = 15.0;
    public double spidey_rage_speed_multiplier = 1.5;
    public double spidey_rage_web_velocity_multiplier = 1.5;
    public int spidey_dive_max_chain = 4;
    public int spidey_dive_chain_window_ticks = 24;
    public double spidey_dive_hit_damage = 6.0;
    public double spidey_dive_finisher_damage = 8.0;
    public int spidey_dive_cooldown_ticks = 240;
    public double spidey_dive_energy_cost = 20.0;

    // Spirit Channeler.
    public double spirit_summon_energy_cost = 20.0;
    public int spirit_summon_cooldown_ticks = 200;
    public int spirit_lifespan_ticks = 1200;
    public double spirit_shadow_step_range = 16.0;
    public int spirit_shadow_step_cooldown_ticks = 200;
    public double spirit_shadow_step_energy_cost = 10.0;
    public int spirit_restless_shadows_duration_ticks = 600;
    public int spirit_restless_shadows_cooldown_ticks = 3600;
    public double spirit_restless_shadows_energy_cost = 75.0;
    public double spirit_restless_shadows_radius = 12.0;
    public double spirit_restless_shadows_damage = 6.0;

    // Illusionist.
    public int illusionist_spatial_erasure_cooldown_ticks = 300;
    public int illusionist_spatial_erasure_duration_ticks = 160;
    public double illusionist_spatial_erasure_damage = 10.0;
    public int illusionist_spatial_erasure_slowness_ticks = 80;
    public double illusionist_grip_range = 15.0;
    public double illusionist_grip_default_distance = 5.0;
    public double illusionist_grip_throw_multiplier = 3.0;
    public int illusionist_grip_release_cooldown_ticks = 100;
    public int illusionist_grip_passive_radius = 10;
    public int illusionist_celestial_pull_cooldown_ticks = 3600;
    public double illusionist_celestial_pull_radius = 12.0;
    public int illusionist_celestial_pull_duration_ticks = 80;
    public double illusionist_celestial_pull_damage = 20.0;
    public double illusionist_celestial_pull_energy_cost = 60.0;

    // Kitsune.
    public int kitsune_foxfire_duration_ticks = 80;
    public double kitsune_foxfire_damage_per_second = 1.0;
    public int kitsune_transformation_duration_ticks = 600;
    public int kitsune_transformation_cooldown_ticks = 2400;
    public double kitsune_transformation_speed_multiplier = 3.0;
    public double kitsune_claw_energy_cost = 10.0;
    public double kitsune_claw_damage = 8.0;
    public double kitsune_claw_range = 4.0;
    public int kitsune_claw_cooldown_ticks = 40;
    public double kitsune_burst_damage = 6.0;
    public double kitsune_burst_speed = 1.4;
    public double kitsune_burst_energy_cost = 15.0;
    public int kitsune_burst_cooldown_ticks = 100;
    public double kitsune_pounce_energy_cost = 15.0;
    public double kitsune_pounce_range = 18.0;
    public double kitsune_pounce_damage = 8.0;
    public double kitsune_pounce_knockback = 1.4;
    public int kitsune_pounce_cooldown_ticks = 120;
    public double kitsune_detonation_damage = 10.0;
    public double kitsune_detonation_radius = 6.0;
    public double kitsune_detonation_energy_cost = 25.0;
    public int kitsune_detonation_cooldown_ticks = 300;
    public double punch_raycast_range = 60;
    public int boots_flight_duration_ticks = 6000;
    public int boots_recharge_duration_ticks = 3600;
    /** Energy consumed per second while Boots of the Sky flight is active (0.10 = one tenth of a point). */
    public double boots_flight_energy_drain_per_second = 0.10;
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
    /** Number of polar-bear bodyguards summoned by Ice Emperor's V ability. */
    public int ice_emperor_guard_count = 2;
    /** Cooldown for summoning Ice Emperor polar-bear bodyguards (1200 ticks = 60 seconds). */
    public int ice_emperor_guard_cooldown_ticks = 1200;
    /** Damage dealt to the Ice Emperor owner when one of their bonded guards dies. */
    public double ice_emperor_guard_death_damage = 4.0;
    /** Movement-speed multiplier applied to Frost Guards summoned without enough Energy. */
    public double ice_emperor_guard_low_energy_speed_multiplier = 0.60;
    /** Attack-damage multiplier applied to Frost Guards summoned without enough Energy. */
    public double ice_emperor_guard_low_energy_damage_multiplier = 0.50;
    /** Percent chance for each low-Energy Frost Guard to spawn as a rogue hostile to its owner. */
    public double ice_emperor_guard_hostile_chance_percent = 10.0;
    /** Minimum Energy reserve required to release Heat Blast; all current Energy is consumed. */
    public double ice_emperor_heat_blast_min_energy = 10.0;
    /** Maximum radius of Ice Emperor Heat Blast. */
    public double ice_emperor_heat_blast_radius = 12.0;
    /** Fire duration applied by Heat Blast, in seconds. */
    public int ice_emperor_heat_blast_fire_seconds = 5;
    /** Slowness duration applied by Heat Blast, in ticks. */
    public int ice_emperor_heat_blast_slowness_ticks = 120;
    /** Slowness amplifier applied by Heat Blast (2 = Slowness III). */
    public int ice_emperor_heat_blast_slowness_amplifier = 2;

    // Ice Emperor Forms (one active form at a time). The key code is the default for the
    // client keybind; players can also change it in Minecraft's Controls screen.
    public boolean ice_emperor_forms_enabled = true;
    public boolean ice_emperor_form_armoured_enabled = true;
    public boolean ice_emperor_form_stealth_enabled = true;
    public boolean ice_emperor_form_assassin_enabled = true;
    public int ice_emperor_form_switch_keycode = 75; // GLFW_KEY_K
    public String ice_emperor_default_form = "ARMOURED";
    public boolean ice_emperor_form_persist_through_death = true;
    public boolean ice_emperor_form_persist_through_reconnect = true;
    public int ice_emperor_armoured_resistance_amplifier = 2; // amplifier 2 = Resistance III
    public double ice_emperor_armoured_movement_multiplier = 0.90;
    public double ice_emperor_armoured_outgoing_damage_multiplier = 0.90;
    public double ice_emperor_stealth_detection_radius = 10.0;
    public int ice_emperor_stealth_update_interval_ticks = 5;
    public boolean ice_emperor_stealth_allies_count = true;
    public boolean ice_emperor_forms_particles_enabled = true;
    public boolean ice_emperor_forms_sound_enabled = true;
    public boolean ice_emperor_forms_action_bar_enabled = true;
    public double ice_emperor_assassin_movement_multiplier = 1.25;
    public double ice_emperor_assassin_melee_damage_multiplier = 1.30;
    public double ice_emperor_assassin_max_health_multiplier = 0.80;
    public boolean ice_emperor_assassin_ice_trail_enabled = true;

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
    public int time_warper_form_switch_cooldown_ticks = 12000;
    public double time_warper_clone_energy_cost = 20.0;
    public int time_warper_clone_shards_cost = 3;
    public double time_warper_elite_clone_energy_cost = 40.0;
    public int time_warper_clone_max_active = 25;
    public int time_warper_clone_lifespan_ticks = 2400;
    public int time_warper_time_shard_lifetime_ticks = 80;
    public double time_warper_time_shard_drop_chance = 0.15;
    public double time_warper_golden_shard_drop_chance = 0.25;
    public double time_warper_elite_clone_shard_cost = 1.0;
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
        goat_jump_multiplier = clamp(goat_jump_multiplier, 1.0, 2.0, 1.25);
        goat_max_speed = clamp(goat_max_speed, 1.0, 10.0, 5.0);
        goat_sprint_start_multiplier = clamp(goat_sprint_start_multiplier, 1.0, 10.0, 2.0);
        if (goat_sprint_start_multiplier > goat_max_speed) goat_sprint_start_multiplier = goat_max_speed;
        goat_sprint_acceleration_ticks_to_max = clamp(goat_sprint_acceleration_ticks_to_max, 1, 2400, 100);
        goat_sprint_reset_delay_ticks = clamp(goat_sprint_reset_delay_ticks, 0, 1200, 10);
        goat_jump_boost_amplifier = clamp(goat_jump_boost_amplifier, 0, 1, 0);
        goat_jump_fall_damage_multiplier = clamp(goat_jump_fall_damage_multiplier, 0.0, 1.0, 0.0);
        goat_passive_fall_damage_multiplier = clamp(goat_passive_fall_damage_multiplier, 0.0, 1.0, 0.4);
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
        boots_flight_energy_drain_per_second = clamp(boots_flight_energy_drain_per_second, 0.0, 100.0, 0.10);
        ice_emperor_guard_count = clamp(ice_emperor_guard_count, 1, 6, 2);
        ice_emperor_guard_cooldown_ticks = clamp(ice_emperor_guard_cooldown_ticks, 0, 120000, 1200);
        ice_emperor_guard_death_damage = clamp(ice_emperor_guard_death_damage, 0.0, 100.0, 4.0);
        ice_emperor_guard_low_energy_speed_multiplier = clamp(ice_emperor_guard_low_energy_speed_multiplier, 0.1, 1.0, 0.60);
        ice_emperor_guard_low_energy_damage_multiplier = clamp(ice_emperor_guard_low_energy_damage_multiplier, 0.1, 1.0, 0.50);
        ice_emperor_guard_hostile_chance_percent = clamp(ice_emperor_guard_hostile_chance_percent, 0.0, 100.0, 10.0);
        ice_emperor_heat_blast_min_energy = clamp(ice_emperor_heat_blast_min_energy, 1.0, 100000.0, 10.0);
        ice_emperor_heat_blast_radius = clamp(ice_emperor_heat_blast_radius, 1.0, 64.0, 12.0);
        ice_emperor_heat_blast_fire_seconds = clamp(ice_emperor_heat_blast_fire_seconds, 1, 30, 5);
        ice_emperor_heat_blast_slowness_ticks = clamp(ice_emperor_heat_blast_slowness_ticks, 1, 1200, 120);
        ice_emperor_heat_blast_slowness_amplifier = clamp(ice_emperor_heat_blast_slowness_amplifier, 0, 4, 2);
        ice_emperor_form_switch_keycode = clamp(ice_emperor_form_switch_keycode, 32, 348, 75);
        ice_emperor_armoured_resistance_amplifier = clamp(ice_emperor_armoured_resistance_amplifier, 0, 4, 2);
        ice_emperor_armoured_movement_multiplier = clamp(ice_emperor_armoured_movement_multiplier, 0.10, 1.0, 0.90);
        ice_emperor_armoured_outgoing_damage_multiplier = clamp(ice_emperor_armoured_outgoing_damage_multiplier, 0.10, 1.0, 0.90);
        ice_emperor_stealth_detection_radius = clamp(ice_emperor_stealth_detection_radius, 1.0, 128.0, 10.0);
        ice_emperor_stealth_update_interval_ticks = clamp(ice_emperor_stealth_update_interval_ticks, 1, 200, 5);
        ice_emperor_assassin_movement_multiplier = clamp(ice_emperor_assassin_movement_multiplier, 1.0, 3.0, 1.25);
        ice_emperor_assassin_melee_damage_multiplier = clamp(ice_emperor_assassin_melee_damage_multiplier, 1.0, 3.0, 1.30);
        ice_emperor_assassin_max_health_multiplier = clamp(ice_emperor_assassin_max_health_multiplier, 0.10, 1.0, 0.80);
        if (ice_emperor_default_form == null) ice_emperor_default_form = "ARMOURED";
        ice_emperor_default_form = ice_emperor_default_form.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(ice_emperor_default_form)) ice_emperor_default_form = "ARMOURED";
        if (!ice_emperor_form_armoured_enabled && !ice_emperor_form_stealth_enabled && !ice_emperor_form_assassin_enabled) {
            // Keep the default value valid even when administrators intentionally disable all forms.
            ice_emperor_default_form = "ARMOURED";
        } else if (ice_emperor_default_form.equals("ARMOURED") && !ice_emperor_form_armoured_enabled) {
            ice_emperor_default_form = ice_emperor_form_stealth_enabled ? "STEALTH" : "ASSASSIN";
        } else if (ice_emperor_default_form.equals("STEALTH") && !ice_emperor_form_stealth_enabled) {
            ice_emperor_default_form = ice_emperor_form_armoured_enabled ? "ARMOURED" : "ASSASSIN";
        } else if (ice_emperor_default_form.equals("ASSASSIN") && !ice_emperor_form_assassin_enabled) {
            ice_emperor_default_form = ice_emperor_form_armoured_enabled ? "ARMOURED" : "STEALTH";
        }
        poseidon_water_regeneration_amplifier = clamp(poseidon_water_regeneration_amplifier, 0, 9, 4);
        poseidon_water_haste_amplifier = clamp(poseidon_water_haste_amplifier, 0, 3, 0);
        poseidon_water_resistance_amplifier = clamp(poseidon_water_resistance_amplifier, 0, 2, 0);
        poseidon_water_strength_amplifier = clamp(poseidon_water_strength_amplifier, 0, 2, 0);
        if (poseidon_water_condition == null) poseidon_water_condition = "TOUCHING";
        poseidon_water_condition = poseidon_water_condition.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("TOUCHING", "SUBMERGED", "SWIMMING_OR_SUBMERGED").contains(poseidon_water_condition)) {
            poseidon_water_condition = "TOUCHING";
        }
        poseidon_tsunami_cooldown_ticks = clamp(poseidon_tsunami_cooldown_ticks, 0, 1200000, 2400);
        poseidon_tsunami_search_radius = clamp(poseidon_tsunami_search_radius, 1.0, 100.0, 100.0);
        poseidon_tsunami_wave_radius = clamp(poseidon_tsunami_wave_radius, 1.0, 64.0, 18.0);
        poseidon_tsunami_wave_duration_ticks = clamp(poseidon_tsunami_wave_duration_ticks, 10, 100, 36);
        poseidon_tsunami_damage = clamp(poseidon_tsunami_damage, 0.0, 100.0, 8.0);
        poseidon_tsunami_stun_ticks = clamp(poseidon_tsunami_stun_ticks, 0, 1200, 50);
        poseidon_tsunami_buff_ticks = clamp(poseidon_tsunami_buff_ticks, 1, 12000, 600);
        poseidon_tsunami_buff_regeneration_amplifier = clamp(poseidon_tsunami_buff_regeneration_amplifier, 0, 9, 2);
        thor_riptide_min_charge_ticks = clamp(thor_riptide_min_charge_ticks, 0, 1200, 10);
        thor_riptide_strength = clamp(thor_riptide_strength, 0.1, 10.0, 3.0);
        thor_throw_speed = clamp(thor_throw_speed, 0.1, 10.0, 1.65);
        thor_crater_radius = clamp(thor_crater_radius, 1, 12, 4);
        thor_crater_depth = clamp(thor_crater_depth, 1, 12, 3);
        thor_impact_damage = clamp(thor_impact_damage, 0.0, 100.0, 12.0);
        thor_impact_radius = clamp(thor_impact_radius, 0.0, 64.0, 5.0);
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
                    if (loadedVersion < 10) {
                        // Upgrade only the old shipped defaults. Non-default values remain admin choices.
                        if (Math.abs(loaded.goat_jump_multiplier - 2.2) < 0.000001) loaded.goat_jump_multiplier = 1.25;
                        if (Math.abs(loaded.goat_max_speed - 2.2) < 0.000001) loaded.goat_max_speed = 5.0;
                    }
                    if (loadedVersion < 12) {
                        if (loaded.thor_mace_wind_burst_level == 3) loaded.thor_mace_wind_burst_level = 1;
                        if (loaded.thor_riptide_min_charge_ticks == 12) loaded.thor_riptide_min_charge_ticks = 10;
                        if (Math.abs(loaded.thor_riptide_strength - 2.35) < 0.000001) loaded.thor_riptide_strength = 3.0;
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
