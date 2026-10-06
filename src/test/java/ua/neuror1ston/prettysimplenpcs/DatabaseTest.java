package ua.neuror1ston.prettysimplenpcs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.neuror1ston.prettysimplenpcs.database.DatabaseManager;

import java.nio.file.Path;
import java.util.UUID;

public class DatabaseTest {

    @BeforeEach
    public void setup(@TempDir Path tempDir) {
        DatabaseManager.init(tempDir);
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.close();
    }

    @Test
    public void testPlayerFlags() {
        UUID playerUuid = UUID.randomUUID();
        Assertions.assertFalse(DatabaseManager.hasFlag(playerUuid, "quest_completed"));

        DatabaseManager.setFlag(playerUuid, "quest_completed", "true");
        Assertions.assertTrue(DatabaseManager.hasFlag(playerUuid, "quest_completed"));
        Assertions.assertEquals("true", DatabaseManager.getFlag(playerUuid, "quest_completed", "false"));

        DatabaseManager.removeFlag(playerUuid, "quest_completed");
        Assertions.assertFalse(DatabaseManager.hasFlag(playerUuid, "quest_completed"));
    }

    @Test
    public void testTradeLimits() {
        UUID playerUuid = UUID.randomUUID();
        String entryId = "rare_scroll_1";

        int initialBought = DatabaseManager.getPurchasedCount(playerUuid, entryId, 3600);
        Assertions.assertEquals(0, initialBought);

        DatabaseManager.recordPurchase(playerUuid, entryId, 2);
        int afterFirstBuy = DatabaseManager.getPurchasedCount(playerUuid, entryId, 3600);
        Assertions.assertEquals(2, afterFirstBuy);

        DatabaseManager.recordPurchase(playerUuid, entryId, 3);
        int afterSecondBuy = DatabaseManager.getPurchasedCount(playerUuid, entryId, 3600);
        Assertions.assertEquals(5, afterSecondBuy);
    }
}
