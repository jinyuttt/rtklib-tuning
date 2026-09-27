package org.rtklib.tuning.dto;

import java.util.List;
import java.util.Map;

public class EpochDataDto {
    private long timestamp;
    private double diffAge;
    private int satCount;
    private double gdop;
    private double hdop;
    private double vdop;
    private Map<String, Double> snrData;
    private Map<String, Double> elevationData;
    private Map<String, Boolean> filteredSats;

    public EpochDataDto() {}

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getDiffAge() { return diffAge; }
    public void setDiffAge(double diffAge) { this.diffAge = diffAge; }

    public int getSatCount() { return satCount; }
    public void setSatCount(int satCount) { this.satCount = satCount; }

    public double getGdop() { return gdop; }
    public void setGdop(double gdop) { this.gdop = gdop; }

    public double getHdop() { return hdop; }
    public void setHdop(double hdop) { this.hdop = hdop; }

    public double getVdop() { return vdop; }
    public void setVdop(double vdop) { this.vdop = vdop; }

    public Map<String, Double> getSnrData() { return snrData; }
    public void setSnrData(Map<String, Double> snrData) { this.snrData = snrData; }

    public Map<String, Double> getElevationData() { return elevationData; }
    public void setElevationData(Map<String, Double> elevationData) { this.elevationData = elevationData; }

    public Map<String, Boolean> getFilteredSats() { return filteredSats; }
    public void setFilteredSats(Map<String, Boolean> filteredSats) { this.filteredSats = filteredSats; }
}