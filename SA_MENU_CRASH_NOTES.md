# Shadow Archer (SA) menu and input notes — Port v11

The tester reports that V no longer crashes but the morph menu does not open, B appears ineffective, and Poseidon lacks underwater breathing. The original SA crash report was not provided, so this revision addresses the observed behavior rather than claiming a proven crash root cause.

## V — morph menu

- Removed the additional `ServerPlayNetworking.canSend` gate from the server's menu response. The server only reaches this handler after receiving the mod's keybind payload and verifying that the player has Shadow Archer; it now sends the registered `yppowers:open_morph_menu` payload directly.
- Removed the redundant `canSend` check from the morph form submission. The screen only exists after the registered mod packet flow has already succeeded; connection/world guards remain.
- The client receiver logs successful opening at debug level and reports/logs if the screen fails to install. Initialization failures continue to be logged.

## B — special arrow selection

- B cycles only arrow types that have been unlocked, as specified in `ORIGINAL_SPECIFICATION.md`.
- If only Split Shot is unlocked, B now explains the unlock conditions instead of merely echoing Split Shot, so the player can tell the key was received.
- Unlocks are now saved in the world's `PowerManager` persistent data and restored on join/reassignment, rather than being lost from the in-memory map after reconnects or server restarts.
- Unlocking still consumes 8 regular arrows and one thematic item: Ender Eye (Homing Missile), TNT (TNT Arrow), Lightning Rod (Lightning Arrow), or Packed Ice (Ice Prison). The server checks inventory and unlocks automatically when the materials are present.

## Poseidon — underwater breathing

- Poseidon refreshes Water Breathing while touching or submerged in water.
- Water Breathing is removed when the player is no longer in water and when Poseidon is removed, consistent with the existing Dolphin's Grace and Regeneration passives.

## Verification status

The source was statically reviewed and the resource JSON files were validated. This authoring environment has no installed Gradle executable and could not download a distribution previously; no dependency-backed compilation or in-game test is claimed. Run `./gradlew clean build` using the project's Gradle 9.2.1 bootstrap on the same setup that built v8, then verify V, B (including arrow unlocking), and underwater breathing on the test server.
