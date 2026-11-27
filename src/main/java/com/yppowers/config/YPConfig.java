package com.yppowers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;

public class YPConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static YPConfig instance;
   private static final int CURRENT_CONFIG_VERSION = 36;
   private static final File FILE = resolveConfigFile();

   private static File resolveConfigFile() {
      try {
         if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
            return FabricLoader.getInstance().getConfigDir().resolve("yppowers_config.json").toFile();
         }
      } catch (RuntimeException ignored) {
      }
      return new File("config", "yppowers_config.json");
   }
   public int config_version = 36;
   public int divinity_heal_radius = 12;
   public double divinity_heal_amount = 0.5;
   public int divinity_forcefield_duration_ticks = 300;
   public int divinity_forcefield_cooldown_ticks = 600;
   public int divinity_shield_radius = 12;
   public double divinity_shield_damage_reduction = 0.4;
   public double divinity_stored_damage_cap = 40.0;
   public double divinity_reflect_range = 32.0;
   public double divinity_reflect_damage_multiplier = 0.75;
   public int divinity_reflect_cooldown_ticks = 200;
   public double divinity_deflected_projectile_charge = 1.0;
   public double divinity_shield_block_charge_fraction = 0.2;
   public double goat_jump_multiplier = 1.25;
   public double goat_max_speed = 9.0;
   public double goat_sprint_start_multiplier = 2.0;
   public int goat_sprint_acceleration_ticks_to_max = 300;
   public int goat_awakening_max_speed_ticks = 60;
   public double goat_awakening_energy_per_block = 5.0;
   public double goat_awakening_max_block_hardness = 2.0;
   public int goat_awakening_break_interval_ticks = 4;
   public int goat_sprint_reset_delay_ticks = 10;
   public boolean goat_sprint_preserve_acceleration_while_jumping = true;
   public boolean goat_sprint_enabled = true;
   public boolean goat_sprint_particles_enabled = false;
   public boolean goat_sprint_sounds_enabled = false;
   public int goat_jump_boost_amplifier = 0;
   public boolean goat_jump_fall_protection_enabled = true;
   public double goat_jump_fall_damage_multiplier = 0.0;
   public boolean goat_passive_fall_protection_enabled = true;
   public double goat_passive_fall_damage_multiplier = 0.4;
   public int time_stop_max_duration_ticks = 80;
   public int time_stop_cooldown_ticks = 1200;
   public int time_warp_rewind_cooldown_ticks = 2400;
   public int teleport_gambit_gateway_cooldown_ticks = 600;
   public int teleport_gambit_gateway_duration_ticks = 100;
   public int teleport_gambit_domain_charge_ticks = 160;
   public double teleport_gambit_domain_pull_radius = 25.0;
   public int teleport_gambit_domain_cooldown_ticks = 18000;
   public int teleport_gambit_random_portal_charge_ticks = 100;
   public double teleport_gambit_random_portal_radius = 8.0;
   public int teleport_gambit_random_portal_cooldown_ticks = 6000;
   public double teleport_gambit_random_portal_energy_cost = 50.0;
   public int teleport_gambit_random_portal_search_attempts = 24;
   public double teleport_gambit_awakening_energy_cost = 100.0;
   public int teleport_gambit_awakening_cooldown_ticks = 1728000;
   public int teleport_gambit_structure_search_radius_chunks = 128;
   public List<String> teleport_gambit_structure_blacklist = new ArrayList<>(
      List.of("minecraft:stronghold", "minecraft:ancient_city", "minecraft:trial_chambers")
   );
   public int serious_punch_cooldown_ticks = 200;
   public int poseidon_trident_swap_cooldown_ticks = 60;
   public int poseidon_water_regeneration_amplifier = 0;
   public boolean poseidon_water_buffs_enabled = true;
   public int poseidon_water_haste_amplifier = 2;
   public double poseidon_high_speed_swim_multiplier = 2.0;
   public int poseidon_water_resistance_amplifier = 0;
   public int poseidon_water_strength_amplifier = 0;
   public boolean poseidon_water_hunger_protection_enabled = true;
   public String poseidon_water_condition = "TOUCHING";
   public int poseidon_tsunami_cooldown_ticks = 2400;
   public double poseidon_tsunami_search_radius = 100.0;
   public double poseidon_tsunami_wave_radius = 18.0;
   public int poseidon_tsunami_wave_duration_ticks = 36;
   public double poseidon_tsunami_damage = 8.0;
   public int poseidon_tsunami_stun_ticks = 20;
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
   public int thor_riptide_min_charge_ticks = 10;
   public double thor_riptide_strength = 2.5;
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
   public double stick_of_deletion_swap_range = 24.0;
   public int stick_of_deletion_swap_cooldown_ticks = 160;
   public double stick_of_deletion_swap_energy_cost = 15.0;
   public String shadow_archer_bow_default_mode = "INFINITY";
   public int shadow_archer_bow_power_level = 5;
   public int shadow_archer_bow_punch_level = 2;
   public int shadow_archer_bow_flame_level = 1;
   public int shadow_archer_bow_unbreaking_level = 3;
   public int shadow_archer_arrow_swap_cooldown_ticks = 120;
   public double shadow_archer_arrow_swap_energy_cost = 15.0;
   public double shadow_archer_arrow_swap_max_distance = 64.0;
   public int fire_god_cannon_cooldown_ticks = 240;
   public double fire_god_blaze_beam_energy_per_shot = 1.5;
   public int fire_god_blaze_beam_shot_interval_ticks = 5;
   public double fire_god_blaze_beam_projectile_speed = 1.6;
   public double fire_god_wither_skull_energy_cost = 18.0;
   public int fire_god_wither_skull_cooldown_ticks = 100;
   public double fire_god_wither_skull_damage = 6.0;
   public int fire_god_wither_skull_wither_ticks = 60;
   public double fire_god_wither_skull_projectile_speed = 1.5;
   public double fire_god_wither_skull_explosion_power = 1.0;
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
   public int fire_god_blaze_cost_energy = 20;
   public int fire_god_blaze_max_active = 2;
   public int fire_god_blaze_cooldown_ticks = 400;
   public int fire_god_blaze_lifespan_ticks = 1200;
   public double fire_god_blaze_max_health = 20.0;
   public double fire_god_blaze_attack_damage = 6.0;
   public int fire_god_wither_skeleton_cost_energy = 25;
   public int fire_god_wither_skeleton_max_active = 2;
   public int fire_god_wither_skeleton_cooldown_ticks = 400;
   public int fire_god_wither_skeleton_lifespan_ticks = 1200;
   public double fire_god_wither_skeleton_max_health = 28.0;
   public double fire_god_wither_skeleton_attack_damage = 8.0;
   public int serious_punch_momentum_max = 8;
   public int serious_punch_momentum_gain_per_hit = 1;
   public int serious_punch_momentum_decay_ticks = 80;
   public double serious_punch_momentum_damage_per_stack = 0.04;
   public double serious_punch_helldive_energy_cost = 30.0;
   public double serious_punch_helldive_damage = 10.0;
   public double serious_punch_helldive_radius = 7.0;
   public int serious_punch_helldive_crater_radius = 4;
   public int serious_punch_helldive_crater_depth = 2;
   public double serious_punch_helldive_launch_velocity = 1.2;
   public int serious_punch_helldive_cooldown_ticks = 300;
   public int shadow_archer_railshot_base_draw_ticks = 4;
   public int shadow_archer_railshot_max_draw_ticks = 20;
   public int shadow_archer_railshot_draw_gain_ticks = 2;
   public int shadow_archer_railshot_charge_decay_ticks = 100;
   public double shadow_archer_railshot_speed_multiplier_per_charge = 0.08;
   public double shadow_archer_railshot_damage_multiplier_per_charge = 0.12;
   public double shadow_archer_railshot_inaccuracy_multiplier_per_charge = 0.08;
   public int shadow_archer_pinning_duration_ticks = 40;
   public int shadow_archer_tracer_duration_ticks = 600;
   public int shadow_archer_eclipse_duration_ticks = 160;
   public double shadow_archer_eclipse_radius = 8.0;
   public double shadow_archer_phase_max_distance = 64.0;
   public double spidey_web_sling_range = 32.0;
   public double spidey_web_sling_velocity = 1.0;
   public double spidey_web_projectile_speed = 1.8;
   public int spidey_temporary_web_duration_ticks = 200;
   public double spidey_web_grenade_radius = 6.0;
   public int spidey_web_grenade_duration_ticks = 60;
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
   public double spirit_restless_shadows_damage = 4.0;
   public double spirit_restless_shadows_health = 30.0;
   public double spirit_restless_shadows_darkness_radius = 12.0;
   public int spirit_restless_shadows_darkness_ticks = 100;
   public int illusionist_spatial_erasure_cooldown_ticks = 500;
   public int illusionist_spatial_erasure_duration_ticks = 160;
   public double illusionist_spatial_erasure_damage = 8.0;
   public int illusionist_spatial_erasure_slowness_ticks = 60;
   public double illusionist_grip_range = 12.0;
   public double illusionist_grip_default_distance = 5.0;
   public double illusionist_grip_throw_multiplier = 3.0;
   public int illusionist_grip_release_cooldown_ticks = 160;
   public int illusionist_grip_passive_radius = 10;
   public int illusionist_celestial_pull_cooldown_ticks = 3600;
   public double illusionist_celestial_pull_radius = 12.0;
   public int illusionist_celestial_pull_duration_ticks = 80;
   public double illusionist_celestial_pull_damage = 20.0;
   public double illusionist_celestial_pull_energy_cost = 60.0;
   public double illusionist_cloud_flight_energy_cost = 25.0;
   public int illusionist_cloud_flight_duration_ticks = 600;
   public int illusionist_cloud_flight_cooldown_ticks = 1200;
   public double illusionist_celestial_moon_radius = 5.0;
   public double illusionist_celestial_moon_height = 22.0;
   public double illusionist_celestial_moon_scale = 1.6;
   public int illusionist_celestial_moon_max_blocks = 480;
   public double illusionist_celestial_weightless_radius = 48.0;
   public double illusionist_celestial_crater_radius = 16.0;
   public double illusionist_celestial_crater_damage = 45.0;
   public int kitsune_foxfire_duration_ticks = 80;
   public double kitsune_foxfire_damage_per_second = 1.0;
   public int kitsune_transformation_duration_ticks = 600;
   public int kitsune_transformation_cooldown_ticks = 2400;
   public double kitsune_transformation_speed_multiplier = 2.0;
   public double kitsune_fox_place_range = 16.0;
   public double kitsune_carry_range = 12.0;
   public double kitsune_carry_enemy_damage_per_second = 2.0;
   public int kitsune_tunnel_blocks_per_tick = 6;
   public double kitsune_claw_energy_cost = 10.0;
   public double kitsune_claw_damage = 6.0;
   public double kitsune_claw_range = 4.0;
   public int kitsune_claw_cooldown_ticks = 60;
   public double kitsune_burst_damage = 6.0;
   public double kitsune_burst_speed = 1.4;
   public double kitsune_burst_energy_cost = 15.0;
   public int kitsune_burst_cooldown_ticks = 100;
   public double kitsune_pounce_energy_cost = 15.0;
   public double kitsune_pounce_range = 18.0;
   public double kitsune_pounce_damage = 8.0;
   public double kitsune_pounce_knockback = 1.4;
   public int kitsune_pounce_cooldown_ticks = 120;
   public double kitsune_detonation_damage = 8.0;
   public double kitsune_detonation_radius = 5.0;
   public double kitsune_detonation_energy_cost = 25.0;
   public int kitsune_detonation_cooldown_ticks = 300;
   public double punch_raycast_range = 40.0;
   public int boots_flight_duration_ticks = 6000;
   public int boots_recharge_duration_ticks = 3600;
   public double boots_flight_energy_drain_per_second = 0.1;
   public double ice_emperor_aura_radius = 4.0;
   public double ice_emperor_aura_freeze_damage = 1.0;
   public int ice_emperor_aura_freeze_damage_interval_ticks = 40;
   public int ice_emperor_freeze_duration_ticks = 40;
   public int ice_emperor_cooldown_ticks = 600;
   public int ice_emperor_meteor_cooldown_ticks = 2400;
   public double ice_emperor_meteor_radius = 24.0;
   public double ice_emperor_meteor_damage = 12.0;
   public int ice_emperor_meteor_effect_duration_ticks = 100;
   public int ice_emperor_meteor_ice_duration_ticks = 1200;
   public int ice_emperor_guard_count = 1;
   public int ice_emperor_guard_cooldown_ticks = 1200;
   public double ice_emperor_guard_death_damage = 4.0;
   public double ice_emperor_guard_low_energy_speed_multiplier = 0.6;
   public double ice_emperor_guard_low_energy_damage_multiplier = 0.5;
   public double ice_emperor_guard_hostile_chance_percent = 10.0;
   public double ice_emperor_heat_blast_min_energy = 10.0;
   public double ice_emperor_heat_blast_radius = 12.0;
   public int ice_emperor_heat_blast_fire_seconds = 5;
   public int ice_emperor_heat_blast_slowness_ticks = 120;
   public int ice_emperor_heat_blast_slowness_amplifier = 2;
   public boolean ice_emperor_forms_enabled = true;
   public boolean ice_emperor_form_armoured_enabled = true;
   public boolean ice_emperor_form_stealth_enabled = true;
   public boolean ice_emperor_form_assassin_enabled = true;
   public int ice_emperor_form_switch_keycode = 75;
   public String ice_emperor_default_form = "ARMOURED";
   public boolean ice_emperor_form_persist_through_death = true;
   public boolean ice_emperor_form_persist_through_reconnect = true;
   public int ice_emperor_armoured_resistance_amplifier = 1;
   public double ice_emperor_armoured_movement_multiplier = 0.9;
   public double ice_emperor_armoured_outgoing_damage_multiplier = 0.9;
   public double ice_emperor_stealth_detection_radius = 10.0;
   public int ice_emperor_stealth_update_interval_ticks = 5;
   public boolean ice_emperor_stealth_allies_count = true;
   public boolean ice_emperor_forms_particles_enabled = true;
   public boolean ice_emperor_forms_sound_enabled = true;
   public boolean ice_emperor_forms_action_bar_enabled = true;
   public double ice_emperor_assassin_movement_multiplier = 1.25;
   public double ice_emperor_assassin_melee_damage_multiplier = 1.3;
   public double ice_emperor_assassin_max_health_multiplier = 0.8;
   public boolean ice_emperor_assassin_ice_trail_enabled = true;
   public boolean power_visual_effects_enabled = true;
   public double power_visual_effect_intensity = 1.0;
   public boolean power_visual_screen_shake_enabled = true;
   public boolean power_visual_fov_punch_enabled = true;
   public boolean power_visual_sound_layers_enabled = true;
   public boolean power_visual_custom_particles_enabled = true;
   public boolean global_health_enabled = true;
   public double global_player_health_value = 40.0;
   public double energy_max = 100.0;
   public double energy_regen_per_second = 5.0;
   public double energy_cost_primary = 10.0;
   public double energy_cost_secondary = 15.0;
   public double energy_cost_tertiary = 20.0;
   public double energy_cost_quaternary = 20.0;
   public double energy_cost_alternate = 5.0;
   public double energy_cost_shadow_archer_arrow = 8.0;
   public int energy_bleeding_initial_ticks = 80;
   public int energy_bleeding_max_ticks = 160;
   public double energy_bleeding_damage_per_second = 1.0;
   public double energy_low_threshold_percent = 0.25;
   public int energy_hud_x = 10;
   public int energy_hud_bottom_offset = 78;
   public int goat_overdrive_duration_ticks = 100;
   public int goat_overdrive_cooldown_ticks = 2400;
   public double goat_overdrive_energy_cost = 80.0;
   public double goat_overdrive_movement_multiplier = 3.0;
   public double goat_overdrive_jump_multiplier = 4.0;
   public double goat_overdrive_knockback_resistance_bonus = 0.6;
   public double goat_overdrive_max_damage_debt = 200.0;
   public int goat_overdrive_debt_release_interval_ticks = 10;
   public double goat_overdrive_debt_release_per_hit = 1.0;
   public int time_warper_black_hole_charge_ticks = 40;
   public int time_warper_black_hole_pull_ticks = 80;
   public int time_warper_black_hole_recollapse_ticks = 30;
   public double time_warper_black_hole_entity_radius = 20.0;
   public double time_warper_black_hole_terrain_radius = 8.0;
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
   public double time_warper_black_hole_items_for_max_power = 256.0;
   public double time_warper_black_hole_max_power_multiplier = 2.0;
   public double time_warper_black_hole_block_item_value = 0.02;
   public double time_warper_black_hole_max_blast_damage = 12.0;
   public int time_warper_black_hole_max_blocks_per_tick = 16;
   public int time_warper_awakening_sustain_max_ticks = 600;
   public double time_warper_awakening_sustain_energy_per_second = 1.0;
   public double time_warper_awakening_sustain_health_per_second = 0.25;
   public double time_warper_awakening_sustain_radius = 8.0;
   public double time_warper_domain_energy_cost = 40.0;
   public int time_warper_domain_cooldown_ticks = 18000;
   public double time_warper_domain_capture_radius = 16.0;
   public int boots_raigo_charge_ticks = 60;
   public double boots_raigo_range = 80.0;
   public double boots_raigo_radius = 12.0;
   public double boots_raigo_max_damage = 16.0;
   public int boots_raigo_cooldown_ticks = 3600;
   public double boots_raigo_energy_cost = 75.0;
   public boolean boots_raigo_terrain_damage = false;
   public int boots_raigo_max_blocks_per_tick = 64;
   public int thor_riptide_input_buffer_ticks = 10;
   public int thor_riptide_chain_delay_ticks = 6;
   public int thor_riptide_steering_ticks = 15;
   public double thor_riptide_air_steering = 0.15;
   public double thor_riptide_max_velocity = 3.2;
   public double thor_riptide_energy_cost = 10.0;
   public double summon_health_multiplier = 1.6;
   public double summon_damage_multiplier = 1.6;
   public double summon_armor_bonus = 10.0;
   public double summon_knockback_resistance_bonus = 0.6;
   public double summon_movement_multiplier = 1.15;
   public double summon_follow_range = 48.0;
   public boolean summon_regeneration_enabled = true;
   public boolean summon_fire_resistance_enabled = true;
   public boolean summon_equipment_upgrade_enabled = true;
   public boolean gauntlet_spawn_enabled = false;
   public boolean grimoire_spawn_enabled = false;
   public int artifact_relocation_days = 14;
   public int gauntlet_black_mob_influence_radius_blocks = 128;
   public double gauntlet_black_mob_min_spawn_chance = 0.05;
   public double gauntlet_black_mob_max_spawn_chance = 1.0;
   public double awakening_punch_energy_cost = 65.0;
   public int awakening_punch_cooldown_ticks = 1200;
   public double awakening_punch_radius = 12.0;
   public double awakening_punch_damage = 20.0;
   public int awakening_punch_max_blocks = 96;
   public double shadow_awakening_mount_range = 16.0;
   public double shadow_awakening_minion_radius = 32.0;
   public double fallout_awakening_bomb_energy_cost = 50.0;
   public int fallout_awakening_bomb_cooldown_ticks = 1800;
   public double fallout_awakening_bomb_radius = 12.0;
   public double fallout_awakening_bomb_damage = 12.0;
   public int fallout_awakening_bomb_poison_ticks = 200;
   public int fallout_awakening_bomb_poison_amplifier = 0;
   public double fallout_awakening_health_multiplier = 2.0;
   public double fallout_awakening_damage_multiplier = 1.5;
   public double fallout_awakening_speed_multiplier = 1.5;
   public double spidey_awakening_climb_velocity = 0.34;
   public int poseidon_awakening_sink_radius = 256;
   public int poseidon_awakening_sink_columns_per_tick = 1024;
   public double poseidon_awakening_sink_energy_cost = 80.0;
   public int poseidon_awakening_sink_cooldown_ticks = 72000;
   public int poseidon_awakening_sink_item_cleanup_interval_ticks = 10;
   public boolean grimoire_flight_enabled = true;
   public float grimoire_flight_speed = 0.08F;
   public int grimoire_beam_range_blocks = 32;
   public double grimoire_beam_damage = 6.0;
   public int grimoire_beam_damage_interval_ticks = 20;
   public int grimoire_beam_block_break_ticks = 40;
   public boolean combat_tag_enabled = true;
   public int combat_tag_duration_seconds = 30;
   public boolean combat_tag_kill_on_logout = true;
   public boolean combat_tag_block_alliance_changes = true;
   public boolean combat_tag_broadcast_logout = true;
   public boolean energy_hud_enabled = true;
   public String energy_hud_anchor = "BOTTOM_RIGHT";
   public int energy_hud_offset_x = 8;
   public int energy_hud_offset_y = 8;

   public void validate() {
      this.power_visual_effect_intensity = clamp(this.power_visual_effect_intensity, 0.0, 2.0, 1.0);
      this.energy_max = clamp(this.energy_max, 1.0, 100000.0, 100.0);
      this.energy_regen_per_second = clamp(this.energy_regen_per_second, 0.0, 1000.0, 5.0);
      this.energy_cost_primary = clamp(this.energy_cost_primary, 0.0, 100000.0, 10.0);
      this.energy_cost_secondary = clamp(this.energy_cost_secondary, 0.0, 100000.0, 15.0);
      this.energy_cost_tertiary = clamp(this.energy_cost_tertiary, 0.0, 100000.0, 20.0);
      this.energy_cost_quaternary = clamp(this.energy_cost_quaternary, 0.0, 100000.0, 20.0);
      this.energy_cost_alternate = clamp(this.energy_cost_alternate, 0.0, 100000.0, 5.0);
      this.energy_cost_shadow_archer_arrow = clamp(this.energy_cost_shadow_archer_arrow, 0.0, 100000.0, 8.0);
      this.shadow_archer_pinning_duration_ticks = clamp(this.shadow_archer_pinning_duration_ticks, 20, 200, 40);
      this.shadow_archer_tracer_duration_ticks = clamp(this.shadow_archer_tracer_duration_ticks, 200, 12000, 600);
      this.shadow_archer_eclipse_duration_ticks = clamp(this.shadow_archer_eclipse_duration_ticks, 40, 600, 160);
      this.shadow_archer_eclipse_radius = clamp(this.shadow_archer_eclipse_radius, 3.0, 16.0, 8.0);
      this.shadow_archer_phase_max_distance = clamp(this.shadow_archer_phase_max_distance, 16.0, 256.0, 64.0);
      this.spidey_web_projectile_speed = clamp(this.spidey_web_projectile_speed, 0.5, 5.0, 1.8);
      this.spidey_temporary_web_duration_ticks = clamp(this.spidey_temporary_web_duration_ticks, 20, 12000, 200);
      this.global_player_health_value = clamp(this.global_player_health_value, 1.0, 2048.0, 40.0);
      this.energy_bleeding_initial_ticks = clamp(this.energy_bleeding_initial_ticks, 1, 12000, 80);
      this.energy_bleeding_max_ticks = clamp(this.energy_bleeding_max_ticks, this.energy_bleeding_initial_ticks, 12000, 160);
      this.energy_bleeding_damage_per_second = clamp(this.energy_bleeding_damage_per_second, 0.0, 100.0, 1.0);
      this.energy_low_threshold_percent = clamp(this.energy_low_threshold_percent, 0.0, 1.0, 0.25);
      this.energy_hud_x = clamp(this.energy_hud_x, 0, 10000, 10);
      this.energy_hud_bottom_offset = clamp(this.energy_hud_bottom_offset, 20, 1000, 78);
      this.goat_jump_multiplier = clamp(this.goat_jump_multiplier, 1.0, 2.0, 1.25);
      this.goat_max_speed = clamp(this.goat_max_speed, 1.0, 10.0, 9.0);
      this.goat_sprint_start_multiplier = clamp(this.goat_sprint_start_multiplier, 1.0, 10.0, 2.0);
      if (this.goat_sprint_start_multiplier > this.goat_max_speed) {
         this.goat_sprint_start_multiplier = this.goat_max_speed;
      }

      this.goat_sprint_acceleration_ticks_to_max = clamp(this.goat_sprint_acceleration_ticks_to_max, 1, 2400, 300);
      this.goat_awakening_max_speed_ticks = clamp(this.goat_awakening_max_speed_ticks, 1, 2400, 60);
      this.goat_awakening_energy_per_block = clamp(this.goat_awakening_energy_per_block, 0.0, 1000.0, 5.0);
      this.goat_awakening_max_block_hardness = clamp(this.goat_awakening_max_block_hardness, 0.0, 50.0, 2.0);
      this.goat_awakening_break_interval_ticks = clamp(this.goat_awakening_break_interval_ticks, 1, 200, 4);
      this.goat_sprint_reset_delay_ticks = clamp(this.goat_sprint_reset_delay_ticks, 0, 1200, 10);
      this.goat_jump_boost_amplifier = clamp(this.goat_jump_boost_amplifier, 0, 1, 0);
      this.goat_jump_fall_damage_multiplier = clamp(this.goat_jump_fall_damage_multiplier, 0.0, 1.0, 0.0);
      this.goat_passive_fall_damage_multiplier = clamp(this.goat_passive_fall_damage_multiplier, 0.0, 1.0, 0.4);
      this.goat_overdrive_duration_ticks = clamp(this.goat_overdrive_duration_ticks, 1, 12000, 100);
      this.goat_overdrive_cooldown_ticks = clamp(this.goat_overdrive_cooldown_ticks, 0, 120000, 2400);
      this.goat_overdrive_energy_cost = clamp(this.goat_overdrive_energy_cost, 0.0, 100000.0, 80.0);
      this.goat_overdrive_movement_multiplier = clamp(this.goat_overdrive_movement_multiplier, 1.0, 10.0, 3.0);
      this.goat_overdrive_jump_multiplier = clamp(this.goat_overdrive_jump_multiplier, 1.0, 10.0, 4.0);
      this.goat_overdrive_knockback_resistance_bonus = clamp(this.goat_overdrive_knockback_resistance_bonus, 0.0, 1.0, 0.6);
      this.goat_overdrive_max_damage_debt = clamp(this.goat_overdrive_max_damage_debt, 1.0, 10000.0, 200.0);
      this.goat_overdrive_debt_release_interval_ticks = clamp(this.goat_overdrive_debt_release_interval_ticks, 1, 1200, 10);
      this.goat_overdrive_debt_release_per_hit = clamp(this.goat_overdrive_debt_release_per_hit, 0.1, 100.0, 1.0);
      this.time_warper_black_hole_charge_ticks = clamp(this.time_warper_black_hole_charge_ticks, 1, 1200, 40);
      this.time_warper_black_hole_pull_ticks = clamp(this.time_warper_black_hole_pull_ticks, 1, 1200, 80);
      this.time_warper_black_hole_recollapse_ticks = clamp(this.time_warper_black_hole_recollapse_ticks, 10, 100, 30);
      this.time_warper_black_hole_entity_radius = clamp(this.time_warper_black_hole_entity_radius, 1.0, 128.0, 20.0);
      this.time_warper_black_hole_terrain_radius = clamp(this.time_warper_black_hole_terrain_radius, 0.0, 32.0, 8.0);
      this.time_warper_black_hole_implosion_radius = clamp(this.time_warper_black_hole_implosion_radius, 1.0, 64.0, 12.0);
      this.time_warper_black_hole_max_damage = clamp(this.time_warper_black_hole_max_damage, 0.0, 100.0, 10.0);
      this.time_warper_black_hole_cooldown_ticks = clamp(this.time_warper_black_hole_cooldown_ticks, 0, 1200000, 6000);
      this.time_warper_black_hole_health_sacrifice_percent = clamp(this.time_warper_black_hole_health_sacrifice_percent, 0.0, 0.95, 0.5);
      this.time_warper_black_hole_energy_cost = clamp(this.time_warper_black_hole_energy_cost, 0.0, 100000.0, 80.0);
      this.time_warper_black_hole_max_blocks_per_tick = clamp(this.time_warper_black_hole_max_blocks_per_tick, 0, 512, 16);
      this.time_warper_awakening_sustain_max_ticks = clamp(this.time_warper_awakening_sustain_max_ticks, 20, 12000, 600);
      this.time_warper_awakening_sustain_energy_per_second = clamp(this.time_warper_awakening_sustain_energy_per_second, 0.0, 100.0, 1.0);
      this.time_warper_awakening_sustain_health_per_second = clamp(this.time_warper_awakening_sustain_health_per_second, 0.0, 20.0, 0.25);
      this.time_warper_awakening_sustain_radius = clamp(this.time_warper_awakening_sustain_radius, 1.0, 32.0, 8.0);
      this.time_warper_domain_energy_cost = clamp(this.time_warper_domain_energy_cost, 0.0, 100000.0, 40.0);
      this.time_warper_domain_cooldown_ticks = clamp(this.time_warper_domain_cooldown_ticks, 0, 1200000, 18000);
      this.time_warper_domain_capture_radius = clamp(this.time_warper_domain_capture_radius, 4.0, 64.0, 16.0);
      this.spirit_restless_shadows_health = clamp(this.spirit_restless_shadows_health, 1.0, 1024.0, 30.0);
      this.spirit_restless_shadows_darkness_radius = clamp(this.spirit_restless_shadows_darkness_radius, 0.0, 48.0, 12.0);
      this.spirit_restless_shadows_darkness_ticks = clamp(this.spirit_restless_shadows_darkness_ticks, 20, 600, 100);
      this.teleport_gambit_random_portal_charge_ticks = clamp(this.teleport_gambit_random_portal_charge_ticks, 20, 600, 100);
      this.teleport_gambit_random_portal_radius = clamp(this.teleport_gambit_random_portal_radius, 4.0, 64.0, 8.0);
      this.teleport_gambit_random_portal_cooldown_ticks = clamp(this.teleport_gambit_random_portal_cooldown_ticks, 0, 120000, 6000);
      this.teleport_gambit_random_portal_energy_cost = clamp(this.teleport_gambit_random_portal_energy_cost, 0.0, 100000.0, 50.0);
      this.teleport_gambit_random_portal_search_attempts = clamp(this.teleport_gambit_random_portal_search_attempts, 4, 64, 24);
      this.teleport_gambit_awakening_energy_cost = clamp(this.teleport_gambit_awakening_energy_cost, 0.0, 100000.0, 100.0);
      this.teleport_gambit_awakening_cooldown_ticks = clamp(this.teleport_gambit_awakening_cooldown_ticks, 0, 63072000, 1728000);
      this.teleport_gambit_structure_search_radius_chunks = clamp(this.teleport_gambit_structure_search_radius_chunks, 8, 512, 128);
      if (this.teleport_gambit_structure_blacklist == null) {
         this.teleport_gambit_structure_blacklist = new ArrayList<>(List.of("minecraft:stronghold", "minecraft:ancient_city", "minecraft:trial_chambers"));
      }

      this.teleport_gambit_structure_blacklist = this.teleport_gambit_structure_blacklist
         .stream()
         .filter(Objects::nonNull)
         .map(value -> value.trim().toLowerCase(Locale.ROOT))
         .filter(value -> value.length() <= 128 && value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
         .distinct()
         .limit(64L)
         .collect(Collectors.toCollection(ArrayList::new));
      this.fire_god_blaze_beam_energy_per_shot = clamp(this.fire_god_blaze_beam_energy_per_shot, 0.0, 5.0, 1.5);
      this.fire_god_blaze_beam_shot_interval_ticks = clamp(this.fire_god_blaze_beam_shot_interval_ticks, 1, 40, 5);
      this.fire_god_blaze_beam_projectile_speed = clamp(this.fire_god_blaze_beam_projectile_speed, 0.1, 6.0, 1.6);
      this.fire_god_wither_skull_energy_cost = clamp(this.fire_god_wither_skull_energy_cost, 0.0, 100.0, 18.0);
      this.fire_god_wither_skull_cooldown_ticks = clamp(this.fire_god_wither_skull_cooldown_ticks, 0, 12000, 100);
      this.fire_god_wither_skull_damage = clamp(this.fire_god_wither_skull_damage, 0.0, 40.0, 6.0);
      this.fire_god_wither_skull_wither_ticks = clamp(this.fire_god_wither_skull_wither_ticks, 0, 1200, 60);
      this.fire_god_wither_skull_projectile_speed = clamp(this.fire_god_wither_skull_projectile_speed, 0.1, 6.0, 1.5);
      this.fire_god_wither_skull_explosion_power = clamp(this.fire_god_wither_skull_explosion_power, 0.0, 4.0, 1.0);
      this.poseidon_high_speed_swim_multiplier = clamp(this.poseidon_high_speed_swim_multiplier, 0.0, 40.0, 2.0);
      this.kitsune_carry_range = clamp(this.kitsune_carry_range, 3.0, 24.0, 12.0);
      this.kitsune_transformation_speed_multiplier = clamp(this.kitsune_transformation_speed_multiplier, 1.0, 4.0, 2.0);
      this.kitsune_carry_enemy_damage_per_second = clamp(this.kitsune_carry_enemy_damage_per_second, 0.0, 40.0, 2.0);
      this.kitsune_tunnel_blocks_per_tick = clamp(this.kitsune_tunnel_blocks_per_tick, 1, 12, 6);
      this.illusionist_cloud_flight_energy_cost = clamp(this.illusionist_cloud_flight_energy_cost, 0.0, 100000.0, 25.0);
      this.illusionist_cloud_flight_duration_ticks = clamp(this.illusionist_cloud_flight_duration_ticks, 40, 12000, 600);
      this.illusionist_cloud_flight_cooldown_ticks = clamp(this.illusionist_cloud_flight_cooldown_ticks, 0, 120000, 1200);
      this.illusionist_celestial_moon_radius = clamp(this.illusionist_celestial_moon_radius, 2.0, 10.0, 5.0);
      this.illusionist_celestial_moon_scale = clamp(this.illusionist_celestial_moon_scale, 0.5, 3.0, 1.6);
      this.illusionist_celestial_moon_max_blocks = clamp(this.illusionist_celestial_moon_max_blocks, 100, 1500, 480);
      this.illusionist_celestial_moon_height = clamp(this.illusionist_celestial_moon_height, 10.0, 60.0, 22.0);
      this.illusionist_celestial_weightless_radius = clamp(this.illusionist_celestial_weightless_radius, 8.0, 128.0, 48.0);
      this.illusionist_celestial_crater_radius = clamp(this.illusionist_celestial_crater_radius, 4.0, 40.0, 16.0);
      this.illusionist_celestial_crater_damage = clamp(this.illusionist_celestial_crater_damage, 0.0, 500.0, 45.0);
      this.time_warper_black_hole_items_for_max_power = clamp(this.time_warper_black_hole_items_for_max_power, 1.0, 100000.0, 256.0);
      this.time_warper_black_hole_max_power_multiplier = clamp(this.time_warper_black_hole_max_power_multiplier, 1.0, 6.0, 2.0);
      this.time_warper_black_hole_block_item_value = clamp(this.time_warper_black_hole_block_item_value, 0.0, 10.0, 0.02);
      this.time_warper_black_hole_max_blast_damage = clamp(this.time_warper_black_hole_max_blast_damage, 0.0, 200.0, 12.0);
      this.boots_raigo_charge_ticks = clamp(this.boots_raigo_charge_ticks, 1, 1200, 60);
      this.boots_raigo_range = clamp(this.boots_raigo_range, 1.0, 256.0, 80.0);
      this.boots_raigo_radius = clamp(this.boots_raigo_radius, 1.0, 128.0, 12.0);
      this.boots_raigo_max_damage = clamp(this.boots_raigo_max_damage, 0.0, 100.0, 16.0);
      this.boots_raigo_cooldown_ticks = clamp(this.boots_raigo_cooldown_ticks, 0, 1200000, 3600);
      this.boots_raigo_energy_cost = clamp(this.boots_raigo_energy_cost, 0.0, 100000.0, 75.0);
      this.boots_flight_energy_drain_per_second = clamp(this.boots_flight_energy_drain_per_second, 0.0, 100.0, 0.1);
      this.ice_emperor_guard_count = clamp(this.ice_emperor_guard_count, 1, 6, 1);
      this.ice_emperor_guard_cooldown_ticks = clamp(this.ice_emperor_guard_cooldown_ticks, 0, 120000, 1200);
      this.ice_emperor_guard_death_damage = clamp(this.ice_emperor_guard_death_damage, 0.0, 100.0, 4.0);
      this.ice_emperor_guard_low_energy_speed_multiplier = clamp(this.ice_emperor_guard_low_energy_speed_multiplier, 0.1, 1.0, 0.6);
      this.ice_emperor_guard_low_energy_damage_multiplier = clamp(this.ice_emperor_guard_low_energy_damage_multiplier, 0.1, 1.0, 0.5);
      this.ice_emperor_guard_hostile_chance_percent = clamp(this.ice_emperor_guard_hostile_chance_percent, 0.0, 100.0, 10.0);
      this.ice_emperor_heat_blast_min_energy = clamp(this.ice_emperor_heat_blast_min_energy, 1.0, 100000.0, 10.0);
      this.ice_emperor_heat_blast_radius = clamp(this.ice_emperor_heat_blast_radius, 1.0, 64.0, 12.0);
      this.ice_emperor_heat_blast_fire_seconds = clamp(this.ice_emperor_heat_blast_fire_seconds, 1, 30, 5);
      this.ice_emperor_heat_blast_slowness_ticks = clamp(this.ice_emperor_heat_blast_slowness_ticks, 1, 1200, 120);
      this.ice_emperor_heat_blast_slowness_amplifier = clamp(this.ice_emperor_heat_blast_slowness_amplifier, 0, 4, 2);
      this.ice_emperor_form_switch_keycode = clamp(this.ice_emperor_form_switch_keycode, 32, 348, 75);
      this.ice_emperor_armoured_resistance_amplifier = clamp(this.ice_emperor_armoured_resistance_amplifier, 0, 4, 1);
      this.ice_emperor_armoured_movement_multiplier = clamp(this.ice_emperor_armoured_movement_multiplier, 0.1, 1.0, 0.9);
      this.ice_emperor_armoured_outgoing_damage_multiplier = clamp(this.ice_emperor_armoured_outgoing_damage_multiplier, 0.1, 1.0, 0.9);
      this.ice_emperor_stealth_detection_radius = clamp(this.ice_emperor_stealth_detection_radius, 1.0, 128.0, 10.0);
      this.ice_emperor_stealth_update_interval_ticks = clamp(this.ice_emperor_stealth_update_interval_ticks, 1, 200, 5);
      this.ice_emperor_assassin_movement_multiplier = clamp(this.ice_emperor_assassin_movement_multiplier, 1.0, 3.0, 1.25);
      this.ice_emperor_assassin_melee_damage_multiplier = clamp(this.ice_emperor_assassin_melee_damage_multiplier, 1.0, 3.0, 1.3);
      this.ice_emperor_assassin_max_health_multiplier = clamp(this.ice_emperor_assassin_max_health_multiplier, 0.1, 1.0, 0.8);
      if (this.ice_emperor_default_form == null) {
         this.ice_emperor_default_form = "ARMOURED";
      }

      this.ice_emperor_default_form = this.ice_emperor_default_form.trim().toUpperCase(Locale.ROOT);
      if (!Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(this.ice_emperor_default_form)) {
         this.ice_emperor_default_form = "ARMOURED";
      }

      if (!this.ice_emperor_form_armoured_enabled && !this.ice_emperor_form_stealth_enabled && !this.ice_emperor_form_assassin_enabled) {
         this.ice_emperor_default_form = "ARMOURED";
      } else if (this.ice_emperor_default_form.equals("ARMOURED") && !this.ice_emperor_form_armoured_enabled) {
         this.ice_emperor_default_form = this.ice_emperor_form_stealth_enabled ? "STEALTH" : "ASSASSIN";
      } else if (this.ice_emperor_default_form.equals("STEALTH") && !this.ice_emperor_form_stealth_enabled) {
         this.ice_emperor_default_form = this.ice_emperor_form_armoured_enabled ? "ARMOURED" : "ASSASSIN";
      } else if (this.ice_emperor_default_form.equals("ASSASSIN") && !this.ice_emperor_form_assassin_enabled) {
         this.ice_emperor_default_form = this.ice_emperor_form_armoured_enabled ? "ARMOURED" : "STEALTH";
      }

      this.poseidon_water_regeneration_amplifier = clamp(this.poseidon_water_regeneration_amplifier, 0, 9, 0);
      this.poseidon_water_haste_amplifier = clamp(this.poseidon_water_haste_amplifier, 0, 3, 2);
      this.poseidon_water_resistance_amplifier = clamp(this.poseidon_water_resistance_amplifier, 0, 2, 0);
      this.poseidon_water_strength_amplifier = clamp(this.poseidon_water_strength_amplifier, 0, 2, 0);
      this.awakening_punch_energy_cost = clamp(this.awakening_punch_energy_cost, 0.0, 100000.0, 65.0);
      this.awakening_punch_cooldown_ticks = clamp(this.awakening_punch_cooldown_ticks, 0, 1200000, 1200);
      this.awakening_punch_radius = clamp(this.awakening_punch_radius, 2.0, 32.0, 12.0);
      this.awakening_punch_damage = clamp(this.awakening_punch_damage, 0.0, 200.0, 20.0);
      this.awakening_punch_max_blocks = clamp(this.awakening_punch_max_blocks, 0, 512, 96);
      this.shadow_awakening_mount_range = clamp(this.shadow_awakening_mount_range, 3.0, 64.0, 16.0);
      this.shadow_awakening_minion_radius = clamp(this.shadow_awakening_minion_radius, 16.0, 128.0, 32.0);
      this.fallout_awakening_bomb_energy_cost = clamp(this.fallout_awakening_bomb_energy_cost, 0.0, 100000.0, 50.0);
      this.fallout_awakening_bomb_cooldown_ticks = clamp(this.fallout_awakening_bomb_cooldown_ticks, 0, 1200000, 1800);
      this.fallout_awakening_bomb_radius = clamp(this.fallout_awakening_bomb_radius, 2.0, 48.0, 12.0);
      this.fallout_awakening_bomb_damage = clamp(this.fallout_awakening_bomb_damage, 0.0, 200.0, 12.0);
      this.fallout_awakening_bomb_poison_ticks = clamp(this.fallout_awakening_bomb_poison_ticks, 20, 2400, 200);
      this.fallout_awakening_bomb_poison_amplifier = clamp(this.fallout_awakening_bomb_poison_amplifier, 0, 4, 0);
      this.fallout_awakening_health_multiplier = clamp(this.fallout_awakening_health_multiplier, 1.0, 20.0, 2.0);
      this.fallout_awakening_damage_multiplier = clamp(this.fallout_awakening_damage_multiplier, 1.0, 20.0, 1.5);
      this.fallout_awakening_speed_multiplier = clamp(this.fallout_awakening_speed_multiplier, 1.0, 5.0, 1.5);
      this.spidey_awakening_climb_velocity = clamp(this.spidey_awakening_climb_velocity, 0.1, 1.5, 0.34);
      this.poseidon_awakening_sink_radius = clamp(this.poseidon_awakening_sink_radius, 64, 512, 256);
      this.poseidon_awakening_sink_columns_per_tick = clamp(this.poseidon_awakening_sink_columns_per_tick, 512, 16384, 1024);
      this.poseidon_awakening_sink_energy_cost = clamp(this.poseidon_awakening_sink_energy_cost, 0.0, 100000.0, 80.0);
      this.poseidon_awakening_sink_cooldown_ticks = clamp(this.poseidon_awakening_sink_cooldown_ticks, 0, 1200000, 72000);
      this.poseidon_awakening_sink_item_cleanup_interval_ticks = clamp(this.poseidon_awakening_sink_item_cleanup_interval_ticks, 1, 200, 10);
      if (this.poseidon_water_condition == null) {
         this.poseidon_water_condition = "TOUCHING";
      }

      this.poseidon_water_condition = this.poseidon_water_condition.trim().toUpperCase(Locale.ROOT);
      if (!Set.of("TOUCHING", "SUBMERGED", "SWIMMING_OR_SUBMERGED").contains(this.poseidon_water_condition)) {
         this.poseidon_water_condition = "TOUCHING";
      }

      this.poseidon_tsunami_cooldown_ticks = clamp(this.poseidon_tsunami_cooldown_ticks, 0, 1200000, 2400);
      this.poseidon_tsunami_search_radius = clamp(this.poseidon_tsunami_search_radius, 1.0, 100.0, 100.0);
      this.poseidon_tsunami_wave_radius = clamp(this.poseidon_tsunami_wave_radius, 1.0, 64.0, 18.0);
      this.poseidon_tsunami_wave_duration_ticks = clamp(this.poseidon_tsunami_wave_duration_ticks, 10, 100, 36);
      this.poseidon_tsunami_damage = clamp(this.poseidon_tsunami_damage, 0.0, 100.0, 8.0);
      this.poseidon_tsunami_stun_ticks = clamp(this.poseidon_tsunami_stun_ticks, 0, 1200, 20);
      this.poseidon_tsunami_buff_ticks = clamp(this.poseidon_tsunami_buff_ticks, 1, 12000, 600);
      this.poseidon_tsunami_buff_regeneration_amplifier = clamp(this.poseidon_tsunami_buff_regeneration_amplifier, 0, 9, 2);
      this.thor_riptide_min_charge_ticks = clamp(this.thor_riptide_min_charge_ticks, 0, 1200, 10);
      this.thor_riptide_strength = clamp(this.thor_riptide_strength, 0.1, 10.0, 2.5);
      this.thor_throw_speed = clamp(this.thor_throw_speed, 0.1, 10.0, 1.65);
      this.thor_crater_radius = clamp(this.thor_crater_radius, 1, 12, 4);
      this.thor_crater_depth = clamp(this.thor_crater_depth, 1, 12, 3);
      this.thor_impact_damage = clamp(this.thor_impact_damage, 0.0, 100.0, 12.0);
      this.thor_impact_radius = clamp(this.thor_impact_radius, 0.0, 64.0, 5.0);
      this.thor_riptide_input_buffer_ticks = clamp(this.thor_riptide_input_buffer_ticks, 0, 100, 10);
      this.thor_riptide_chain_delay_ticks = clamp(this.thor_riptide_chain_delay_ticks, 0, 1200, 6);
      this.thor_riptide_steering_ticks = clamp(this.thor_riptide_steering_ticks, 0, 1200, 15);
      this.thor_riptide_air_steering = clamp(this.thor_riptide_air_steering, 0.0, 1.0, 0.15);
      this.thor_riptide_max_velocity = clamp(this.thor_riptide_max_velocity, 0.1, 10.0, 3.2);
      this.thor_riptide_energy_cost = clamp(this.thor_riptide_energy_cost, 0.0, 100000.0, 10.0);
      this.divinity_shield_radius = clamp(this.divinity_shield_radius, 4, 32, 12);
      this.divinity_shield_damage_reduction = clamp(this.divinity_shield_damage_reduction, 0.0, 0.9, 0.4);
      this.divinity_stored_damage_cap = clamp(this.divinity_stored_damage_cap, 0.0, 1000.0, 40.0);
      this.divinity_reflect_range = clamp(this.divinity_reflect_range, 4.0, 64.0, 32.0);
      this.divinity_reflect_damage_multiplier = clamp(this.divinity_reflect_damage_multiplier, 0.0, 10.0, 0.75);
      this.divinity_reflect_cooldown_ticks = clamp(this.divinity_reflect_cooldown_ticks, 0, 120000, 200);
      this.divinity_deflected_projectile_charge = clamp(this.divinity_deflected_projectile_charge, 0.0, 50.0, 1.0);
      this.divinity_shield_block_charge_fraction = clamp(this.divinity_shield_block_charge_fraction, 0.0, 1.0, 0.2);
      this.stick_of_deletion_swap_range = clamp(this.stick_of_deletion_swap_range, 4.0, 64.0, 24.0);
      this.stick_of_deletion_swap_cooldown_ticks = clamp(this.stick_of_deletion_swap_cooldown_ticks, 0, 120000, 160);
      this.stick_of_deletion_swap_energy_cost = clamp(this.stick_of_deletion_swap_energy_cost, 0.0, 100000.0, 15.0);
      this.shadow_archer_bow_default_mode = this.shadow_archer_bow_default_mode == null
         ? "INFINITY"
         : this.shadow_archer_bow_default_mode.trim().toUpperCase(Locale.ROOT);
      if (!Set.of("INFINITY", "MENDING").contains(this.shadow_archer_bow_default_mode)) {
         this.shadow_archer_bow_default_mode = "INFINITY";
      }

      this.shadow_archer_bow_power_level = clamp(this.shadow_archer_bow_power_level, 0, 10, 5);
      this.shadow_archer_bow_punch_level = clamp(this.shadow_archer_bow_punch_level, 0, 10, 2);
      this.shadow_archer_bow_flame_level = clamp(this.shadow_archer_bow_flame_level, 0, 10, 1);
      this.shadow_archer_bow_unbreaking_level = clamp(this.shadow_archer_bow_unbreaking_level, 0, 10, 3);
      this.shadow_archer_arrow_swap_cooldown_ticks = clamp(this.shadow_archer_arrow_swap_cooldown_ticks, 0, 120000, 120);
      this.shadow_archer_arrow_swap_energy_cost = clamp(this.shadow_archer_arrow_swap_energy_cost, 0.0, 100000.0, 15.0);
      this.shadow_archer_arrow_swap_max_distance = clamp(this.shadow_archer_arrow_swap_max_distance, 8.0, 256.0, 64.0);
      this.summon_health_multiplier = clamp(this.summon_health_multiplier, 0.1, 10.0, 1.6);
      this.summon_damage_multiplier = clamp(this.summon_damage_multiplier, 0.1, 10.0, 1.6);
      this.summon_armor_bonus = clamp(this.summon_armor_bonus, 0.0, 30.0, 10.0);
      this.summon_knockback_resistance_bonus = clamp(this.summon_knockback_resistance_bonus, 0.0, 1.0, 0.6);
      this.summon_movement_multiplier = clamp(this.summon_movement_multiplier, 0.1, 5.0, 1.15);
      this.summon_follow_range = clamp(this.summon_follow_range, 8.0, 128.0, 48.0);
      this.artifact_relocation_days = clamp(this.artifact_relocation_days, 1, 365, 14);
      this.gauntlet_black_mob_influence_radius_blocks = clamp(this.gauntlet_black_mob_influence_radius_blocks, 16, 2048, 128);
      this.gauntlet_black_mob_min_spawn_chance = clamp(this.gauntlet_black_mob_min_spawn_chance, 0.0, 1.0, 0.05);
      this.gauntlet_black_mob_max_spawn_chance = clamp(this.gauntlet_black_mob_max_spawn_chance, this.gauntlet_black_mob_min_spawn_chance, 1.0, 1.0);
      this.grimoire_flight_speed = (float)clamp((double)this.grimoire_flight_speed, 0.01, 0.5, 0.08);
      this.grimoire_beam_range_blocks = clamp(this.grimoire_beam_range_blocks, 4, 128, 32);
      this.grimoire_beam_damage = clamp(this.grimoire_beam_damage, 0.0, 100.0, 6.0);
      this.grimoire_beam_damage_interval_ticks = clamp(this.grimoire_beam_damage_interval_ticks, 1, 1200, 20);
      this.grimoire_beam_block_break_ticks = clamp(this.grimoire_beam_block_break_ticks, 1, 1200, 40);
      this.combat_tag_duration_seconds = clamp(this.combat_tag_duration_seconds, 5, 300, 30);
      this.energy_hud_offset_x = clamp(this.energy_hud_offset_x, 0, 2000, 8);
      this.energy_hud_offset_y = clamp(this.energy_hud_offset_y, 0, 2000, 8);
      if (this.energy_hud_anchor == null) {
         this.energy_hud_anchor = "BOTTOM_RIGHT";
      }
      this.energy_hud_anchor = this.energy_hud_anchor.trim().toUpperCase(Locale.ROOT);
      if (!Set.of("TOP_LEFT", "TOP_RIGHT", "TOP_CENTER", "BOTTOM_LEFT", "BOTTOM_RIGHT").contains(this.energy_hud_anchor)) {
         this.energy_hud_anchor = "BOTTOM_RIGHT";
      }
   }

   private static double clamp(double value, double min, double max, double fallback) {
      return !Double.isFinite(value) ? fallback : Math.max(min, Math.min(max, value));
   }

   private static int clamp(int value, int min, int max, int fallback) {
      return Math.max(min, Math.min(max, value));
   }

   public static synchronized YPConfig getInstance() {
      if (instance == null) {
         instance = new YPConfig();
      }

      return instance;
   }

   public synchronized void load() {
      YPConfig defaults = new YPConfig();
      if (FILE.isFile()) {
         try (FileReader reader = new FileReader(FILE)) {
            JsonElement root = JsonParser.parseReader(reader);
            YPConfig loaded = (YPConfig)GSON.fromJson(root, YPConfig.class);
            if (loaded != null) {
               JsonObject json = root != null && root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();
               int loadedVersion = json.has("config_version") && json.get("config_version").isJsonPrimitive() ? json.get("config_version").getAsInt() : 0;
               if (loadedVersion < 2) {
                  if (loaded.time_warp_rewind_cooldown_ticks == 0) {
                     loaded.time_warp_rewind_cooldown_ticks = 600;
                  }

                  if (loaded.teleport_gambit_gateway_cooldown_ticks == 0) {
                     loaded.teleport_gambit_gateway_cooldown_ticks = 300;
                  }
               }

               if (loadedVersion < 10) {
                  if (Math.abs(loaded.goat_jump_multiplier - 2.2) < 1.0E-6) {
                     loaded.goat_jump_multiplier = 1.25;
                  }

                  if (Math.abs(loaded.goat_max_speed - 2.2) < 1.0E-6) {
                     loaded.goat_max_speed = 5.0;
                  }
               }

               if (loadedVersion < 12) {
                  if (loaded.thor_mace_wind_burst_level == 3) {
                     loaded.thor_mace_wind_burst_level = 1;
                  }

                  if (loaded.thor_riptide_min_charge_ticks == 12) {
                     loaded.thor_riptide_min_charge_ticks = 10;
                  }

                  if (Math.abs(loaded.thor_riptide_strength - 2.35) < 1.0E-6) {
                     loaded.thor_riptide_strength = 3.0;
                  }
               }

               if (loadedVersion < 17 && loaded.poseidon_water_haste_amplifier == 0) {
                  loaded.poseidon_water_haste_amplifier = 2;
               }

               if (loadedVersion < 19 && Math.abs(loaded.poseidon_high_speed_swim_multiplier - 5.0) < 1.0E-6) {
                  loaded.poseidon_high_speed_swim_multiplier = 24.0;
               }

               if (loadedVersion < 21) {
                  if (Math.abs(loaded.goat_max_speed - 5.0) < 1.0E-6) {
                     loaded.goat_max_speed = 9.0;
                  }

                  if (loaded.goat_sprint_acceleration_ticks_to_max == 100) {
                     loaded.goat_sprint_acceleration_ticks_to_max = 300;
                  }
               }

               if (loadedVersion < 24) {
                  loaded.gauntlet_spawn_enabled = false;
                  loaded.grimoire_spawn_enabled = false;
               }

               if (loadedVersion < 25 && loaded.energy_hud_bottom_offset == 61) {
                  loaded.energy_hud_bottom_offset = 78;
               }

               if (loadedVersion < 28) {
                  if (!json.has("power_visual_effects_enabled")) {
                     loaded.power_visual_effects_enabled = true;
                  }

                  if (!json.has("power_visual_effect_intensity")) {
                     loaded.power_visual_effect_intensity = 1.0;
                  }
               }

               if (loadedVersion < 31) {
                  if (loaded.divinity_shield_radius == 16) {
                     loaded.divinity_shield_radius = 12;
                  }

                  if (Math.abs(loaded.divinity_stored_damage_cap - 80.0) < 1.0E-6) {
                     loaded.divinity_stored_damage_cap = 40.0;
                  }

                  if (Math.abs(loaded.divinity_reflect_damage_multiplier - 1.0) < 1.0E-6) {
                     loaded.divinity_reflect_damage_multiplier = 0.75;
                  }

                  if (loaded.divinity_reflect_cooldown_ticks == 100) {
                     loaded.divinity_reflect_cooldown_ticks = 200;
                  }

                  if (Math.abs(loaded.divinity_deflected_projectile_charge - 4.0) < 1.0E-6) {
                     loaded.divinity_deflected_projectile_charge = 1.0;
                  }

                  if (Math.abs(loaded.divinity_shield_block_charge_fraction - 0.35) < 1.0E-6) {
                     loaded.divinity_shield_block_charge_fraction = 0.2;
                  }

                  if (Math.abs(loaded.goat_max_speed - 9.0) < 1.0E-6) {
                     loaded.goat_max_speed = 5.0;
                  }

                  if (Math.abs(loaded.goat_awakening_energy_per_block - 1.0) < 1.0E-6) {
                     loaded.goat_awakening_energy_per_block = 5.0;
                  }

                  if (Math.abs(loaded.goat_awakening_max_block_hardness - 4.0) < 1.0E-6) {
                     loaded.goat_awakening_max_block_hardness = 2.0;
                  }

                  if (loaded.goat_awakening_break_interval_ticks == 2) {
                     loaded.goat_awakening_break_interval_ticks = 4;
                  }

                  if (loaded.goat_overdrive_duration_ticks == 300) {
                     loaded.goat_overdrive_duration_ticks = 100;
                  }

                  if (loaded.goat_overdrive_cooldown_ticks == 1800) {
                     loaded.goat_overdrive_cooldown_ticks = 2400;
                  }

                  if (Math.abs(loaded.goat_overdrive_energy_cost - 60.0) < 1.0E-6) {
                     loaded.goat_overdrive_energy_cost = 80.0;
                  }

                  if (loaded.time_stop_max_duration_ticks == 120) {
                     loaded.time_stop_max_duration_ticks = 80;
                  }

                  if (loaded.time_stop_cooldown_ticks == 600) {
                     loaded.time_stop_cooldown_ticks = 1200;
                  }

                  if (loaded.time_warp_rewind_cooldown_ticks == 600) {
                     loaded.time_warp_rewind_cooldown_ticks = 2400;
                  }

                  if (loaded.teleport_gambit_gateway_cooldown_ticks == 300) {
                     loaded.teleport_gambit_gateway_cooldown_ticks = 600;
                  }

                  if (Math.abs(loaded.teleport_gambit_random_portal_radius - 12.0) < 1.0E-6) {
                     loaded.teleport_gambit_random_portal_radius = 8.0;
                  }

                  if (loaded.poseidon_water_regeneration_amplifier == 4) {
                     loaded.poseidon_water_regeneration_amplifier = 0;
                  }

                  if (Math.abs(loaded.poseidon_high_speed_swim_multiplier - 24.0) < 1.0E-6) {
                     loaded.poseidon_high_speed_swim_multiplier = 2.0;
                  }

                  if (loaded.poseidon_tsunami_stun_ticks == 50) {
                     loaded.poseidon_tsunami_stun_ticks = 20;
                  }

                  if (loaded.poseidon_awakening_sink_radius == 500) {
                     loaded.poseidon_awakening_sink_radius = 256;
                  }

                  if (loaded.poseidon_awakening_sink_columns_per_tick == 4096) {
                     loaded.poseidon_awakening_sink_columns_per_tick = 1024;
                  }

                  if (Math.abs(loaded.thor_riptide_strength - 3.0) < 1.0E-6) {
                     loaded.thor_riptide_strength = 2.5;
                  }

                  if (Math.abs(loaded.stick_of_deletion_swap_range - 32.0) < 1.0E-6) {
                     loaded.stick_of_deletion_swap_range = 24.0;
                  }

                  if (loaded.stick_of_deletion_swap_cooldown_ticks == 100) {
                     loaded.stick_of_deletion_swap_cooldown_ticks = 160;
                  }

                  if (Math.abs(loaded.energy_cost_shadow_archer_arrow - 5.0) < 1.0E-6) {
                     loaded.energy_cost_shadow_archer_arrow = 8.0;
                  }

                  if (loaded.shadow_archer_arrow_swap_cooldown_ticks == 80) {
                     loaded.shadow_archer_arrow_swap_cooldown_ticks = 120;
                  }

                  if (Math.abs(loaded.shadow_archer_arrow_swap_energy_cost - 10.0) < 1.0E-6) {
                     loaded.shadow_archer_arrow_swap_energy_cost = 15.0;
                  }

                  if (Math.abs(loaded.shadow_archer_arrow_swap_max_distance - 96.0) < 1.0E-6) {
                     loaded.shadow_archer_arrow_swap_max_distance = 64.0;
                  }

                  if (loaded.shadow_archer_pinning_duration_ticks == 60) {
                     loaded.shadow_archer_pinning_duration_ticks = 40;
                  }

                  if (loaded.shadow_archer_tracer_duration_ticks == 2400) {
                     loaded.shadow_archer_tracer_duration_ticks = 600;
                  }

                  if (Math.abs(loaded.shadow_archer_phase_max_distance - 128.0) < 1.0E-6) {
                     loaded.shadow_archer_phase_max_distance = 64.0;
                  }

                  if (Math.abs(loaded.shadow_awakening_minion_radius - 64.0) < 1.0E-6) {
                     loaded.shadow_awakening_minion_radius = 32.0;
                  }

                  if (Math.abs(loaded.fire_god_blaze_beam_energy_per_shot - 0.12) < 1.0E-6) {
                     loaded.fire_god_blaze_beam_energy_per_shot = 1.5;
                  }

                  if (loaded.fire_god_blaze_beam_shot_interval_ticks == 3) {
                     loaded.fire_god_blaze_beam_shot_interval_ticks = 5;
                  }

                  if (loaded.fire_god_blaze_max_active == 3) {
                     loaded.fire_god_blaze_max_active = 2;
                  }

                  if (loaded.fire_god_blaze_cooldown_ticks == 200) {
                     loaded.fire_god_blaze_cooldown_ticks = 400;
                  }

                  if (loaded.fire_god_wither_skeleton_max_active == 3) {
                     loaded.fire_god_wither_skeleton_max_active = 2;
                  }

                  if (loaded.fire_god_wither_skeleton_cooldown_ticks == 200) {
                     loaded.fire_god_wither_skeleton_cooldown_ticks = 400;
                  }

                  if (Math.abs(loaded.serious_punch_momentum_damage_per_stack - 0.08) < 1.0E-6) {
                     loaded.serious_punch_momentum_damage_per_stack = 0.04;
                  }

                  if (Math.abs(loaded.awakening_punch_damage - 24.0) < 1.0E-6) {
                     loaded.awakening_punch_damage = 20.0;
                  }

                  if (loaded.punch_raycast_range == 60.0) {
                     loaded.punch_raycast_range = 40.0;
                  }

                  if (Math.abs(loaded.spidey_web_sling_velocity - 1.5) < 1.0E-6) {
                     loaded.spidey_web_sling_velocity = 1.0;
                  }

                  if (loaded.spidey_temporary_web_duration_ticks == 600) {
                     loaded.spidey_temporary_web_duration_ticks = 200;
                  }

                  if (loaded.spidey_web_grenade_duration_ticks == 100) {
                     loaded.spidey_web_grenade_duration_ticks = 60;
                  }

                  if (Math.abs(loaded.spidey_awakening_climb_velocity - 0.45) < 1.0E-6) {
                     loaded.spidey_awakening_climb_velocity = 0.34;
                  }

                  if (Math.abs(loaded.fallout_awakening_bomb_radius - 16.0) < 1.0E-6) {
                     loaded.fallout_awakening_bomb_radius = 12.0;
                  }

                  if (loaded.fallout_awakening_bomb_poison_amplifier == 1) {
                     loaded.fallout_awakening_bomb_poison_amplifier = 0;
                  }

                  if (Math.abs(loaded.fallout_awakening_health_multiplier - 4.0) < 1.0E-6) {
                     loaded.fallout_awakening_health_multiplier = 2.0;
                  }

                  if (Math.abs(loaded.fallout_awakening_damage_multiplier - 4.0) < 1.0E-6) {
                     loaded.fallout_awakening_damage_multiplier = 1.5;
                  }

                  if (Math.abs(loaded.time_warper_black_hole_terrain_radius - 12.0) < 1.0E-6) {
                     loaded.time_warper_black_hole_terrain_radius = 8.0;
                  }

                  if (loaded.time_warper_black_hole_max_blocks_per_tick == 64) {
                     loaded.time_warper_black_hole_max_blocks_per_tick = 16;
                  }

                  if (Math.abs(loaded.time_warper_black_hole_max_power_multiplier - 3.0) < 1.0E-6) {
                     loaded.time_warper_black_hole_max_power_multiplier = 2.0;
                  }

                  if (Math.abs(loaded.time_warper_black_hole_max_blast_damage - 24.0) < 1.0E-6) {
                     loaded.time_warper_black_hole_max_blast_damage = 12.0;
                  }

                  if (Math.abs(loaded.time_warper_domain_capture_radius - 24.0) < 1.0E-6) {
                     loaded.time_warper_domain_capture_radius = 16.0;
                  }

                  if (Math.abs(loaded.boots_raigo_radius - 20.0) < 1.0E-6) {
                     loaded.boots_raigo_radius = 12.0;
                  }

                  if (Math.abs(loaded.ice_emperor_meteor_radius - 50.0) < 1.0E-6) {
                     loaded.ice_emperor_meteor_radius = 24.0;
                  }

                  if (loaded.ice_emperor_meteor_cooldown_ticks == 1200) {
                     loaded.ice_emperor_meteor_cooldown_ticks = 2400;
                  }

                  if (loaded.ice_emperor_meteor_effect_duration_ticks == 160) {
                     loaded.ice_emperor_meteor_effect_duration_ticks = 100;
                  }

                  if (loaded.ice_emperor_guard_count == 2) {
                     loaded.ice_emperor_guard_count = 1;
                  }

                  if (loaded.ice_emperor_armoured_resistance_amplifier == 2) {
                     loaded.ice_emperor_armoured_resistance_amplifier = 1;
                  }

                  if (loaded.ice_emperor_freeze_duration_ticks == 50) {
                     loaded.ice_emperor_freeze_duration_ticks = 40;
                  }

                  if (Math.abs(loaded.spirit_restless_shadows_health - 40.0) < 1.0E-6) {
                     loaded.spirit_restless_shadows_health = 30.0;
                  }

                  if (Math.abs(loaded.spirit_restless_shadows_damage - 6.0) < 1.0E-6) {
                     loaded.spirit_restless_shadows_damage = 4.0;
                  }

                  if (Math.abs(loaded.spirit_restless_shadows_darkness_radius - 16.0) < 1.0E-6) {
                     loaded.spirit_restless_shadows_darkness_radius = 12.0;
                  }

                  if (loaded.illusionist_spatial_erasure_cooldown_ticks == 300) {
                     loaded.illusionist_spatial_erasure_cooldown_ticks = 500;
                  }

                  if (Math.abs(loaded.illusionist_spatial_erasure_damage - 10.0) < 1.0E-6) {
                     loaded.illusionist_spatial_erasure_damage = 8.0;
                  }

                  if (loaded.illusionist_spatial_erasure_slowness_ticks == 80) {
                     loaded.illusionist_spatial_erasure_slowness_ticks = 60;
                  }

                  if (Math.abs(loaded.illusionist_grip_range - 15.0) < 1.0E-6) {
                     loaded.illusionist_grip_range = 12.0;
                  }

                  if (loaded.illusionist_grip_release_cooldown_ticks == 100) {
                     loaded.illusionist_grip_release_cooldown_ticks = 160;
                  }

                  if (Math.abs(loaded.kitsune_transformation_speed_multiplier - 3.0) < 1.0E-6) {
                     loaded.kitsune_transformation_speed_multiplier = 2.0;
                  }

                  if (Math.abs(loaded.kitsune_claw_damage - 8.0) < 1.0E-6) {
                     loaded.kitsune_claw_damage = 6.0;
                  }

                  if (loaded.kitsune_claw_cooldown_ticks == 40) {
                     loaded.kitsune_claw_cooldown_ticks = 60;
                  }

                  if (Math.abs(loaded.kitsune_detonation_damage - 10.0) < 1.0E-6) {
                     loaded.kitsune_detonation_damage = 8.0;
                  }

                  if (Math.abs(loaded.kitsune_detonation_radius - 6.0) < 1.0E-6) {
                     loaded.kitsune_detonation_radius = 5.0;
                  }
               }

               if (loadedVersion < 35 && Math.abs(loaded.goat_max_speed - 5.0) < 1.0E-6) {
                  loaded.goat_max_speed = 9.0;
               }

               loaded.config_version = 36;
               defaults = loaded;
            }
         } catch (RuntimeException | IOException var9) {
            System.err.println("[YP Powers] Could not read config; using default values: " + var9.getMessage());
         }
      }

      defaults.config_version = 36;
      defaults.validate();
      instance = defaults;
      defaults.save();
   }

   public synchronized void save() {
      File parent = FILE.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         System.err.println("[YP Powers] Could not create config directory: " + parent);
      } else {
         try (FileWriter writer = new FileWriter(FILE)) {
            writer.write(this.toDocumentedJson());
         } catch (IOException var7) {
            System.err.println("[YP Powers] Could not save config: " + var7.getMessage());
         }
      }
   }

   private String toDocumentedJson() {
      String[] lines = GSON.toJson(this).split("\\R", -1);
      YPConfig defaults = new YPConfig();
      StringBuilder output = new StringBuilder();

      for (String line : lines) {
         String trimmed = line.trim();
         int quote = trimmed.startsWith("\"") ? trimmed.indexOf(34, 1) : -1;
         int colon = quote >= 0 ? trimmed.indexOf(58, quote + 1) : -1;
         if (colon > 0) {
            String key = trimmed.substring(1, quote);
            line = line + " // " + configLineComment(key, defaults);
         }

         output.append(line).append(System.lineSeparator());
      }

      return output.toString();
   }

   private static String configLineComment(String key, YPConfig defaults) {
      String label = humanizeConfigKey(key);
      String explanation;
      if (key.equals("config_version")) {
         explanation = "Internal config format version; do not edit.";
      } else if (key.endsWith("_cooldown_ticks")) {
         explanation = "Wait time between uses, in ticks (20 ticks = 1 second).";
      } else if (key.endsWith("_duration_ticks")) {
         explanation = "How long this effect lasts, in ticks (20 ticks = 1 second).";
      } else if (key.endsWith("_interval_ticks")) {
         explanation = "Time between repeated actions, in ticks (20 ticks = 1 second).";
      } else if (key.endsWith("_ticks")) {
         explanation = "Time or work amount measured in game ticks (20 ticks = 1 second).";
      } else if (key.endsWith("_range_chunks")) {
         explanation = "Search/range limit measured in chunks (1 chunk = 16 blocks).";
      } else if (key.endsWith("_radius") || key.endsWith("_range") || key.endsWith("_distance")) {
         explanation = "Area/range distance measured in blocks.";
      } else if (key.endsWith("_energy_cost") || key.endsWith("_cost_energy") || key.contains("_energy_per_") || key.startsWith("energy_cost_")) {
         explanation = "Energy consumed by this ability/action.";
      } else if (key.endsWith("_damage_per_second")) {
         explanation = "Damage dealt per second (20 game ticks).";
      } else if (key.endsWith("_damage_multiplier")
         || key.endsWith("_speed_multiplier")
         || key.endsWith("_health_multiplier")
         || key.endsWith("_movement_multiplier")
         || key.endsWith("_jump_multiplier")
         || key.endsWith("_multiplier")) {
         explanation = "Multiplier applied to the named value (1.0 = unchanged).";
      } else if (key.endsWith("_damage")) {
         explanation = "Damage in health points (2 points = 1 heart).";
      } else if (key.endsWith("_health")) {
         explanation = "Maximum/current health amount in points (2 points = 1 heart).";
      } else if (key.endsWith("_amplifier")) {
         explanation = "Status-effect strength; 0 = level I, 1 = level II, and so on.";
      } else if (key.endsWith("_level")) {
         explanation = "Enchantment or effect level.";
      } else if (key.equals("goat_max_speed")) {
         explanation = "Maximum GOAT sprint speed relative to a normal sprint (9.0 = nine times normal sprint speed).";
      } else if (key.endsWith("_enabled") || key.endsWith("_enable") || key.endsWith("_enabled_by_default") || defaultsValueIsBoolean(key, defaults)) {
         explanation = "Turn this feature on (true) or off (false).";
      } else if (key.contains("_max_") || key.endsWith("_cap") || key.endsWith("_count")) {
         explanation = "Maximum allowed amount/count for the named feature.";
      } else if (key.endsWith("_speed")) {
         explanation = "Movement/projectile speed setting for the named feature.";
      } else if (key.endsWith("_keycode")) {
         explanation = "Default keyboard key code; players can rebind it in Controls.";
      } else if (defaultsValueIsList(key, defaults)) {
         explanation = "List of values used by the named feature; edit the entries to customize it.";
      } else {
         explanation = "Controls " + label + "; see the Powers Guide for the ability it belongs to.";
      }

      String defaultValue = defaultValueText(key, defaults);
      return label + ": " + explanation + " New-install default: " + defaultValue + ".";
   }

   private static String humanizeConfigKey(String key) {
      StringBuilder words = new StringBuilder();

      for (String part : key.split("_")) {
         if (!part.isEmpty()) {
            if (!words.isEmpty()) {
               words.append(' ');
            }
            String word = switch (part) {
               case "vfx" -> "VFX";
               case "hud" -> "HUD";
               case "hp" -> "HP";
               case "id" -> "ID";
               case "pvp" -> "PvP";
               default -> Character.toUpperCase(part.charAt(0)) + part.substring(1);
            };
            words.append(word);
         }
      }

      return words.toString();
   }

   private static boolean defaultsValueIsBoolean(String key, YPConfig defaults) {
      try {
         return YPConfig.class.getField(key).get(defaults) instanceof Boolean;
      } catch (ReflectiveOperationException var3) {
         return false;
      }
   }

   private static boolean defaultsValueIsList(String key, YPConfig defaults) {
      try {
         return YPConfig.class.getField(key).get(defaults) instanceof List;
      } catch (ReflectiveOperationException var3) {
         return false;
      }
   }

   private static String defaultValueText(String key, YPConfig defaults) {
      try {
         String value = GSON.toJson(YPConfig.class.getField(key).get(defaults));
         if (value.length() > 120) {
            value = value.substring(0, 117) + "...";
         }

         return value;
      } catch (ReflectiveOperationException var3) {
         return "unspecified";
      }
   }
}
