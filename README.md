# YP Powers — Fabric Gradle Project

**Release version: `1-14-3`** · Minecraft Java Edition **1.21.11** · Fabric · Java 21

A Fabric mod implementing twelve YP Powers, including the original eleven powers plus Thor. The consolidated development history is in [`CHANGELOG.md`](CHANGELOG.md). The version is sourced from `mod_version` in `gradle.properties` and is inserted into `fabric.mod.json` by the Gradle `processResources` task.
Special thanks to @autumneddd for helping me upload the initial release.

## Requirements

- Minecraft Java Edition **1.21.11**
- Java **21** (JDK, not just a runtime)
- Gradle **9.2.1** or a compatible Gradle 9.2+ installation
- Fabric Loader **0.18.1** and Fabric API **0.141.6+1.21.11** (Gradle resolves these dependencies)

## Build

Open a terminal in the project root (the directory containing `build.gradle`) and run:

```bash
./gradlew build
```

On Windows, use `gradlew.bat build`. The included launcher is a checksum-verifying bootstrap script that installs the pinned Gradle 9.2.1 distribution in the Gradle user cache; it is provided because the standard Gradle wrapper JAR could not be fetched in the offline authoring environment. It needs internet access the first time it runs.

The remapped mod JAR is produced in `build/libs/` as `yppowers-1-14-3.jar` (with the source JAR produced alongside it). To launch a development client or dedicated server after Gradle has resolved dependencies:

```bash
./gradlew runClient
./gradlew runServer
```

If you already have Gradle installed, `gradle build` works as well. Java 21 is required for Minecraft 1.21.11. The launcher verifies the downloaded Gradle archive against a pinned SHA-256 checksum before extracting or running it.

## Keybindings

All five keybindings are registered as normal Minecraft keybindings and can be rebound at **Options → Controls → Key Binds → YP Powers**.

| Default key | Function |
| --- | --- |
| `G` | Primary power ability |
| `H` | Secondary ability (Time Warper rewind; Teleport Gambit opens its Domain) |
| `V` | Tertiary ability; Shadow Archer morph menu, GOAT Overdrive, or Time Warper Black Hole |
| `B` | Quaternary ability; Shadow Archer arrow selection or a power-specific fourth ability |
| `R` | Alternate ability; GOAT/Ice Emperor toggle, Poseidon enchantment swap, or Boots of the Sky Raigo |

The Boots of the Sky also accept a double-tap of jump as an alternate flight toggle; the server validates that you have that power. For The GOAT and Ice Emperor, `R` toggles their powers on/off. The toggle state is saved with the world, and reassignment starts the assigned power enabled.

The key actions are handled server-side; the client sends key requests through Fabric networking.


### Teleport Gambit: Domain ability (`H`)

Teleport Gambit can keep using `G` for its temporary gateway. Press `H` to place an End Portal Frame three blocks above the caster and charge a Domain for 8 seconds. Increasing portal, end, enchantment, electric-spark and dragon-breath particles mark the charge while players within 25 blocks are pulled toward the frame. At the end of the charge, nearby players are pulled into the owner's separate pocket area in `yppowers:tg_domain`.

Each owner receives a separate, UUID-derived location with a 257×257-block bedrock platform. The floor and player-built structures are saved normally with the dimension's chunks, and the dimension's time is fixed to daytime. The Domain owner receives 5× modifiers for health, combat, movement, armor and knockback resistance, absorption capacity, interaction range, mining, oxygen, step height and fall-safety attributes; jump strength is intentionally not modified. Other players are trapped within that Domain while the owner remains there. When the owner dies, disconnects, loses Teleport Gambit, or leaves the Domain, the other players are returned to their recorded entry locations. If a visitor dies during an active Domain, their respawn is returned to it; when the session ends, dead or disconnected members are returned to their saved original locations. The default Domain cooldown is 15 minutes.

The Domain is supplied as a custom data-pack dimension in this mod. Restart the world/server after installing this version so Minecraft loads the dimension registry definitions. If an owner leaves the platform's bounded area, the Domain session closes to avoid leaving the owner in the void.

## Operator commands

```text
/yp power assign <player> <power>
/yp power clear <player>
/yp power check [player]
/grace <minutes>
/grace 0
```

Valid power IDs: `divinity`, `goat`, `time_warper`, `gambit`, `punch`, `poseidon`, `shadow_archer`, `boots_sky`, `deletion`, `fire_god`, `ice_emperor`, `thor`.

