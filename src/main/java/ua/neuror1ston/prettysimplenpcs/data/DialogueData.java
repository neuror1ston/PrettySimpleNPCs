package ua.neuror1ston.prettysimplenpcs.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data structures for dialogue trees, branching choices, conditions, and actions.
 */
public class DialogueData {

    public static class Action {
        public enum ActionType {
            CONSOLE_COMMAND,
            PLAYER_COMMAND,
            GIVE_ITEM,
            TAKE_ITEM,
            PLAY_SOUND,
            SET_FLAG,
            REMOVE_FLAG,
            OPEN_TRADE
        }

        private ActionType type = ActionType.CONSOLE_COMMAND;
        private String value = ""; // Command, item ID, sound name, or flag key
        private int count = 1;      // Item count or numeric param

        public Action() {}

        public Action(ActionType type, String value) {
            this.type = type;
            this.value = value;
        }

        public Action(ActionType type, String value, int count) {
            this.type = type;
            this.value = value;
            this.count = count;
        }

        public ActionType getType() { return type; }
        public void setType(ActionType type) { this.type = type; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class Choice {
        private String text = "Продовжити";
        private String targetNodeId = ""; // If empty or "EXIT", closes dialogue
        private String requiredFlag = ""; // Flag required to be present in SQLite
        private String prohibitedFlag = ""; // Flag that must NOT be present
        private String requiredItem = ""; // Item ID required in player's inventory
        private int requiredItemCount = 1;
        private List<Action> actions = new ArrayList<>();

        public Choice() {}

        public Choice(String text, String targetNodeId) {
            this.text = text;
            this.targetNodeId = targetNodeId;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getTargetNodeId() { return targetNodeId; }
        public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

        public String getRequiredFlag() { return requiredFlag; }
        public void setRequiredFlag(String requiredFlag) { this.requiredFlag = requiredFlag; }

        public String getProhibitedFlag() { return prohibitedFlag; }
        public void setProhibitedFlag(String prohibitedFlag) { this.prohibitedFlag = prohibitedFlag; }

        public String getRequiredItem() { return requiredItem; }
        public void setRequiredItem(String requiredItem) { this.requiredItem = requiredItem; }

        public int getRequiredItemCount() { return requiredItemCount; }
        public void setRequiredItemCount(int requiredItemCount) { this.requiredItemCount = requiredItemCount; }

        public List<Action> getActions() { return actions; }
        public void setActions(List<Action> actions) { this.actions = actions; }
    }

    public static class Node {
        private String id = "start";
        private String text = "...";
        private List<Action> enterActions = new ArrayList<>();
        private List<Choice> choices = new ArrayList<>();

        public Node() {}

        public Node(String id, String text) {
            this.id = id;
            this.text = text;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public List<Action> getEnterActions() { return enterActions; }
        public void setEnterActions(List<Action> enterActions) { this.enterActions = enterActions; }

        public List<Choice> getChoices() { return choices; }
        public void setChoices(List<Choice> choices) { this.choices = choices; }
    }

    public static class Tree {
        private String id = "new_dialogue";
        private String title = "Новий діалог";
        private String startNodeId = "start";
        private Map<String, Node> nodes = new HashMap<>();

        public Tree() {}

        public Tree(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getStartNodeId() { return startNodeId; }
        public void setStartNodeId(String startNodeId) { this.startNodeId = startNodeId; }

        public Map<String, Node> getNodes() { return nodes; }
        public void setNodes(Map<String, Node> nodes) { this.nodes = nodes; }

        public Node getNode(String nodeId) {
            return nodes.get(nodeId);
        }
    }
}
