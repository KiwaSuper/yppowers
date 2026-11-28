# Changelog

## 2.3.0 — Modular Architecture + Legacy Config & World-Save Compatibility

### Architecture & Modularity
- Refactored the `1.19.14` codebase into clean modular packages while keeping **100% compatibility** with your existing flat `config/yppowers_config.json` and existing world saves (`yppowers_data`, `yppowers_alliances`, `yppowers_energy`, Time Domain, Portable Chunks, and Gambit cooldowns):
  - **`core/`**: Typed `AbilityKey`, `HudPill`, and `PowerRegistry`.
  - **`system/`**: `CombatTagSystem`, `EpicVfx`, `PowerCombatUtil`, `SummoningVfx`, `SummonTuning`, `TargetingUtil`.
  - **`progression/`**: `RitualManager` and `AwakeningManager`.
  - **`artifact/`**: `ArtifactSpawnManager`, `GrimoireOfLight*`, and `GauntletOfDarkness*`.
  - **`power/<name>/`**: Dedicated sub-packages for all 17 powers (`shadowarcher/`, `timewarper/`, `iceemperor/`, `teleportgambit/`, `firegod/`, etc.) — removed the flat `power/impl/` folder.
  - **`command/`**: Split commands cleanly into `YPCommands`, `PowerCommands`, `AllianceCommands`, and `AdminCommands` (including `/yp cooldown clear <player>`).
  - **`network/`**: Added unified `HudSnapshot` & `HudSyncService`.

### HUD & Power Menu Upgrades
- **Live Cooldown Pills & Dynamic Bound Keys (`YPHudOverlay`)**: Restored live ability/toggle pills beneath the framed 10-spark Energy panel with **smooth draining cooldown progress bars**, **actual rebound key labels** from Minecraft Controls, power accent colors, Awakening cooldown pill, and red draining Combat Timer banner (toggleable via `energy_hud_show_cooldown_pills` in `config/yppowers_config.json`).
- **3-Tab Power Menu (`U` or `/yp guide`)**:
  1. **Power & Rituals**: Ritual progress, Awakening status/cooldown, Grimoire status, and Ice/Spirit/Shadow Archer selectors.
  2. **Powers Guide (17)**: Browse all 17 powers, passives, moves, and Awakenings with your live bound key labels.
  3. **HUD Settings**: Live-toggle Energy HUD & Cooldown Pills, switch anchor corners (`BOTTOM_RIGHT` default), and adjust X/Y offsets with live preview.

### Security, Exploits & Bug-Fix Audit (1.19.14 Hardening)
- **Dupe & World-Griefing Exploits Closed**:
  - **Portable Chunk Storage**: Blocked copying/clearing indestructible or special blocks (Bedrock, End Portals, Vaults, Spawners, Heavy Cores, Command/Structure blocks) and stripped container `BlockEntity` contents during `captureReplica` (closing the 20x item/chest duplication exploit while keeping Swap chest transfers intact), plus added a 5-failure recovery abort cap to prevent permanent server-wide journal lockups.
  - **Teleport Gambit & Time Warper**: Removed real `Blocks.BEACON` placement from Teleport Gambit Domain (particles only — closes Beacon duplication); fixed `EndPortalBlockMixin` to block *all* entities (mobs, items, pets, players) from entering The End through temporary Gambit gateways; disabled equipment drops on Time Clones and Fallout Warrior minions (closes free diamond/iron gear farming); prevented nesting the Grimoire of Light in bundles/shulkers and blocked hiding artifacts in Armor Stands, Allays, Decorated Pots, Chiseled Bookshelves, cursor stacks, double chests, and Ender Chests.
  - **Domain & Protection Integrity**: Blocked cross-domain portal stacking (`time_domain` ↔ `teleport_gambit`), guarded block-modifying powers (Grimoire Beam, Awakening Omni-Punch, Spatial Erasure, Kitsune Tunneling, Spidey Cobwebs, Ice Emperor Trail, Teleport Pads, Shadow Archer TNT) with world border, build-limit, and `TimeWarperDomainManager.mayModifyBlock` checks, and ensured re-casting Spatial Erasure restores prior erased blocks cleanly.
