package ua.neuror1ston.prettysimplenpcs.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import ua.neuror1ston.prettysimplenpcs.database.DatabaseManager;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles execution of node and choice actions with support for placeholders.
 */
public class ActionProcessor {
    private static final Pattern FLAG_PATTERN = Pattern.compile("%flag:([a-zA-Z0-9_\\-]+)%");

    public static String replacePlaceholders(String text, ServerPlayerEntity player, NpcData npcData) {
        if (text == null) return "";
        String result = text;
        if (player != null) {
            result = result.replace("%player%", player.getName().getString());
            result = result.replace("%player_uuid%", player.getUuidAsString());
        }
        if (npcData != null) {
            result = result.replace("%npc_id%", npcData.getId());
            result = result.replace("%npc_name%", npcData.getName());
        }
        if (player != null) {
            Matcher matcher = FLAG_PATTERN.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String flagKey = matcher.group(1);
                String flagVal = DatabaseManager.getFlag(player.getUuid(), flagKey, "");
                matcher.appendReplacement(sb, Matcher.quoteReplacement(flagVal));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    public static void executeAction(DialogueData.Action action, ServerPlayerEntity player, SimpleNpcEntity npc) {
        if (action == null || player == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        NpcData npcData = npc != null ? npc.getNpcData() : null;
        String val = replacePlaceholders(action.getValue(), player, npcData);

        switch (action.getType()) {
            case CONSOLE_COMMAND -> {
                if (!val.isEmpty()) {
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), val);
                }
            }
            case PLAYER_COMMAND -> {
                if (!val.isEmpty()) {
                    server.getCommandManager().executeWithPrefix(player.getCommandSource(), val);
                }
            }
            case GIVE_ITEM -> {
                Identifier id = Identifier.tryParse(val);
                if (id != null && Registries.ITEM.containsId(id)) {
                    Item item = Registries.ITEM.get(id);
                    int count = Math.max(1, action.getCount());
                    ItemStack stack = new ItemStack(item, count);
                    if (!player.getInventory().insertStack(stack)) {
                        player.dropItem(stack, false);
                    }
                }
            }
            case TAKE_ITEM -> {
                Identifier id = Identifier.tryParse(val);
                if (id != null && Registries.ITEM.containsId(id)) {
                    Item item = Registries.ITEM.get(id);
                    int needed = Math.max(1, action.getCount());
                    for (int i = 0; i < player.getInventory().size() && needed > 0; i++) {
                        ItemStack stack = player.getInventory().getStack(i);
                        if (stack.isOf(item)) {
                            int toTake = Math.min(stack.getCount(), needed);
                            stack.decrement(toTake);
                            needed -= toTake;
                        }
                    }
                }
            }
            case PLAY_SOUND -> {
                Identifier soundId = Identifier.tryParse(val);
                if (soundId != null && Registries.SOUND_EVENT.containsId(soundId)) {
                    SoundEvent sound = Registries.SOUND_EVENT.get(soundId);
                    player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                            sound, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
            }
            case SET_FLAG -> {
                if (!val.isEmpty()) {
                    String[] parts = val.split("=", 2);
                    String flagKey = parts[0].trim();
                    String flagVal = parts.length > 1 ? parts[1].trim() : "true";
                    DatabaseManager.setFlag(player.getUuid(), flagKey, flagVal);
                }
            }
            case REMOVE_FLAG -> {
                if (!val.isEmpty()) {
                    DatabaseManager.removeFlag(player.getUuid(), val.trim());
                }
            }
            case OPEN_TRADE -> {
                NpcNetwork.sendOpenTradeToClient(player, npc, val);
            }
        }
    }
}
