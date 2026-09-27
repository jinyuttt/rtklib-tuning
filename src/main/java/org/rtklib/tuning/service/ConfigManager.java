package org.rtklib.tuning.service;

import org.rtklib.tuning.model.ParamPreset;
import org.rtklib.tuning.model.SolveResult;
import org.rtklib.tuning.model.TuningConfig;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class ConfigManager {

    private TuningConfig config = new TuningConfig();
    private String rootDir = System.getProperty("user.home");
    private final List<ParamPreset> presets = new ArrayList<>();
    private final List<Consumer<SolveResult>> resultListeners = new CopyOnWriteArrayList<>();

    public ConfigManager() {
        initDefaultPresets();
    }

    public TuningConfig getConfig() {
        return config;
    }

    public void updateConfig(TuningConfig newConfig) {
        this.config = newConfig;
    }

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }

    public List<ParamPreset> getPresets() {
        return Collections.unmodifiableList(presets);
    }

    public void applyPreset(String presetName) {
        presets.stream()
            .filter(p -> p.getName().equals(presetName))
            .findFirst()
            .ifPresent(preset -> applyParams(preset.getParams()));
    }

    @SuppressWarnings("unchecked")
    private void applyParams(Map<String, Object> params) {
        if (params.containsKey("positioningMode")) {
            config.setPositioningMode(((Number) params.get("positioningMode")).intValue());
        }
        if (params.containsKey("elevationMask")) {
            config.setElevationMask(((Number) params.get("elevationMask")).intValue());
        }
        if (params.containsKey("snrMask")) {
            config.setSnrMask(((Number) params.get("snrMask")).intValue());
        }
        if (params.containsKey("ionosphereCorrection")) {
            config.setIonosphereCorrection(((Number) params.get("ionosphereCorrection")).intValue());
        }
        if (params.containsKey("troposphereCorrection")) {
            config.setTroposphereCorrection(((Number) params.get("troposphereCorrection")).intValue());
        }
        if (params.containsKey("dynamicModel")) {
            config.setDynamicModel(((Number) params.get("dynamicModel")).intValue());
        }
        if (params.containsKey("enableSystems")) {
            config.setEnableSystems((List<String>) params.get("enableSystems"));
        }
        if (params.containsKey("enableFrequencies")) {
            config.setEnableFrequencies((List<String>) params.get("enableFrequencies"));
        }
    }

    public void addResultListener(Consumer<SolveResult> listener) {
        resultListeners.add(listener);
    }

    public void removeResultListener(Consumer<SolveResult> listener) {
        resultListeners.remove(listener);
    }

    public void publishResult(SolveResult result) {
        for (Consumer<SolveResult> listener : resultListeners) {
            try {
                listener.accept(result);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void initDefaultPresets() {
        presets.add(new ParamPreset("RTK Quick", "Fast RTK fix, suitable for short baseline",
            Map.of(
                "positioningMode", 0,
                "elevationMask", 15,
                "ionosphereCorrection", 1,
                "troposphereCorrection", 1,
                "dynamicModel", 0,
                "enableSystems", List.of("GPS", "BDS"),
                "enableFrequencies", List.of("L1")
            )));

        presets.add(new ParamPreset("RTK Precise", "High precision, multi-frequency",
            Map.of(
                "positioningMode", 0,
                "elevationMask", 10,
                "ionosphereCorrection", 1,
                "troposphereCorrection", 1,
                "dynamicModel", 0,
                "enableSystems", List.of("GPS", "BDS", "GLO"),
                "enableFrequencies", List.of("L1", "L2")
            )));

        presets.add(new ParamPreset("PPP", "Precise Point Positioning",
            Map.of(
                "positioningMode", 3,
                "elevationMask", 10,
                "ionosphereCorrection", 2,
                "troposphereCorrection", 2,
                "dynamicModel", 0,
                "enableSystems", List.of("GPS", "GLO"),
                "enableFrequencies", List.of("L1", "L2")
            )));

        presets.add(new ParamPreset("DGPS", "Differential GPS, robust",
            Map.of(
                "positioningMode", 1,
                "elevationMask", 15,
                "ionosphereCorrection", 0,
                "troposphereCorrection", 1,
                "dynamicModel", 0,
                "enableSystems", List.of("GPS"),
                "enableFrequencies", List.of("L1")
            )));

        presets.add(new ParamPreset("Static Survey", "Long session static positioning",
            Map.of(
                "positioningMode", 0,
                "elevationMask", 10,
                "ionosphereCorrection", 1,
                "troposphereCorrection", 1,
                "dynamicModel", 1,
                "enableSystems", List.of("GPS", "BDS", "GLO", "GAL"),
                "enableFrequencies", List.of("L1", "L2")
            )));
    }
}