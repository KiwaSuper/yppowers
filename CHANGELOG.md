# Changelog

All notable changes to YP Powers are listed here.

[1.2.0] - 2026-10-31

Powers

Thor: Added a soulbound Mjölnir with Density V, Wind Burst II, Mending, and Unbreaking III. Hold and release right-click to Riptide, even outside water. Press G to throw it, striking real lightning, damaging nearby enemies, and optionally creating a crater. The hammer returns through blocks like a Loyalty trident. Press G again while deployed to teleport to it and recall it. The hammer is never lost on death.

Ice Emperor: Added Armoured, Stealth, and Assassin forms, Frost Guard summons, and Heat Blast. Frost Guards protect their owner and allies, with a weakened emergency summon available at low Energy.

Poseidon: Added Haste, Resistance, and Strength while in water, alongside hunger depletion protection. Reworked the tsunami into a moving wave with improved visuals.

The GOAT: Rebalanced jumping and sprint acceleration, added maximum-speed visual trails, and restored the R passive toggle.

Boots of the Sky: Added toggleable Feather Falling IV for the special boots and gradual Energy consumption during flight.

Time Warper: Optimized rewind history and improved Black Hole visuals.

Shadow Archer: Fixed Railshot piercing compatibility with Minecraft 1.21.11.

Time Clones: Added player-skin-based appearances and gold-tinted elite clones.

Multiple Powers: Added enhanced activation and impact visual effects, including particle bursts, spirals, lightning, and energy effects.

Added Kitsune, Spidey, Illusionist.

## [1.1.0] - 2026-10-22

### Powers
- **Thor:** a new power centered on a soulbound mace with Density 5, Wind Burst 2, Mending, and Unbreaking 3. Hold and release right-click to Riptide (works out of water), and press `G` to throw the mace. Where it lands it strikes lightning, digs a crater, and the mace flies back to you like a Loyalty trident, smashing through blocks as it returns. Press `G` again while the mace is still flying to teleport to it and call it back. The mace is never lost on death.

### Configuration
- New Thor settings: `thor_riptide_min_charge_ticks`, `thor_riptide_strength`, `thor_throw_speed`, `thor_crater_enabled`, `thor_crater_radius`, `thor_crater_depth`, `thor_impact_damage`, and `thor_impact_radius`.

## [1.0.0] - 2026-10-18

First public release, for Minecraft Java Edition 1.21.11 (Fabric).

### Powers
- **Divinity Shield:** a barrier dome that blocks enemies and projectiles, heals allies inside it, and reflects 30% of incoming damage. Comes with a shield you keep on death.
- **The GOAT:** every jump is boosted, sprint speed builds up to 2.2×, and you get +5 hearts, knockback resistance, and less fall damage. `R` turns it on or off.
- **Time Warper:** Time Stop freezes everything around you for 6 seconds. Time Warp sends every player back to where they were 25 seconds ago.
- **Teleport Gambit:** places a portal for 5 seconds that sends you 25–50 blocks forward to safe ground. Stand on it and it fires right away. A short lockout stops you bouncing straight back.
- **Serious Punch:** fires a three-way shockwave that tunnels 60 blocks, damages and freezes enemies, and gives you Strength III.
- **Poseidon:** strong buffs and Water Breathing in water, weaker on land. Switch your trident between Riptide and Loyalty + Channeling.
- **Shadow Archer:** five special arrow types that you unlock with materials, plus a searchable morph menu. You can turn into any vanilla mob you have killed, and each form has its own abilities and traits.
- **Boots of the Sky:** 5 minutes of survival flight with special Netherite Boots, and Slow Falling when your flight runs out.
- **Stick of Deletion:** launches enemies about 40 blocks, with extra damage if they hit a wall.
- **Fire God:** full fire and lava immunity, clear vision in lava, Nether mobs ignore you, Ghast fireball cannon, and an endless-fuel Blaze Rod.
- **Ice Emperor:** a frost aura that freezes water and slowly damages hostile mobs, an ice trail, and Absolute Zero (an area freeze). Weak to fire. `R` turns it on or off.

### Features
- Five rebindable keybinds (`G`, `H`, `V`, `B`, `R`).
- Operator commands to assign, clear, and check powers.
- An alliance system that protects allies from friendly fire and from your abilities.
- Every cooldown and key value can be changed in `config/yppowers_config.json`.
- Powers, on/off toggles, arrow unlocks, morph unlocks, and alliances are saved with the world.
