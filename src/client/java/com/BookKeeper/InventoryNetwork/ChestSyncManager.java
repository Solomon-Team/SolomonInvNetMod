package com.BookKeeper.InventoryNetwork;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages aggregated chest inventory data synchronized across all clients.
 * Stores chest snapshots received via WebSocket and provides query methods.
 * Server is the SINGLE SOURCE OF TRUTH - this class is only a cache.
 */
public class ChestSyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("InventoryNetwork-ChestSync");

    // Singleton instance
    private static ChestSyncManager instance;

    // Storage: Key = "x,y,z", Value = ChestSnapshot
    private final ConcurrentHashMap<String, ChestSnapshot> chestData = new ConcurrentHashMap<>();

    // Listeners for UI updates
    private final List<Consumer<ChestUpdate>> updateListeners = new ArrayList<>();

    private ChestSyncManager() {
        LOGGER.info("ChestSyncManager initialized");
    }

    public static synchronized ChestSyncManager getInstance() {
        if (instance == null) {
            instance = new ChestSyncManager();
        }
        return instance;
    }

    /**
     * Handle full chest state message from WebSocket.
     * Replaces all existing chest data with the provided state.
     */
    public void handleFullState(JsonObject message) {
        try {
            if (!message.has("chests")) {
                LOGGER.warn("Full state message missing 'chests' field");
                return;
            }

            var chestsArray = message.getAsJsonArray("chests");
            int count = 0;

            // Clear existing data
            chestData.clear();

            // Process each chest
            for (var element : chestsArray) {
                JsonObject chestObj = element.getAsJsonObject();
                ChestSnapshot snapshot = ChestSnapshot.fromJson(chestObj);
                String key = makeKey(snapshot.x, snapshot.y, snapshot.z);
                chestData.put(key, snapshot);
                count++;
            }

            LOGGER.info("Loaded full chest state: {} chests", count);

            // Notify listeners
            notifyListeners(new ChestUpdate(ChestUpdate.Type.FULL_STATE, null));

        } catch (Exception e) {
            LOGGER.error("Failed to handle full state message", e);
        }
    }

    /**
     * Handle incremental chest update message from WebSocket.
     * Updates a single chest in the local cache.
     */
    public void handleChestUpdate(JsonObject message) {
        try {
            if (!message.has("chest")) {
                LOGGER.warn("Chest update message missing 'chest' field");
                return;
            }

            JsonObject chestObj = message.getAsJsonObject("chest");
            ChestSnapshot snapshot = ChestSnapshot.fromJson(chestObj);
            String key = makeKey(snapshot.x, snapshot.y, snapshot.z);

            chestData.put(key, snapshot);
            LOGGER.debug("Updated chest at ({}, {}, {})", snapshot.x, snapshot.y, snapshot.z);

            // Notify listeners
            notifyListeners(new ChestUpdate(ChestUpdate.Type.INCREMENTAL, snapshot));

        } catch (Exception e) {
            LOGGER.error("Failed to handle chest update message", e);
        }
    }

    /**
     * Get chest data at specific coordinates.
     * @return ChestSnapshot or null if not found
     */
    public ChestSnapshot getChestAt(int x, int y, int z) {
        return chestData.get(makeKey(x, y, z));
    }

    /**
     * Get all chest snapshots.
     * @return Unmodifiable collection of all chests
     */
    public Collection<ChestSnapshot> getAllChests() {
        return Collections.unmodifiableCollection(chestData.values());
    }

    /**
     * Get total number of tracked chests.
     */
    public int getChestCount() {
        return chestData.size();
    }

    /**
     * Search for chests containing a specific item.
     * @param itemId Item identifier to search for
     * @return List of chests containing the item
     */
    public List<ChestSnapshot> findChestsWithItem(String itemId) {
        List<ChestSnapshot> results = new ArrayList<>();
        for (ChestSnapshot chest : chestData.values()) {
            if (chest.containsItem(itemId)) {
                results.add(chest);
            }
        }
        return results;
    }

    /**
     * Register a listener for chest data updates.
     * Useful for updating UI when new data arrives.
     */
    public void addUpdateListener(Consumer<ChestUpdate> listener) {
        updateListeners.add(listener);
    }

    /**
     * Clear all chest data (for disconnect/logout).
     */
    public void clear() {
        chestData.clear();
        LOGGER.info("Cleared all chest data");
    }

    /**
     * Refresh chest data from server using REST API.
     * Use this when:
     * - ChestSyncManager is empty and WebSocket hasn't delivered full state yet
     * - Data seems stale or uncertain
     * - Manual refresh is needed
     *
     * This is a fallback mechanism - normally WebSocket provides automatic updates.
     * Server is the single source of truth and will overwrite local cache.
     *
     * @param apiClient The API client instance
     * @param jwtToken JWT authentication token
     * @return CompletableFuture that completes when refresh is done
     */
    public CompletableFuture<Boolean> refreshFromServer(ApiClient apiClient, String jwtToken) {
        if (apiClient == null || jwtToken == null || jwtToken.isEmpty()) {
            LOGGER.warn("Cannot refresh: ApiClient or JWT token is null");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                LOGGER.info("Refreshing chest data from server (REST API fallback)...");
                JsonObject response = apiClient.fetchAllChests(jwtToken);

                if (response == null) {
                    LOGGER.error("Failed to fetch chest data from server");
                    return false;
                }

                // Parse and load the chest data
                if (response.has("chests")) {
                    var chestsArray = response.getAsJsonArray("chests");
                    int count = 0;

                    // Clear existing data (server is source of truth)
                    chestData.clear();

                    // Load fresh data from server
                    for (var element : chestsArray) {
                        JsonObject chestObj = element.getAsJsonObject();
                        ChestSnapshot snapshot = ChestSnapshot.fromJson(chestObj);
                        String key = makeKey(snapshot.x, snapshot.y, snapshot.z);
                        chestData.put(key, snapshot);
                        count++;
                    }

                    LOGGER.info("Refreshed {} chests from server", count);

                    // Notify listeners
                    notifyListeners(new ChestUpdate(ChestUpdate.Type.FULL_STATE, null));
                    return true;
                } else {
                    LOGGER.warn("Server response missing 'chests' field");
                    return false;
                }
            } catch (Exception e) {
                LOGGER.error("Error refreshing chest data from server", e);
                return false;
            }
        });
    }

    private String makeKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private void notifyListeners(ChestUpdate update) {
        for (Consumer<ChestUpdate> listener : updateListeners) {
            try {
                listener.accept(update);
            } catch (Exception e) {
                LOGGER.error("Error in chest update listener", e);
            }
        }
    }

    /**
     * Represents a single chest snapshot with items and metadata.
     */
    public static class ChestSnapshot {
        public final int x, y, z;
        public final JsonObject items;
        public final JsonObject signs;
        public final String openedByUuid;
        public final String openedByUsername;
        public final String lastSeenAt;

        public ChestSnapshot(int x, int y, int z, JsonObject items, JsonObject signs,
                           String openedByUuid, String openedByUsername, String lastSeenAt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.items = items != null ? items : new JsonObject();
            this.signs = signs;
            this.openedByUuid = openedByUuid;
            this.openedByUsername = openedByUsername;
            this.lastSeenAt = lastSeenAt;
        }

        public static ChestSnapshot fromJson(JsonObject json) {
            int x = json.get("x").getAsInt();
            int y = json.get("y").getAsInt();
            int z = json.get("z").getAsInt();

            JsonObject items = json.has("items") && !json.get("items").isJsonNull()
                    ? json.getAsJsonObject("items")
                    : new JsonObject();

            JsonObject signs = json.has("signs") && !json.get("signs").isJsonNull()
                    ? json.getAsJsonObject("signs")
                    : null;

            String openedByUuid = json.has("opened_by") && json.get("opened_by").isJsonObject()
                    ? json.getAsJsonObject("opened_by").get("uuid").getAsString()
                    : null;

            String openedByUsername = json.has("opened_by") && json.get("opened_by").isJsonObject()
                    ? json.getAsJsonObject("opened_by").get("username").getAsString()
                    : null;

            String lastSeenAt = json.has("last_seen_at")
                    ? json.get("last_seen_at").getAsString()
                    : null;

            return new ChestSnapshot(x, y, z, items, signs, openedByUuid, openedByUsername, lastSeenAt);
        }

        /**
         * Check if this chest contains a specific item.
         * @param itemId Item identifier (e.g., "minecraft:diamond")
         * @return true if chest contains the item
         */
        public boolean containsItem(String itemId) {
            if (items == null || items.size() == 0) {
                return false;
            }

            // Items are stored as {"slot_number": {"id": "item_id", "count": X, ...}, ...}
            for (String key : items.keySet()) {
                JsonObject itemObj = items.getAsJsonObject(key);
                if (itemObj.has("id")) {
                    String id = itemObj.get("id").getAsString();
                    if (id.equals(itemId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Get total count of a specific item in this chest.
         */
        public int getItemCount(String itemId) {
            int total = 0;
            if (items == null || items.size() == 0) {
                return 0;
            }

            for (String key : items.keySet()) {
                JsonObject itemObj = items.getAsJsonObject(key);
                if (itemObj.has("id") && itemObj.get("id").getAsString().equals(itemId)) {
                    if (itemObj.has("count")) {
                        total += itemObj.get("count").getAsInt();
                    }
                }
            }
            return total;
        }
    }

    /**
     * Represents a chest data update event.
     */
    public static class ChestUpdate {
        public enum Type {
            FULL_STATE,     // Complete state refresh
            INCREMENTAL     // Single chest update
        }

        public final Type type;
        public final ChestSnapshot chest;  // null for FULL_STATE

        public ChestUpdate(Type type, ChestSnapshot chest) {
            this.type = type;
            this.chest = chest;
        }
    }
}
