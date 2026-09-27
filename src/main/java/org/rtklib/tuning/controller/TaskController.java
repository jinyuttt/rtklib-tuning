package org.rtklib.tuning.controller;

import org.rtklib.tuning.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<Map<String, Object>> list() throws IOException {
        return taskService.listTasks();
    }

    @GetMapping("/{id}")
    public Map<String, Object> getTask(@PathVariable String id,
                                       @RequestParam(defaultValue = "summary") String part) throws IOException {
        return taskService.loadTask(id, part);
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> task) throws IOException {
        return taskService.saveTask(task);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) throws IOException {
        return Map.of("success", taskService.deleteTask(id));
    }

    @DeleteMapping
    public Map<String, Object> clearAll() throws IOException {
        return Map.of("success", true, "deleted", taskService.clearAll());
    }
}