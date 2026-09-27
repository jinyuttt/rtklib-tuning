package org.rtklib.tuning.controller;

import org.rtklib.tuning.model.SolveRequest;
import org.rtklib.tuning.model.SolveResult;
import org.rtklib.tuning.service.PositioningEngine;
import org.rtklib.tuning.service.PositioningEngine.MessageListener;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/solve")
public class SolveController {

    private final PositioningEngine engine;

    private final Set<MessageListener> wsListeners = ConcurrentHashMap.newKeySet();
    private final Map<String, Object> solveResult = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> solveEpochs = Collections.synchronizedList(new ArrayList<>());
    private final List<String> solveLogs = Collections.synchronizedList(new ArrayList<>());
    private volatile MessageListener currentListener = null;

    public SolveController(PositioningEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("running", engine.isRunning());
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody SolveRequest req) {
        if (engine.isRunning()) {
            return Map.of("success", false, "error", "解算已在运行中");
        }

        solveResult.clear();
        solveEpochs.clear();
        solveLogs.clear();
        removeCurrentListener();

        MessageListener listener = new MessageListener() {
            @Override
            public void onLog(String msg) {
                solveLogs.add("[" + java.time.LocalTime.now().toString().substring(0, 8) + "] " + msg);
                for (MessageListener l : wsListeners) {
                    try { l.onLog(msg); } catch (Exception ignored) {}
                }
            }

            @Override
            public void onEpoch(SolveResult result) {
                Map<String, Object> ep = new LinkedHashMap<>();
                ep.put("timestamp", result.getTimestamp());
                ep.put("fixMode", result.getFixMode());
                ep.put("satCount", result.getSatCount());
                ep.put("latitude", result.getLatitude());
                ep.put("longitude", result.getLongitude());
                ep.put("height", result.getHeight());
                ep.put("age", result.getAge());
                ep.put("ratio", result.getRatio());
                ep.put("pdop", result.getPdop());
                solveEpochs.add(ep);
                for (MessageListener l : wsListeners) {
                    try { l.onEpoch(result); } catch (Exception ignored) {}
                }
            }

            @Override
            public void onDone(int totalEpochs, int successCount, int failCount) {
                long fixCount = solveEpochs.stream()
                        .filter(e -> Integer.valueOf(1).equals(e.get("fixMode")))
                        .count();
                long floatCount = solveEpochs.stream()
                        .filter(e -> Integer.valueOf(2).equals(e.get("fixMode")))
                        .count();
                long singleCount = solveEpochs.stream()
                        .filter(e -> Integer.valueOf(5).equals(e.get("fixMode")) || Integer.valueOf(6).equals(e.get("fixMode")))
                        .count();
                long epochTotal = solveEpochs.size();
                long validCount = fixCount + floatCount + singleCount;
                solveResult.put("totalEpochs", totalEpochs);
                solveResult.put("successCount", successCount > 0 ? successCount : validCount);
                solveResult.put("failCount", failCount > 0 && successCount == 0 ? (totalEpochs - epochTotal) : failCount);
                solveResult.put("fixCount", fixCount);
                solveResult.put("floatCount", floatCount);
                solveResult.put("singleCount", singleCount);
                solveResult.put("fixRate", epochTotal > 0 ? Math.round((double) fixCount / epochTotal * 1000) / 10.0 : 0);
                solveResult.put("floatRate", epochTotal > 0 ? Math.round((double) floatCount / epochTotal * 1000) / 10.0 : 0);
                solveResult.put("singleRate", epochTotal > 0 ? Math.round((double) singleCount / epochTotal * 1000) / 10.0 : 0);
                solveResult.put("validRate", epochTotal > 0 ? Math.round((double) validCount / totalEpochs * 1000) / 10.0 : 0);
                for (MessageListener l : wsListeners) {
                    try { l.onDone(totalEpochs, successCount, failCount); } catch (Exception ignored) {}
                }
            }

            @Override
            public void onError(String error) {
                solveResult.put("error", error);
                for (MessageListener l : wsListeners) {
                    try { l.onError(error); } catch (Exception ignored) {}
                }
            }
        };

        currentListener = listener;
        engine.addListener(listener);
        engine.start(req);

        return Map.of("success", true, "mode", req.getMode() != null ? req.getMode() : "kinematic");
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        engine.stop();
        removeCurrentListener();
        return Map.of("running", false);
    }

    @GetMapping("/result")
    public Map<String, Object> result() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("running", engine.isRunning());
        r.put("summary", new LinkedHashMap<>(solveResult));
        r.put("epochCount", solveEpochs.size());
        List<Map<String, Object>> recent = new ArrayList<>(solveEpochs);
        if (recent.size() > 5000) recent = recent.subList(recent.size() - 5000, recent.size());
        r.put("epochs", recent);
        return r;
    }

    @GetMapping("/logs")
    public Map<String, Object> logs() {
        List<String> recent = new ArrayList<>(solveLogs);
        if (recent.size() > 200) recent = recent.subList(recent.size() - 200, recent.size());
        return Map.of("logs", recent);
    }

    private void removeCurrentListener() {
        if (currentListener != null) {
            engine.removeListener(currentListener);
            currentListener = null;
        }
    }

    public void addWsListener(MessageListener l) { wsListeners.add(l); }
    public void removeWsListener(MessageListener l) { wsListeners.remove(l); }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) name = "upload.bin";
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "rtklib-upload");
        Files.createDirectories(dir);
        Path dest = dir.resolve(System.currentTimeMillis() + "_" + name);
        file.transferTo(dest.toFile());
        return Map.of("path", dest.toString(), "name", name, "size", file.getSize());
    }
}