## 1-14-3 — Teleport Gambit Domain dimension registry fix

- Fixes `data/yppowers/dimension_type/tg_domain_type.json` for Minecraft 1.21.11. The `monster_spawn_light_level` uniform provider now uses the correct direct `min_inclusive`/`max_inclusive` fields rather than the outdated nested `value` object.
- Replaces pre-1.21.11 `fixed_time` and `effects` properties with `has_fixed_time`, `skybox`, and `cardinal_light`.
- Moves dimension behavior flags that migrated in 1.21.11 into `attributes`, including bed behavior, respawn anchors, piglin zombification, raids, Nether portal piglin spawns, and environment visuals.
- Keeps the Domain's custom flat void generator and the manager's repeated daytime reset at time 6000 while the Domain is active.
- Updates `mod_version` to `1-14-3`; expected JAR: `build/libs/yppowers-1-14-3.jar`.
- **Verification:** JSON syntax and targeted structural checks passed. A full Gradle build and in-game registry-load test remain outstanding.

## 1-14-2 — Seven compile-error fixes

- Fixes the Time Warper black-hole implosion and Boots of the Sky Raigo `FLASH` particle calls by passing `TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFF)` instead of passing the particle type directly.
- Adds the missing `EnergyManager` import to `ThorPower`.
- Makes Thor's buffered-launch deadline a `long` before storing it in the `Map<UUID, Long>`.
- Uses `server.getOverworld().getSpawnPoint().getPos()` for the Overworld spawn fallback; in Minecraft 1.21.11, `SpawnPoint` provides a `BlockPos` via `getPos()`, not `getX()/getY()/getZ()`.
- Retains the 1-14-1 Shadow Archer camera-sizing/stealth changes, 1-14-0 Domain and GOAT updates, and existing power behavior.
- Updates `mod_version` to `1-14-2`; expected remapped JAR: `build/libs/yppowers-1-14-2.jar`.
- **Verification:** the reported compiler errors have been addressed in source. A full Gradle compile and in-game test must still be run; source packaging checks do not prove semantic compilation.

## Previous patch — 1-14-1: Shadow Archer morph camera sizing

- Adds a server-to-client payload carrying the active disguise's actual eye height and entity height to the Shadow Archer player.
- Adjusts the local player's eye height while morphed so first-person view originates at the disguised mob's eyes.
- Applies a bounded, subtle FOV adjustment based on the disguise height and scales third-person camera distance within safe limits so small and large mob forms fit the view more naturally.
- Resets camera state when the player returns to human form, loses Shadow Archer, disconnects, or the client changes servers; the state is based on the actual disguise entity rather than a hard-coded species list.
- Retains the prior passive smoke-particle removal. Ability-triggered visual effects are unchanged, since they occur when abilities are actively used rather than being a persistent disguise tell.
- Version property updated to `1-14-1`; expected JAR filename: `yppowers-1-14-1.jar`.
- **Verification note:** source/resource checks are performed for this revision, but the changes still need a successful Gradle build and first-/third-person testing in a Minecraft 1.21.11 client.

## Previous patch — 1-14-0: Domain compile fixes, GOAT jump tuning, and morph stealth

- Updates the Gradle/Fabric version to `1-14-0`; expected JAR name: `yppowers-1-14-0.jar`.
- Fixes the five compile errors listed in the supplied domain build report: dragon-breath particles now use a particle effect instance, player distance uses `getEntityPos()`, and overworld return coordinates use `getSpawnPoint()`.
- Reduces the default GOAT jump multiplier from 2.4× to 2.2×. The existing powered-jump fall-protection tracking remains, so GOAT should not take fall damage from a tracked powered jump.
- Removes the recurring smoke particle emitted by Shadow Archer morph disguise refresh, preventing that obvious visual tell.
- **Still requires runtime verification:** morph camera/FOV scaling to match each mob form is not yet implemented in this revision; GUI/texture rendering and full game behavior must be tested in Minecraft.

# YP Powers — Full Changelog

**Current project / Fabric metadata version: `1-14-3`**  
**Minecraft:** Java Edition 1.21.11 · **Loader:** Fabric · **Java:** 21

This changelog consolidates the feature specification, README, port changelog, bug-audit notes, and version-specific implementation notes included with this source project. Release labels such as `v14.0` are the development sequence; the current Gradle/Fabric version string is `1-14-3`.

## Previous release — `1-14-1` (development revision v14.1)

