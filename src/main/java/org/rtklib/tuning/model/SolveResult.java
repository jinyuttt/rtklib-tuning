package org.rtklib.tuning.model;

public class SolveResult {

    private long timestamp;
    private double latitude;
    private double longitude;
    private double height;
    private int fixMode;
    private int satCount;
    private double pdop;
    private double hdop;
    private double vdop;
    private double age;
    private double ratio;

    public SolveResult() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public int getFixMode() { return fixMode; }
    public void setFixMode(int fixMode) { this.fixMode = fixMode; }

    public int getSatCount() { return satCount; }
    public void setSatCount(int satCount) { this.satCount = satCount; }

    public double getPdop() { return pdop; }
    public void setPdop(double pdop) { this.pdop = pdop; }

    public double getHdop() { return hdop; }
    public void setHdop(double hdop) { this.hdop = hdop; }

    public double getVdop() { return vdop; }
    public void setVdop(double vdop) { this.vdop = vdop; }

    public double getAge() { return age; }
    public void setAge(double age) { this.age = age; }

    public double getRatio() { return ratio; }
    public void setRatio(double ratio) { this.ratio = ratio; }

    public String getFixModeName() {
        return switch (fixMode) {
            case 0 -> "None";
            case 1 -> "Fix";
            case 2 -> "Float";
            case 3 -> "SBAS";
            case 4 -> "DGPS";
            case 5 -> "Single";
            case 6 -> "PPP";
            case 7 -> "DR";
            default -> "Unknown(" + fixMode + ")";
        };
    }
}