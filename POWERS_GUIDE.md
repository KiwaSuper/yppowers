# YP Powers — Powers Guide

**Target:** Minecraft Java 1.21.11 · Fabric · Java 21 · Version `1-16-8`

This guide describes the power kits present in the source tree. Exact costs, durations, radii, damage values, entity limits, and other tunable numbers are controlled by `config/yppowers_config.json` where a setting is exposed. The guide does not replace the config and command references.

## Controls at a glance

The shared default activation slots are **G (Primary), H (Secondary), V (Tertiary), B (Quaternary), and R (Alternate)**. Rebind them at **Options → Controls → Key Binds → YP Powers**. A power may leave slots unused or interpret them differently. Some abilities require a particular item or a separate interaction such as right-clicking a block. Do not assume a slot activates an ability when its prerequisite is not met.

- `/yp power assign <player> <power-id>` assigns a power; `/yp power clear <player>` removes it.
- `/yp power check [player]` checks assignments.
- `/yp alliance add|remove|list <player>` manages friendly relationships.
- `/grace <minutes>` enables Grace; `/grace 0` ends it early. Grace blocks supported ability activations and PvP according to the mod's enforcement paths.
- Most active abilities use shared Energy and cooldown handling. Passive toggles and certain traversal actions may be free. The in-game feedback and current config are authoritative for a particular ability.

## 1. Divinity Shield — `divinity`

**Role:** Defensive zone control and team support.

- **G — Divinity Shield:** Activates a timed spherical shield around the player. The dome pushes non-projectile entities away from its interior, deflects hostile projectiles and reflects part of eligible incoming attacker damage.
- **Ally support:** The active shield periodically heals the owner and allied players within its configured radius.
- **Visuals:** End-rod shell points, a moving electric equator, and a green perimeter indicate the shield. Ambient visuals are now throttled; entity/projectile checks and healing cadence remain unchanged.
- **Other protections:** The power includes shield-related equipment protection through the existing shared hooks. Check `divinity_*` values in the config for radius, duration, cooldown and healing tuning.

## 2. The GOAT — `goat`

**Role:** Enhanced mobility with a short high-output Overdrive.

- **Jump passive:** Enhanced jumps retain the goat-themed sound and use the configurable jump enhancement. Tracked powered jumps are eligible for dedicated fall protection; other falls use the separate configurable passive reduction.
- **Sprint passive:** Sprint acceleration begins at the configured starting multiplier (2× by default) and ramps toward the configured cap (5× by default). Acceleration has a reset delay and can be configured to persist through ordinary jumps. Once the acceleration cap is reached and the player is still moving at the corresponding speed, a restrained yellow dust wake appears behind them; it stops when sprinting or the required speed state ends.
- **R — Passive toggle:** Turns GOAT's passive enhanced-human kit on or off. When off, the bonus health, knockback resistance, managed jump enhancement, jump handling, fall protection, sprint acceleration, and the passive melee adjustment are disabled; turning it back on restores them. The state is saved across reconnects and server restarts. The toggle is free.
- **V — Overdrive:** Remains a separate active ability and can still be used while the passive kit is toggled off. It temporarily boosts combat and movement with its own Energy/cooldown behavior and is kept separate from the sprint modifier to avoid accidental multiplier stacking.
- **Tuning:** See `goat_*` settings for jump strength, fall protection, starting/maximum sprint multipliers, acceleration duration/rate, reset delay, Overdrive, and optional sprint particles/sound. Sprint speed still ramps only while sprinting; the toggle enables the sprint passive but does not grant permanent 5× movement speed.

## 3. Time Warper — `time_warper`

**Role:** Battlefield control, temporal repositioning, and clone support.

