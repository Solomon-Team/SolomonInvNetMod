package com.BookKeeper.InventoryNetwork;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Singleton WebSocket manager for real-time communication with BookKeeper backend.
 * Handles connection lifecycle, automatic reconnection, and message routing.
 */
public class WebSocketManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("InventoryNetwork-WebSocket");
    private static WebSocketManager instance;

    private OkHttpClient client;
    private WebSocket webSocket;
    private String baseUrl;
    private String currentToken;
    private boolean isConnected = false;
    private long lastReconnectAttempt = 0;
    private int reconnectBackoff = 1000;  // Start at 1 second
    private static final int MAX_BACKOFF = 30000;  // Max 30 seconds

    private final Gson gson = new Gson();

    private WebSocketManager() {
        // OkHttp client with WebSocket support
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)  // No read timeout for long-lived WebSocket
                .writeTimeout(10, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)  // Automatic ping every 30 seconds
                .build();
    }

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    /**
     * Connect to WebSocket server with JWT token.
     *
     * @param baseUrl Backend API base URL (e.g., "http://localhost:8000")
     * @param jwtToken JWT access token from magic link authentication
     */
    public synchronized void connect(String baseUrl, String jwtToken) {
        if (isConnected) {
            LOGGER.warn("Already connected to WebSocket");
            return;
        }

        this.baseUrl = baseUrl;
        this.currentToken = jwtToken;

        // Convert HTTP URL to WebSocket URL
        String wsUrl = baseUrl.replace("http://", "ws://").replace("https://", "wss://");
        wsUrl += "/ws/mc?token=" + jwtToken;

        LOGGER.info("Connecting to WebSocket: {}", wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new BookKeeperWebSocketListener());
    }

    /**
     * Disconnect from WebSocket server.
     * Closes connection gracefully with normal closure code.
     */
    public synchronized void disconnect() {
        if (webSocket != null) {
            webSocket.close(1000, "Client disconnect");
            webSocket = null;
        }
        isConnected = false;
        currentToken = null;
        LOGGER.info("WebSocket disconnected");
    }

    /**
     * Attempt to reconnect to WebSocket server.
     * Uses exponential backoff to avoid hammering the server.
     */
    public synchronized void attemptReconnect() {
        if (isConnected || currentToken == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastReconnectAttempt < reconnectBackoff) {
            return;  // Too soon, wait for backoff period
        }

        lastReconnectAttempt = now;
        LOGGER.info("Attempting WebSocket reconnect (backoff: {}ms)", reconnectBackoff);

        connect(baseUrl, currentToken);
        reconnectBackoff = Math.min(reconnectBackoff * 2, MAX_BACKOFF);
    }

    /**
     * Check if WebSocket is currently connected.
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Send pong response to server ping.
     */
    private void sendPong() {
        if (!isConnected || webSocket == null) {
            return;
        }

        try {
            String pongJson = String.format(
                    "{\"type\":\"pong\",\"timestamp\":\"%s\"}",
                    Instant.now().toString()
            );
            webSocket.send(pongJson);
            LOGGER.debug("Sent pong to server");
        } catch (Exception e) {
            LOGGER.error("Failed to send pong", e);
        }
    }

    /**
     * Handle incoming WebSocket message from server.
     */
    private void handleMessage(String jsonText) {
        try {
            JsonObject json = gson.fromJson(jsonText, JsonObject.class);
            String type = json.get("type").getAsString();

            switch (type) {
                case "message":
                    handleBroadcastMessage(json);
                    break;
                case "ping":
                    sendPong();
                    break;
                case "connected":
                    int userId = json.get("user_id").getAsInt();
                    String username = json.get("username").getAsString();
                    String structureId = json.get("structure_id").getAsString();
                    LOGGER.info("WebSocket authenticated: user_id={}, username={}, structure={}",
                            userId, username, structureId);
                    break;
                default:
                    LOGGER.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse WebSocket message", e);
        }
    }

    /**
     * Handle broadcast message from server.
     * Formats with [SERVER] prefix and displays in Minecraft chat.
     */
    private void handleBroadcastMessage(JsonObject json) {
        String text = json.get("text").getAsString();
        String kind = json.get("kind").getAsString();

        // Format with [SERVER] prefix in gold color (§6)
        String formattedText = "§6[SERVER]§r " + text;

        // Display based on message kind
        switch (kind) {
            case "CHAT":
                displayChatMessage(formattedText, false);
                break;
            case "ACTIONBAR":
                displayChatMessage(formattedText, true);
                break;
            case "TITLE":
                // Future: implement title display
                displayChatMessage(formattedText, false);
                break;
            case "BOSSBAR":
                // Future: implement boss bar display
                displayChatMessage(formattedText, false);
                break;
            default:
                displayChatMessage(formattedText, false);
        }

        LOGGER.info("Received broadcast message: kind={}, text={}", kind, text);
    }

    /**
     * Display a message in Minecraft chat.
     * Must be executed on Minecraft main thread.
     *
     * @param text Message text (supports Minecraft formatting codes)
     * @param actionBar If true, displays in action bar; if false, displays in chat
     */
    private void displayChatMessage(String text, boolean actionBar) {
        // Execute on Minecraft main thread for thread safety
        Minecraft.getInstance().execute(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                Component msg = Component.literal(text);
                player.displayClientMessage(msg, actionBar);
            }
        });
    }

    /**
     * WebSocket event listener.
     * Handles connection lifecycle events and incoming messages.
     */
    private class BookKeeperWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket ws, Response response) {
            isConnected = true;
            reconnectBackoff = 1000;  // Reset backoff on successful connection
            LOGGER.info("WebSocket connected successfully");

            // Display connection message in chat
            displayChatMessage("§6[SERVER]§r Connected to BookKeeper", false);
        }

        @Override
        public void onMessage(WebSocket ws, String text) {
            LOGGER.debug("Received WebSocket message: {}", text);
            handleMessage(text);
        }

        @Override
        public void onFailure(WebSocket ws, Throwable t, Response response) {
            isConnected = false;
            LOGGER.error("WebSocket error: {}", t.getMessage());

            // Display disconnection message
            displayChatMessage("§c[SERVER]§r Lost connection to BookKeeper", false);

            // Schedule reconnect in background thread
            new Thread(this::scheduleReconnect).start();
        }

        @Override
        public void onClosing(WebSocket ws, int code, String reason) {
            LOGGER.info("WebSocket closing: code={}, reason={}", code, reason);
            ws.close(1000, null);
        }

        @Override
        public void onClosed(WebSocket ws, int code, String reason) {
            isConnected = false;
            LOGGER.info("WebSocket closed: code={}, reason={}", code, reason);

            // Only attempt reconnect if not normal closure
            if (code != 1000) {
                displayChatMessage("§c[SERVER]§r Disconnected from BookKeeper", false);
                new Thread(this::scheduleReconnect).start();
            }
        }

        /**
         * Schedule reconnection attempt with exponential backoff.
         */
        private void scheduleReconnect() {
            try {
                Thread.sleep(reconnectBackoff);
                attemptReconnect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