- **PvP, Grace Period & Alliance Fixes**:
  - **Grace Enforcement**: Summoned mobs (`yppowers_owner_*` & rogue Frost Guards) can no longer target or damage players during Grace; cancelled active Grimoire Beams, Grimoire Flight, Time Stops, Black Holes, Illusionist Grips/Pulls, Kitsune Transformations/Foxfire DoT, Spidey Web Swings, and Awakening passives during Grace; moved `keyType == 11, 13` (Swap / Gambit Portal) and `keyType == 19` (Kitsune Carry) under full Grace, Grimoire-lockout, and Bleeding checks.
  - **Friendly Fire & Attribution**: Switched Thor lightning to cosmetic bolts with attributed damage, attributed all AoE/DoT/projectile/minion abilities via `indirectMagic(player, player)`, unified `PowerCombatUtil.canHarm` (excluding Spectators, allies, friendly minions, and morph disguises while allowing rogue Frost Guards to fight), and made offline-owner alliance checks safe in `MobEntityMixin` and `LivingEntityMixin`.
  - **Cooldown & State Exploits**: Stopped death/disconnect from resetting power cooldowns across all combat powers, rejected dead/removed players in C2S gameplay packets and Energy spends, fixed `ThorMaceItemMixin` to enforce `PowerType.THOR` + Grace + Bleeding + server-authoritative launch, and fixed overlapping flight bugs across Grimoire Flight, Wither Ascendance, Shadow Archer Morph, Boots of the Sky, and Illusionist Cloud Flight (which now enforces its configured duration).
- **Gameplay Bug Fixes & Crash Prevention**:
  - **GOAT Overdrive + Sprint**: Fixed negative/zero speed freeze when combining GOAT sprint acceleration with Overdrive (`GoatPower.computeOverdriveSpeedBonus`), and cleared Overdrive/damage debt/Bleeding cleanly on death or power switch.
  - **Poseidon Tsunami & Shield Drop**: Fixed zero-cooldown Tsunami spam and the Poseidon shield drop check.
  - **Spirit Channeler & Fallout**: Stopped Spirit Channeler from consuming Bones/Prismarine Shards/Phantom Membranes after a form is already unlocked; fixed Fallout Warrior summoning when `fallout_minions_per_cast` > 3.
  - **Stability & Server Shutdown**: Made `ShadowArcherMorphRegistry` thread-safe (`ConcurrentHashMap` + atomic list swap), removed dead `WorldMixin`, fixed Nether ceiling structure arrivals in Teleport Gambit Awakening, excluded bosses from Kitsune Carry & Illusionist Grip, prevented Spidey Web Swing wall-clipping, fixed Kitsune carried-passenger `NoGravity` leaks on disconnect, and wired full `SERVER_STOPPING` / `SERVER_STOPPED` cleanup hooks for all powers and subsystems.

## 2.2.1

### Compatibility
- **Java 21 Target (`>=21`)**: Switched build target, Mixin compatibility level (`JAVA_21`), and `fabric.mod.json` requirement from Java 25 to **Java 21** (classfile major version `65`), so the mod works out-of-the-box on both default Minecraft 1.21.11 **Java 21** and newer JVMs like **Java 25**.

## 2.2.0 — 1.19.14 Full Expansion on Legacy Architecture (Java 25)

