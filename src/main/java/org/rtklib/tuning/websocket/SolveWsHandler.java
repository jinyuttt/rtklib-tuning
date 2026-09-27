package org.rtklib.tuning.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rtklib.tuning.model.SolveResult;
import org.rtklib.tuning.service.ConfigManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SolveWsHandler extends TextWebSocketHandler {

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConfigManager configManager;

    private final java.util.function.Consumer<SolveResult> resultListener = result -> {
        try {
            String json = objectMapper.writeValueAsString(result);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            // ignore
        }
    };

    public SolveWsHandler(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        configManager.addResultListener(resultListener);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        if (sessions.isEmpty()) {
            configManager.removeResultListener(resultListener);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // client messages are ignored for now
    }
}