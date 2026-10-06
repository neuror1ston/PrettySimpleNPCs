package ua.neuror1ston.prettysimplenpcs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ua.neuror1ston.prettysimplenpcs.data.DialogueData;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.data.TradeData;

import java.util.UUID;

public class NpcDataTest {

    @Test
    public void testNpcDataSerialization() {
        NpcData data = new NpcData("cr_guard_1");
        data.setName("Стражник Варты");
        data.setTitle("[Стража]");
        data.setScale(1.2f);
        data.setAiState(NpcData.AiState.PATROL);
        data.setBaseSpeed(0.32f);
        data.getIdleBarks().add("Стой, кто идет?");
        data.getIdleBarks().add("Ночью ворота закрыты.");

        String json = NpcData.GSON.toJson(data);
        Assertions.assertNotNull(json);

        NpcData parsed = NpcData.GSON.fromJson(json, NpcData.class);
        Assertions.assertEquals("cr_guard_1", parsed.getId());
        Assertions.assertEquals("Стражник Варты", parsed.getName());
        Assertions.assertEquals(1.2f, parsed.getScale(), 0.001);
        Assertions.assertEquals(NpcData.AiState.PATROL, parsed.getAiState());
        Assertions.assertEquals(2, parsed.getIdleBarks().size());
    }

    @Test
    public void testTradeCurrencyConversion() {
        // 10 silver = 1 gold
        TradeData.TradeEntry entry = new TradeData.TradeEntry("minecraft:iron_sword", 1, 5, 2);
        // 5 silver + 2 gold * 10 = 25 silver
        Assertions.assertEquals(25, entry.getTotalPriceInSilver());
    }

    @Test
    public void testDialogueDataSerialization() {
        DialogueData.Tree tree = new DialogueData.Tree("innkeeper", "Корчма");
        DialogueData.Node node = new DialogueData.Node("start", "Добро пожаловать в таверну!");
        DialogueData.Choice choice = new DialogueData.Choice("Дай мне эля", "ale");
        choice.getActions().add(new DialogueData.Action(DialogueData.Action.ActionType.GIVE_ITEM, "minecraft:potion", 1));

        node.getChoices().add(choice);
        tree.getNodes().put("start", node);

        String json = NpcData.GSON.toJson(tree);
        Assertions.assertTrue(json.contains("Добро пожаловать в таверну!"));

        DialogueData.Tree parsed = NpcData.GSON.fromJson(json, DialogueData.Tree.class);
        Assertions.assertEquals("innkeeper", parsed.getId());
        Assertions.assertEquals(1, parsed.getNode("start").getChoices().size());
    }
}
