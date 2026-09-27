package org.rtklib.tuning.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "station")
public class Station {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_station_id", nullable = false)
    private BaseStation baseStation;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "data_source")
    private String dataSource;

    @Column(name = "data_path", length = 1000)
    private String dataPath;

    private Double latitude;
    private Double longitude;
    private Double height;

    @Column(nullable = false)
    private Boolean enabled = true;

    public Station() {}

    public Station(String name, BaseStation baseStation) {
        this.name = name;
        this.baseStation = baseStation;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BaseStation getBaseStation() { return baseStation; }
    public void setBaseStation(BaseStation baseStation) { this.baseStation = baseStation; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public String getDataPath() { return dataPath; }
    public void setDataPath(String dataPath) { this.dataPath = dataPath; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}