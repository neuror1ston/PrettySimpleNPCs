package ua.neuror1ston.prettysimplenpcs.entity;

import net.minecraft.entity.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import ua.neuror1ston.prettysimplenpcs.ai.NpcAiController;
import ua.neuror1ston.prettysimplenpcs.data.DialogueData;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.item.NpcWandItem;
import ua.neuror1ston.prettysimplenpcs.item.PathWandItem;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;
import ua.neuror1ston.prettysimplenpcs.storage.NpcJsonStorage;
import ua.polynav.api.INavMeshAgent;
import ua.polynav.entity.NavMeshMoveControl;
import ua.polynav.entity.NavMeshNavigation;
import ua.polynav.navmesh.pathfinding.NavPath;

import java.util.List;

/**
 * Lightweight, high-performance RP NPC entity integrated with PolyNav Core.
 * Stripped of all vanilla $O(N^2)$ collision checks and heavy mob behaviors.
 */
public class SimpleNpcEntity extends PathAwareEntity implements INavMeshAgent {
    public static final TrackedData<String> NPC_ID = DataTracker.registerData(SimpleNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<Float> NPC_SCALE = DataTracker.registerData(SimpleNpcEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final TrackedData<String> NPC_TITLE = DataTracker.registerData(SimpleNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<String> SKIN_TYPE = DataTracker.registerData(SimpleNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<String> SKIN_SOURCE = DataTracker.registerData(SimpleNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<String> SKIN_VALUE = DataTracker.registerData(SimpleNpcEntity.class, TrackedDataHandlerRegistry.STRING);
    public static final TrackedData<String> CURRENT_BARK = DataTracker.registerData(SimpleNpcEntity.class, TrackedDataHandlerRegistry.STRING);

    private final NavMeshNavigation navMeshNavigation;
    private final NavMeshMoveControl navMeshMoveControl;
    private final NpcAiController aiController;

    private NavPath currentNavPath;
    private NpcData cachedData;
    private int barkTickTimer = 0;
    private int barkDisplayTicks = 0;
    private boolean manuallyDeleted = false;

    public SimpleNpcEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.navMeshMoveControl = new NavMeshMoveControl(this);
        this.moveControl = this.navMeshMoveControl;
        this.navMeshNavigation = new NavMeshNavigation(this, world);
        this.navigation = this.navMeshNavigation;
        this.aiController = new NpcAiController(this);

        // Standard 0.6 step height for smooth stairs, slabs, and Conquest Reforged layers
        this.setStepHeight(0.6f);
        this.setInvulnerable(true);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected net.minecraft.entity.ai.control.BodyControl createBodyControl() {
        return new net.minecraft.entity.ai.control.BodyControl(this) {
            @Override
            public void tick() {
                // Keep bodyYaw strictly matched to yaw and headYaw to prevent visual jitter
                SimpleNpcEntity.this.bodyYaw = SimpleNpcEntity.this.getYaw();
                SimpleNpcEntity.this.headYaw = SimpleNpcEntity.this.getYaw();
            }
        };
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(NPC_ID, "");
        this.dataTracker.startTracking(NPC_SCALE, 1.0f);
        this.dataTracker.startTracking(NPC_TITLE, "");
        this.dataTracker.startTracking(SKIN_TYPE, NpcData.SkinType.DEFAULT.name());
        this.dataTracker.startTracking(SKIN_SOURCE, NpcData.SkinSource.PLAYER_NICK.name());
        this.dataTracker.startTracking(SKIN_VALUE, "");
        this.dataTracker.startTracking(CURRENT_BARK, "");
    }

    public void setNpcData(NpcData data) {
        this.cachedData = data;
        if (data != null) {
            this.dataTracker.set(NPC_ID, data.getId());
            this.dataTracker.set(NPC_SCALE, data.getScale());
            this.dataTracker.set(NPC_TITLE, data.getTitle());
            this.dataTracker.set(SKIN_TYPE, data.getSkinType().name());
            this.dataTracker.set(SKIN_SOURCE, data.getSkinSource().name());
            this.dataTracker.set(SKIN_VALUE, data.getSkinValue());

            this.setCustomName(Text.literal(data.getName()));
            this.setCustomNameVisible(true);

            // Equipment sync
            applyEquipmentFromData(data);

            this.calculateDimensions();
        }
    }

    public NpcData getNpcData() {
        if (this.cachedData == null) {
            String id = this.dataTracker.get(NPC_ID);
            if (!id.isEmpty()) {
                this.cachedData = NpcJsonStorage.getNpc(id);
            }
            if (this.cachedData == null && !id.isEmpty()) {
                this.cachedData = new NpcData(id);
                this.cachedData.setDimension(this.getWorld().getRegistryKey().getValue().toString());
                this.cachedData.setHomePos(this.getPos());
                this.cachedData.setHomeYaw(this.getYaw());
            }
        }
        return this.cachedData;
    }

    public String getNpcId() {
        return this.dataTracker.get(NPC_ID);
    }

    public float getNpcScale() {
        return this.dataTracker.get(NPC_SCALE);
    }

    public String getNpcTitle() {
        return this.dataTracker.get(NPC_TITLE);
    }

    public String getSkinTypeValue() {
        return this.dataTracker.get(SKIN_TYPE);
    }

    public String getSkinSourceValue() {
        return this.dataTracker.get(SKIN_SOURCE);
    }

    public String getSkinValue() {
        return this.dataTracker.get(SKIN_VALUE);
    }

    public String getCurrentBark() {
        return this.dataTracker.get(CURRENT_BARK);
    }

    public void setCurrentBark(String bark) {
        this.dataTracker.set(CURRENT_BARK, bark);
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        float s = getNpcScale();
        return EntityDimensions.changing(0.6f * s, 1.8f * s);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (NPC_SCALE.equals(data)) {
            this.calculateDimensions();
        }
    }

    private void applyEquipmentFromData(NpcData data) {
        setEquippedItem(EquipmentSlot.HEAD, data.getHelmetItem());
        setEquippedItem(EquipmentSlot.CHEST, data.getChestplateItem());
        setEquippedItem(EquipmentSlot.LEGS, data.getLeggingsItem());
        setEquippedItem(EquipmentSlot.FEET, data.getBootsItem());
        setEquippedItem(EquipmentSlot.MAINHAND, data.getMainHandItem());
        setEquippedItem(EquipmentSlot.OFFHAND, data.getOffHandItem());
    }

    private void setEquippedItem(EquipmentSlot slot, String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            this.equipStack(slot, ItemStack.EMPTY);
            return;
        }
        Identifier id = Identifier.tryParse(itemId);
        if (id != null && Registries.ITEM.containsId(id)) {
            Item item = Registries.ITEM.get(id);
            this.equipStack(slot, new ItemStack(item));
        } else {
            this.equipStack(slot, ItemStack.EMPTY);
        }
    }

    // --- Extreme Performance Stripping: O(N^2) Push & Collisions Disabled ---

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    protected void tickCramming() {
        // No-op: Completely cuts O(N^2) bounding box scans among 300+ mobs
    }

    @Override
    public void pushAwayFrom(Entity entity) {
        // No-op: Do not push away
    }

    @Override
    public boolean isAffectedByDaylight() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // Invulnerable to regular attacks, mobs, void, explosions
        return false;
    }

    // --- INavMeshAgent Implementation ---

    @Override
    public NavPath getCurrentNavPath() {
        return currentNavPath;
    }

    @Override
    public void setCurrentNavPath(NavPath path) {
        this.currentNavPath = path;
        ua.polynav.network.PathNetwork.sendPathToClients(this, path);
    }

    public NavMeshNavigation getNavMeshNavigation() {
        return navMeshNavigation;
    }

    // --- Interaction Listener ---

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);

        // Admin Wand Shift + Right-Click -> open Admin GUI
        if (held.getItem() instanceof NpcWandItem) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                if (serverPlayer.hasPermissionLevel(2)) {
                    NpcNetwork.sendOpenAdminGuiToClient(serverPlayer, this);
                    return ActionResult.SUCCESS;
                } else {
                    player.sendMessage(Text.literal("§cУ вас нет прав для настройки NPC."), true);
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.SUCCESS;
        }

        // Path Wand Click -> select this NPC for patrol point adding
        if (held.getItem() instanceof PathWandItem) {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                PathWandItem.setSelectedNpc(serverPlayer, this.getNpcId());
                player.sendMessage(Text.literal("§aВыбран NPC для записи маршрута: §e" + this.getNpcId()), true);
            }
            return ActionResult.SUCCESS;
        }

        // Regular Right-Click -> Stop navigation, face player, open dialogue
        if (!this.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            this.navMeshNavigation.stop();
            lookAtEntity(player, 60.0f, 60.0f);

            NpcData data = getNpcData();
            if (data != null && !data.getDialogueId().isEmpty()) {
                DialogueData.Tree tree = NpcJsonStorage.getDialogue(data.getDialogueId());
                if (tree != null) {
                    NpcNetwork.sendOpenDialogueToClient(serverPlayer, this, tree, tree.getStartNodeId());
                    return ActionResult.SUCCESS;
                }
            }

            // Fallback: If no dialogue tree attached, but trade is present, open trade
            if (data != null && !data.getTradeMatrixId().isEmpty()) {
                NpcNetwork.sendOpenTradeToClient(serverPlayer, this);
                return ActionResult.SUCCESS;
            }

            // If nothing attached, show simple message
            player.sendMessage(Text.literal("§e" + this.getName().getString() + "§7: Приветствую тебя, путник."), false);
            return ActionResult.SUCCESS;
        }

        return ActionResult.SUCCESS;
    }

