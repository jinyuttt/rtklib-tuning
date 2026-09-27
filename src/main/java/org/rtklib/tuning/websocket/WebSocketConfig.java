package org.rtklib.tuning.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.rtklib.tuning.service.ConfigManager;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ConfigManager configManager;

    public WebSocketConfig(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new SolveWsHandler(configManager), "/ws/solve")
                .setAllowedOrigins("*");
    }
}