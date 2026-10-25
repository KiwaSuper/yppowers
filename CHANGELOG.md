# Changelog

All notable changes to YP Powers are listed here.

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
