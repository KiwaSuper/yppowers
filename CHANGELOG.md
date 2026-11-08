# Changelog

**Current source version:** `1.4.1` · Minecraft Java 1.21.11 · Fabric · Java 21

## 1.4.1 — Keep Thor lightning-on-hit and lightning immunity

- **THOR:** Restored melee lightning-on-hit (`thor_mace_lightning_on_hit`, skips allies) and full lightning damage immunity (active even during Grace), alongside hold-right-click Riptide III anywhere, Speed II, and the Unbreakable soulbound mace.

## 1.4.0 — Sync 1-16-16 updates + Restore Thor Riptide & Speed II

- **THOR:** Restored hold-and-release right-click Riptide III anywhere (`UseAction.TRIDENT` charging animation + Riptide III launch sound, no cooldown, 0 Energy) and permanent Speed II; soulbound Unbreakable mace uses fixed **Density 5, Wind Burst 1, Fire Aspect 2, Curse of Vanishing, Mending, Unbreaking 3**.
- **SYNC (`1-16-9` through `1-16-16`):** Includes Celestial Pull cinematic moon, Shadow Archer morph hitbox/reach/climbing/flight fixes, Kitsune POV & ranged block placement, Spidey web line & MLG fix, Fallout Vindicator Assassin & aura rings, Spirit Channeler Restless Shadows upgrades, charged Black Hole without item drops, and `EpicVfx` cinematic activations.

## 1-16-16 — Massive, cinematic Celestial Pull moon

- **CHANGE — Bigger, more massive moon:** the effective radius is now `illusionist_celestial_moon_radius` x the new `illusionist_celestial_moon_scale` (default 1.6, so about 8 blocks instead of 5, capped at 14). It hovers higher for larger sizes (never closer than 2.8 radii above the target) and the crater radius grows with it.
- **NEW — Smooth, cinematic staging:** the moon assembles in a rising three-turn spiral over about 1.2 seconds (in small per-tick batches), then hangs with a rotating corona of light that brightens toward the strike, a ground shadow that tightens on the target, gravity tendrils spiralling up from the ground, and a rising sonic charge. In the last 14 ticks a ring of fire sweeps down from the moon to the impact point. On impact the moon disintegrates into falling block debris (removed in larger batches) while the existing staged crater blast plays.
- **PERFORMANCE:** `illusionist_celestial_moon_max_blocks` (default 480) caps the moon's block entities; larger moons are thinned evenly along the spiral so a massive moon does not cost more than the budget. Per-tick particle counts stay bounded.
- Added crying obsidian accents to the moon's block mix.

## 1-16-15 — Shadow Archer morphs: weapons, blocks, inherent abilities and view

- **FIX — Weapons, mining and block placement while morphed.** The disguise entity surrounds the player, so the vanilla crosshair targeted it instead of the world, and its large hitbox also blocked block placement on the server. The server-side disguise now has a zero-volume hitbox (its model and camera dimensions are unchanged), the client re-aims a crosshair that lands on the disguise at the real block or entity every tick, and server callbacks re-aim any attack or use that still reaches the disguise.
- **NEW — Tall forms reach further:** temporary interaction-range modifiers (+0.8 block per block of height above a player, up to +5) so large forms can still reach the ground; removed when the morph ends and never saved.
- **FIX — Spider/cave spider wall climbing:** the old server-side push depended on a collision flag that is rarely set for client-driven players. Climbing now uses client ladder-style physics (forward or jump climbs, otherwise the spider clings) while the server cancels fall damage when clinging. Hugging a wall also keeps vanilla from treating the climb as flying.
- **FIX — Flight:** flying forms get per-mob flight speed (dragon fastest, ghast slowest), and flight is revoked again when switching from a flying form to a grounded one instead of lingering until the morph ended. Original flight state and speed are restored when the morph ends.
- **CHANGE — Strider/lava forms float on lava** (sneak to dive) in addition to fire resistance and speed.
- **CHANGE — Camera:** morph eye height is clamped to 0.45–2.6 blocks so giants do not put the camera out of reach of the ground and tiny forms do not sit inside the floor.

## 1-16-14 — Thor trim, Celestial Pull overhaul, creepier Restless Shadows, Kitsune builder, optimization

