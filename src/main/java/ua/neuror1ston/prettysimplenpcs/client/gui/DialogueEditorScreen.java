package ua.neuror1ston.prettysimplenpcs.client.gui;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ua.neuror1ston.prettysimplenpcs.client.gui.widgets.MinimalButton;
import ua.neuror1ston.prettysimplenpcs.data.DialogueData;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;

import java.util.*;

/**
 * Dedicated Narrative Graph & Full-Screen Canvas Dialogue Editor.
 * Features:
 * 1. Interactive moving Graph Map (Карта графа):
 *    - Pannable 2D canvas with mouse drag.
 *    - Node cards showing ID, speech excerpt, and outgoing choices.
 *    - Connecting lines/arrows between choices and target nodes.
 *    - Dragging node cards to organize layout.
 *    - Instant open/edit on double click or button.
 * 2. Giant Multi-line Writing Canvas (Полотно реплики):
 *    - Full-screen multi-line text editor for writing long speeches.
 *    - Compact formatting toolbar (Bold, Italic, Placeholders, Colors).
 *    - Responsive scrollable choices manager with compact add-row at bottom.
 *    - Responsive scrollable actions manager.
 */
public class DialogueEditorScreen extends Screen {
    private final DialogueData.Tree tree;
    private final Screen parentScreen;

    // Mode: 0 = Graph Map, 1 = Full Canvas Text Editor
    private int activeMode = 0;

    private String selectedNodeId = "start";
    private boolean savedNotification = false;

    // Graph Map state
    private int panX = 50;
    private int panY = 50;
    private boolean isDraggingMap = false;
    private double lastDragMouseX = 0;
    private double lastDragMouseY = 0;

    private String draggingNodeId = null;
    private int dragNodeOffsetX = 0;
    private int dragNodeOffsetY = 0;

    public static class NodePoint {
        public int x;
        public int y;
        public NodePoint(int x, int y) { this.x = x; this.y = y; }
    }
    private final Map<String, NodePoint> nodePositions = new HashMap<>();

    // Full Canvas Text Editor widgets
    private EditBoxWidget canvasEditBox;
    private TextFieldWidget newChoiceTextField;
    private TextFieldWidget newChoiceTargetField;
    private TextFieldWidget actionValField;
    private DialogueData.Action.ActionType selectedActionType = DialogueData.Action.ActionType.CONSOLE_COMMAND;

    private int choicesScrollOffset = 0;
    private int actionsScrollOffset = 0;

    // Graph Map mode widgets
    private TextFieldWidget newGraphNodeField;

    public DialogueEditorScreen(DialogueData.Tree tree, Screen parentScreen) {
        super(Text.literal("Редактор диалога: " + tree.getId()));
        this.tree = tree;
        this.parentScreen = parentScreen;

        if (tree.getNodes().isEmpty()) {
            tree.getNodes().put("start", new DialogueData.Node("start", "Приветствую тебя, путник!"));
            tree.getNode("start").getChoices().add(new DialogueData.Choice("Мне пора.", "EXIT"));
        }

        if (!tree.getNodes().containsKey(selectedNodeId)) {
            selectedNodeId = tree.getStartNodeId().isEmpty() ? tree.getNodes().keySet().iterator().next() : tree.getStartNodeId();
        }

        calculateDefaultNodePositions();
    }

    private void calculateDefaultNodePositions() {
        if (!tree.getNodes().containsKey(selectedNodeId)) {
            selectedNodeId = tree.getStartNodeId().isEmpty() ? tree.getNodes().keySet().iterator().next() : tree.getStartNodeId();
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        Map<String, Integer> colMap = new HashMap<>();
        Map<String, Integer> rowMap = new HashMap<>();

        String start = tree.getStartNodeId().isEmpty() ? "start" : tree.getStartNodeId();
        if (tree.getNodes().containsKey(start)) {
            queue.add(start);
            visited.add(start);
            colMap.put(start, 0);
        }

        int[] colHeights = new int[25];

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            int col = colMap.getOrDefault(curr, 0);
            int row = colHeights[col]++;
            rowMap.put(curr, row);

            DialogueData.Node n = tree.getNode(curr);
            if (n != null) {
                for (DialogueData.Choice c : n.getChoices()) {
                    String target = c.getTargetNodeId();
                    if (!target.isEmpty() && !"EXIT".equalsIgnoreCase(target) && tree.getNodes().containsKey(target)) {
                        if (!visited.contains(target)) {
                            visited.add(target);
                            colMap.put(target, col + 1);
                            queue.add(target);
                        }
                    }
                }
            }
        }

        // Unvisited fallback
        for (String id : tree.getNodes().keySet()) {
            if (!visited.contains(id)) {
                int col = 0;
                colMap.put(id, col);
                rowMap.put(id, colHeights[col]++);
            }
        }

        // Assign default coordinates
        for (String id : tree.getNodes().keySet()) {
            if (!nodePositions.containsKey(id)) {
                int col = colMap.getOrDefault(id, 0);
                int row = rowMap.getOrDefault(id, 0);
                nodePositions.put(id, new NodePoint(60 + (col * 220), 50 + (row * 130)));
            }
        }
    }

