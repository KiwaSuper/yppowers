# Port v13.3 — Ice Prison Arrow reliability and Serious Punch fan

## Ice Prison Arrow

- Special arrows are now registered at the moment they enter `ServerWorld`, locking in the arrow type selected when fired. The existing periodic scan remains as a fallback.
- Ice Prison uses the collision's exact hit position rather than the arrow's last entity position.
- The entity actually struck is always included (unless it is the shooter or allied), and nearby hostile living entities within a 3.5-block radius are included.
- Preserves the documented effect: four seconds of Slowness II and Blindness.
- Adds a snowflake impact burst and glass-like cue so testers can tell the effect triggered.

## Serious Punch

- Launches a three-path fan at once: the original forward punch and two additional punches at -30° and +30° yaw.
- All three paths emit Sonic Boom particles and clear the existing 3×3 tunnel without block drops.
- Damage and the existing weakness/immobilization effects now apply to entities intersecting any of the three paths, within the original 25-block damage range.
- Allied players remain protected. Cooldown and per-hit damage are unchanged.

## Validation

- Static source and resource checks plus ZIP integrity checks are performed for this revision.
- A successful Gradle build is not claimed unless the dependency-backed build completes.
