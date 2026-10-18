# Port v12 — GOAT, Fire God, and Ice Emperor improvements

## The GOAT
- Removed the jump cooldown and movement check from the passive leap. Jump scaling now runs on every server-side player jump.
- Reapplies the passive Jump Boost if another effect removes it while the power is enabled.
- Added the `R` toggle to disable/re-enable GOAT passives. Disabling removes max-health and knockback-resistance modifiers, movement-speed modifier, and Jump Boost.

## Ice Emperor
- Added a visible rotating snowflake cylinder/inner ring for the Glacial Field around the player.
- Added the `R` toggle. When off, the passive aura, ice trail, passive buffs/weakness, and Absolute Zero ability are disabled.

## Fire God
- Expanded immunity from mob targeting to Piglin Brutes, Zombified Piglins, Hoglins, Zoglins, Nether-spawning Skeletons, and Endermen in addition to previously covered Nether hostiles.
- Stale target relationships are cleared by a MobEntity tick hook before normal AI runs, avoiding a costly per-player entity scan.

## Persistence and controls
- `PowerManager` saves disabled power IDs per player under `DisabledPowers` in world data. A toggle survives relog and server restart; assigning a power administratively resets that power to enabled.
- The alternate key (`R`) is now labelled as a generic alternate ability/power toggle, since it remains Poseidon's alternate trident swap while toggling GOAT and Ice Emperor.
- Removed the obsolete `goat_jump_cooldown_ticks` config field.

## Validation status
- Resource JSON and archive checks are performed during packaging.
- Full Gradle compilation could not be verified in this environment because Gradle 9.2.1 is not cached and downloading from `services.gradle.org` may fail. Run `./gradlew clean build` before installing.


## Follow-up: Ice Emperor freezing damage
- Glacial Aura now deals 1.0 freeze-type damage to nearby hostile mobs every 40 ticks (2 seconds) while they remain in the aura.
- Added `ice_emperor_aura_freeze_damage` and `ice_emperor_aura_freeze_damage_interval_ticks` configuration settings.
- Damage timing is tracked per entity, so overlapping Ice Emperor fields do not stack the damage frequency.
- Old/inactive target cooldown records are cleaned periodically.
- Compile/runtime verification remains pending; run `./gradlew clean build` before installing.
