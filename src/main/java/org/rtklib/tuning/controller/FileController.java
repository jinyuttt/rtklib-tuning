package org.rtklib.tuning.controller;

import org.rtklib.tuning.dto.RtcmMessageDto;
import org.rtklib.tuning.dto.SolutionEpochDto;
import org.rtklib.tuning.model.FileSource;
import org.rtklib.tuning.service.ConfigManager;
import org.rtklib.tuning.service.DataService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final ConfigManager configManager;
    private final DataService dataService;

    public FileController(ConfigManager configManager, DataService dataService) {
        this.configManager = configManager;
        this.dataService = dataService;
    }

    @GetMapping("/root")
    public Map<String, String> getRootDir() {
        return Map.of("rootDir", configManager.getRootDir());
    }

    @PutMapping("/root")
    public Map<String, Object> setRootDir(@RequestBody Map<String, String> body) {
        String dir = body.getOrDefault("rootDir", System.getProperty("user.home"));
        File f = new File(dir);
        if (f.exists() && f.isDirectory()) {
            configManager.setRootDir(dir);
            return Map.of("success", true, "rootDir", dir);
        }
        return Map.of("success", false, "error", "Directory does not exist: " + dir);
    }

    @GetMapping("/list")
    public List<FileSource> listFiles(@RequestParam(required = false) String dir) {
        String target = (dir != null && !dir.isEmpty()) ? dir : configManager.getRootDir();
        File folder = new File(target);
        if (!folder.exists() || !folder.isDirectory()) {
            return Collections.emptyList();
        }
        return Arrays.stream(Objects.requireNonNull(folder.listFiles()))
            .filter(File::isFile)
            .map(this::toFileSource)
            .collect(Collectors.toList());
    }

    @GetMapping("/dirs")
    public Map<String, Object> listDirs(@RequestParam(required = false) String dir) {
        String target = (dir != null && !dir.isEmpty()) ? dir : configManager.getRootDir();
        File folder = new File(target);
        if (!folder.exists() || !folder.isDirectory()) {
            return Map.of("path", target, "parent", "", "dirs", Collections.emptyList());
        }
        String parent = folder.getParent() != null ? folder.getParent() : "";
        List<Map<String, String>> dirs = Arrays.stream(Objects.requireNonNull(folder.listFiles()))
            .filter(File::isDirectory)
            .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER))
            .map(d -> Map.of("name", d.getName(), "path", d.getAbsolutePath()))
            .collect(Collectors.toList());
        return Map.of("path", folder.getAbsolutePath(), "parent", parent, "dirs", dirs);
    }

    @GetMapping("/info")
    public FileSource fileInfo(@RequestParam String path) {
        File file = new File(path);
        if (!file.exists()) return null;
        return toFileSource(file);
    }

    @PostMapping(value = "/analyze", consumes = "application/json")
    public Map<String, Object> analyze(@RequestBody Map<String, String> body) {
        String path = body.getOrDefault("path", "").trim();
        if (path.isEmpty()) {
            return Map.of("ok", false, "error", "未指定文件路径");
        }
        File file = new File(path);
        if (!file.exists()) {
            return Map.of("ok", false, "error", "文件不存在: " + path);
        }
        return doAnalyze(file.toPath(), file.length());
    }

    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public Map<String, Object> analyzeUpload(@RequestParam("file") MultipartFile upload) throws IOException {
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "rtklib-upload");
        Files.createDirectories(dir);
        Path dest = dir.resolve(System.currentTimeMillis() + "_" + upload.getOriginalFilename());
        upload.transferTo(dest.toFile());
        try {
            return doAnalyze(dest, upload.getSize());
        } finally {
            try { Files.deleteIfExists(dest); } catch (Exception ignored) {}
        }
    }

    private Map<String, Object> doAnalyze(Path filePath, long fileSize) {
        String name = filePath.getFileName().toString().toLowerCase();
        try {
            if (name.endsWith(".rtcm3") || name.endsWith(".rtcm")) {
                return analyzeRtcm(filePath, fileSize);
            } else if (name.endsWith(".pos") || name.endsWith(".sol")) {
                return analyzeSolution(filePath, fileSize);
            } else if (name.endsWith(".obs") || name.endsWith(".o") || isObsRinex(name)) {
                return analyzeObs(filePath, fileSize);
            } else {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("type", "generic");
                r.put("totalBytes", fileSize);
                r.put("name", filePath.getFileName().toString());
                r.put("charts", new LinkedHashMap<>());
                return r;
            }
        } catch (Exception e) {
            return Map.of("ok", false, "error", "分析失败: " + e.getMessage());
        }
    }

    private static boolean isObsRinex(String name) {
        if (!name.matches(".*\\.\\d{2}[on]$")) return false;
        return name.matches(".*\\.\\d{2}o$");
    }

    private Map<String, Object> analyzeObs(Path obsFile, long fileSize) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "obs");
        result.put("totalBytes", fileSize);
        result.put("name", obsFile.getFileName().toString());

        Path navFile = findMatchingNav(obsFile);
        result.put("navFound", navFile != null);

        try {
            org.rtklib.java.rinex.RinexParser parser = new org.rtklib.java.rinex.RinexParser();
            boolean obsOk = parser.parseObs(obsFile.toAbsolutePath().toString());
            if (!obsOk) {
                result.put("charts", new LinkedHashMap<>());
                return result;
            }

            Map<String, Object> charts = new LinkedHashMap<>();
            List<Map<String, Object>> snrSeries = new ArrayList<>();
            Map<String, List<Float>> snrBySat = new LinkedHashMap<>();

            if (parser.obs != null && parser.obs.data != null) {
                int totalObs = parser.obs.data.length;
                result.put("totalObsRecords", totalObs);
                Map<Long, List<org.rtklib.java.data.Obsd>> byEpoch = new LinkedHashMap<>();
                for (org.rtklib.java.data.Obsd obsd : parser.obs.data) {
                    if (obsd == null || obsd.sat <= 0 || obsd.time == null) continue;
                    String satId = satIdOf(obsd.sat);
                    if (satId == null) continue;
                    if (obsd.SNR != null && obsd.SNR.length > 0) {
                        float snr = obsd.SNR[0];
                        snrBySat.computeIfAbsent(satId, k -> new ArrayList<>()).add(snr);
                        long epochKey = obsd.time.time;
                        byEpoch.computeIfAbsent(epochKey, k -> new ArrayList<>()).add(obsd);
                    }
                }

                List<Long> sortedKeys = new ArrayList<>(byEpoch.keySet());
                Collections.sort(sortedKeys);
                int sampleStep = Math.max(1, sortedKeys.size() / 30);
                for (int i = 0; i < sortedKeys.size(); i += sampleStep) {
                    long t = sortedKeys.get(i);
                    Map<String, Object> epoch = new LinkedHashMap<>();
                    epoch.put("t", t);
                    Map<String, Double> snrs = new LinkedHashMap<>();
                    for (org.rtklib.java.data.Obsd obsd : byEpoch.get(t)) {
                        String satId = satIdOf(obsd.sat);
                        if (satId != null && obsd.SNR != null && obsd.SNR.length > 0) {
                            snrs.put(satId, (double) obsd.SNR[0]);
                        }
                    }
                    epoch.put("snrs", snrs);
                    snrSeries.add(epoch);
                }
                result.put("totalEpochs", sortedKeys.size());
            }

            List<Map<String, Object>> snrDist = new ArrayList<>();
            for (Map.Entry<String, List<Float>> e : snrBySat.entrySet()) {
                List<Float> vals = e.getValue();
                if (vals.isEmpty()) continue;
                Map<String, Object> bar = new LinkedHashMap<>();
                bar.put("satId", e.getKey());
                bar.put("count", vals.size());
                snrDist.add(bar);
            }
            charts.put("snrSeries", snrSeries);
            charts.put("snrDist", snrDist);

            if (navFile != null && Files.exists(navFile) && Files.size(navFile) > 100) {
                try {
                    org.rtklib.java.rinex.RinexSppProcessor.SppResult spp =
                        org.rtklib.java.rinex.RinexSppProcessor.processRinex(obsFile.toAbsolutePath().toString(), navFile.toAbsolutePath().toString());
                    if (spp != null && spp.solutions != null && !spp.solutions.isEmpty()) {
                        List<Map<String, Object>> sky = new ArrayList<>();
                        List<Map<String, Object>> dopSeries = new ArrayList<>();
                        Map<String, Object> dopSummary = new LinkedHashMap<>();
                        double gdopSum = 0, hdopSum = 0, vdopSum = 0;
                        int dopCount = 0;
                        Map<String, Integer> satCount = new LinkedHashMap<>();

                        for (org.rtklib.java.data.SolData sol : spp.solutions) {
                            if (sol.satObsList == null || sol.satObsList.isEmpty()) continue;
                            Map<String, Object> dopP = new LinkedHashMap<>();
                            dopP.put("t", sol.time.time);
                            dopP.put("gdop", Math.round(sol.gdop * 100) / 100.0);
                            dopP.put("hdop", Math.round(sol.hdop * 100) / 100.0);
                            dopP.put("vdop", Math.round(sol.vdop * 100) / 100.0);
                            dopSeries.add(dopP);
                            gdopSum += sol.gdop; hdopSum += sol.hdop; vdopSum += sol.vdop;
                            dopCount++;

                            for (org.rtklib.java.data.SatObsData sod : sol.satObsList) {
                                if (sod.el <= 0) continue;
                                satCount.merge(sod.satPrn, 1, Integer::sum);
                                boolean exists = sky.stream().anyMatch(s -> s.get("id").equals(sod.satPrn));
                                if (!exists) {
                                    Map<String, Object> sat = new LinkedHashMap<>();
                                    sat.put("id", sod.satPrn);
                                    sat.put("elevation", Math.round(sod.el * 10) / 10.0);
                                    sat.put("azimuth", Math.round(sod.az * 10) / 10.0);
                                    sat.put("snr", Math.round(sod.cn0 * 10) / 10.0);
                                    sat.put("filtered", !sod.used);
                                    sky.add(sat);
                                }
                            }
                        }

                        if (dopCount > 0) {
                            dopSummary.put("gdop", Math.round(gdopSum / dopCount * 100) / 100.0);
                            dopSummary.put("hdop", Math.round(hdopSum / dopCount * 100) / 100.0);
                            dopSummary.put("vdop", Math.round(vdopSum / dopCount * 100) / 100.0);
                        }

                        charts.put("sky", sky);
                        charts.put("dop", dopSummary);
                        charts.put("dopSeries", dopSeries);
                        result.put("sppEpochs", spp.solutions.size());
                    }
                } catch (Exception sppEx) {
                    // SPP失败也不影响基本图表
                }
            }

            result.put("charts", charts);
        } catch (Exception e) {
            result.put("charts", new LinkedHashMap<>());
            result.put("parseError", e.getMessage());
        }

        return result;
    }

    private Path findMatchingNav(Path obsFile) {
        if (obsFile == null || !Files.exists(obsFile)) return null;
        try {
            Path parent = obsFile.getParent();
            if (parent == null) parent = Path.of(".");
            String name = obsFile.getFileName().toString().toLowerCase();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            String[] navExts = {".nav", ".n", ".23n", ".24n", ".25n", ".26n", ".20n", ".21n", ".22n", ".27n", ".28n"};
            for (String ext : navExts) {
                Path cand = parent.resolve(base + ext);
                if (Files.exists(cand) && Files.size(cand) > 100) return cand;
            }
            try (var stream = Files.list(parent)) {
                return stream.filter(p -> {
                    String ln = p.getFileName().toString().toLowerCase();
                    if (ln.startsWith(base) && Files.isRegularFile(p)) {
                        try { return Files.size(p) > 100; } catch (Exception e) { return false; }
                    }
                    return false;
                }).filter(p -> {
                    String ln = p.getFileName().toString().toLowerCase();
                    for (String ext : navExts) if (ln.endsWith(ext)) return true;
                    if (ln.matches(".*\\.\\d{2}n$")) return true;
                    return false;
                }).findFirst().orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> analyzeRtcm(Path filePath, long fileSize) throws IOException {
        List<RtcmMessageDto> messages = dataService.parseRtcmFile(filePath);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "rtcm");
        result.put("totalMessages", messages.size());
        result.put("totalBytes", fileSize);

        if (!messages.isEmpty()) {
            long t0 = messages.get(0).getTimestamp();
            long t1 = messages.get(messages.size() - 1).getTimestamp();
            result.put("durationSec", (t1 - t0) / 1000.0);
        } else {
            result.put("durationSec", 0);
        }

        Map<Integer, Long> typeCount = messages.stream()
            .collect(Collectors.groupingBy(RtcmMessageDto::getMessageType, Collectors.counting()));
        List<Map<String, Object>> typeSummary = typeCount.entrySet().stream()
            .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
            .map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("type", e.getKey());
                m.put("count", e.getValue());
                m.put("name", RtcmMessageDto.typeName(e.getKey()));
                return m;
            })
            .collect(Collectors.toList());
        result.put("typeSummary", typeSummary);

        List<Map<String, Object>> gaps = new ArrayList<>();
        for (int i = 1; i < messages.size(); i++) {
            long gap = messages.get(i).getTimestamp() - messages.get(i - 1).getTimestamp();
            if (gap > 5000) {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put("index", i);
                g.put("gapSec", gap / 1000.0);
                gaps.add(g);
            }
        }
        result.put("gaps", gaps);

        result.put("charts", new LinkedHashMap<>());
        try {
            enrichRtcmCharts(filePath, result);
        } catch (Exception e) {
            // 图表数据生成失败不影响基本信息
        }

        return result;
    }

    private void enrichRtcmCharts(Path rtcmFile, Map<String, Object> result) throws Exception {
        Path tempDir = Files.createTempDirectory("rtcm_analyze_");
        try {
            org.rtklib.java.rinex.RtcmFileToRinexConverter converter =
                new org.rtklib.java.rinex.RtcmFileToRinexConverter(3.04, tempDir.toString(), "RTCM");
            boolean ok = converter.convert(rtcmFile.toAbsolutePath().toString());
            if (!ok) return;

            String obsPath = converter.getObsFilePath();
            String navPath = converter.getNavFilePath();
            if (obsPath == null || !Files.exists(Path.of(obsPath))) return;

            boolean hasNav = navPath != null && Files.exists(Path.of(navPath)) && Files.size(Path.of(navPath)) > 100;

            org.rtklib.java.rinex.RinexParser parser = new org.rtklib.java.rinex.RinexParser();
            boolean obsOk = parser.parseObs(obsPath);
            if (!obsOk) return;

            List<Map<String, Object>> snrSeries = new ArrayList<>();
            Map<String, List<Float>> snrBySat = new LinkedHashMap<>();
            Set<String> allSats = new LinkedHashSet<>();

            if (parser.obs != null && parser.obs.data != null) {
                Map<Long, List<org.rtklib.java.data.Obsd>> byEpoch = new LinkedHashMap<>();
                for (org.rtklib.java.data.Obsd obsd : parser.obs.data) {
                    if (obsd == null || obsd.sat <= 0 || obsd.time == null) continue;
                    String satId = satIdOf(obsd.sat);
                    if (satId == null) continue;
                    allSats.add(satId);
                    if (obsd.SNR != null && obsd.SNR.length > 0) {
                        float snr = obsd.SNR[0];
                        snrBySat.computeIfAbsent(satId, k -> new ArrayList<>()).add(snr);
                        long epochKey = obsd.time.time;
                        byEpoch.computeIfAbsent(epochKey, k -> new ArrayList<>()).add(obsd);
                    }
                }

                List<Long> sortedKeys = new ArrayList<>(byEpoch.keySet());
                Collections.sort(sortedKeys);
                int sampleStep = Math.max(1, sortedKeys.size() / 30);
                for (int i = 0; i < sortedKeys.size(); i += sampleStep) {
                    long t = sortedKeys.get(i);
                    Map<String, Object> epoch = new LinkedHashMap<>();
                    epoch.put("t", t);
                    Map<String, Double> snrs = new LinkedHashMap<>();
                    for (org.rtklib.java.data.Obsd obsd : byEpoch.get(t)) {
                        String satId = satIdOf(obsd.sat);
                        if (satId != null && obsd.SNR != null && obsd.SNR.length > 0) {
                            snrs.put(satId, (double) obsd.SNR[0]);
                        }
                    }
                    epoch.put("snrs", snrs);
                    snrSeries.add(epoch);
                }
            }

            List<Map<String, Object>> snrDist = new ArrayList<>();
            for (Map.Entry<String, List<Float>> e : snrBySat.entrySet()) {
                List<Float> vals = e.getValue();
                if (vals.isEmpty()) continue;
                Map<String, Object> bar = new LinkedHashMap<>();
                bar.put("satId", e.getKey());
                bar.put("count", vals.size());
                snrDist.add(bar);
            }

            result.putIfAbsent("charts", new LinkedHashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, Object> charts = (Map<String, Object>) result.get("charts");
            charts.put("snrSeries", snrSeries);
            charts.put("snrDist", snrDist);

            if (hasNav) {
                try {
                    org.rtklib.java.rinex.RinexSppProcessor.SppResult spp =
                        org.rtklib.java.rinex.RinexSppProcessor.processRinex(obsPath, navPath);
                    if (spp != null && spp.solutions != null && !spp.solutions.isEmpty()) {
                        List<Map<String, Object>> sky = new ArrayList<>();
                        List<Map<String, Object>> dopSeries = new ArrayList<>();
                        Map<String, Object> dopSummary = new LinkedHashMap<>();
                        double gdopSum = 0, hdopSum = 0, vdopSum = 0;
                        int dopCount = 0;

                        for (org.rtklib.java.data.SolData sol : spp.solutions) {
                            if (sol.satObsList == null || sol.satObsList.isEmpty()) continue;
                            Map<String, Object> dopP = new LinkedHashMap<>();
                            dopP.put("t", sol.time.time);
                            dopP.put("gdop", Math.round(sol.gdop * 100) / 100.0);
                            dopP.put("hdop", Math.round(sol.hdop * 100) / 100.0);
                            dopP.put("vdop", Math.round(sol.vdop * 100) / 100.0);
                            dopSeries.add(dopP);
                            gdopSum += sol.gdop; hdopSum += sol.hdop; vdopSum += sol.vdop;
                            dopCount++;

                            for (org.rtklib.java.data.SatObsData sod : sol.satObsList) {
                                if (sod.el <= 0) continue;
                                boolean exists = sky.stream().anyMatch(s -> s.get("id").equals(sod.satPrn));
                                if (!exists) {
                                    Map<String, Object> sat = new LinkedHashMap<>();
                                    sat.put("id", sod.satPrn);
                                    sat.put("elevation", Math.round(sod.el * 10) / 10.0);
                                    sat.put("azimuth", Math.round(sod.az * 10) / 10.0);
                                    sat.put("snr", Math.round(sod.cn0 * 10) / 10.0);
                                    sat.put("filtered", !sod.used);
                                    sky.add(sat);
                                }
                            }
                        }

                        if (dopCount > 0) {
                            dopSummary.put("gdop", Math.round(gdopSum / dopCount * 100) / 100.0);
                            dopSummary.put("hdop", Math.round(hdopSum / dopCount * 100) / 100.0);
                            dopSummary.put("vdop", Math.round(vdopSum / dopCount * 100) / 100.0);
                        }

                        charts.put("sky", sky);
                        charts.put("dop", dopSummary);
                        charts.put("dopSeries", dopSeries);
                    }
                } catch (Exception sppEx) {
                    // SPP失败也不影响基本图表
                }
            }
        } finally {
            try {
                Files.walk(tempDir).sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
            } catch (Exception ignored) {}
        }
    }

    private static String satIdOf(int sat) {
        if (sat <= 0) return null;
        String prefix;
        if (sat <= 37) prefix = "G";
        else if (sat <= 61) prefix = "R";
        else if (sat <= 71) prefix = "E";
        else if (sat <= 137) prefix = "C";
        else if (sat <= 202) prefix = "J";
        else return null;
        int num = sat;
        if (sat > 37 && sat <= 61) num = sat - 37;
        else if (sat > 61 && sat <= 71) num = sat - 61;
        else if (sat > 71 && sat <= 137) num = sat - 71;
        else if (sat > 137 && sat <= 202) num = sat - 137;
        return prefix + String.format("%02d", num);
    }

    private Map<String, Object> analyzeSolution(Path filePath, long fileSize) throws IOException {
        List<SolutionEpochDto> epochs = dataService.parseSolutionFile(filePath);
        Map<String, Object> stats = dataService.computeSolutionStats(epochs);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "solution");
        result.put("totalBytes", fileSize);
        result.put("totalEpochs", stats.getOrDefault("totalEpochs", 0));
        result.put("fixEpochs", stats.getOrDefault("fixEpochs", 0));
        result.put("floatEpochs", stats.getOrDefault("floatEpochs", 0));
        result.put("fixRate", stats.getOrDefault("fixRate", 0));
        if (stats.containsKey("fixLat")) result.put("fixLat", stats.get("fixLat"));
        if (stats.containsKey("fixLon")) result.put("fixLon", stats.get("fixLon"));
        if (stats.containsKey("fixHgt")) result.put("fixHgt", stats.get("fixHgt"));

        Map<String, Object> charts = new LinkedHashMap<>();
        List<Map<String, Object>> epochData = new ArrayList<>();
        for (SolutionEpochDto e : epochs) {
            Map<String, Object> ed = new LinkedHashMap<>();
            ed.put("t", e.getTimestamp());
            ed.put("latitude", e.getLatitude());
            ed.put("longitude", e.getLongitude());
            ed.put("height", e.getHeight());
            ed.put("fixMode", e.getFixMode());
            ed.put("ratio", e.getRatio());
            epochData.add(ed);
        }
        charts.put("epochs", epochData);
        result.put("charts", charts);

        return result;
    }

    private FileSource toFileSource(File file) {
        FileSource fs = new FileSource();
        fs.setName(file.getName());
        fs.setPath(file.getAbsolutePath());
        fs.setSize(file.length());
        fs.setLastModified(file.lastModified());
        String name = file.getName().toLowerCase();
        if (name.endsWith(".obs") || name.endsWith(".o")) {
            fs.setType("OBS");
        } else if (name.endsWith(".nav") || name.endsWith(".n")) {
            fs.setType("NAV");
        } else if (name.endsWith(".rtcm3") || name.endsWith(".rtcm")) {
            fs.setType("RTCM3");
        } else if (name.endsWith(".nmea")) {
            fs.setType("NMEA");
        } else if (name.endsWith(".pos") || name.endsWith(".sol")) {
            fs.setType("SOLUTION");
        } else if (name.endsWith(".ubx")) {
            fs.setType("UBX");
        } else {
            fs.setType("OTHER");
        }
        return fs;
    }
}