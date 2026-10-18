# Port changelog — Minecraft 1.21.11

This source port preserves the eleven existing YP Powers implementations while migrating interfaces changed between the original 1.20.4 project and Minecraft 1.21.11.

## Source changes in this pass

- Migrated networking from identifier/raw-buffer channels to typed custom payload records and `PacketCodec`s for keybind, morph request, Fire God sync, and morph-menu messages.
- Migrated YP item flags to the `CUSTOM_DATA` component and custom item names/unbreakable status to item data components.
- Updated trident and boot enchantment setup to registry-backed enchantments.
- Updated keybinding categories and attribute modifier IDs/operations.
- Migrated the `PowerManager` and `AllianceManager` persistent-state setup to codec-backed `PersistentStateType`s, retaining the previous NBT structure so existing data can be read.
- Updated NBT accessor calls to the current 1.21.11 fallback/empty-compound APIs.
- Updated entity damage calls/injections for the `ServerWorld` parameter.
- Updated entity teleport calls to pass position flags and camera-reset behavior.
- Updated entity creation to provide a `SpawnReason`.
- Updated the Shadow Archer TNT-arrow explosion to the current full `World.createExplosion` signature.
- Replaced the removed legacy fog-rendering hook with a `FogRenderer` hook that adjusts environmental lava fog for Fire God.
- Updated the End Portal collision injection to include `EntityCollisionHandler` and the teleport boolean.
- Updated the furnace fuel-time hook to accept `FuelRegistry`.

- Corrected all three typed-payload encoders to use `PacketCodec.of`'s value-first encoder contract.
- Upgraded the project bootstrap scripts from Gradle 8.14.3 to Gradle 9.2.1, the minimum supported by Fabric Loom 1.14, and pinned the official binary distribution SHA-256.

## Verification status

The migration is **not yet a verified release**. Source was inspected against the 1.21.11 Yarn API documentation, but Gradle could not be bootstrapped in this environment because `services.gradle.org` could not be resolved. A full compile, mixin application, dedicated-server launch, client launch, and in-game testing remain outstanding. No claim is made that the current source compiles without further fixes.

## Follow-up audit pass (v4 source package)

- Reconciled the README's Java/Gradle requirements with the target configuration (Java 21 and Gradle 9.2.1).
- Rechecked metadata JSON, mixin configuration, key custom-payload registrations, current NBT API calls, and the updated fog/explosion/teleport/entity hooks.
- The static checks are not a substitute for the mapped Minecraft compile: the environment still cannot bootstrap Gradle because its distribution host is unreachable, so no successful compilation or runtime behavior is claimed.

## Follow-up audit pass (v5 source package)

- Configured Loom to generate `yppowers.refmap.json`, matching the declared mixin config name.
- Enabled Loom's legacy Mixin annotation processor as a compatibility fallback for remapping injector targets on the obfuscated 1.21.11 runtime.
- Corrected stale port-status text that still named the previous Gradle version.
- Re-ran source syntax parsing and static resource/launcher checks; a mapped Gradle build still could not be run because this environment cannot resolve the Gradle distribution host.

- Rechecked the furnace fuel-time hook against the 1.21.11 Yarn signature: `getFuelTime(FuelRegistry, ItemStack)` is an instance method, so the injection handler remains an instance callback.


## v6 bug-audit fixes
- Fixed configuration startup persistence so the loaded config object is saved, preserving user values.
- Removed the PlayerEntity.disableShield mixin injection because that method is absent in the 1.21.11 Yarn API; targeting it could fail the required mixin application. The old shield-disable immunity hook is not implemented by this port until a verified 1.21.11 equivalent can be identified.
- Coordinated velocity restoration when Time Warper, Serious Punch, and Ice Emperor freezes overlap.
- Prevented ability keybinds from sending packets while screens/menus are open and drained queued presses when input is inactive.
- Changed Poseidon trident mode detection to inspect the actual enchantment component after reconnects, and restricted swapping to the mod's own tagged trident so normal tridents are not overwritten.
- Used give-or-drop handling for power-granted and death-retained items so full inventories do not silently lose them.

## Verification notes
- Source audit and syntax-only parsing are not equivalent to a Gradle build. Minecraft/Fabric dependency resolution was unavailable in this environment, so compilation, mixin application, and runtime behavior remain unverified.

## v6 bug audit
See `BUG_AUDIT_1.21.11.md` for the findings, fixes, validation checks, and unresolved limitations.


## v7 compiler-report fixes

- Migrated Entity accessors to 1.21.11 Yarn names (`getEntityWorld`, `getEntityPos`) and cast server-player worlds where a `ServerWorld` is required.
- Replaced removed `velocityModified` field writes with an invoker for the protected `scheduleVelocityUpdate` hook.
- Updated the fireball constructor to pass a `Vec3d` velocity.
- Updated stick cooldown use to an `ItemStack`, the explosion sound to its value, and entity registry lookup to `getOptionalValue`.
- Updated the UNBREAKABLE item component to its 1.21.11 `Unit` marker.
- Normalized `settings.gradle` root project assignment syntax.


## Port v8 — compiler report follow-up

- Updated four `World.isClient` field reads to the 1.21.11 `isClient()` method.
- Migrated the Shadow Archer morph screen to `keyPressed(KeyInput)` and `input.isEnter()`.
- Added a Mixin accessor for the private persistent-projectile damage value because 1.21.11 has `setDamage` but no public getter.
- Migrated `/yp power` permission gating to `PermissionPredicate` / `Permission.Level(GAMEMASTERS)`.
- Switched to Loom's default in-place Mixin remapping and removed the legacy Mixin AP/refmap configuration, addressing the non-fatal `Cannot remap modifiers ... targets []` warning source path.
- Updated `settings.gradle` to use explicit Groovy property assignment.

## Port v10 — cooldown defaults and Shadow Archer menu hardening

- Enabled the previously optional Time Warp rewind cooldown at 600 ticks (30 seconds) and Teleport Gambit gateway cooldown at 300 ticks (15 seconds).
- Added `config_version: 2` and a one-time migration for v9-generated config files that stored those new cooldowns as zero. Users can set either value to `0` after migration to disable it.
- Guarded client keybind and morph requests against disconnected/no-world/no-network states and checked that the server declared each custom payload before sending.
- Prevented duplicate or late Shadow Archer menu-open payloads from reopening the screen during an active screen or while disconnecting; added logging for menu initialization exceptions.
- Moved the morph text field's initial focus to Minecraft 1.21.11's `Screen.setInitialFocus()` lifecycle hook.
- Logged and gracefully closed the custom screen if widget rendering throws a recoverable `RuntimeException`, instead of repeatedly trying to draw a broken screen.
- The tester did not provide a crash report for the Shadow Archer menu, so this pass addresses lifecycle and networking edge cases but cannot establish the original crash's root cause. Runtime verification is still required.
