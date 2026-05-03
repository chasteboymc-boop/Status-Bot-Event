# Discord Status Mod (Fabric)

A server‑side only Fabric mod for **Minecraft 1.21.1** that updates a Discord channel name to reflect the server’s status.

- **✅ Online** – when the Minecraft server starts  
- **❌ Offline** – when the Minecraft server stops  

## Prerequisites

- Java 21 (or newer) installed and configured for Gradle.
- Fabric Loader 0.16.9 and Fabric API 0.102.0 (these are pulled automatically by the build script).
- A Discord bot token with the **Manage Channels** permission.
- The bot must have the **Server Members Intent** and **Message Content Intent** disabled (they are not needed).

## Setup Steps

1. **Create a Discord Bot**

   - Go to the [Discord Developer Portal](https://discord.com/developers/applications).
   - Create a new application → **Bot** → **Add Bot**.
   - Copy the **Bot Token** – you’ll need it for the config file.
   - Under **Privileged Gateway Intents**, you can leave everything disabled.
   - In **OAuth2 → URL Generator**, select the **bot** scope and enable the **Manage Channels** permission.
   - Use the generated URL to invite the bot to your server.

2. **Configure the Mod**

   The mod expects a JSON file at `config/discord-status.json`.  
   The first time the server runs, the mod will generate a placeholder file:

   ```json
   {
     "botToken": "ENTER YOUR BOT TOKEN",
     "channelId": "CHANNEL ID FOR SCAN PLAYER IN SẺVER"
   }
   ```

   - Replace `YOUR_BOT_TOKEN_HERE` with the token you copied earlier.
   - Replace `YOUR_CHANNEL_ID_HERE` with the **ID** of the text channel you want the mod to rename.  
     (Enable **Developer Mode** in Discord → right‑click the channel → **Copy ID**.)

3. **Build the Mod**

   ```bash
   ./gradlew shadowJar
   ```

   On Windows (PowerShell):

   ```powershell
   .\gradlew.bat shadowJar
   ```

   You can also run a full build (Windows / PowerShell):

   ```powershell
   .\gradlew.bat build
   ```

   The compiled JAR will appear in `build/libs/discord-status-mod-1.0.0-dev-shadow.jar`.  
   Copy this JAR into your server’s `mods/` folder.

4. **Run the Server**

   Start your Fabric server as usual.  
   - When the server finishes loading, the bot will rename the configured channel to **✅ Online**.  
   - When the server stops, the bot will rename the channel to **❌ Offline** (non‑blocking, so shutdown isn’t delayed).

## Notes & Best Practices

- **Never** hard‑code the bot token or channel ID in source files; they are read from the JSON config.
- The Discord client runs **asynchronously**; channel renames are queued and do not block the main server thread.
- Errors from the Discord API are logged to the console but do **not** crash the Minecraft server.
- This mod is **server‑side only** – it does not contain any client code.

## Development

If you wish to modify the mod:

```bash
# Install dependencies and generate IDE files
./gradlew genSources eclipse   # or ./gradlew genSources idea for IntelliJ
```

After making changes, rebuild with `./gradlew shadowJar` and replace the JAR in the server’s `mods/` folder.

---

Enjoy automatic Discord status updates for your Fabric server!