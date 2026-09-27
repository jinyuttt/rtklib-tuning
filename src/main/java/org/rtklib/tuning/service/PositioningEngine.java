package org.rtklib.tuning.service;

import org.rtklib.java.constants.Constants;
import org.rtklib.java.data.*;
import org.rtklib.java.pntpos.PosHandler;
import org.rtklib.java.rinex.PostPosProcessor;
import org.rtklib.java.rinex.RtcmFileToRinexConverter;
import org.rtklib.java.rinex.PostPosProcessor.PostPosResult;
import org.rtklib.tuning.model.SolveRequest;
import org.rtklib.tuning.model.SolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Service
public class PositioningEngine {

    private static final Logger log = LoggerFactory.getLogger(PositioningEngine.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread solveThread;
    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    public interface MessageListener {
        void onLog(String msg);
        void onEpoch(SolveResult result);
        void onDone(int totalEpochs, int successCount, int failCount);
        void onError(String error);
    }

    public boolean isRunning() { return running.get(); }

    public void addListener(MessageListener l) { listeners.add(l); }
    public void removeListener(MessageListener l) { listeners.remove(l); }

    public void stop() {
        running.set(false);
        if (solveThread != null) solveThread.interrupt();
    }

    public void start(SolveRequest req) {
        if (!running.compareAndSet(false, true)) return;

        solveThread = new Thread(() -> {
            try { runSolve(req); } catch (Exception e) {
                log.error("solve error", e);
                emitError(e.getMessage());
                emitDone(0, 0, 0);
            } finally { running.set(false); }
        }, "rtk-solve");
        solveThread.setDaemon(true);
        solveThread.start();
    }

    private void runSolve(SolveRequest req) throws Exception {
        emitLog("解算准备中...");

        String mode = req.getMode() != null ? req.getMode() : "kinematic";
        Map<String, Object> p = req.getParams() != null ? req.getParams() : Map.of();

        if (req.getRoverFile() == null || req.getRoverFile().isEmpty()) {
            emitError("未指定测站/观测文件");
            return;
        }
        if (!Files.exists(Path.of(req.getRoverFile()))) {
            emitError("测站文件不存在: " + req.getRoverFile());
            return;
        }
        if (isRtkMode(mode) && (req.getBaseFile() == null || req.getBaseFile().isEmpty())) {
            emitError("RTK模式需要基站文件");
            return;
        }
        if (isRtkMode(mode) && !Files.exists(Path.of(req.getBaseFile()))) {
            emitError("基站文件不存在: " + req.getBaseFile());
            return;
        }

        Path tempDir = Files.createTempDirectory("rtk_solve_");
        Path obsFile = null, navFile = null, baseObsFile = null;

        try {
            if (isRtcm(req.getRoverFile())) {
                emitLog("转换测站 RTCM → RINEX: " + req.getRoverFile());
                RtcmFileToRinexConverter roverConv = new RtcmFileToRinexConverter(3.04, tempDir.toString(), "ROVER");
                roverConv.convert(req.getRoverFile());
                obsFile = Path.of(roverConv.getObsFilePath());
                Path autoNav = Path.of(roverConv.getNavFilePath());
                if (Files.exists(autoNav) && Files.size(autoNav) > 100) {
                    navFile = autoNav;
                }
            } else {
                obsFile = Path.of(req.getRoverFile());
                if (req.getNavFile() != null && !req.getNavFile().isEmpty()) {
                    navFile = Path.of(req.getNavFile());
                } else {
                    navFile = findMatchingNav(obsFile);
                    if (navFile != null) {
                        emitLog("自动匹配导航文件: " + navFile.getFileName());
                    }
                }
            }

            if (isRtkMode(mode) && req.getBaseFile() != null) {
                if (isRtcm(req.getBaseFile())) {
                    emitLog("转换基站 RTCM → RINEX: " + req.getBaseFile());
                    RtcmFileToRinexConverter baseConv = new RtcmFileToRinexConverter(3.04, tempDir.toString(), "BASE");
                    baseConv.convert(req.getBaseFile());
                    baseObsFile = Path.of(baseConv.getObsFilePath());
                    if (navFile == null) {
                        Path baseNav = Path.of(baseConv.getNavFilePath());
                        if (Files.exists(baseNav) && Files.size(baseNav) > 100) navFile = baseNav;
                    }
                } else {
                    baseObsFile = Path.of(req.getBaseFile());
                    if (navFile == null) navFile = findMatchingNav(baseObsFile);
                }
            }

            if (obsFile == null || !Files.exists(obsFile)) {
                emitError("观测文件未找到");
                return;
            }

            if (navFile != null && !Files.exists(navFile)) {
                emitLog("警告: 导航文件不存在，尝试忽略");
                navFile = null;
            }

            boolean needNav = !"kinematic".equals(mode) && !"static".equals(mode) && !"dgps".equals(mode);
            if (needNav && navFile == null) {
                emitError("SPP/PPP模式需要导航文件(.nav)，请在同目录放置或手动指定");
                return;
            }

            emitLog("开始解算 | 模式: " + mode);
            PrcOpt prcOpt = buildPrcOpt(mode, p);
            log.info("解算参数: mode={}, prcOpt.mode={}, navsys=0x{}, nf={}, elmin={}, snrmask[0]={}, ionoopt={}, tropopt={}",
                    mode, prcOpt.mode, Integer.toHexString(prcOpt.navsys), prcOpt.nf, prcOpt.elmin,
                    prcOpt.snrmask != null ? prcOpt.snrmask.mask[0] : 0, prcOpt.ionoopt, prcOpt.tropopt);
            SolOpt solOpt = new SolOpt();
            solOpt.posf = Constants.SOLF_LLH;
            solOpt.trace = 0;

            PosHandler handler = new PosHandler() {
                @Override
                public void onSolution(Sol sol, Ssat[] ssats) {
                    SolveResult r = toSolveResult(sol);
                    emitEpoch(r);
                }

                @Override
                public void onPosFail(GTime time, String reason) {
                    emitLog("历元失败: " + reason);
                }

                @Override
                public void onFinish(int total, int success, int fail) {
                }
            };

            PostPosProcessor processor = new PostPosProcessor(prcOpt, solOpt, handler);

            emitLog("文件: " + obsFile.getFileName());
            if (navFile != null) emitLog("导航: " + navFile.getFileName());
            if (baseObsFile != null) emitLog("基站: " + baseObsFile.getFileName());

            String obsPath = obsFile.toAbsolutePath().toString();
            String navPath = navFile != null ? navFile.toAbsolutePath().toString() : null;
            String basePath = baseObsFile != null ? baseObsFile.toAbsolutePath().toString() : null;

            PostPosResult result;
            try {
                result = runProcessor(processor, mode, obsPath, navPath, basePath, req);
            } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
                emitLog("RTKLIB内部异常: " + e.getMessage() + "，尝试降级重解(单频+无AR)...");
                log.warn("RTKLIB exception, retrying with nf=1, AR=off", e);
                PrcOpt fallbackOpt = buildPrcOpt(mode, p);
                fallbackOpt.nf = 1;
                fallbackOpt.modear = Constants.ARMODE_OFF;
                PostPosProcessor fallback = new PostPosProcessor(fallbackOpt, solOpt, handler);
                try {
                    result = runProcessor(fallback, mode, obsPath, navPath, basePath, req);
                } catch (Exception e2) {
                    emitLog("降级重解也失败: " + e2.getMessage());
                    log.error("Fallback solve also failed", e2);
                    result = new PostPosResult(0, 0, 0, List.of(), List.of());
                }
            }

            emitLog(String.format("完成: %d/%d成功, %d失败",
                    result.successCount, result.totalEpochs, result.failCount));
            emitDone(result.totalEpochs, result.successCount, result.failCount);

        } finally {
            deleteTempDir(tempDir);
        }
    }

