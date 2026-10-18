# YP Powers

A Fabric mod for **Minecraft Java Edition 1.21.11** that adds 11 unique combat powers. Server operators can assign them to players. Every power has its own abilities, passives, and keybinds, and an alliance system keeps your teammates safe from your attacks.

**[Download the latest release](https://github.com/autumneddd/yppowers/releases/latest)**

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) **0.18.1 or newer** for Minecraft 1.21.11.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) **0.141.6+1.21.11** or newer.
3. Put `yppowers-1.0.0.jar` and Fabric API in your `.minecraft/mods` folder.
4. Launch the game with the Fabric profile.

For multiplayer, the mod must be installed on **both the server and every client**.

---

## Getting started

An operator gives a player a power:

```text
/yp power assign <player> <power>
```

The player can then use the power's abilities with the keybinds below.

---

## Powers

| Power | ID | Summary |
| --- | --- | --- |
| Divinity Shield | `divinity` | Protective dome that blocks enemies and heals allies |
| The GOAT | `goat` | Huge jumps, building sprint speed, and extra health |
| Time Warper | `time_warper` | Freeze time or rewind every player 25 seconds |
| Teleport Gambit | `gambit` | Place a short-lived portal that throws you forward |
| Serious Punch | `punch` | Three-way shockwave that tunnels through terrain |
| Poseidon | `poseidon` | Strong in water, with a trident that switches enchantments |
| Shadow Archer | `shadow_archer` | Special arrows and morphing into mobs you have killed |
| Boots of the Sky | `boots_sky` | Survival flight while wearing special boots |
| Stick of Deletion | `deletion` | A stick that launches enemies across the map |
| Fire God | `fire_god` | Fire immunity, Nether mobs ignore you, Ghast fireballs |
| Ice Emperor | `ice_emperor` | Frost aura and a freezing area attack |

### Divinity Shield
- **`G` — Barrier Dome:** Raises a 12-block dome for 15 seconds. Enemies and projectiles that aren't allied can't get inside.
- Allies inside the dome heal ½ heart every second.
- Reflects 30% of incoming damage back at attackers.
- Comes with a shield that axes can't disable. You keep it when you die.

### The GOAT
- Every jump launches you 2.4× higher, with permanent Jump Boost.
- Sprinting builds your speed up to 2.2× normal.
- +5 hearts, 20% knockback resistance, and 60% less fall damage.
- Your melee attacks deal 10% less damage, for balance.
- **`R`:** Turns the passives on or off.

### Time Warper
- **`G` — Time Stop:** Freezes entities, projectiles, and gravity for 6 seconds, except for allies. Your hits do 50% damage while time is stopped.
- **`H` — Time Warp:** Sends every player in every dimension back to where they were 25 seconds ago, including their health and facing direction. Everyone else gets Slowness II and Mining Fatigue for 6 seconds.

### Teleport Gambit
- **`G` — Gateway:** Places an End Portal for 5 seconds. Stepping into it sends you to safe ground 25–50 blocks in the direction you're looking.
- When you arrive, you get 3 seconds of Slow Falling and 2 seconds of Resistance II.

### Serious Punch
- **`G` — Shockwave:** Fires three punches in a fan: straight ahead, 30° left, and 30° right. Each one clears a 3×3 tunnel up to 60 blocks long.
- Enemies within 25 blocks of the blast take 6.5 hearts of damage, get Weakness II, and are frozen for 1.5 seconds.
- You get Strength III for 10 seconds.

### Poseidon
- **In water:** Dolphin's Grace, Regeneration, Water Breathing, and +20% melee and trident damage.
- **On land:** 10% slower, plus Weakness.
- **`G` / `R` — Trident Swap:** Switches your trident between **Riptide III** and **Loyalty III + Channeling**.
- Comes with a trident that you keep when you die.

### Shadow Archer
- **`B` — Arrow Select:** Cycles through the arrow types you've unlocked.
  - **Split Shot** (unlocked from the start): fires 3 arrows in a cone.
  - **Homing Missile:** seeks out targets within 35 blocks.
  - **TNT Arrow:** explodes on impact.
  - **Lightning Arrow:** calls down 2 lightning strikes.
  - **Ice Prison:** gives Slowness II and Blindness for 4 seconds.
- **Unlocking arrows:** Carry 8 arrows plus the matching item. The item is an **Eye of Ender** for Homing, **TNT** for TNT Arrow, a **Lightning Rod** for Lightning, and **Packed Ice** for Ice Prison.
- **`V` — Morph Menu:** Lets you search for and turn into any mob you have killed. Each form has its own `G` / `H` / `R` abilities and passive traits. For example, Blazes can fly and shoot fireballs, and Endermen can teleport. See the [morph catalog](SHADOW_ARCHER_MORPH_CATALOG.md) for the full list.