- **REMOVED — Thor Hammer Throw and Ancient Rage.** The mace right-click launch (flight) is untouched. G only recalls a hammer that was already thrown before the update.
- **CHANGE — Time Shards are Time Warper only.** Ordinary and Golden shards can only be collected by touch by the Time Warper. Golden shards no longer vanilla-pickup; they go straight into the Time Warper's inventory.
- **CHANGE — Celestial Pull (Illusionist):** the moon is now a large hollow shell (`illusionist_celestial_moon_radius`, default 5) hovering high above the target and assembled in batches; it only replaces air and never deletes terrain. Every non-allied entity and item within `illusionist_celestial_weightless_radius` (default 48) is weightless while it is active; gravity is always restored (including after disconnects, chunk unloads and server stops, via a retry list). Captured mobs hang beneath the moon and are slammed down at the end. Damage is now `max(pull_damage, illusionist_celestial_crater_damage)` (new default 45) over a larger well (`illusionist_celestial_crater_radius`, default 16).
- **FIX — Moon crater desync/lag:** the moon blocks no longer fall with gravity (which made the client and server disagree when they landed); they hover and are removed in batches of 40, and the crater blast plays out over 16 ticks with bounded particle counts instead of one packet spike. The pull duration is capped at 560 ticks so moon blocks cannot hit the falling-block despawn timer.
- **CHANGE — Restless Shadows (Spirit Channeler):** health is configurable (`spirit_restless_shadows_health`, default 40, previously fixed), its melee damage now uses `spirit_restless_shadows_damage`, it plays a slow heartbeat, vex whispers and warden growls, leaves sculk-soul/ink wisps, and applies Darkness to nearby enemy players (`spirit_restless_shadows_darkness_radius`/`_ticks`). Manifesting plays an elder-guardian curse.
- **NEW — Giant Kitsune can place blocks:** while transformed, right-clicking with a block in hand places it on the surface you are looking at up to `kitsune_fox_place_range` (default 16) away, through vanilla block interaction so placement rules, sounds and item use still apply.
- **OPTIMIZATION:** Shadow Archer arrow tracking and cleanup no longer scan every loaded entity each tick (type-filtered lookups near the archer; UUID lookups for cleanup). Time shard ticking and collection are skipped entirely when no shards exist and use UUID lookups otherwise. Time Stop checks for late-spawned entities every 2 ticks. Fallout rings and shockwaves use slightly lower particle budgets with the same look. Black Hole and Celestial Pull effects are spread over several ticks.
- **BUG CHECK:** the full source tree was parsed with the JDK compiler (no syntax errors; no undefined local variables or missing project methods), all JSON resources were validated, every configuration key used in code is defined, and every mixin entry has a source file. Minecraft API calls could not be compiled here.

## 1-16-13 — Charged Black Hole and cinematic VFX

- **NEW — Black Hole item charge:** items it swallows (stacks that reach the core) are counted, destroyed blocks count fractionally, and the total is shown on the action bar with the current power multiplier. More absorbed items grow the sphere, add a spinning accretion disk and polar jets, and scale the final explosion.
- **NEW — Charged final explosion:** after the implosion a delayed blast adds radius, extra outward damage (capped by `time_warper_black_hole_max_blast_damage`) and knockback, an expanding multi-ring shockwave and dome, an explosion emitter cluster, sonic boom, thunder and (at 2x power or more) cosmetic lightning. Allies and the caster's own minions are unaffected. Mob drops from kills are still deleted. New config: `time_warper_black_hole_items_for_max_power`, `time_warper_black_hole_max_power_multiplier`, `time_warper_black_hole_block_item_value`, `time_warper_black_hole_max_blast_damage`.
- **NEW — Cinematic VFX toolkit:** the shared visual helper gained expanding shockwaves, converging rings, spherical bursts, rising helixes, light pillars, beams and flashes (built on velocity particles so a single packet keeps moving on the client). Existing `impact` and `activation` effects were upgraded, so every power that already used them got richer visuals.
- **NEW — Cinematic activations added** for Thor (Riptide, Ancient Rage, recall), Fire God (summons, Ghast Cannon), Ice Emperor (form switch, guards, Absolute Zero), Poseidon (Tsunami), Shadow Archer (all morph abilities, Ice Prison), Teleport Gambit (gateway, transfer, Domain), Stick of Deletion, Boots of the Sky (Raigo strike), Illusionist (Spatial Erasure, Celestial Pull), Spirit Channeler (Shadow Step, uncontrolled collapse), Spidey (Web Grenade, Rage), Kitsune (Foxfire Burst), Serious Punch tunnel, and Time Warper (Time Stop, clones, rewind teleport).

## 1-16-12 — Kitsune camera and Black Hole drops

