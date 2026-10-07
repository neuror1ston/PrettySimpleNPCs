package ua.neuror1ston.prettysimplenpcs.ai;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ua.neuror1ston.prettysimplenpcs.PrettySimpleNpcsMod;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.polynav.api.NavMeshAPI;
import ua.polynav.navmesh.geometry.NavNode;

import java.util.List;

/**
 * Lightweight AI behavior driver handling STATIC, ROAM_RADIUS, and PATROL modes
 * using PolyNav Core, with jitter-free smooth player tracking.
 */
public class NpcAiController {
    private final SimpleNpcEntity npc;
    private int stateTicks = 0;
    private int currentPatrolIndex = 0;
    private boolean patrolForward = true;
    private int waitTimer = 0;
    private PlayerEntity targetPlayer = null;

    public NpcAiController(SimpleNpcEntity npc) {
        this.npc = npc;
    }

    public void tick(NpcData data) {
        stateTicks++;

        switch (data.getAiState()) {
            case STATIC -> tickStatic(data);
            case ROAM_RADIUS -> tickRoam(data);
            case PATROL -> tickPatrol(data);
        }
    }

    private void tickStatic(NpcData data) {
        if (!data.isLookAtPlayers()) {
            // Smoothly align with home yaw
            alignYaw(data.getHomeYaw(), data.getHomePitch(), 10.0f);
            return;
        }

        // Search for nearest player every 10 ticks to save CPU
        if (stateTicks % 10 == 0) {
            targetPlayer = npc.getWorld().getClosestPlayer(npc, 8.0);
        }

        // Verify target player validity
        if (targetPlayer != null && (targetPlayer.isRemoved() || !targetPlayer.isAlive() || npc.squaredDistanceTo(targetPlayer) > 64.0)) {
            targetPlayer = null;
        }

        if (targetPlayer != null) {
            // Smoothly and continuously rotate body and head towards the player EVERY TICK
            double dx = targetPlayer.getX() - npc.getX();
            double dz = targetPlayer.getZ() - npc.getZ();
            double dy = targetPlayer.getEyeY() - npc.getEyeY();
            double distHorizontal = Math.sqrt(dx * dx + dz * dz);

            float targetYaw = (float) (MathHelper.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            float targetPitch = (float) (-(MathHelper.atan2(dy, distHorizontal) * (180.0 / Math.PI)));

            alignYaw(targetYaw, targetPitch, 15.0f);
        } else {
            // Return back to home yaw smoothly
            alignYaw(data.getHomeYaw(), data.getHomePitch(), 8.0f);
        }
    }

    private void alignYaw(float targetYaw, float targetPitch, float speed) {
        float newYaw = rotlerp(npc.getYaw(), targetYaw, speed);
        npc.setYaw(newYaw);
        npc.setHeadYaw(newYaw);
        npc.setBodyYaw(newYaw);
        npc.headYaw = newYaw;
        npc.bodyYaw = newYaw;

        float newPitch = rotlerp(npc.getPitch(), targetPitch, speed);
        npc.setPitch(newPitch);
    }

    private void tickRoam(NpcData data) {
        // If entity is not moving and timer expired, pick a random point within roam radius
        if (npc.getCurrentNavPath() == null || npc.getCurrentNavPath().isFinished()) {
            if (++waitTimer > 80 + npc.getRandom().nextInt(60)) { // 4-7 seconds idle pause
                waitTimer = 0;
                double r = data.getRoamRadius();
                if (r > 0 && npc.getWorld() instanceof ServerWorld serverWorld) {
                    Vec3d homePos = new Vec3d(data.getHomeX(), data.getHomeY(), data.getHomeZ());

                    // 1. First priority: select a verified walkable node on the baked NavMesh
                    NavNode randomNode = NavMeshAPI.getRandomNodeInRadius(homePos, r, npc.getRandom());

                    Vec3d target = null;
                    if (randomNode != null) {
                        target = randomNode.getPos();
                    } else {
                        // 2. Fallback: find a safe ground position without clipping into solid blocks
                        target = findSafeRoamTarget(serverWorld, homePos, r);
                    }

                    if (target != null) {
                        safeNavigateTo(target, data.getBaseSpeed());
                    }
                }
            }
        } else {
            waitTimer = 0;
        }
    }

    private Vec3d findSafeRoamTarget(ServerWorld world, Vec3d homePos, double radius) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = npc.getRandom().nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(npc.getRandom().nextDouble()) * radius;
            double tx = homePos.x + dist * Math.cos(angle);
            double tz = homePos.z + dist * Math.sin(angle);
            int blockX = (int) Math.floor(tx);
            int blockZ = (int) Math.floor(tz);
            int startY = (int) Math.floor(homePos.y);

            // Scan ±4 blocks vertically around homeY to find solid ground with head clearance
            for (int dy = 3; dy >= -4; dy--) {
                BlockPos groundPos = new BlockPos(blockX, startY + dy, blockZ);
                BlockState groundState = world.getBlockState(groundPos);

                if (groundState.getCollisionShape(world, groundPos).isEmpty() || !groundState.getFluidState().isEmpty()) {
                    continue;
                }

                BlockPos footPos = groundPos.up();
                BlockPos headPos = footPos.up();

                BlockState footState = world.getBlockState(footPos);
                BlockState headState = world.getBlockState(headPos);

                // Both feet and head levels must be free of solid obstacles
                if (footState.getCollisionShape(world, footPos).isEmpty() &&
                    headState.getCollisionShape(world, headPos).isEmpty() &&
                    footState.getFluidState().isEmpty()) {
                    double surfaceY = groundPos.getY() + groundState.getCollisionShape(world, groundPos).getMax(Direction.Axis.Y);
                    return new Vec3d(tx, surfaceY, tz);
                }
            }
        }
        return null;
    }

    private void tickPatrol(NpcData data) {
        List<Vec3d> points = data.getPatrolPoints();
        if (points == null || points.isEmpty()) {
            tickStatic(data);
            return;
        }

        if (currentPatrolIndex >= points.size()) {
            currentPatrolIndex = 0;
        }

        Vec3d targetPoint = points.get(currentPatrolIndex);
        double distSq = npc.squaredDistanceTo(targetPoint.x, targetPoint.y, targetPoint.z);

        if (distSq < 1.0) {
            // Reached waypoint, wait configured ticks
            if (++waitTimer >= data.getWaitTicksPerPoint()) {
                waitTimer = 0;
                advancePatrolPoint(data, points.size());
            }
        } else {
            // Navigate to current waypoint if not already moving
            if (npc.getCurrentNavPath() == null || npc.getCurrentNavPath().isFinished()) {
                safeNavigateTo(targetPoint, data.getBaseSpeed());
            }
        }
    }

    private void safeNavigateTo(Vec3d target, double speed) {
        try {
            NavMeshAPI.navigateTo(npc, target, speed);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            PrettySimpleNpcsMod.LOGGER.warn("NavMesh thread pool was not ready. Re-initializing pool...");
            try {
                ua.polynav.navmesh.pathfinding.AsyncPathProcessor.init(ua.polynav.config.NavMeshConfig.load());
                NavMeshAPI.navigateTo(npc, target, speed);
            } catch (Exception ignored) {}
        } catch (Exception e) {
            PrettySimpleNpcsMod.LOGGER.debug("Navigation notice for {}: {}", npc.getNpcId(), e.getMessage());
        }
    }

    private void advancePatrolPoint(NpcData data, int totalPoints) {
        if (totalPoints <= 1) return;

        if (data.isPatrolPingPong()) {
            if (patrolForward) {
                if (currentPatrolIndex + 1 < totalPoints) {
                    currentPatrolIndex++;
                } else {
                    patrolForward = false;
                    currentPatrolIndex--;
                }
            } else {
                if (currentPatrolIndex - 1 >= 0) {
                    currentPatrolIndex--;
                } else {
                    patrolForward = true;
                    currentPatrolIndex++;
                }
            }
        } else if (data.isPatrolLoop()) {
            currentPatrolIndex = (currentPatrolIndex + 1) % totalPoints;
        } else {
            if (currentPatrolIndex + 1 < totalPoints) {
                currentPatrolIndex++;
            }
        }
    }

    private float rotlerp(float from, float to, float maxDelta) {
        float diff = MathHelper.wrapDegrees(to - from);
        if (diff > maxDelta) diff = maxDelta;
        if (diff < -maxDelta) diff = -maxDelta;
        return from + diff;
    }
}
