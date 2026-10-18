# YP Powers Port v11 — Tester follow-up

Target: Minecraft Java 1.21.11, Fabric, Java 21.

## Fixes

- SA V: removed the extra `ServerPlayNetworking.canSend` gate that could suppress the menu response. The server sends the registered open-menu payload after receiving the keybind request and verifying the player is assigned Shadow Archer.
- SA V diagnostics: server logs the menu response; client logs a successful screen install. The client reports if screen initialization or rendering fails instead of silently failing without feedback.
- SA form submission: removed the redundant ability-channel check from the morph request; live player/world/network-handler checks remain. The server still verifies that the player has Shadow Archer before applying a morph.
- SA B: cycles through arrow types that have been unlocked. If only Split Shot is unlocked, B now explains the materials needed for each other type instead of looking like a no-op.
- SA arrow unlock persistence: unlocked arrow types are saved in `PowerManager` world persistent data and restored when a player joins or is reassigned Shadow Archer. Existing worlds without this field remain readable.
- Poseidon: grants Water Breathing while touching or submerged in water and removes it when leaving water or when Poseidon is removed.

## Arrow unlock conditions

The existing design is preserved: the server automatically unlocks an arrow type when the player has at least 8 standard arrows plus the matching item, consuming those materials. Homing Missile requires an Ender Eye; TNT Arrow requires TNT; Lightning Arrow requires a Lightning Rod; Ice Prison requires Packed Ice. Split Shot is unlocked by default. B cycles unlocked types, not locked types.

## Verification status

Static checks cover Java delimiter balance, resource JSON parsing, expected source hooks, and ZIP integrity. A full build could not be run in this authoring environment because the Gradle 9.2.1 bootstrap cannot resolve `services.gradle.org`. The user has previously confirmed that Port v8 built on their machine; Port v11 changes need a fresh `./gradlew clean build` before use. In-game behavior is not claimed as verified.