- **FIX — Kitsune POV:** replaced the 1-16-11 size-scale workaround. The transformation now uses the same camera sync as Shadow Archer morphs: the fox avatar is hidden in your own first-person view (it no longer blocks the screen), the camera uses the fox's eye height (never below 1.62), and the third-person camera distance scales with the fox. Your hitbox is no longer changed.
- **CHANGE — Black Hole no longer produces item drops:** blocks it destroys are removed without drops, and mobs killed by the implosion drop no items. Experience and player inventories are unchanged.

## 1-16-11 — Kitsune POV and build JDK pin

- **FIX — Kitsune POV:** the first-person camera sat inside the 3x fox's head and the model blocked the view. During the transformation the player now gets a temporary (never saved) scale modifier, about 1.4x with the default multiplier, so the eyes sit above the fox's head. It is removed when the transformation ends, on death, on disconnect and on power removal, and it is skipped (with a message) when there is no headroom.
- **BUILD:** added `gradle/gradle-daemon-jvm.properties` pinning the Gradle daemon to JDK 21. A JDK 26 default caused "Unsupported class file major version 70". JDK 21 must be installed.

## 1-16-10 — Vindicator Assassin, visible Fallout radius and Foxfire, cross-power minion fixes

- **CHANGE — Fallout Assassin is now a Vindicator** with an iron axe and its native melee AI. Healer and Defender remain pillagers.
- **NEW — Warden-style emergence:** the squad spawns buried and rises out of the ground over 3 seconds (block-crumble and sculk-soul particles, Warden emerge sound) while invulnerable with AI paused. Entities cannot use the Warden's real emerge pose, so this is a rise-from-the-ground effect.
- **NEW — Visible Fallout radius:** the Radioactive Aura edge is drawn as a ring of green sparkles; the Toxic Pool edge is drawn as a ring too.
- **NEW — Visible Foxfire:** afflicted targets are wrapped in blue soul-fire flames every tick, with soul wisps and a Glowing outline (removed when Foxfire ends or detonates). Foxfire never applies to the owner's own minions.
- **FIX — Minions fighting each other:** a mixin now stops minions of the same owner (or allied owners) from targeting each other. Previously an Iron Golem spirit could target Fallout squad members.
- **FIX — Spirit Channeler:** spirits and the Restless Shadow no longer attack the owner's other minions or passive animals; they prefer whoever hit the owner, then the owner's victim, then the nearest hostile. Targeting runs every 5 ticks (was 10) and spirits teleport to the owner if left far behind.
- **FIX — Illusionist:** Psychokinetic Grip ignores allied players; Spatial Erasure and Celestial Pull no longer affect the owner's or allies' minions; passive item pull runs every 2 ticks (was 10) so it is smooth; Celestial Pull's final damage no longer scans every loaded entity.
- **FIX — Kitsune:** Foxfire ticking and Detonation look targets up directly instead of scanning every loaded entity; Claw, Burst, Pounce and Detonation skip the owner's own minions and fox avatar.
- **FIX — Spidey:** the web sling now draws a visible web line to its anchor.
- **FIX — HELLDIVE:** the impact no longer damages the owner's own minions.

## 1-16-9 — Movement sync, Spidey, Fallout Warrior and Kitsune fixes