    @Override
    protected void init() {
        this.clearChildren();

        // 1. Top Bar Navigation
        initTopBar();

        // 2. Active Mode Layout
        if (activeMode == 0) {
            initGraphMapMode();
        } else {
            initFullCanvasEditorMode();
        }
    }

    private void initTopBar() {
        // Back Button
        MinimalButton backBtn = MinimalButton.builder(Text.literal("Назад к NPC"), button -> {
            if (this.client != null) {
                this.client.setScreen(this.parentScreen);
            }
        }).dimensions(10, 6, 90, 18).build();
        this.addDrawableChild(backBtn);

        // Mode switch tabs
        MinimalButton graphTabBtn = MinimalButton.builder(Text.literal("КАРТА ГРАФА"), button -> {
            this.activeMode = 0;
            this.init();
        }).dimensions(108, 6, 105, 18).build();
        graphTabBtn.setActiveState(activeMode == 0);
        this.addDrawableChild(graphTabBtn);

        MinimalButton canvasTabBtn = MinimalButton.builder(Text.literal("ПОЛОТНО РЕПЛИКИ"), button -> {
            this.activeMode = 1;
            this.init();
        }).dimensions(218, 6, 120, 18).build();
        canvasTabBtn.setActiveState(activeMode == 1);
        this.addDrawableChild(canvasTabBtn);

        // Save Button
        MinimalButton saveBtn = MinimalButton.builder(Text.literal("Сохранить"), button -> {
            saveDialogueToServer();
            savedNotification = true;
        }).dimensions(this.width - 100, 6, 90, 18).build();
        saveBtn.setCustomBorderColor(0xFF227722);
        this.addDrawableChild(saveBtn);
    }

    private void initGraphMapMode() {
        // Bottom control toolbar on Graph Map
        int barY = this.height - 24;

        this.newGraphNodeField = new TextFieldWidget(this.textRenderer, 12, barY, 110, 16, Text.literal("node_id"));
        this.newGraphNodeField.setText("node_" + (tree.getNodes().size() + 1));
        this.addSelectableChild(this.newGraphNodeField);

        MinimalButton addNodeBtn = MinimalButton.builder(Text.literal("+ Новый узел"), button -> {
            String id = newGraphNodeField.getText().trim();
            if (!id.isEmpty() && !tree.getNodes().containsKey(id)) {
                tree.getNodes().put(id, new DialogueData.Node(id, "Новая реплика..."));
                nodePositions.put(id, new NodePoint(-panX + (width / 2) - 80, -panY + (height / 2) - 40));
                this.selectedNodeId = id;
                this.init();
            }
        }).dimensions(126, barY, 95, 16).build();
        this.addDrawableChild(addNodeBtn);

        MinimalButton resetViewBtn = MinimalButton.builder(Text.literal("Центрировать"), button -> {
            this.panX = 50;
            this.panY = 50;
        }).dimensions(225, barY, 85, 16).build();
        this.addDrawableChild(resetViewBtn);
    }

