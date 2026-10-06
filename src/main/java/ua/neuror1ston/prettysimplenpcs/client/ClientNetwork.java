package ua.neuror1ston.prettysimplenpcs.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import ua.neuror1ston.prettysimplenpcs.client.gui.AdminNpcScreen;
import ua.neuror1ston.prettysimplenpcs.client.gui.DialogueScreen;
import ua.neuror1ston.prettysimplenpcs.client.gui.TradeScreen;
import ua.neuror1ston.prettysimplenpcs.data.DialogueData;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles incoming server packets on the client and transitions into corresponding screens.
 */
public class ClientNetwork {

    public static void registerClientReceivers() {
        // S2C: Open Admin GUI
        ClientPlayNetworking.registerGlobalReceiver(NpcNetwork.OPEN_ADMIN_GUI_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            String json = buf.readString(32767);
            NpcData data = NpcData.GSON.fromJson(json, NpcData.class);

            int dialoguesCount = buf.readInt();
            List<String> dialogues = new ArrayList<>();
            java.util.Map<String, DialogueData.Tree> dialogueTrees = new java.util.HashMap<>();
            for (int i = 0; i < dialoguesCount; i++) {
                String id = buf.readString(256);
                String title = buf.readString(256);
                String treeJson = buf.readString(32767);
                dialogues.add(id);
                try {
                    DialogueData.Tree tree = NpcData.GSON.fromJson(treeJson, DialogueData.Tree.class);
                    if (tree != null) {
                        dialogueTrees.put(id, tree);
                    }
                } catch (Exception ignored) {}
            }

            int tradesCount = buf.readInt();
            List<String> trades = new ArrayList<>();
            for (int i = 0; i < tradesCount; i++) {
                String id = buf.readString(256);
                String title = buf.readString(256);
                trades.add(id);
            }

            client.execute(() -> {
                client.setScreen(new AdminNpcScreen(entityId, data, dialogues, dialogueTrees, trades));
            });
        });

        // S2C: Open or Update Dialogue Screen
        ClientPlayNetworking.registerGlobalReceiver(NpcNetwork.OPEN_DIALOGUE_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            String dialogueId = buf.readString(256);
            String dialogueTitle = buf.readString(256);
            String npcName = buf.readString(256);
            String nodeId = buf.readString(256);
            String text = buf.readString(32767);

            int choicesCount = buf.readInt();
            List<DialogueScreen.ChoiceEntry> choices = new ArrayList<>();
            for (int i = 0; i < choicesCount; i++) {
                String choiceText = buf.readString(256);
                String target = buf.readString(256);
                choices.add(new DialogueScreen.ChoiceEntry(choiceText, target));
            }

            client.execute(() -> {
                if (client.currentScreen instanceof DialogueScreen activeScreen) {
                    activeScreen.updateNode(text, choices);
                } else {
                    client.setScreen(new DialogueScreen(entityId, dialogueId, dialogueTitle, npcName, text, choices));
                }
            });
        });

        // S2C: Open Trade Screen
        ClientPlayNetworking.registerGlobalReceiver(NpcNetwork.OPEN_TRADE_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            String tradeId = buf.readString(256);
            String title = buf.readString(256);
            int silver = buf.readInt();
            int gold = buf.readInt();

            int count = buf.readInt();
            List<TradeScreen.TradeClientEntry> entries = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = buf.readString(256);
                String itemId = buf.readString(256);
                int itemCount = buf.readInt();
                int priceSilver = buf.readInt();
                int priceGold = buf.readInt();
                String poolType = buf.readString(64);
                int remaining = buf.readInt();
                entries.add(new TradeScreen.TradeClientEntry(id, itemId, itemCount, priceSilver, priceGold, poolType, remaining));
            }

            client.execute(() -> {
                client.setScreen(new TradeScreen(entityId, tradeId, title, silver, gold, entries));
            });
        });

        // S2C: Floating Idle Bark
        ClientPlayNetworking.registerGlobalReceiver(NpcNetwork.BARK_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            String bark = buf.readString(512);

            client.execute(() -> {
                if (client.world != null) {
                    Entity entity = client.world.getEntityById(entityId);
                    if (entity instanceof SimpleNpcEntity simpleNpc) {
                        simpleNpc.setClientBarkDisplay(bark);
                    }
                }
            });
        });

        // S2C: Custom Skin Texture sync
        ClientPlayNetworking.registerGlobalReceiver(NpcNetwork.SYNC_SKIN_S2C, (client, handler, buf, responseSender) -> {
            String npcId = buf.readString(256);
            int len = buf.readInt();
            byte[] skinBytes = new byte[len];
            buf.readBytes(skinBytes);

            client.execute(() -> {
                ClientSkinManager.registerCustomSkinBytes(npcId, skinBytes);
            });
        });
    }
}
