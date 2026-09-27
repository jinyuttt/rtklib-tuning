package org.rtklib.tuning.service;

import com.fazecast.jSerialComm.SerialPort;
import org.rtklib.tuning.model.DeviceInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DeviceScanner {

    public List<DeviceInfo> scanSerialPorts() {
        List<DeviceInfo> devices = new ArrayList<>();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort port : ports) {
            DeviceInfo info = new DeviceInfo();
            info.setPortName(port.getSystemPortName());
            info.setDescription(port.getDescriptivePortName());
            info.setBaudRate(115200);
            info.setOpen(port.isOpen());
            devices.add(info);
        }
        return devices;
    }

    public boolean openPort(String portName, int baudRate) {
        SerialPort port = SerialPort.getCommPort(portName);
        if (port == null) return false;
        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(1);
        port.setParity(SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        return port.openPort();
    }

    public boolean closePort(String portName) {
        SerialPort port = SerialPort.getCommPort(portName);
        if (port == null || !port.isOpen()) return false;
        return port.closePort();
    }

    public SerialPort getPort(String portName) {
        return SerialPort.getCommPort(portName);
    }
}