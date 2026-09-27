package org.rtklib.tuning.dto;

public class SolutionEpochDto {
    private long timestamp;
    private int fixMode;
    private double latitude;
    private double longitude;
    private double height;
    private double ratio;
    private double northStd;
    private double eastStd;
    private double upStd;
    private double diffAge;

    public SolutionEpochDto() {}

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getFixMode() { return fixMode; }
    public void setFixMode(int fixMode) { this.fixMode = fixMode; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public double getRatio() { return ratio; }
    public void setRatio(double ratio) { this.ratio = ratio; }

    public double getNorthStd() { return northStd; }
    public void setNorthStd(double northStd) { this.northStd = northStd; }

    public double getEastStd() { return eastStd; }
    public void setEastStd(double eastStd) { this.eastStd = eastStd; }

    public double getUpStd() { return upStd; }
    public void setUpStd(double upStd) { this.upStd = upStd; }

    public double getDiffAge() { return diffAge; }
    public void setDiffAge(double diffAge) { this.diffAge = diffAge; }
}