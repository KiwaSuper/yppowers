# Port v13.5 — compile-report fix

The supplied `problems-report(4).html` showed two compilation errors in `ShadowArcherPower.java`: `ParticleTypes.DRAGON_BREATH` is a `ParticleType<DragonBreathParticleEffect>`, but `ServerWorld.spawnParticles` requires a `ParticleEffect` instance.

Both Dragon Breath and Dragon Roar visual effects now call `DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F)` and pass that effect instance to `spawnParticles`. No morph abilities, unlock rules, or unrelated powers were changed by this fix.

The type usage is confirmed against Yarn 1.21.11+build.6 API documentation. A full project build is attempted separately; see final report for its result.
