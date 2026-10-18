# Port v13.5 — Shadow Archer morph system expansion

## Scope

This revision changes only the Shadow Archer morph experience and its related client/server synchronization. It preserves the existing Shadow Archer arrow selector, special-arrow unlocks, and the v13.2-v13.4 menu, Ice Prison, Serious Punch, and Teleport Gambit changes.

## Supported forms and coverage

The menu is built at runtime from the Minecraft registry and includes every `minecraft:` entity type whose base class is a `MobEntity` in the running 1.21.11 client. This intentionally avoids hard-coding an assumed complete roster and automatically excludes non-mob entities and identifiers that are not present in the installed game version. Each discovered form has an explicit profile below where available; all remaining vanilla mob forms receive a generic profile with a practical attack/movement/defence kit.

### Explicit profile groups

- **Bosses / major threats:** Ender Dragon, Wither, Warden, Elder Guardian, Ravager, Iron Golem, Giant.
- **Magic / ranged:** Blaze, Ghast, Happy Ghast (if registered by the target build), Evoker, Witch, Guardian, Breeze, Skeleton, Stray, Bogged, Parched (if registered by the target build), Wither Skeleton, Pillager, Vindicator, Piglin Brute, Piglin, Drowned, Shulker, Creeper.
- **Teleport, flight, climbing, evasion:** Enderman, Phantom, Bat, Spider, Cave Spider, Vex, Allay, Bee, Parrot.
- **Chargers and tough bodies:** Goat, Hoglin, Zoglin, Slime, Magma Cube, Creaking, Armadillo, Panda, Polar Bear, Wolf, Zombie, Husk, Zombie Villager, Zombified Piglin.
- **Aquatic / lava / movement:** Dolphin, Axolotl, Turtle, Nautilus, Zombie Nautilus, Strider, Snow Golem, Frog, Camel, Camel Husk, Horse, Donkey, Mule, Skeleton Horse, Zombie Horse, Fox, Cat, Ocelot, Rabbit.
- **Utility / passive / unusual forms:** Copper Golem, Villager, Wandering Trader, Llama, Trader Llama, Glow Squid, Squid, Cod, Salmon, Tropical Fish, Pufferfish, Chicken, Cow, Mooshroom, Sheep, Pig, Sniffer, Silverfish, Endermite, Illusioner, Tadpole.

Some names in the static profile map are conditional examples for forward/backward compatibility; they are displayed only if the current runtime registry actually contains them. The runtime registry is the authority for the available menu list. Other vanilla `MobEntity` types automatically receive a generic profile (water-oriented, mount-oriented, ranged, crawler, or general combat profile based on their registry ID).

## Ability model

Profiles declare three active abilities, shown as **G / H / R** in the menu. Abilities are implemented on the server with per-ability cooldowns and bounded nearby-entity checks. Representative assignments include:

- **Ender Dragon:** Dragon Breath, Dragon Roar, Air Dash; flight, resistance and slow falling.
- **Wither:** Wither Barrage, Wither Blast, Air Dash; flight, resistance and fire resistance.
- **Warden:** Sonic Boom, Ground Slam, Echo Pulse; melee strength, resistance and night vision.
- **Enderman:** safe short-range teleport, Shadow Burst, Phase Dash.
- **Blaze / Ghast:** fire attacks and/or gusts with aerial movement and fire protection.
- **Evoker / Witch:** magic-fang style cone attack, weakening brew, utility pulse.
- **Spider / Cave Spider:** web snare, poison bite, charge; wall-climbing support, with Cave Spider poison resistance.
- **Creeper:** manually activated controlled blast that does not damage blocks or the user.
- **Goat:** Headbutt, Charge, Frog Leap.
- **Ravager / Iron Golem:** charge, ground slam, guard.
- **Slime / Magma Cube:** super bounce, slime slam and/or fire burst.
- **Dolphin / Axolotl / Turtle / Nautilus:** aquatic dash, healing, guard or other aquatic skills.
- **Wolf:** melee slash, pack howl, charge; the howl buffs allies recognized by the existing alliance system.
- **Copper Golem / villagers / traders:** utility pulse, guard, healing or trade-wind-style buffs.
- **Any remaining vanilla mob form:** gets a useful generic set of active skills rather than only a changed model.

Other active skills include Guardian Beam, Snowburst, Frog Leap, Fox Dash, Shulker Lift, Puffer Burst, Root Pulse, Healing Pulse, Infection Bite and Sand Shot. The `Ability` enum, cooldown table and executor have been statically checked for complete coverage. A later morph audit follow-up adds a dedicated Strider `LAVA_DASH` ability and the per-form catalog file.