    // --- Tick Logic & Barks ---

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient()) {
            if (this.manuallyDeleted) {
                this.discard();
                return;
            }

            NpcData data = getNpcData();
            if (data != null) {
                // Ensure tracked in active entities
                NpcLifecycleManager.registerEntity(this);

                // AI behavior tick
                aiController.tick(data);

                // Idle Barks tick
                handleIdleBarks(data);
            }
        } else {
            // Client side bark display countdown
            if (barkDisplayTicks > 0) {
                barkDisplayTicks--;
                if (barkDisplayTicks <= 0) {
                    setCurrentBark("");
                }
            }
        }
    }

    private void handleIdleBarks(NpcData data) {
        List<String> barks = data.getIdleBarks();
        if (barks == null || barks.isEmpty()) return;

        int intervalTicks = Math.max(5, data.getBarkIntervalSeconds()) * 20;
        if (++barkTickTimer >= intervalTicks) {
            barkTickTimer = 0;
            String bark = barks.get(this.random.nextInt(barks.size()));
            if (bark != null && !bark.trim().isEmpty()) {
                // Send floating bark packet to nearby players without chat spam
                NpcNetwork.sendBarkToNearbyClients(this, bark, data.getBarkRadius());
            }
        }
    }

    public void setClientBarkDisplay(String bark) {
        setCurrentBark(bark);
        this.barkDisplayTicks = 120; // 6 seconds display
    }

    public void markDeleted() {
        this.manuallyDeleted = true;
        this.discard();
    }

    public boolean isManuallyDeleted() {
        return manuallyDeleted;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!this.getWorld().isClient()) {
            NpcLifecycleManager.unregisterEntity(this);
        }
    }

    // --- Persistence / NBT ---

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("NpcId", getNpcId());
        nbt.putBoolean("ManuallyDeleted", manuallyDeleted);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        String id = nbt.getString("NpcId");
        this.manuallyDeleted = nbt.getBoolean("ManuallyDeleted");
        if (this.manuallyDeleted) {
            this.discard();
            return;
        }

        if (!id.isEmpty()) {
            this.dataTracker.set(NPC_ID, id);
            NpcData data = NpcJsonStorage.getNpc(id);
            if (data != null) {
                setNpcData(data);
                if (!this.getWorld().isClient()) {
                    NpcLifecycleManager.registerEntity(this);
                }
            } else {
                // If the JSON passport is gone from storage, discard zombie entity immediately
                this.discard();
            }
        }
    }
}