### Shadow Archer: morph-sized camera and disguise stealth

- The server sends the owner's active disguise eye height and entity height to the client. The local player's first-person camera uses the disguise's eye height rather than the human player's eye height.
- A restrained, clamped FOV adjustment and a clamped third-person camera-distance adjustment make tiny and tall forms fit the view more naturally. Camera dimensions are derived from the actual spawned disguise, not a hand-maintained species table.
- Synchronizes the disguise entity's network ID and suppresses that entity's render only while the owner is in first person, preventing a mob model from obstructing its own camera while keeping the disguise visible in third person and to other players.
- Camera state is cleared on human reset, power removal, disconnect and client disconnect. The recurring passive disguise-refresh smoke particle remains removed; deliberate ability activation effects remain unchanged.

### Release metadata and verification

- Sets `mod_version=1-14-1` in `gradle.properties`; the expected remapped artifact for that historical revision was `build/libs/yppowers-1-14-1.jar`.
- Resource JSON and source-level checks are run for this revision. Full semantic compilation, mixin application and first-/third-person runtime behavior still require a Minecraft 1.21.11 test environment.

## Previous release — `1-14-0` (development revision v14.0)

### Time Warper: frozen-player gray tint

- Added a server-authoritative `sync_time_stop_visual` payload to synchronize which player entities are currently frozen by Time Stop.
- Frozen player models render with a muted neutral-gray tint. The tint is also applied to supported equipment, armor, and feature-rendered cosmetic layers.
- Tracks overlapping Time Stops with reference-counted frozen state, so ending one stop does not clear the tint if another active stop still freezes the same target.
- Synchronizes the active frozen-player set to clients that join during Time Stop.
- Clears cached visual state on client disconnect and server shutdown to avoid stale entity IDs persisting between sessions.

### Teleport Gambit: persistent pocket Domain

- Added a secondary `H` ability that places a temporary End Portal Frame three blocks above the caster and charges for 8 seconds with escalating portal, reverse-portal, end-rod, enchantment, electric-spark and dragon-breath particles.
- Players within the configurable 25-block pull radius are progressively pulled toward the frame; at the end of the charge they are transported into a persistent custom dimension at separate owner-specific locations.
- Added `yppowers:tg_domain`, a permanent-day void dimension. On first use, each owner receives a 257×257 bedrock platform, constructed incrementally during the charge to avoid placing the entire floor in one server tick. Ordinary chunk saving preserves player-built structures between visits.
- The Domain owner receives temporary 5× attribute modifiers for max health, movement speed/efficiency, attack damage/speed/knockback, sweeping damage, armor/toughness, normal and explosion knockback resistance, absorption capacity, interaction range, block and submerged mining, sneaking speed, step height, oxygen bonus and fall-safety stats. Jump strength is deliberately not modified. Modifiers are removed when the Domain ends.
- Domain visitors cannot leave the owner-specific platform/session while its owner remains inside. The session ends when the owner dies, disconnects, is reassigned away from Teleport Gambit, or leaves the Domain; online visitors return to their exact recorded world/position, while disconnected/respawning players receive a persisted pending return. The respawn hook returns the owner to their pre-Domain location after death, and sends visitors who die during an active session back into the still-open Domain.
- Added a default 15-minute persistent cooldown and config values for charge duration, pull radius and cooldown. Cooldowns and pending return locations are saved in YP Powers world data. Server shutdown returns online members before saving, and orphaned Domain arrivals after a crash are sent back to the Overworld spawn rather than left stranded.
- Added the custom dimension and dimension-type data-pack definitions and documented the ability. Install the mod and restart the world/server for the custom dimension to load.

### Configuration additions

| Key | Default | Purpose |
|---|---:|---|
| `teleport_gambit_domain_charge_ticks` | `160` | Charge duration (8 seconds).
| `teleport_gambit_domain_pull_radius` | `25.0` | Radius from the active frame that pulls players.
| `teleport_gambit_domain_cooldown_ticks` | `18000` | Domain cooldown (15 minutes).

### Release metadata and documentation

- Sets `mod_version=1-14-0` in `gradle.properties`.
- Gradle's existing `processResources` task expands the Fabric metadata version field from that property, so a successful build embeds `"version": "1-14-0"` in the JAR's `fabric.mod.json`.
- The expected remapped output filename is `build/libs/yppowers-1-14-0.jar`.
- Adds this consolidated changelog and links it from the README.

