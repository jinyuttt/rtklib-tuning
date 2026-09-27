package org.rtklib.tuning.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeviceInfo {

    private String portName;
    private String description;
    private String manufacturer;
    private int baudRate;
    private boolean isOpen;

    public DeviceInfo() {
        this.baudRate = 115200;
        this.isOpen = false;
    }

    public DeviceInfo(String portName, String description) {
        this();
        this.portName = portName;
        this.description = description;
    }

    public String getPortName() { return portName; }
    public void setPortName(String portName) { this.portName = portName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public int getBaudRate() { return baudRate; }
    public void setBaudRate(int baudRate) { this.baudRate = baudRate; }

    public boolean isOpen() { return isOpen; }
    public void setOpen(boolean open) { this.isOpen = open; }
}