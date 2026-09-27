package org.rtklib.tuning.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class Rtcm3Analyzer {

    private static final int RTCM3_PREAMBLE = 0xD3;

    public static class RtcmMessage {
        public int type;
        public int length;
        public long stationId;
        public Map<String, Object> fields;

        public RtcmMessage(int type, int length) {
            this.type = type;
            this.length = length;
            this.fields = new LinkedHashMap<>();
        }
    }

    public static class StreamSummary {
        public int totalMessages;
        public Map<Integer, Integer> typeCounts;
        public Set<Integer> observedTypes;
        public long stationId;
        public boolean hasObservations;
        public boolean hasEphemeris;

        public StreamSummary() {
            this.totalMessages = 0;
            this.typeCounts = new LinkedHashMap<>();
            this.observedTypes = new TreeSet<>();
            this.hasObservations = false;
            this.hasEphemeris = false;
        }
    }

    private final StreamSummary summary = new StreamSummary();

    public Optional<RtcmMessage> parseNext(byte[] data, int offset) {
        if (offset >= data.length) return Optional.empty();

        int preamble = data[offset] & 0xFF;
        if (preamble != RTCM3_PREAMBLE) return Optional.empty();

        if (offset + 3 >= data.length) return Optional.empty();

        int length = ((data[offset + 1] & 0x03) << 8) | (data[offset + 2] & 0xFF);

        if (offset + 3 + length + 3 > data.length) return Optional.empty();

        int type = ((data[offset + 3] & 0xFF) << 4) | ((data[offset + 4] & 0xF0) >> 4);

        RtcmMessage msg = new RtcmMessage(type, length);
        msg.fields.put("preamble", preamble);
        msg.fields.put("rawLength", length);

        if (type >= 1001 && type <= 1012) {
            summary.hasObservations = true;
        }
        if (type >= 1019 && type <= 1044) {
            summary.hasEphemeris = true;
        }

        summary.totalMessages++;
        summary.typeCounts.merge(type, 1, Integer::sum);
        summary.observedTypes.add(type);

        return Optional.of(msg);
    }

    public StreamSummary getSummary() {
        return summary;
    }

    public void resetSummary() {
        summary.totalMessages = 0;
        summary.typeCounts.clear();
        summary.observedTypes.clear();
        summary.stationId = 0;
        summary.hasObservations = false;
        summary.hasEphemeris = false;
    }

    public String diagnoseStream() {
        StringBuilder sb = new StringBuilder();
        sb.append("RTCM3 Stream Diagnostics\n");
        sb.append("========================\n");
        sb.append(String.format("Total messages: %d\n", summary.totalMessages));
        sb.append(String.format("Message types: %s\n", summary.observedTypes));
        sb.append(String.format("Has observations: %b\n", summary.hasObservations));
        sb.append(String.format("Has ephemeris: %b\n", summary.hasEphemeris));

        if (!summary.hasObservations) {
            sb.append("\nWARNING: No observation messages found. Check data source.\n");
        }
        if (!summary.hasEphemeris) {
            sb.append("\nWARNING: No ephemeris messages found. Fix may not converge.\n");
        }

        sb.append("\nMessage type distribution:\n");
        summary.typeCounts.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .forEach(e -> sb.append(String.format("  Type %d: %d (%.1f%%)\n",
                e.getKey(), e.getValue(), 100.0 * e.getValue() / summary.totalMessages)));

        return sb.toString();
    }
}