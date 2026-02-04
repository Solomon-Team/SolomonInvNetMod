package com.BookKeeper.InventoryNetwork.api;

import com.BookKeeper.InventoryNetwork.ApiClient;
import com.BookKeeper.InventoryNetwork.WebSocketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation of SchematicSyncApi that bridges SolomonMatica to the backend
 * using the existing ApiClient and WebSocketManager.
 */
public class SchematicSyncApiImpl implements SchematicSyncApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("SolomonInvNet-SchematicSync");

    private final ApiClient apiClient;
    private final WebSocketManager webSocketManager;
    private final List<LoadSchematicCallback> callbacks = new CopyOnWriteArrayList<>();

    public SchematicSyncApiImpl(ApiClient apiClient, WebSocketManager webSocketManager) {
        this.apiClient = apiClient;
        this.webSocketManager = webSocketManager;
    }

    @Override
    public boolean isConnected() {
        return webSocketManager.isConnected();
    }

    @Override
    public String getJwtToken() {
        return webSocketManager.getJwtToken();
    }

    @Override
    public String getApiBaseUrl() {
        return apiClient.getBaseUrl();
    }

    @Override
    public CompletableFuture<String> uploadSchematic(Path path, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String token = getJwtToken();
            if (token == null) {
                LOGGER.error("Cannot upload schematic: not authenticated");
                return null;
            }

            try {
                String schematicId = apiClient.uploadSchematic(token, path, name);
                if (schematicId != null) {
                    LOGGER.info("Successfully uploaded schematic '{}' with ID: {}", name, schematicId);
                }
                return schematicId;
            } catch (Exception e) {
                LOGGER.error("Failed to upload schematic '{}'", name, e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> downloadSchematic(String id, Path target) {
        return CompletableFuture.supplyAsync(() -> {
            String token = getJwtToken();
            if (token == null) {
                LOGGER.error("Cannot download schematic: not authenticated");
                return false;
            }

            try {
                boolean success = apiClient.downloadSchematic(token, id, target);
                if (success) {
                    LOGGER.info("Successfully downloaded schematic {} to {}", id, target);
                }
                return success;
            } catch (Exception e) {
                LOGGER.error("Failed to download schematic {}", id, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> sendSplitResults(int schematicId, String jsonResults) {
        return CompletableFuture.supplyAsync(() -> {
            String token = getJwtToken();
            if (token == null) {
                LOGGER.error("Cannot send split results: not authenticated");
                return false;
            }

            try {
                boolean success = apiClient.postSplitResults(token, schematicId, jsonResults);
                if (success) {
                    LOGGER.info("Successfully sent split results for schematic {}", schematicId);
                }
                return success;
            } catch (Exception e) {
                LOGGER.error("Failed to send split results for schematic {}", schematicId, e);
                return false;
            }
        });
    }

    @Override
    public void registerLoadSchematicCallback(LoadSchematicCallback callback) {
        if (callback != null && !callbacks.contains(callback)) {
            callbacks.add(callback);
            LOGGER.debug("Registered LoadSchematicCallback: {}", callback.getClass().getName());
        }
    }

    @Override
    public void unregisterLoadSchematicCallback(LoadSchematicCallback callback) {
        if (callback != null) {
            callbacks.remove(callback);
            LOGGER.debug("Unregistered LoadSchematicCallback: {}", callback.getClass().getName());
        }
    }

    @Override
    public void sendLoadSchematicAck(String requestId, boolean success, String error) {
        webSocketManager.sendLoadSchematicAck(requestId, success, error);
    }

    /**
     * Called by WebSocketManager when a load_schematic message is received.
     * Notifies all registered callbacks.
     *
     * @param schematicId Schematic ID to load
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @param requestId Request ID for acknowledgment
     */
    public void notifyLoadSchematicCallbacks(String schematicId, int x, int y, int z, String requestId) {
        LOGGER.info("Received load_schematic request: schematic={}, pos=({}, {}, {}), requestId={}",
                schematicId, x, y, z, requestId);

        if (callbacks.isEmpty()) {
            LOGGER.warn("No LoadSchematicCallback registered - schematic load request will be ignored");
            sendLoadSchematicAck(requestId, false, "No schematic handler registered");
            return;
        }

        for (LoadSchematicCallback callback : callbacks) {
            try {
                callback.onLoadSchematic(schematicId, x, y, z, requestId);
            } catch (Exception e) {
                LOGGER.error("Error in LoadSchematicCallback: {}", callback.getClass().getName(), e);
            }
        }
    }
}
