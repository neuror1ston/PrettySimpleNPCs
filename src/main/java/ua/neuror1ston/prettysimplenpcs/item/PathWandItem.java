package ua.neuror1ston.prettysimplenpcs.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.entity.NpcLifecycleManager;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wand tool for recording waypoint chains for patrol routes.
 * Rendered as an enchanted stick.
 */
public class PathWandItem extends Item {
    private static final Map<UUID, String> SELECTED_NPC = new ConcurrentHashMap<>();

    public PathWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    public static void setSelectedNpc(ServerPlayerEntity player, String npcId) {
        SELECTED_NPC.put(player.getUuid(), npcId);
    }

    public static String getSelectedNpc(ServerPlayerEntity player) {
        return SELECTED_NPC.get(player.getUuid());
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient()) {
            return ActionResult.SUCCESS;
        }

        if (context.getPlayer() instanceof ServerPlayerEntity player) {
            if (!player.hasPermissionLevel(2)) {
                player.sendMessage(Text.literal("§cУ вас нет прав для настройки маршрутов."), true);
                return ActionResult.FAIL;
            }

            String selectedId = SELECTED_NPC.get(player.getUuid());
            if (selectedId == null || selectedId.isEmpty()) {
                player.sendMessage(Text.literal("§eСначала кликните этой палочкой по NPC, маршрут которого хотите записать!"), true);
                return ActionResult.FAIL;
            }

            NpcData data = NpcJsonStorage.getNpc(selectedId);
            if (data == null) {
                player.sendMessage(Text.literal("§cNPC с ID " + selectedId + " не найден в базе."), true);
                return ActionResult.FAIL;
            }

            BlockPos blockPos = context.getBlockPos().offset(context.getSide());
            Vec3d point = new Vec3d(blockPos.getX() + 0.5, blockPos.getY(), blockPos.getZ() + 0.5);

            data.getPatrolPoints().add(point);
            data.setAiState(NpcData.AiState.PATROL);
            NpcJsonStorage.saveNpc(data);

            SimpleNpcEntity active = NpcLifecycleManager.getEntity(selectedId);
            if (active != null) {
                active.setNpcData(data);
            }

            player.sendMessage(Text.literal("§aДобавлена точка #" + data.getPatrolPoints().size() +
                    " для §e" + selectedId + "§a: " + String.format("(%.1f, %.1f, %.1f)", point.x, point.y, point.z)), false);

            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player && player.isSneaking()) {
            String selectedId = SELECTED_NPC.get(player.getUuid());
            if (selectedId != null) {
                NpcData data = NpcJsonStorage.getNpc(selectedId);
                if (data != null && !data.getPatrolPoints().isEmpty()) {
                    data.getPatrolPoints().clear();
                    NpcJsonStorage.saveNpc(data);
                    player.sendMessage(Text.literal("§cМаршрут для " + selectedId + " полностью очищен!"), false);
                }
            }
            return TypedActionResult.success(player.getStackInHand(hand));
        }
        return TypedActionResult.pass(user.getStackInHand(hand));
    }
}
