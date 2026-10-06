package ua.neuror1ston.prettysimplenpcs.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ua.neuror1ston.prettysimplenpcs.PrettySimpleNpcsMod;
import ua.neuror1ston.prettysimplenpcs.data.ActionProcessor;
import ua.neuror1ston.prettysimplenpcs.data.DialogueData;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.data.TradeData;
import ua.neuror1ston.prettysimplenpcs.database.DatabaseManager;
import ua.neuror1ston.prettysimplenpcs.entity.NpcLifecycleManager;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Handles all client-server networking packets for GUI, dialogues, trades, barks, and skins.
 */
public class NpcNetwork {
    public static final Identifier OPEN_ADMIN_GUI_S2C = new Identifier(PrettySimpleNpcsMod.MOD_ID, "open_admin_gui");
    public static final Identifier UPDATE_NPC_C2S = new Identifier(PrettySimpleNpcsMod.MOD_ID, "update_npc");
    public static final Identifier DELETE_NPC_C2S = new Identifier(PrettySimpleNpcsMod.MOD_ID, "delete_npc");
    public static final Identifier UPLOAD_SKIN_C2S = new Identifier(PrettySimpleNpcsMod.MOD_ID, "upload_skin");
    public static final Identifier SYNC_SKIN_S2C = new Identifier(PrettySimpleNpcsMod.MOD_ID, "sync_skin");

    public static final Identifier OPEN_DIALOGUE_S2C = new Identifier(PrettySimpleNpcsMod.MOD_ID, "open_dialogue");
    public static final Identifier DIALOGUE_CHOICE_C2S = new Identifier(PrettySimpleNpcsMod.MOD_ID, "dialogue_choice");

    public static final Identifier OPEN_TRADE_S2C = new Identifier(PrettySimpleNpcsMod.MOD_ID, "open_trade");
    public static final Identifier PERFORM_TRADE_C2S = new Identifier(PrettySimpleNpcsMod.MOD_ID, "perform_trade");

    public static final Identifier BARK_S2C = new Identifier(PrettySimpleNpcsMod.MOD_ID, "bark");
    public static final Identifier SAVE_DIALOGUE_C2S = new Identifier(PrettySimpleNpcsMod.MOD_ID, "save_dialogue");

    public static void registerServerReceivers() {
        // C2S: Update NPC from Admin GUI
        ServerPlayNetworking.registerGlobalReceiver(UPDATE_NPC_C2S, (server, player, handler, buf, responseSender) -> {
            if (!player.hasPermissionLevel(2)) return;
            String json = buf.readString(32767);
            server.execute(() -> {
                try {
                    NpcData updated = NpcData.GSON.fromJson(json, NpcData.class);
                    if (updated != null && updated.getId() != null) {
                        NpcJsonStorage.saveNpc(updated);
                        SimpleNpcEntity entity = NpcLifecycleManager.getEntity(updated.getId());
                        if (entity != null) {
                            entity.setNpcData(updated);
                        }
                        player.sendMessage(Text.literal("§aПараметры NPC '" + updated.getId() + "' успешно сохранены!"), true);
                    }
                } catch (Exception e) {
                    PrettySimpleNpcsMod.LOGGER.error("Failed to parse UpdateNpc packet", e);
                }
            });
        });

        // C2S: Delete NPC completely
        ServerPlayNetworking.registerGlobalReceiver(DELETE_NPC_C2S, (server, player, handler, buf, responseSender) -> {
            if (!player.hasPermissionLevel(2)) return;
            int entityId = buf.readInt();
            String id = buf.readString(256);
            server.execute(() -> {
                // 1. Direct world entity removal by entityId
                net.minecraft.entity.Entity target = player.getWorld().getEntityById(entityId);
                if (target instanceof SimpleNpcEntity sn) {
                    sn.markDeleted();
                    NpcLifecycleManager.unregisterEntity(sn);
                }

                // 2. Lifecycle manager tracked entity
                SimpleNpcEntity tracked = NpcLifecycleManager.getEntity(id);
                if (tracked != null) {
                    tracked.markDeleted();
                    NpcLifecycleManager.unregisterEntity(tracked);
                }

                // 3. Scan all loaded worlds for any residual matching instance
                for (ServerWorld world : server.getWorlds()) {
                    for (net.minecraft.entity.Entity e : world.iterateEntities()) {
                        if (e instanceof SimpleNpcEntity sn && id.equals(sn.getNpcId())) {
                            sn.markDeleted();
                            NpcLifecycleManager.unregisterEntity(sn);
                        }
                    }
                }

                // 4. Delete JSON passport from storage
                boolean deleted = NpcJsonStorage.deleteNpc(id);
                if (deleted) {
                    player.sendMessage(Text.literal("§cNPC '" + id + "' безвозвратно удален."), false);
                }
            });
        });

        // C2S: Upload Skin PNG
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_SKIN_C2S, (server, player, handler, buf, responseSender) -> {
            if (!player.hasPermissionLevel(2)) return;
            String npcId = buf.readString(256);
            int byteLength = buf.readInt();
            if (byteLength > 64 * 1024) { // max 64KB
                return;
            }
            byte[] skinBytes = new byte[byteLength];
            buf.readBytes(skinBytes);

            server.execute(() -> {
                try {
                    Path skinPath = NpcJsonStorage.getSkinsDir().resolve(npcId + ".png");
                    Files.write(skinPath, skinBytes);

                    // Update NPC data
                    NpcData data = NpcJsonStorage.getNpc(npcId);
                    if (data != null) {
                        data.setSkinSource(NpcData.SkinSource.LOCAL_FILE);
                        data.setSkinValue(npcId + ".png");
                        NpcJsonStorage.saveNpc(data);
                        SimpleNpcEntity active = NpcLifecycleManager.getEntity(npcId);
                        if (active != null) {
                            active.setNpcData(data);
                        }
                    }

                    // Broadcast skin to all players online
                    broadcastSkinToClients(server, npcId, skinBytes);
                } catch (IOException e) {
                    PrettySimpleNpcsMod.LOGGER.error("Failed to write skin file for {}", npcId, e);
                }
            });
        });