- **TIME form — G:** Time Stop freezes eligible entities for the configured duration. Allied players are excluded by the existing alliance check. New entities are tracked while the stop is active. The client receives the gray frozen-player visual state.
- **H — Rewind:** Rewinds online players to their recorded position/orientation from approximately 25 seconds earlier when sufficient history is available. It does not create a second copy of those players.
- **V — Black Hole:** Charges a black hole that pulls eligible entities and, when configured, processes terrain in a bounded per-tick budget before implosion. Particle emission is already throttled to selected visual frames.
- **REALITY form — G:** Summons an ordinary Time Clone using Time Shards and Energy.
- **B — Elite Clone:** Requires a physical Golden Time Shard and Energy.
- **R — Switch form:** Cycles TIME and REALITY, subject to its switch cooldown. The default switch cooldown is 10 minutes. Existing ability cooldowns and summons are not reset by switching.
- **Time Shards:** Ordinary shards are temporary touch pickups; ordinary count is stored separately. Rare Golden Shards are physical boss-drop items. Clone lifespans pause while stored during owner disconnects; the server persists their variant and remaining lifespan.
- **Clone appearance:** Normal clones use the summoner’s available Minecraft skin texture; elite clones use a gold-tinted version that retains the skin’s shading and transparency. Skin downloads/tinting are asynchronous and cached per client session, with Steve as the fallback. Clones remain zombie entities so their existing AI/combat model is preserved; their body geometry is therefore not an exact classic/slim player model.
- **Tuning:** Settings use the `time_warper_*` prefix. Clone counts, shard costs/drop chances, durations, cooldowns and Black Hole terrain budgets are configurable.

## 4. Teleport Gambit — `gambit`

**Role:** Gate placement and pocket-dimension capture.

- **G — Gateway:** Places a temporary gateway/pad with a limited lifespan and a guarded arrival/teleport path.
- **H — Domain:** Starts the multi-stage Domain ritual/charge, builds or prepares the destination platform, displays the gateway beam, then pulls the affected players into the pocket Domain.
- **Safety behavior:** Domain state uses server-side lifecycle management, with restore/return handling for owner/visitor death, reconnect, shutdown and unavailable destination worlds. The gate animation was thinned without changing its stage timing or transfer behavior.
- **Tuning:** See `teleport_gambit_*` settings and `THOR_CONFIGURATION.md` for the general configuration pattern; the Domain charge, pull, cooldown, and platform builder have dedicated settings.

## 5. Serious Punch — `punch`

**Role:** High-impact melee and destructive forward pressure.

- **G — Serious Punch:** Delivers a multi-direction forward punch/shockwave, damages eligible entities, applies control debuffs and can clear blocks along configured punch tunnels.
- **H — HELLDIVE:** Launches the user upward/backward, then performs the slam/crater sequence. It has a cooldown and guards against concurrent activations.
- **Momentum:** Consecutive bare-fist hits on valid living targets build capped Momentum. The stack count increases following successful hits, resets on target changes, and decays after its configured interval. The bonus only affects eligible vanilla unarmed melee hits; it is not an unlimited multiplier for power or environmental damage.
- **Tuning:** See `serious_punch_*` settings for punch range/damage, tunnel behavior, HELLDIVE and Momentum scaling.

## 6. Poseidon — `poseidon`

**Role:** Water mobility, sustain, trident utility, and a wave attack.

- **G / R — Trident mode interaction:** While using Poseidon's marked trident, the relevant slot toggles its enchantment mode. The item state persists through reconnects.
- **H — Tsunami:** Searches for a nearby body of water within its configured search range and launches a brief expanding particle-based water wall. The wave damages/stuns valid enemies, avoids allies and does not place permanent water blocks. A separate personal blessing grants its configured temporary effects.
- **Water passives:** Water Breathing, Dolphin's Grace, enhanced underwater regeneration, water combat benefits, configurable Haste/Resistance/Strength and hunger protection operate according to their water conditions. Hunger protection prevents depletion rather than refilling food or saturation.
- **Cleanup:** Poseidon tracks its managed effects so it can remove/restore them when the water condition or power assignment changes, instead of intentionally clearing unrelated effects.
- **Tuning:** See `poseidon_*` settings for water detection, effect levels, regeneration, tsunami search/radius/duration/cooldown and combat values.

## 7. Shadow Archer — `shadow_archer`

**Role:** Ranged combat, special arrows, and temporary mob morphs.

- **G / H / R — Morph slots:** Activates one of the configured morph slots. Morphs can change appearance, body sizing and selected form-specific properties; returning to human form is available through the morph UI/command path.
- **V — Morph menu:** Opens the Shadow Archer morph interface through the client-server request flow.
- **B — Arrow cycle:** Cycles through unlocked arrow variants. Split Shot is available by default; more variants unlock through the described item/mob progression.
- **Special arrows:** The kit includes variants such as Split Shot, Homing, TNT, Lightning, Ice Prison and Railshot. Railshot alone has a progressive draw/accuracy/speed/damage sequence; switching arrow types clears its accumulated charge.
- **Compatibility:** Morph camera/view updates are synchronized; the morph catalog and unlocked-arrow state are synchronized/persisted through the existing system.
- **Tuning and reference:** See `SHADOW_ARCHER_MORPH_CATALOG.md` and the `shadow_archer_*` config fields for the supported forms and arrow behaviors.

