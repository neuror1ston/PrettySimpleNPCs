package ua.neuror1ston.prettysimplenpcs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.neuror1ston.prettysimplenpcs.command.NpcCommand;
import ua.neuror1ston.prettysimplenpcs.database.DatabaseManager;
import ua.neuror1ston.prettysimplenpcs.entity.NpcLifecycleManager;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.neuror1ston.prettysimplenpcs.item.NpcWandItem;
import ua.neuror1ston.prettysimplenpcs.item.PathWandItem;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;

public class PrettySimpleNpcsMod implements ModInitializer {
    public static final String MOD_ID = "prettysimplenpcs";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Entity registration
    public static final EntityType<SimpleNpcEntity> SIMPLE_NPC_ENTITY_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MOD_ID, "simple_npc"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, SimpleNpcEntity::new)
                    .dimensions(EntityDimensions.changing(0.6f, 1.8f))
                    .build()
    );

    // Items
    public static final Item NPC_WAND = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "npc_wand"),
            new NpcWandItem(new Item.Settings().maxCount(1))
    );

    public static final Item PATH_WAND = Registry.register(
            Registries.ITEM,
            new Identifier(MOD_ID, "path_wand"),
            new PathWandItem(new Item.Settings().maxCount(1))
    );

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Pretty Simple NPCs mod...");

        // Entity attributes
        FabricDefaultAttributeRegistry.register(SIMPLE_NPC_ENTITY_TYPE, SimpleNpcEntity.createAttributes());

        // Declarative JSON configs init
        NpcJsonStorage.init();

        // Ensure NavMesh pathfinding thread pool is initialized on every server launch
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                ua.stubname.navmesh.pathfinding.AsyncPathProcessor.init(ua.stubname.config.NavMeshConfig.load());
            } catch (Exception e) {
                LOGGER.warn("NavMesh AsyncPathProcessor startup initialization notice: {}", e.getMessage());
            }
        });

        // Database Lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DatabaseManager.init(server.getSavePath(WorldSavePath.ROOT));
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DatabaseManager.close();
        });

        // Resilience tick (auto-respawns missing NPCs at loaded chunks)
        ServerTickEvents.END_SERVER_TICK.register(NpcLifecycleManager::serverTick);

        // Network receivers
        NpcNetwork.registerServerReceivers();

        // Commands
        CommandRegistrationCallback.EVENT.register(NpcCommand::register);

        LOGGER.info("Pretty Simple NPCs successfully initialized!");
    }
}
