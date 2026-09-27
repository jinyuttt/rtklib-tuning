package org.rtklib.tuning.dto;

import java.util.List;
import java.util.Map;

public class JumpEventDto {
    private long timestamp;
    private int fromMode;
    private int toMode;
    private String fromModeName;
    private String toModeName;
    private double coordinateJump;
    private String attribution;
    private String suggestion;

    private List<EpochDataDto> contextBefore;
    private List<EpochDataDto> contextAfter;
    private List<SolutionEpochDto> solutionContext;
    private List<RtcmMessageDto> rtcmContext;

    public JumpEventDto() {}

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getFromMode() { return fromMode; }
    public void setFromMode(int fromMode) {
        this.fromMode = fromMode;
        this.fromModeName = fixName(fromMode);
    }

    public int getToMode() { return toMode; }
    public void setToMode(int toMode) {
        this.toMode = toMode;
        this.toModeName = fixName(toMode);
    }

    public String getFromModeName() { return fromModeName; }
    public String getToModeName() { return toModeName; }

    public double getCoordinateJump() { return coordinateJump; }
    public void setCoordinateJump(double coordinateJump) { this.coordinateJump = coordinateJump; }

    public String getAttribution() { return attribution; }
    public void setAttribution(String attribution) { this.attribution = attribution; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public List<EpochDataDto> getContextBefore() { return contextBefore; }
    public void setContextBefore(List<EpochDataDto> contextBefore) { this.contextBefore = contextBefore; }

    public List<EpochDataDto> getContextAfter() { return contextAfter; }
    public void setContextAfter(List<EpochDataDto> contextAfter) { this.contextAfter = contextAfter; }

    public List<SolutionEpochDto> getSolutionContext() { return solutionContext; }
    public void setSolutionContext(List<SolutionEpochDto> solutionContext) { this.solutionContext = solutionContext; }

    public List<RtcmMessageDto> getRtcmContext() { return rtcmContext; }
    public void setRtcmContext(List<RtcmMessageDto> rtcmContext) { this.rtcmContext = rtcmContext; }

    public static String fixName(int mode) {
        return switch (mode) {
            case 0 -> "No Fix";
            case 1 -> "PP";
            case 2 -> "SPP";
            case 3 -> "DGPS";
            case 4 -> "PPS";
            case 5 -> "RTK Float";
            case 10 -> "RTK Fix";
            case 11 -> "RTK Float";
            default -> "Unknown(" + mode + ")";
        };
    }
}