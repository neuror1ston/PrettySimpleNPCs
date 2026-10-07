package ua.neuror1ston.prettysimplenpcs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;

import java.io.IOException;
import java.nio.file.Path;

public class WorldStorageIsolationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    public void cleanup() {
        NpcJsonStorage.close();
    }

    @Test
    public void testWorldsDoNotCrossContaminateNpcs() throws IOException {
        Path worldA = tempDir.resolve("world_a");
        Path worldB = tempDir.resolve("world_b");

        // 1. Enter World A and create an NPC
        NpcJsonStorage.initForWorld(worldA);
        Assertions.assertTrue(NpcJsonStorage.getAllNpcs().isEmpty(), "New World A should have 0 NPCs initially");

        NpcData npcA = new NpcData("guard_a");
        npcA.setName("Guard of Alpha");
        npcA.setDimension("minecraft:overworld");
        NpcJsonStorage.saveNpc(npcA);

        Assertions.assertEquals(1, NpcJsonStorage.getAllNpcs().size());
        Assertions.assertNotNull(NpcJsonStorage.getNpc("guard_a"));

        // 2. Leave World A
        NpcJsonStorage.close();
        Assertions.assertNull(NpcJsonStorage.getNpc("guard_a"));
        Assertions.assertTrue(NpcJsonStorage.getAllNpcs().isEmpty());

        // 3. Enter World B (e.g. flat test world)
        NpcJsonStorage.initForWorld(worldB);
        // CRITICAL CHECK: World B must NOT contain guard_a from World A!
        Assertions.assertNull(NpcJsonStorage.getNpc("guard_a"), "World B must not load NPC from World A!");
        Assertions.assertTrue(NpcJsonStorage.getAllNpcs().isEmpty(), "World B must have 0 NPCs initially");

        // Create NPC in World B
        NpcData npcB = new NpcData("merchant_b");
        npcB.setName("Merchant of Beta");
        npcB.setDimension("minecraft:the_nether");
        NpcJsonStorage.saveNpc(npcB);

        Assertions.assertEquals(1, NpcJsonStorage.getAllNpcs().size());
        Assertions.assertNotNull(NpcJsonStorage.getNpc("merchant_b"));
        Assertions.assertNull(NpcJsonStorage.getNpc("guard_a"));

        // 4. Return to World A
        NpcJsonStorage.close();
        NpcJsonStorage.initForWorld(worldA);

        Assertions.assertEquals(1, NpcJsonStorage.getAllNpcs().size());
        Assertions.assertNotNull(NpcJsonStorage.getNpc("guard_a"), "World A must preserve its own NPC");
        Assertions.assertNull(NpcJsonStorage.getNpc("merchant_b"), "World A must not load NPC from World B");
    }
}