Power administration and `/grace` require operator permission level 2 or higher. `/grace <minutes>` disables active power abilities and PvP for the specified number of minutes. `/grace 0` ends it early. Players are told when a power ability is blocked; PvE combat remains enabled.

## Alliance commands

```text
/yp alliance add <player>
/yp alliance remove <player>
/yp alliance list
```

These commands are available to players. Alliances are mutual and persisted with the world. Allied players are protected from direct friendly fire and from the specified power effects.

## Configuration

The file is generated at `config/yppowers_config.json` on first launch. If the file is missing or a setting is omitted, its documented default is supplied. Cooldowns are measured in game ticks (20 ticks = 1 second); edit `config/yppowers_config.json` and restart the game/server for the changes to take effect. Set an optional cooldown to `0` to disable it. Existing cooldown defaults are preserved, while their durations can now be changed in the config.

| Key | Default |
| --- | ---: |
| `divinity_heal_radius` | `12` |
| `divinity_heal_amount` | `0.5` |
| `divinity_forcefield_duration_ticks` | `300` (15 seconds) |
| `divinity_forcefield_cooldown_ticks` | `600` (30 seconds) |
| `goat_jump_multiplier` | `2.4` |
| `goat_max_speed` | `2.2` |
| `time_stop_max_duration_ticks` | `120` (6 seconds) |
| `time_stop_cooldown_ticks` | `600` (30 seconds) |
| `time_warp_rewind_cooldown_ticks` | `600` (30 seconds) |
| `teleport_gambit_gateway_duration_ticks` | `100` (5 seconds) |
| `teleport_gambit_gateway_cooldown_ticks` | `300` (15 seconds) |
| `teleport_gambit_domain_charge_ticks` | `160` (8 seconds) |
| `teleport_gambit_domain_pull_radius` | `25.0` blocks |
| `teleport_gambit_domain_cooldown_ticks` | `18000` (15 minutes) |
| `serious_punch_cooldown_ticks` | `200` (10 seconds) |
| `poseidon_trident_swap_cooldown_ticks` | `60` (3 seconds) |
| `shadow_archer_morph_cooldown_ticks` | `200` (10 seconds) |
| `stick_of_deletion_cooldown_ticks` | `80` (4 seconds) |
| `fire_god_cannon_cooldown_ticks` | `240` (12 seconds) |
| `punch_raycast_range` | `60` |
| `boots_flight_duration_ticks` | `6000` (5 minutes) |
| `boots_recharge_duration_ticks` | `3600` (3 minutes) |
| `ice_emperor_aura_radius` | `4.0` |
| `ice_emperor_freeze_duration_ticks` | `50` (2.5 seconds) |
| `ice_emperor_cooldown_ticks` | `600` (30 seconds) |

## Project layout

- `src/main/java/com/yppowers/` — mod initialization, powers, persistence, commands, networking, and mixins
- `src/main/resources/fabric.mod.json` — Fabric metadata
- `src/main/resources/yppowers.mixins.json` — mixin registration
- `ORIGINAL_SPECIFICATION.md` — verbatim copy of the supplied specification
- `src/main/resources/assets/yppowers/lang/en_us.json` — keybinding names

## Verification status

The current source package is labelled `1-14-3`. This revision fixes the Teleport Gambit Domain dimension-type JSON for Minecraft 1.21.11, including the updated environment attributes and `monster_spawn_light_level` codec shape, which were preventing the world registry from loading. The seven source fixes from `1-14-2` and Shadow Archer camera changes are retained. A clean Gradle build, client/server startup, and in-game verification remain unconfirmed in the authoring environment because it cannot resolve `services.gradle.org` to download Gradle 9.2.1. Run `./gradlew clean build` (or `gradlew.bat clean build` on Windows) before installing this revision. Test gameplay in a disposable world first.

## Fixes in this revision

