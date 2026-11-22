package com.yppowers.config;

public final class GeneralConfig {
    public boolean globalHealthEnabled = true;
    public double globalPlayerHealth = 40.0;
    public double vfxParticleScale = 1.0;

    public void validate() {
        globalPlayerHealth = ConfigMath.clamp(globalPlayerHealth, 1.0, 2048.0, 40.0);
        vfxParticleScale = ConfigMath.clamp(vfxParticleScale, 0.0, 2.0, 1.0);
    }
}
