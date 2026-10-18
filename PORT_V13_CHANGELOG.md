# Port v13 — Ice Emperor freezing damage

## Ice Emperor Glacial Aura
- Added passive freeze-type damage to hostile mobs that remain inside the Glacial Aura.
- Default: 1.0 damage (half a heart) every 40 ticks (2 seconds) per target.
- Added configuration keys:
  - `ice_emperor_aura_freeze_damage` (default `1.0`; set to `0` to disable)
  - `ice_emperor_aura_freeze_damage_interval_ticks` (default `40`; 20 ticks = 1 second)
- Uses Minecraft's native freeze damage source so damage is classified as freezing damage rather than magic damage.
- Shares a per-target cooldown across overlapping fields, preventing multiple Ice Emperor holders from multiplying damage frequency.
- Cleans stale per-entity damage timing records periodically.

## Validation
- Resource JSON, ZIP integrity, and source delimiter checks performed.
- Full Gradle compilation could not be run in this environment because Gradle 9.2.1 is not installed/cached and the wrapper download requires external network access. Run `./gradlew clean build` before installing.


### Compile fix (report follow-up)
- Added the missing `net.minecraft.server.world.ServerWorld` import in `FireGodPower.java`, resolving the reported `cannot find symbol: ServerWorld` compile error.
