# Changelog

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
