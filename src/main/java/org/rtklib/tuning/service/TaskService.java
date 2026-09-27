package org.rtklib.tuning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TaskService {

    private static final Path TASK_ROOT = Path.of(System.getProperty("user.dir"), "tasks");
    private static final String INDEX_FILE = "task_index.json";
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public TaskService() throws IOException {
        Files.createDirectories(TASK_ROOT);
    }

    private Path indexPath() { return TASK_ROOT.resolve(INDEX_FILE); }

    public List<Map<String, Object>> listTasks() throws IOException {
        if (!Files.exists(indexPath())) return List.of();
        return mapper.readValue(indexPath().toFile(), List.class);
    }

    public Map<String, Object> saveTask(Map<String, Object> task) throws IOException {
        String id = "T" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dir = TASK_ROOT.resolve(id);
        Files.createDirectories(dir);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", id);
        meta.put("label", task.getOrDefault("label", ""));
        meta.put("type", task.getOrDefault("type", "single"));
        meta.put("rootPath", task.getOrDefault("rootPath", ""));
        meta.put("date", task.getOrDefault("date", ""));
        meta.put("pairs", task.getOrDefault("pairs", List.of()));
        meta.put("paramSets", task.getOrDefault("paramSets", List.of()));
        meta.put("evalStartHour", task.getOrDefault("evalStartHour", 0));
        meta.put("evalEndHour", task.getOrDefault("evalEndHour", 23));
        meta.put("bestParam", task.getOrDefault("bestParam", ""));
        meta.put("bestFixRate", task.getOrDefault("bestFixRate", 0));
        meta.put("pairCount", task.getOrDefault("pairCount", 0));
        meta.put("createdAt", LocalDateTime.now().toString());

        mapper.writeValue(dir.resolve("meta.json").toFile(), meta);
        mapper.writeValue(dir.resolve("summary.json").toFile(), task.getOrDefault("summary", Map.of()));
        mapper.writeValue(dir.resolve("details.json").toFile(), task.getOrDefault("details", Map.of()));

        List<Map<String, Object>> index;
        if (Files.exists(indexPath())) {
            index = mapper.readValue(indexPath().toFile(), List.class);
        } else {
            index = new ArrayList<>();
        }
        index.add(0, meta);
        mapper.writeValue(indexPath().toFile(), index);

        return meta;
    }

    public Map<String, Object> loadTask(String id, String part) throws IOException {
        Path dir = TASK_ROOT.resolve(id);
        if (!Files.isDirectory(dir)) throw new NoSuchFileException("Task not found: " + id);
        String file = "details".equals(part) ? "details.json" : "summary.json";
        Path fp = dir.resolve(file);
        if (!Files.exists(fp)) return Map.of();
        return mapper.readValue(fp.toFile(), Map.class);
    }

    public boolean deleteTask(String id) throws IOException {
        Path dir = TASK_ROOT.resolve(id);
        if (!Files.isDirectory(dir)) return false;
        try (var ds = Files.newDirectoryStream(dir)) { ds.forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) {} }); }
        Files.deleteIfExists(dir);

        if (Files.exists(indexPath())) {
            List<Map<String, Object>> index = mapper.readValue(indexPath().toFile(), List.class);
            index.removeIf(m -> id.equals(m.get("id")));
            mapper.writeValue(indexPath().toFile(), index);
        }
        return true;
    }

    public int clearAll() throws IOException {
        if (!Files.exists(indexPath())) return 0;
        List<Map<String, Object>> index = mapper.readValue(indexPath().toFile(), List.class);
        int count = index.size();
        for (Map<String, Object> m : index) {
            Path dir = TASK_ROOT.resolve((String) m.get("id"));
            if (Files.isDirectory(dir)) {
                try (var ds = Files.newDirectoryStream(dir)) { ds.forEach(f -> { try { Files.deleteIfExists(f); } catch (IOException ignored) {} }); }
                Files.deleteIfExists(dir);
            }
        }
        Files.deleteIfExists(indexPath());
        return count;
    }
}