### Verification status

- The mixin configuration and other resource JSON files have been checked during previous source passes; current gray-tint hooks are documented in `PORT_V13_13_TIME_STOP_GRAY_FROZEN_PLAYER_VFX.md`.
- A complete Gradle build and live rendering/multiplayer test remain outstanding. The authoring environment could not download Gradle 9.2.1 from `services.gradle.org`, so this package is source, not a newly compiled JAR.

## v13.12 — Grace period, repeated Time Stop hits, and real Heavy Core

- Replaced the earlier bedrock marker with the actual vanilla `Blocks.HEAVY_CORE` block at Thor's hammer impact site.
- Protects tracked Heavy Core markers from ordinary player mining, World block break/replacement paths, and common break/explosion paths, including in Creative mode. A narrowly scoped cleanup authorization allows Thor's own recall/death handling to remove the exact owned marker.
- Repairs the tracked marker if a lower-level change bypasses block protection while its chunk is loaded. Recall/death cleanup can load the marker's chunk so a far-away or cross-dimension marker is not orphaned.
- Corrected Time Stop's vanilla hurt-cooldown interaction: frozen targets' damage cooldown is cleared immediately before damage resolution, allowing repeated hits while the target stays frozen for the full active duration.
- Added operator-level `/grace <minutes>`; `/grace 0` ends the grace period early.
- During grace, active power-key abilities, Shadow Archer morph requests, Thor's right-click launch, custom on-hit power procs, and player-versus-player damage are blocked. Attempted abilities/PvP trigger rate-limited action-bar feedback. PvE combat remains available.
- Suppresses relevant power damage modifiers, defensive immunities, power jump/fall bonuses, and Fire God's hostile-mob avoidance while grace is active.
- Persists the grace countdown in YP Powers world data and restores it when the world loads.
- Known limitation: passive attributes and status effects already applied outside the intercepted damage/jump hooks are not all globally removed for the duration of grace.

## v13.11 — Thor lifecycle and Minecraft 1.21.11 compiler-report fixes

- Hardened Thor's thrown mace lifecycle: the thrown entity is protected, tracked and recalled; death cleanup removes outstanding hammer state and recreates the soulbound mace on respawn.
- Persists hammer owner, thrown-entity UUID, dimension and marker coordinates so tracked state survives server restart/disconnect scenarios.
- Ensures changing the assigned power does not silently clear the outstanding hammer marker; death cleanup can still find a tracked Thor hammer after reassignment.
- Fixed reported 1.21.11 API errors by using `World.isClient()`, the velocity-update invoker in place of removed `velocityModified` field writes, and resolved `RegistryEntry<SoundEvent>` values for sound playback.
- Exposed Thor's tunable settings through `config/yppowers_config.json` and kept its primary/secondary/alternate actions on configurable normal Minecraft keybindings. The v13.12 entry above supersedes the earlier marker material with the real Heavy Core block.

## v13.10 — Thor configuration bindings

- Expanded Thor settings into the shared JSON configuration system, including enchantment levels, swap cooldown, Storm Launch timing/movement, Ancient Rage settings, hammer throw/impact feedback, and recall effects.
- Kept missing settings in older config files populated with declared defaults.
- Kept Thor assignable through `/yp power assign <player> thor`; keybinds are adjustable under **Options → Controls → Key Binds → YP Powers**.

## v13.9 — Thor added

- Added Thor as the twelfth assignable power.
- **Hammer Time:** soulbound, unbreakable mace; `R` cycles mutually exclusive max-level damage enchantments (Density V, Breach IV, Sharpness V, Smite V, or Bane of Arthropods V) while retaining shared mace enchantments. Mace hits call lightning on targets.
- **Storm Launch:** right-click timing mechanic to launch the player forward/upward without requiring rain or water.
- **Ancient Rage (`G`):** strikes non-allied living entities in a configurable radius with lightning and grants Strength III.
- **Hammer Throw / Recall (`H`):** throws Mjölnir; impact triggers lightning and creates a tracked marker. Recalling the hammer removes its marker and stuns nearby non-allies.
- Subsequent releases add complete configuration exposure and replace the earlier bedrock marker design with the actual Heavy Core block.

## v13.8.2 — Poseidon maxed trident

