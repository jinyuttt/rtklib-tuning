package org.rtklib.tuning.model;

import java.util.List;

public class TuningConfig {

    private String roverPort;
    private int roverBaudRate;
    private String baseSource;
    private String baseMountpoint;
    private String ntripHost;
    private int ntripPort;
    private String ntripUser;
    private String ntripPassword;

    private int positioningMode;
    private int solutionFormat;
    private int elevationMask;
    private int snrMask;
    private int ionosphereCorrection;
    private int troposphereCorrection;
    private int dynamicModel;
    private double baselineLength;

    private List<String> enableSystems;
    private List<String> enableFrequencies;

    public TuningConfig() {
        this.roverBaudRate = 115200;
        this.ntripPort = 2101;
        this.positioningMode = 0;
        this.solutionFormat = 0;
        this.elevationMask = 15;
        this.snrMask = 0;
        this.ionosphereCorrection = 1;
        this.troposphereCorrection = 1;
        this.dynamicModel = 0;
        this.baselineLength = 0.0;
    }

    public String getRoverPort() { return roverPort; }
    public void setRoverPort(String roverPort) { this.roverPort = roverPort; }

    public int getRoverBaudRate() { return roverBaudRate; }
    public void setRoverBaudRate(int roverBaudRate) { this.roverBaudRate = roverBaudRate; }

    public String getBaseSource() { return baseSource; }
    public void setBaseSource(String baseSource) { this.baseSource = baseSource; }

    public String getBaseMountpoint() { return baseMountpoint; }
    public void setBaseMountpoint(String baseMountpoint) { this.baseMountpoint = baseMountpoint; }

    public String getNtripHost() { return ntripHost; }
    public void setNtripHost(String ntripHost) { this.ntripHost = ntripHost; }

    public int getNtripPort() { return ntripPort; }
    public void setNtripPort(int ntripPort) { this.ntripPort = ntripPort; }

    public String getNtripUser() { return ntripUser; }
    public void setNtripUser(String ntripUser) { this.ntripUser = ntripUser; }

    public String getNtripPassword() { return ntripPassword; }
    public void setNtripPassword(String ntripPassword) { this.ntripPassword = ntripPassword; }

    public int getPositioningMode() { return positioningMode; }
    public void setPositioningMode(int positioningMode) { this.positioningMode = positioningMode; }

    public int getSolutionFormat() { return solutionFormat; }
    public void setSolutionFormat(int solutionFormat) { this.solutionFormat = solutionFormat; }

    public int getElevationMask() { return elevationMask; }
    public void setElevationMask(int elevationMask) { this.elevationMask = elevationMask; }

    public int getSnrMask() { return snrMask; }
    public void setSnrMask(int snrMask) { this.snrMask = snrMask; }

    public int getIonosphereCorrection() { return ionosphereCorrection; }
    public void setIonosphereCorrection(int ionosphereCorrection) { this.ionosphereCorrection = ionosphereCorrection; }

    public int getTroposphereCorrection() { return troposphereCorrection; }
    public void setTroposphereCorrection(int troposphereCorrection) { this.troposphereCorrection = troposphereCorrection; }

    public int getDynamicModel() { return dynamicModel; }
    public void setDynamicModel(int dynamicModel) { this.dynamicModel = dynamicModel; }

    public double getBaselineLength() { return baselineLength; }
    public void setBaselineLength(double baselineLength) { this.baselineLength = baselineLength; }

    public List<String> getEnableSystems() { return enableSystems; }
    public void setEnableSystems(List<String> enableSystems) { this.enableSystems = enableSystems; }

    public List<String> getEnableFrequencies() { return enableFrequencies; }
    public void setEnableFrequencies(List<String> enableFrequencies) { this.enableFrequencies = enableFrequencies; }
}