package org.rtklib.tuning.controller;

import org.rtklib.tuning.model.DeviceInfo;
import org.rtklib.tuning.service.DeviceScanner;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private final DeviceScanner deviceScanner;

    public DeviceController(DeviceScanner deviceScanner) {
        this.deviceScanner = deviceScanner;
    }

    @GetMapping("/scan")
    public List<DeviceInfo> scan() {
        return deviceScanner.scanSerialPorts();
    }

    @PostMapping("/open")
    public Map<String, Object> open(@RequestBody Map<String, Object> body) {
        String port = (String) body.get("port");
        int baudRate = body.containsKey("baudRate") ? ((Number) body.get("baudRate")).intValue() : 115200;
        boolean success = deviceScanner.openPort(port, baudRate);
        return Map.of("success", success, "port", port, "baudRate", baudRate);
    }

    @PostMapping("/close")
    public Map<String, Object> close(@RequestBody Map<String, Object> body) {
        String port = (String) body.get("port");
        boolean success = deviceScanner.closePort(port);
        return Map.of("success", success, "port", port);
    }
}