# Port v13.4 — Teleport Gambit immediate entry and anti-bounce lockout

- Removed the activation-time entrant ignore list, so the player standing on the newly created End Portal triggers it immediately on the next server tick.
- Added a per-player 20-tick transfer lockout after successful teleportation. Active gateways skip locked-out players, preventing immediate gate-to-gate bounce loops.
- Lockout expiry uses world ticks; disconnected players have their own lockout entry cleared.
- Preserved the configured gateway lifetime, creation cooldown, destination search, arrival effects, and block restoration behavior.
- A full Gradle build and in-game multiplayer verification are still required; the authoring environment has previously been unable to fetch Gradle from services.gradle.org.