- Applies all vanilla trident enchantments to Poseidon's tagged trident, using separate compatible sets for Riptide and Loyalty/Channeling modes.
- Replaces incompatible enchantment sets on swap rather than stacking incompatible enchantments.
- Upgrades existing tagged tridents on assignment, player join and respawn while preserving the current mode.
- Retains the existing swap cooldown and unbreakable-item behavior.

## v13.8.1 — Ice Meteor compile fix

- Corrected the Ice Meteor impact sound call to pass the `SoundEvent` value expected by Minecraft 1.21.11 rather than a registry reference.
- No ability behavior or configuration defaults were intentionally changed in this focused fix.

## v13.8 — Ice Meteor and Shadow Archer unlock fix

- Added Ice Emperor's secondary Ice Meteor with a configurable default 20-second cooldown and configurable impact radius (default 50 blocks, safety-clamped to 1–128 blocks).
- Builds a large snow/packed-ice visual shell around a synchronized sphere of snowball entities.
- On impact, creates temporary packed ice over the impact area, applies configurable freeze damage and freezing effects, Slowness IV and Blindness, and emits a localized snowstorm.
- Excludes the caster and allies from the meteor's damage/effects; the caster receives Strength II for the effect duration.
- Canonicalizes Shadow Archer morph IDs before checking kill-unlock data so IDs such as `spider` and `minecraft:spider` are not treated as different unlocks.
- Adds configuration for meteor cooldown, radius, damage, effect duration and temporary ice duration.

## v13.7 — Shadow Archer menu blur-crash fix

- Removed the morph menu's second blur/background-render call that triggered `IllegalStateException: Can only blur once per frame`.
- Replaced it with a dark backdrop and bordered panel while preserving the search/list controls, selection, diagnostics and fallback behavior.
- Preserved the v13.6 GOAT and Ice Emperor movement changes.

## v13.6 — Shadow Archer menu, GOAT fall safety, and Ice trail

- Improved Shadow Archer menu failure diagnostics and made entity-registry enumeration fault-tolerant; unified the V-key and server-response screen opening flows.
- Adds 90% fall-damage reduction after a qualifying GOAT jump, with the jump-protection state cleared on landing, power removal or toggle-off. The existing general fall reduction remains for other falls.
- Refreshes Ice Emperor's packed-ice trail and particles more consistently while sprint-gliding or standing on ice, with continuously refreshed speed assistance.

## v13.5.1 — Shadow Archer Dragon Breath compile fix

- Uses a `DragonBreathParticleEffect` instance for Dragon Breath/Roar particle emission, matching the Minecraft 1.21.11 particle API.

## v13.5 — Shadow Archer morph expansion

- Added a data-driven morph profile registry and a runtime-derived chooser for vanilla `MobEntity` forms available in the game registry.
- Extended the V menu with search, paging, locked/unlocked feedback, and ability/passive descriptions while preserving server-side kill-to-unlock checks.
- Added server-authoritative G/H/R abilities, per-ability cooldowns, passive traits, alliance-aware targeting and safe state cleanup/restoration when switching forms or returning to Human.
- Added custom profiles for major bosses, ranged and magic mobs, movement/flight/climbing forms, chargers, aquatic forms, utility forms and many common mobs; forms without a specific profile receive a generic useful ability/trait kit.
- Preserves the player's true identity/inventory; mob entities are visual disguise proxies rather than native replacements. Native boss AI, multipart collision and exact vanilla attacks are not reproduced; some projectile-like attacks use controlled raycast/cone approximations.
- Added Strider's dedicated Lava Dash and made its lava adaptation depend on actually being in lava.
- Kept B-arrow selection and special-arrow systems intact.

## v13.4 — Teleport Gambit immediate gateway and anti-bounce fix

- A player already standing on the newly placed gateway is teleported on the next server tick; stepping off and back on is no longer required.
- Adds a 20-tick (one-second) per-player transfer lockout across active Gambit gates to prevent back-and-forth teleport loops.
- Preserves configured gateway lifetime/cooldown, safe destination search, arrival effects and temporary-block restoration.

## v13.3 — Ice Prison Arrow reliability and Serious Punch fan

- Registers special arrows as they enter the server world so the selected arrow type remains attached to that projectile.
- Uses the exact impact collision position for Ice Prison; includes the directly hit non-allied target and nearby hostile living entities within the impact radius.
- Retains Slowness II and Blindness for four seconds and adds snowflake/glass-like impact feedback.
- Serious Punch now launches a three-path fan: forward, 30 degrees left and 30 degrees right. Particles, tunnel clearing, and entity hit detection follow all three paths; block drops are suppressed and allies are protected.

