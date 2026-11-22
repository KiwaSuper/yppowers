package com.yppowers.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yppowers.YPPowersMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Clean structured JSON configuration saved at {@code config/yppowers.json}.
 * Supports hot-reloading at runtime via {@code /yp config reload}.
 */
public final class YPConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile YPConfig instance = new YPConfig();

    public int schemaVersion = 2;
    public GeneralConfig general = new GeneralConfig();
    public EnergyConfig energy = new EnergyConfig();
    public HudConfig hud = new HudConfig();
    public PowerConfigs powers = new PowerConfigs();

    public static YPConfig get() {
        return instance;
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("yppowers.json");
    }

    public static synchronized YPConfig load() {
        return loadFrom(defaultPath());
    }

    public static synchronized YPConfig loadFrom(Path path) {
        YPConfig loaded = new YPConfig();
        if (Files.isRegularFile(path)) {
            try {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                YPConfig parsed = GSON.fromJson(json, YPConfig.class);
                if (parsed != null) loaded = parsed;
            } catch (IOException | RuntimeException ex) {
                YPPowersMod.LOGGER.warn("Could not read config at {}, falling back to defaults: {}", path, ex.getMessage());
            }
        }
        loaded.schemaVersion = 2;
        loaded.validate();
        instance = loaded;
        saveTo(loaded, path);
        return loaded;
    }

    public static synchronized void save() {
        saveTo(instance, defaultPath());
    }

    public static synchronized void saveTo(YPConfig config, Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(path, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            YPPowersMod.LOGGER.warn("Could not write config at {}: {}", path, ex.getMessage());
        }
    }

    public static synchronized void setForTests(YPConfig config) {
        config.validate();
        instance = config;
    }

    public void validate() {
        if (general == null) general = new GeneralConfig();
        if (energy == null) energy = new EnergyConfig();
        if (hud == null) hud = new HudConfig();
        if (powers == null) powers = new PowerConfigs();
        general.validate();
        energy.validate();
        hud.validate();
        powers.validate();
    }
}
