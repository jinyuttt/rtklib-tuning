 package org.rtklib.tuning.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tuning_config")
public class TuningConfigEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "positioning_mode")
    private Integer positioningMode = 0;

    @Column(name = "elevation_mask")
    private Integer elevationMask = 15;

    @Column(name = "snr_mask")
    private Integer snrMask = 0;

    @Column(name = "dynamic_model")
    private Integer dynamicModel = 0;

    @Column(name = "ionosphere_correction")
    private Integer ionosphereCorrection = 1;

    @Column(name = "troposphere_correction")
    private Integer troposphereCorrection = 1;

    @Column(name = "max_age")
    private Integer maxAge = 30;

    @Column(name = "rej_ionno")
    private Integer rejIonno = 30;

    @Column(name = "excl_sats", length = 500)
    private String exclSats;

    @Column(name = "enable_systems", length = 200)
    private String enableSystems;

    @Column(name = "enable_frequencies", length = 200)
    private String enableFrequencies;

    public TuningConfigEntity() {}

    public TuningConfigEntity(Station station) { this.station = station; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }

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