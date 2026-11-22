package com.yppowers.config;

import java.util.Locale;
import java.util.Set;

/** Clean, strongly-typed nested configuration blocks for all 17 powers. */
public final class PowerConfigs {
    public Divinity divinity = new Divinity();
    public Goat goat = new Goat();
    public TimeWarper timeWarper = new TimeWarper();
    public TeleportGambit teleportGambit = new TeleportGambit();
    public SeriousPunch seriousPunch = new SeriousPunch();
    public Poseidon poseidon = new Poseidon();
    public ShadowArcher shadowArcher = new ShadowArcher();
    public BootsOfTheSky bootsOfTheSky = new BootsOfTheSky();
    public StickOfDeletion stickOfDeletion = new StickOfDeletion();
    public FireGod fireGod = new FireGod();
    public IceEmperor iceEmperor = new IceEmperor();
    public Thor thor = new Thor();
    public FalloutWarrior falloutWarrior = new FalloutWarrior();
    public Spidey spidey = new Spidey();
    public SpiritChanneler spiritChanneler = new SpiritChanneler();
    public Illusionist illusionist = new Illusionist();
    public Kitsune kitsune = new Kitsune();

    public void validate() {
        if (divinity == null) divinity = new Divinity();
        if (goat == null) goat = new Goat();
        if (timeWarper == null) timeWarper = new TimeWarper();
        if (teleportGambit == null) teleportGambit = new TeleportGambit();
        if (seriousPunch == null) seriousPunch = new SeriousPunch();
        if (poseidon == null) poseidon = new Poseidon();
        if (shadowArcher == null) shadowArcher = new ShadowArcher();
        if (bootsOfTheSky == null) bootsOfTheSky = new BootsOfTheSky();
        if (stickOfDeletion == null) stickOfDeletion = new StickOfDeletion();
        if (fireGod == null) fireGod = new FireGod();
        if (iceEmperor == null) iceEmperor = new IceEmperor();
        if (thor == null) thor = new Thor();
        if (falloutWarrior == null) falloutWarrior = new FalloutWarrior();
        if (spidey == null) spidey = new Spidey();
        if (spiritChanneler == null) spiritChanneler = new SpiritChanneler();
        if (illusionist == null) illusionist = new Illusionist();
        if (kitsune == null) kitsune = new Kitsune();

        divinity.validate();
        goat.validate();
        timeWarper.validate();
        teleportGambit.validate();
        seriousPunch.validate();
        poseidon.validate();
        shadowArcher.validate();
        bootsOfTheSky.validate();
        stickOfDeletion.validate();
        fireGod.validate();
        iceEmperor.validate();
        thor.validate();
        falloutWarrior.validate();
        spidey.validate();
        spiritChanneler.validate();
        illusionist.validate();
        kitsune.validate();
    }

    public static final class Divinity {
        public int healRadius = 12;
        public double healAmount = 0.5;
        public int forcefieldDurationTicks = 300;
        public int forcefieldCooldownTicks = 600;
        public double damageReflectRatio = 0.30;

        void validate() {
            healRadius = ConfigMath.clamp(healRadius, 2, 64, 12);
            healAmount = ConfigMath.clamp(healAmount, 0.0, 20.0, 0.5);
            forcefieldDurationTicks = ConfigMath.clamp(forcefieldDurationTicks, 20, 6000, 300);
            forcefieldCooldownTicks = ConfigMath.clamp(forcefieldCooldownTicks, 0, 120000, 600);
            damageReflectRatio = ConfigMath.clamp(damageReflectRatio, 0.0, 1.0, 0.30);
        }
    }

    public static final class Goat {
        public double jumpMultiplier = 1.25;
        public int jumpBoostAmplifier = 0;
        public double sprintStartMultiplier = 2.0;
        public double maxSpeed = 5.0;
        public int sprintAccelerationTicks = 100;
        public int sprintResetDelayTicks = 10;
        public boolean preserveSprintWhileJumping = true;
        public boolean sprintEnabled = true;
        public boolean sprintParticlesEnabled = false;
        public boolean sprintSoundsEnabled = false;
        public boolean jumpFallProtectionEnabled = true;
        public double jumpFallDamageMultiplier = 0.0;
        public boolean passiveFallProtectionEnabled = true;
        public double passiveFallDamageMultiplier = 0.4;
        public int overdriveDurationTicks = 300;
        public int overdriveCooldownTicks = 1800;
        public double overdriveEnergyCost = 60.0;
        public double overdriveMovementMultiplier = 3.0;
        public double overdriveJumpMultiplier = 4.0;
        public double overdriveKnockbackResistanceBonus = 0.6;
        public double overdriveMaxDamageDebt = 200.0;
        public int overdriveDebtReleaseIntervalTicks = 10;
        public double overdriveDebtReleasePerHit = 1.0;

