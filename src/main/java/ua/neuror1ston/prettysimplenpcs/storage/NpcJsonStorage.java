package ua.neuror1ston.prettysimplenpcs.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
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
 * with instant hot-reloading support.
 */
public class NpcJsonStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT_DIR = FabricLoader.getInstance().getConfigDir().resolve(PrettySimpleNpcsMod.MOD_ID);
    private static final Path NPCS_DIR = ROOT_DIR.resolve("npcs");
    private static final Path DIALOGUES_DIR = ROOT_DIR.resolve("dialogues");
    private static final Path TRADES_DIR = ROOT_DIR.resolve("trades");
    private static final Path SKINS_DIR = ROOT_DIR.resolve("skins");

    private static final Map<String, NpcData> NPC_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, DialogueData.Tree> DIALOGUE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, TradeData.TradeMatrix> TRADE_CACHE = new ConcurrentHashMap<>();

    public static void init() {
        try {
            Files.createDirectories(NPCS_DIR);
            Files.createDirectories(DIALOGUES_DIR);
            Files.createDirectories(TRADES_DIR);
            Files.createDirectories(SKINS_DIR);
            reloadAll();
            createDefaultConfigsIfEmpty();
        } catch (IOException e) {
            PrettySimpleNpcsMod.LOGGER.error("Failed to initialize PrettySimpleNPCs storage directories", e);
        }
    }

    public static Path getSkinsDir() {
        return SKINS_DIR;
    }

    public static synchronized void reloadAll() {
        reloadNpcs();
        reloadDialogues();
        reloadTrades();
        PrettySimpleNpcsMod.LOGGER.info("Successfully reloaded all NPC data ({} npcs, {} dialogues, {} trades)",
                NPC_CACHE.size(), DIALOGUE_CACHE.size(), TRADE_CACHE.size());
    }

    public static synchronized void reloadNpcs() {
        NPC_CACHE.clear();
        if (!Files.exists(NPCS_DIR)) return;
        try (Stream<Path> stream = Files.walk(NPCS_DIR)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try (FileReader reader = new FileReader(path.toFile())) {
                    NpcData data = GSON.fromJson(reader, NpcData.class);
                    if (data != null && data.getId() != null) {
                        NPC_CACHE.put(data.getId(), data);
                    }
                } catch (Exception e) {
                    PrettySimpleNpcsMod.LOGGER.error("Failed to read NPC config: {}", path, e);
                }
            });
        } catch (IOException e) {
            PrettySimpleNpcsMod.LOGGER.error("Error reading NPCs directory", e);
        }
    }

    public static synchronized void reloadDialogues() {
        DIALOGUE_CACHE.clear();
        if (!Files.exists(DIALOGUES_DIR)) return;
        try (Stream<Path> stream = Files.walk(DIALOGUES_DIR)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try (FileReader reader = new FileReader(path.toFile())) {
                    DialogueData.Tree tree = GSON.fromJson(reader, DialogueData.Tree.class);
                    if (tree != null && tree.getId() != null) {
                        DIALOGUE_CACHE.put(tree.getId(), tree);
                    }
                } catch (Exception e) {
                    PrettySimpleNpcsMod.LOGGER.error("Failed to read dialogue file: {}", path, e);
                }
            });
        } catch (IOException e) {
            PrettySimpleNpcsMod.LOGGER.error("Error reading dialogues directory", e);
        }
    }

    public static synchronized void reloadTrades() {
        TRADE_CACHE.clear();
        if (!Files.exists(TRADES_DIR)) return;
        try (Stream<Path> stream = Files.walk(TRADES_DIR)) {
            stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try (FileReader reader = new FileReader(path.toFile())) {
                    TradeData.TradeMatrix matrix = GSON.fromJson(reader, TradeData.TradeMatrix.class);
                    if (matrix != null && matrix.getId() != null) {
                        TRADE_CACHE.put(matrix.getId(), matrix);
                    }
                } catch (Exception e) {
                    PrettySimpleNpcsMod.LOGGER.error("Failed to read trade matrix file: {}", path, e);
                }
            });
        } catch (IOException e) {
            PrettySimpleNpcsMod.LOGGER.error("Error reading trades directory", e);
        }
    }

    // NPC operations
    public static void saveNpc(NpcData data) {
        NPC_CACHE.put(data.getId(), data);
        File file = NPCS_DIR.resolve(data.getId() + ".json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            PrettySimpleNpcsMod.LOGGER.error("Failed to persist NPC config for {}", data.getId(), e);
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
        File file = NPCS_DIR.resolve(id + ".json").toFile();
        return file.exists() && file.delete();
    }

    // Dialogue operations
    public static void saveDialogue(DialogueData.Tree tree) {
        DIALOGUE_CACHE.put(tree.getId(), tree);
        File file = DIALOGUES_DIR.resolve(tree.getId() + ".json").toFile();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(tree, writer);
        } catch (IOException e) {
            PrettySimpleNpcsMod.LOGGER.error("Failed to persist dialogue for {}", tree.getId(), e);
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
        File file = TRADES_DIR.resolve(matrix.getId() + ".json").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(matrix, writer);
        } catch (IOException e) {
            PrettySimpleNpcsMod.LOGGER.error("Failed to persist trade matrix for {}", matrix.getId(), e);
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
