package org.rtklib.tuning.model;

import java.util.Map;

public class SolveRequest {

    private String mode;
    private String baseFile;
    private String roverFile;
    private String navFile;
    private String sp3File;
    private String clkFile;
    private String rootDir;
    private String baseStationId;
    private String roverStationId;
    private String date;
    private int startHour;
    private int endHour;

    private Map<String, Object> params;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getBaseFile() { return baseFile; }
    public void setBaseFile(String baseFile) { this.baseFile = baseFile; }

    public String getRoverFile() { return roverFile; }
    public void setRoverFile(String roverFile) { this.roverFile = roverFile; }

    public String getNavFile() { return navFile; }
    public void setNavFile(String navFile) { this.navFile = navFile; }

    public String getSp3File() { return sp3File; }
    public void setSp3File(String sp3File) { this.sp3File = sp3File; }

    public String getClkFile() { return clkFile; }
    public void setClkFile(String clkFile) { this.clkFile = clkFile; }

    public String getRootDir() { return rootDir; }
    public void setRootDir(String rootDir) { this.rootDir = rootDir; }

    public String getBaseStationId() { return baseStationId; }
    public void setBaseStationId(String baseStationId) { this.baseStationId = baseStationId; }

    public String getRoverStationId() { return roverStationId; }
    public void setRoverStationId(String roverStationId) { this.roverStationId = roverStationId; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public int getStartHour() { return startHour; }
    public void setStartHour(int startHour) { this.startHour = startHour; }

    public int getEndHour() { return endHour; }
    public void setEndHour(int endHour) { this.endHour = endHour; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}