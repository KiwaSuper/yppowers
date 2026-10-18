# Shadow Archer Morph Catalog — Minecraft Java 1.21.11

## Roster policy

The morph picker is built dynamically from the current Minecraft entity registry. It includes every registered `minecraft:` entity whose base class is a `MobEntity` in the running 1.21.11 client. The source does **not** rely on a fixed list to determine coverage: each runtime mob gets a form entry. This explicit-profile table documents the forms with bespoke assignments; any additional vanilla mob type receives the generic fallback listed below. IDs in the table are only retained if they exist in the actual runtime registry, so conditional/absent IDs are not shown in the live picker.

All abilities are player-compatible server-side effects; native mob AI, multipart boss collision, exact vanilla animations, and native projectile/boss behavior are approximated rather than copied. Search the registry ID or display name in the V morph screen. Forms remain locked until the Shadow Archer player has defeated the corresponding mob type, and the server validates requests.

## Explicit profiles

Each profile lists its `G / H / R` active abilities and passive traits.

| Mob registry ID | G ability | H ability | R ability | Passive traits |
|---|---|---|---|---|
| `minecraft:allay` | Air Dash | Healing Pulse | Utility Pulse | Flight, Slow falling |
| `minecraft:armadillo` | Armadillo Roll | Guard | Charge | Damage resistance |
| `minecraft:axolotl` | Healing Pulse | Poison Bite | Aquatic Dash | Water breathing and faster swimming, Slow regeneration |
| `minecraft:bat` | Echo Pulse | Air Dash | Phase Dash | Flight, Night vision, Slow falling |
| `minecraft:bee` | Venom Sting | Gust | Air Dash | Flight, Slow falling |
| `minecraft:blaze` | Fire Blast | Flame Burst | Air Dash | Flight, Fire resistance |
| `minecraft:bogged` | Spore Cloud | Ranged Shot | Fox Dash | None beyond player baseline |
| `minecraft:breeze` | Gust | Charge | Air Dash | Slow falling, Enhanced jumping |
| `minecraft:camel` | Equine Dash | Ground Slam | Guard | Enhanced jumping, Damage resistance |
| `minecraft:camel_husk` | Equine Dash | Ground Slam | Guard | Enhanced jumping, Damage resistance |
| `minecraft:cat` | Fox Dash | Charge | Utility Pulse | Enhanced speed, Enhanced jumping, Slow falling |
| `minecraft:cave_spider` | Poison Bite | Web Snare | Charge | Wall climbing, Enhanced jumping, Poison resistance |
| `minecraft:chicken` | Frog Leap | Gust | Fox Dash | Enhanced jumping, Slow falling |
| `minecraft:cod` | Aquatic Dash | Gust | Utility Pulse | Water breathing and faster swimming, Enhanced speed |
| `minecraft:copper_golem` | Utility Pulse | Guard | Charge | Damage resistance |
| `minecraft:cow` | Ground Slam | Healing Pulse | Charge | Damage resistance |
| `minecraft:creaking` | Creaking Root Pulse | Ground Slam | Guard | Damage resistance |
| `minecraft:creeper` | Controlled Blast | Gust | Charge | Damage resistance |
| `minecraft:dolphin` | Aquatic Dash | Healing Pulse | Charge | Water breathing and faster swimming, Enhanced speed |
| `minecraft:donkey` | Equine Dash | Charge | Guard | Enhanced speed, Enhanced jumping |
| `minecraft:drowned` | Ranged Shot | Poison Bite | Aquatic Dash | Water breathing and faster swimming |
| `minecraft:elder_guardian` | Guardian Beam | Spore Cloud | Aquatic Dash | Water breathing and faster swimming, Damage resistance |
| `minecraft:ender_dragon` | Dragon Breath | Dragon Roar | Air Dash | Flight, Damage resistance, Slow falling |
| `minecraft:enderman` | Teleport | Shadow Burst | Phase Dash | Enhanced melee strength, Damage resistance, Night vision |
| `minecraft:endermite` | Poison Bite | Phase Dash | Charge | Enhanced speed |
| `minecraft:evoker` | Evoker Fangs | Witch's Brew | Utility Pulse | Damage resistance |
| `minecraft:fox` | Fox Dash | Poison Bite | Charge | Enhanced speed, Slow falling |
| `minecraft:frog` | Frog Leap | Web Snare | Charge | Enhanced jumping, Water breathing and faster swimming |
| `minecraft:ghast` | Fire Blast | Gust | Air Dash | Flight, Fire resistance, Slow falling |
| `minecraft:ghastling` | Gust | Air Dash | Healing Pulse | Flight, Slow falling |
| `minecraft:giant` | Ground Slam | Charge | Guard | Enhanced melee strength, Damage resistance |
| `minecraft:glow_squid` | Echo Pulse | Gust | Aquatic Dash | Water breathing and faster swimming, Night vision |
| `minecraft:goat` | Headbutt | Charge | Frog Leap | Enhanced jumping, Damage resistance |
| `minecraft:guardian` | Guardian Beam | Poison Bite | Aquatic Dash | Water breathing and faster swimming, Damage resistance |
| `minecraft:happy_ghast` | Gust | Air Dash | Healing Pulse | Flight, Slow falling |
| `minecraft:hoglin` | Charge | Melee Slash | Ground Slam | Enhanced melee strength, Damage resistance |
| `minecraft:horse` | Equine Dash | Charge | Guard | Enhanced speed, Enhanced jumping |
| `minecraft:husk` | Infection Bite | Melee Slash | Charge | Enhanced melee strength |
| `minecraft:illusioner` | Phase Dash | Ranged Shot | Shadow Burst | Slow falling, Night vision |
| `minecraft:iron_golem` | Ground Slam | Guard | Charge | Enhanced melee strength, Damage resistance |
| `minecraft:llama` | Gust | Charge | Guard | Damage resistance |
| `minecraft:magma_cube` | Super Bounce | Flame Burst | Slime Slam | Bouncy movement, Enhanced jumping, Fire resistance |
| `minecraft:mooshroom` | Ground Slam | Healing Pulse | Charge | Damage resistance |
| `minecraft:mule` | Equine Dash | Charge | Guard | Enhanced speed, Enhanced jumping |
| `minecraft:nautilus` | Aquatic Dash | Charge | Guard | Water breathing and faster swimming, Damage resistance |
| `minecraft:ocelot` | Fox Dash | Charge | Utility Pulse | Enhanced speed, Enhanced jumping, Slow falling |
| `minecraft:panda` | Ground Slam | Guard | Charge | Enhanced melee strength, Damage resistance |
| `minecraft:parched` | Sand Shot | Ranged Shot | Charge | Slow falling |
| `minecraft:parrot` | Air Dash | Gust | Echo Pulse | Flight, Slow falling |
| `minecraft:phantom` | Charge | Shadow Burst | Air Dash | Flight, Slow falling, Night vision |
| `minecraft:pig` | Ground Slam | Healing Pulse | Charge | Damage resistance |
| `minecraft:piglin` | Ranged Shot | Melee Slash | Charge | Fire resistance |
| `minecraft:piglin_brute` | Melee Slash | Ground Slam | Guard | Enhanced melee strength, Damage resistance |
| `minecraft:pillager` | Ranged Shot | Melee Slash | Charge | None beyond player baseline |
| `minecraft:polar_bear` | Charge | Melee Slash | Guard | Enhanced melee strength, Damage resistance |
| `minecraft:pufferfish` | Puffer Burst | Aquatic Dash | Guard | Water breathing and faster swimming, Poison resistance |
| `minecraft:rabbit` | Fox Dash | Frog Leap | Charge | Enhanced speed, Enhanced jumping |
| `minecraft:ravager` | Charge | Ground Slam | Guard | Enhanced melee strength, Damage resistance |
| `minecraft:salmon` | Aquatic Dash | Gust | Utility Pulse | Water breathing and faster swimming, Enhanced speed |
| `minecraft:sheep` | Ground Slam | Healing Pulse | Charge | Damage resistance |
| `minecraft:shulker` | Levitation Shot | Guard | Phase Dash | Damage resistance |
| `minecraft:silverfish` | Poison Bite | Phase Dash | Charge | Enhanced speed |
| `minecraft:skeleton` | Ranged Shot | Melee Slash | Fox Dash | None beyond player baseline |
| `minecraft:skeleton_horse` | Equine Dash | Charge | Guard | Enhanced speed, Enhanced jumping |
| `minecraft:slime` | Super Bounce | Slime Slam | Charge | Bouncy movement, Enhanced jumping, Damage resistance |
| `minecraft:sniffer` | Creaking Root Pulse | Utility Pulse | Ground Slam | Damage resistance |
| `minecraft:snow_golem` | Snowburst | Spore Cloud | Gust | Slow falling |
| `minecraft:spider` | Web Snare | Poison Bite | Charge | Wall climbing, Enhanced jumping |
| `minecraft:squid` | Echo Pulse | Gust | Aquatic Dash | Water breathing and faster swimming, Night vision |
| `minecraft:stray` | Snowburst | Ranged Shot | Fox Dash | Slow falling |
| `minecraft:strider` | Lava Dash | Guard | Charge | Fire resistance, Lava adaptation |
| `minecraft:tadpole` | Aquatic Dash | Gust | Utility Pulse | Water breathing and faster swimming, Enhanced speed |
| `minecraft:trader_llama` | Gust | Charge | Guard | Damage resistance |
| `minecraft:tropical_fish` | Aquatic Dash | Gust | Utility Pulse | Water breathing and faster swimming, Enhanced speed |
| `minecraft:turtle` | Guard | Aquatic Dash | Ground Slam | Water breathing and faster swimming, Damage resistance |
| `minecraft:vex` | Phase Dash | Melee Slash | Air Dash | Flight, Slow falling |
| `minecraft:villager` | Trade Winds | Healing Pulse | Utility Pulse | Slow regeneration |
| `minecraft:vindicator` | Melee Slash | Ground Slam | Charge | Enhanced melee strength |
| `minecraft:wandering_trader` | Trade Winds | Healing Pulse | Utility Pulse | Slow regeneration |
| `minecraft:warden` | Sonic Boom | Ground Slam | Echo Pulse | Enhanced melee strength, Damage resistance, Night vision |
| `minecraft:witch` | Witch's Brew | Spore Cloud | Utility Pulse | None beyond player baseline |
| `minecraft:wither` | Wither Barrage | Wither Blast | Air Dash | Flight, Damage resistance, Fire resistance |
| `minecraft:wither_skeleton` | Wither Barrage | Melee Slash | Charge | Fire resistance, Enhanced melee strength |
| `minecraft:wolf` | Melee Slash | Pack Howl | Charge | Enhanced speed, Enhanced melee strength |
| `minecraft:zoglin` | Charge | Melee Slash | Ground Slam | Enhanced melee strength, Damage resistance |
| `minecraft:zombie` | Infection Bite | Melee Slash | Charge | Enhanced melee strength |
| `minecraft:zombie_horse` | Equine Dash | Infection Bite | Guard | Enhanced speed, Enhanced jumping |
| `minecraft:zombie_nautilus` | Aquatic Dash | Poison Bite | Charge | Water breathing and faster swimming, Enhanced melee strength |
| `minecraft:zombie_villager` | Infection Bite | Melee Slash | Charge | Enhanced melee strength |
| `minecraft:zombified_piglin` | Melee Slash | Charge | Ground Slam | Fire resistance, Enhanced melee strength |

