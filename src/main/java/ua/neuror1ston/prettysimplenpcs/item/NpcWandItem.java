package ua.neuror1ston.prettysimplenpcs.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.entity.NpcLifecycleManager;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;

/**
 * Wand tool for admins/lore creators to spawn new NPCs or configure existing ones.
 * Rendered as an enchanted stick.
 */
public class NpcWandItem extends Item {
    public NpcWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        if (context.getPlayer() instanceof ServerPlayerEntity player) {
            if (!player.hasPermissionLevel(2)) {
                player.sendMessage(Text.literal("§cУ вас нет прав для создания NPC."), true);
                return ActionResult.FAIL;
            }

            BlockPos clicked = context.getBlockPos();
            Direction side = context.getSide();
            BlockPos spawnPos = clicked.offset(side);

            ServerWorld world = (ServerWorld) context.getWorld();

            // Generate unique NPC id
            String newId = "npc_" + System.currentTimeMillis() % 100000;
            NpcData data = new NpcData(newId);
            data.setName("Новый Житель");
            data.setTitle("Горожанин");
            data.setHomePos(new Vec3d(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5));
            data.setHomeYaw(player.getYaw() + 180.0f); // Face the creator

            // Persist JSON config immediately
            NpcJsonStorage.saveNpc(data);

            // Spawn entity
            SimpleNpcEntity entity = NpcLifecycleManager.spawnOrRespawn(world, data);
            if (entity != null) {
                player.sendMessage(Text.literal("§aСоздан новый NPC §e" + newId + "§a! Открываем настройки..."), false);
                // Open Admin GUI immediately
                NpcNetwork.sendOpenAdminGuiToClient(player, entity);
            }
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }
}
