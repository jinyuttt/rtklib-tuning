package org.rtklib.tuning.controller;

import org.rtklib.tuning.model.ParamPreset;
import org.rtklib.tuning.model.TuningConfig;
import org.rtklib.tuning.service.ConfigManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/param")
public class ParamController {

    private final ConfigManager configManager;

    public ParamController(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @GetMapping("/config")
    public TuningConfig getConfig() {
        return configManager.getConfig();
    }

    @PutMapping("/config")
    public TuningConfig updateConfig(@RequestBody TuningConfig config) {
        configManager.updateConfig(config);
        return configManager.getConfig();
    }

    @GetMapping("/presets")
    public List<ParamPreset> getPresets() {
        return configManager.getPresets();
    }

    @PostMapping("/preset/{name}")
    public Map<String, Object> applyPreset(@PathVariable String name) {
        configManager.applyPreset(name);
        return Map.of("applied", name, "config", configManager.getConfig());
    }
}