        void validate() {
            jumpMultiplier = ConfigMath.clamp(jumpMultiplier, 1.0, 2.5, 1.25);
            jumpBoostAmplifier = ConfigMath.clamp(jumpBoostAmplifier, 0, 2, 0);
            maxSpeed = ConfigMath.clamp(maxSpeed, 1.0, 10.0, 5.0);
            sprintStartMultiplier = ConfigMath.clamp(sprintStartMultiplier, 1.0, maxSpeed, 2.0);
            sprintAccelerationTicks = ConfigMath.clamp(sprintAccelerationTicks, 1, 2400, 100);
            sprintResetDelayTicks = ConfigMath.clamp(sprintResetDelayTicks, 0, 1200, 10);
            jumpFallDamageMultiplier = ConfigMath.clamp(jumpFallDamageMultiplier, 0.0, 1.0, 0.0);
            passiveFallDamageMultiplier = ConfigMath.clamp(passiveFallDamageMultiplier, 0.0, 1.0, 0.4);
            overdriveDurationTicks = ConfigMath.clamp(overdriveDurationTicks, 1, 12000, 300);
            overdriveCooldownTicks = ConfigMath.clamp(overdriveCooldownTicks, 0, 120000, 1800);
            overdriveEnergyCost = ConfigMath.clamp(overdriveEnergyCost, 0.0, 100000.0, 60.0);
            overdriveMovementMultiplier = ConfigMath.clamp(overdriveMovementMultiplier, 1.0, 10.0, 3.0);
            overdriveJumpMultiplier = ConfigMath.clamp(overdriveJumpMultiplier, 1.0, 10.0, 4.0);
            overdriveKnockbackResistanceBonus = ConfigMath.clamp(overdriveKnockbackResistanceBonus, 0.0, 1.0, 0.6);
            overdriveMaxDamageDebt = ConfigMath.clamp(overdriveMaxDamageDebt, 1.0, 10000.0, 200.0);
            overdriveDebtReleaseIntervalTicks = ConfigMath.clamp(overdriveDebtReleaseIntervalTicks, 1, 1200, 10);
            overdriveDebtReleasePerHit = ConfigMath.clamp(overdriveDebtReleasePerHit, 0.1, 100.0, 1.0);
        }
    }

    public static final class TimeWarper {
        public int timeStopDurationTicks = 120;
        public int timeStopCooldownTicks = 600;
        public int rewindCooldownTicks = 600;
        public int formSwitchCooldownTicks = 12000;
        public int blackHoleChargeTicks = 40;
        public int blackHolePullTicks = 80;
        public double blackHoleEntityRadius = 20.0;
        public double blackHoleTerrainRadius = 12.0;
        public double blackHoleImplosionRadius = 12.0;
        public double blackHoleMaxDamage = 10.0;
        public int blackHoleCooldownTicks = 6000;
        public double blackHoleHealthSacrificePercent = 0.5;
        public double blackHoleEnergyCost = 80.0;
        public boolean blackHoleTerrainDamage = true;
        public boolean blackHoleAffectPlayers = true;
        public boolean blackHoleAffectMobs = true;
        public boolean blackHoleAffectItems = true;
        public double blackHoleItemsForMaxPower = 256.0;
        public double blackHoleMaxPowerMultiplier = 3.0;
        public double blackHoleBlockItemValue = 0.02;
        public double blackHoleMaxBlastDamage = 24.0;
        public int blackHoleMaxBlocksPerTick = 64;
        public double cloneEnergyCost = 20.0;
        public int cloneShardsCost = 3;
        public double eliteCloneEnergyCost = 40.0;
        public int eliteCloneGoldenShardsCost = 1;
        public int cloneMaxActive = 25;
        public int cloneLifespanTicks = 2400;
        public int timeShardLifetimeTicks = 80;
        public double timeShardDropChance = 0.15;
        public double goldenShardDropChance = 0.25;

        void validate() {
            timeStopDurationTicks = ConfigMath.clamp(timeStopDurationTicks, 20, 1200, 120);
            timeStopCooldownTicks = ConfigMath.clamp(timeStopCooldownTicks, 0, 120000, 600);
            rewindCooldownTicks = ConfigMath.clamp(rewindCooldownTicks, 0, 120000, 600);
            formSwitchCooldownTicks = ConfigMath.clamp(formSwitchCooldownTicks, 0, 1200000, 12000);
            blackHoleChargeTicks = ConfigMath.clamp(blackHoleChargeTicks, 1, 1200, 40);
            blackHolePullTicks = ConfigMath.clamp(blackHolePullTicks, 1, 1200, 80);
            blackHoleEntityRadius = ConfigMath.clamp(blackHoleEntityRadius, 1.0, 128.0, 20.0);
            blackHoleTerrainRadius = ConfigMath.clamp(blackHoleTerrainRadius, 0.0, 32.0, 12.0);
            blackHoleImplosionRadius = ConfigMath.clamp(blackHoleImplosionRadius, 1.0, 64.0, 12.0);
            blackHoleMaxDamage = ConfigMath.clamp(blackHoleMaxDamage, 0.0, 100.0, 10.0);
            blackHoleCooldownTicks = ConfigMath.clamp(blackHoleCooldownTicks, 0, 1200000, 6000);
            blackHoleHealthSacrificePercent = ConfigMath.clamp(blackHoleHealthSacrificePercent, 0.0, 0.95, 0.5);
            blackHoleEnergyCost = ConfigMath.clamp(blackHoleEnergyCost, 0.0, 100000.0, 80.0);
            blackHoleItemsForMaxPower = ConfigMath.clamp(blackHoleItemsForMaxPower, 1.0, 100000.0, 256.0);
            blackHoleMaxPowerMultiplier = ConfigMath.clamp(blackHoleMaxPowerMultiplier, 1.0, 6.0, 3.0);
            blackHoleBlockItemValue = ConfigMath.clamp(blackHoleBlockItemValue, 0.0, 10.0, 0.02);
            blackHoleMaxBlastDamage = ConfigMath.clamp(blackHoleMaxBlastDamage, 0.0, 200.0, 24.0);
            blackHoleMaxBlocksPerTick = ConfigMath.clamp(blackHoleMaxBlocksPerTick, 0, 512, 64);
            cloneEnergyCost = ConfigMath.clamp(cloneEnergyCost, 0.0, 100000.0, 20.0);
            cloneShardsCost = ConfigMath.clamp(cloneShardsCost, 0, 1000, 3);
            eliteCloneEnergyCost = ConfigMath.clamp(eliteCloneEnergyCost, 0.0, 100000.0, 40.0);
            eliteCloneGoldenShardsCost = ConfigMath.clamp(eliteCloneGoldenShardsCost, 0, 64, 1);
            cloneMaxActive = ConfigMath.clamp(cloneMaxActive, 1, 100, 25);
            cloneLifespanTicks = ConfigMath.clamp(cloneLifespanTicks, 100, 72000, 2400);
            timeShardLifetimeTicks = ConfigMath.clamp(timeShardLifetimeTicks, 20, 1200, 80);
            timeShardDropChance = ConfigMath.clamp(timeShardDropChance, 0.0, 1.0, 0.15);
            goldenShardDropChance = ConfigMath.clamp(goldenShardDropChance, 0.0, 1.0, 0.25);
        }
    }

