package com.BookKeeper.InventoryNetwork;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class BookKeeperConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("InventoryNetwork-Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Configuration values
    private String apiBaseUrl = "http://localhost:8000";
    private boolean autoMagicLink = true;
    private int magicLinkCooldownSeconds = 60;

    // Singleton instance
    private static BookKeeperConfig instance;

    private BookKeeperConfig() {
    }

    public static BookKeeperConfig getInstance() {
        if (instance == null) {
            instance = new BookKeeperConfig();
        }
        return instance;
    }

    /**
     * Load configuration from file or create default if not exists.
     */
    public void load(File configFile) {
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                BookKeeperConfig loaded = GSON.fromJson(reader, BookKeeperConfig.class);
                if (loaded != null) {
                    this.apiBaseUrl = loaded.apiBaseUrl;
                    this.autoMagicLink = loaded.autoMagicLink;
                    this.magicLinkCooldownSeconds = loaded.magicLinkCooldownSeconds;
                    LOGGER.info("Loaded BookKeeper config from: {}", configFile.getAbsolutePath());
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load config file, using defaults", e);
            }
        } else {
            // Create default config file
            save(configFile);
            LOGGER.info("Created default BookKeeper config at: {}", configFile.getAbsolutePath());
        }
    }

    /**
     * Save current configuration to file.
     */
    public void save(File configFile) {
        try {
            // Create parent directories if needed
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config file", e);
        }
    }

    // Getters
    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public boolean isAutoMagicLink() {
        return autoMagicLink;
    }

    public int getMagicLinkCooldownSeconds() {
        return magicLinkCooldownSeconds;
    }

    // Setters (for runtime changes if needed)
    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public void setAutoMagicLink(boolean autoMagicLink) {
        this.autoMagicLink = autoMagicLink;
    }

    public void setMagicLinkCooldownSeconds(int magicLinkCooldownSeconds) {
        this.magicLinkCooldownSeconds = magicLinkCooldownSeconds;
    }
}