## v13.2 — Shadow Archer morph-menu reliability

- Synchronizes Shadow Archer ownership on join, assignment and power clear, allowing the V menu to open immediately for qualified players.
- Server-side morph requests still validate Shadow Archer ownership and kill unlocks; client-side opening does not bypass authorization.
- Keeps the older menu-response packet as a compatibility fallback.

## v13 — Ice Emperor Glacial Aura freezing damage

- Adds configurable native freeze-type damage to hostile mobs that remain in the Glacial Aura: default 1.0 damage (half a heart) every 40 ticks (two seconds) per target.
- Shares timing across overlapping aura fields so multiple Ice Emperor holders do not multiply the damage rate; stale per-target timing records are cleaned up.
- Adds `ice_emperor_aura_freeze_damage` and `ice_emperor_aura_freeze_damage_interval_ticks` configuration.

## v12 — GOAT, Fire God and Ice Emperor improvements

- **The GOAT:** removed the jump cooldown/movement condition so the scaled passive applies on every server-side jump; reapplies Jump Boost if it is removed while enabled. `R` toggles GOAT passives and clears its power-owned attributes/effects when disabled.
- **Ice Emperor:** adds a visible rotating snowflake cylinder/inner ring for Glacial Field. `R` toggles the full power, including its aura, trail, buffs/weakness and Absolute Zero.
- **Fire God:** expands hostile-mob immunity to additional Nether-associated mobs, including Piglin Brutes, Zombified Piglins, Hoglins, Zoglins and Endermen. Clears existing hostile targets before their normal AI tick.
- Saves disabled power IDs in world data so GOAT/Ice Emperor toggle states survive reconnects and restarts; reassignment re-enables that power.
- Removes the obsolete `goat_jump_cooldown_ticks` setting and improves Stick of Deletion's custom-launch timing relative to vanilla melee handling.

## v11 — tester follow-up: Shadow Archer and Poseidon

- Fixes the Shadow Archer V-menu open response by removing an additional payload-send check after server-side ownership validation; adds diagnostics for menu initialization/render errors.
- B cycles only unlocked arrow types and explains material requirements when only Split Shot is available.
- Persists Shadow Archer arrow unlocks through reconnects/restarts. Unlock recipes consume eight regular arrows plus a thematic ingredient: Ender Eye (Homing Missile), TNT (TNT Arrow), Lightning Rod (Lightning Arrow) or Packed Ice (Ice Prison). Split Shot is initially unlocked.
- Adds Poseidon's Water Breathing while in/touching water, removing it on land and when Poseidon is removed.

## v10 — cooldown defaults and Shadow Archer menu hardening

- Makes Time Warp rewind cooldown configurable with a 600-tick (30-second) default and Teleport Gambit gateway cooldown configurable with a 300-tick (15-second) default.
- Adds a one-time config migration for v9 files that had stored zero for those new cooldown keys; after migration, a deliberate configured zero continues to disable a cooldown.
- Hardens keybind/morph networking against disconnected clients, stale or duplicate menu packets and screen lifecycle errors; uses the Minecraft 1.21.11 initial-focus lifecycle hook.

## v9 — configurable cooldown foundation

- Adds/extends JSON-configurable cooldown support while preserving existing cooldown defaults, and carries cooldown settings into the 1.21.11 config workflow. See `COOLDOWN_CONFIGURATION.md` for the current keys/defaults and the v10 migration note.

## v8 — compiler-report fixes and cooldown configuration

- Addresses the reported 1.21.11 API errors involving `World.isClient()`, `Screen.keyPressed(KeyInput)`, persistent projectile damage access, and command permission predicates.
- Uses Loom's default in-place mixin remapping and corrects Gradle settings syntax.
- Begins consolidating hard-coded ability cooldowns into `config/yppowers_config.json` while preserving known defaults.
- The project owner reported that the v8 source built successfully on their machine; later revisions still need their own clean build/test.

## v7 — Minecraft 1.21.11 compiler-report fixes

- Migrates entity world/position accessors and server-player server/world access to current Yarn names.
- Replaces removed `velocityModified` field writes with an invoker for velocity-update scheduling.
- Fixes the fireball constructor, stick cooldown argument, sound event value, optional registry lookup and unbreakable item component.

## v6 — source audit and runtime safety pass