## 8. Boots of the Sky — `boots_sky`

**Role:** Limited-duration flight and targeted thunder attacks.

- **Flight:** Uses the marked Boots of the Sky item. The flight toggle is available through the power input and double-tap-jump pathway. Flight time is limited, has a recharge, and spends a small configurable Energy upkeep once per second while active.
- **Safety:** Flight ends if the marked boots are unequipped, the vanilla flying state is unexpectedly cancelled, the timer expires or Energy upkeep cannot be paid. Slow Falling is applied on supported shutdown paths.
- **N — Feather Falling toggle:** Independently enables or disables Feather Falling IV on the marked Boots of the Sky only. The setting persists through death, logout, reconnect and server restart. It does not toggle flight, double-jump, recharge or Slow Falling.
- **R — Raigo:** Locks a target, raises a visual orb into a thunderhead and performs the configured lightning strikes/impact effects. Gameplay damage and target validation remain server-side.
- **Tuning:** See `boots_flight_*`, `boots_raigo_*`, and related `boots_*` settings.

## 9. Stick of Deletion — `deletion`

**Role:** Knockback and collision punishment through the Stick of Deletion combat interaction.

- The power tracks eligible launched/knocked entities and applies collision-impact damage when they hit a surface, with a bounded tracking duration and cleanup for removed entities.
- The impact uses a short explosion visual/sound and does not need a permanent background task per target.
- Cooldown and knockback tuning are controlled by the existing `deletion_*` configuration and combat hooks.

## 10. Fire God — `fire_god`

**Role:** Fire immunity, hostile-mob pressure and personal summons.

- **G — Ghast Cannon:** Fires the existing fireball/cannon attack.
- **H — Blaze summon:** Calls an Ember Guard that follows/defends the owner, respects alliances and expires according to its configured lifespan. A per-owner active limit and cooldown apply.
- **V — Wither Skeleton summon:** Calls a Cinder Sentinel with its own active cap, cooldown, health and lifespan settings.
- **Passive fire resistance:** Extinguishes the owner when burning and maintains Fire Resistance. Status effect renewal is now periodic rather than reapplying a new instance every server tick.
- **Tuning:** See `fire_god_*` settings for summon limits, combat stats, cooldowns, lifespan and cannon behavior.

## 11. Ice Emperor — `ice_emperor`

**Role:** Area freeze, defensive/offensive form choice and Frost Guard support.

- **G — Absolute Zero:** Damages and slows nearby eligible enemies, freezes them and can form a temporary packed-ice prison.
- **H — Ice Meteor:** Calls down an ice meteor at a forward surface target; its impact damages/freezes opponents, creates a snowstorm and grants the configured caster buff.
- **V — Frost Guards:** Summons a configurable number of polar-bear bodyguards. Guards protect the owner/allies, with the special low-Energy fallback and configurable rogue chance documented in the changelog.
- **R — Power toggle:** Turns the Ice Emperor kit/passive field on or off.
- **L — Heat Blast:** Consumes all current Energy (minimum 10 by default), releases an expanding heat wave, sets valid targets on fire and applies Slowness. It starts a 60-second lockout for the Ice Emperor’s other active abilities. Allies are protected by the existing target rules. Radius, fire duration and Slowness settings are configurable.
- **K — Form cycle:** When forms are enabled, cycles Armoured, Stealth and Assassin forms. Armoured focuses on durability, Stealth on conditional invisibility, and Assassin on speed/damage with reduced maximum health. K is separately configurable.
- **Glacial Field:** Provides its existing ice trail/passive effects while enabled. Temporary trail blocks are tracked for restoration and do not intentionally replace protected blocks.
- **Tuning:** See `ICE_EMPEROR_FORMS.md` and the `ice_emperor_*` config fields.

## 12. Thor — `thor`

