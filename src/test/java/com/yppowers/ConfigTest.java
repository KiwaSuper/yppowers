package com.yppowers;

import com.yppowers.config.YPConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @Test
    void validatesAndClampsInvalidValues() {
        YPConfig cfg = new YPConfig();
        cfg.energy.maxEnergy = -50.0;
        cfg.energy.regenPerSecond = Double.NaN;
        cfg.powers.goat.sprintStartMultiplier = 99.0;
        cfg.powers.goat.maxSpeed = 5.0;
        cfg.powers.poseidon.waterCondition = "invalid_mode";
        cfg.powers.iceEmperor.defaultForm = "unknown";

        cfg.validate();

        assertEquals(1.0, cfg.energy.maxEnergy);
        assertEquals(5.0, cfg.energy.regenPerSecond);
        assertEquals(5.0, cfg.powers.goat.sprintStartMultiplier);
        assertEquals("TOUCHING", cfg.powers.poseidon.waterCondition);
        assertEquals("ARMOURED", cfg.powers.iceEmperor.defaultForm);
    }

    @Test
    void jsonRoundTripPreservesCustomValues(@TempDir Path tempDir) {
        Path file = tempDir.resolve("yppowers.json");
        YPConfig original = new YPConfig();
        original.general.globalPlayerHealth = 60.0;
        original.energy.maxEnergy = 150.0;
        original.powers.thor.riptideStrength = 4.5;
        original.powers.timeWarper.cloneShardsCost = 7;

        YPConfig.saveTo(original, file);
        YPConfig reloaded = YPConfig.loadFrom(file);

        assertEquals(60.0, reloaded.general.globalPlayerHealth);
        assertEquals(150.0, reloaded.energy.maxEnergy);
        assertEquals(4.5, reloaded.powers.thor.riptideStrength);
        assertEquals(7, reloaded.powers.timeWarper.cloneShardsCost);
    }
}
