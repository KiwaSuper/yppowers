# YP Powers — Fabric Gradle Project

A Fabric mod for **Minecraft Java Edition 1.21.11**, implementing the eleven YP Powers from the supplied specification. The power IDs, primary key assignments, command names, and documented configuration defaults are retained.

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

The remapped mod JAR is produced in `build/libs/`. To launch a development client or dedicated server after Gradle has resolved dependencies:

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
| `H` | Time Warper rewind |
| `V` | Shadow Archer morph menu |
| `B` | Shadow Archer arrow selection |
| `R` | Alternate ability; toggles GOAT passives or the Ice Emperor power, and swaps Poseidon's trident |

The Boots of the Sky also accept a double-tap of jump as an alternate flight toggle; the server validates that you have that power. For The GOAT and Ice Emperor, `R` toggles their powers on/off. The toggle state is saved with the world, and reassignment starts the assigned power enabled.

The key actions are handled server-side; the client sends key requests through Fabric networking.

## Operator commands

```text
/yp power assign <player> <power>
/yp power clear <player>
/yp power check [player]
```

Valid power IDs: `divinity`, `goat`, `time_warper`, `gambit`, `punch`, `poseidon`, `shadow_archer`, `boots_sky`, `deletion`, `fire_god`, `ice_emperor`.

Power administration requires operator permission level 2 or higher.

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

The user previously reported that v8 built successfully on their machine. This revision adds power toggle persistence, changes the GOAT passive jump behavior, adds visible Ice Emperor field particles, and expands Fire God target immunity. The authoring environment does not have a cached Gradle distribution and could not download Gradle 9.2.1 (`services.gradle.org` DNS resolution may fail), so this revision still requires `./gradlew clean build` before use. Please run `./gradlew clean build` (or `gradlew.bat clean build` on Windows) before installing this revision. Test gameplay in a disposable world first because several abilities deliberately alter blocks and entity movement.

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

## Shadow Archer Morph Expansion (v13.5)

- **Complete Shadow Archer form catalog:** see `SHADOW_ARCHER_MORPH_CATALOG.md` for each explicit profile, its G/H/R abilities, traits, and the dynamic generic fallback policy.

- Strider now has its own **Lava Dash** (stronger when actually in lava), instead of reusing the aquatic dash profile. All ability enum entries are checked against the cooldown table and executor.
- Full explicit profile/ability/trait table: `SHADOW_ARCHER_MORPH_CATALOG.md`.


### v13.5 compile-report fix

- Fixed both Dragon Breath particle calls to use `DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F)`, the required 1.21.11 particle-effect instance rather than the particle type. See `PORT_V13_5_BUILD_FIX.md`.