    public static final class TeleportGambit {
        public int gatewayCooldownTicks = 300;
        public int gatewayDurationTicks = 100;
        public int domainChargeTicks = 160;
        public double domainPullRadius = 25.0;
        public int domainCooldownTicks = 18000;

        void validate() {
            gatewayCooldownTicks = ConfigMath.clamp(gatewayCooldownTicks, 0, 120000, 300);
            gatewayDurationTicks = ConfigMath.clamp(gatewayDurationTicks, 20, 1200, 100);
            domainChargeTicks = ConfigMath.clamp(domainChargeTicks, 20, 1200, 160);
            domainPullRadius = ConfigMath.clamp(domainPullRadius, 4.0, 128.0, 25.0);
            domainCooldownTicks = ConfigMath.clamp(domainCooldownTicks, 0, 1200000, 18000);
        }
    }

    public static final class SeriousPunch {
        public int cooldownTicks = 200;
        public double raycastRange = 60.0;
        public int momentumMax = 8;
        public int momentumGainPerHit = 1;
        public int momentumDecayTicks = 80;
        public double momentumDamagePerStack = 0.08;
        public double helldiveEnergyCost = 30.0;
        public double helldiveDamage = 10.0;
        public double helldiveRadius = 7.0;
        public int helldiveCraterRadius = 4;
        public int helldiveCraterDepth = 2;
        public double helldiveLaunchVelocity = 1.2;
        public int helldiveCooldownTicks = 300;

        void validate() {
            cooldownTicks = ConfigMath.clamp(cooldownTicks, 0, 120000, 200);
            raycastRange = ConfigMath.clamp(raycastRange, 8.0, 128.0, 60.0);
            momentumMax = ConfigMath.clamp(momentumMax, 1, 50, 8);
            momentumGainPerHit = ConfigMath.clamp(momentumGainPerHit, 1, 10, 1);
            momentumDecayTicks = ConfigMath.clamp(momentumDecayTicks, 10, 1200, 80);
            momentumDamagePerStack = ConfigMath.clamp(momentumDamagePerStack, 0.0, 2.0, 0.08);
            helldiveEnergyCost = ConfigMath.clamp(helldiveEnergyCost, 0.0, 100000.0, 30.0);
            helldiveDamage = ConfigMath.clamp(helldiveDamage, 0.0, 200.0, 10.0);
            helldiveRadius = ConfigMath.clamp(helldiveRadius, 2.0, 32.0, 7.0);
            helldiveCraterRadius = ConfigMath.clamp(helldiveCraterRadius, 1, 12, 4);
            helldiveCraterDepth = ConfigMath.clamp(helldiveCraterDepth, 1, 8, 2);
            helldiveLaunchVelocity = ConfigMath.clamp(helldiveLaunchVelocity, 0.3, 4.0, 1.2);
            helldiveCooldownTicks = ConfigMath.clamp(helldiveCooldownTicks, 0, 120000, 300);
        }
    }

    public static final class Poseidon {
        public int tridentSwapCooldownTicks = 60;
        public int waterRegenerationAmplifier = 4;
        public boolean waterBuffsEnabled = true;
        public int waterHasteAmplifier = 0;
        public int waterResistanceAmplifier = 0;
        public int waterStrengthAmplifier = 0;
        public boolean waterHungerProtectionEnabled = true;
        public String waterCondition = "TOUCHING";
        public int tsunamiCooldownTicks = 2400;
        public double tsunamiSearchRadius = 100.0;
        public double tsunamiWaveRadius = 18.0;
        public int tsunamiWaveDurationTicks = 36;
        public double tsunamiDamage = 8.0;
        public int tsunamiStunTicks = 50;
        public int tsunamiBuffTicks = 600;
        public int tsunamiBuffRegenerationAmplifier = 2;

