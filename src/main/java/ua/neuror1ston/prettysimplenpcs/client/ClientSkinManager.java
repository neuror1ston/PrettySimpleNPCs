package ua.neuror1ston.prettysimplenpcs.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.neuror1ston.prettysimplenpcs.PrettySimpleNpcsMod;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles skin texture loading, caching, rate-limited Mojang skin resolution,
 * and custom local skin textures without spamming Mojang servers.
 */
public class ClientSkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("prettysimplenpcs-skins");
    private static final Map<String, Identifier> CUSTOM_SKIN_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Identifier> NICK_SKIN_TEXTURES = new ConcurrentHashMap<>();
    private static final Set<String> PENDING_REQUESTS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Long> FAILED_REQUESTS = new ConcurrentHashMap<>();

    public static Identifier getNpcSkin(SimpleNpcEntity npc) {
        String source = npc.getSkinSourceValue();
        String val = npc.getSkinValue();

        if (NpcData.SkinSource.LOCAL_FILE.name().equalsIgnoreCase(source)) {
            if (val != null && !val.isEmpty()) {
                Identifier cached = CUSTOM_SKIN_TEXTURES.get(val);
                if (cached != null) return cached;

                Identifier loaded = loadLocalSkinFromDisk(val);
                if (loaded != null) {
                    CUSTOM_SKIN_TEXTURES.put(val, loaded);
                    return loaded;
                }
            }
        } else if (NpcData.SkinSource.PLAYER_NICK.name().equalsIgnoreCase(source)) {
            if (val != null && !val.trim().isEmpty()) {
                String cleanNick = val.trim().toLowerCase();
                Identifier cached = NICK_SKIN_TEXTURES.get(cleanNick);
                if (cached != null) return cached;

                // Rate-limited safe asynchronous resolution
                requestSkinSafely(cleanNick);
            }
        }

        // Safe fallback skin
        return DefaultSkinHelper.getTexture(npc.getUuid());
    }

    private static void requestSkinSafely(String username) {
        if (PENDING_REQUESTS.contains(username)) {
            return;
        }

        Long lastFail = FAILED_REQUESTS.get(username);
        if (lastFail != null && System.currentTimeMillis() - lastFail < 120_000L) { // 2 min cooldown on failure
            return;
        }

        PENDING_REQUESTS.add(username);

        CompletableFuture.runAsync(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();

                // 1. Try checking online player list first (instant & offline-friendly)
                if (client.getNetworkHandler() != null) {
                    for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                        if (entry.getProfile().getName().equalsIgnoreCase(username)) {
                            Identifier skin = entry.getSkinTexture();
                            if (skin != null) {
                                NICK_SKIN_TEXTURES.put(username, skin);
                                PENDING_REQUESTS.remove(username);
                                return;
                            }
                        }
                    }
                }

                // 2. Resolve UUID from Mojang API safely
                UUID playerUuid = resolveUuidFromMojang(username);
                if (playerUuid == null) {
                    FAILED_REQUESTS.put(username, System.currentTimeMillis());
                    PENDING_REQUESTS.remove(username);
                    return;
                }

                // 3. Load skin texture via Minecraft SkinProvider
                GameProfile profile = new GameProfile(playerUuid, username);
                client.getSkinProvider().loadSkin(profile, (type, identifier, texture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        NICK_SKIN_TEXTURES.put(username, identifier);
                    }
                }, true);

                PENDING_REQUESTS.remove(username);
            } catch (Throwable t) {
                FAILED_REQUESTS.put(username, System.currentTimeMillis());
                PENDING_REQUESTS.remove(username);
            }
        });
    }

    private static UUID resolveUuidFromMojang(String username) {
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Minecraft-Fabric-PrettySimpleNPCs");

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("id")) {
                        String idStr = json.get("id").getAsString();
                        // Format into standard UUID string with dashes
                        String formatted = idStr.replaceFirst(
                                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                                "$1-$2-$3-$4-$5"
                        );
                        return UUID.fromString(formatted);
                    }
                }
            }
        } catch (Exception ignored) {
            // Silently handle rate limits or offline network states
        }
        return null;
    }

    public static void registerCustomSkinBytes(String skinKey, byte[] bytes) {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            NativeImage image = NativeImage.read(bis);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier id = new Identifier(PrettySimpleNpcsMod.MOD_ID, "textures/skins/" + skinKey.replace(".png", ""));
            MinecraftClient.getInstance().execute(() -> {
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                CUSTOM_SKIN_TEXTURES.put(skinKey, id);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to register custom skin bytes for {}", skinKey, e);
        }
    }

    private static Identifier loadLocalSkinFromDisk(String filename) {
        Path path = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config")
                .resolve(PrettySimpleNpcsMod.MOD_ID)
                .resolve("skins")
                .resolve(filename);

        if (Files.exists(path)) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                NativeImage image = NativeImage.read(fis);
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier id = new Identifier(PrettySimpleNpcsMod.MOD_ID, "textures/skins/" + filename.replace(".png", ""));
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                return id;
            } catch (Exception e) {
                LOGGER.error("Failed to load skin from disk: {}", path, e);
            }
        }
        return null;
    }
}