    private PostPosResult runProcessor(PostPosProcessor processor, String mode,
                                       String obsPath, String navPath, String basePath,
                                       SolveRequest req) {
        if (isPppMode(mode)) {
            emitLog("PPP模式处理中...");
            String sp3Path = req.getSp3File() != null && !req.getSp3File().isEmpty() ? req.getSp3File() : null;
            String clkPath = req.getClkFile() != null && !req.getClkFile().isEmpty() ? req.getClkFile() : null;
            return processor.process(obsPath, navPath, basePath, sp3Path, clkPath, null);
        } else if (isRtkMode(mode)) {
            emitLog("RTK模式处理中...");
            return processor.process(obsPath, basePath, navPath);
        } else {
            emitLog("SPP模式处理中...");
            return processor.process(obsPath, navPath);
        }
    }

    private PrcOpt buildPrcOpt(String mode, Map<String, Object> p) {
        PrcOpt opt = new PrcOpt();

        opt.mode = switch (mode) {
            case "static" -> Constants.PMODE_STATIC;
            case "kinematic" -> Constants.PMODE_KINEMA;
            case "dgps" -> Constants.PMODE_DGPS;
            case "ppp-static" -> Constants.PMODE_PPP_STATIC;
            case "ppp-kinematic" -> Constants.PMODE_PPP_KINEMA;
            default -> Constants.PMODE_SINGLE;
        };

        int navsys = 0;
        List<String> systems = (List<String>) p.getOrDefault("systems", List.of("GPS", "BDS"));
        for (String s : systems) {
            switch (s.toUpperCase()) {
                case "GPS" -> navsys |= Constants.SYS_GPS;
                case "BDS" -> navsys |= Constants.SYS_CMP;
                case "GLO" -> navsys |= Constants.SYS_GLO;
                case "GAL" -> navsys |= Constants.SYS_GAL;
                case "QZS" -> navsys |= Constants.SYS_QZS;
            }
        }
        if (navsys == 0) navsys = Constants.SYS_GPS | Constants.SYS_CMP;
        opt.navsys = navsys;

        List<String> freqs = (List<String>) p.getOrDefault("freqs", List.of("L1", "L2"));
        opt.nf = Math.min(freqs.size(), 3);

        int elMask = p.containsKey("elevMask") ? ((Number) p.get("elevMask")).intValue() : 15;
        opt.elmin = elMask * Math.PI / 180.0;

        int snrDb = p.containsKey("snrMask") ? ((Number) p.get("snrMask")).intValue() : 30;
        opt.snrmask = new SnrMask();
        for (int i = 0; i < 9; i++) opt.snrmask.mask[i] = (double) snrDb;

        opt.sateph = 0;
        opt.ionoopt = p.containsKey("iono") ? ionoToInt((String) p.get("iono")) : Constants.IONOOPT_BRDC;
        opt.tropopt = p.containsKey("trop") ? tropToInt((String) p.get("trop")) : Constants.TROPOPT_SAAS;

        opt.modear = p.containsKey("modear") ? modearToInt((String) p.get("modear")) : Constants.ARMODE_CONT;
        if (p.containsKey("thresar")) {
            double thresar = ((Number) p.get("thresar")).doubleValue();
            opt.thresar[0] = thresar;
            opt.thresar[1] = 0.25;
        }
        opt.soltype = p.containsKey("soltype") ? soltypeToInt((String) p.get("soltype")) : 0;

        opt.dynamics = "off".equals(p.get("dynamics")) ? 0 : 1;
        opt.niter = 1;

        opt.maxtdiff = p.containsKey("maxAge") ? ((Number) p.get("maxAge")).doubleValue() : 30.0;

        if (p.containsKey("rejionno")) {
            double ratioVal = ((Number) p.get("rejionno")).doubleValue() / 10.0;
            opt.thresar[0] = ratioVal;
            opt.thresar[1] = 0.25;
        }

        return opt;
    }