**Role:** Soulbound Mjölnir, lightning impact, and high-momentum launch combat.

- **G — Mjölnir:** Throws the marked soulbound mace; press G again while the hammer is deployed to blink to it and recall it. Impact can strike lightning, damage nearby enemies and create a configured crater. The return path can pass through ordinary blocks according to the existing implementation.
- **H — Ancient Rage:** Activates Thor's timed strength/combat buff.
- **R — Enchantment mode:** Cycles supported mace enchantment modes.
- **Storm Launch:** The existing guided charge/ready/launch system supports buffered launch input and chained launches while preserving its established timing, movement and Energy costs.
- **Heavy Core marker:** A protected marker identifies the deployed weapon location and follows the existing removal/recall/death cleanup rules.
- **Tuning:** See `THOR_CONFIGURATION.md` and `thor_*` settings.

## 13. Fallout Warrior — `fallout_warrior`

**Role:** Radioactive support aura and a poison-zone minion squad.

- **G — Radioactive Warriors:** Summons the three-role radioactive squad (Healer, Defender and Assassin) under the active-minion cap and ability cooldown rules.
- **H — Poison pool:** Creates a temporary toxic area that damages/debuffs valid enemies in range.
- **R — Radioactive Aura:** Toggles a no-Energy passive aura that heals allies and harms enemies at its configured interval/radius.
- **Ally and cleanup rules:** Aura/zone/minion logic uses owner and alliance checks; summons are tracked so expired/dead minions can be cleaned up.
- **Tuning:** See `fallout_warrior_*` configuration values.

## 14. Spidey — `spidey`

**Role:** Grappling movement, crowd control, temporary rage and aerial chained attacks.

- **G — Web Sling:** Launches the web traversal/grappling mechanic. Right-click with the marked Web Slingers item uses the same server-side mechanic.
- **H — Web Grenade:** Creates the configured slowing/webbing/poison cloud, excluding owner and allies.
- **V — Rage Mode:** Temporarily boosts movement and melee power. When it ends, applies the configured Heart Palpitations debuff.
- **B — Spider Dive:** Starts a chained aerial dive/kick sequence. Valid hits can rebound into another dive; the final hit is stronger. A chain window, miss recovery and cooldown limit repeated activation.
- **R — Automatic Cobweb MLG:** Toggles the fall-triggered automatic cobweb placement. Placement checks for suitable non-solid space and a safe base block; the ability does not consume ammunition.
- **Tuning:** See `spidey_*` settings.

## 15. Spirit Channeler — `spirit_channeler`

**Role:** Unlockable spirit summons and an uncontrollable high-risk ultimate.

- **G — Spirit summon:** Summons the selected unlocked spirit form. Unlocks persist through the existing player data system and are progressed through mob kills/ingredients.
- **H — Shadow Step:** Teleports toward a validated destination within the configured range.
- **V — Restless Shadows:** Summons an aggressive shadow entity/controller for its configured duration. It seeks valid targets and stops when defeated or expired.
- **B / R — Cycle spirit:** Cycles the selected unlocked spirit form before summoning.
- **Insufficient Energy:** The ultimate intentionally permits its low-Energy outcome: a red `ARE YOU SURE?` warning is visual only, followed by an uncontrollable shadow and one broad knockback event that can affect the caster/allies. This is intentionally hazardous rather than a failed activation.
- **Tuning:** See `spirit_channeler_*` configuration values.

## 16. Illusionist — `illusionist`

**Role:** Temporary spatial removal, telekinesis and a gravity-well ultimate.

- **G — Arm Spatial Erasure:** Arms the next main-hand right-click on a block. The selected 3×3×3 region is temporarily removed and later restored under the saved-block safeguards; entities in the restored area receive the configured damage/slowness.
- **H — Psychokinetic Grip:** Grabs a valid entity or item in range and holds it at the current distance.
- **V — Celestial Pull:** Creates a sphere-like cluster of falling blocks above a selected/validated center, pulls eligible non-player entities toward the gravity well, then removes the falling blocks at impact and deals its configured magic damage.
- **B — Throw Grip:** Throws the held target in the look direction and starts the release cooldown.
- **R — Increase grip distance:** When a target is held, increases the hold distance within its configured 2–10 block bounds. The current implementation uses R rather than mouse-wheel input.
- **Passive attraction:** Nearby item entities and XP orbs are pulled toward the owner on a periodic interval.
- **Tuning:** See `illusionist_*` settings.

