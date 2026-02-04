package com.BookKeeper.InventoryNetwork.api;

/**
 * Provider class for SchematicSyncApi.
 * This class is used by SolomonMatica (via reflection) to obtain the API instance.
 *
 * Usage from SolomonMatica:
 * <pre>
 * Class<?> providerClass = Class.forName("com.BookKeeper.InventoryNetwork.api.SchematicSyncApiProvider");
 * Method getApiMethod = providerClass.getMethod("getApi");
 * Object api = getApiMethod.invoke(null);
 * </pre>
 */
public class SchematicSyncApiProvider {
    private static SchematicSyncApi instance;

    /**
     * Get the SchematicSyncApi instance.
     *
     * @return The API instance, or null if not initialized
     */
    public static SchematicSyncApi getApi() {
        return instance;
    }

    /**
     * Set the SchematicSyncApi instance.
     * Called during mod initialization.
     *
     * @param api The API implementation
     */
    public static void setApi(SchematicSyncApi api) {
        instance = api;
    }

    /**
     * Check if the API is available.
     *
     * @return true if API is initialized, false otherwise
     */
    public static boolean isAvailable() {
        return instance != null;
    }
}
