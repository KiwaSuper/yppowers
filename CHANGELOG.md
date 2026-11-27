# Changelog

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