### Merged 1.19.14 Content & Legacy Architecture
- Restored the **legacy architecture & flat `config/yppowers_config.json`** (`PowerManager`, `EnergyManager`, `AllianceManager`, `YPConfig`) compiled for **Java 25** (`compatibilityLevel: JAVA_25`, `"java": ">=25"`).
- **Rituals & Awakenings (`P`)**: Full ritual trials (`RitualManager`) and Awakening ultimates (`AwakeningManager`) for all 17 powers, including Fire God **Wither Ascendance** + Skull Volley (`I`), GOAT high-speed ramming, Poseidon Sink confirmation UI, Teleport Gambit structure teleport GUI, and destructive-action confirmation (`F6`).
- **2 World Artifacts (`ArtifactSpawnManager`)**: **Grimoire of Light** (custom visuals, flight, light beam, skillset toggle `J`) and **Gauntlet of Darkness** (vault spawning, Blackened mobs & custom tint renderer).
- **Time Warper Private Dimension & Portable Chunks**: `yppowers:time_domain`, **Chunk Duping Domain** (`M`), and **Portable Chunk Swap** (`Z`).
- **Expanded Power Moves**: Kitsune Carry/Mount/Tunnel (`C`), Stick of Deletion Swap (`X`), Shadow Archer Special Arrows toggle (`J`), Swap With Latest Arrow (`Y`), Bow Enchant Swap (`O`), and first/third-person disguise camera mixins; Teleport Gambit Random Portals; Divinity stored damage/reflect.
- **Bundled Client VFX Engine**: 8 custom particle types/textures (`dust_ring`, `foxfire_wisp`, `frost_shard`, `rune`, `soul_wisp`, `spark`, `toxic_bubble`, `web_strand`), client `VfxManager`, camera shake & FOV punch, and custom advancements (*balright bro*, *Yummy Kebab*).
- **Power Menu (`U`) & Test Mode (`/yp test`)**: Built-in Power Menu for rituals, awakenings, and selectors.

### Carried Forward from v2.1.x
- **Anti-Combat-Log PvP Timer (30s default)**: Server-authoritative PvP tagging (`CombatTagManager`), kills combat-loggers preserving soulbound gear, broadcasts combat-log, blocks mid-fight alliance edits, clears on death/`/grace`, adds `/yp combat clear <player>`, and displays a draining red HUD banner.
- **Consent-Based Alliance Invites**: `/yp alliance invite|add|accept|decline|remove|list` with 60s invites, clickable `[Accept]`/`[Decline]` buttons, and cached offline ally names.
- **Anchorable HUD & Live HUD Settings**: `BOTTOM_RIGHT` default (avoiding Simple Voice Chat), live **HUD Settings** tab inside the `U` Power Menu (`/yp guide`), Power Badge + Grace/Grimoire chip, 10-spark Energy bar, and always-visible-under-F3 rendering.
- **Always-Visible Ability Particles on Minimal**: All 301 server particle calls (`force=true, important=true`), client `VfxManager` spawns, and all 8 custom `VfxParticles` stay visible even on Minimal particles.
- Added `/yp config reload`, `/yp power list`, and `/yp guide`.

## 2.1.1

### VFX & Visibility
- **Always-Visible Ability Particles**: All power particles (`VfxSystem`, GOAT sprint wake, Serious Punch tunnel blasts) now spawn with `force = true` and `important = true`, so ability visuals stay visible even when Minecraft Video Settings → Particles is set to **Minimal** (or **Decreased**).

## 2.1.0 — Combat Timer, Consent Alliances, Dynamic HUD Bars & In-Game Power Guide

### Security & PvP Fixes
- **Anti-Combat-Log PvP Timer (30s default)**:
  - Damaging another non-allied player (directly, via projectiles, lightning, or summoned minions) tags both players in combat for 30 seconds (`config/yppowers.json` → `combat`).
  - Logging out while combat-tagged kills the player (preserving soulbound power items via normal death handling) and broadcasts a combat-log message in chat.
  - Displays a live red `COMBAT <time> — DO NOT LOG OUT` banner with a draining bar on the HUD.
  - Blocks alliance invites/accepts/removals mid-fight (`blockAllianceChangesInCombat`), ignores PvE/allies/Creative/Spectator/Grace, clears on death or `/grace`, and adds `/yp combat clear <player>` for operators.
