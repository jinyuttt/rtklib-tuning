package org.rtklib.tuning.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import java.util.regex.Pattern;

@Service
public class FileIndexService {

    public static final class StationInfo {
        public String stationId;
        public List<DateEntry> dates = new ArrayList<>();
        public boolean labeledAsBase;
    }

    public static final class DateEntry {
        public String date;
        public List<FileEntry> files = new ArrayList<>();
    }

    public static final class FileEntry {
        public String filePath;
        public String fileName;
        public long size;
        public String type;
        public int hour = -1;
    }

    public static final class ScanResult {
        public String rootPath;
        public List<StationInfo> stations = new ArrayList<>();
    }

    private static final Set<String> EXCLUDE_DIRS = Set.of("product", "solutions", "rinex", "temp", ".git");
    private static final Pattern DATE_PAT = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})$");
    private static final Pattern HOUR_PAT = Pattern.compile("^(\\d{1,2})\\.(rtcm3?|obs|pos|nav)$", Pattern.CASE_INSENSITIVE);

    public ScanResult scan(String rootPathStr) {
        ScanResult result = new ScanResult();
        result.rootPath = rootPathStr;

        Path root = Path.of(rootPathStr);
        if (!Files.isDirectory(root)) return result;

        List<Path> dirsToDelete = new ArrayList<>();

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(root, Files::isDirectory)) {
            for (Path dir : dirs) {
                String name = dir.getFileName().toString();
                if (EXCLUDE_DIRS.contains(name.toLowerCase())) continue;

                StationInfo st = new StationInfo();
                st.stationId = name;
                List<Path> empty = scanStation(dir, st);
                dirsToDelete.addAll(empty);
                if (!st.dates.isEmpty()) {
                    result.stations.add(st);
                } else {
                    dirsToDelete.add(dir);
                }
            }
        } catch (IOException ignored) {}

        result.stations.sort(Comparator.comparing(s -> s.stationId));

        if (!dirsToDelete.isEmpty()) {
            Thread cleanupThread = new Thread(() -> cleanupEmptyDirs(dirsToDelete), "scan-cleanup");
            cleanupThread.setDaemon(true);
            cleanupThread.start();
        }

        return result;
    }

    private void cleanupEmptyDirs(List<Path> dirs) {
        for (Path dir : dirs) {
            try {
                Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
        }
    }

    private List<Path> scanStation(Path stationDir, StationInfo st) {
        List<Path> empty = new ArrayList<>();
        try (DirectoryStream<Path> dateDirs = Files.newDirectoryStream(stationDir, Files::isDirectory)) {
            for (Path dateDir : dateDirs) {
                String dirName = dateDir.getFileName().toString();
                if (!DATE_PAT.matcher(dirName).matches()) continue;

                List<FileEntry> fileList = new ArrayList<>();
                boolean hasRtcm = false;

                try (DirectoryStream<Path> files = Files.newDirectoryStream(dateDir)) {
                    for (Path f : files) {
                        String fn = f.getFileName().toString();
                        FileEntry fe = new FileEntry();
                        fe.filePath = f.toAbsolutePath().toString();
                        fe.fileName = fn;
                        try { fe.size = Files.size(f); } catch (IOException e2) {}

                        String lower = fn.toLowerCase();
                        if (lower.endsWith(".rtcm3") || lower.endsWith(".rtcm")) {
                            fe.type = "rtcm";
                            hasRtcm = true;
                            var m = HOUR_PAT.matcher(fn);
                            if (m.matches()) fe.hour = Integer.parseInt(m.group(1));
                        } else if (lower.endsWith(".obs") || lower.contains("obs")) {
                            fe.type = "obs";
                        } else if (lower.endsWith(".pos")) {
                            fe.type = "pos";
                        } else if (lower.endsWith(".nav")) {
                            fe.type = "nav";
                        } else if (lower.endsWith(".txt")) {
                            fe.type = "stat";
                        } else {
                            fe.type = "other";
                        }
                        fileList.add(fe);
                    }
                }

                if (hasRtcm) {
                    DateEntry de = new DateEntry();
                    de.date = dirName;
                    de.files = fileList;
                    de.files.sort(Comparator.comparing(f -> f.fileName));
                    st.dates.add(de);
                } else {
                    empty.add(dateDir);
                }
            }
        } catch (IOException ignored) {}
        st.dates.sort(Comparator.comparing(d -> d.date));
        return empty;
    }

    public String getDateRange(List<DateEntry> dates) {
        if (dates.isEmpty()) return "--";
        return dates.get(0).date + " ~ " + dates.get(dates.size() - 1).date;
    }

    public int countFiles(List<DateEntry> dates, String type) {
        return dates.stream()
            .flatMap(d -> d.files.stream())
            .filter(f -> type.equals(f.type))
            .mapToInt(f -> 1).sum();
    }
}