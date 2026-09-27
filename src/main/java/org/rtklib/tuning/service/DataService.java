package org.rtklib.tuning.service;

import org.rtklib.tuning.dto.*;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

@Service
public class DataService {

    /** Parse RTCM3 binary file into message list */
    public List<RtcmMessageDto> parseRtcmFile(Path file) throws IOException {
        List<RtcmMessageDto> messages = new ArrayList<>();
        byte[] data = Files.readAllBytes(file);
        int i = 0;
        long startTime = System.currentTimeMillis() - data.length * 10L;

        while (i < data.length - 2) {
            if (data[i] == (byte) 0xD3) {
                int payloadLen = ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
                int totalLen = payloadLen + 6;
                if (i + totalLen > data.length) break;
                if (payloadLen < 0 || payloadLen > 1023) { i++; continue; }

                int msgType = ((data[i + 3] & 0xFF) << 4) | ((data[i + 4] & 0xF0) >> 4);
                int seqNum = data[i + 5] & 0xFF;

                messages.add(new RtcmMessageDto(
                    startTime + i * 10L,
                    msgType, seqNum, totalLen
                ));
                i += totalLen;
            } else {
                i++;
            }
        }
        return messages;
    }

    /** Parse solution .pos file */
    public List<SolutionEpochDto> parseSolutionFile(Path file) throws IOException {
        List<SolutionEpochDto> epochs = new ArrayList<>();
        Pattern linePat = Pattern.compile(
            "^(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.?\\d*)\\s+" +
            "(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+(-?\\d+\\.\\d+)\\s+" +
            "(\\d+)\\s+.*?(\\d+\\.?\\d*)\\s+(\\d+\\.?\\d*)\\s+(\\d+\\.?\\d*)\\s+" +
            "(\\d+\\.?\\d*)?\\s*(\\d+\\.?\\d*)?"
        );

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file.toFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("%") || line.isEmpty()) continue;

                Matcher m = linePat.matcher(line);
                if (m.matches()) {
                    SolutionEpochDto e = new SolutionEpochDto();
                    e.setTimestamp(parseTimestamp(m.group(1)));
                    e.setLatitude(Double.parseDouble(m.group(2)));
                    e.setLongitude(Double.parseDouble(m.group(3)));
                    e.setHeight(Double.parseDouble(m.group(4)));
                    e.setFixMode(Integer.parseInt(m.group(5)));
                    try { e.setNorthStd(Double.parseDouble(m.group(6))); } catch (Exception ex) {}
                    try { e.setEastStd(Double.parseDouble(m.group(7))); } catch (Exception ex) {}
                    try { e.setUpStd(Double.parseDouble(m.group(8))); } catch (Exception ex) {}
                    try { e.setRatio(m.group(9) != null ? Double.parseDouble(m.group(9)) : 0); } catch (Exception ex) {}
                    try { e.setDiffAge(m.group(10) != null ? Double.parseDouble(m.group(10)) : 0); } catch (Exception ex) {}
                    epochs.add(e);
                }
            }
        }
        return epochs;
    }

    /** Compute solution statistics from epochs */
    public Map<String, Object> computeSolutionStats(List<SolutionEpochDto> epochs) {
        Map<String, Object> stats = new LinkedHashMap<>();
        if (epochs == null || epochs.isEmpty()) {
            stats.put("totalEpochs", 0);
            stats.put("fixEpochs", 0);
            stats.put("floatEpochs", 0);
            stats.put("singleEpochs", 0);
            stats.put("fixRate", 0);
            return stats;
        }

        int total = epochs.size();
        List<SolutionEpochDto> fixed = new ArrayList<>();
        int floatCount = 0, singleCount = 0;

        for (SolutionEpochDto e : epochs) {
            int m = e.getFixMode();
            if (m == 1) { fixed.add(e); }
            else if (m == 2) { floatCount++; }
            else { singleCount++; }
        }

        int fixCount = fixed.size();
        double fixRate = Math.round((double) fixCount / total * 10000) / 100.0;

        stats.put("totalEpochs", total);
        stats.put("fixEpochs", fixCount);
        stats.put("floatEpochs", floatCount);
        stats.put("singleEpochs", singleCount);
        stats.put("fixRate", fixRate);

        if (!fixed.isEmpty()) {
            double avgLat = fixed.stream().mapToDouble(SolutionEpochDto::getLatitude).average().orElse(0);
            double avgLon = fixed.stream().mapToDouble(SolutionEpochDto::getLongitude).average().orElse(0);
            double avgHgt = fixed.stream().mapToDouble(SolutionEpochDto::getHeight).average().orElse(0);
            stats.put("fixLat", Math.round(avgLat * 1e9) / 1e9);
            stats.put("fixLon", Math.round(avgLon * 1e9) / 1e9);
            stats.put("fixHgt", Math.round(avgHgt * 1000) / 1000.0);

            double dLat = 111000.0 * Math.cos(Math.toRadians(avgLat));
            double[] nDev = fixed.stream().mapToDouble(e -> (e.getLatitude() - avgLat) * 111000).toArray();
            double[] eDev = fixed.stream().mapToDouble(e -> (e.getLongitude() - avgLon) * dLat).toArray();
            double[] uDev = fixed.stream().mapToDouble(e -> e.getHeight() - avgHgt).toArray();

            stats.put("nMax", round3(max(nDev)));
            stats.put("nMin", round3(min(nDev)));
            stats.put("eMax", round3(max(eDev)));
            stats.put("eMin", round3(min(eDev)));
            stats.put("uMax", round3(max(uDev)));
            stats.put("uMin", round3(min(uDev)));
            stats.put("nRange", round3(max(nDev) - min(nDev)));
            stats.put("eRange", round3(max(eDev) - min(eDev)));
            stats.put("uRange", round3(max(uDev) - min(uDev)));
        }

        List<Double> ratios = epochs.stream().map(SolutionEpochDto::getRatio).filter(r -> r > 0).toList();
        if (!ratios.isEmpty()) {
            stats.put("ratioAvg", round3(ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
            stats.put("ratioMax", round3(ratios.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
            stats.put("ratioMin", round3(ratios.stream().mapToDouble(Double::doubleValue).min().orElse(0)));
        }

        stats.put("epochs", epochs);

        return stats;
    }

    private static double max(double[] arr) { return java.util.Arrays.stream(arr).max().orElse(0); }
    private static double min(double[] arr) { return java.util.Arrays.stream(arr).min().orElse(0); }
    private static double round3(double v) { return Math.round(v * 1000) / 1000.0; }

    private long parseTimestamp(String s) {
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return sdf.parse(s.substring(0, 19)).getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}