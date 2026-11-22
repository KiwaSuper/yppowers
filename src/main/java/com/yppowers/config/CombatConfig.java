package com.yppowers.config;

public final class CombatConfig {
    public boolean enabled = true;
    public int durationSeconds = 30;
    public boolean killOnLogout = true;
    public boolean blockAllianceChangesInCombat = true;
    public boolean broadcastCombatLog = true;

    public void validate() {
        durationSeconds = ConfigMath.clamp(durationSeconds, 5, 300, 30);
    }

    public int durationTicks() {
        return durationSeconds * 20;
    }
}
