package com.yourmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Loads the Discord configuration from {@code config/discord-status.json}.
 * If the file does not exist, a placeholder file is generated with example
 * values.
 */
public final class ConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("config/discord-status.json");

    public static class Config {
        public String botToken;
        public String channelId;
        public String voiceChannelId;
        public String onlineChannelName = "✅•Online•✅";
        public String offlineChannelName = "❌•Offline•❌";
        public String eventNameFormat = "👤• Player: {count}";
        public String eventLocation = "Minecraft Server";
    }

    /**
     * Loads the configuration, creating a placeholder if necessary.
     *
     * @return the loaded configuration
     */
    public static Config load() {
        if (!CONFIG_FILE.getParentFile().exists()) {
            // Ensure the config directory exists
            // noinspection ResultOfMethodCallIgnored
            CONFIG_FILE.getParentFile().mkdirs();
        }

        if (!CONFIG_FILE.isFile()) {
            // Create placeholder config
            Config placeholder = new Config();
            placeholder.botToken = "YOUR_BOT_TOKEN_HERE";
            placeholder.channelId = "YOUR_CHANNEL_ID_HERE";
            placeholder.voiceChannelId = "YOUR_VOICE_CHANNEL_ID_HERE";
            placeholder.onlineChannelName = "✅•Online•✅";
            placeholder.offlineChannelName = "❌•Offline•❌";
            placeholder.eventNameFormat = "👤• Player: {count}";
            placeholder.eventLocation = "Minecraft Server";

            try (FileWriter writer = new FileWriter(CONFIG_FILE);
                    JsonWriter jsonWriter = new JsonWriter(writer)) {
                GSON.toJson(placeholder, Config.class, jsonWriter);
                System.out.println("[DiscordStatusMod] Generated placeholder config at " + CONFIG_FILE.getPath());
            } catch (IOException e) {
                System.err.println("[DiscordStatusMod] Failed to write placeholder config: " + e.getMessage());
            }
            return placeholder;
        }

        // Read existing config
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Config config = GSON.fromJson(reader, Config.class);
            if (config == null || config.botToken == null || config.channelId == null) {
                throw new JsonParseException("Missing required fields");
            }
            return config;
        } catch (IOException | JsonParseException e) {
            System.err.println("[DiscordStatusMod] Error reading config file: " + e.getMessage());
            // Return a placeholder to avoid NPEs; the bot will fail to login gracefully
            Config fallback = new Config();
            fallback.botToken = "";
            fallback.channelId = "";
            return fallback;
        }
    }
}