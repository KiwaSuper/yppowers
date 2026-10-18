# YP Powers — 1.21.11 compiler follow-up (v8)

## Errors addressed from the supplied Gradle report

1. `World.isClient` field access (4 locations): replaced with `World.isClient()`.
2. Old `Screen.keyPressed(int, int, int)` signature (2 errors): migrated to `keyPressed(KeyInput)`; Enter is checked through `KeyInput.isEnter()`.
3. `ArrowEntity.getDamage()` (2 errors): Minecraft 1.21.11 exposes `PersistentProjectileEntity.setDamage(double)` but not a public getter. Added `PersistentProjectileDamageAccessor` to read the private projectile damage field and registered it in the mixin config.
4. `ServerCommandSource.hasPermissionLevel(int)` (1 error): migrated to the 1.21.11 permission predicate API using `Permission.Level(PermissionLevel.GAMEMASTERS)`.

## Build/remapping messages

- The settings.gradle Groovy assignment deprecation was fixed with `url = uri(...)` syntax.
- Removed the legacy Mixin annotation processor/refmap configuration and use Loom 1.14's default in-place Mixin remapping. Modern Loom documentation says the AP is disabled by default and mixin targets are remapped in-place; this is intended to avoid the legacy AP message `Cannot remap modifiers because it does not exists in any of the targets [] or their parents`.
- This environment cannot resolve `services.gradle.org`, so I could not run a full Gradle compile or confirm that the Mixin warning is gone. Treat it as a build-configuration change, not a verified runtime result.

## Checks performed here

- `fabric.mod.json`, `yppowers.mixins.json`, and `assets/yppowers/lang/en_us.json` parse as JSON.
- `sh -n gradlew` passes.
- A source scan confirms the reported obsolete source patterns were removed.

## Remaining verification

Run `./gradlew clean build` on a networked machine with Java 21. If compilation passes, the mod still needs client/server startup and in-game tests, especially the new accessor mixin and command permission check.
