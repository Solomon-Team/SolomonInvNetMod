package com.BookKeeper.InventoryNetwork;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
