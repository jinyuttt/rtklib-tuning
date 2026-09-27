package org.rtklib.tuning.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "base_station")
public class BaseStation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    private Double latitude;
    private Double longitude;
    private Double height;

    @Column(name = "correction_source")
    private String correctionSource;

    @Column(name = "connection_params", length = 2000)
    private String connectionParams;

    @Column(nullable = false)
    private Boolean enabled = true;

    public BaseStation() {}

    public BaseStation(String name) { this.name = name; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public String getCorrectionSource() { return correctionSource; }
    public void setCorrectionSource(String correctionSource) { this.correctionSource = correctionSource; }

    public String getConnectionParams() { return connectionParams; }
    public void setConnectionParams(String connectionParams) { this.connectionParams = connectionParams; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}