### Boots of the Sky
- **`G` or double-tap jump:** Turns flight on or off in survival while you wear the special Netherite Boots.
- Gives 5 minutes of flight, which takes 3 minutes to recharge once it runs out.
- You get Slow Falling automatically when flight turns off or runs out in mid-air.
- Comes with Netherite Boots (Feather Falling IV) that you keep when you die.

### Stick of Deletion
- Hitting anything with a regular Stick launches it about 40 blocks.
- Targets that hit a wall take 4 hearts of extra damage.

### Fire God
- Fully immune to fire, lava, and magma blocks. You can see clearly in lava.
- Nether mobs (Blazes, Ghasts, Piglins, Hoglins, Magma Cubes, Wither Skeletons, and others) ignore you.
- **Ember of the Undying Flame:** A soulbound Blaze Rod that never runs out as furnace fuel.
- **`G` — Ghast Cannon:** Shoots an explosive Ghast fireball.

### Ice Emperor
- **Glacial Aura:** A 4-block aura around you. It freezes water, puts out fires, slows hostile mobs, and slowly damages them with frost.
- **Ice Trail:** Sprinting leaves packed ice behind you. You deal +20% damage while standing on ice.
- **`G` — Absolute Zero:** A 10-block frost wave. Enemies take 2 hearts of damage, are frozen for 2.5 seconds, and get Slowness IV.
- **Weaknesses:** You take 30% more fire and lava damage, and you get Weakness while in the Nether.
- Immune to freezing and powder snow.
- **`R`:** Turns the power on or off.

---

## Controls

You can rebind all keys under **Options → Controls → Key Binds → YP Powers**.

| Key | Action |
| --- | --- |
| `G` | Primary ability |
| `H` | Time Warp (Time Warper), or a secondary morph ability |
| `V` | Shadow Archer morph menu |
| `B` | Shadow Archer arrow select |
| `R` | Alternate ability or power toggle |

---

## Commands

### Power management (operators only)

```text
/yp power assign <player> <power>
/yp power clear <player>
/yp power check [player]
```

### Alliances (all players)

Allies can't hurt each other. Your abilities also won't affect them, including freezes, knockback, and area damage, and they share your healing effects.

```text
/yp alliance add <player>
/yp alliance remove <player>
/yp alliance list
```

---

## Configuration

The mod creates `config/yppowers_config.json` the first time it runs. Edit the file and restart the game or server to apply changes.

Times are measured in **ticks**, where 20 ticks equal 1 second. Setting a cooldown to `0` disables it.

| Setting | Default |
| --- | --- |
| `divinity_heal_radius` | `12` |
| `divinity_heal_amount` | `0.5` |
| `divinity_forcefield_duration_ticks` | `300` (15 s) |
| `divinity_forcefield_cooldown_ticks` | `600` (30 s) |
| `goat_jump_multiplier` | `2.4` |
| `goat_max_speed` | `2.2` |
| `time_stop_max_duration_ticks` | `120` (6 s) |
| `time_stop_cooldown_ticks` | `600` (30 s) |
| `time_warp_rewind_cooldown_ticks` | `600` (30 s) |
| `teleport_gambit_gateway_duration_ticks` | `100` (5 s) |
| `teleport_gambit_gateway_cooldown_ticks` | `300` (15 s) |
| `serious_punch_cooldown_ticks` | `200` (10 s) |
| `punch_raycast_range` | `60` |
| `poseidon_trident_swap_cooldown_ticks` | `60` (3 s) |
| `shadow_archer_morph_cooldown_ticks` | `200` (10 s) |
| `stick_of_deletion_cooldown_ticks` | `80` (4 s) |
| `fire_god_cannon_cooldown_ticks` | `240` (12 s) |
| `boots_flight_duration_ticks` | `6000` (5 min) |
| `boots_recharge_duration_ticks` | `3600` (3 min) |
| `ice_emperor_aura_radius` | `4.0` |
| `ice_emperor_aura_freeze_damage` | `1.0` |
| `ice_emperor_aura_freeze_damage_interval_ticks` | `40` (2 s) |
| `ice_emperor_freeze_duration_ticks` | `50` (2.5 s) |
| `ice_emperor_cooldown_ticks` | `600` (30 s) |

---

## Building from source

You need **Java 21** (a JDK).

```bash
./gradlew build
```

On Windows, run `gradlew.bat build`. The finished JAR is saved in `build/libs/`. The first build needs internet access to download Gradle and the Minecraft dependencies.

To start a test client or server:

```bash
./gradlew runClient
./gradlew runServer
```

---

## License

All Rights Reserved. See [LICENSE](LICENSE) for details.
