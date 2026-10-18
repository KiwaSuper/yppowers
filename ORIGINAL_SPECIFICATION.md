### 1. Divinity Shield

- **Activation:** Press `G` to open or close the barrier dome.
- **Visuals:** Features an `END_ROD` particle dome shell, a rotating equator ring of electric sparks, and a ground-level perimeter glow.
- **Barrier Physics:** Non-allied entities and projectiles are physically blocked and cannot pass into the dome. Pushing against the perimeter triggers defensive sparks and sound effects.
- **Healing:** Allies within the 12-block radius heal 0.5 HP (1/4 heart) every second with heart particles.
- **Defense:** Reflected damage deals 30% of incoming damage back to attackers. Holding a shield cannot be disabled by axe attacks.
- Notes: The player gets the shield on spawn, and the shield is removed from drops.

### 2. The GOAT

- **Super Leap:** Jumping while moving launches you 2.4x higher than normal, and you get a 2x jump boost forever (3s cooldown, plays goat jump sound).
- **Momentum Sprint:** Continuous sprinting progressively accelerates your speed up to 2.2x normal velocity. You get a 1.5x speed boost at all times.
- **Passive Attributes:** Grants +6 extra hearts (+10 HP max health bonus), 20% knockback resistance, and 60% fall damage reduction. Outgoing melee damage is reduced by 10% for balance.

### 3. Time Warper

- **Time Stop (`G`):** Freezes all non-allied entities, mobs, projectiles, gravity, and health regeneration in place for 6 seconds.
  - *Anti-Griefing:* Attacks dealt by the caster during Time Stop deal 50% damage to prevent instant unpreventable kills.
- **Time Warp (`H`):** Rewinds all players across all dimensions back to their exact coordinates, health, and view angles from 25 seconds ago. All other players receive Slowness II for 6 seconds following the rewind as well as Mining Fatigue.

### 4. Teleport Gambit

- **Activation:** Press `G` to place an active End Portal block for 5 seconds.
- **Teleport:** Stepping into the gateway searches for safe solid ground between 25 and 50 blocks in your look direction, and if there are none, it extends to the nearest safe block.
- **Safe Arrival:** Upon teleporting, grants 3 seconds of Slow Falling and 2 seconds of Resistance II.

### 5. Serious Punch

- **Activation:** Press `G` to unleash a concentrated kinetic shockwave forward in a fan shape.
- **Visuals:** It uses the warden sonic attack particle.
- **Tunnel Destruction:** Obliterates blocks in a 3x3 tunnel up to 60 blocks forward (drops cleanly suppressed to avoid lag).
- **Shockwave Impact:** Entities within a 25-block sphere take 13.0 damage (6.5 hearts), receive Weakness II, and are frozen in place for 1.5 seconds.
- **Caster Buff:** The user receives Strength III for 10 seconds.

### 6. Poseidon

- **Aquatic Power:** In water, gain Dolphin's Grace I (2.0x swim speed), Regeneration I, and +20% melee/trident damage.
- **Land Weakness:** Suffers a -10% movement speed penalty while on dry land as well as Weakness.
- **Trident Enchantment Swap (`G` or `R`):** Instantly toggles held trident between **Riptide III** and **Loyalty III + Channeling** (3s cooldown).
- Notes: The player gets the trident on spawn, and the shield is removed from drops.

### 7. Shadow Archer

- **Arrow Unlocks:** Craft special arrow batches (8 arrows + thematic item) to unlock arrow types.
- **Arrow Selection (`B`):** Cycle or select between:
  - *Split Shot:* Fires 3 arrows in a cone (each deals 0.75x base damage).
  - *Homing Missile:* Seeks targets within 35 blocks.
  - *TNT Arrow:* 2.0F explosion on impact.
  - *Lightning Arrow:* Calls down 2 lightning strikes on the hit target.
  - *Ice Prison:* Inflicts 4 seconds of Slowness II and Blindness.
- **Morphing (`V`):** Opens a GUI where you can open a menu with a search bar and a button. When you enter a mob’s name and press the morph button, the game checks if the user has killed the mob before morphing (10s cooldown).

### 8. Boots of the Sky

- **Flight:** Wearing Netherite Boots with this power grants creative flight in survival mode (5 minutes total flight capacity).
- **Toggle:** Press `G` or double-tap jump to toggle flight on or off.
- **Recharge & Safety:** Requires 3 minutes to recharge when depleted. Automatically applies 4 seconds of Slow Falling whenever flight is toggled off or expires mid-air.
- Notes: The player gets Netherite Boots with Feather Falling IV and the boots are removed from drops.

