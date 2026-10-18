# YP Powers — Cooldown Configuration (Minecraft 1.21.11)

The config file is created at `config/yppowers_config.json` when the mod starts. New settings are added automatically to existing configs on startup. Edit the file and restart the game/server to apply changes.

**Time units:** values are Minecraft game ticks. There are 20 ticks per second, so `100` means 5 seconds. Cooldown values are clamped to zero or higher. Setting a cooldown to `0` disables that cooldown.

| JSON key | Default | What it controls |
| --- | ---: | --- |
| `divinity_forcefield_cooldown_ticks` | `600` (30 s) | Delay before activating Divinity Shield again. Turning an already-active shield off is still allowed. |
| `time_stop_cooldown_ticks` | `600` (30 s) | Time Stop cooldown. |
| `time_warp_rewind_cooldown_ticks` | `600` (30 s) | Cooldown after Time Warp successfully rewinds at least one player. |
| `teleport_gambit_gateway_cooldown_ticks` | `300` (15 s) | Cooldown after a gateway is successfully created. This timer runs while the gateway is active. |
| `teleport_gambit_gateway_duration_ticks` | `100` (5 s) | How long a Teleport Gambit gateway remains active. Minimum effective duration is 1 tick. |
| `serious_punch_cooldown_ticks` | `200` (10 s) | Serious Punch cooldown. |
| `poseidon_trident_swap_cooldown_ticks` | `60` (3 s) | Cooldown between toggling the special Poseidon trident's enchantment mode. |
| `shadow_archer_morph_cooldown_ticks` | `200` (10 s) | Cooldown after changing or resetting the Shadow Archer morph. |
| `stick_of_deletion_cooldown_ticks` | `80` (4 s) | Cooldown after the Stick of Deletion triggers its special knockback. |
| `fire_god_cannon_cooldown_ticks` | `240` (12 s) | Fire God's Ghast Cannon cooldown. |
| `ice_emperor_cooldown_ticks` | `600` (30 s) | Ice Emperor's Absolute Zero cooldown. |

Divinity Shield, Time Stop, Ice Emperor, The GOAT, Serious Punch, Poseidon, Shadow Archer, Stick of Deletion, and Fire God retain their prior default cooldown lengths. Time Warp's rewind and Teleport Gambit's gateway now default to a 30-second and 15-second cooldown, respectively. Set either value to `0` to disable it.

Boots of the Sky uses a flight duration/recharge cycle instead of a separate activation cooldown. These are already configurable with `boots_flight_duration_ticks` and `boots_recharge_duration_ticks`.

**Upgrade note:** v9-generated config files saved both the gateway cooldown and rewind cooldown as `0`. On the first v10 load, a config without `config_version` is migrated to `600` ticks for rewind and `300` ticks for gateway if those values are zero. This one-time migration provides the requested cooldown defaults. After the config is saved with `config_version: 2`, setting either value to `0` will stay disabled on future launches.

Example: to give Time Warp's rewind a 30-second cooldown and Teleport Gambit a 15-second cooldown, set:

```json
{
  "time_warp_rewind_cooldown_ticks": 600,
  "teleport_gambit_gateway_cooldown_ticks": 300
}
```

Do not replace your whole config with this example; edit just the desired keys in your existing `yppowers_config.json` so you keep all other settings.

## GOAT passive jump

The GOAT's jump scaling is now a true passive and applies on every server-side jump. It no longer has a cooldown; the old `goat_jump_cooldown_ticks` setting is obsolete and is removed from newly saved configurations. Use `R` to toggle The GOAT's passives on or off.
