package org.rtklib.tuning.model;

public class StationMapping {

    private String stationId;
    private String mountpoint;
    private double latitude;
    private double longitude;
    private double height;
    private String format;

    public StationMapping() {}

    public StationMapping(String stationId, String mountpoint, double lat, double lon, double height) {
        this.stationId = stationId;
        this.mountpoint = mountpoint;
        this.latitude = lat;
        this.longitude = lon;
        this.height = height;
        this.format = "RTCM3";
    }

    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }

    public String getMountpoint() { return mountpoint; }
    public void setMountpoint(String mountpoint) { this.mountpoint = mountpoint; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}