- Teleport Gambit now triggers on the next server tick for a player already standing on the newly placed gate; stepping away and re-entering is no longer required. Successful arrivals receive a 1-second lockout from all active Gambit gates to prevent immediate back-and-forth portal loops.
- Absolute Zero now immobilizes targets server-side for the configured freeze duration and restores their previous velocity without breaking overlapping freezes from Time Warper or Serious Punch.
- Ice Emperor no longer extinguishes its own player every tick, allowing the documented fire/lava vulnerability to take effect; the aura still extinguishes fire blocks within its radius.
- The GOAT's jump passive now applies on every server-side jump with no movement check or per-jump cooldown; `R` toggles its passives.
- Ice Emperor has a visible snowflake field around the player; `R` toggles the full power, pausing the passive aura/trail and disabling Absolute Zero while off.
- Fire God now makes Nether-native hostile variants (including Piglin Brutes, Zombified Piglins, Hoglins, and Zoglins) ignore the player and clears stale targets before mob AI ticks.
- GOAT and Ice Emperor on/off state is persisted in world data under `DisabledPowers`.
- Stick of Deletion's custom launch is applied after vanilla melee handling so vanilla knockback is less likely to overwrite it.
- Added checksum-verifying `gradlew` / `gradlew.bat` bootstrap launchers for Gradle 9.2.1. These download the official distribution on first use; they are bootstrap scripts rather than the standard Gradle wrapper JAR.
- Configured Loom to generate the explicit `yppowers.refmap.json` and enabled its legacy Mixin annotation processor for robust remapping.


## Port status: 1.21.11 migration

The project configuration and mod metadata have been updated for Minecraft 1.21.11, Fabric Loader 0.18.1, Fabric API 0.141.6, Yarn 1.21.11+build.6, Fabric Loom 1.14.10, and Java 21. The Java source still requires a successful dependency-backed compile and runtime testing before this should be considered a complete port. This environment could not download Gradle dependencies, so no successful build is claimed.

### Port v8 compiler follow-up (Minecraft 1.21.11)

- Fixed the nine compile errors from the latest report: `World.isClient()`, `Screen.keyPressed(KeyInput)`, persistent projectile damage access, and command permission API.
- Switched from the legacy Mixin annotation processor/refmap configuration to Loom 1.14's default in-place Mixin remapper. The old refmap configuration is no longer declared in `yppowers.mixins.json`.
- Updated `settings.gradle` to remove Gradle's deprecated Groovy space-assignment syntax.
- Cooldown configuration update: existing hard-coded cooldowns are now config-driven; Time Warp rewind defaults to 30 seconds and Teleport Gambit gateway defaults to 15 seconds. v9 config files are migrated once so these formerly-zero settings receive the new defaults; a saved `0` in config version 2 disables the cooldown. See `COOLDOWN_CONFIGURATION.md`.
- The v8 base was reported as building successfully by the user. This cooldown update has not been dependency-compiled in the authoring environment because `services.gradle.org` could not be resolved.


### Port v10 cooldown and Shadow Archer menu hardening

- Enabled configurable cooldown defaults for Time Warp rewind (600 ticks / 30 seconds) and Teleport Gambit gateway creation (300 ticks / 15 seconds).
- Added a one-time `config_version` migration for v9-generated configs, whose two new cooldown keys were saved as zero. If you intentionally want either disabled, set that key back to `0` after the first v10 launch saves the migrated config.
- Hardened client networking against stale/disconnected connections, ignored duplicate menu-open packets, added a server compatibility check before sending the open-menu payload, and moved focus handling to the 1.21.11 `Screen.setInitialFocus()` lifecycle hook.
- The tester's actual SA menu crash stack trace was not included with the report, so these are defensive fixes rather than a confirmed root-cause fix. If it still crashes, attach the newest `crash-reports/*.txt` or `logs/latest.log` showing the exception.


### Port v11 tester follow-up (SA menu, arrow selection, Poseidon)

- SA V: Removed the menu response's additional `canSend` gate. The response is sent after the server has received the mod's keybind payload and verified the player has Shadow Archer. Client logs now show whether the menu screen was installed; menu morph requests retain live-connection checks.
- SA B: It cycles unlocked arrow types. When only Split Shot is unlocked, B now explains the four unlock recipes. Arrow unlocks are stored in world persistent data and survive reconnects and server restarts.
- Poseidon: Grants Water Breathing while in water and removes the effect on land or when the power is removed.
- These v11 source changes have passed static delimiter/resource/ZIP checks; a dependency-backed Gradle build is still required and has not been claimed in this authoring environment. See `SA_MENU_CRASH_NOTES.md`.


### Ice Emperor Glacial Aura Freezing Damage

Glacial Aura deals configurable freeze-type damage to hostile mobs that remain within its radius:

- `ice_emperor_aura_freeze_damage`: damage per hit (default `1.0`, or half a heart).
- `ice_emperor_aura_freeze_damage_interval_ticks`: ticks between hits per mob (default `40`, or 2 seconds; 20 ticks = 1 second).

