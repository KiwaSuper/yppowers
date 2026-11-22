# Changelog

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
