# 1.21.11 Compiler Report Follow-up (v7)

The attached compiler report was analyzed. It reported 100 compiler-error occurrences, mostly cascading from Yarn 1.21.11 entity API renames. This source pass addresses all root causes visible in that report:

- `Entity#getWorld()` -> `getEntityWorld()`
- `Entity#getPos()` -> `getEntityPos()`
- server-player `getServer()` -> `getEntityWorld().getServer()`
- server-player `getServerWorld()` -> cast `getEntityWorld()` to `ServerWorld`
- removed `Entity.velocityModified` field -> invoker for `scheduleVelocityUpdate()`
- `FireballEntity` constructor now receives a `Vec3d` velocity
- `ItemCooldownManager#set` now receives the stick `ItemStack`
- `SoundEvents.ENTITY_GENERIC_EXPLODE` uses its value when calling the block-position sound overload
- entity registry lookup now uses `getOptionalValue(id)`
- unbreakable item data now stores `Unit.INSTANCE` in `DataComponentTypes.UNBREAKABLE`

## Verification status

This is a source-level patch based on the user's Gradle compile report and the official Yarn 1.21.11+build.6 API documentation. It has not been compiled in this environment because the Gradle/Minecraft dependencies are not available here. A fresh Gradle build can reveal additional errors that were not reachable in the report's failed compile pass. Do not consider this a tested release until `./gradlew build` succeeds.