- **Consent-Based Alliance Invites**: Fixed the unilateral force-ally PvP exploit. `/yp alliance invite <player>` (or `/yp alliance add <player>`) now sends a 60-second invite with clickable `[Accept]` and `[Decline]` buttons in chat (`/yp alliance accept <player>` / `/yp alliance decline <player>`).

### HUD & UI Upgrades
- **Dynamic Bound Key Labels**: HUD ability pills and the Power Guide automatically display each player's *actual* rebound keys from Minecraft Controls instead of hardcoded defaults.
- **Smooth Cooldown Progress Bars**: `CooldownSystem` now tracks & persists max cooldown durations alongside remaining ticks, rendering live draining progress fills inside every cooling-down HUD pill.
- **In-Game Power Guide & HUD Settings Screen (`P` or `/yp guide`)**:
  - **Powers Guide tab**: Browse all 17 powers, passives, and abilities with your live bound key labels (auto-selecting your currently assigned power).
  - **HUD Settings tab**: Live-toggle the Energy Bar & Power Panel, switch HUD anchor presets (`TOP_LEFT`, `TOP_RIGHT`, `TOP_CENTER`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`), adjust X/Y pixel offsets with live preview, and save straight to `config/yppowers.json`.

## 2.0.0 — Complete Clean-Slate Rebuild (Java 25 / Fabric 1.21.11)

### Architecture & Modularity
- Rebuilt the entire mod from the ground up with a thin `YPPowersMod` / `YPPowersClient` composition root and zero power-specific switch cascades.
- Replaced god-classes (`PowerManager`, `EnergyManager`, flat 674-line `YPConfig`, magic integer keybinds `0..9`) with:
  - Typed `PowerId`, `AbilitySlot`, `AbilitySpec`, `PowerRegistry`, and per-power NBT bags in `PlayerDataStore` (`yppowers_v2_players`).
  - Dedicated unified subsystems: `EnergySystem`, `CooldownSystem`, `FreezeSystem`, `SummonSystem`, `SoulboundItemSystem`, `TempBlockSystem`, `AllianceSystem`, `GraceSystem`, `GlobalHealthSystem`, `CombatUtil`, and `VfxSystem`.
  - Clean nested JSON configuration (`config/yppowers.json`) with runtime `/yp config reload`.
  - Sub-packaged implementations for the 4 largest powers (`timewarper/`, `shadowarcher/`, `iceemperor/`, `teleportgambit/`) and focused modules for all 17 powers.

### UX & Visual Polish
- Added an upgraded **Power & Cooldown HUD** above the 10-spark Energy bar displaying the player's Power Badge, active Form/Mode/Shard/Flight chip, Grace/Lockout indicators, and live ability cooldown & toggle pills.
- Overhauled the **Shadow Archer Morph Screen** with category filter tabs (`All`, `Flying`, `Aquatic`, `Nether`, `Overworld`, `Boss`), live search, unlock count, unlocked-first sorting, active-form indicator, and one-click Human return.
- Added `/yp power list`, `/yp energy get`, `/yp cooldown clear`, and `/yp config reload`.

### Bug Fixes & Performance
- Fixed `ThorMaceItemMixin` to enforce `PowerId.THOR` ownership and Grace-period gating on both client prediction and server execution.
- Removed obsolete `WorldMixin` heavy-core hooks completely.
- Fixed offline-owner alliance resolution in friendly-minion combat/AI checks and `/yp alliance list` (now displays cached offline names cleanly without trailing commas).
- Guaranteed temporary block rollback on server shutdown via `TempBlockSystem`.
- Added JUnit 5 unit test suite covering Config, Energy, Cooldowns, Alliances, RewindHistory, PowerId/AbilitySlot, and MorphRegistry.
