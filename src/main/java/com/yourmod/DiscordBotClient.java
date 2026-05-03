package com.yourmod;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.ScheduledEvent.Status;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles Discord bot lifecycle and channel renaming.
 */
public final class DiscordBotClient {

    private final JDA jda;
    private final String channelId;
    private final String voiceChannelId;
    private final String eventNameFormat;
    private final String eventLocation;

    /**
     * Single status message ID (contains player count + online player names).
     */
    private String lastStatusMessageId = null;

    /**
     * Last scheduled event ID created by this bot instance (used for replacing).
     */
    private String lastScheduledEventId = null;

    private DiscordBotClient(JDA jda, String channelId, String voiceChannelId, String eventNameFormat,
            String eventLocation) {
        this.jda = jda;
        this.channelId = channelId;
        this.voiceChannelId = voiceChannelId;
        this.eventNameFormat = eventNameFormat;
        this.eventLocation = eventLocation;
    }

    /**
     * Logs in the Discord bot asynchronously.
     *
     * @param token           Bot token
     * @param channelId       Channel ID to post status
     * @param voiceChannelId  Voice channel ID for scheduled events
     * @param eventNameFormat Format string for scheduled event name (with {count}
     *                        placeholder)
     * @param eventLocation   Location for scheduled events
     * @return a CompletableFuture that completes with the client instance
     */
    public static CompletableFuture<DiscordBotClient> loginAsync(String token, String channelId,
            String voiceChannelId, String eventNameFormat, String eventLocation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JDA jda = JDABuilder.createDefault(token)
                        .setAutoReconnect(true)
                        .build()
                        .awaitReady(); // block until fully logged in and gateway is ready
                return new DiscordBotClient(jda, channelId, voiceChannelId, eventNameFormat, eventLocation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Renames the configured channel and BLOCKS up to 8 seconds for completion.
     *
     * @param name new channel name
     * @throws Exception if rename fails or times out
     */
    public void renameChannelBlocking(String name) throws Exception {
        System.out.println("[discord-status] Attempting to rename channel to: " + name);

        GuildChannel channel = jda.getChannelById(GuildChannel.class, channelId);
        if (channel == null) {
            System.err.println("[discord-status] ERROR: Channel ID not found or bot lacks access: " + channelId);
            throw new IllegalStateException("Channel not found: " + channelId);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        channel.getManager().setName(name).queue(
                success -> {
                    System.out.println("[discord-status] Channel renamed to: " + name);
                    future.complete(null);
                },
                error -> {
                    // Rate limit can return a Retry-After in the warning logs; when the server is
                    // stopping
                    // we should not block the shutdown waiting for it.
                    System.err.println("[discord-status] Rename FAILED: " + error.getMessage());
                    future.completeExceptionally(error);
                });

        // IMPORTANT:
        // - This method is used from SERVER_STOPPING (server thread).
        // - If Discord responds with 429 (rate limit), JDA may delay the request for a
        // long time.
        // - Blocking the server thread here makes the server look "stuck on JDA
        // shutdown".
        //
        // We only wait a short amount of time; if it doesn't finish, we skip and
        // continue shutdown.
        try {
            future.get(2, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException timeout) {
            System.err.println("[discord-status] Rename timed out (skipping to allow server shutdown).");
        }
    }

    /**
     * Updates a single status message which includes both player count and the list
     * of online player names.
     * Strategy:
     * - Edit existing message if ID is known
     * - If not known or edit fails (message deleted manually), create a new one
     *
     * @param playerNames list/collection of online player names
     */
    public void updateStatusMessage(Collection<String> playerNames) {
        int count = playerNames == null ? 0 : playerNames.size();

        StringBuilder sb = new StringBuilder();
        if (count == 0) {
            sb.append("👤 Không có ai đang chơi.");
        } else {
            sb.append("👤 Đang có **").append(count).append("** người chơi.\n");
            for (String name : playerNames) {
                if (name == null || name.isBlank())
                    continue;
                sb.append("- ").append(name).append("\n");
            }
        }
        String text = sb.toString().trim();

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                if (lastStatusMessageId != null) {
                    channel.editMessageById(lastStatusMessageId, text).queue(
                            null,
                            e -> {
                                // Edit failed (message might be deleted), create new
                                lastStatusMessageId = null;
                                createNewStatusMessage(channel, text);
                            });
                } else {
                    createNewStatusMessage(channel, text);
                }
            }
        } catch (Exception e) {
            System.err.println("[discord-status] Error sending/editing status message: " + e.getMessage());
        }

        updateScheduledEvent(count);
    }

    private void createNewStatusMessage(TextChannel channel, String text) {
        channel.sendMessage(text).queue(
                msg -> {
                    lastStatusMessageId = msg.getId();
                    System.out.println("[discord-status] Status message created — message ID: " + msg.getId());
                },
                e -> System.err.println("[discord-status] Failed to send status message: " + e.getMessage()));
    }

    public void updateScheduledEvent(int count) {
        GuildChannel channel = jda.getChannelById(GuildChannel.class, channelId);
        if (channel == null)
            return;

        Guild guild = channel.getGuild();
        if (guild == null)
            return;

        String eventName = eventNameFormat.replace("{count}", String.valueOf(count));

        if (lastScheduledEventId != null) {
            ScheduledEvent oldEvent = guild.getScheduledEventById(lastScheduledEventId);
            if (oldEvent != null) {
                oldEvent.getManager().setName(eventName).queue(
                        v -> System.out.println("[discord-status] Scheduled event updated: " + eventName),
                        e -> {
                            // Edit failed, fallback to create
                            lastScheduledEventId = null;
                            createNewScheduledEvent(guild, eventName);
                        });
            } else {
                lastScheduledEventId = null;
                createNewScheduledEvent(guild, eventName);
            }
        } else {
            createNewScheduledEvent(guild, eventName);
        }
    }

    private void createNewScheduledEvent(Guild guild, String eventName) {
        guild.createScheduledEvent(
                eventName,
                eventLocation,
                OffsetDateTime.now().plusMinutes(1),
                OffsetDateTime.now().plusDays(365)) // End time set to 1 year later
                .queue(
                        event -> {
                            lastScheduledEventId = event.getId();
                            event.getManager().setStatus(Status.ACTIVE).queue(
                                    v -> System.out
                                            .println("[discord-status] Scheduled event started (ACTIVE): " + eventName),
                                    e -> System.err.println(
                                            "[discord-status] Failed to start scheduled event: " + e.getMessage()));
                        },
                        e -> System.err
                                .println("[discord-status] Failed to create scheduled event: " + e.getMessage()));
    }

    /**
     * Delete ALL scheduled events created by this bot (best-effort, non-blocking).
     */
    public void deleteAllBotScheduledEvents() {
        GuildChannel channel = jda.getChannelById(GuildChannel.class, channelId);
        if (channel == null)
            return;

        Guild guild = channel.getGuild();
        if (guild == null)
            return;

        for (ScheduledEvent event : guild.getScheduledEvents()) {
            if (event.getCreatorId().equals(jda.getSelfUser().getId())) {
                event.delete().queue(
                        v -> System.out.println("[discord-status] Deleted event: " + event.getName()),
                        e -> System.err.println("[discord-status] Failed to delete event: " + e.getMessage()));
            }
        }

        lastScheduledEventId = null;
    }

    /**
     * Deletes the stored status message if it exists. Fire-and-forget.
     */
    public void deleteStatusMessage() {
        if (lastStatusMessageId != null) {
            try {
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    channel.deleteMessageById(lastStatusMessageId).queue(null, e -> {
                    });
                }
            } catch (Exception ignored) {
            }
            lastStatusMessageId = null;
        }
    }

    /**
     * Convenience: delete status message + delete all bot-created events.
     */
    public void cleanupAllBotMessagesAndEvents() {
        deleteStatusMessage();
        deleteAllBotScheduledEvents();
    }

    /**
     * Gracefully shuts down the Discord client.
     */
    public void shutdown() {
        if (jda != null) {
            // shutdownNow() cancels pending requests to speed up JVM exit during server
            // stop
            jda.shutdownNow();
        }
    }
}