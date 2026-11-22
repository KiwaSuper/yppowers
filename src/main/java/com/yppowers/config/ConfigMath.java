package com.yppowers.config;

final class ConfigMath {
    private ConfigMath() {}

    static double clamp(double value, double min, double max, double fallback) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    static int clamp(int value, int min, int max, int fallback) {
        return Math.max(min, Math.min(max, value));
    }
}