        // C2S: Dialogue Choice Selected
        ServerPlayNetworking.registerGlobalReceiver(DIALOGUE_CHOICE_C2S, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            String dialogueId = buf.readString(256);
            String currentNodeId = buf.readString(256);
            String targetNodeId = buf.readString(256);
            int choiceIndex = buf.readInt();

            server.execute(() -> {
                net.minecraft.entity.Entity e = player.getWorld().getEntityById(entityId);
                SimpleNpcEntity npc = e instanceof SimpleNpcEntity simpleNpc ? simpleNpc : null;

                DialogueData.Tree tree = NpcJsonStorage.getDialogue(dialogueId);
                if (tree == null) return;

                boolean openedTrade = false;

                // 1. Retrieve the clicked choice from the current node
                DialogueData.Node currentNode = tree.getNode(currentNodeId);
                DialogueData.Choice clickedChoice = null;
                if (currentNode != null) {
                    List<DialogueData.Choice> validChoices = currentNode.getChoices().stream()
                            .filter(c -> isChoiceAvailable(player, c))
                            .toList();
                    if (choiceIndex >= 0 && choiceIndex < validChoices.size()) {
                        clickedChoice = validChoices.get(choiceIndex);
                    } else if (choiceIndex >= 0 && choiceIndex < currentNode.getChoices().size()) {
                        clickedChoice = currentNode.getChoices().get(choiceIndex);
                    }
                }

                // 2. Execute any actions attached directly to this choice
                if (clickedChoice != null) {
                    for (DialogueData.Action act : clickedChoice.getActions()) {
                        if (act.getType() == DialogueData.Action.ActionType.OPEN_TRADE) {
                            openedTrade = true;
                        }
                        ActionProcessor.executeAction(act, player, npc);
                    }
                }

                // 3. Special targetNodeId hooks: "trade", "OPEN_TRADE", or "shop"
                if (!openedTrade && ("trade".equalsIgnoreCase(targetNodeId) || "OPEN_TRADE".equalsIgnoreCase(targetNodeId) || "shop".equalsIgnoreCase(targetNodeId))) {
                    if (tree.getNode(targetNodeId) == null) {
                        ActionProcessor.executeAction(new DialogueData.Action(DialogueData.Action.ActionType.OPEN_TRADE, ""), player, npc);
                        openedTrade = true;
                    }
                }

                // 4. If trade was opened, or if exiting dialogue, stop here (do not send dialogue update)
                if (openedTrade || "EXIT".equalsIgnoreCase(targetNodeId) || targetNodeId.isEmpty()) {
                    return;
                }

                // 5. Navigate to the target node
                DialogueData.Node nextNode = tree.getNode(targetNodeId);
                if (nextNode != null) {
                    // Execute enter actions for the new node
                    for (DialogueData.Action act : nextNode.getEnterActions()) {
                        if (act.getType() == DialogueData.Action.ActionType.OPEN_TRADE) {
                            openedTrade = true;
                        }
                        ActionProcessor.executeAction(act, player, npc);
                    }

                    // Only send dialogue update if trade was NOT opened by enter actions
                    if (!openedTrade) {
                        sendDialogueNodeUpdate(player, entityId, tree, nextNode);
                    }
                }
            });
        });

        // C2S: Perform Trade Transaction
        ServerPlayNetworking.registerGlobalReceiver(PERFORM_TRADE_C2S, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readInt();
            String tradeMatrixId = buf.readString(256);
            String entryId = buf.readString(256);
            int count = buf.readInt();

            server.execute(() -> {
                TradeData.TradeMatrix matrix = NpcJsonStorage.getTradeMatrix(tradeMatrixId);
                if (matrix == null) return;

                TradeData.TradeEntry entry = matrix.getEntry(entryId);
                if (entry == null || count <= 0) return;

                // Check per-player limit in SQLite
                if (entry.getPoolType() == TradeData.PoolType.LIMITED) {
                    int alreadyBought = DatabaseManager.getPurchasedCount(player.getUuid(), entry.getId(), entry.getResetCooldownSeconds());
                    if (alreadyBought + count > entry.getPerPlayerCap()) {
                        player.sendMessage(Text.literal("§cПревышен лимит покупок этого товара!"), true);
                        return;
                    }
                }

                // Currency calculation: 1 gold = 10 silver
                // silver = iron_nugget, gold = gold_nugget
                int totalSilverCost = entry.getTotalPriceInSilver() * count;
                int playerSilver = countItems(player, Items.IRON_NUGGET);
                int playerGold = countItems(player, Items.GOLD_NUGGET);
                int playerTotalSilver = playerSilver + (playerGold * 10);

                if (playerTotalSilver < totalSilverCost) {
                    player.sendMessage(Text.literal("§cНедостаточно монет для покупки!"), true);
                    return;
                }

                // Deduct currency
                deductCurrency(player, totalSilverCost);

                // Give purchased items
                Identifier itemId = Identifier.tryParse(entry.getItemId());
                if (itemId != null && Registries.ITEM.containsId(itemId)) {
                    Item item = Registries.ITEM.get(itemId);
                    int totalItems = entry.getItemCount() * count;
                    ItemStack resultStack = new ItemStack(item, totalItems);
                    if (!player.getInventory().insertStack(resultStack)) {
                        player.dropItem(resultStack, false);
                    }
                }

                // Record purchase in SQLite for limited pools
                if (entry.getPoolType() == TradeData.PoolType.LIMITED) {
                    DatabaseManager.recordPurchase(player.getUuid(), entry.getId(), count);
                }

                player.sendMessage(Text.literal("§aУспешная покупка: §f" + entry.getItemCount() * count + " шт."), true);

                // Refresh trade UI for the player
                net.minecraft.entity.Entity e = player.getWorld().getEntityById(entityId);
                if (e instanceof SimpleNpcEntity simpleNpc) {
                    sendOpenTradeToClient(player, simpleNpc);
                }
            });
        });
        // C2S: Save/Create Dialogue from Admin GUI
        ServerPlayNetworking.registerGlobalReceiver(SAVE_DIALOGUE_C2S, (server, player, handler, buf, responseSender) -> {
            if (!player.hasPermissionLevel(2)) return;
            String json = buf.readString(32767);
            server.execute(() -> {
                try {
                    DialogueData.Tree tree = NpcData.GSON.fromJson(json, DialogueData.Tree.class);
                    if (tree != null && tree.getId() != null) {
                        NpcJsonStorage.saveDialogue(tree);
                        player.sendMessage(Text.literal("§a[PrettySimpleNPCs] Диалог '" + tree.getId() + "' сохранен!"), true);
                    }
                } catch (Exception e) {
                    PrettySimpleNpcsMod.LOGGER.error("Failed to parse SaveDialogue packet", e);
                }
            });
        });
    }

    public static void sendOpenAdminGuiToClient(ServerPlayerEntity player, SimpleNpcEntity npc) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(npc.getId());
        buf.writeString(NpcData.GSON.toJson(npc.getNpcData()));

        // Also pack lists of all registered dialogue trees and trade matrices
        Collection<DialogueData.Tree> dialogues = NpcJsonStorage.getAllDialogues();
        buf.writeInt(dialogues.size());
        for (DialogueData.Tree d : dialogues) {
            buf.writeString(d.getId());
            buf.writeString(d.getTitle());
            buf.writeString(NpcData.GSON.toJson(d));
        }

        Collection<TradeData.TradeMatrix> trades = NpcJsonStorage.getAllTrades();
        buf.writeInt(trades.size());
        for (TradeData.TradeMatrix t : trades) {
            buf.writeString(t.getId());
            buf.writeString(t.getTitle());
        }

        ServerPlayNetworking.send(player, OPEN_ADMIN_GUI_S2C, buf);
    }

    public static void sendOpenDialogueToClient(ServerPlayerEntity player, SimpleNpcEntity npc, DialogueData.Tree tree, String startNodeId) {
        DialogueData.Node startNode = tree.getNode(startNodeId);
        if (startNode == null) return;

        // Execute enter actions for start node
        for (DialogueData.Action act : startNode.getEnterActions()) {
            ActionProcessor.executeAction(act, player, npc);
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(npc.getId());
        buf.writeString(tree.getId());
        buf.writeString(tree.getTitle());
        buf.writeString(npc.getName().getString());
        buf.writeString(startNode.getId());
        String processedText = ActionProcessor.replacePlaceholders(startNode.getText(), player, npc.getNpcData());
        buf.writeString(processedText);

        // Filter and write valid choices
        List<DialogueData.Choice> validChoices = startNode.getChoices().stream()
                .filter(c -> isChoiceAvailable(player, c))
                .toList();

        buf.writeInt(validChoices.size());
        for (DialogueData.Choice c : validChoices) {
            buf.writeString(ActionProcessor.replacePlaceholders(c.getText(), player, npc.getNpcData()));
            buf.writeString(c.getTargetNodeId());
        }

        ServerPlayNetworking.send(player, OPEN_DIALOGUE_S2C, buf);
    }

    public static void sendDialogueNodeUpdate(ServerPlayerEntity player, int entityId, DialogueData.Tree tree, DialogueData.Node node) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeString(tree.getId());
        buf.writeString(tree.getTitle());

        SimpleNpcEntity npc = player.getWorld().getEntityById(entityId) instanceof SimpleNpcEntity sn ? sn : null;
        NpcData npcData = npc != null ? npc.getNpcData() : null;
        String npcName = npc != null ? npc.getName().getString() : "Собеседник";
        buf.writeString(npcName);

        buf.writeString(node.getId());
        String processedText = ActionProcessor.replacePlaceholders(node.getText(), player, npcData);
        buf.writeString(processedText);

        List<DialogueData.Choice> validChoices = node.getChoices().stream()
                .filter(c -> isChoiceAvailable(player, c))
                .toList();

        buf.writeInt(validChoices.size());
        for (DialogueData.Choice c : validChoices) {
            buf.writeString(ActionProcessor.replacePlaceholders(c.getText(), player, npcData));
            buf.writeString(c.getTargetNodeId());
        }

        ServerPlayNetworking.send(player, OPEN_DIALOGUE_S2C, buf);
    }

    private static boolean isChoiceAvailable(ServerPlayerEntity player, DialogueData.Choice choice) {
        if (!choice.getRequiredFlag().isEmpty()) {
            if (!DatabaseManager.hasFlag(player.getUuid(), choice.getRequiredFlag())) {
                return false;
            }
        }
        if (!choice.getProhibitedFlag().isEmpty()) {
            if (DatabaseManager.hasFlag(player.getUuid(), choice.getProhibitedFlag())) {
                return false;
            }
        }
        if (!choice.getRequiredItem().isEmpty()) {
            Identifier itemId = Identifier.tryParse(choice.getRequiredItem());
            if (itemId != null && Registries.ITEM.containsId(itemId)) {
                Item item = Registries.ITEM.get(itemId);
                if (countItems(player, item) < choice.getRequiredItemCount()) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void sendOpenTradeToClient(ServerPlayerEntity player, SimpleNpcEntity npc) {
        sendOpenTradeToClient(player, npc, "");
    }

    public static void sendOpenTradeToClient(ServerPlayerEntity player, SimpleNpcEntity npc, String tradeMatrixIdOverride) {
        String targetMatrixId = (tradeMatrixIdOverride != null && !tradeMatrixIdOverride.trim().isEmpty())
                ? tradeMatrixIdOverride.trim()
                : (npc != null && npc.getNpcData() != null ? npc.getNpcData().getTradeMatrixId() : "");

        TradeData.TradeMatrix matrix = null;
        if (!targetMatrixId.isEmpty()) {
            matrix = NpcJsonStorage.getTradeMatrix(targetMatrixId);
        }
        if (matrix == null && npc != null && npc.getNpcData() != null && !npc.getNpcData().getTradeMatrixId().isEmpty()) {
            matrix = NpcJsonStorage.getTradeMatrix(npc.getNpcData().getTradeMatrixId());
        }
        if (matrix == null) {
            matrix = NpcJsonStorage.getTradeMatrix("default_trades");
        }
        if (matrix == null && !NpcJsonStorage.getAllTrades().isEmpty()) {
            matrix = NpcJsonStorage.getAllTrades().iterator().next();
        }
        if (matrix == null) {
            player.sendMessage(Text.literal("§c[Торговля] У этого NPC нет товаров для продажи."), false);
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(npc != null ? npc.getId() : -1);
        buf.writeString(matrix.getId());
        buf.writeString(matrix.getTitle());

        // Player money balance
        int ironNuggets = countItems(player, Items.IRON_NUGGET);
        int goldNuggets = countItems(player, Items.GOLD_NUGGET);
        buf.writeInt(ironNuggets); // Silver
        buf.writeInt(goldNuggets); // Gold

        // Trade entries with remaining stock
        buf.writeInt(matrix.getEntries().size());
        for (TradeData.TradeEntry entry : matrix.getEntries()) {
            buf.writeString(entry.getId());
            buf.writeString(entry.getItemId());
            buf.writeInt(entry.getItemCount());
            buf.writeInt(entry.getPriceSilver());
            buf.writeInt(entry.getPriceGold());
            buf.writeString(entry.getPoolType().name());

            int bought = DatabaseManager.getPurchasedCount(player.getUuid(), entry.getId(), entry.getResetCooldownSeconds());
            int remaining = entry.getPoolType() == TradeData.PoolType.LIMITED ? Math.max(0, entry.getPerPlayerCap() - bought) : -1;
            buf.writeInt(remaining);
        }

        ServerPlayNetworking.send(player, OPEN_TRADE_S2C, buf);
    }

    public static void sendBarkToNearbyClients(SimpleNpcEntity npc, String bark, double radius) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(npc.getId());
        buf.writeString(bark);

        for (ServerPlayerEntity p : PlayerLookup.around((net.minecraft.server.world.ServerWorld) npc.getWorld(), npc.getPos(), radius)) {
            ServerPlayNetworking.send(p, BARK_S2C, buf);
        }
    }

    public static void broadcastSkinToClients(MinecraftServer server, String npcId, byte[] skinBytes) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(npcId);
        buf.writeInt(skinBytes.length);
        buf.writeBytes(skinBytes);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, SYNC_SKIN_S2C, buf);
        }
    }

    // Helper currency methods
    private static int countItems(ServerPlayerEntity player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void deductCurrency(ServerPlayerEntity player, int totalSilverNeeded) {
        int ironNuggets = countItems(player, Items.IRON_NUGGET);
        if (ironNuggets >= totalSilverNeeded) {
            removeItems(player, Items.IRON_NUGGET, totalSilverNeeded);
            return;
        }

        // Spend all available iron nuggets first
        removeItems(player, Items.IRON_NUGGET, ironNuggets);
        int remainingSilver = totalSilverNeeded - ironNuggets;

        // Convert gold nuggets (1 gold = 10 silver)
        int goldNeeded = (int) Math.ceil(remainingSilver / 10.0);
        removeItems(player, Items.GOLD_NUGGET, goldNeeded);

        int changeSilver = (goldNeeded * 10) - remainingSilver;
        if (changeSilver > 0) {
            ItemStack change = new ItemStack(Items.IRON_NUGGET, changeSilver);
            if (!player.getInventory().insertStack(change)) {
                player.dropItem(change, false);
            }
        }
    }

    private static void removeItems(ServerPlayerEntity player, Item item, int count) {
        int needed = count;
        for (int i = 0; i < player.getInventory().size() && needed > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                int toRemove = Math.min(stack.getCount(), needed);
                stack.decrement(toRemove);
                needed -= toRemove;
            }
        }
    }
}
