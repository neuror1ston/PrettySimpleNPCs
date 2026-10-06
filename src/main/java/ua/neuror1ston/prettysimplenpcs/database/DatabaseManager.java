package ua.neuror1ston.prettysimplenpcs.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;

/**
 * Lightweight embedded SQLite database manager for persisting player flags,
 * dialogue states, and purchase limit cooldowns.
 */
public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("prettysimplenpcs-db");
    private static Connection connection;

    public static synchronized void init(Path worldRoot) {
        try {
            Class.forName("org.sqlite.JDBC");
            Path dbDir = worldRoot.resolve("prettysimplenpcs");
            Files.createDirectories(dbDir);
            Path dbFile = dbDir.resolve("player_data.db");

            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            connection = DriverManager.getConnection(url);

            try (Statement st = connection.createStatement()) {
                // Table for player flags & quest state
                st.execute("CREATE TABLE IF NOT EXISTS player_flags (" +
                        "player_uuid TEXT NOT NULL, " +
                        "flag_key TEXT NOT NULL, " +
                        "flag_value TEXT, " +
                        "updated_at INTEGER, " +
                        "PRIMARY KEY (player_uuid, flag_key))");

                // Table for active dialogue positions
                st.execute("CREATE TABLE IF NOT EXISTS dialogue_progress (" +
                        "player_uuid TEXT NOT NULL, " +
                        "dialogue_id TEXT NOT NULL, " +
                        "current_node TEXT, " +
                        "updated_at INTEGER, " +
                        "PRIMARY KEY (player_uuid, dialogue_id))");

                // Table for trade limits per player
                st.execute("CREATE TABLE IF NOT EXISTS trade_limits (" +
                        "player_uuid TEXT NOT NULL, " +
                        "trade_entry_id TEXT NOT NULL, " +
                        "purchased_count INTEGER DEFAULT 0, " +
                        "last_purchase_timestamp INTEGER, " +
                        "PRIMARY KEY (player_uuid, trade_entry_id))");
            }
            LOGGER.info("Connected to SQLite database at {}", dbFile);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize SQLite database", e);
        }
    }

    public static synchronized void setFlag(UUID player, String key, String value) {
        if (connection == null) return;
        String sql = "INSERT OR REPLACE INTO player_flags (player_uuid, flag_key, flag_value, updated_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, key);
            ps.setString(3, value);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Error setting flag for player {}", player, e);
        }
    }

    public static synchronized String getFlag(UUID player, String key, String defaultValue) {
        if (connection == null) return defaultValue;
        String sql = "SELECT flag_value FROM player_flags WHERE player_uuid = ? AND flag_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("flag_value");
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting flag for player {}", player, e);
        }
        return defaultValue;
    }

    public static synchronized boolean hasFlag(UUID player, String key) {
        return getFlag(player, key, null) != null;
    }

    public static synchronized void removeFlag(UUID player, String key) {
        if (connection == null) return;
        String sql = "DELETE FROM player_flags WHERE player_uuid = ? AND flag_key = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Error removing flag for player {}", player, e);
        }
    }

    public static synchronized int getPurchasedCount(UUID player, String tradeEntryId, int resetCooldownSeconds) {
        if (connection == null) return 0;
        String sql = "SELECT purchased_count, last_purchase_timestamp FROM trade_limits WHERE player_uuid = ? AND trade_entry_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, tradeEntryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("purchased_count");
                    long lastTime = rs.getLong("last_purchase_timestamp");
                    long now = System.currentTimeMillis();
                    if (resetCooldownSeconds > 0 && (now - lastTime) > (resetCooldownSeconds * 1000L)) {
                        // Cooldown expired, count is reset
                        return 0;
                    }
                    return count;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting purchased count for player {}", player, e);
        }
        return 0;
    }

    public static synchronized void recordPurchase(UUID player, String tradeEntryId, int count) {
        if (connection == null) return;
        int current = getPurchasedCount(player, tradeEntryId, -1);
        int newCount = current + count;
        String sql = "INSERT OR REPLACE INTO trade_limits (player_uuid, trade_entry_id, purchased_count, last_purchase_timestamp) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, tradeEntryId);
            ps.setInt(3, newCount);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Error recording purchase for player {}", player, e);
        }
    }

    public static synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
            connection = null;
        }
    }
}
