package com.yourmod;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class DiscordStatusMod implements DedicatedServerModInitializer {

    private DiscordBotClient botClient;
    private ConfigLoader.Config config;

    private final Set<String> onlineNames = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitializeServer() {
        this.config = ConfigLoader.load();

        if (config.botToken == null || config.botToken.trim().isEmpty()
                || config.botToken.equals("YOUR_BOT_TOKEN_HERE")
                || config.channelId == null || config.channelId.trim().isEmpty()
                || config.channelId.equals("YOUR_CHANNEL_ID_HERE")
                || !config.channelId.matches("\\d+")
                || config.voiceChannelId == null || config.voiceChannelId.trim().isEmpty()
                || config.voiceChannelId.equals("YOUR_VOICE_CHANNEL_ID_HERE")
                || !config.voiceChannelId.matches("\\d+")) {
            System.err.println(
                    "[discord-status] FATAL: Invalid config — bot will not start. Check config/discord-status.json");
            return;
        }

        // Register all Fabric events on the main thread BEFORE bot login.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (botClient != null) {
                String name = handler.getPlayer().getName().getString();
                if (name != null && !name.isBlank()) {
                    onlineNames.add(name);
                }
                botClient.updateStatusMessage(onlineNames);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (botClient != null) {
                String name = handler.getPlayer().getName().getString();
                if (name != null && !name.isBlank()) {
                    onlineNames.remove(name);
                }
                botClient.updateStatusMessage(onlineNames);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (botClient != null) {
                // Clear local state, and delete everything the bot posted/created
                onlineNames.clear();
                botClient.cleanupAllBotMessagesAndEvents();

                try {
                    botClient.renameChannelBlocking(config.offlineChannelName);
                    System.out.println("[discord-status] Server stopping — channel set to ❌ Offline");
                } catch (Exception e) {
                    System.err.println("[discord-status] Rename error: " + e.getMessage());
                } finally {
                    botClient.shutdown();
                    System.out.println("[discord-status] Bot disconnected safely.");
                }
            }
        });

        // Start bot async AFTER all events are registered
        CompletableFuture<DiscordBotClient> loginFuture = DiscordBotClient.loginAsync(
                config.botToken,
                config.channelId,
                config.voiceChannelId,
                config.eventNameFormat,
                config.eventLocation);
        loginFuture.whenComplete((client, throwable) -> {
            if (throwable != null) {
                System.err.println("[discord-status] Failed to start Discord bot: " + throwable.getMessage());
                return;
            }
            this.botClient = client;
            System.out.println("[discord-status] Discord bot logged in successfully.");

            try {
                botClient.renameChannelBlocking(config.onlineChannelName);
                System.out.println("[discord-status] Bot ready — channel set to ✅ Online");

                // Init status message immediately (empty set = 0 players)
                botClient.updateStatusMessage(onlineNames);
            } catch (Exception e) {
                System.err.println("[discord-status] Bot ready — failed to set channel: " + e.getMessage());
            }
        });
    }
}