- **FIX — Launch/knockback not applying (Spidey web sling and Spider Dive, HELLDIVE, Kitsune Pounce/Claw, Illusionist, Spirit Channeler):** these powers flagged velocity with `velocityDirty`, which does not send the new velocity to the client in 1.21.11. They now use the same `scheduleVelocityUpdate` hook as GOAT, Thor and the other working powers.
- **FIX — Spidey automatic Cobweb MLG:** the old check read the player's server-side velocity (unreliable for client-driven players) and only looked one block down. It now measures fall speed from Y, runs every tick (was every 3), looks down through the player's column and places the cobweb on top of the first solid block. No cobweb is placed above water/lava. Landing cobwebs are removed after 4 seconds.
- **NEW — Spidey Web Slingers:** given automatically when Spidey is assigned, on join and on respawn if missing; kept on death.
- **FIX — Fallout Warrior squad:** the aura and Toxic Pool no longer damage the owner's own minions or allies (this was killing the squad). Pillagers only attack with a crossbow, so all three roles now carry one; Defender and Assassin also strike in melee. Minions retarget every 5 ticks (was 10) and prefer whoever hit the owner, then the owner's target, then the nearest hostile. The Healer now heals the owner 1 HP per second (was per 2 seconds) with heart particles. Squad arrows can no longer hit the owner, allies or other squad members, and the squad teleports to the owner if left far behind.
- **FIX — Kitsune Transformation:** the fox avatar's AI is disabled and it is positioned slightly ahead of the player's motion each tick, so it no longer trails behind.
- `spidey_mlg_search_radius` is no longer used (the cobweb is placed in the player's own column).

## 1-16-8 — Shadow Archer compile fixes

- **MODIFICATION — Railshot pierce level:** `PersistentProjectileEntity.setPierceLevel` is not accessible from `ArrowEntity` in Minecraft 1.21.11, so Railshot now calls it through a new `PersistentProjectilePierceInvoker` mixin (registered in `yppowers.mixins.json`). Behavior is unchanged.
- **MODIFICATION — Railshot spark particles:** `getEntityWorld()` returns a plain `World`; the call is now cast to `ServerWorld` before `spawnParticles`, matching the rest of the file.
- No other source changes. These resolve the two remaining `ShadowArcherPower.java` diagnostics from 1-16-7; a full Gradle build was not run here.

## 1-16-7 — Minecraft 1.21.11 compile compatibility corrections

- **MODIFICATION — Entity velocity sync:** Updated the renamed public velocity-dirty flag usages in Spidey, Kitsune, Illusionist, Spirit Channeler and Serious Punch to the Yarn 1.21.11 API.
- **MODIFICATION — Entity package mappings:** Corrected the Wolf and Iron Golem imports to `net.minecraft.entity.passive`.
- **MODIFICATION — Ice Emperor/network references:** Added the missing networking imports, corrected the player position accessor, and added the missing Boots of the Sky power import used by the keybind receiver.
- **MODIFICATION — World/particle API compatibility:** Routed the Time Clone portal particle through `ServerWorld.spawnParticles` and used the required dragon-breath particle effect parameter for Kitsune visuals.
- **MODIFICATION — Registry and NBT API compatibility:** Used registry-entry sound values for the three reported explosion sound calls, `NbtList.getCompoundOrEmpty` for stored clone records, and copy constructors for saved status-effect instances.
- **Build report review:** The uploaded v1-16-6 Gradle report reached `:compileJava` and reports 39 compiler errors. This pass addresses the non-Shadow-Archer API/import diagnostics. The two diagnostics in `ShadowArcherPower.java` remain unresolved; no changes were made to that power's projectile behavior. This archive is therefore a partial compile-correction pass, not a confirmed successful build.

## 1-16-6 — Boots Toggle, Heat Blast, GOAT Trail & Time Clone Skins

- **MODIFICATION — Boots of the Sky Feather Falling:** Added a dedicated configurable N-default keybind that toggles Feather Falling IV only on the marked Boots of the Sky. The state is stored in `PowerManager` persistent data and reapplied on assignment, join and respawn; it does not alter flight, double-jump, recharge or Slow Falling.
- **VISUAL IMPROVEMENT — GOAT maximum-speed trail:** Added a restrained yellow dust/cloud wake behind the player only after the configured sprint acceleration reaches its cap and horizontal movement remains above a speed threshold. Emission is limited to every other tick with a small particle count. Movement values and acceleration settings are unchanged.
- **NEW ABILITY — Ice Emperor Heat Blast:** Added a configurable L-default keybind. Activation requires at least the configured minimum Energy (10 by default), consumes the entire remaining reserve, then applies a persistent 60-second lockout to the Ice Emperor's other active abilities. A 12-tick expanding orange/red/white heat wave ignites and slows each valid target once; allies, Grace and owner-minion protection use existing target checks. Radius, fire duration and Slowness settings are configurable.
- **VISUAL IMPROVEMENT — Time Clone skins:** Normal clones now resolve the summoner's player skin; elite clones use an asynchronously generated gold-tinted copy. Per-client caches are cleared on disconnect, stale asynchronous completions are discarded, and Steve is used when the original skin cannot be retrieved. Clone AI, entity type, health, damage and lifetime rules are unchanged. Their zombie model geometry is retained, so classic/slim player body geometry is not reproduced exactly.
- **Configuration:** Raised config schema version to 11; missing Heat Blast values are filled from defaults and existing configured values are retained.
- **Verification:** Static source/resource checks and ZIP integrity are reported separately from Gradle compilation and live gameplay tests. Gradle could not start because the wrapper could not resolve `services.gradle.org` to download Gradle 9.2.1.

## 1-16-5 — Restore GOAT's R-key passive toggle

- **R toggle restored:** R now switches GOAT's passive enhanced-human kit on/off again. The enabled state is stored through the existing `PowerManager` persistence, so it survives reconnects and server restarts; assigning GOAT anew starts the kit enabled.
- **Disabled-state cleanup:** Turning passives off removes the GOAT-owned bonus health, knockback resistance, jump enhancement, sprint acceleration, jump-fall protection tracking, and passive fall-damage/melee modifiers. Re-enabling reapplies the managed passive kit without free healing or duplicate attribute modifiers.
- **Overdrive separation:** V Overdrive remains a separate active ability. The GOAT passive toggle remains free and recalculates Overdrive's speed offset if passives are switched during Overdrive.
- **Visuals preserved:** All v1-16-4 bounded, event-driven `EpicVfx` additions remain in place; this correction does not intentionally change their effects.
- **Version:** `mod_version=1-16-5`; expected JAR: `build/libs/yppowers-1-16-5.jar`.

## 1-16-4 — Visual Effects and Intrinsic GOAT Passives

- **GOAT identity:** GOAT's health, knockback-resistance, Jump Boost, jump handling, fall-protection rules and sprint progression are now intrinsic while the player is assigned GOAT. R no longer disables the passive kit; it displays a brief explanation instead. The shared power-enabled state treats GOAT as always enabled, preventing command or stale persisted toggle state from disabling its passives. Removing or changing the power still removes its managed modifiers and effects.
- **GOAT movement preserved:** Progressive sprinting remains conditional on sprinting and its existing configuration. Jump strength, sprint multipliers, acceleration timing, fall-protection behavior, Overdrive, cooldowns and Energy behavior are unchanged.
- **Epic VFX accents:** Added bounded, one-shot layered particle accents (end rods, electric sparks, reverse portal, enchantment and cloud particles) to major activation/impact moments across GOAT, Serious Punch HELLDIVE, Thor hammer impact, Time Warper Black Hole, Kitsune attacks, Spidey Dive finisher, Spirit Channeler summon, Illusionist Celestial Pull, Fallout Warrior summon and Ice Emperor meteor impact. No damage, hitbox, range, cooldown, duration, Energy or entity behavior was changed by these visual additions.
- **Performance guardrail:** VFX accents are emitted only at activation/impact events, not as new per-tick loops. Particle scale is bounded in the shared `EpicVfx` helper to prevent accidental unbounded particle counts.
- **Verification:** Source-level syntax/structure, JSON, configuration-reference, registration, documentation and ZIP checks are reported separately from Gradle compilation and gameplay testing.

## 1-16-3 — Stability and performance pass

- **Time Warper rewind history:** Replaced per-player `LinkedList` history queues with pre-sized `ArrayDeque` queues to remove a linked-list node allocation for every recorded snapshot. Offline history cleanup now runs once per second; rewind still acts only on online players and continues recording the same 25-second window.
- **Divinity Shield visuals:** Reduced dome/perimeter particle sampling and emits those ambient visuals every other tick. Healing pulses, entity pushes, projectile deflection, damage reflection, shield duration and cooldown behavior remain unchanged.
- **Teleport Gambit beam:** Reduced beam-column/ring samples and emits beam animation frames every other tick. The charge, beacon, pull and dimension-transfer phase timings remain unchanged.
- **Fire God passive:** Fire Resistance refresh now occurs near effect expiry instead of creating/reapplying a potion-effect instance every tick. Existing fire immunity and summon cooldown/lifespan handling remain unchanged.
- **Server tick dispatch:** Reuses the same `PowerManager` instance for grace and cooldown countdowns and assigned-player dispatch within the server tick.
- **Documentation:** Added a detailed `POWERS_GUIDE.md` for all 17 registered power IDs and their default controls; trimmed `README.md` to project overview, build steps and documentation links.
- **Verification:** Static syntax/resource/call-site checks and ZIP integrity checks are performed separately from Gradle compilation and in-game testing. The Gradle build is still subject to dependency/network availability.

## 1-16-2 — Poseidon Water Buffs and GOAT Balance

- **Poseidon water buffs:** Added configurable Haste, Resistance, and Strength while the configured water condition is satisfied. Defaults are effect amplifier 0 (level I); effects are managed with leases so unrelated stronger/longer effects are not blindly removed.
- **Hunger protection:** Added a server-side `HungerManager` hook that skips hunger depletion while an enabled Poseidon is in its configured water state. It preserves current food and saturation instead of applying Saturation or refilling the bar, and clears accumulated exhaustion to prevent a delayed drain on exit.
- **Preserved Poseidon kit:** Kept water breathing, Dolphin's Grace, water regeneration, the underwater combat bonus, trident modes, and tsunami. Cleanup now removes only Poseidon-managed effects/modifiers rather than unconditionally deleting every Resistance/Regeneration/Weakness-style effect.
- **GOAT jump:** Reduced default extra jump multiplier from 2.2 to 1.25 and changed the managed infinite Jump Boost from II to I. The existing goat long-jump sound remains. No dedicated jump cooldown existed in this source revision, so none was added.
- **GOAT fall protection:** Tracks powered jumps individually and defaults to no fall damage only for those tracked jumps. The existing 60% passive reduction for other falls remains available as a separate configurable setting.
- **GOAT sprint:** Replaced the old 1.5x-to-2.2x ramp with a 2.0x starting multiplier that accelerates to a 5.0x cap over 100 continuous sprint ticks. Added configurable reset delay, airborne momentum behavior, passive toggle, and optional foot particles/start sound. Stable attribute IDs prevent speed stacking; Overdrive still targets its configured total multiplier rather than stacking on sprint speed.
- **Configuration migration:** Config version 9 adds all new values and migrates the old shipped `2.2` GOAT defaults to the new values without rewriting non-default custom values.
- **Build status:** Gradle compilation remains unverified because this environment cannot resolve `services.gradle.org` to download Gradle 9.2.1. Static syntax, config and ZIP checks are reported separately from gameplay testing.

## 1-16-1 — Ice Emperor Forms

- Added Armoured, Stealth, and Assassin forms to the existing Ice Emperor power.
- Added server-validated form cycling on a dedicated, configurable K-default keybind; existing G/H/V/B/R bindings are unchanged.
- Added Resistance III, optional armoured mobility/offense penalties, conditional stealth detection, and assassin speed/melee/health modifiers.
- Added configurable form enable switches, default form, persistence settings, particle/sound/action-bar settings, and numeric safety validation.
- Persisted selected form by UUID in `PowerManager`, and added client form-state synchronization.
- Restricted Assassin damage scaling to vanilla melee attacks using a scoped tracker, so scripted power damage that reuses `PLAYER_ATTACK` is not unintentionally amplified.
- Cleaned up form attribute modifiers, form-owned invisibility, and managed Resistance during switching, death, respawn, disconnect, dimension changes, grace, power disable, and reassignment.
- Build status: not verified; the wrapper could not download Gradle 9.2.1 because `services.gradle.org` did not resolve in the build environment.

## 1-15-5 — Low-Energy Frost Guard gamble

- **Emergency summon:** Ice Emperor can press V to summon Frost Guards even when Energy is below the normal tertiary ability cost. The remaining Energy is consumed, but this special fallback does not initiate Bleeding. An already active Bleeding lockout and Grace still block the ability.
- **Weakened bears:** Guards summoned through the low-Energy fallback have movement speed multiplied by `ice_emperor_guard_low_energy_speed_multiplier` (default `0.60`) and attack damage multiplied by `ice_emperor_guard_low_energy_damage_multiplier` (default `0.50`).
- **Rogue chance:** Each bear in a low-Energy summon independently has a configurable 10% default chance to become a Rogue Frost Guard, immediately targeting and attacking its summoner. The owner-damage safeguard is bypassed only for that marked rogue; rogue guards still ignore allied players. Normal, fully paid summons are never made hostile by this chance.
- **Configuration:** Added the speed, attack-damage, and hostile-chance settings and their validation. Updated the specification, README, configuration guide, build status, and Frost Guard regression checks.
- **Version:** `mod_version=1-15-5`; expected JAR: `build/libs/yppowers-1-15-5.jar`.

## 1-15-4 — Thor blink-recall teleport compile fix

- **Compile fix:** Updated Thor’s G-key blink-to-hammer call to use the Minecraft 1.21.11 `ServerPlayerEntity.teleport` overload with destination world, coordinates, position flags, yaw, pitch and camera-reset flag. This resolves the reported `ServerWorld cannot be converted to double` compiler error.
- **Behavior:** Keeps the existing intent: teleport to the deployed hammer, then recall it.
- **Version:** `mod_version=1-15-4`; expected JAR: `build/libs/yppowers-1-15-4.jar`.

## 1-15-3 — Ice Emperor's Frost Guard and free passive toggles

- **Ice Emperor (V):** Added a configurable summon ability for persistent polar-bear bodyguards. The bears follow their owner, join the owner's fight, retaliate against attackers, and refuse to target the owner or allied players. A death bond deals configurable damage to the owner when one of the summoned bears dies.
- **Guard configuration:** Added `ice_emperor_guard_count` (default 2), `ice_emperor_guard_cooldown_ticks` (default 1200 / 60 seconds), and `ice_emperor_guard_death_damage` (default 4 damage per guard death). Guard count is clamped to 1–6.
- **Free passives:** GOAT's and Ice Emperor's R-key passive enable/disable toggles no longer spend Energy. Passive effects continue to operate without periodic Energy drain; active abilities retain their existing Energy costs.
- **Cleanup and friendly-fire protection:** Guard entities are removed cleanly when Ice Emperor is unassigned, and damage from a guard to its owner/allies is blocked as a final safeguard in addition to its target filtering.
- **Validation:** Added the guard settings to config validation and updated source documentation. Delimiter and JSON checks are run for this release; full Gradle compilation still needs an available Gradle distribution and game-runtime testing.
- **Version:** `mod_version=1-15-3`; expected JAR: `build/libs/yppowers-1-15-3.jar`.

## 1-15-2 — Short-lived mythic Poseidon wave

- **Poseidon:** Reworked the H-key tsunami into a moving, expanding wall of water particles with a tall foaming crest, falling spray, bubbles, a brief sea-spray column, and a roaring splash soundtrack. The wave lasts 36 ticks (1.8 seconds) by default and damages/stuns opponents as its crest passes. Allies are excluded and each target can be hit only once.
- **Performance and safety:** The wave does not place actual water blocks or schedule fluid ticks, avoiding runaway flooding and block-update costs. Decorative particle calls are throttled while gameplay hit checks continue each tick.
- **Configuration:** Added `poseidon_tsunami_wave_duration_ticks`.
- **Version:** `mod_version=1-15-2`; expected JAR: `build/libs/yppowers-1-15-2.jar`.

## 1-15-1 — Real Raigo lightning, Thor Mjölnir rework, Poseidon tsunami

- **Raigo:** Thunderhead rain and impact now use actual non-cosmetic Minecraft `LightningEntity` strikes, so the lightning has normal world effects rather than being visual-only.
- **Thor:** G throws the soulbound Mjölnir. Press G again while it is deployed to teleport to it and recall it. On impact it strikes lightning, damages nearby opponents, optionally digs a configurable crater, and returns toward Thor in a Loyalty-style flight while breaking ordinary blocks along its path. H activates Ancient Rage; R still cycles mace enchantments. Death/respawn restores the soulbound mace.
- **Thor settings:** Added `thor_riptide_min_charge_ticks`, `thor_riptide_strength`, `thor_throw_speed`, `thor_crater_enabled`, `thor_crater_radius`, `thor_crater_depth`, `thor_impact_damage`, and `thor_impact_radius`.
- **Poseidon:** Added H-key tsunami ability. It searches for water within 100 blocks, damages and briefly stuns opponents in its wave area, and buffs Poseidon with Regeneration, Dolphin's Grace and Strength for 30 seconds by default. Water regeneration remains configurable.
- **Documentation:** Updated the detailed original specification, configuration guide, README, and build-status notes.
- **Version:** `mod_version=1-15-1`; expected JAR: `build/libs/yppowers-1-15-1.jar`.

## 1-14-9 — Flight Energy, Raigo thunderhead and Black Hole optimization

- **Boots of the Sky:** Flight toggles no longer charge the ordinary primary Energy fee. Flight consumes configurable `boots_flight_energy_drain_per_second` (default `0.10`) once per second. Energy regeneration pauses only during active flight so the small upkeep causes a gradual net drain. If Energy cannot cover the payment, flight stops safely with Slow Falling and without triggering Bleeding.
- **Raigo visuals:** A bright white orb rises from the caster to the sky above the locked target, expands into a cloud/smoke/electric thunderhead, then rains particle lightning before the existing impact tick. Target, radius, damage, knockback, cooldown, Energy cost and optional terrain behavior are unchanged. The final decorative spark count is reduced.
- **Black Hole performance:** Particle emission and implosion VFX counts are reduced; the expanding spiral updates every third tick. The terrain sphere is generated lazily within the existing per-tick work budget, eliminating the full-list allocation and sort during activation. The same eligible spherical positions, protection checks, pull and implosion behavior remain.
- **Configuration:** Added `boots_flight_energy_drain_per_second` (default `0.10`, clamped to `0–100`).
- **Version:** `mod_version=1-14-9`; expected JAR: `build/libs/yppowers-1-14-9.jar`.

## 1-14-8 — Clearer Thor Storm Launch and chaining

- **Simpler prompts:** Replaced the long wind-up/ready/missed wording with short action-bar states: `CHARGING`, `LAUNCH QUEUED`, `READY TO LAUNCH`, `LAUNCHED`, `NEXT LAUNCH QUEUED`, and `RECHARGED`.
- **Repeatable chain input:** A right-click during the chain delay or remaining flight burst queues the next launch. It charges and fires automatically after the delay, so one tap during each flight can sustain a chain.
- **Buffered wind-up:** A late right-click queues the launch to fire automatically at readiness; otherwise, the player can launch during the clearly labelled ready window.
- **Preserved movement:** Launch velocity, upward impulse, steering, velocity cap, energy cost per charge, and existing timings/configuration values were not changed.
- **Version:** `mod_version=1-14-8`; expected JAR: `build/libs/yppowers-1-14-8.jar`.

## 1-14-7 — Domain and Black Hole particle implosions

- **Teleport Gambit Domain:** during the existing four-tick pull, remove the gate block and replace its visual with a dense, shrinking ball of near-black particles and a contracting corkscrew. The original replaceable block state is restored afterward; if the original position was air, the gate remains gone. Pull duration, affected-player capture, direct dimension teleport, cooldown, session membership and return behavior are unchanged.
- **Time Warper Black Hole:** replace the sparse portal ring with a dense, dark entity-effect cloud, smoke and ash, plus a rotating 3-D spiral whose radius grows through the existing charge and pull phases. At the existing implosion tick, damage, knockback and terrain processing still resolve immediately; a separate five-tick visual contracts inward and ends in a white flash. This visual state does not keep the ability active or alter its gameplay timing.
- **Optimization:** use batched particle spawns for dense clouds and a small number of spiral points per tick. No new configuration or gameplay behavior was added; the added state is limited to visual cleanup.
- **Documentation cleanup:** condensed README and changelog; updated the specification and verification notes; removed obsolete per-patch debug and porting notes.
- **Version:** `mod_version=1-14-7`; expected JAR: `build/libs/yppowers-1-14-7.jar`.

## 1-14-6 — Clearer gate and fast pull

- Made the fully charged Domain gate more obvious with a brief beacon-style column, activation burst and rotating halo.
- Kept the four-tick pull and direct cross-dimension teleport.

## 1-14-5 — Domain beam and pull animation

- Added the temporary beacon beam and short pull animation before direct transfer.

## 1-14-4 — Domain transition repair

- Simplified Domain entry to direct player transfers and improved pull stability and session handling.

## 1-14-3 — Domain dimension registry

- Updated the custom dimension-type JSON structure for Minecraft 1.21.11.

## 1-14-2 — Reported compile fixes

- Corrected `FLASH` particle-effect construction, Thor's buffered-launch deadline type and Overworld spawn-point access.

## 1-14-1 — Shadow Archer view sizing

- Added synchronized morph view dimensions, a bounded view adjustment and first-person disguise-model suppression; retained the removal of recurring disguise smoke.

## 1-14-0 — Shared Energy and power expansion

- Added configurable player health and Energy/HUD, Bleeding, GOAT Overdrive, Time Warper Black Hole, Boots of the Sky Raigo, and additional Thor launch controls and configuration.
- Included pixel-art Energy HUD resources and retained the Domain dimension implementation.

## Earlier project milestones

- Added the original power roster: Divinity Shield, GOAT, Time Warper, Teleport Gambit, Serious Punch, Poseidon, Shadow Archer, Boots of the Sky, Stick of Deletion, Fire God and Ice Emperor.
- Expanded Shadow Archer with special arrows and kill-unlocked vanilla mob morphs; added Ice Emperor's meteor, Poseidon's enchantment swapping, Thor's mace/hammer systems, Grace, alliance controls and configurable cooldowns.
- Ported the mod to Minecraft 1.21.11 using Fabric, Java 21, typed networking payloads and component-based item data.

## Verification note

Static checks are not proof of a successful game build. A full `./gradlew clean build`, registry startup, client/server launch and in-game regression test are required. In the authoring environment, the Gradle distribution host was not reachable, so full semantic compilation and runtime behavior have not been confirmed here.