**Explicit profiles:** 90 registry IDs. Every other registered vanilla `MobEntity` receives a fallback profile automatically.

## Generic fallback profiles

| Runtime ID pattern | G ability | H ability | R ability | Traits |
|---|---|---|---|---|
| Contains `fish`, `squid`, `nautilus`, `axolotl`, `dolphin`, or `turtle` | Aquatic Dash | Gust | Healing Pulse | Water breathing and faster swimming; speed |
| Contains `horse`, `camel`, or `llama` | Equine Dash | Charge | Guard | Speed; jump |
| Contains `skeleton`, `pillager`, or `archer` | Ranged Shot | Melee Slash | Fox Dash | Slow falling |
| Contains `spider` or `arthropod` | Poison Bite | Web Snare | Charge | Wall climbing; speed |
| Any other vanilla `MobEntity` without explicit profile | Melee Slash | Guard | Charge | Resistance |

## Ability implementation notes

- `DRAGON_BREATH`, `DRAGON_ROAR`, `WITHER_BARRAGE`, `WITHER_BLAST`, `SONIC_BOOM`, `BEAM`, and other ranged skills use bounded server-side cone/radius checks, damage/status effects, particles, and sounds. They do not spawn native Wither skulls or destructive fireballs, and they do not use vanilla boss AI.
- `TELEPORT` and `PHASE_DASH` use safe destination checks; they fall back to a dash where appropriate.
- `CREEPER_BLAST` damages/knocks back nearby non-allies, but does not damage blocks or harm the user.
- `WALL_CLIMB` uses a modest upward-velocity assist while horizontal collision is detected. `FLIGHT` grants the vanilla player flight toggle while morphed.
- Aquatic abilities grant water breathing and water mobility effects; aquatic utility and healing skills are kept on player-side state.
- Cooldowns are server-side and tracked per player/per ability; switching forms does not clear active cooldowns. Returning to Human is always allowed.
- All area attacks filter nearby entities through the existing alliance manager to avoid harming allied players.