    private void initFullCanvasEditorMode() {
        DialogueData.Node currentNode = tree.getNode(selectedNodeId);
        if (currentNode == null) return;

        int canvasX = 14;
        int canvasW = this.width - 28;

        // Toolbar row: Formatting and placeholders (compact buttons to prevent overflow)
        int tbY = 28;
        int curX = canvasX;

        // B & I
        this.addDrawableChild(MinimalButton.builder(Text.literal("§lB"), b -> insertFormat("§l")).dimensions(curX, tbY, 20, 16).build());
        curX += 22;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§oI"), b -> insertFormat("§o")).dimensions(curX, tbY, 20, 16).build());
        curX += 24;

        // Placeholders
        this.addDrawableChild(MinimalButton.builder(Text.literal("%player%"), b -> insertFormat("%player%")).dimensions(curX, tbY, 52, 16).build());
        curX += 54;
        this.addDrawableChild(MinimalButton.builder(Text.literal("%npc%"), b -> insertFormat("%npc_name%")).dimensions(curX, tbY, 40, 16).build());
        curX += 44;

        // Colors
        int colBtnW = 18;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§6●"), b -> insertFormat("§6")).dimensions(curX, tbY, colBtnW, 16).build());
        curX += colBtnW + 2;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§e●"), b -> insertFormat("§e")).dimensions(curX, tbY, colBtnW, 16).build());
        curX += colBtnW + 2;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§a●"), b -> insertFormat("§a")).dimensions(curX, tbY, colBtnW, 16).build());
        curX += colBtnW + 2;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§c●"), b -> insertFormat("§c")).dimensions(curX, tbY, colBtnW, 16).build());
        curX += colBtnW + 2;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§7●"), b -> insertFormat("§7")).dimensions(curX, tbY, colBtnW, 16).build());
        curX += colBtnW + 2;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§f●"), b -> insertFormat("§f")).dimensions(curX, tbY, colBtnW, 16).build());
        curX += colBtnW + 2;
        this.addDrawableChild(MinimalButton.builder(Text.literal("§c✕"), b -> insertFormat("§r")).dimensions(curX, tbY, colBtnW, 16).build());

        // Node Quick Switcher (Right aligned)
        int nodeBtnW = 120;
        int nodeBtnX = canvasX + canvasW - nodeBtnW;
        MinimalButton nodeSwitchBtn = MinimalButton.builder(Text.literal("Узел: " + selectedNodeId), button -> {
            List<String> keys = new ArrayList<>(tree.getNodes().keySet());
            int idx = keys.indexOf(selectedNodeId);
            selectedNodeId = keys.get((idx + 1) % keys.size());
            this.init();
        }).dimensions(nodeBtnX, tbY, nodeBtnW, 16).build();
        this.addDrawableChild(nodeSwitchBtn);

        // Multi-line Writing Canvas
        int canvasY = tbY + 20;
        int bottomSectionH = Math.min(130, Math.max(92, (int) (this.height * 0.40f)));
        int canvasH = Math.max(50, this.height - canvasY - bottomSectionH - 12);

        this.canvasEditBox = new EditBoxWidget(this.textRenderer, canvasX, canvasY, canvasW, canvasH,
                Text.literal("Введите реплику персонажа..."), Text.literal("Реплика"));
        this.canvasEditBox.setMaxLength(2048);
        this.canvasEditBox.setText(currentNode.getText());
        this.canvasEditBox.setChangeListener(currentNode::setText);
        this.addSelectableChild(this.canvasEditBox);

        // Bottom Section: Choices (Left) and Actions (Right)
        int bottomY = canvasY + canvasH + 8;
        int halfW = (canvasW - 10) / 2;

        // Left: Choices List & Add Choice
        initChoicesSection(canvasX, bottomY, halfW, bottomSectionH, currentNode);

        // Right: Actions List & Add Action
        initActionsSection(canvasX + halfW + 10, bottomY, halfW, bottomSectionH, currentNode);
    }

    private void initChoicesSection(int x, int y, int w, int h, DialogueData.Node currentNode) {
        int addY = y + h - 20;

        this.newChoiceTextField = new TextFieldWidget(this.textRenderer, x + 4, addY, w - 86, 16, Text.literal("Текст"));
        this.newChoiceTextField.setPlaceholder(Text.literal("Текст ответа..."));
        if (this.newChoiceTextField.getText().isEmpty()) {
            this.newChoiceTextField.setText("Далее");
        }
        this.addSelectableChild(this.newChoiceTextField);

        this.newChoiceTargetField = new TextFieldWidget(this.textRenderer, x + w - 80, addY, 54, 16, Text.literal("Куда"));
        this.newChoiceTargetField.setPlaceholder(Text.literal("EXIT"));
        if (this.newChoiceTargetField.getText().isEmpty()) {
            this.newChoiceTargetField.setText("EXIT");
        }
        this.addSelectableChild(this.newChoiceTargetField);

        MinimalButton addChoiceBtn = MinimalButton.builder(Text.literal("+"), b -> {
            String ct = newChoiceTextField.getText().trim();
            String tg = newChoiceTargetField.getText().trim();
            if (!ct.isEmpty()) {
                currentNode.getChoices().add(new DialogueData.Choice(ct, tg.isEmpty() ? "EXIT" : tg));
                newChoiceTextField.setText("");
                newChoiceTargetField.setText("EXIT");
                int listH = (h - 22) - 16;
                choicesScrollOffset = Math.max(0, currentNode.getChoices().size() * 18 - listH);
                this.init();
            }
        }).dimensions(x + w - 24, addY, 20, 16).build();
        addChoiceBtn.setCustomBorderColor(0xFF338833);
        this.addDrawableChild(addChoiceBtn);
    }

    private void initActionsSection(int x, int y, int w, int h, DialogueData.Node currentNode) {
        int addY = y + h - 20;

        MinimalButton actTypeBtn = MinimalButton.builder(Text.literal(formatActionTypeNameShort(selectedActionType)), b -> {
            DialogueData.Action.ActionType[] types = DialogueData.Action.ActionType.values();
            int next = (selectedActionType.ordinal() + 1) % types.length;
            selectedActionType = types[next];
            b.setMessage(Text.literal(formatActionTypeNameShort(selectedActionType)));
        }).dimensions(x + 4, addY, 74, 16).build();
        this.addDrawableChild(actTypeBtn);

        this.actionValField = new TextFieldWidget(this.textRenderer, x + 80, addY, w - 106, 16, Text.literal("Значение"));
        this.actionValField.setPlaceholder(Text.literal("playsound ..."));
        this.addSelectableChild(this.actionValField);

        MinimalButton addActionBtn = MinimalButton.builder(Text.literal("+"), b -> {
            String val = actionValField.getText().trim();
            if (!val.isEmpty()) {
                currentNode.getEnterActions().add(new DialogueData.Action(selectedActionType, val));
                actionValField.setText("");
                int listH = (h - 22) - 16;
                actionsScrollOffset = Math.max(0, currentNode.getEnterActions().size() * 18 - listH);
                this.init();
            }
        }).dimensions(x + w - 24, addY, 20, 16).build();
        addActionBtn.setCustomBorderColor(0xFF338833);
        this.addDrawableChild(addActionBtn);
    }