**Mechanics that remain approximations:** ranged attacks such as Wither Barrage, Fire Blast and Guardian Beam currently use server-side cone/raycast-style damage and particles rather than spawning native Wither-skull/fireball projectile entities. This keeps targeting alliance-aware and avoids accidentally enabling vanilla explosive block damage. Native boss AI, multipart body attacks, exact mob attack animations, and some mob-specific behaviors are also not reproduced.

## Controls and progression

- **V:** opens the existing Shadow Archer morph-selection screen immediately for players with Shadow Archer.
- **Search / pages:** find forms by name or registry ID; locked entries remain visible but cannot bypass server validation.
- **Morph / Human:** requests are sent to the server. The server checks the user's kill-unlock record before permitting a form; returning to Human is always permitted.
- **G / H / R:** activates the selected form's three profile abilities while morphed. Outside a morph, existing Shadow Archer arrow behavior is left unchanged.
- **B:** existing arrow selector remains unchanged.

## State cleanup and safety

- The player remains a real `ServerPlayerEntity`; the mob entity is only an invulnerable, no-AI visual disguise proxy.
- Form skills execute server-side, use per-ability cooldowns, filter allies through the existing alliance API, and use local bounded entity boxes rather than world-wide scans per ability.
- The original form-managed status effects, flight state, and custom-name state are snapshotted and restored when returning to Human or changing forms. Temporary morph state is rebuilt after respawn; disconnect and power removal remove the disguise and restore state.
- Ability cooldowns are not cleared by form switching or returning to Human, reducing cooldown bypass. They continue ticking while the player remains online and has Shadow Archer.
- Creeper-style blast is deliberately player-compatible: no block destruction and no self-damage.
- The mob's native AI, boss logic, multipart collision model, and exact vanilla attack implementation are not copied. Boss/mob skills are lightweight custom approximations; abilities do not turn the player into a native mob entity.

## Files changed in v13.5

- `src/main/java/com/yppowers/power/impl/ShadowArcherMorphRegistry.java` — profile/trait/ability registry and runtime vanilla mob roster.
- `src/main/java/com/yppowers/power/impl/ShadowArcherPower.java` — server-side morph abilities, traits, cooldowns, and morph-state cleanup.
- `src/main/java/com/yppowers/client/ShadowArcherMorphScreen.java` — search, paging, locked/unlocked state, ability/trait descriptions.
- `src/main/java/com/yppowers/client/ClientPowerData.java` — client cache for server-synchronized kill unlocks.
- `src/main/java/com/yppowers/network/YPNetworking.java` — morph catalog sync payload and server synchronization.
- `src/main/java/com/yppowers/client/YPClientNetworking.java` — catalog receiver and menu refresh.
- `README.md` — current revision and verification notes.
- `SHADOW_ARCHER_MORPH_CATALOG.md` — complete explicit profile/ability/trait table and generic fallback rules.
- `PORT_V13_5_SHADOW_ARCHER_MORPH_EXPANSION.md` — this change inventory.

## Verification status

Static source checks: passed for ability cooldown/executor coverage, basic delimiter balance in all six edited Java files, JSON parse of all resource JSON files, and ZIP integrity. A full Gradle build was attempted with `./gradlew clean build --no-daemon`, but this environment could not resolve `services.gradle.org`, so Gradle 9.2.1 could not be downloaded. **Compilation is not verified in this environment. In-game behavior—including the full boss roster, disguise visuals, every active skill, multiplayer synchronization and rapid form switching—still requires testing.**


## Complete profile listing
See `SHADOW_ARCHER_MORPH_CATALOG.md` for the per-form G/H/R ability and passive-trait table, plus the fallback rules used for additional runtime vanilla mobs.

## Morph audit follow-up
- Added a dedicated **Lava Dash** active ability to Strider and made its lava-adaptation speed trait depend on actually being in lava, rather than the Nether dimension alone.
- Added `SHADOW_ARCHER_MORPH_CATALOG.md` with all explicit profile IDs, their G/H/R ability assignments and passive traits, plus generic fallback profile rules.


## Strider profile audit follow-up
Strider now has a dedicated `LAVA_DASH` ability. It applies a stronger movement burst while the player is actually in lava and the `LAVA_ADAPTED` passive grants extra speed while in lava, instead of using the Nether dimension as a proxy for lava contact.