    private SolveResult toSolveResult(Sol sol) {
        SolveResult r = new SolveResult();
        if (sol.time != null) {
            long gpsSec = sol.time.time;
            double frac = sol.time.sec;
            r.setTimestamp((gpsSec + 315964800L) * 1000L + (long)(frac * 1000));
        }
        r.setFixMode(sol.stat);
        r.setSatCount(sol.ns & 0xFF);
        r.setAge(sol.age);
        r.setRatio(sol.ratio);

        if (sol.rr != null && sol.rr.length >= 3) {
            double[] llh = ecef2llh(sol.rr[0], sol.rr[1], sol.rr[2]);
            r.setLatitude(llh[0] * 180.0 / Math.PI);
            r.setLongitude(llh[1] * 180.0 / Math.PI);
            r.setHeight(llh[2]);
        }
        r.setPdop(sol.pdop);
        r.setHdop(sol.hdop);
        r.setVdop(sol.vdop);
        return r;
    }

    private Path findMatchingNav(Path obsFile) {
        if (obsFile == null || !Files.exists(obsFile)) return null;
        try {
            Path parent = obsFile.getParent();
            if (parent == null) parent = Path.of(".");
            String name = obsFile.getFileName().toString();
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
                    if (ln.startsWith(base.toLowerCase()) && Files.isRegularFile(p) && Files.exists(p)) {
                        try { return Files.size(p) > 100; } catch (Exception e) { return false; }
                    }
                    return false;
                }).filter(p -> {
                    String ln = p.getFileName().toString().toLowerCase();
                    for (String ext : navExts) if (ln.endsWith(ext)) return true;
                    return false;
                }).findFirst().orElse(null);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRtcm(String path) {
        if (path == null || path.isEmpty()) return false;
        String l = path.toLowerCase();
        return l.endsWith(".rtcm3") || l.endsWith(".rtcm");
    }

