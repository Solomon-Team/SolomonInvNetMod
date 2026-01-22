package com.BookKeeper.InventoryNetwork.api;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * API interface for schematic synchronization between SolomonMatica and SolomonInvNetMod.
 * This allows SolomonMatica to use SolomonInvNetMod's WebSocket connection for schematic operations.
 */
public interface SchematicSyncApi {

    /**
     * Check if WebSocket is currently connected to backend.
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Get the current JWT token for authenticated API calls.
     *
     * @return JWT token, or null if not authenticated
     */
    String getJwtToken();

    /**
     * Get the backend API base URL.
     *
     * @return API base URL (e.g., "http://localhost:8000")
     */
    String getApiBaseUrl();

    /**
     * Upload a schematic file to the backend.
     *
     * @param path Path to the schematic file (.litematic)
     * @param name Display name for the schematic
     * @return CompletableFuture resolving to schematic ID on success, or null on failure
     */
    CompletableFuture<String> uploadSchematic(Path path, String name);

    /**
     * Download a schematic file from the backend.
     *
     * @param id Schematic ID to download
     * @param target Target path to save the downloaded file
     * @return CompletableFuture resolving to true on success, false on failure
     */
    CompletableFuture<Boolean> downloadSchematic(String id, Path target);

    /**
     * Send KDTreeSplitter split results to the backend.
     *
     * @param schematicId Schematic ID the results belong to
     * @param jsonResults JSON string containing split results (leaf bounds, metrics, materials)
     * @return CompletableFuture resolving to true on success, false on failure
     */
    CompletableFuture<Boolean> sendSplitResults(int schematicId, String jsonResults);

    /**
     * Register a callback to be notified when the server requests loading a schematic.
     *
     * @param callback Callback to invoke when load_schematic message is received
     */
    void registerLoadSchematicCallback(LoadSchematicCallback callback);

    /**
     * Unregister a previously registered callback.
     *
     * @param callback Callback to remove
     */
    void unregisterLoadSchematicCallback(LoadSchematicCallback callback);

    /**
     * Send acknowledgment for a load_schematic request.
     *
     * @param requestId Request ID from the load_schematic message
     * @param success Whether the schematic was loaded successfully
     * @param error Error message if unsuccessful, or null
     */
    void sendLoadSchematicAck(String requestId, boolean success, String error);

    /**
     * Callback interface for server-initiated schematic load requests.
     */
    interface LoadSchematicCallback {
        /**
         * Called when the server requests loading a schematic at specific coordinates.
         *
         * @param schematicId ID of the schematic to load
         * @param x X coordinate to place the schematic
         * @param y Y coordinate to place the schematic
         * @param z Z coordinate to place the schematic
         * @param requestId Unique request ID for acknowledgment
         */
        void onLoadSchematic(String schematicId, int x, int y, int z, String requestId);
    }
}