## State safety

- The player remains a `ServerPlayerEntity`; a separate invulnerable, no-AI entity is used as the visible disguise proxy.
- Switching forms or returning to Human discards the old proxy and restores the form-managed effects, flight state, custom name, and name visibility snapshot.
- On respawn the old proxy is removed and the active form is rebuilt on the next server tick. On disconnect or power removal, the morph is cleaned up. Dimension changes cause the proxy to be recreated in the player’s current world.
- The server validates that the player owns Shadow Archer, that the requested ID is a vanilla `MobEntity`, and that the player has the corresponding kill unlock. The client UI cannot grant unlocks or abilities.

## Files changed for v13.5

- `src/main/java/com/yppowers/power/impl/ShadowArcherMorphRegistry.java` — runtime roster, explicit profiles, generic fallback.
- `src/main/java/com/yppowers/power/impl/ShadowArcherPower.java` — form abilities, traits, cooldowns, disguise and cleanup.
- `src/main/java/com/yppowers/client/ShadowArcherMorphScreen.java` — searchable paginated picker and ability/trait description.
- `src/main/java/com/yppowers/client/ClientPowerData.java` — synced kill-unlock cache.
- `src/main/java/com/yppowers/network/YPNetworking.java` — registry-kill catalog synchronization.
- `src/main/java/com/yppowers/client/YPClientNetworking.java` — catalog receiver and screen refresh.
- `README.md`, `PORT_V13_5_SHADOW_ARCHER_MORPH_EXPANSION.md`, and this catalog file — documentation.

## Verification status

- Java 21 parser check: all project Java sources parsed with zero syntax/parser errors.
- Ability coverage audit: all 47 declared ability enum values appear in both the cooldown table and ability executor.
- Resource JSON validation and ZIP integrity checks passed.
- Full Gradle build could not start because the environment could not resolve `services.gradle.org` to download Gradle 9.2.1. Compilation is therefore **not verified** here. In-game behavior for all forms, proxy collisions, boss profiles, and multiplayer synchronization still requires testing.
