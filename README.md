# YP Powers (v2.0)

A Fabric mod for **Minecraft Java Edition 1.21.11** (compiled with **Java 25**) adding **17 unique combat powers**, a shared Energy & Bleeding system, bidirectional alliances, global Grace periods, a persistent Pocket Domain dimension, mob morphing, an upgraded Power & Cooldown HUD, and clean structured JSON configuration.

---

## Requirements & Installation

1. **Java 25** (JDK/JRE 25 LTS)
2. **Minecraft 1.21.11** with [Fabric Loader](https://fabricmc.net/use/) **0.18.1+**
3. [Fabric API](https://modrinth.com/mod/fabric-api) **0.141.6+1.21.11+**
4. Place `yppowers-2.0.0.jar` and Fabric API in `.minecraft/mods` (required on **both server and client** for multiplayer).

---

## Getting Started

```text
/yp power assign <player> <power-id>
/yp power list
```

Once assigned, your HUD displays the **10-spark Energy bar**, your **Power Badge**, active **Form/Mode chip**, and **live ability cooldown & toggle pills**.

---

## Powers (17 Total)

| Power | ID | Highlights |
| --- | --- | --- |
| Divinity Shield | `divinity` | Barrier dome (`G`) blocking enemies/projectiles & healing allies, 30% damage reflect, soulbound shield |
| The GOAT | `goat` | Super jumps + fall protection, ramping sprint speed, free passive toggle (`R`), Overdrive + deferred damage debt (`V`) |
| Time Warper | `time_warper` | TIME/REALITY forms (`R`), Time Stop (`G`), 25s Global Rewind (`H`), item/block-charged Black Hole (`V`), Time & Elite Clones (`G`/`B`) |
| Teleport Gambit | `gambit` | Forward End Portal Gateway (`G`) & multi-stage Pocket Domain (`H` → `yppowers:tg_domain`) |
| Serious Punch | `punch` | 3-way tunneling Shockwave (`G`), HELLDIVE crater slam (`H`), bare-fist Momentum stacks |
| Poseidon | `poseidon` | Water buffs + hunger freeze, soulbound Riptide/Loyalty Trident swap (`G`/`R`), particle Tsunami (`H`) |
| Shadow Archer | `shadow_archer` | Unlockable special arrows (`B`: Split Shot, Homing, TNT, Lightning, Ice Prison, Railshot) & searchable/filterable Mob Morphs (`V`) |
| Boots of the Sky | `boots_sky` | Soulbound flight boots (`G` / double-tap `Space`) with recharge & upkeep, Feather Falling toggle (`N`), Raigo lightning strike (`R`) |
| Stick of Deletion | `deletion` | Regular Stick launches enemies across the map with wall-impact collision damage |
| Fire God | `fire_god` | Fire/lava immunity + clear lava vision, Nether mob truce, infinite-fuel Blaze Rod, Ghast Cannon (`G`), Ember Guard (`H`), Cinder Sentinel (`V`) |
| Ice Emperor | `ice_emperor` | Glacial Aura & Ice Trail (`R`), Absolute Zero (`G`), Ice Meteor (`H`), Frost Guard bears (`V`), Armoured/Stealth/Assassin forms (`K`), Heat Blast (`L`) |
| Thor | `thor` | Unbreakable soulbound mace with hold-right-click Riptide anywhere, lightning on hit, lightning immunity, permanent Speed II |
| Fallout Warrior | `fallout_warrior` | 3-role Radioactive Squad (`G`), Toxic Pool (`H`), Radioactive heal/harm Aura (`R`) |
| Spidey | `spidey` | Web Sling (`G` / Web Slingers item), Web Grenade (`H`), Rage Mode (`V`), chained Spider Dive (`B`), Auto-Cobweb MLG (`R`) |
| Spirit Channeler | `spirit_channeler` | Unlockable Wolf/Guardian/Wraith spirits (`G`, cycle `B`/`R`), Shadow Step (`H`), hazardous Restless Shadows (`V`) |
| Illusionist | `illusionist` | Armed 3×3×3 Spatial Erasure (`G` + right-click), Psychokinetic Grip (`H`, throw `B`/attack, dist `R`), Celestial Pull hollow moon (`V`) |
| Kitsune | `kitsune` | Giant Kitsune form (`R`, long-range block placement), Claw Swipe (`G`), Foxfire Burst (`H`), Spirit Pounce (`V`), Foxfire Detonation (`B`) |

---

## Controls

Rebind all keys under **Options → Controls → Key Binds → YP Powers**:

| Default Key | Slot |
| --- | --- |
| `G` | Primary Ability |
| `H` | Secondary Ability |
| `V` | Tertiary Ability / Shadow Archer Morph Menu |
| `B` | Quaternary Ability / Shadow Archer Arrow Cycle |
| `R` | Alternate Ability / Power Toggle |
| `K` | Ice Emperor Form Switch |
| `N` | Boots of the Sky Feather Falling Toggle |
| `L` | Ice Emperor Heat Blast |

---

## Commands

### Operator Commands
```text
/yp power assign <player> <power-id>
/yp power clear <player>
/yp power check [player]
/yp power list
/yp energy set <player> <amount>
/yp energy get <player>
/yp cooldown clear <player>
/yp health enable | disable | status
/yp config reload
/yp web_slingers give <player>
/grace <minutes>        (use 0 to end early)
```

### Alliance Commands (All Players)
```text
/yp alliance add <player>
/yp alliance remove <player>
/yp alliance list
```

---

## Configuration

Configuration is saved cleanly as nested JSON at `config/yppowers.json`, organized into `general`, `energy`, `hud`, and `powers.<powerName>` sections. Edit and run `/yp config reload` in-game to apply changes immediately without restarting.

---

## Building from Source

Requires **JDK 25**:

```bash
./gradlew build
```

On Windows:
```powershell
.\gradlew.bat build
```

Run unit tests with:
```powershell
.\gradlew.bat test
```

The built mod JAR is output to `build/libs/yppowers-2.0.0.jar`.

---

## License

All Rights Reserved. See [LICENSE](LICENSE).