## 17. Kitsune — `kitsune`

**Role:** Foxfire damage-over-time, transformation mobility, and burst/pounce attacks.

- **G — Claw Swipe:** Sweeping melee attack that knocks enemies back and applies Foxfire.
- **H — Foxfire Burst:** Launches a cluster of supernatural flame projectiles that can apply Foxfire.
- **V — Spirit Pounce:** Leaps toward a valid target, damaging and launching it on a successful hit.
- **B — Foxfire Detonation:** Processes valid targets currently afflicted with Foxfire once per activation, using a controlled detonation effect rather than unrestricted persistent TNT.
- **R — Giant Kitsune transformation:** Activates/deactivates the timed fast transformation, with Foxfire trails and configured movement speed. It uses the existing vanilla-fox-oriented appearance approach and restores player state through the documented lifecycle hooks.
- **Foxfire:** Deals configured damage over time; repeated applications follow the existing refresh rules. Owner/allies are protected by the power's target checks.
- **Tuning:** See `kitsune_*` settings.

## Shared technical behavior

- **Server authority:** Ability inputs request server-side validation; important targeting, damage, Energy use, cooldowns and world changes run on the server.
- **Alliances and Grace:** Power combat/activation paths use the existing alliance and Grace helpers where applicable. Grace is not a universal guarantee for every environmental or non-PvP world action; test any custom server interactions against the expected rules.
- **Persistence:** Power assignment, selected forms, Energy and progression data use the existing saved-state/config systems where designed to persist. Summons and temporary effects have individual cleanup rules.
- **Performance:** Frequent ambient shield particles, the short Domain beam, Time Warper rewind history and Fire God passive effect renewal were optimized in v1-16-3. Gameplay timers, hit checks and teleport stages are intentionally not slowed down by these visual/data changes.
- **Configuration:** Config is loaded from `config/yppowers_config.json`. Missing fields are expected to receive defaults. Use the names documented in `CONFIGURATION_AND_COMMANDS.md` and power-specific documents rather than guessing a property name.

## Version 1-16-3 notes

- Ambient Divinity Shield particle sampling is throttled and reduced; collision, healing and projectile checks remain on their prior tick cadence.
- Teleport Gambit's short beacon beam uses fewer particle updates; charge, pull and transfer timing remain unchanged.
- Time Warper rewind history now uses an array-backed FIFO, reducing per-snapshot allocations; offline-history sweeping runs once per second and does not affect rewinds of online targets.
- Fire God refreshes its passive Fire Resistance near expiry instead of constructing/reapplying an effect every tick.
- This is a development guide. See `BUILD_AND_TEST_STATUS.md` for the current compile/runtime verification status.

## Version 1-16-6 — ability and visual additions

- Boots of the Sky gains an independent, persistent N-key Feather Falling IV toggle for its marked boots.
- GOAT gains a low-rate yellow wake only after the configured sprint ramp reaches its maximum and the player is still moving fast enough.
- Ice Emperor gains L-key Heat Blast: all Energy is spent on activation, eligible targets are ignited/slowed, and other active Ice Emperor abilities are locked for 60 seconds.
- Time Clone textures now use the summoner’s skin, with a gold-tinted variant for elite clones and a Steve fallback. Zombie AI and body geometry remain unchanged.

## Version 1-16-5 correction and retained visual notes

- GOAT R toggles passives on/off again; the state persists and the toggle costs no Energy. V Overdrive remains a separate active ability.
- The bounded, event-driven visual accents added in v1-16-4 are retained.

## Version 1-16-4 visual and GOAT notes (historical)

- In v1-16-4 only, GOAT passives were forced on. Version 1-16-5 restores the R-key toggle and persisted enabled/disabled state.
- Major activation and impact visuals now receive additional bounded particle layers through the shared `EpicVfx` helper. These accents are event-driven rather than continuously spawned and do not alter combat or ability rules.
- The v1-16-4 visual accents remain applied to GOAT jumps/Overdrive, HELLDIVE, Thor hammer impact, Black Hole implosion, Kitsune attacks, Spidey Dive finisher, Spirit summons, Celestial Pull, Fallout summons and Ice Emperor meteor impacts.