- Fixes config startup persistence so loaded configuration is preserved.
- Removes an obsolete shield-disable mixin injection targeting an absent 1.21.11 method; that specific legacy shield-disable behavior remains unimplemented pending a verified replacement.
- Coordinates velocity restoration when Time Warper, Serious Punch and Ice Emperor freezes overlap.
- Prevents ability packets from firing while screens/menus are open and drains queued key presses when input becomes inactive.
- Makes Poseidon's trident-mode detection inspect the real enchantment component after reconnects and restricts swaps to the mod-tagged trident.
- Uses give-or-drop handling for power-granted/death-retained items when inventory is full.

## v5 and v4 — migration audit and build configuration

- Corrects Gradle/Java requirements in the project documentation.
- Sets up the mixin refmap/remapping path for the Fabric Loom version then in use and audits key networking, NBT, fog, explosion, teleport and entity hooks against the current mapped API.
- Updates the furnace fuel-time hook to the instance method signature `getFuelTime(FuelRegistry, ItemStack)`.
- These audit steps were source/configuration passes; they did not independently prove a successful runtime build.

## Initial Minecraft 1.21.11 port

- Migrates networking to typed Fabric custom payload records and `PacketCodec`s for keybind, morph, Fire God sync and menu messages.
- Migrates custom item flags/names/unbreakable status to item data components; updates trident/boot enchantments, keybinding categories and attribute modifier identifiers/operations.
- Moves power assignment/alliance persistence to codec-backed `PersistentStateType`s while retaining prior NBT field names where documented for compatibility.
- Updates NBT calls, entity damage and teleport signatures, entity spawning with `SpawnReason`, Shadow Archer TNT explosion calls, Fire God lava-fog rendering, End Portal collision and furnace fuel-time hooks.
- Upgrades the Gradle bootstrap to 9.2.1 for Fabric Loom 1.14 and pins the downloaded distribution checksum.

## Current power roster (feature summary)

The current source contains twelve assignable power IDs:

- **Divinity Shield (`divinity`):** deployable particle dome/force field, ally healing, and defensive/reflection effects.
- **The GOAT (`goat`):** enhanced jumps and sprint movement, passive attribute bonuses and fall protection; passives can be toggled with `R`.
- **Time Warper (`time_warper`):** Time Stop freezes non-allied entities and their movement for the active duration; Time Warp rewinds recorded player state and applies post-rewind slowing effects. Frozen players are now gray-tinted for clients.
- **Teleport Gambit (`gambit`):** temporary End Portal gateway with safe-destination search, immediate entry activation and one-second anti-bounce protection.
- **Serious Punch (`punch`):** forward kinetic attack with three fan paths, a block-clearing tunnel, target debuffs and caster Strength.
- **Poseidon (`poseidon`):** water-oriented bonuses, Water Breathing, and a special trident with mutually exclusive enchantment modes.
- **Shadow Archer (`shadow_archer`):** selectable special arrows, kill-unlocked vanilla mob morphs, search/paging UI, server-side G/H/R form abilities and passives.
- **Boots of the Sky (`boots_sky`):** limited-duration survival flight with a recharge cycle and Slow Falling safety behavior.
- **Stick of Deletion (`deletion`):** powerful custom knockback with collision impact damage and cooldown.
- **Fire God (`fire_god`):** fire/lava passives, improved lava visibility, Nether-hostile avoidance and Ghast Cannon.
- **Ice Emperor (`ice_emperor`):** Glacial Aura, freeze-type damage, Absolute Zero, packed-ice trail and Ice Meteor; many effects have configuration settings.
- **Thor (`thor`):** soulbound mace, enchantment-mode cycling, Storm Launch, Ancient Rage, Mjölnir throw/recall and a protected Heavy Core impact marker.

## Commands and configuration

- Operator power commands: `/yp power assign <player> <power>`, `/yp power clear <player>`, `/yp power check [player]`.
- Alliance commands: `/yp alliance add <player>`, `/yp alliance remove <player>`, `/yp alliance list`.
- Grace period: operator-only `/grace <minutes>`; `/grace 0` ends it early. It disables supported active abilities and PvP while leaving PvE enabled. Some previously applied passive attributes/status effects are not globally stripped.
- Keybindings are configurable from **Options → Controls → Key Binds → YP Powers**. Cooldowns and numerous ability values are configurable in `config/yppowers_config.json`; consult `COOLDOWN_CONFIGURATION.md` and `THOR_CONFIGURATION.md`.