### 9. Stick of Deletion

- **Attack:** Attacking any living entity with a standard Stick applies 12.0F knockback (\~40 blocks launch distance).
- **Wall Collision:** If the target strikes a solid wall or obstacle, they take 8.0F (4 hearts) bonus impact damage.
- **Cooldown:** 4 seconds between deletion strikes with hotbar item sweep.

### 10. Fire God

- **Passives:** 100% immune to fire, lava, and magma blocks. Lava fog is removed for crystal-clear underwater-style lava vision. Nether-native mobs (Blazes, Ghasts, Piglins, Magma Cubes, Wither Skeletons) ignore the player.
- **Ember of the Undying Flame:** Soulbound Blaze Rod providing infinite furnace burn time (usable only by the power holder).
- **Ghast Cannon (`G`):** Launches an explosive Ghast fireball directly forward (12s cooldown).

### 11. Ice Emperor

- **Glacial Aura (Passive):** Creates a 4-block radius aura around the player that freezes water into Frosted Ice, extinguishes fire, inflicts Slowness I on aggressive entities, and deals 1.0 freeze-type damage every 2 seconds to hostiles that remain in the field. Immune to freezing damage and powder snow.
- **Absolute Zero (`G`):** Emits a radial wave of frost 10 blocks outward. Enemies caught inside take 4.0 damage (2.0 hearts), are encased in a temporary ice prison for 2.5 seconds, and receive Slowness IV for 4 seconds (30s cooldown).
- **Ice Spike Trail:** Sprinting leaves a short-lived path of packed ice beneath your feet, boosting movement speed on terrain and granting +20% damage bonus while standing on ice.
- **Thermal Flaw:** Takes +30% extra damage from fire/lava sources and suffers Weakness I when in Nether environments.

---

## ⌨️ Controls & Keybindings

Rebindable in **Options → Controls → Key Binds → YP Powers**:

| Key     | Function                                                                                                                              |
| ------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **`G`** | **Primary Ability** (Divinity Shield / Serious Punch / Teleport Gateway / Boots Flight / Ghast Cannon / Trident Swap / Absolute Zero) |
| **`H`** | **Time Warp** (Rewind all players 25s into the past)                                                                                  |
| **`V`** | **Archer Form Shift** (Cycle Human / Wolf / Bat)                                                                                      |
| **`B`** | **Archer Arrow Menu** (Cycle special arrow types)                                                                                     |
| **`R`** | **Trident Quick Swap** (Alternate key for Poseidon)                                                                                   |

---

## 💬 Commands

### Power Administration (Operators Only)
```bash
/yp power assign <player> <power>
/yp power clear <player>
/yp power check [player]
```

*Valid Power IDs:* `divinity`, `goat`, `time_warper`, `gambit`, `punch`, `poseidon`, `shadow_archer`, `boots_sky`, `deletion`, `fire_god`, `ice_emperor`

### Alliance System (All Players)

Allies share healing auras and are protected from friendly-fire, time stop freeze, absolute zero freeze, and knockback:
```bash
/yp alliance add <player>
/yp alliance remove <player>
/yp alliance list
```

---

## ⚙️ Configuration Reference (`yppowers_config.json`)

Located at `config/yppowers_config.json`:

- `divinity_heal_radius`: Radius of shield healing and barrier (default: `12`)
- `divinity_heal_amount`: Health healed per second (default: `0.5`)
- `divinity_forcefield_duration_ticks`: Shield duration in ticks (default: `300` / 15s)
- `divinity_forcefield_cooldown_ticks`: Shield cooldown in ticks (default: `600` / 30s)
- `goat_jump_multiplier`: Vertical jump scaling (default: `2.4`)
- `goat_max_speed`: Maximum sprint speed cap (default: `2.2`)
- `time_stop_max_duration_ticks`: Time stop duration (default: `120` / 6s)
- `time_stop_cooldown_ticks`: Time stop cooldown (default: `600` / 30s)
- `punch_raycast_range`: Trench destruction distance (default: `60`)
- `boots_flight_duration_ticks`: Maximum flight time (default: `6000` / 5m)
- `boots_recharge_duration_ticks`: Flight recharge cooldown (default: `3600` / 3m)
- `ice_emperor_aura_radius`: Radius of frost aura (default: `4`)
- `ice_emperor_freeze_duration_ticks`: Absolute Zero freeze duration (default: `50` / 2.5s)
- `ice_emperor_cooldown_ticks`: Absolute Zero cooldown (default: `600` / 30s)