    private boolean isRtkMode(String mode) {
        return "kinematic".equals(mode) || "static".equals(mode) || "dgps".equals(mode);
    }

    private boolean isPppMode(String mode) {
        return mode != null && mode.startsWith("ppp");
    }

    private int ionoToInt(String val) {
        return switch (val) {
            case "off" -> Constants.IONOOPT_OFF;
            case "brdc", "broadcast" -> Constants.IONOOPT_BRDC;
            case "sbas" -> Constants.IONOOPT_SBAS;
            case "iflc", "iono-free" -> Constants.IONOOPT_IFLC;
            case "est" -> Constants.IONOOPT_EST;
            default -> Constants.IONOOPT_BRDC;
        };
    }

    private int tropToInt(String val) {
        return switch (val) {
            case "off" -> Constants.TROPOPT_OFF;
            case "saas", "saastamoinen" -> Constants.TROPOPT_SAAS;
            case "sbas" -> Constants.TROPOPT_SBAS;
            case "est" -> Constants.TROPOPT_EST;
            default -> Constants.TROPOPT_SAAS;
        };
    }

    private int modearToInt(String val) {
        return switch (val) {
            case "off" -> Constants.ARMODE_OFF;
            case "continuous" -> Constants.ARMODE_CONT;
            case "instantaneous" -> Constants.ARMODE_INST;
            case "fix-and-hold" -> Constants.ARMODE_FIXHOLD;
            default -> Constants.ARMODE_CONT;
        };
    }

    private int soltypeToInt(String val) {
        return switch (val) {
            case "forward" -> 0;
            case "backward" -> 1;
            case "combined" -> 2;
            default -> 0;
        };
    }

    private void emitLog(String msg) {
        for (MessageListener l : listeners) {
            try { l.onLog(msg); } catch (Exception ignored) {}
        }
    }

    private void emitEpoch(SolveResult r) {
        for (MessageListener l : listeners) {
            try { l.onEpoch(r); } catch (Exception ignored) {}
        }
    }

    private void emitDone(int total, int success, int fail) {
        for (MessageListener l : listeners) {
            try { l.onDone(total, success, fail); } catch (Exception ignored) {}
        }
    }

    private void emitError(String msg) {
        for (MessageListener l : listeners) {
            try { l.onError(msg); } catch (Exception ignored) {}
        }
    }

    private double[] ecef2llh(double x, double y, double z) {
        double a = 6378137.0;
        double b = 6356752.314245;
        double e2 = (a * a - b * b) / (a * a);
        double r = Math.sqrt(x * x + y * y);
        double lat = Math.atan2(z, r * (1 - e2));
        for (int i = 0; i < 10; i++) {
            double N = a / Math.sqrt(1 - e2 * Math.sin(lat) * Math.sin(lat));
            lat = Math.atan2(z + e2 * N * Math.sin(lat), r);
        }
        double lon = Math.atan2(y, x);
        double N = a / Math.sqrt(1 - e2 * Math.sin(lat) * Math.sin(lat));
        double h = r / Math.cos(lat) - N;
        return new double[]{lat, lon, h};
    }

    private void deleteTempDir(Path dir) {
        try {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}