## Known verification limitations

- The available authoring environment could not download Gradle 9.2.1 from `services.gradle.org`. Therefore the current source package has not passed a complete Gradle compile, remapped JAR build, client/server startup, mixin application, or full in-game regression test here.
- The current tint/render mixins, full grace-period behavior, Heavy Core block protection, and Time Stop repeated-hit behavior especially require in-game testing on a test world/server.
- This is a changelog of documented implementation changes, not a claim that every feature has been exhaustively tested.


## `1-14-0` — Global Health, Energy Sparks, Overdrive, Black Hole, Raigo and Thor hardening

- Adds configurable global player-only maximum health (default 40 HP) and operator commands `/yp health enable|disable|status`; mobs are not modified by the global-health manager.
- Adds a world-persistent, server-authoritative Energy pool (default 100, configurable regeneration and costs), synchronizes Energy and Bleeding state to clients, and adds `/yp energy <player> set <amount>` for administrators.
- Adds a configurable `energy_cost_shadow_archer_arrow` and enforces the cost at server projectile spawn; insufficient Energy cancels the arrow ability, triggers Bleeding, and split-shot child arrows do not charge twice.
- Generates and registers transparent pixel-art PNGs for full, empty and nine fractional Energy Sparks in purple, orange and red, plus a custom Bleeding icon. The HUD renders ten sparks with scaled positions, partial fill, a configurable low-Energy threshold, and a Bleeding indicator.
- Adds Bleeding when an ability is attempted without sufficient Energy; default duration 4 seconds, configurable up to 8 seconds, default damage 1 HP/second, with Energy regeneration continuing while abilities are blocked.
- Adds GOAT Overdrive (`V`): default 15 seconds, 90-second cooldown and 60 Energy, configurable movement/jump modifiers, Strength III, Resistance II, Regeneration II, knockback resistance, goat-themed particles and server-side deferred damage debt. Debt is bounded and repaid after Overdrive; healing does not clear it, and void/generic-kill damage is not deferred.
- Improves GOAT debt capture by intercepting the finalized `LivingEntity.applyDamage` amount after vanilla armor/resistance calculations rather than recording raw incoming attack damage.
- Adds Time Warper Black Hole (`V`): 2-second charge, 4-second pull phase, default 20-block entity radius, 12-block terrain/implosion radii, maximum 10 HP implosion damage, 300-second cooldown, 50% current-health sacrifice, configurable entity groups and bounded terrain processing. Terrain destruction never deliberately force-loads chunks and skips bedrock, Heavy Core, End Portal Frames, negative-hardness blocks and blocks with block entities.
- Adds Boots of the Sky Raigo (`R`): 3-second charge, 80-block target range, 20-block impact radius, maximum 16 HP damage, 180-second cooldown and configurable Energy cost. A growing lightning sphere/telegraph precedes the impact; terrain damage defaults off and is bounded/protected when enabled.
- Adds configurable Thor airborne launch input buffering, chain delay, air steering, velocity cap and Energy cost; improves the server tick integration for Raigo and ensures lightning-tagged/natural/custom lightning damage is blocked for Thor without a general immunity.
- Preserves the previous Teleport Gambit Domain dimension resources and manager instead of regenerating or replacing the pocket dimension.
- Fixes Energy costs on the Shadow Archer morph selector so opening the UI itself does not spend Energy; the requested morph remains server-validated and charged. Unsupported power key slots and jump-double-tap signals from non-Boots owners do not consume Energy.
- Ends the temporary GOAT Overdrive state on player disconnect and server shutdown while retaining Energy, cooldowns and deferred damage debt; debt is not consumed while the player is dead.
- Cancels in-flight Black Hole/Raigo state safely if the caster leaves the source dimension, loses the required power, dies, or Grace starts. Black Hole terrain work begins during charging so normal bounded processing can progress before the implosion.
- Preserves Thor lightning immunity during Grace because it is an environmental/passive defense, not an ability activation.
- Bumps the config schema version to 5 so the Energy, health, Overdrive, Black Hole, Raigo and Thor settings are part of the next saved config.
- Verification status for this revision: static source/resource checks only. The Gradle wrapper could not download Gradle 9.2.1 because `services.gradle.org` did not resolve in this environment. This changelog describes source changes, not a claim of successful build or gameplay testing.