Set the damage value to `0` to disable aura damage. This damage only applies while Ice Emperor is enabled.

## Latest port fixes

- **v13.2 — Shadow Archer morph menu:** ownership is synchronized on join/assignment/clear, V opens the menu immediately for Shadow Archer holders, and ownership state resets on disconnect. See `PORT_V13_2_MORPH_MENU_FIX.md`.
- **v13.3 — Ice Prison Arrow:** locks an arrow's selected type at spawn, uses the exact collision location, applies the documented debuffs reliably to the directly hit target and nearby enemies, and adds impact feedback.
- **v13.3 — Serious Punch:** launches three simultaneous punch paths in a fan (center, 30° left, 30° right); block clearing, particles, and damage follow all three paths.


### Port v13.4 — Teleport Gambit immediate entry and anti-bounce lockout

- Removed the activation-time entrant ignore list, so the player standing on the newly created End Portal triggers it immediately on the next server tick.
- Added a per-player 20-tick transfer lockout after successful teleportation. Active gateways skip locked-out players, preventing immediate gate-to-gate bounce loops.
- Lockout expiry uses world ticks; disconnected players have their own lockout entry cleared.
- Preserved the configured gateway lifetime, creation cooldown, destination search, arrival effects, and block restoration behavior.
- Static source/resource/ZIP checks are performed for this package; a full Gradle build and in-game multiplayer verification remain necessary.

### Port v13.5 — Shadow Archer morph expansion

- Added a data-driven morph profile registry and a runtime-derived picker for all vanilla `MobEntity` forms present in the installed Minecraft 1.21.11 registry; forms without a custom profile receive generic useful abilities/traits.
- Expanded the existing V morph menu with search, paging, locked/unlocked feedback, and profile ability/trait summaries. Existing kill-to-unlock progression remains server-validated.
- Added server-authoritative G/H/R profile abilities, ability cooldowns, passives, alliance-aware targeting, controlled Creeper blast, and cleanup/restoration for effects, flight and disguise state.
- Preserved the B arrow selector and existing special-arrow behavior. The player remains a real player; mob entities are visual disguise proxies and vanilla AI/boss internals are not copied.
- Static source/resource checks passed, but a full Gradle build could not run because `services.gradle.org` could not be resolved by the authoring environment. Compilation and in-game testing remain outstanding. See `PORT_V13_5_SHADOW_ARCHER_MORPH_EXPANSION.md`.

## Shadow Archer camera sizing (introduced in 1-14-1; retained in 1-14-2)

While morphed, Shadow Archer receives the actual disguise entity's eye height for first-person view. A bounded FOV adjustment and third-person camera-distance scaling also adapt to the form's height. The owner's separate disguise entity is hidden only in first-person view, and remains visible in third person and to other players. These are client-side camera adjustments only; the player's server-side collision/body dimensions remain the normal player dimensions. A running client test is still needed to confirm camera feel across short and tall forms. Passive disguise-refresh smoke particles are suppressed; ability-activation particles remain as normal feedback.

## Shadow Archer Morph Expansion (v13.5)

- **Complete Shadow Archer form catalog:** see `SHADOW_ARCHER_MORPH_CATALOG.md` for each explicit profile, its G/H/R abilities, traits, and the dynamic generic fallback policy.

- Strider now has its own **Lava Dash** (stronger when actually in lava), instead of reusing the aquatic dash profile. All ability enum entries are checked against the cooldown table and executor.
- Full explicit profile/ability/trait table: `SHADOW_ARCHER_MORPH_CATALOG.md`.


### v13.5 compile-report fix

- Fixed both Dragon Breath particle calls to use `DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F)`, the required 1.21.11 particle-effect instance rather than the particle type. See `PORT_V13_5_BUILD_FIX.md`.


## Port v13.6 follow-up fixes
- Shadow Archer's morph selector no longer silently closes after a render exception; it shows a diagnostic fallback and registry lookup has a safe fallback roster.
- GOAT jumps grant 90% fall-damage reduction until landing, while other falls retain the 60% reduction.
- Ice Emperor's sprint trail now refreshes particles, packed ice, and speed support each tick for a more continuous glide.


## v13.7 — Shadow Archer Menu Blur Fix

See `PORT_V13_7_SHADOW_ARCHER_BLUR_FIX.md`. The launch log identified a duplicate-per-frame blur call in the morph screen; the menu now draws its own dark panel without invoking the screen blur renderer. v13.6 GOAT fall protection and Ice Emperor trail adjustments are preserved.

