package ua.neuror1ston.prettysimplenpcs.entity;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ua.neuror1ston.prettysimplenpcs.PrettySimpleNpcsMod;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ensures NPC resilience against accidental kills, chunk desyncs, and void drops.
 * Automatically respawns missing NPCs at their home position if their chunk is loaded,
 * with strict per-world lifecycle management.
 */
public class NpcLifecycleManager {
    private static final Map<String, SimpleNpcEntity> ACTIVE_ENTITIES = new ConcurrentHashMap<>();
    private static int checkTicks = 0;

    public static void onServerStarted(MinecraftServer server) {
        ACTIVE_ENTITIES.clear();
        checkTicks = 0;
    }

    public static void onServerStopping(MinecraftServer server) {
        ACTIVE_ENTITIES.clear();
        checkTicks = 0;
    }

    public static void registerEntity(SimpleNpcEntity entity) {
        String id = entity.getNpcId();
        if (id != null && !id.isEmpty()) {
            ACTIVE_ENTITIES.put(id, entity);
        }
    }

    public static void unregisterEntity(SimpleNpcEntity entity) {
        String id = entity.getNpcId();
        if (id != null && !id.isEmpty()) {
            ACTIVE_ENTITIES.remove(id, entity);
        }
    }

    public static SimpleNpcEntity getEntity(String id) {
        return ACTIVE_ENTITIES.get(id);
    }

    public static void serverTick(MinecraftServer server) {
        // Run verification check every 5 seconds (100 ticks)
        if (++checkTicks < 100) return;
        checkTicks = 0;

        for (NpcData data : NpcJsonStorage.getAllNpcs()) {
            String id = data.getId();
            SimpleNpcEntity active = ACTIVE_ENTITIES.get(id);

            if (active != null && !active.isRemoved() && active.isAlive()) {
                continue; // Entity is alive and active
            }

            // Check if any entity in loaded worlds actually exists with this ID
            boolean foundInWorld = false;
            for (ServerWorld w : server.getWorlds()) {
                for (net.minecraft.entity.Entity e : w.iterateEntities()) {
                    if (e instanceof SimpleNpcEntity sn && id.equals(sn.getNpcId())) {
                        if (!sn.isRemoved() && sn.isAlive()) {
                            registerEntity(sn);
                            foundInWorld = true;
                            break;
                        }
                    }
                }
                if (foundInWorld) break;
            }
            if (foundInWorld) continue;

            // Entity is truly missing, check if its home chunk is loaded in its dimension
            Vec3d home = data.getHomePos();
            BlockPos homePos = BlockPos.ofFloored(home.x, home.y, home.z);
            ChunkPos chunkPos = new ChunkPos(homePos);

            ServerWorld world = resolveWorld(server, data.getDimension());
            if (world != null && world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                // Respawn entity using its saved passport
                PrettySimpleNpcsMod.LOGGER.info("Auto-respawning missing NPC '{}' in {} at {}",
                        id, world.getRegistryKey().getValue(), homePos);
                spawnOrRespawn(world, data);
            }
        }
    }

    private static ServerWorld resolveWorld(MinecraftServer server, String dimStr) {
        if (dimStr != null && !dimStr.isEmpty()) {
            Identifier dimId = Identifier.tryParse(dimStr);
            if (dimId != null) {
                RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, dimId);
                ServerWorld w = server.getWorld(key);
                if (w != null) return w;
            }
        }
        return server.getOverworld();
    }

    public static SimpleNpcEntity spawnOrRespawn(ServerWorld world, NpcData data) {
        SimpleNpcEntity entity = PrettySimpleNpcsMod.SIMPLE_NPC_ENTITY_TYPE.create(world);
        if (entity != null) {
            Vec3d home = data.getHomePos();
            entity.refreshPositionAndAngles(home.x, home.y, home.z, data.getHomeYaw(), data.getHomePitch());
            data.setDimension(world.getRegistryKey().getValue().toString());
            entity.setNpcData(data);
            world.spawnEntity(entity);
            registerEntity(entity);
            return entity;
        }
        return null;
    }
}