    private void insertFormat(String code) {
        if (canvasEditBox != null) {
            String cur = canvasEditBox.getText();
            String updated = cur + code;
            canvasEditBox.setText(updated);
            DialogueData.Node node = tree.getNode(selectedNodeId);
            if (node != null) {
                node.setText(updated);
            }
        }
    }

    private String formatActionTypeNameShort(DialogueData.Action.ActionType type) {
        return switch (type) {
            case CONSOLE_COMMAND -> "Консоль";
            case PLAYER_COMMAND -> "Команда";
            case GIVE_ITEM -> "Выдать";
            case TAKE_ITEM -> "Забрать";
            case PLAY_SOUND -> "Звук";
            case SET_FLAG -> "+Флаг";
            case REMOVE_FLAG -> "-Флаг";
            case OPEN_TRADE -> "Торговля";
        };
    }

    private void saveDialogueToServer() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(NpcData.GSON.toJson(tree));
        ClientPlayNetworking.send(NpcNetwork.SAVE_DIALOGUE_C2S, buf);
    }

    // --- Interactive Mouse Handlers ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeMode == 0 && mouseY >= 28 && mouseY <= height - 26) {
            // Check if clicked any node card
            int cardW = 160;

            for (Map.Entry<String, DialogueData.Node> entry : tree.getNodes().entrySet()) {
                String id = entry.getKey();
                DialogueData.Node node = entry.getValue();
                NodePoint np = nodePositions.computeIfAbsent(id, k -> new NodePoint(60, 60));

                int sx = panX + np.x;
                int sy = panY + np.y;
                int cardH = Math.max(68, 44 + Math.min(4, node.getChoices().size()) * 12);

                if (mouseX >= sx && mouseX <= sx + cardW && mouseY >= sy && mouseY <= sy + cardH) {
                    this.selectedNodeId = id;

                    // Delete button [X] at top-right
                    if (!"start".equalsIgnoreCase(id) && mouseX >= sx + cardW - 18 && mouseX <= sx + cardW - 4 && mouseY >= sy + 4 && mouseY <= sy + 18) {
                        tree.getNodes().remove(id);
                        nodePositions.remove(id);
                        this.selectedNodeId = tree.getNodes().keySet().iterator().next();
                        this.init();
                        return true;
                    }

                    // Open in canvas editor button
                    if (mouseY >= sy + cardH - 18 && mouseY <= sy + cardH - 2) {
                        this.activeMode = 1;
                        this.init();
                        return true;
                    }

                    // Card dragging
                    if (button == 0) {
                        draggingNodeId = id;
                        dragNodeOffsetX = (int) mouseX - sx;
                        dragNodeOffsetY = (int) mouseY - sy;
                        return true;
                    }
                }
            }

            // Clicked empty canvas: start panning
            if (button == 0 || button == 2) {
                isDraggingMap = true;
                lastDragMouseX = mouseX;
                lastDragMouseY = mouseY;
                return true;
            }
        } else if (activeMode == 1) {
            DialogueData.Node currentNode = tree.getNode(selectedNodeId);
            if (currentNode != null) {
                int canvasX = 14;
                int canvasW = this.width - 28;
                int bottomSectionH = Math.min(130, Math.max(92, (int) (this.height * 0.40f)));
                int canvasH = Math.max(50, this.height - 48 - bottomSectionH - 12);
                int bottomY = 48 + canvasH + 8;
                int halfW = (canvasW - 10) / 2;

                int choicesX = canvasX;
                int choicesListY = bottomY + 16;
                int choicesListH = bottomSectionH - 38;

                // 1. Check clicks inside Choices list
                if (mouseX >= choicesX + 4 && mouseX <= choicesX + halfW - 4 && mouseY >= choicesListY && mouseY <= choicesListY + choicesListH) {
                    for (int i = 0; i < currentNode.getChoices().size(); i++) {
                        int rowY = choicesListY + (i * 18) - choicesScrollOffset;
                        if (mouseY >= rowY && mouseY < rowY + 16) {
                            // Check delete button [X]
                            if (mouseX >= choicesX + halfW - 22 && mouseX <= choicesX + halfW - 10 && mouseY >= rowY + 2 && mouseY <= rowY + 14) {
                                currentNode.getChoices().remove(i);
                                this.init();
                                return true;
                            }
                            // Clicked row: populate text fields for editing
                            DialogueData.Choice c = currentNode.getChoices().get(i);
                            if (newChoiceTextField != null) newChoiceTextField.setText(c.getText());
                            if (newChoiceTargetField != null) newChoiceTargetField.setText(c.getTargetNodeId());
                            return true;
                        }
                    }
                }

                // 2. Check clicks inside Actions list
                int actionsX = canvasX + halfW + 10;
                int actListY = bottomY + 16;
                int actListH = bottomSectionH - 38;

                if (mouseX >= actionsX + 4 && mouseX <= actionsX + halfW - 4 && mouseY >= actListY && mouseY <= actListY + actListH) {
                    for (int j = 0; j < currentNode.getEnterActions().size(); j++) {
                        int rowY = actListY + (j * 18) - actionsScrollOffset;
                        if (mouseY >= rowY && mouseY < rowY + 16) {
                            // Check delete button [X]
                            if (mouseX >= actionsX + halfW - 22 && mouseX <= actionsX + halfW - 10 && mouseY >= rowY + 2 && mouseY <= rowY + 14) {
                                currentNode.getEnterActions().remove(j);
                                this.init();
                                return true;
                            }
                            // Clicked row: populate value field
                            DialogueData.Action a = currentNode.getEnterActions().get(j);
                            if (actionValField != null) actionValField.setText(a.getValue());
                            selectedActionType = a.getType();
                            this.init();
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (activeMode == 1) {
            int canvasX = 14;
            int canvasW = this.width - 28;
            int bottomSectionH = Math.min(130, Math.max(92, (int) (this.height * 0.40f)));
            int canvasH = Math.max(50, this.height - 48 - bottomSectionH - 12);
            int bottomY = 48 + canvasH + 8;
            int halfW = (canvasW - 10) / 2;

            DialogueData.Node currentNode = tree.getNode(selectedNodeId);
            if (currentNode != null) {
                int listH = bottomSectionH - 38;

                // Choices list scroll
                if (mouseX >= canvasX && mouseX <= canvasX + halfW && mouseY >= bottomY + 16 && mouseY <= bottomY + bottomSectionH - 22) {
                    int totalH = currentNode.getChoices().size() * 18;
                    int maxScroll = Math.max(0, totalH - listH);
                    if (maxScroll > 0) {
                        choicesScrollOffset = Math.max(0, Math.min(maxScroll, choicesScrollOffset - (int) (amount * 16)));
                        return true;
                    }
                }

                // Actions list scroll
                int actionsX = canvasX + halfW + 10;
                if (mouseX >= actionsX && mouseX <= actionsX + halfW && mouseY >= bottomY + 16 && mouseY <= bottomY + bottomSectionH - 22) {
                    int totalH = currentNode.getEnterActions().size() * 18;
                    int maxScroll = Math.max(0, totalH - listH);
                    if (maxScroll > 0) {
                        actionsScrollOffset = Math.max(0, Math.min(maxScroll, actionsScrollOffset - (int) (amount * 16)));
                        return true;
                    }
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeMode == 0) {
            if (draggingNodeId != null) {
                NodePoint np = nodePositions.get(draggingNodeId);
                if (np != null) {
                    np.x = (int) mouseX - panX - dragNodeOffsetX;
                    np.y = (int) mouseY - panY - dragNodeOffsetY;
                    return true;
                }
            } else if (isDraggingMap) {
                panX += (int) (mouseX - lastDragMouseX);
                panY += (int) (mouseY - lastDragMouseY);
                lastDragMouseX = mouseX;
                lastDragMouseY = mouseY;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingNodeId = null;
        this.isDraggingMap = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // --- Screen Rendering ---

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Deep medieval black background
        context.fill(0, 0, this.width, this.height, 0xFF0A0A0A);

        // Top bar separator
        context.fill(0, 26, this.width, 27, 0xFF2A2A2A);

        if (savedNotification) {
            context.drawText(this.textRenderer, "§a[Сохранено!]", this.width - 190, 10, 0x88FF88, false);
        }

        if (activeMode == 0) {
            renderGraphMap(context, mouseX, mouseY, delta);
        } else {
            renderFullCanvasEditor(context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderGraphMap(DrawContext context, int mouseX, int mouseY, float delta) {
        // Canvas clipping area
        int mapTop = 27;
        int mapBottom = this.height - 27;
        context.enableScissor(0, mapTop, this.width, mapBottom);

        // 1. Render subtle coordinate grid dots
        int gridStep = 40;
        int startX = (panX % gridStep + gridStep) % gridStep;
        int startY = mapTop + ((panY % gridStep + gridStep) % gridStep);
        for (int x = startX; x < this.width; x += gridStep) {
            for (int y = startY; y < mapBottom; y += gridStep) {
                context.fill(x, y, x + 2, y + 2, 0xFF1C1C1C);
            }
        }

        // 2. Render connecting lines between choice anchors and target nodes
        int cardW = 160;
        for (Map.Entry<String, DialogueData.Node> entry : tree.getNodes().entrySet()) {
            DialogueData.Node node = entry.getValue();
            NodePoint np = nodePositions.computeIfAbsent(node.getId(), k -> new NodePoint(60, 60));
            int fromX = panX + np.x + cardW;

            for (int i = 0; i < node.getChoices().size(); i++) {
                DialogueData.Choice c = node.getChoices().get(i);
                int fromY = panY + np.y + 36 + (Math.min(i, 4) * 12);

                String targetId = c.getTargetNodeId();
                if ("EXIT".equalsIgnoreCase(targetId) || targetId.isEmpty()) {
                    // Draw red exit marker
                    context.fill(fromX, fromY - 1, fromX + 16, fromY + 1, 0xFF993333);
                    context.drawText(this.textRenderer, "§c[ВЫХОД]", fromX + 18, fromY - 4, 0xEE6666, false);
                } else if (nodePositions.containsKey(targetId)) {
                    NodePoint targetNp = nodePositions.get(targetId);
                    int toX = panX + targetNp.x;
                    int toY = panY + targetNp.y + 16;

                    // Direct connection line with arrow
                    drawConnectionLine(context, fromX, fromY, toX, toY);
                }
            }
        }

        // 3. Render Node Cards
        for (Map.Entry<String, DialogueData.Node> entry : tree.getNodes().entrySet()) {
            String id = entry.getKey();
            DialogueData.Node node = entry.getValue();
            NodePoint np = nodePositions.computeIfAbsent(id, k -> new NodePoint(60, 60));

            int sx = panX + np.x;
            int sy = panY + np.y;
            int cardH = Math.max(68, 44 + Math.min(4, node.getChoices().size()) * 12);
            boolean isSelected = id.equals(selectedNodeId);
            boolean isStart = id.equals(tree.getStartNodeId()) || "start".equalsIgnoreCase(id);

            // Card background & borders
            context.fill(sx, sy, sx + cardW, sy + cardH, 0xFF141414);
            context.drawBorder(sx, sy, cardW, cardH, isSelected ? 0xFF888888 : 0xFF353535);
            if (isSelected) {
                context.drawBorder(sx + 1, sy + 1, cardW - 2, cardH - 2, 0xFF555555);
            }

            // Header title
            String header = (isStart ? "§6[СТАРТ] " : "§e") + id;
            context.drawText(this.textRenderer, header, sx + 6, sy + 6, 0xFFFFFF, false);

            // Delete [X] button
            if (!"start".equalsIgnoreCase(id)) {
                boolean isDelHover = mouseX >= sx + cardW - 18 && mouseX <= sx + cardW - 4 && mouseY >= sy + 4 && mouseY <= sy + 18;
                context.fill(sx + cardW - 16, sy + 4, sx + cardW - 4, sy + 16, isDelHover ? 0xFF661111 : 0xFF220A0A);
                context.drawText(this.textRenderer, "X", sx + cardW - 13, sy + 6, isDelHover ? 0xFFFFFF : 0xFFAAAA, false);
            }

            // Excerpt snippet of speech
            String snippet = textRenderer.trimToWidth(node.getText().replace('\n', ' '), cardW - 14);
            context.drawText(this.textRenderer, "§8«§f" + snippet + "§8»", sx + 6, sy + 18, 0xCCCCCC, false);

            // Outgoing choices (up to 4 shown)
            for (int i = 0; i < Math.min(4, node.getChoices().size()); i++) {
                DialogueData.Choice c = node.getChoices().get(i);
                int cy = sy + 32 + (i * 12);
                String choiceSummary = (i + 1) + ". " + c.getText();
                String clipped = textRenderer.trimToWidth(choiceSummary, cardW - 45);
                context.drawText(this.textRenderer, "§7" + clipped, sx + 6, cy, 0xAAAAAA, false);
                context.drawText(this.textRenderer, "§b->" + c.getTargetNodeId(), sx + cardW - 38, cy, 0x66CCEE, false);
            }
            if (node.getChoices().size() > 4) {
                context.drawText(this.textRenderer, "§8+ еще " + (node.getChoices().size() - 4), sx + 6, sy + 32 + (4 * 12), 0x777777, false);
            }

            // Bottom Edit Button [РЕДАКТИРОВАТЬ]
            int editBtnY = sy + cardH - 16;
            boolean isEditHover = mouseX >= sx + 4 && mouseX <= sx + cardW - 4 && mouseY >= editBtnY && mouseY <= editBtnY + 12;
            context.fill(sx + 4, editBtnY, sx + cardW - 4, editBtnY + 12, isEditHover ? 0xFF282828 : 0xFF1B1B1B);
            context.drawBorder(sx + 4, editBtnY, cardW - 8, 12, isEditHover ? 0xFF666666 : 0xFF2E2E2E);
            context.drawText(this.textRenderer, "§7[Редактировать]", sx + 34, editBtnY + 2, 0xFFFFFF, false);
        }

        context.disableScissor();

        // Bottom graph status info
        context.fill(0, mapBottom, this.width, this.height, 0xFF0E0E0E);
        context.fill(0, mapBottom, this.width, mapBottom + 1, 0xFF2A2A2A);
        context.drawText(this.textRenderer, "§8[ЛКМ перетаскивание узлов / полотна, Двойной клик на узел для открытия]", 325, this.height - 20, 0x777777, false);

        if (newGraphNodeField != null) newGraphNodeField.render(context, mouseX, mouseY, delta);
    }

    private void drawConnectionLine(DrawContext context, int x1, int y1, int x2, int y2) {
        // Orthogonal connecting step line
        int midX = x1 + (x2 - x1) / 2;
        context.fill(Math.min(x1, midX), y1 - 1, Math.max(x1, midX), y1 + 1, 0xFF4A6572);
        context.fill(midX - 1, Math.min(y1, y2), midX + 1, Math.max(y1, y2), 0xFF4A6572);
        context.fill(Math.min(midX, x2), y2 - 1, Math.max(midX, x2), y2 + 1, 0xFF4A6572);

        // Arrow head pointing to target node
        context.drawText(this.textRenderer, ">", x2 - 5, y2 - 4, 0xFF88CCFF, false);
    }

    private void renderFullCanvasEditor(DrawContext context, int mouseX, int mouseY, float delta) {
        DialogueData.Node currentNode = tree.getNode(selectedNodeId);
        if (currentNode == null) return;

        int canvasX = 14;
        int canvasW = this.width - 28;
        int bottomSectionH = Math.min(130, Math.max(92, (int) (this.height * 0.40f)));
        int canvasH = Math.max(50, this.height - 48 - bottomSectionH - 12);
        int bottomY = 48 + canvasH + 8;
        int halfW = (canvasW - 10) / 2;

        // Render Canvas EditBox
        if (canvasEditBox != null) {
            canvasEditBox.render(context, mouseX, mouseY, delta);
        }

        // Character count indicator (Bottom-right of EditBox)
        context.drawText(this.textRenderer, currentNode.getText().length() + "/2048", canvasX + canvasW - 55, 48 + canvasH - 11, 0x777777, false);

        // --- Left Section: Choices ---
        int choicesX = canvasX;
        context.fill(choicesX, bottomY, choicesX + halfW, bottomY + bottomSectionH, 0xFF0E0E0E);
        context.drawBorder(choicesX, bottomY, halfW, bottomSectionH, 0xFF2A2A2A);
        context.drawText(this.textRenderer, "§6Варианты ответа (" + currentNode.getChoices().size() + "):", choicesX + 6, bottomY + 5, 0xFFFFAA, false);

        int listY = bottomY + 16;
        int listH = bottomSectionH - 38;

        context.enableScissor(choicesX + 2, listY, choicesX + halfW - 2, listY + listH);
        if (currentNode.getChoices().isEmpty()) {
            context.drawText(this.textRenderer, "§8[Нет вариантов, диалог завершится]", choicesX + 8, listY + 8, 0x666666, false);
        } else {
            for (int i = 0; i < currentNode.getChoices().size(); i++) {
                DialogueData.Choice c = currentNode.getChoices().get(i);
                int rowY = listY + (i * 18) - choicesScrollOffset;
                if (rowY + 16 < listY || rowY > listY + listH) continue;

                int rowW = halfW - 8;
                boolean isRowHover = (mouseX >= choicesX + 4 && mouseX <= choicesX + 4 + rowW &&
                                     mouseY >= rowY && mouseY < rowY + 16 &&
                                     mouseY >= listY && mouseY <= listY + listH);

                context.fill(choicesX + 4, rowY, choicesX + 4 + rowW, rowY + 16, isRowHover ? 0xFF1C221C : 0xFF141414);
                context.drawBorder(choicesX + 4, rowY, rowW, 16, isRowHover ? 0xFF354835 : 0xFF242424);

                String num = (i + 1) + ". ";
                String targetTxt = " -> [" + c.getTargetNodeId() + "]";
                int targetW = textRenderer.getWidth(targetTxt);
                int maxTxtW = rowW - 22 - targetW - 8;
                String clipped = textRenderer.trimToWidth(c.getText(), maxTxtW);

                context.drawText(this.textRenderer, num + clipped, choicesX + 8, rowY + 4, isRowHover ? 0xFFFFFF : 0xCCCCCC, false);
                context.drawText(this.textRenderer, targetTxt, choicesX + 4 + rowW - 18 - targetW, rowY + 4, 0x66CCEE, false);

                // Delete button [X]
                boolean isDelHover = isRowHover && (mouseX >= choicesX + 4 + rowW - 16 && mouseX <= choicesX + 4 + rowW - 4 && mouseY >= rowY + 2 && mouseY <= rowY + 14);
                context.fill(choicesX + 4 + rowW - 16, rowY + 2, choicesX + 4 + rowW - 4, rowY + 14, isDelHover ? 0xFF662222 : 0xFF2E1A1A);
                context.drawText(this.textRenderer, "x", choicesX + 4 + rowW - 12, rowY + 3, isDelHover ? 0xFFFFAA : 0xEE7777, false);
            }
        }
        context.disableScissor();

        // Choices scrollbar
        int totalChoicesH = currentNode.getChoices().size() * 18;
        int maxChoicesScroll = Math.max(0, totalChoicesH - listH);
        if (maxChoicesScroll > 0) {
            int sbH = Math.max(10, (int) ((float) listH / totalChoicesH * listH));
            int sbY = listY + (int) ((float) choicesScrollOffset / maxChoicesScroll * (listH - sbH));
            int sbX = choicesX + halfW - 4;
            context.fill(sbX, listY, sbX + 2, listY + listH, 0x44222222);
            context.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF666666);
        }

        // --- Right Section: Actions ---
        int actionsX = canvasX + halfW + 10;
        context.fill(actionsX, bottomY, actionsX + halfW, bottomY + bottomSectionH, 0xFF0E0E0E);
        context.drawBorder(actionsX, bottomY, halfW, bottomSectionH, 0xFF2A2A2A);
        context.drawText(this.textRenderer, "§eДействия при входе (" + currentNode.getEnterActions().size() + "):", actionsX + 6, bottomY + 5, 0xFFFFAA, false);

        int actListY = bottomY + 16;
        int actListH = bottomSectionH - 38;

        context.enableScissor(actionsX + 2, actListY, actionsX + halfW - 2, actListY + actListH);
        if (currentNode.getEnterActions().isEmpty()) {
            context.drawText(this.textRenderer, "§8[Нет действий при входе]", actionsX + 8, actListY + 8, 0x666666, false);
        } else {
            for (int j = 0; j < currentNode.getEnterActions().size(); j++) {
                DialogueData.Action a = currentNode.getEnterActions().get(j);
                int rowY = actListY + (j * 18) - actionsScrollOffset;
                if (rowY + 16 < actListY || rowY > actListY + actListH) continue;

                int rowW = halfW - 8;
                boolean isRowHover = (mouseX >= actionsX + 4 && mouseX <= actionsX + 4 + rowW &&
                                     mouseY >= rowY && mouseY < rowY + 16 &&
                                     mouseY >= actListY && mouseY <= actListY + actListH);

                context.fill(actionsX + 4, rowY, actionsX + 4 + rowW, rowY + 16, isRowHover ? 0xFF222018 : 0xFF141414);
                context.drawBorder(actionsX + 4, rowY, rowW, 16, isRowHover ? 0xFF4A4028 : 0xFF242424);

                String badge = "[" + formatActionTypeNameShort(a.getType()) + "] ";
                context.drawText(this.textRenderer, badge, actionsX + 8, rowY + 4, 0xE5A842, false);
                int badgeW = textRenderer.getWidth(badge);
                int maxValW = rowW - 22 - badgeW - 8;
                String valClipped = textRenderer.trimToWidth(a.getValue(), maxValW);
                context.drawText(this.textRenderer, valClipped, actionsX + 8 + badgeW, rowY + 4, isRowHover ? 0xFFFFFF : 0xCCCCCC, false);

                // Delete button [X]
                boolean isDelHover = isRowHover && (mouseX >= actionsX + 4 + rowW - 16 && mouseX <= actionsX + 4 + rowW - 4 && mouseY >= rowY + 2 && mouseY <= rowY + 14);
                context.fill(actionsX + 4 + rowW - 16, rowY + 2, actionsX + 4 + rowW - 4, rowY + 14, isDelHover ? 0xFF661111 : 0xFF2E1A1A);
                context.drawText(this.textRenderer, "x", actionsX + 4 + rowW - 12, rowY + 3, isDelHover ? 0xFFFFAA : 0xEE7777, false);
            }
        }
        context.disableScissor();

        // Actions scrollbar
        int totalActionsH = currentNode.getEnterActions().size() * 18;
        int maxActionsScroll = Math.max(0, totalActionsH - actListH);
        if (maxActionsScroll > 0) {
            int sbH = Math.max(10, (int) ((float) actListH / totalActionsH * actListH));
            int sbY = actListY + (int) ((float) actionsScrollOffset / maxActionsScroll * (actListH - sbH));
            int sbX = actionsX + halfW - 4;
            context.fill(sbX, actListY, sbX + 2, actListY + actListH, 0x44222222);
            context.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF666666);
        }

        // Render input text fields
        if (newChoiceTextField != null) newChoiceTextField.render(context, mouseX, mouseY, delta);
        if (newChoiceTargetField != null) newChoiceTargetField.render(context, mouseX, mouseY, delta);
        if (actionValField != null) actionValField.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