### v13.8.1 — Ice Meteor sound compile fix
- Fixed the `SoundEvents.ENTITY_GENERIC_EXPLODE` registry-reference mismatch by passing its `.value()` to `World.playSound`.

### v13.8 — Ice Meteor and Shadow Archer morph unlock fix
See `PORT_V13_8_ICE_METEOR_MORPH_UNLOCK_FIX.md` for the H-key Ice Meteor ability, configurable radius/cooldown/effects, and canonical morph-unlock validation.


## v13.8.2 — Poseidon maxed trident enchantments

- Poseidon's bound trident now receives every vanilla trident enchantment at its maximum level.
- Quick-swap alternates between **Riptide III** and **Loyalty III + Channeling I**, while retaining Impaling V, Unbreaking III, Mending I, and Curse of Vanishing I in both modes.
- Existing tagged Poseidon tridents are upgraded in place on assignment, join, and respawn without changing their current Riptide-versus-Loyalty mode.
- The existing swap cooldown, unbreakable trident behavior, and item protection are retained.


### Thor

*Mjölnir, the storm's judgment, answers only its chosen wielder.*

- **Passive — Hammer Time:** Spawn with a soulbound, unbreakable, fully enchanted mace. Press `R` to cycle five mutually exclusive damage enchantments: Density V, Breach IV, Sharpness V, Smite V, and Bane of Arthropods V by default. Enchantment levels, the swap cooldown, and hit-lightning behavior are configurable. Shared enchantments include Wind Burst III, Fire Aspect II, Unbreaking III, Mending I, and Curse of Vanishing I by default.
- **Mobility — Storm Launch:** Right-click the mace to begin a configurable wind-up, then right-click again during the configurable ready window to launch forward and upward. Timing, velocity, cooldown, particles, and sounds are configurable.
- **`G` — Ancient Rage:** Lightning strikes nearby non-allied living targets and grants Strength III for 10 seconds by default. Radius, cooldown, Strength duration/amplifier, and visual/audio feedback are configurable.
- **`H` — Hammer Throw / Recall:** Throw Mjölnir toward the crosshair. On impact it triggers four lightning strikes and leaves the real Heavy Core block as an indestructible marker. Player mining (including Creative mode), normal block replacement, and block-breaking paths are blocked for the tracked marker; it is repaired as a fallback if an external change bypasses these protections. Press `H` again to recall the mace and remove the marker; Thor's death also removes it. Marker ownership survives server restarts. Impact and recall effects are configurable.


## v13.12 — Grace Period, Time Stop, and Heavy Core Corrections

- Thor's thrown hammer now places a real Heavy Core block. Server-side block replacement and breaking are intercepted for the tracked marker, including mining in Creative mode and normal explosion/break paths; it is repaired as a fallback if a change bypasses those protections. It is only intentionally removed by recall or the owner's death.
- Fixed Time Stop's repeated-hit issue: frozen targets have their vanilla hurt cooldown reset immediately before damage resolution, so they can be hit repeatedly while still remaining frozen for the full configured duration.
- Added operator command `/grace <minutes>`. During grace, active power-key abilities, Shadow Archer morph requests and special-arrow effects, Thor's right-click launch, custom power on-hit procs, power damage modifiers/defensive immunities, power jump/fall bonuses, Fire God's mob-avoidance passive, and player-caused PvP damage (including projectile impacts) are blocked. PvE combat stays enabled. Existing passive attribute/status bonuses that are applied outside damage/jump hooks are not globally stripped by this command.
- Attempting to use a power ability during grace shows an action-bar notice. The countdown is stored in world persistent data and continues across a restart from the last saved tick. `/grace 0` disables grace immediately.
- Source/resource checks were performed; full Gradle compilation and in-game multiplayer testing still need to be run on a development machine with Gradle 9.2.1 available.

## Time Warper frozen-player visual effect (v14.0)

Players frozen by Time Stop receive a muted gray tint on their player model and feature-rendered equipment. The tint follows server-authoritative freeze state and is removed when the stop ends.


## Global Health and Energy HUD

Global player health is enabled by default and targets **40 HP** (20 hearts) for players only; mobs are unaffected. Administrators can use `/yp health status`, `/yp health enable`, or `/yp health disable`. These commands require operator permission level 2 or higher.

