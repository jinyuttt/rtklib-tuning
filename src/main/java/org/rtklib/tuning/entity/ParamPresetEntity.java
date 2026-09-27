package org.rtklib.tuning.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "param_preset")
public class ParamPresetEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 1000)
    private String description;

    private Integer positioningMode;
    private Integer elevationMask;
    private Integer snrMask;
    private Integer dynamicModel;
    private Integer ionosphereCorrection;
    private Integer troposphereCorrection;
    private Integer maxAge;
    private Integer rejIonno;

    @Column(length = 500)
    private String exclSats;

    @Column(length = 200)
    private String enableSystems;

    @Column(length = 200)
    private String enableFrequencies;

    public ParamPresetEntity() {}

    public ParamPresetEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getPositioningMode() { return positioningMode; }
    public void setPositioningMode(Integer positioningMode) { this.positioningMode = positioningMode; }

    public Integer getElevationMask() { return elevationMask; }
    public void setElevationMask(Integer elevationMask) { this.elevationMask = elevationMask; }

    public Integer getSnrMask() { return snrMask; }
    public void setSnrMask(Integer snrMask) { this.snrMask = snrMask; }

    public Integer getDynamicModel() { return dynamicModel; }
    public void setDynamicModel(Integer dynamicModel) { this.dynamicModel = dynamicModel; }

    public Integer getIonosphereCorrection() { return ionosphereCorrection; }
    public void setIonosphereCorrection(Integer ionosphereCorrection) { this.ionosphereCorrection = ionosphereCorrection; }

    public Integer getTroposphereCorrection() { return troposphereCorrection; }
    public void setTroposphereCorrection(Integer troposphereCorrection) { this.troposphereCorrection = troposphereCorrection; }

    public Integer getMaxAge() { return maxAge; }
    public void setMaxAge(Integer maxAge) { this.maxAge = maxAge; }

    public Integer getRejIonno() { return rejIonno; }
    public void setRejIonno(Integer rejIonno) { this.rejIonno = rejIonno; }

    public String getExclSats() { return exclSats; }
    public void setExclSats(String exclSats) { this.exclSats = exclSats; }

    public String getEnableSystems() { return enableSystems; }
    public void setEnableSystems(String enableSystems) { this.enableSystems = enableSystems; }

    public String getEnableFrequencies() { return enableFrequencies; }
    public void setEnableFrequencies(String enableFrequencies) { this.enableFrequencies = enableFrequencies; }
}