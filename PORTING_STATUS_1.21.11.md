# YP Powers — Minecraft 1.21.11 Port Status

## Target configuration

- Minecraft: `1.21.11`
- Yarn mappings: `1.21.11+build.6`
- Fabric Loader: `0.18.1`
- Fabric API: `0.141.6+1.21.11`
- Fabric Loom: `1.14.10`
- Gradle bootstrap: `9.2.1` (Fabric Loom 1.14 requires Gradle 9.2 or newer)
- Java: `21`

## Migrated source areas

- Fabric typed custom-payload networking and codecs.
- Item data components for custom flags, item names, unbreakable status, and enchantments.
- Keybinding categories and attribute modifier APIs.
- Codec-backed world persistent states for assigned powers, Shadow Archer kill unlocks, and player alliances.
- Current NBT accessor methods.
- Entity damage signatures and teleport signatures.
- `EntityType.create` calls with explicit spawn reasons.
- The 1.21.11 explosion API used by Shadow Archer TNT arrows.
- The updated `FogRenderer` API used by Fire God lava-vision behavior.
- End Portal collision and furnace fuel hook signatures.

## Still needs verification

This is a continuing source port, **not yet a verified release JAR**. I could not run Gradle because the environment failed to resolve `services.gradle.org` while trying to fetch the pinned Gradle 9.2.1 distribution. Consequently, the following have not been confirmed:

- Successful Java compilation against the Minecraft 1.21.11 mapped game JAR.
- Successful Mixin transformation/target application at startup.
- Client and dedicated-server startup.
- Saved-data migration from a world previously used with the original version.
- Runtime behavior of all eleven powers and client/server networking.

The old persistent-data NBT field names are retained by the codec-backed state wrappers to help maintain compatibility with existing worlds, but this has not been tested in-game.

## Follow-up audit pass

The v4 source package also corrects contradictory Java/Gradle requirements in `README.md`. A static audit validated all resource JSON files and the Unix launcher shell syntax, and a source scan found no remaining occurrences of the common removed NBT/networking patterns. These checks do not validate Java method/field resolution against the game JAR, Mixin application, or in-game behavior.


## Follow-up audit pass (v5 source package)

- Fixed the `PacketCodec.of` encoder order in the three non-unit payload codecs. The value now precedes the buffer as required by the current API.
- Updated checksum-verifying bootstrap launchers to Gradle 9.2.1, which satisfies Loom 1.14's Gradle 9.2 minimum.
- Configured the mixin annotation processor and its refmap filename explicitly so that it matches `yppowers.mixins.json`.
- A full Gradle build remains unavailable in this environment because DNS/network access to `services.gradle.org` fails.

- Rechecked `AbstractFurnaceBlockEntity.getFuelTime(FuelRegistry, ItemStack)` against the 1.21.11 Yarn docs and kept its injection handler non-static to match the instance target.


## v6 bug-audit update
Source-level fixes applied for config overwrite, an obsolete required shield mixin target, overlapping freeze restoration, GUI key handling, Poseidon trident state, and full-inventory item delivery. The shield-disable immunity behavior is currently omitted because the old target method is absent in 1.21.11; it needs a separately verified implementation if still desired.

Status remains **unverified**: no successful Gradle compilation or in-game test was possible in the available environment.


For the latest source-level bug audit and exact limitations, see `BUG_AUDIT_1.21.11.md`. The v6 source has passed syntax-only parsing and resource/metadata checks; it has **not** passed a Gradle build.


## Latest follow-up (v8)

The next reported compile errors were patched at source level. The full project has not been compiled in this environment, so these changes still require a local `./gradlew clean build` to establish complete API compatibility.


## v11 tester follow-up

- Sent the SA open-menu payload directly after server-side keybind/power validation instead of gating it on `ServerPlayNetworking.canSend`; added server/client log breadcrumbs and visible errors if screen setup or rendering fails.
- Preserved the SA arrow-unlock design, added clearer B-key feedback when Split Shot is the only unlocked option, and moved arrow unlock storage into world persistent data.
- Added Poseidon's missing Water Breathing passive with removal outside water and when the power is removed.
- Static source/resource checks passed, but the Gradle 9.2.1 download is still blocked by DNS in the authoring environment. The v11 changes require `./gradlew clean build` and in-game confirmation by the project owner/testers.


## Port v12 changes
- GOAT jump scaling is now applied to every server-side jump without a cooldown or movement requirement.
- `R` toggles GOAT passives and Ice Emperor power on/off. Disabled states persist through reconnects and server restarts.
- Added visible snowflake-field particles for Ice Emperor.
- Expanded Fire God targeting immunity to more Nether-native mobs and adds a pre-AI mixin hook to clear pre-existing targets before mobs act.
- This revision has not been confirmed by a clean Gradle build; run `./gradlew clean build`.
