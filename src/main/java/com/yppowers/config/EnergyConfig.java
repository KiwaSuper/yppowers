package com.yppowers.config;

public final class EnergyConfig {
    public double maxEnergy = 100.0;
    public double regenPerSecond = 5.0;
    public double defaultPrimaryCost = 10.0;
    public double defaultSecondaryCost = 15.0;
    public double defaultTertiaryCost = 20.0;
    public double defaultQuaternaryCost = 20.0;
    public double defaultAlternateCost = 5.0;
    public int bleedingInitialTicks = 80;
    public int bleedingMaxTicks = 160;
    public double bleedingDamagePerSecond = 1.0;

    public void validate() {
        maxEnergy = ConfigMath.clamp(maxEnergy, 1.0, 100000.0, 100.0);
        regenPerSecond = ConfigMath.clamp(regenPerSecond, 0.0, 1000.0, 5.0);
        defaultPrimaryCost = ConfigMath.clamp(defaultPrimaryCost, 0.0, 100000.0, 10.0);
        defaultSecondaryCost = ConfigMath.clamp(defaultSecondaryCost, 0.0, 100000.0, 15.0);
        defaultTertiaryCost = ConfigMath.clamp(defaultTertiaryCost, 0.0, 100000.0, 20.0);
        defaultQuaternaryCost = ConfigMath.clamp(defaultQuaternaryCost, 0.0, 100000.0, 20.0);
        defaultAlternateCost = ConfigMath.clamp(defaultAlternateCost, 0.0, 100000.0, 5.0);
        bleedingInitialTicks = ConfigMath.clamp(bleedingInitialTicks, 1, 12000, 80);
        bleedingMaxTicks = ConfigMath.clamp(bleedingMaxTicks, bleedingInitialTicks, 12000, 160);
        bleedingDamagePerSecond = ConfigMath.clamp(bleedingDamagePerSecond, 0.0, 100.0, 1.0);
    }
}
