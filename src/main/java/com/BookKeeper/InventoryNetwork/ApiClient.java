package com.BookKeeper.InventoryNetwork;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("InventoryNetwork-API");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Gson gson;
    private final String baseUrl;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Request a magic login link for the player.
     * Returns the magic URL that the player can click to login.
     */
    public MagicLinkResponse requestMagicLink(UUID mcUuid, String mcName) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("mcUuid", mcUuid.toString());
        requestBody.addProperty("mcName", mcName);

        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/mc/magic-link")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOGGER.error("Failed to request magic link: HTTP {}", response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            MagicLinkResponse result = new MagicLinkResponse();
            result.token = json.get("token").getAsString();
            result.magicUrl = json.get("magicUrl").getAsString();
            result.expiresAt = json.get("expiresAt").getAsString();
            result.isNewUser = json.get("isNewUser").getAsBoolean();

            return result;
        } catch (IOException e) {
            LOGGER.error("Failed to request magic link", e);
            return null;
        }
    }

    /**
     * Join a structure using a join code.
     */
    public JoinStructureResponse joinStructure(UUID mcUuid, String code) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("mcUuid", mcUuid.toString());
        requestBody.addProperty("code", code);

        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/mc/join-structure")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                // Try to extract error message from response
                try {
                    JsonObject errorJson = gson.fromJson(responseBody, JsonObject.class);
                    String detail = errorJson.has("detail") ? errorJson.get("detail").getAsString() : "Unknown error";
                    LOGGER.error("Failed to join structure: HTTP {} - {}", response.code(), detail);

                    JoinStructureResponse result = new JoinStructureResponse();
                    result.success = false;
                    result.message = detail;
                    return result;
                } catch (Exception e) {
                    LOGGER.error("Failed to join structure: HTTP {}", response.code());
                    JoinStructureResponse result = new JoinStructureResponse();
                    result.success = false;
                    result.message = "Failed to join structure (HTTP " + response.code() + ")";
                    return result;
                }
            }

            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            JoinStructureResponse result = new JoinStructureResponse();
            result.success = json.get("success").getAsBoolean();
            result.structureId = json.get("structureId").getAsString();
            result.structureName = json.get("structureName").getAsString();
            result.message = json.get("message").getAsString();

            return result;
        } catch (IOException e) {
            LOGGER.error("Failed to join structure", e);
            JoinStructureResponse result = new JoinStructureResponse();
            result.success = false;
            result.message = "Network error: " + e.getMessage();
            return result;
        }
    }

    /**
     * Exchange magic token for JWT access token.
     * This simulates the frontend's magic-login endpoint call.
     *
     * @param magicToken The magic token from requestMagicLink()
     * @return JWT access token, or null if exchange failed
     */
    public String exchangeMagicToken(String magicToken) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("token", magicToken);

        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/auth/magic-login")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOGGER.error("Failed to exchange magic token: HTTP {}", response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            // Extract JWT token from response (field is "access_token", not "token")
            if (json.has("access_token")) {
                String jwtToken = json.get("access_token").getAsString();
                LOGGER.info("Successfully exchanged magic token for JWT");
                return jwtToken;
            }

            LOGGER.error("No access_token in magic-login response. Response: {}", responseBody);
            return null;
        } catch (IOException e) {
            LOGGER.error("Failed to exchange magic token", e);
            return null;
        }
    }

    /**
     * Send chest data to backend for ChestSync feature.
     * This enables real-time chest inventory synchronization across all clients.
     *
     * @param jwtToken JWT access token for authentication
     * @param mcUuid Player UUID
     * @param mcName Player username
     * @param x Chest X coordinate
     * @param y Chest Y coordinate
     * @param z Chest Z coordinate
     * @param containerData Chest contents as JsonObject (items JSON)
     * @param signsData Signs data as JsonObject (optional)
     * @return true if successful, false otherwise
     */
    public boolean sendChestData(String jwtToken, UUID mcUuid, String mcName,
                                  int x, int y, int z,
                                  JsonObject containerData, JsonObject signsData) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("uuid", mcUuid.toString());
        requestBody.addProperty("username", mcName);
        requestBody.addProperty("x", x);
        requestBody.addProperty("y", y);
        requestBody.addProperty("z", z);
        requestBody.addProperty("event", "Container");
        requestBody.add("Container", containerData);
        if (signsData != null) {
            requestBody.add("Signs", signsData);
        }

        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/mc/events/jwt")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOGGER.error("Failed to send chest data: HTTP {} at ({}, {}, {})",
                        response.code(), x, y, z);
                return false;
            }

            LOGGER.debug("Successfully sent chest data at ({}, {}, {})", x, y, z);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to send chest data at ({}, {}, {})", x, y, z, e);
            return false;
        }
    }

    /**
     * Fetch all chest data from the server.
     * This is the REST API fallback when WebSocket is disconnected or data needs to be refreshed.
     * Server is the single source of truth for chest data.
     *
     * @param jwtToken JWT authentication token
     * @return JsonObject containing chest data, or null if failed
     */
    public JsonObject fetchAllChests(String jwtToken) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/mc/chests")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOGGER.error("Failed to fetch chest data: HTTP {}", response.code());
                return null;
            }

            String responseBody = response.body().string();
            JsonObject data = gson.fromJson(responseBody, JsonObject.class);
            LOGGER.info("Successfully fetched chest data from server");
            return data;
        } catch (IOException e) {
            LOGGER.error("Failed to fetch chest data from server", e);
            return null;
        }
    }

    /**
     * Upload a schematic file to the backend.
     * Uses multipart/form-data for file upload.
     *
     * @param jwtToken JWT access token for authentication
     * @param file Path to the schematic file
     * @param name Display name for the schematic
     * @return Schematic ID on success, or null on failure
     */
    public String uploadSchematic(String jwtToken, Path file, String name) {
        try {
            String fileName = file.getFileName().toString();
            byte[] fileBytes = Files.readAllBytes(file);

            RequestBody fileBody = RequestBody.create(fileBytes, MediaType.parse("application/octet-stream"));

            MultipartBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, fileBody)
                    .addFormDataPart("name", name)
                    .build();

            Request request = new Request.Builder()
                    .url(baseUrl + "/api/schematics/upload")
                    .addHeader("Authorization", "Bearer " + jwtToken)
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    LOGGER.error("Failed to upload schematic: HTTP {}", response.code());
                    return null;
                }

                String responseBody = response.body().string();
                JsonObject json = gson.fromJson(responseBody, JsonObject.class);

                if (json.has("id")) {
                    String schematicId = json.get("id").getAsString();
                    LOGGER.info("Successfully uploaded schematic '{}' with ID: {}", name, schematicId);
                    return schematicId;
                }

                LOGGER.error("No id in upload response. Response: {}", responseBody);
                return null;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to upload schematic '{}'", name, e);
            return null;
        }
    }

    /**
     * Download a schematic file from the backend.
     *
     * @param jwtToken JWT access token for authentication
     * @param id Schematic ID to download
     * @param target Target path to save the downloaded file
     * @return true on success, false on failure
     */
    public boolean downloadSchematic(String jwtToken, String id, Path target) {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/schematics/" + id + "/download")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOGGER.error("Failed to download schematic {}: HTTP {}", id, response.code());
                return false;
            }

            byte[] data = response.body().bytes();

            // Ensure parent directories exist
            Files.createDirectories(target.getParent());
            Files.write(target, data);

            LOGGER.info("Successfully downloaded schematic {} to {}", id, target);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to download schematic {}", id, e);
            return false;
        }
    }

    /**
     * Post split results from KDTreeSplitter to the backend.
     *
     * @param jwtToken JWT access token for authentication
     * @param schematicId Schematic ID the results belong to
     * @param jsonResults JSON string containing split results
     * @return true on success, false on failure
     */
    public boolean postSplitResults(String jwtToken, int schematicId, String jsonResults) {
        RequestBody body = RequestBody.create(jsonResults, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "/api/schematics/" + schematicId + "/split-results")
                .addHeader("Authorization", "Bearer " + jwtToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                LOGGER.error("Failed to post split results for schematic {}: HTTP {}", schematicId, response.code());
                return false;
            }

            LOGGER.info("Successfully posted split results for schematic {}", schematicId);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to post split results for schematic {}", schematicId, e);
            return false;
        }
    }

    // Response classes
    public static class MagicLinkResponse {
        public String token;
        public String magicUrl;
        public String expiresAt;
        public boolean isNewUser;
    }

    public static class JoinStructureResponse {
        public boolean success;
        public String structureId;
        public String structureName;
        public String message;
    }
}
