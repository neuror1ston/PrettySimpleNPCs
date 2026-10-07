package ua.neuror1ston.prettysimplenpcs.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data passport representing the persistent declarative state of an NPC.
 */
public class NpcData {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum SkinType {
        DEFAULT, // Steve (4px arms)
        SLIM     // Alex (3px arms)
    }

    public enum SkinSource {
        PLAYER_NICK,
        LOCAL_FILE
    }

    public enum AiState {
        STATIC,
        ROAM_RADIUS,
        PATROL
    }

    // Identity
    private String id = "npc_" + System.currentTimeMillis();
    private String name = "NPC";
    private String title = "";
    private SkinType skinType = SkinType.DEFAULT;
    private SkinSource skinSource = SkinSource.PLAYER_NICK;
    private String skinValue = ""; // Player nickname or texture filename
    private float scale = 1.0f; // 0.5 to 1.5

    // Equipment (Registry ID strings like "minecraft:iron_sword")
    private String helmetItem = "";
    private String chestplateItem = "";
    private String leggingsItem = "";
    private String bootsItem = "";
    private String mainHandItem = "";
    private String offHandItem = "";

    // Navigation & AI
    private AiState aiState = AiState.STATIC;
    private float baseSpeed = 0.28f;
    private boolean lookAtPlayers = true;
    private double homeX = 0;
    private double homeY = 0;
    private double homeZ = 0;
    private float homeYaw = 0;
    private float homePitch = 0;
    private double roamRadius = 12.0;
    private String dimension = "minecraft:overworld";

    // Patrol settings
    private List<Vec3d> patrolPoints = new ArrayList<>();
    private boolean patrolLoop = true;
    private boolean patrolPingPong = false;
    private int waitTicksPerPoint = 40; // 2 seconds

    // Dialogue & Barks
    private String dialogueId = "";
    private List<String> idleBarks = new ArrayList<>();
    private int barkIntervalSeconds = 20;
    private double barkRadius = 8.0;

    // Trade Matrix
    private String tradeMatrixId = "";

    public NpcData() {}

    public NpcData(String id) {
        this.id = id;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public SkinType getSkinType() { return skinType; }
    public void setSkinType(SkinType skinType) { this.skinType = skinType; }

    public SkinSource getSkinSource() { return skinSource; }
    public void setSkinSource(SkinSource skinSource) { this.skinSource = skinSource; }

    public String getSkinValue() { return skinValue; }
    public void setSkinValue(String skinValue) { this.skinValue = skinValue; }

    public float getScale() { return Math.max(0.5f, Math.min(1.5f, scale)); }
    public void setScale(float scale) { this.scale = Math.max(0.5f, Math.min(1.5f, scale)); }

    public String getHelmetItem() { return helmetItem; }
    public void setHelmetItem(String helmetItem) { this.helmetItem = helmetItem; }

    public String getChestplateItem() { return chestplateItem; }
    public void setChestplateItem(String chestplateItem) { this.chestplateItem = chestplateItem; }

    public String getLeggingsItem() { return leggingsItem; }
    public void setLeggingsItem(String leggingsItem) { this.leggingsItem = leggingsItem; }

    public String getBootsItem() { return bootsItem; }
    public void setBootsItem(String bootsItem) { this.bootsItem = bootsItem; }

    public String getMainHandItem() { return mainHandItem; }
    public void setMainHandItem(String mainHandItem) { this.mainHandItem = mainHandItem; }

    public String getOffHandItem() { return offHandItem; }
    public void setOffHandItem(String offHandItem) { this.offHandItem = offHandItem; }

    public AiState getAiState() { return aiState; }
    public void setAiState(AiState aiState) { this.aiState = aiState; }

    public float getBaseSpeed() { return baseSpeed; }
    public void setBaseSpeed(float baseSpeed) { this.baseSpeed = baseSpeed; }

    public boolean isLookAtPlayers() { return lookAtPlayers; }
    public void setLookAtPlayers(boolean lookAtPlayers) { this.lookAtPlayers = lookAtPlayers; }

    public double getHomeX() { return homeX; }
    public double getHomeY() { return homeY; }
    public double getHomeZ() { return homeZ; }
    public Vec3d getHomePos() { return new Vec3d(homeX, homeY, homeZ); }
    public void setHomePos(Vec3d pos) {
        this.homeX = pos.x;
        this.homeY = pos.y;
        this.homeZ = pos.z;
    }

    public float getHomeYaw() { return homeYaw; }
    public void setHomeYaw(float homeYaw) { this.homeYaw = homeYaw; }

    public float getHomePitch() { return homePitch; }
    public void setHomePitch(float homePitch) { this.homePitch = homePitch; }

    public double getRoamRadius() { return roamRadius; }
    public void setRoamRadius(double roamRadius) { this.roamRadius = roamRadius; }

    public String getDimension() { return dimension != null ? dimension : "minecraft:overworld"; }
    public void setDimension(String dimension) { this.dimension = dimension != null ? dimension : "minecraft:overworld"; }

    public List<Vec3d> getPatrolPoints() { return patrolPoints; }
    public void setPatrolPoints(List<Vec3d> patrolPoints) { this.patrolPoints = patrolPoints; }

    public boolean isPatrolLoop() { return patrolLoop; }
    public void setPatrolLoop(boolean patrolLoop) { this.patrolLoop = patrolLoop; }

    public boolean isPatrolPingPong() { return patrolPingPong; }
    public void setPatrolPingPong(boolean patrolPingPong) { this.patrolPingPong = patrolPingPong; }

    public int getWaitTicksPerPoint() { return waitTicksPerPoint; }
    public void setWaitTicksPerPoint(int waitTicksPerPoint) { this.waitTicksPerPoint = waitTicksPerPoint; }

    public String getDialogueId() { return dialogueId; }
    public void setDialogueId(String dialogueId) { this.dialogueId = dialogueId; }

    public List<String> getIdleBarks() { return idleBarks; }
    public void setIdleBarks(List<String> idleBarks) { this.idleBarks = idleBarks; }

    public int getBarkIntervalSeconds() { return barkIntervalSeconds; }
    public void setBarkIntervalSeconds(int barkIntervalSeconds) { this.barkIntervalSeconds = barkIntervalSeconds; }

    public double getBarkRadius() { return barkRadius; }
    public void setBarkRadius(double barkRadius) { this.barkRadius = barkRadius; }

    public String getTradeMatrixId() { return tradeMatrixId; }
    public void setTradeMatrixId(String tradeMatrixId) { this.tradeMatrixId = tradeMatrixId; }
}
