package org.rtklib.tuning.dto;

import java.util.List;

public class RtcmMessageDto {
    private long timestamp;
    private int messageType;
    private int sequenceNumber;
    private int dataLength;
    private String description;

    public RtcmMessageDto() {}

    public RtcmMessageDto(long timestamp, int messageType, int sequenceNumber, int dataLength) {
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.sequenceNumber = sequenceNumber;
        this.dataLength = dataLength;
        this.description = typeName(messageType);
    }

    public static String typeName(int type) {
        return switch (type) {
            case 1001 -> "L1 GPS Obs";
            case 1002 -> "L1 GPS Obs (ext)";
            case 1003 -> "L1/L2 GPS Obs";
            case 1004 -> "L1/L2 GPS Obs (ext)";
            case 1005 -> "Station XYZ";
            case 1006 -> "Station XYZ+Height";
            case 1007 -> "Antenna Descriptor";
            case 1008 -> "Antenna Descriptor+Serial";
            case 1009 -> "L1 GLONASS Obs";
            case 1010 -> "L1 GLONASS Obs (ext)";
            case 1011 -> "L1/L2 GLONASS Obs";
            case 1012 -> "L1/L2 GLONASS Obs (ext)";
            case 1013 -> "System Parameters";
            case 1019 -> "GPS Ephemeris";
            case 1020 -> "GLONASS Ephemeris";
            case 1029 -> "Unicode Text";
            case 1033 -> "Receiver+Antenna";
            case 1042 -> "BDS Ephemeris";
            case 1044 -> "QZSS Ephemeris";
            case 1045 -> "Galileo F/NAV Ephemeris";
            case 1046 -> "Galileo I/NAV Ephemeris";
            case 1074 -> "GPS MSM4";
            case 1075 -> "GPS MSM5";
            case 1077 -> "GPS MSM7";
            case 1084 -> "GLONASS MSM4";
            case 1085 -> "GLONASS MSM5";
            case 1087 -> "GLONASS MSM7";
            case 1094 -> "Galileo MSM4";
            case 1095 -> "Galileo MSM5";
            case 1097 -> "Galileo MSM7";
            case 1124 -> "BDS MSM4";
            case 1125 -> "BDS MSM5";
            case 1127 -> "BDS MSM7";
            case 1230 -> "GLONASS Code+Phase Bias";
            default -> "RTCM-" + type;
        };
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getMessageType() { return messageType; }
    public void setMessageType(int messageType) { this.messageType = messageType; }

    public int getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public int getDataLength() { return dataLength; }
    public void setDataLength(int dataLength) { this.dataLength = dataLength; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}