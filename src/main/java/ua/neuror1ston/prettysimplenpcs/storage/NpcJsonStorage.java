package ua.neuror1ston.prettysimplenpcs.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.neuror1ston.prettysimplenpcs.PrettySimpleNpcsMod;
import ua.neuror1ston.prettysimplenpcs.data.DialogueData;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.data.TradeData;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Storage manager for persistent declarative JSON configs (NPCs, Dialogues, Trades)
 * with strict per-world isolation and instant hot-reloading support.
 */
public class NpcJsonStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger("PrettySimpleNPCs-Storage");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Global directory in .minecraft/config/prettysimplenpcs/ for default templates and fallback assets
    private static final Path GLOBAL_DIR = resolveGlobalDir();
    private static final Path GLOBAL_SKINS_DIR = GLOBAL_DIR.resolve("skins");

    private static Path resolveGlobalDir() {
        try {
            if (FabricLoader.getInstance() != null && FabricLoader.getInstance().getConfigDir() != null) {
                return FabricLoader.getInstance().getConfigDir().resolve(PrettySimpleNpcsMod.MOD_ID);
            }
        } catch (Throwable ignored) {}
        return Path.of("config", PrettySimpleNpcsMod.MOD_ID);
    }

    // Per-world directories (set when a world save is loaded)
    private static Path rootDir;
    private static Path npcsDir;
    private static Path dialoguesDir;
    private static Path tradesDir;
    private static Path skinsDir;

    private static final Map<String, NpcData> NPC_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, DialogueData.Tree> DIALOGUE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, TradeData.TradeMatrix> TRADE_CACHE = new ConcurrentHashMap<>();

    /**
     * Initializes global templates directory on game start.
     */
    public static void init() {
        initGlobalTemplates();
    }

    public static void initGlobalTemplates() {
        try {
            Files.createDirectories(GLOBAL_DIR);
            Files.createDirectories(GLOBAL_SKINS_DIR);
            Files.createDirectories(GLOBAL_DIR.resolve("dialogues"));
            Files.createDirectories(GLOBAL_DIR.resolve("trades"));
        } catch (IOException e) {
            LOGGER.error("Failed to initialize global templates directory", e);
        }
    }

    /**
     * Initializes storage for a specific world save (e.g. saves/<worldName>/prettysimplenpcs/).
     */
    public static synchronized void initForWorld(Path worldSaveDir) {
        rootDir = worldSaveDir.resolve("prettysimplenpcs");
        npcsDir = rootDir.resolve("npcs");
        dialoguesDir = rootDir.resolve("dialogues");
        tradesDir = rootDir.resolve("trades");
        skinsDir = rootDir.resolve("skins");

        try {
            Files.createDirectories(npcsDir);
            Files.createDirectories(dialoguesDir);
            Files.createDirectories(tradesDir);
            Files.createDirectories(skinsDir);

            reloadAll();
            createDefaultConfigsIfEmpty();
            LOGGER.info("Initialized per-world NPC storage at {}", rootDir);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize per-world storage directories at {}", rootDir, e);
        }
    }

    public static synchronized void close() {
        NPC_CACHE.clear();
        DIALOGUE_CACHE.clear();
        TRADE_CACHE.clear();
        rootDir = null;
        npcsDir = null;
        dialoguesDir = null;
        tradesDir = null;
        skinsDir = null;
    }

    public static Path getSkinsDir() {
        if (skinsDir != null) {
            return skinsDir;
        }
        return GLOBAL_SKINS_DIR;
    }

    public static Path getNpcsDir() {
        return npcsDir;
    }

    public static Path getDialoguesDir() {
        return dialoguesDir;
    }

    public static Path getTradesDir() {
        return tradesDir;
    }

    public static synchronized void reloadAll() {
        reloadNpcs();
        reloadDialogues();
        reloadTrades();
        LOGGER.info("Successfully reloaded all per-world NPC data ({} npcs, {} dialogues, {} trades)",
                NPC_CACHE.size(), DIALOGUE_CACHE.size(), TRADE_CACHE.size());
    }

    public static synchronized void reloadNpcs() {
        NPC_CACHE.clear();
        if (npcsDir == null || !Files.exists(npcsDir)) return;
        try (Stream<Path> stream = Files.walk(npcsDir)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try (FileReader reader = new FileReader(path.toFile())) {
                    NpcData data = GSON.fromJson(reader, NpcData.class);
                    if (data != null && data.getId() != null) {
                        NPC_CACHE.put(data.getId(), data);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to read NPC config: {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Error reading NPCs directory: {}", npcsDir, e);
        }
    }

    public static synchronized void reloadDialogues() {
        DIALOGUE_CACHE.clear();
        if (dialoguesDir == null || !Files.exists(dialoguesDir)) return;
        try (Stream<Path> stream = Files.walk(dialoguesDir)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try (FileReader reader = new FileReader(path.toFile())) {
                    DialogueData.Tree tree = GSON.fromJson(reader, DialogueData.Tree.class);
                    if (tree != null && tree.getId() != null) {
                        DIALOGUE_CACHE.put(tree.getId(), tree);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to read dialogue file: {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Error reading dialogues directory: {}", dialoguesDir, e);
        }
    }

    public static synchronized void reloadTrades() {
        TRADE_CACHE.clear();
        if (tradesDir == null || !Files.exists(tradesDir)) return;
        try (Stream<Path> stream = Files.walk(tradesDir)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try (FileReader reader = new FileReader(path.toFile())) {
                    TradeData.TradeMatrix matrix = GSON.fromJson(reader, TradeData.TradeMatrix.class);
                    if (matrix != null && matrix.getId() != null) {
                        TRADE_CACHE.put(matrix.getId(), matrix);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to read trade matrix file: {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Error reading trades directory: {}", tradesDir, e);
        }
    }

    // NPC operations
    public static void saveNpc(NpcData data) {
        NPC_CACHE.put(data.getId(), data);
        if (npcsDir == null) return;
        File file = npcsDir.resolve(data.getId() + ".json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to persist NPC config for {}", data.getId(), e);
        }
    }

    public static NpcData getNpc(String id) {
        return NPC_CACHE.get(id);
    }

    public static Collection<NpcData> getAllNpcs() {
        return NPC_CACHE.values();
    }

    public static boolean deleteNpc(String id) {
        NPC_CACHE.remove(id);
        if (npcsDir == null) return false;
        File file = npcsDir.resolve(id + ".json").toFile();
        return file.exists() && file.delete();
    }

    // Dialogue operations
    public static void saveDialogue(DialogueData.Tree tree) {
        DIALOGUE_CACHE.put(tree.getId(), tree);
        if (dialoguesDir == null) return;
        File file = dialoguesDir.resolve(tree.getId() + ".json").toFile();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(tree, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to persist dialogue for {}", tree.getId(), e);
        }
    }

    public static DialogueData.Tree getDialogue(String id) {
        return DIALOGUE_CACHE.get(id);
    }

    public static Collection<DialogueData.Tree> getAllDialogues() {
        return DIALOGUE_CACHE.values();
    }

    // Trade operations
    public static void saveTradeMatrix(TradeData.TradeMatrix matrix) {
        TRADE_CACHE.put(matrix.getId(), matrix);
        if (tradesDir == null) return;
        File file = tradesDir.resolve(matrix.getId() + ".json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(matrix, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to persist trade matrix for {}", matrix.getId(), e);
        }
    }

    public static TradeData.TradeMatrix getTradeMatrix(String id) {
        return TRADE_CACHE.get(id);
    }

    public static Collection<TradeData.TradeMatrix> getAllTrades() {
        return TRADE_CACHE.values();
    }

    private static void createDefaultConfigsIfEmpty() {
        if (DIALOGUE_CACHE.isEmpty()) {
            DialogueData.Tree sampleDialogue = new DialogueData.Tree("sample_innkeeper", "Трактирщик Богдан");
            DialogueData.Node startNode = new DialogueData.Node("start", "Приветствую тебя, путник! Добро пожаловать в нашу таверну. Чего желаешь?");

            DialogueData.Choice choiceTrade = new DialogueData.Choice("Покажи, что у тебя есть на продажу.", "trade");
            DialogueData.Action openTradeAct = new DialogueData.Action(DialogueData.Action.ActionType.OPEN_TRADE, "");
            choiceTrade.getActions().add(openTradeAct);

            DialogueData.Choice choiceRumors = new DialogueData.Choice("Какие слухи ходят в округе?", "rumors");
            DialogueData.Choice choiceExit = new DialogueData.Choice("Мне пора идти.", "EXIT");

            startNode.getChoices().add(choiceTrade);
            startNode.getChoices().add(choiceRumors);
            startNode.getChoices().add(choiceExit);

            DialogueData.Node rumorsNode = new DialogueData.Node("rumors", "Говорят, в старой сторожевой башне по ночам видят странные огни... Будь осторожен на тракте.");
            DialogueData.Choice backChoice = new DialogueData.Choice("Спасибо за предупреждение.", "start");
            rumorsNode.getChoices().add(backChoice);

            sampleDialogue.getNodes().put("start", startNode);
            sampleDialogue.getNodes().put("rumors", rumorsNode);
            saveDialogue(sampleDialogue);
        }

        if (TRADE_CACHE.isEmpty()) {
            TradeData.TradeMatrix defaultTrades = new TradeData.TradeMatrix("default_trades", "Таверна: Припасы");
            TradeData.TradeEntry bread = new TradeData.TradeEntry("minecraft:bread", 2, 2, 0); // 2 серебра
            TradeData.TradeEntry potion = new TradeData.TradeEntry("minecraft:cooked_beef", 1, 5, 0); // 5 серебра
            defaultTrades.getEntries().add(bread);
            defaultTrades.getEntries().add(potion);
            saveTradeMatrix(defaultTrades);
        }
    }
}
