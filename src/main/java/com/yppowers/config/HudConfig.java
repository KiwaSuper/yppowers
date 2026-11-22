package com.yppowers.config;

import java.util.Locale;
import java.util.Set;

public final class HudConfig {
    public boolean showEnergyBar = true;
    public boolean showPowerPanel = true;
    /** Supported anchors: TOP_LEFT, TOP_RIGHT, TOP_CENTER, BOTTOM_LEFT, BOTTOM_RIGHT. */
    public String anchor = "TOP_LEFT";
    public int offsetX = 8;
    public int offsetY = 8;
    public double energyLowThresholdPercent = 0.25;

    public void validate() {
        energyLowThresholdPercent = ConfigMath.clamp(energyLowThresholdPercent, 0.0, 1.0, 0.25);
        offsetX = ConfigMath.clamp(offsetX, 0, 2000, 8);
        offsetY = ConfigMath.clamp(offsetY, 0, 2000, 8);
        if (anchor == null) anchor = "TOP_LEFT";
        anchor = anchor.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TOP_LEFT", "TOP_RIGHT", "TOP_CENTER", "BOTTOM_LEFT", "BOTTOM_RIGHT").contains(anchor)) {
            anchor = "TOP_LEFT";
        }
    }
}
