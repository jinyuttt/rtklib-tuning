package org.rtklib.tuning.controller;

import org.rtklib.tuning.dto.SolutionEpochDto;
import org.rtklib.tuning.model.SolveRequest;
import org.rtklib.tuning.model.SolveResult;
import org.rtklib.tuning.service.PositioningEngine;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/analyze")
public class AnalysisController {

    private final PositioningEngine engine;
    private final Map<String, List<SolutionEpochDto>> solCache = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> evalEmitters = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> evalResults = new ConcurrentHashMap<>();
    private final Map<String, String> evalDirCache = new ConcurrentHashMap<>();

    private static final Path EVAL_BASE_DIR = Paths.get("data", "eval");
    private static final DateTimeFormatter POS_TIME_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.000");

    public AnalysisController(PositioningEngine engine) {
        this.engine = engine;
    }

    public void putSolutionEpochs(String cacheKey, List<SolutionEpochDto> epochs) {
        if (cacheKey != null && !cacheKey.isEmpty() && epochs != null) {
            solCache.put(cacheKey, epochs);
        }
    }

    private void sendSseEvent(String evalId, String eventName, Object data) {
        SseEmitter emitter = evalEmitters.get(evalId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
            } catch (Exception ignored) {}
        }
    }

    @PostMapping("/evaluate")
    public Map<String, Object> evaluate(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> paramSets = (List<Map<String, Object>>) body.get("paramSets");
        String cacheKey = (String) body.getOrDefault("cacheKey", "");
        String roverFile = (String) body.getOrDefault("roverFile", "");
        String baseFile = (String) body.getOrDefault("baseFile", "");
        boolean isAsync = Boolean.TRUE.equals(body.get("async"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filePairs = (List<Map<String, Object>>) body.get("filePairs");

        if (paramSets == null) paramSets = List.of();

        if (filePairs == null || filePairs.isEmpty()) {
            if (!roverFile.isEmpty()) {
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("roverFile", roverFile);
                pair.put("baseFile", baseFile != null ? baseFile : "");
                filePairs = List.of(pair);
            }
        }

        if (filePairs == null || filePairs.isEmpty()) {
            List<Map<String, Object>> empty = new ArrayList<>();
            for (Map<String, Object> p : paramSets) {
                empty.add(Map.of("label", p.getOrDefault("label", "?"), "fixRate", 0.0, "fixEpochs", 0, "totalEpochs", 0));
            }
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("results", empty);
            resp.put("error", "未指定测站文件");
            return resp;
        }

        if (isAsync) {
            String evalId = "eval_" + System.currentTimeMillis();
            SseEmitter emitter = new SseEmitter(600_000L);
            evalEmitters.put(evalId, emitter);
            emitter.onCompletion(() -> evalEmitters.remove(evalId));
            emitter.onTimeout(() -> evalEmitters.remove(evalId));

            final List<Map<String, Object>> finalFilePairs = filePairs;
            final List<Map<String, Object>> finalParamSets = paramSets;

            new Thread(() -> {
                try {
                    runEvalAsync(evalId, finalParamSets, finalFilePairs);
                } catch (Exception e) {
                    sendSseEvent(evalId, "error", Map.of("msg", e.getMessage()));
                    evalEmitters.remove(evalId);
                }
            }, "eval-" + evalId).start();

            return Map.of("evalId", evalId);
        }

        return runEvalSync(paramSets, filePairs);
    }

    @GetMapping(value = "/evaluate-progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter evaluateProgress(@RequestParam String evalId) {
        SseEmitter emitter = evalEmitters.get(evalId);
        if (emitter != null) return emitter;
        SseEmitter dead = new SseEmitter(0L);
        try { dead.send(SseEmitter.event().name("error").data(Map.of("msg", "评估不存在或已结束"))); dead.complete(); } catch (Exception ignored) {}
        return dead;
    }

    private void runEvalAsync(String evalId, List<Map<String, Object>> paramSets, List<Map<String, Object>> filePairs) {
        List<Map<String, Object>> results = new ArrayList<>();
        String firstError = null;
        int bestIdx = -1;
        double bestRate = -1;

        try { Files.createDirectories(EVAL_BASE_DIR.resolve(evalId)); } catch (IOException ignored) {}
        evalDirCache.put(evalId, EVAL_BASE_DIR.resolve(evalId).toString());

        for (int pi = 0; pi < paramSets.size(); pi++) {
            Map<String, Object> p = paramSets.get(pi);
            String label = (String) p.getOrDefault("label", "?");
            String pMode = (String) p.getOrDefault("mode", "kinematic");
            Map<String, Object> normParams = normalizeParams(p);

            List<SolutionEpochDto> allEpochs = new ArrayList<>();
            int pairSuccess = 0;

            for (int fi = 0; fi < filePairs.size(); fi++) {
                Map<String, Object> fp = filePairs.get(fi);
                String rf = (String) fp.getOrDefault("roverFile", "");
                String bf = (String) fp.getOrDefault("baseFile", "");
                String fileLabel = fp.getOrDefault("date", "") + "/" + fp.getOrDefault("hour", rf.length() > 0 ? rf.substring(Math.max(0, rf.length() - 10)) : "?");

                sendSseEvent(evalId, "progress", Map.of(
                    "paramIdx", pi, "paramLabel", label,
                    "fileIdx", fi, "totalFiles", filePairs.size(),
                    "file", fileLabel, "status", "running"
                ));

                if (rf.isEmpty()) continue;

                List<SolutionEpochDto> epochs = runSolveForEval(pMode, rf, bf, normParams);
                if (epochs != null && !epochs.isEmpty()) {
                    allEpochs.addAll(epochs);
                    pairSuccess++;
                    int epCount = epochs.size();
                    int fixC = (int) epochs.stream().filter(e -> e.getFixMode() == 1).count();
                    double fr = epCount > 0 ? Math.round((double) fixC / epCount * 10000) / 100.0 : 0;
                    sendSseEvent(evalId, "progress", Map.of(
                        "paramIdx", pi, "paramLabel", label,
                        "fileIdx", fi, "totalFiles", filePairs.size(),
                        "file", fileLabel, "status", "done",
                        "epochs", epCount, "fixRate", fr
                    ));
                } else {
                    sendSseEvent(evalId, "progress", Map.of(
                        "paramIdx", pi, "paramLabel", label,
                        "fileIdx", fi, "totalFiles", filePairs.size(),
                        "file", fileLabel, "status", "done",
                        "epochs", 0, "fixRate", 0.0
                    ));
                }
            }

            if (allEpochs.isEmpty()) {
                results.add(new LinkedHashMap<>(Map.of(
                    "label", label, "fixRate", 0.0, "fixEpochs", 0,
                    "floatEpochs", 0, "singleEpochs", 0, "totalEpochs", 0
                )));
                if (firstError == null) firstError = "解算失败: " + label;
                sendSseEvent(evalId, "paramDone", Map.of(
                    "paramIdx", pi, "paramLabel", label,
                    "totalEpochs", 0, "fixRate", 0.0
                ));
                continue;
            }

            int maxAge = p.containsKey("maxAge") ? ((Number) p.get("maxAge")).intValue() : 30;
            double thresar = p.containsKey("thresar") ? ((Number) p.get("thresar")).doubleValue() : 3.0;

            int total = allEpochs.size();
            int fixCount = 0, floatCount = 0, singleCount = 0;

            for (SolutionEpochDto e : allEpochs) {
                int origMode = e.getFixMode();
                boolean canFix = (origMode == 1);
                if (canFix && maxAge > 0 && e.getDiffAge() > maxAge) canFix = false;
                if (canFix && thresar > 0 && e.getRatio() > 0 && e.getRatio() < thresar) canFix = false;
                if (canFix) fixCount++;
                else if (origMode == 2) floatCount++;
                else singleCount++;
            }

            double fixRate = total > 0 ? Math.round((double) fixCount / total * 10000) / 100.0 : 0;
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("label", label);
            resultMap.put("fixRate", fixRate);
            resultMap.put("fixEpochs", fixCount);
            resultMap.put("floatEpochs", floatCount);
            resultMap.put("singleEpochs", singleCount);
            resultMap.put("totalEpochs", total);
            resultMap.put("pairCount", pairSuccess);
            results.add(resultMap);

            writePosFile(evalId, label, allEpochs);

            if (fixRate > bestRate) {
                bestRate = fixRate;
                bestIdx = pi;
            }

            sendSseEvent(evalId, "paramDone", Map.of(
                "paramIdx", pi, "paramLabel", label,
                "totalEpochs", total, "fixRate", fixRate
            ));
        }

        results.sort((a, b) -> Double.compare(
            ((Number) b.getOrDefault("fixRate", 0)).doubleValue(),
            ((Number) a.getOrDefault("fixRate", 0)).doubleValue()
        ));

        Map<String, Object> doneData = new LinkedHashMap<>();
        doneData.put("results", results);
        doneData.put("evalId", evalId);
        if (firstError != null) doneData.put("error", firstError);
        if (bestIdx >= 0 && bestIdx < paramSets.size()) {
            doneData.put("bestIdx", bestIdx);
            doneData.put("bestLabel", paramSets.get(bestIdx).getOrDefault("label", "?"));
        }

        sendSseEvent(evalId, "done", doneData);
        SseEmitter emitter = evalEmitters.get(evalId);
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
            evalEmitters.remove(evalId);
        }
    }

    private Map<String, Object> runEvalSync(List<Map<String, Object>> paramSets, List<Map<String, Object>> filePairs) {
        String syncEvalId = "sync_" + System.currentTimeMillis();
        List<Map<String, Object>> results = new ArrayList<>();
        String firstError = null;
        int bestIdx = -1;
        double bestRate = -1;

        try { Files.createDirectories(EVAL_BASE_DIR.resolve(syncEvalId)); } catch (IOException ignored) {}
        evalDirCache.put(syncEvalId, EVAL_BASE_DIR.resolve(syncEvalId).toString());

        for (int pi = 0; pi < paramSets.size(); pi++) {
            Map<String, Object> p = paramSets.get(pi);
            String label = (String) p.getOrDefault("label", "?");
            String pMode = (String) p.getOrDefault("mode", "kinematic");
            Map<String, Object> normParams = normalizeParams(p);

            List<SolutionEpochDto> allEpochs = new ArrayList<>();
            int pairSuccess = 0;

            for (Map<String, Object> fp : filePairs) {
                String rf = (String) fp.getOrDefault("roverFile", "");
                String bf = (String) fp.getOrDefault("baseFile", "");
                if (rf.isEmpty()) continue;

                List<SolutionEpochDto> epochs = runSolveForEval(pMode, rf, bf, normParams);
                if (epochs != null && !epochs.isEmpty()) {
                    allEpochs.addAll(epochs);
                    pairSuccess++;
                }
            }

            if (allEpochs.isEmpty()) {
                results.add(new LinkedHashMap<>(Map.of(
                    "label", label, "fixRate", 0.0, "fixEpochs", 0,
                    "floatEpochs", 0, "singleEpochs", 0, "totalEpochs", 0
                )));
                if (firstError == null) firstError = "解算失败: " + label + " (检查文件或参数)";
                continue;
            }

            int maxAge = p.containsKey("maxAge") ? ((Number) p.get("maxAge")).intValue() : 30;
            double thresar = p.containsKey("thresar") ? ((Number) p.get("thresar")).doubleValue() : 3.0;

            int total = allEpochs.size();
            int fixCount = 0, floatCount = 0, singleCount = 0;

            for (SolutionEpochDto e : allEpochs) {
                int origMode = e.getFixMode();
                boolean canFix = (origMode == 1);
                if (canFix && maxAge > 0 && e.getDiffAge() > maxAge) canFix = false;
                if (canFix && thresar > 0 && e.getRatio() > 0 && e.getRatio() < thresar) canFix = false;
                if (canFix) fixCount++;
                else if (origMode == 2) floatCount++;
                else singleCount++;
            }

            double fixRate = total > 0 ? Math.round((double) fixCount / total * 10000) / 100.0 : 0;
            Map<String, Object> resultMap = new LinkedHashMap<>();
            resultMap.put("label", label);
            resultMap.put("fixRate", fixRate);
            resultMap.put("fixEpochs", fixCount);
            resultMap.put("floatEpochs", floatCount);
            resultMap.put("singleEpochs", singleCount);
            resultMap.put("totalEpochs", total);
            resultMap.put("pairCount", pairSuccess);
            results.add(resultMap);

            writePosFile(syncEvalId, label, allEpochs);

            if (fixRate > bestRate) {
                bestRate = fixRate;
                bestIdx = pi;
            }
        }

        results.sort((a, b) -> Double.compare(
            ((Number) b.getOrDefault("fixRate", 0)).doubleValue(),
            ((Number) a.getOrDefault("fixRate", 0)).doubleValue()
        ));

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("results", results);
        resp.put("evalId", syncEvalId);
        if (firstError != null) resp.put("error", firstError);
        if (bestIdx >= 0 && bestIdx < paramSets.size()) {
            resp.put("bestIdx", bestIdx);
            resp.put("bestLabel", paramSets.get(bestIdx).getOrDefault("label", "?"));
        }

        return resp;
    }

    private void writePosFile(String evalId, String label, List<SolutionEpochDto> epochs) {
        try {
            Path dir = EVAL_BASE_DIR.resolve(evalId);
            Files.createDirectories(dir);
            String safeName = label.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]", "_");
            Path posFile = dir.resolve(safeName + ".pos");
            try (BufferedWriter w = Files.newBufferedWriter(posFile)) {
                w.write("%  Date       Time(mjd)    Lat(deg)     Lon(deg)    Height(m)  Q  ns  sdn(m)  sde(m)  sdu(m)  sdne(m)  sdeu(m)  sdun(m) age(s) ratio");
                w.newLine();
                for (SolutionEpochDto e : epochs) {
                    Instant inst = Instant.ofEpochMilli(e.getTimestamp());
                    LocalDateTime ldt = LocalDateTime.ofInstant(inst, ZoneOffset.UTC);
                    double mjd = 40587.0 + e.getTimestamp() / 86400000.0;
                    String modeStr = switch (e.getFixMode()) { case 1 -> "1"; case 2 -> "2"; default -> "5"; };
                    w.write(String.format(Locale.US,
                        "%s  %12.5f  %13.9f  %13.9f  %10.4f  %s  0  %7.4f  %7.4f  %7.4f  0.0000  0.0000  0.0000 %5.1f %6.2f",
                        ldt.format(POS_TIME_FMT), mjd,
                        e.getLatitude(), e.getLongitude(), e.getHeight(),
                        modeStr,
                        e.getNorthStd(), e.getEastStd(), e.getUpStd(),
                        e.getDiffAge(), e.getRatio()
                    ));
                    w.newLine();
                }
            }
        } catch (IOException ignored) {}
    }

    private List<SolutionEpochDto> readPosFile(Path posFile) throws IOException {
        List<SolutionEpochDto> epochs = new ArrayList<>();
        List<String> lines = Files.readAllLines(posFile);
        for (String line : lines) {
            if (line.startsWith("%") || line.trim().isEmpty()) continue;
            String[] p = line.trim().split("\\s+");
            if (p.length < 15) continue;
            try {
                String dateStr = p[0] + " " + p[1];
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");
                LocalDateTime ldt = LocalDateTime.parse(dateStr, fmt);
                long ts = ldt.toInstant(ZoneOffset.UTC).toEpochMilli();
                SolutionEpochDto e = new SolutionEpochDto();
                e.setTimestamp(ts);
                e.setLatitude(Double.parseDouble(p[4]));
                e.setLongitude(Double.parseDouble(p[5]));
                e.setHeight(Double.parseDouble(p[6]));
                int q = Integer.parseInt(p[7]);
                e.setFixMode(q == 1 ? 1 : (q == 2 ? 2 : 5));
                e.setNorthStd(p.length > 9 ? Double.parseDouble(p[9]) : 0);
                e.setEastStd(p.length > 10 ? Double.parseDouble(p[10]) : 0);
                e.setUpStd(p.length > 11 ? Double.parseDouble(p[11]) : 0);
                e.setDiffAge(p.length > 14 ? Double.parseDouble(p[14]) : 0);
                e.setRatio(p.length > 15 ? Double.parseDouble(p[15]) : 0);
                epochs.add(e);
            } catch (Exception ignored) {}
        }
        return epochs;
    }

    private List<Map<String, Object>> thinEpochs(List<SolutionEpochDto> epochs, String resolution, int days, int maxPoints) {
        List<SolutionEpochDto> filtered = new ArrayList<>(epochs);
        if (days > 0 && !filtered.isEmpty()) {
            long latestTs = filtered.get(filtered.size() - 1).getTimestamp();
            long cutoffTs = latestTs - (long) days * 86400000L;
            filtered.removeIf(e -> e.getTimestamp() < cutoffTs);
        }
        if (filtered.isEmpty()) return new ArrayList<>();

        Set<Integer> keepIndices = new LinkedHashSet<>();
        keepIndices.add(0);
        keepIndices.add(filtered.size() - 1);
        for (int i = 1; i < filtered.size(); i++) {
            if (filtered.get(i).getFixMode() != filtered.get(i - 1).getFixMode()) {
                keepIndices.add(i);
                if (i + 1 < filtered.size()) keepIndices.add(i + 1);
            }
        }

        int intervalSec = switch (resolution) {
            case "overview" -> 60;
            case "detail" -> 0;
            default -> 10;
        };

        if (intervalSec > 0 && filtered.size() > 1) {
            long startTs = filtered.get(0).getTimestamp();
            long intervalMs = intervalSec * 1000L;
            long nextTarget = startTs + intervalMs;
            for (int i = 1; i < filtered.size(); i++) {
                if (filtered.get(i).getTimestamp() >= nextTarget) {
                    keepIndices.add(i);
                    nextTarget = filtered.get(i).getTimestamp() + intervalMs;
                }
            }
        }

        List<SolutionEpochDto> thinned = new ArrayList<>();
        List<Integer> sortedIdx = new ArrayList<>(keepIndices);
        Collections.sort(sortedIdx);
        for (int idx : sortedIdx) {
            if (idx < filtered.size()) thinned.add(filtered.get(idx));
        }

        if (maxPoints > 0 && thinned.size() > maxPoints) {
            Set<Integer> finalKeep = new LinkedHashSet<>();
            finalKeep.add(0);
            finalKeep.add(thinned.size() - 1);
            for (int i = 1; i < thinned.size(); i++) {
                if (thinned.get(i).getFixMode() != thinned.get(i - 1).getFixMode()) {
                    finalKeep.add(i);
                }
            }
            int remaining = maxPoints - finalKeep.size();
            if (remaining > 0) {
                int step = Math.max(1, (thinned.size() - finalKeep.size()) / remaining);
                int count = 0;
                for (int i = 0; i < thinned.size(); i++) {
                    if (!finalKeep.contains(i)) {
                        count++;
                        if (count % step == 0) finalKeep.add(i);
                    }
                }
            }
            List<Integer> finalSorted = new ArrayList<>(finalKeep);
            Collections.sort(finalSorted);
            List<SolutionEpochDto> capped = new ArrayList<>();
            for (int idx : finalSorted) {
                if (idx < thinned.size()) capped.add(thinned.get(idx));
            }
            thinned = capped;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (SolutionEpochDto e : thinned) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("timestamp", e.getTimestamp());
            em.put("fixMode", e.getFixMode());
            em.put("latitude", e.getLatitude());
            em.put("longitude", e.getLongitude());
            em.put("height", e.getHeight());
            em.put("ratio", e.getRatio());
            em.put("diffAge", e.getDiffAge());
            em.put("northStd", e.getNorthStd());
            em.put("eastStd", e.getEastStd());
            em.put("upStd", e.getUpStd());
            result.add(em);
        }
        return result;
    }

    @GetMapping("/track-data")
    public Map<String, Object> trackData(
            @RequestParam String evalId,
            @RequestParam(defaultValue = "0") int paramIdx,
            @RequestParam(defaultValue = "standard") String resolution,
            @RequestParam(defaultValue = "0") int days,
            @RequestParam(defaultValue = "3000") int maxPoints) {
        String dirStr = evalDirCache.get(evalId);
        if (dirStr == null) {
            Path altDir = EVAL_BASE_DIR.resolve(evalId);
            if (Files.isDirectory(altDir)) {
                dirStr = altDir.toString();
                evalDirCache.put(evalId, dirStr);
            }
        }
        if (dirStr == null) {
            return Map.of("error", "评估不存在或已过期", "epochs", List.of());
        }

        Path dir = Paths.get(dirStr);
        try (var stream = Files.list(dir)) {
            List<Path> posFiles = stream
                .filter(f -> f.toString().endsWith(".pos"))
                .sorted()
                .toList();
            if (posFiles.isEmpty()) {
                return Map.of("error", "无POS文件", "epochs", List.of());
            }
            int idx = Math.min(paramIdx, posFiles.size() - 1);
            Path posFile = posFiles.get(idx);
            String label = posFile.getFileName().toString().replace(".pos", "");

            List<SolutionEpochDto> epochs = readPosFile(posFile);
            List<Map<String, Object>> thinned = thinEpochs(epochs, resolution, days, maxPoints);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("label", label);
            resp.put("totalEpochs", epochs.size());
            resp.put("returnedEpochs", thinned.size());
            resp.put("epochs", thinned);
            return resp;
        } catch (IOException e) {
            return Map.of("error", "读取POS文件失败: " + e.getMessage(), "epochs", List.of());
        }
    }

    @GetMapping("/track-files")
    public Map<String, Object> trackFiles(@RequestParam String evalId) {
        String dirStr = evalDirCache.get(evalId);
        if (dirStr == null) {
            Path altDir = EVAL_BASE_DIR.resolve(evalId);
            if (Files.isDirectory(altDir)) {
                dirStr = altDir.toString();
                evalDirCache.put(evalId, dirStr);
            }
        }
        if (dirStr == null) {
            return Map.of("error", "评估不存在或已过期", "files", List.of());
        }
        Path dir = Paths.get(dirStr);
        try (var stream = Files.list(dir)) {
            List<Map<String, Object>> files = stream
                .filter(f -> f.toString().endsWith(".pos"))
                .sorted()
                .map(f -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", f.getFileName().toString().replace(".pos", ""));
                    m.put("size", f.toFile().length());
                    return m;
                })
                .toList();
            return Map.of("files", files);
        } catch (IOException e) {
            return Map.of("error", "读取目录失败", "files", List.of());
        }
    }

    private Map<String, Object> normalizeParams(Map<String, Object> p) {
        Map<String, Object> norm = new LinkedHashMap<>(p);
        if (p.containsKey("elevationMask") && !p.containsKey("elevMask")) {
            norm.put("elevMask", p.get("elevationMask"));
        }
        List<String> systems = new ArrayList<>();
        if (Boolean.TRUE.equals(p.get("sysG"))) systems.add("GPS");
        if (Boolean.TRUE.equals(p.get("sysC"))) systems.add("BDS");
        if (!systems.isEmpty()) norm.put("systems", systems);
        List<String> freqs = new ArrayList<>();
        int nf = p.containsKey("nf") ? ((Number) p.get("nf")).intValue() : 2;
        if (nf >= 1) freqs.add("L1");
        if (nf >= 2) freqs.add("L2");
        if (nf >= 3) freqs.add("L5");
        if (!freqs.isEmpty()) norm.put("freqs", freqs);
        if (p.containsKey("fL1") || p.containsKey("fL2")) {
            freqs.clear();
            if (Boolean.TRUE.equals(p.get("fL1"))) freqs.add("L1");
            if (Boolean.TRUE.equals(p.get("fL2"))) freqs.add("L2");
            if (!freqs.isEmpty()) norm.put("freqs", freqs);
        }
        return norm;
    }

    private List<SolutionEpochDto> runSolveForEval(String mode, String roverFile, String baseFile, Map<String, Object> params) {
        int waitCount = 0;
        while (engine.isRunning() && waitCount < 100) {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            waitCount++;
        }
        if (engine.isRunning()) {
            engine.stop();
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }

        SolveRequest req = new SolveRequest();
        req.setMode(mode);
        req.setRoverFile(roverFile);
        req.setBaseFile(baseFile);
        req.setParams(params);

        List<SolutionEpochDto> epochs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalFail = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(1);

        PositioningEngine.MessageListener listener = new PositioningEngine.MessageListener() {
            @Override public void onLog(String msg) {}
            @Override public void onEpoch(SolveResult result) {
                SolutionEpochDto dto = new SolutionEpochDto();
                dto.setTimestamp(result.getTimestamp());
                dto.setFixMode(result.getFixMode());
                dto.setRatio(result.getRatio());
                dto.setDiffAge(result.getAge());
                dto.setLatitude(result.getLatitude());
                dto.setLongitude(result.getLongitude());
                dto.setHeight(result.getHeight());
                epochs.add(dto);
                totalSuccess.incrementAndGet();
            }
            @Override public void onDone(int total, int success, int fail) {
                totalFail.set(fail);
                done.countDown();
            }
            @Override public void onError(String error) {
                done.countDown();
            }
        };

        engine.addListener(listener);
        try {
            engine.start(req);
            if (!engine.isRunning()) {
                done.countDown();
            }
            boolean ok = done.await(180, TimeUnit.SECONDS);
            if (!ok) engine.stop();
            waitCount = 0;
            while (engine.isRunning() && waitCount < 100) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                waitCount++;
            }
            return epochs;
        } catch (InterruptedException e) {
            engine.stop();
            return epochs;
        } finally {
            engine.removeListener(listener);
        }
    }

    @PostMapping("/compare-modes")
    public Map<String, Object> compareModes(@RequestBody Map<String, Object> body) {
        return Map.of("results", List.of());
    }

    @GetMapping("/sample-skyplot")
    public Map<String, Object> sampleSkyPlot() {
        List<Map<String, Object>> sats = new ArrayList<>();
        String[] ids = {"G01","G03","G07","G08","G11","G17","G19","G28","C01","C03","C06","C09","C13","C16"};
        Random r = new Random(42);
        for (String sid : ids) {
            Map<String, Object> sat = new LinkedHashMap<>();
            sat.put("id", sid);
            sat.put("azimuth", Math.round(r.nextDouble() * 3600) / 10.0);
            sat.put("elevation", Math.round((10 + r.nextDouble() * 78) * 10) / 10.0);
            sat.put("snr", 28 + r.nextInt(22));
            sat.put("system", sid.startsWith("G") ? "GPS" : sid.startsWith("C") ? "BDS" : sid.startsWith("R") ? "GLO" : "GAL");
            sat.put("filtered", r.nextDouble() < 0.12);
            sats.add(sat);
        }
        return Map.of("satellites", sats, "gdop", 1.8, "hdop", 0.9, "vdop", 1.5, "pdop", 2.1, "epochCount", 14);
    }

    @PostMapping("/sample-snr-timeline")
    public Map<String, Object> sampleSnrTimeline(@RequestBody Map<String, Object> body) {
        int epochs = body.containsKey("epochs") ? ((Number) body.get("epochs")).intValue() : 60;
        String[] sats = {"G01","G03","G07","G11","G17","G19","G28","C01","C06"};
        Random r = new Random(7);
        List<Map<String, Object>> timeline = new ArrayList<>();
        long base = System.currentTimeMillis() - epochs * 1000L;
        for (int i = 0; i < epochs; i++) {
            Map<String, Object> epoch = new LinkedHashMap<>();
            epoch.put("t", base + i * 1000L);
            Map<String, Double> snrs = new LinkedHashMap<>();
            for (String s : sats) {
                double baseSnr = 35 + Math.sin(i * 0.1 + s.hashCode() * 0.3) * 8;
                snrs.put(s, Math.round((baseSnr + r.nextGaussian() * 2) * 10) / 10.0);
            }
            epoch.put("snrs", snrs);
            timeline.add(epoch);
        }
        return Map.of("timeline", timeline, "satIds", List.of(sats));
    }
}