Every player has a shared Energy pool (default **100**) that is validated and stored by the server. Energy regenerates at a configurable rate (default **5 Energy/second**) and is persisted across reconnects, deaths, and server restarts. The HUD renders ten transparent pixel-art Energy Sparks above the vanilla health display. Each spark represents one tenth of the maximum pool; full and partial values use purple normally, orange at/below the configured low-energy threshold, and crimson/red while Bleeding. Empty sparks stay visible. A separate pixel-art Bleeding icon is shown during the status.

If a player attempts an ability without enough Energy, Bleeding begins for 4 seconds by default (configurable maximum 8 seconds). Bleeding blocks subsequent abilities, deals 1 HP per second by default, and does not stop Energy regeneration. Repeated attempts do not stack or indefinitely extend the effect. Common Energy settings include `energy_max`, `energy_regen_per_second`, `energy_cost_primary`, `energy_cost_secondary`, `energy_cost_tertiary`, `energy_cost_quaternary`, `energy_cost_alternate`, `energy_cost_shadow_archer_arrow`, `energy_bleeding_initial_ticks`, `energy_bleeding_max_ticks`, `energy_bleeding_damage_per_second`, `energy_low_threshold_percent`, `energy_hud_x`, and `energy_hud_bottom_offset` in `config/yppowers_config.json`. Shadow Archer pays its arrow cost once for the original shot; split-shot child projectiles do not double-charge.

Operators can inspect/set a player's pool with `/yp energy <player> set <amount>` (for example `/yp energy Steve set 100`).

## GOAT Overdrive

Press `V` while assigned GOAT to activate **Overdrive** (default 15 seconds, 90-second cooldown, 60 Energy). Defaults are 3× movement, 4× jump strength, Strength III, Resistance II, Regeneration II, increased knockback resistance and goat-themed particles. Eligible damage during Overdrive is converted into a bounded damage debt and repaid gradually once Overdrive ends; healing does not erase this debt. Void and generic-kill damage are not deferred. Relevant `goat_overdrive_*` values are configurable. Overdrive's periodic potion effects are owned by Overdrive while active. A disconnect or server shutdown safely ends the temporary Overdrive state; its cooldown and deferred damage debt persist, so reconnecting or restarting cannot refresh the duration or erase the debt. Pre-existing Strength/Resistance/Regeneration effects are snapshotted and restored on a normal end, but exact duration restoration is not guaranteed across a full server restart.

## Time Warper Black Hole ultimate

Press `V` as Time Warper to charge a Black Hole for 2 seconds; it pulls entities for 4 seconds within a configurable default 20-block radius and implodes within 12 blocks for at most 10 HP. The default cooldown is 300 seconds and health cost is 50% of current health (the cast never reduces the caster below 1 HP). Terrain destruction is configurable, excludes bedrock, Heavy Core, End Portal Frames, blocks with block entities, and negative-hardness blocks, only processes loaded chunks, and is capped to a configurable number of blocks per tick. Player, mob and item targeting can be configured separately. The original Time Stop/Time Warp mechanics remain separate from Black Hole.

## Boots of the Sky Raigo ultimate

Press `R` as Boots of the Sky to call down **Raigo**. It charges for 3 seconds, locks a raycast target up to 80 blocks away, creates a growing lightning sphere and target telegraph, then damages entities within a 20-block radius for at most 16 HP and adds a shockwave. Cooldown defaults to 180 seconds. Terrain damage is disabled by default; when enabled, it processes only loaded chunks and protects bedrock, Heavy Core, End Portal Frames, and blocks with block entities. Thor is intended to be immune to natural/custom lightning, including Raigo.

## Thor flight and lightning protection

Thor's mace remains soulbound, retains `R` enchantment cycling, `G` Ancient Rage, `H` hammer throw/recall, and its actual Heavy Core impact marker. Ordinary breaking/replacement is blocked; the tracked marker is removed on Thor's death or hammer recall. The airborne mace launch has configurable wind-up, a short input buffer, chain timing, forward/upward impulse, limited air steering and a velocity cap. Thor's defense hook ignores lightning-bolt damage and lightning-tagged sources without granting unrelated damage immunity. These paths need in-game regression testing for compatibility with other mods that replace damage or block APIs.

See [`CONFIGURATION_AND_COMMANDS.md`](CONFIGURATION_AND_COMMANDS.md) for the new configuration keys and operator commands, and [`TESTING_CHECKLIST.md`](TESTING_CHECKLIST.md) for a targeted regression plan.