        void validate() {
            tridentSwapCooldownTicks = ConfigMath.clamp(tridentSwapCooldownTicks, 0, 12000, 60);
            waterRegenerationAmplifier = ConfigMath.clamp(waterRegenerationAmplifier, 0, 9, 4);
            waterHasteAmplifier = ConfigMath.clamp(waterHasteAmplifier, 0, 3, 0);
            waterResistanceAmplifier = ConfigMath.clamp(waterResistanceAmplifier, 0, 2, 0);
            waterStrengthAmplifier = ConfigMath.clamp(waterStrengthAmplifier, 0, 2, 0);
            waterCondition = waterCondition == null ? "TOUCHING" : waterCondition.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("TOUCHING", "SUBMERGED", "SWIMMING_OR_SUBMERGED").contains(waterCondition)) {
                waterCondition = "TOUCHING";
            }
            tsunamiCooldownTicks = ConfigMath.clamp(tsunamiCooldownTicks, 0, 1200000, 2400);
            tsunamiSearchRadius = ConfigMath.clamp(tsunamiSearchRadius, 1.0, 100.0, 100.0);
            tsunamiWaveRadius = ConfigMath.clamp(tsunamiWaveRadius, 1.0, 64.0, 18.0);
            tsunamiWaveDurationTicks = ConfigMath.clamp(tsunamiWaveDurationTicks, 10, 100, 36);
            tsunamiDamage = ConfigMath.clamp(tsunamiDamage, 0.0, 100.0, 8.0);
            tsunamiStunTicks = ConfigMath.clamp(tsunamiStunTicks, 0, 1200, 50);
            tsunamiBuffTicks = ConfigMath.clamp(tsunamiBuffTicks, 1, 12000, 600);
            tsunamiBuffRegenerationAmplifier = ConfigMath.clamp(tsunamiBuffRegenerationAmplifier, 0, 9, 2);
        }
    }

    public static final class ShadowArcher {
        public int morphCooldownTicks = 200;
        public double specialArrowEnergyCost = 5.0;
        public int railshotBaseDrawTicks = 4;
        public int railshotMaxDrawTicks = 20;
        public int railshotDrawGainTicks = 2;
        public int railshotChargeDecayTicks = 100;
        public double railshotSpeedMultiplierPerCharge = 0.08;
        public double railshotDamageMultiplierPerCharge = 0.12;
        public double railshotInaccuracyMultiplierPerCharge = 0.08;

        void validate() {
            morphCooldownTicks = ConfigMath.clamp(morphCooldownTicks, 0, 12000, 200);
            specialArrowEnergyCost = ConfigMath.clamp(specialArrowEnergyCost, 0.0, 100000.0, 5.0);
            railshotBaseDrawTicks = ConfigMath.clamp(railshotBaseDrawTicks, 1, 40, 4);
            railshotMaxDrawTicks = ConfigMath.clamp(railshotMaxDrawTicks, railshotBaseDrawTicks, 80, 20);
            railshotDrawGainTicks = ConfigMath.clamp(railshotDrawGainTicks, 1, 20, 2);
            railshotChargeDecayTicks = ConfigMath.clamp(railshotChargeDecayTicks, 10, 1200, 100);
            railshotSpeedMultiplierPerCharge = ConfigMath.clamp(railshotSpeedMultiplierPerCharge, 0.0, 1.0, 0.08);
            railshotDamageMultiplierPerCharge = ConfigMath.clamp(railshotDamageMultiplierPerCharge, 0.0, 1.0, 0.12);
            railshotInaccuracyMultiplierPerCharge = ConfigMath.clamp(railshotInaccuracyMultiplierPerCharge, 0.0, 1.0, 0.08);
        }
    }

    public static final class BootsOfTheSky {
        public int flightDurationTicks = 6000;
        public int rechargeDurationTicks = 3600;
        public double flightEnergyDrainPerSecond = 0.10;
        public int raigoChargeTicks = 60;
        public double raigoRange = 80.0;
        public double raigoRadius = 20.0;
        public double raigoMaxDamage = 16.0;
        public int raigoCooldownTicks = 3600;
        public double raigoEnergyCost = 75.0;
        public boolean raigoTerrainDamage = false;
        public int raigoMaxBlocksPerTick = 64;

        void validate() {
            flightDurationTicks = ConfigMath.clamp(flightDurationTicks, 20, 72000, 6000);
            rechargeDurationTicks = ConfigMath.clamp(rechargeDurationTicks, 20, 72000, 3600);
            flightEnergyDrainPerSecond = ConfigMath.clamp(flightEnergyDrainPerSecond, 0.0, 100.0, 0.10);
            raigoChargeTicks = ConfigMath.clamp(raigoChargeTicks, 1, 1200, 60);
            raigoRange = ConfigMath.clamp(raigoRange, 1.0, 256.0, 80.0);
            raigoRadius = ConfigMath.clamp(raigoRadius, 1.0, 128.0, 20.0);
            raigoMaxDamage = ConfigMath.clamp(raigoMaxDamage, 0.0, 100.0, 16.0);
            raigoCooldownTicks = ConfigMath.clamp(raigoCooldownTicks, 0, 1200000, 3600);
            raigoEnergyCost = ConfigMath.clamp(raigoEnergyCost, 0.0, 100000.0, 75.0);
            raigoMaxBlocksPerTick = ConfigMath.clamp(raigoMaxBlocksPerTick, 0, 512, 64);
        }
    }

    public static final class StickOfDeletion {
        public int cooldownTicks = 80;
        public double launchVelocity = 4.2;
        public double wallImpactDamage = 8.0;

        void validate() {
            cooldownTicks = ConfigMath.clamp(cooldownTicks, 0, 12000, 80);
            launchVelocity = ConfigMath.clamp(launchVelocity, 0.5, 10.0, 4.2);
            wallImpactDamage = ConfigMath.clamp(wallImpactDamage, 0.0, 100.0, 8.0);
        }
    }

    public static final class FireGod {
        public int cannonCooldownTicks = 240;
        public double blazeEnergyCost = 20.0;
        public int blazeMaxActive = 3;
        public int blazeCooldownTicks = 200;
        public int blazeLifespanTicks = 1200;
        public double blazeMaxHealth = 20.0;
        public double blazeAttackDamage = 6.0;
        public double witherSkeletonEnergyCost = 25.0;
        public int witherSkeletonMaxActive = 3;
        public int witherSkeletonCooldownTicks = 200;
        public int witherSkeletonLifespanTicks = 1200;
        public double witherSkeletonMaxHealth = 28.0;
        public double witherSkeletonAttackDamage = 8.0;

        void validate() {
            cannonCooldownTicks = ConfigMath.clamp(cannonCooldownTicks, 0, 12000, 240);
            blazeEnergyCost = ConfigMath.clamp(blazeEnergyCost, 0.0, 100000.0, 20.0);
            blazeMaxActive = ConfigMath.clamp(blazeMaxActive, 1, 16, 3);
            blazeCooldownTicks = ConfigMath.clamp(blazeCooldownTicks, 0, 12000, 200);
            blazeLifespanTicks = ConfigMath.clamp(blazeLifespanTicks, 100, 72000, 1200);
            blazeMaxHealth = ConfigMath.clamp(blazeMaxHealth, 1.0, 500.0, 20.0);
            blazeAttackDamage = ConfigMath.clamp(blazeAttackDamage, 0.0, 100.0, 6.0);
            witherSkeletonEnergyCost = ConfigMath.clamp(witherSkeletonEnergyCost, 0.0, 100000.0, 25.0);
            witherSkeletonMaxActive = ConfigMath.clamp(witherSkeletonMaxActive, 1, 16, 3);
            witherSkeletonCooldownTicks = ConfigMath.clamp(witherSkeletonCooldownTicks, 0, 12000, 200);
            witherSkeletonLifespanTicks = ConfigMath.clamp(witherSkeletonLifespanTicks, 100, 72000, 1200);
            witherSkeletonMaxHealth = ConfigMath.clamp(witherSkeletonMaxHealth, 1.0, 500.0, 28.0);
            witherSkeletonAttackDamage = ConfigMath.clamp(witherSkeletonAttackDamage, 0.0, 100.0, 8.0);
        }
    }

    public static final class IceEmperor {
        public double auraRadius = 4.0;
        public double auraFreezeDamage = 1.0;
        public int auraFreezeDamageIntervalTicks = 40;
        public int freezeDurationTicks = 50;
        public int absoluteZeroCooldownTicks = 600;
        public int meteorCooldownTicks = 1200;
        public double meteorRadius = 50.0;
        public double meteorDamage = 12.0;
        public int meteorEffectDurationTicks = 160;
        public int meteorIceDurationTicks = 1200;
        public int guardCount = 2;
        public int guardCooldownTicks = 1200;
        public double guardDeathDamage = 4.0;
        public double guardLowEnergySpeedMultiplier = 0.60;
        public double guardLowEnergyDamageMultiplier = 0.50;
        public double guardHostileChancePercent = 10.0;
        public double heatBlastMinEnergy = 10.0;
        public double heatBlastRadius = 12.0;
        public int heatBlastFireSeconds = 5;
        public int heatBlastSlownessTicks = 120;
        public int heatBlastSlownessAmplifier = 2;
        public int heatBlastLockoutTicks = 1200;
        public boolean formsEnabled = true;
        public boolean armouredFormEnabled = true;
        public boolean stealthFormEnabled = true;
        public boolean assassinFormEnabled = true;
        public String defaultForm = "ARMOURED";
        public int armouredResistanceAmplifier = 2;
        public double armouredMovementMultiplier = 0.90;
        public double armouredOutgoingDamageMultiplier = 0.90;
        public double stealthDetectionRadius = 10.0;
        public int stealthUpdateIntervalTicks = 5;
        public boolean stealthAlliesCount = true;
        public double assassinMovementMultiplier = 1.25;
        public double assassinMeleeDamageMultiplier = 1.30;
        public double assassinMaxHealthMultiplier = 0.80;
        public boolean assassinIceTrailEnabled = true;

        void validate() {
            auraRadius = ConfigMath.clamp(auraRadius, 1.0, 16.0, 4.0);
            auraFreezeDamage = ConfigMath.clamp(auraFreezeDamage, 0.0, 20.0, 1.0);
            auraFreezeDamageIntervalTicks = ConfigMath.clamp(auraFreezeDamageIntervalTicks, 5, 200, 40);
            freezeDurationTicks = ConfigMath.clamp(freezeDurationTicks, 10, 600, 50);
            absoluteZeroCooldownTicks = ConfigMath.clamp(absoluteZeroCooldownTicks, 0, 120000, 600);
            meteorCooldownTicks = ConfigMath.clamp(meteorCooldownTicks, 0, 120000, 1200);
            meteorRadius = ConfigMath.clamp(meteorRadius, 4.0, 80.0, 50.0);
            meteorDamage = ConfigMath.clamp(meteorDamage, 0.0, 100.0, 12.0);
            meteorEffectDurationTicks = ConfigMath.clamp(meteorEffectDurationTicks, 20, 1200, 160);
            meteorIceDurationTicks = ConfigMath.clamp(meteorIceDurationTicks, 40, 12000, 1200);
            guardCount = ConfigMath.clamp(guardCount, 1, 6, 2);
            guardCooldownTicks = ConfigMath.clamp(guardCooldownTicks, 0, 120000, 1200);
            guardDeathDamage = ConfigMath.clamp(guardDeathDamage, 0.0, 100.0, 4.0);
            guardLowEnergySpeedMultiplier = ConfigMath.clamp(guardLowEnergySpeedMultiplier, 0.1, 1.0, 0.60);
            guardLowEnergyDamageMultiplier = ConfigMath.clamp(guardLowEnergyDamageMultiplier, 0.1, 1.0, 0.50);
            guardHostileChancePercent = ConfigMath.clamp(guardHostileChancePercent, 0.0, 100.0, 10.0);
            heatBlastMinEnergy = ConfigMath.clamp(heatBlastMinEnergy, 1.0, 100000.0, 10.0);
            heatBlastRadius = ConfigMath.clamp(heatBlastRadius, 1.0, 64.0, 12.0);
            heatBlastFireSeconds = ConfigMath.clamp(heatBlastFireSeconds, 1, 30, 5);
            heatBlastSlownessTicks = ConfigMath.clamp(heatBlastSlownessTicks, 1, 1200, 120);
            heatBlastSlownessAmplifier = ConfigMath.clamp(heatBlastSlownessAmplifier, 0, 4, 2);
            heatBlastLockoutTicks = ConfigMath.clamp(heatBlastLockoutTicks, 0, 12000, 1200);
            armouredResistanceAmplifier = ConfigMath.clamp(armouredResistanceAmplifier, 0, 4, 2);
            armouredMovementMultiplier = ConfigMath.clamp(armouredMovementMultiplier, 0.10, 1.0, 0.90);
            armouredOutgoingDamageMultiplier = ConfigMath.clamp(armouredOutgoingDamageMultiplier, 0.10, 1.0, 0.90);
            stealthDetectionRadius = ConfigMath.clamp(stealthDetectionRadius, 1.0, 128.0, 10.0);
            stealthUpdateIntervalTicks = ConfigMath.clamp(stealthUpdateIntervalTicks, 1, 200, 5);
            assassinMovementMultiplier = ConfigMath.clamp(assassinMovementMultiplier, 1.0, 3.0, 1.25);
            assassinMeleeDamageMultiplier = ConfigMath.clamp(assassinMeleeDamageMultiplier, 1.0, 3.0, 1.30);
            assassinMaxHealthMultiplier = ConfigMath.clamp(assassinMaxHealthMultiplier, 0.10, 1.0, 0.80);
            defaultForm = defaultForm == null ? "ARMOURED" : defaultForm.trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(defaultForm)) defaultForm = "ARMOURED";
        }
    }

    public static final class Thor {
        public int riptideMinChargeTicks = 10;
        public double riptideStrength = 3.0;
        public boolean lightningOnHit = true;
        public int maceDensityLevel = 5;
        public int maceWindBurstLevel = 1;
        public int maceFireAspectLevel = 2;
        public int maceUnbreakingLevel = 3;

        void validate() {
            riptideMinChargeTicks = ConfigMath.clamp(riptideMinChargeTicks, 0, 1200, 10);
            riptideStrength = ConfigMath.clamp(riptideStrength, 0.1, 10.0, 3.0);
            maceDensityLevel = ConfigMath.clamp(maceDensityLevel, 0, 10, 5);
            maceWindBurstLevel = ConfigMath.clamp(maceWindBurstLevel, 0, 5, 1);
            maceFireAspectLevel = ConfigMath.clamp(maceFireAspectLevel, 0, 5, 2);
            maceUnbreakingLevel = ConfigMath.clamp(maceUnbreakingLevel, 0, 10, 3);
        }
    }

    public static final class FalloutWarrior {
        public boolean auraEnabledByDefault = true;
        public double auraRadius = 8.0;
        public double auraHealAmount = 1.0;
        public double auraDamageAmount = 0.5;
        public int auraIntervalTicks = 40;
        public int poolRadius = 10;
        public int poolDurationTicks = 300;
        public double poolDamage = 1.0;
        public double poolEnergyCost = 20.0;
        public int poolCooldownTicks = 1200;
        public int minionsPerCast = 3;
        public int activeMinionCap = 6;
        public double healerHealth = 16.0;
        public double defenderHealth = 40.0;
        public double assassinHealth = 24.0;
        public double assassinSpeedMultiplier = 1.5;
        public int minionLifespanTicks = 2400;
        public int minionSummonCooldownTicks = 2400;
        public double minionEnergyCost = 40.0;

        void validate() {
            auraRadius = ConfigMath.clamp(auraRadius, 1.0, 32.0, 8.0);
            auraHealAmount = ConfigMath.clamp(auraHealAmount, 0.0, 20.0, 1.0);
            auraDamageAmount = ConfigMath.clamp(auraDamageAmount, 0.0, 20.0, 0.5);
            auraIntervalTicks = ConfigMath.clamp(auraIntervalTicks, 5, 200, 40);
            poolRadius = ConfigMath.clamp(poolRadius, 2, 32, 10);
            poolDurationTicks = ConfigMath.clamp(poolDurationTicks, 20, 6000, 300);
            poolDamage = ConfigMath.clamp(poolDamage, 0.0, 20.0, 1.0);
            poolEnergyCost = ConfigMath.clamp(poolEnergyCost, 0.0, 100000.0, 20.0);
            poolCooldownTicks = ConfigMath.clamp(poolCooldownTicks, 0, 120000, 1200);
            minionsPerCast = ConfigMath.clamp(minionsPerCast, 1, 9, 3);
            activeMinionCap = ConfigMath.clamp(activeMinionCap, 1, 24, 6);
            healerHealth = ConfigMath.clamp(healerHealth, 2.0, 200.0, 16.0);
            defenderHealth = ConfigMath.clamp(defenderHealth, 2.0, 200.0, 40.0);
            assassinHealth = ConfigMath.clamp(assassinHealth, 2.0, 200.0, 24.0);
            assassinSpeedMultiplier = ConfigMath.clamp(assassinSpeedMultiplier, 0.5, 4.0, 1.5);
            minionLifespanTicks = ConfigMath.clamp(minionLifespanTicks, 100, 72000, 2400);
            minionSummonCooldownTicks = ConfigMath.clamp(minionSummonCooldownTicks, 0, 120000, 2400);
            minionEnergyCost = ConfigMath.clamp(minionEnergyCost, 0.0, 100000.0, 40.0);
        }
    }

    public static final class Spidey {
        public double webSlingRange = 32.0;
        public double webSlingVelocity = 1.5;
        public int mlgFallThreshold = 8;
        public int mlgSearchRadius = 3;
        public int mlgCooldownTicks = 40;
        public double webGrenadeRadius = 6.0;
        public int webGrenadeDurationTicks = 100;
        public double webGrenadeDamage = 1.0;
        public int webGrenadeCooldownTicks = 240;
        public double webGrenadeEnergyCost = 20.0;
        public int rageDurationTicks = 300;
        public int rageCooldownTicks = 1800;
        public int rageDebuffTicks = 200;
        public double rageEnergyCost = 15.0;
        public double rageSpeedMultiplier = 1.5;
        public double rageWebVelocityMultiplier = 1.5;
        public int diveMaxChain = 4;
        public int diveChainWindowTicks = 24;
        public double diveHitDamage = 6.0;
        public double diveFinisherDamage = 8.0;
        public int diveCooldownTicks = 240;
        public double diveEnergyCost = 20.0;

        void validate() {
            webSlingRange = ConfigMath.clamp(webSlingRange, 4.0, 96.0, 32.0);
            webSlingVelocity = ConfigMath.clamp(webSlingVelocity, 0.3, 5.0, 1.5);
            mlgFallThreshold = ConfigMath.clamp(mlgFallThreshold, 3, 64, 8);
            mlgSearchRadius = ConfigMath.clamp(mlgSearchRadius, 1, 8, 3);
            mlgCooldownTicks = ConfigMath.clamp(mlgCooldownTicks, 0, 1200, 40);
            webGrenadeRadius = ConfigMath.clamp(webGrenadeRadius, 2.0, 24.0, 6.0);
            webGrenadeDurationTicks = ConfigMath.clamp(webGrenadeDurationTicks, 20, 1200, 100);
            webGrenadeDamage = ConfigMath.clamp(webGrenadeDamage, 0.0, 20.0, 1.0);
            webGrenadeCooldownTicks = ConfigMath.clamp(webGrenadeCooldownTicks, 0, 12000, 240);
            webGrenadeEnergyCost = ConfigMath.clamp(webGrenadeEnergyCost, 0.0, 100000.0, 20.0);
            rageDurationTicks = ConfigMath.clamp(rageDurationTicks, 20, 6000, 300);
            rageCooldownTicks = ConfigMath.clamp(rageCooldownTicks, 0, 120000, 1800);
            rageDebuffTicks = ConfigMath.clamp(rageDebuffTicks, 0, 2400, 200);
            rageEnergyCost = ConfigMath.clamp(rageEnergyCost, 0.0, 100000.0, 15.0);
            rageSpeedMultiplier = ConfigMath.clamp(rageSpeedMultiplier, 1.0, 4.0, 1.5);
            rageWebVelocityMultiplier = ConfigMath.clamp(rageWebVelocityMultiplier, 1.0, 4.0, 1.5);
            diveMaxChain = ConfigMath.clamp(diveMaxChain, 1, 10, 4);
            diveChainWindowTicks = ConfigMath.clamp(diveChainWindowTicks, 5, 100, 24);
            diveHitDamage = ConfigMath.clamp(diveHitDamage, 0.0, 50.0, 6.0);
            diveFinisherDamage = ConfigMath.clamp(diveFinisherDamage, 0.0, 100.0, 8.0);
            diveCooldownTicks = ConfigMath.clamp(diveCooldownTicks, 0, 12000, 240);
            diveEnergyCost = ConfigMath.clamp(diveEnergyCost, 0.0, 100000.0, 20.0);
        }
    }

    public static final class SpiritChanneler {
        public double summonEnergyCost = 20.0;
        public int summonCooldownTicks = 200;
        public int spiritLifespanTicks = 1200;
        public double shadowStepRange = 16.0;
        public int shadowStepCooldownTicks = 200;
        public double shadowStepEnergyCost = 10.0;
        public int restlessShadowsDurationTicks = 600;
        public int restlessShadowsCooldownTicks = 3600;
        public double restlessShadowsEnergyCost = 75.0;
        public double restlessShadowsRadius = 12.0;
        public double restlessShadowsDamage = 6.0;
        public double restlessShadowsHealth = 40.0;
        public double restlessShadowsDarknessRadius = 16.0;
        public int restlessShadowsDarknessTicks = 100;

        void validate() {
            summonEnergyCost = ConfigMath.clamp(summonEnergyCost, 0.0, 100000.0, 20.0);
            summonCooldownTicks = ConfigMath.clamp(summonCooldownTicks, 0, 12000, 200);
            spiritLifespanTicks = ConfigMath.clamp(spiritLifespanTicks, 100, 72000, 1200);
            shadowStepRange = ConfigMath.clamp(shadowStepRange, 2.0, 64.0, 16.0);
            shadowStepCooldownTicks = ConfigMath.clamp(shadowStepCooldownTicks, 0, 12000, 200);
            shadowStepEnergyCost = ConfigMath.clamp(shadowStepEnergyCost, 0.0, 100000.0, 10.0);
            restlessShadowsDurationTicks = ConfigMath.clamp(restlessShadowsDurationTicks, 40, 6000, 600);
            restlessShadowsCooldownTicks = ConfigMath.clamp(restlessShadowsCooldownTicks, 0, 120000, 3600);
            restlessShadowsEnergyCost = ConfigMath.clamp(restlessShadowsEnergyCost, 0.0, 100000.0, 75.0);
            restlessShadowsRadius = ConfigMath.clamp(restlessShadowsRadius, 2.0, 48.0, 12.0);
            restlessShadowsDamage = ConfigMath.clamp(restlessShadowsDamage, 0.0, 50.0, 6.0);
            restlessShadowsHealth = ConfigMath.clamp(restlessShadowsHealth, 1.0, 1024.0, 40.0);
            restlessShadowsDarknessRadius = ConfigMath.clamp(restlessShadowsDarknessRadius, 0.0, 48.0, 16.0);
            restlessShadowsDarknessTicks = ConfigMath.clamp(restlessShadowsDarknessTicks, 20, 600, 100);
        }
    }

    public static final class Illusionist {
        public int spatialErasureCooldownTicks = 300;
        public int spatialErasureDurationTicks = 160;
        public double spatialErasureDamage = 10.0;
        public int spatialErasureSlownessTicks = 80;
        public double gripRange = 15.0;
        public double gripDefaultDistance = 5.0;
        public double gripThrowMultiplier = 3.0;
        public int gripReleaseCooldownTicks = 100;
        public int gripPassiveRadius = 10;
        public int celestialPullCooldownTicks = 3600;
        public double celestialPullRadius = 12.0;
        public int celestialPullDurationTicks = 80;
        public double celestialPullDamage = 20.0;
        public double celestialPullEnergyCost = 60.0;
        public double celestialMoonRadius = 5.0;
        public double celestialMoonHeight = 22.0;
        public double celestialMoonScale = 1.6;
        public int celestialMoonMaxBlocks = 480;
        public double celestialWeightlessRadius = 48.0;
        public double celestialCraterRadius = 16.0;
        public double celestialCraterDamage = 45.0;

        void validate() {
            spatialErasureCooldownTicks = ConfigMath.clamp(spatialErasureCooldownTicks, 0, 12000, 300);
            spatialErasureDurationTicks = ConfigMath.clamp(spatialErasureDurationTicks, 20, 2400, 160);
            spatialErasureDamage = ConfigMath.clamp(spatialErasureDamage, 0.0, 100.0, 10.0);
            spatialErasureSlownessTicks = ConfigMath.clamp(spatialErasureSlownessTicks, 0, 1200, 80);
            gripRange = ConfigMath.clamp(gripRange, 2.0, 48.0, 15.0);
            gripDefaultDistance = ConfigMath.clamp(gripDefaultDistance, 2.0, 10.0, 5.0);
            gripThrowMultiplier = ConfigMath.clamp(gripThrowMultiplier, 0.5, 10.0, 3.0);
            gripReleaseCooldownTicks = ConfigMath.clamp(gripReleaseCooldownTicks, 0, 12000, 100);
            gripPassiveRadius = ConfigMath.clamp(gripPassiveRadius, 0, 32, 10);
            celestialPullCooldownTicks = ConfigMath.clamp(celestialPullCooldownTicks, 0, 1200000, 3600);
            celestialPullRadius = ConfigMath.clamp(celestialPullRadius, 2.0, 48.0, 12.0);
            celestialPullDurationTicks = ConfigMath.clamp(celestialPullDurationTicks, 20, 600, 80);
            celestialPullDamage = ConfigMath.clamp(celestialPullDamage, 0.0, 200.0, 20.0);
            celestialPullEnergyCost = ConfigMath.clamp(celestialPullEnergyCost, 0.0, 100000.0, 60.0);
            celestialMoonRadius = ConfigMath.clamp(celestialMoonRadius, 2.0, 10.0, 5.0);
            celestialMoonHeight = ConfigMath.clamp(celestialMoonHeight, 10.0, 60.0, 22.0);
            celestialMoonScale = ConfigMath.clamp(celestialMoonScale, 0.5, 3.0, 1.6);
            celestialMoonMaxBlocks = ConfigMath.clamp(celestialMoonMaxBlocks, 100, 1500, 480);
            celestialWeightlessRadius = ConfigMath.clamp(celestialWeightlessRadius, 8.0, 128.0, 48.0);
            celestialCraterRadius = ConfigMath.clamp(celestialCraterRadius, 4.0, 40.0, 16.0);
            celestialCraterDamage = ConfigMath.clamp(celestialCraterDamage, 0.0, 500.0, 45.0);
        }
    }

    public static final class Kitsune {
        public int foxfireDurationTicks = 80;
        public double foxfireDamagePerSecond = 1.0;
        public int transformationDurationTicks = 600;
        public int transformationCooldownTicks = 2400;
        public double transformationSpeedMultiplier = 3.0;
        public double foxPlaceRange = 16.0;
        public double clawEnergyCost = 10.0;
        public double clawDamage = 8.0;
        public double clawRange = 4.0;
        public int clawCooldownTicks = 40;
        public double burstDamage = 6.0;
        public double burstSpeed = 1.4;
        public double burstEnergyCost = 15.0;
        public int burstCooldownTicks = 100;
        public double pounceEnergyCost = 15.0;
        public double pounceRange = 18.0;
        public double pounceDamage = 8.0;
        public double pounceKnockback = 1.4;
        public int pounceCooldownTicks = 120;
        public double detonationDamage = 10.0;
        public double detonationRadius = 6.0;
        public double detonationEnergyCost = 25.0;
        public int detonationCooldownTicks = 300;

        void validate() {
            foxfireDurationTicks = ConfigMath.clamp(foxfireDurationTicks, 20, 1200, 80);
            foxfireDamagePerSecond = ConfigMath.clamp(foxfireDamagePerSecond, 0.0, 20.0, 1.0);
            transformationDurationTicks = ConfigMath.clamp(transformationDurationTicks, 40, 6000, 600);
            transformationCooldownTicks = ConfigMath.clamp(transformationCooldownTicks, 0, 120000, 2400);
            transformationSpeedMultiplier = ConfigMath.clamp(transformationSpeedMultiplier, 1.0, 6.0, 3.0);
            foxPlaceRange = ConfigMath.clamp(foxPlaceRange, 4.0, 32.0, 16.0);
            clawEnergyCost = ConfigMath.clamp(clawEnergyCost, 0.0, 100000.0, 10.0);
            clawDamage = ConfigMath.clamp(clawDamage, 0.0, 50.0, 8.0);
            clawRange = ConfigMath.clamp(clawRange, 1.5, 12.0, 4.0);
            clawCooldownTicks = ConfigMath.clamp(clawCooldownTicks, 0, 1200, 40);
            burstDamage = ConfigMath.clamp(burstDamage, 0.0, 50.0, 6.0);
            burstSpeed = ConfigMath.clamp(burstSpeed, 0.3, 4.0, 1.4);
            burstEnergyCost = ConfigMath.clamp(burstEnergyCost, 0.0, 100000.0, 15.0);
            burstCooldownTicks = ConfigMath.clamp(burstCooldownTicks, 0, 1200, 100);
            pounceEnergyCost = ConfigMath.clamp(pounceEnergyCost, 0.0, 100000.0, 15.0);
            pounceRange = ConfigMath.clamp(pounceRange, 4.0, 48.0, 18.0);
            pounceDamage = ConfigMath.clamp(pounceDamage, 0.0, 50.0, 8.0);
            pounceKnockback = ConfigMath.clamp(pounceKnockback, 0.0, 6.0, 1.4);
            pounceCooldownTicks = ConfigMath.clamp(pounceCooldownTicks, 0, 1200, 120);
            detonationDamage = ConfigMath.clamp(detonationDamage, 0.0, 100.0, 10.0);
            detonationRadius = ConfigMath.clamp(detonationRadius, 1.0, 24.0, 6.0);
            detonationEnergyCost = ConfigMath.clamp(detonationEnergyCost, 0.0, 100000.0, 25.0);
            detonationCooldownTicks = ConfigMath.clamp(detonationCooldownTicks, 0, 12000, 300);
        }
    }
}
