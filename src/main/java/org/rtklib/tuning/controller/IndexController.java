package org.rtklib.tuning.controller;

import org.rtklib.tuning.dto.*;
import org.rtklib.tuning.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class IndexController {

    private final FileIndexService indexService;
    private final DataService dataService;

    private final Map<String, List<RtcmMessageDto>> rtcmCache = new ConcurrentHashMap<>();
    private final Map<String, List<SolutionEpochDto>> solCache = new ConcurrentHashMap<>();
    private final AnalysisController analysisController;

    public IndexController(FileIndexService indexService, DataService dataService, AnalysisController analysisController) {
        this.indexService = indexService;
        this.dataService = dataService;
        this.analysisController = analysisController;
    }

    private static final Path CONFIG_FILE = Path.of(System.getProperty("user.dir"), "station_map.json");

    @PostMapping("/scan")
    public FileIndexService.ScanResult scan(@RequestBody Map<String, String> body) {
        String root = body.getOrDefault("root", "D:\\rtcm3");
        return indexService.scan(root);
    }

    @GetMapping("/station-config")
    public Map<String, Object> getStationConfig() {
        if (!Files.exists(CONFIG_FILE)) return Map.of("baseStations", List.of(), "roverStations", List.of());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(CONFIG_FILE.toFile(), Map.class);
        } catch (Exception e) { return Map.of("baseStations", List.of(), "roverStations", List.of()); }
    }

    @PostMapping("/station-config")
    public Map<String, Object> saveStationConfig(@RequestBody Map<String, Object> config) {
        try {
            new com.fasterxml.jackson.databind.ObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValue(CONFIG_FILE.toFile(), config);
            return Map.of("success", true);
        } catch (Exception e) { return Map.of("success", false, "error", e.getMessage()); }
    }

    @PostMapping("/scan-file")
    public Map<String, Object> analysisSingleFile(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        String ext = name != null ? name.toLowerCase() : "";
        Path tmp = Files.createTempFile("scan_", ".tmp");
        file.transferTo(tmp.toFile());

        if (ext.endsWith(".rtcm") || ext.endsWith(".rtcm3") || ext.endsWith(".bin")) {
            List<RtcmMessageDto> msgs = dataService.parseRtcmFile(tmp);
            Files.deleteIfExists(tmp);
            Map<Integer, Long> tc = new LinkedHashMap<>();
            long totalBytes = 0;
            for (RtcmMessageDto m : msgs) {
                tc.merge(m.getMessageType(), 1L, Long::sum);
                totalBytes += m.getDataLength();
            }
            return Map.of(
                "type", "rtcm",
                "fileName", name,
                "totalMessages", msgs.size(),
                "totalBytes", totalBytes,
                "durationSec", msgs.isEmpty() ? 0 :
                    (msgs.get(msgs.size() - 1).getTimestamp() - msgs.get(0).getTimestamp()) / 1000.0,
                "typeSummary", buildTypeSummary(tc),
                "gaps", detectGaps(msgs)
            );
        } else if (ext.endsWith(".pos") || ext.endsWith(".txt")) {
            List<SolutionEpochDto> sols = dataService.parseSolutionFile(tmp);
            Files.deleteIfExists(tmp);
            long fix = sols.stream().filter(s -> s.getFixMode() == 1).count();
            return Map.of(
                "type", "solution",
                "fileName", name,
                "totalEpochs", sols.size(),
                "fixEpochs", fix,
                "floatEpochs", sols.stream().filter(s -> s.getFixMode() == 2).count(),
                "fixRate", sols.isEmpty() ? 0 : Math.round((double) fix / sols.size() * 10000) / 100.0
            );
        }
        Files.deleteIfExists(tmp);
        return Map.of("type", "unknown", "fileName", name);
    }

    @PostMapping("/scan-file-path")
    public Map<String, Object> analysisFilePath(@RequestBody Map<String, Object> body) throws IOException {
        String path = (String) body.get("path");
        if (path == null || path.isBlank()) return Map.of("type", "unknown", "error", "路径为空");
        Path filePath = Path.of(path);
        if (!Files.exists(filePath)) return Map.of("type", "unknown", "error", "文件不存在: " + path);
        String name = filePath.getFileName().toString();
        String ext = name.toLowerCase();

        if (ext.endsWith(".rtcm") || ext.endsWith(".rtcm3") || ext.endsWith(".bin")) {
            List<RtcmMessageDto> msgs = dataService.parseRtcmFile(filePath);
            Map<Integer, Long> tc = new LinkedHashMap<>();
            long totalBytes = 0;
            for (RtcmMessageDto m : msgs) {
                tc.merge(m.getMessageType(), 1L, Long::sum);
                totalBytes += m.getDataLength();
            }
            return Map.of(
                "type", "rtcm",
                "fileName", name,
                "totalMessages", msgs.size(),
                "totalBytes", totalBytes,
                "durationSec", msgs.isEmpty() ? 0 :
                    (msgs.get(msgs.size() - 1).getTimestamp() - msgs.get(0).getTimestamp()) / 1000.0,
                "typeSummary", buildTypeSummary(tc),
                "gaps", detectGaps(msgs)
            );
        } else if (ext.endsWith(".pos") || ext.endsWith(".txt")) {
            List<SolutionEpochDto> sols = dataService.parseSolutionFile(filePath);
            long fix = sols.stream().filter(s -> s.getFixMode() == 1).count();
            return Map.of(
                "type", "solution",
                "fileName", name,
                "totalEpochs", sols.size(),
                "fixEpochs", fix,
                "floatEpochs", sols.stream().filter(s -> s.getFixMode() == 2).count(),
                "fixRate", sols.isEmpty() ? 0 : Math.round((double) fix / sols.size() * 10000) / 100.0
            );
        } else if (ext.endsWith(".obs") || ext.endsWith(".23o") || ext.endsWith(".24o") ||
                   ext.endsWith(".21o") || ext.endsWith(".nav") || ext.endsWith(".23n") ||
                   ext.endsWith(".sp3") || ext.endsWith(".clk")) {
            return Map.of("type", "rinex", "fileName", name, "size", Files.size(filePath));
        }
        return Map.of("type", "other", "fileName", name, "size", Files.size(filePath));
    }

    @PostMapping("/load-station")
    public Map<String, Object> loadStation(@RequestBody Map<String, Object> body) {
        String stationId = (String) body.get("stationId");
        String date = (String) body.get("date");
        int startHour = body.containsKey("startHour") ? (int) body.get("startHour") : 0;
        int endHour = body.containsKey("endHour") ? (int) body.get("endHour") : 24;
        String root = (String) body.getOrDefault("root", "D:\\rtcm3");

        List<String> rtcmFiles = new ArrayList<>();
        Path stationDir = Path.of(root, stationId, date);
        if (Files.isDirectory(stationDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(stationDir)) {
                for (Path f : ds) {
                    String fn = f.getFileName().toString();
                    var m = java.util.regex.Pattern.compile("^(\\d+)\\.(rtcm3?)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(fn);
                    if (m.matches()) {
                        int hour = Integer.parseInt(m.group(1));
                        if (hour >= startHour && hour <= endHour) {
                            rtcmFiles.add(f.toAbsolutePath().toString());
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        rtcmFiles.sort(String::compareTo);

        List<RtcmMessageDto> allMsgs = new ArrayList<>();
        for (String fpath : rtcmFiles) {
            try {
                allMsgs.addAll(dataService.parseRtcmFile(Path.of(fpath)));
            } catch (IOException ignored) {}
        }

        String cacheKey = stationId + "_" + date + "_" + startHour + "_" + endHour;
        rtcmCache.put(cacheKey, allMsgs);

        Map<Integer, Long> tc = new LinkedHashMap<>();
        for (RtcmMessageDto m : allMsgs) tc.merge(m.getMessageType(), 1L, Long::sum);

        Map<String, Object> result = new LinkedHashMap<>(Map.of(
            "cacheKey", cacheKey,
            "totalMessages", allMsgs.size(),
            "fileCount", rtcmFiles.size(),
            "stationId", stationId,
            "date", date,
            "startHour", startHour,
            "endHour", endHour,
            "durationSec", allMsgs.isEmpty() ? 0 :
                (allMsgs.get(allMsgs.size() - 1).getTimestamp() - allMsgs.get(0).getTimestamp()) / 1000.0,
            "typeSummary", buildTypeSummary(tc),
            "gaps", detectGaps(allMsgs)
        ));

        List<SolutionEpochDto> solEpochs = new ArrayList<>();
        if (Files.isDirectory(stationDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(stationDir)) {
                for (Path f : ds) {
                    String fn = f.getFileName().toString().toLowerCase();
                    if (fn.endsWith(".pos")) {
                        String baseName = fn.replace(".pos", "");
                        String[] parts = baseName.split("[-_]");
                        if (parts.length > 0) {
                            try {
                                int fileHour = Integer.parseInt(parts[parts.length - 1]);
                                if (fileHour >= startHour && fileHour < endHour) {
                                    solEpochs.addAll(dataService.parseSolutionFile(f));
                                }
                            } catch (NumberFormatException e) {
                                solEpochs.addAll(dataService.parseSolutionFile(f));
                            }
                        } else {
                            solEpochs.addAll(dataService.parseSolutionFile(f));
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        solEpochs.sort(Comparator.comparingLong(SolutionEpochDto::getTimestamp));

        Map<String, Object> solStats = dataService.computeSolutionStats(solEpochs);
        solStats.remove("epochs");
        result.put("solutionStats", solStats);
        result.put("solutionEpochs", solEpochs);

        solCache.put(cacheKey, solEpochs);
        analysisController.putSolutionEpochs(cacheKey, solEpochs);
        return result;
    }

    private List<Map<String, Object>> buildTypeSummary(Map<Integer, Long> tc) {
        List<Map<String, Object>> l = new ArrayList<>();
        tc.forEach((k, v) -> l.add(Map.of("type", k, "name", RtcmMessageDto.typeName(k), "count", v)));
        return l;
    }

    private List<Map<String, Object>> detectGaps(List<RtcmMessageDto> msgs) {
        List<Map<String, Object>> g = new ArrayList<>();
        for (int i = 1; i < msgs.size(); i++) {
            double gap = (msgs.get(i).getTimestamp() - msgs.get(i - 1).getTimestamp()) / 1000.0;
            if (gap > 2.0) g.add(Map.of(
                "index", i, "gapSec", Math.round(gap * 10) / 10.0,
                "fromTime", msgs.get(i - 1).getTimestamp(), "toTime", msgs.get(i).getTimestamp()
            ));
        }
        return g;
    }
}