package ua.neuror1ston.prettysimplenpcs.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import ua.neuror1ston.prettysimplenpcs.PrettySimpleNpcsMod;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;

import static net.minecraft.server.command.CommandManager.literal;

public class NpcCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("npc")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("reload")
                        .then(literal("dialogues")
                                .executes(context -> {
                                    NpcJsonStorage.reloadDialogues();
                                    context.getSource().sendMessage(Text.literal("§a[PrettySimpleNPCs] Диалоги успешно перезагружены!"));
                                    return 1;
                                }))
                        .then(literal("all")
                                .executes(context -> {
                                    NpcJsonStorage.reloadAll();
                                    context.getSource().sendMessage(Text.literal("§a[PrettySimpleNPCs] Все конфигурации (NPC, диалоги, торговля) перезагружены!"));
                                    return 1;
                                }))
                        .executes(context -> {
                            NpcJsonStorage.reloadAll();
                            context.getSource().sendMessage(Text.literal("§a[PrettySimpleNPCs] Все конфигурации перезагружены!"));
                            return 1;
                        }))
                .then(literal("wand")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            player.giveItemStack(new ItemStack(PrettySimpleNpcsMod.NPC_WAND));
                            context.getSource().sendMessage(Text.literal("§aВам выдана палочка NPC Wand!"));
                            return 1;
                        }))
                .then(literal("pathwand")
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            player.giveItemStack(new ItemStack(PrettySimpleNpcsMod.PATH_WAND));
                            context.getSource().sendMessage(Text.literal("§aВам выдана палочка Path Wand!"));
                            return 1;
                        }))
        );
    }
}
