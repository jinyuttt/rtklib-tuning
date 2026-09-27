package org.rtklib.tuning.controller;

import org.rtklib.tuning.entity.*;
import org.rtklib.tuning.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final BaseStationRepository baseStationRepo;
    private final StationRepository stationRepo;
    private final TuningConfigRepository configRepo;
    private final ParamPresetRepository presetRepo;

    public ConfigController(BaseStationRepository baseStationRepo, StationRepository stationRepo,
                            TuningConfigRepository configRepo, ParamPresetRepository presetRepo) {
        this.baseStationRepo = baseStationRepo;
        this.stationRepo = stationRepo;
        this.configRepo = configRepo;
        this.presetRepo = presetRepo;
    }

    @GetMapping("/basestations")
    public List<BaseStation> listBaseStations() {
        return baseStationRepo.findByEnabledTrueOrderByName();
    }

    @PostMapping("/basestations")
    public BaseStation addBaseStation(@RequestBody BaseStation bs) {
        return baseStationRepo.save(bs);
    }

    @DeleteMapping("/basestations/{id}")
    public Map<String, Boolean> deleteBaseStation(@PathVariable Long id) {
        baseStationRepo.deleteById(id);
        return Map.of("success", true);
    }

    @GetMapping("/basestations/{bsId}/stations")
    public List<Station> listStations(@PathVariable Long bsId) {
        return stationRepo.findByBaseStationIdOrderByName(bsId);
    }

    @PostMapping("/basestations/{bsId}/stations")
    public Station addStation(@PathVariable Long bsId, @RequestBody Station st) {
        BaseStation bs = baseStationRepo.findById(bsId).orElseThrow();
        st.setBaseStation(bs);
        return stationRepo.save(st);
    }

    @DeleteMapping("/stations/{id}")
    public Map<String, Boolean> deleteStation(@PathVariable Long id) {
        stationRepo.deleteById(id);
        return Map.of("success", true);
    }

    @GetMapping("/stations/{stId}/tuning")
    public TuningConfigEntity getTuningConfig(@PathVariable Long stId) {
        return configRepo.findByStationId(stId)
            .orElseGet(() -> {
                Station st = stationRepo.findById(stId).orElseThrow();
                TuningConfigEntity cfg = new TuningConfigEntity(st);
                return configRepo.save(cfg);
            });
    }

    @PutMapping("/stations/{stId}/tuning")
    public TuningConfigEntity saveTuningConfig(@PathVariable Long stId, @RequestBody TuningConfigEntity cfg) {
        Station st = stationRepo.findById(stId).orElseThrow();
        TuningConfigEntity existing = configRepo.findByStationId(stId)
            .orElse(new TuningConfigEntity(st));
        existing.setPositioningMode(cfg.getPositioningMode());
        existing.setElevationMask(cfg.getElevationMask());
        existing.setSnrMask(cfg.getSnrMask());
        existing.setDynamicModel(cfg.getDynamicModel());
        existing.setIonosphereCorrection(cfg.getIonosphereCorrection());
        existing.setTroposphereCorrection(cfg.getTroposphereCorrection());
        existing.setMaxAge(cfg.getMaxAge());
        existing.setRejIonno(cfg.getRejIonno());
        existing.setExclSats(cfg.getExclSats());
        existing.setEnableSystems(cfg.getEnableSystems());
        existing.setEnableFrequencies(cfg.getEnableFrequencies());
        return configRepo.save(existing);
    }

    @GetMapping("/presets")
    public List<ParamPresetEntity> listPresets() {
        return presetRepo.findAll();
    }

    @PostMapping("/presets")
    public ParamPresetEntity addPreset(@RequestBody ParamPresetEntity preset) {
        return presetRepo.save(preset);
    }
}