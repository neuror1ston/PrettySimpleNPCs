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
 *    - Instant formatting toolbar (Bold, Italic, Placeholders, Colors).
 *    - Choices & Actions manager for the node.
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
        }).dimensions(110, 6, 110, 18).build();
        graphTabBtn.setActiveState(activeMode == 0);
        this.addDrawableChild(graphTabBtn);

        MinimalButton canvasTabBtn = MinimalButton.builder(Text.literal("ПОЛОТНО РЕПЛИКИ"), button -> {
            this.activeMode = 1;
            this.init();
        }).dimensions(225, 6, 125, 18).build();
        canvasTabBtn.setActiveState(activeMode == 1);
        this.addDrawableChild(canvasTabBtn);

        // Save Button
        MinimalButton saveBtn = MinimalButton.builder(Text.literal("Сохранить"), button -> {
            saveDialogueToServer();
            savedNotification = true;
        }).dimensions(this.width - 105, 6, 95, 18).build();
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

        int canvasX = 20;
        int canvasW = this.width - 40;

        // Toolbar row 1: Formatting and placeholders
        int tbY = 30;
        this.addDrawableChild(MinimalButton.builder(Text.literal("Жирный"), b -> insertFormat("§l"))
                .dimensions(canvasX, tbY, 48, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("Курсив"), b -> insertFormat("§o"))
                .dimensions(canvasX + 52, tbY, 48, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("%player%"), b -> insertFormat("%player%"))
                .dimensions(canvasX + 104, tbY, 60, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("%npc%"), b -> insertFormat("%npc_name%"))
                .dimensions(canvasX + 168, tbY, 48, 16).build());

        // Toolbar row 2: Color swatches
        this.addDrawableChild(MinimalButton.builder(Text.literal("§6ЗОЛ"), b -> insertFormat("§6"))
                .dimensions(canvasX + 224, tbY, 28, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("§eЖЕЛ"), b -> insertFormat("§e"))
                .dimensions(canvasX + 254, tbY, 28, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("§aЗЕЛ"), b -> insertFormat("§a"))
                .dimensions(canvasX + 284, tbY, 28, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("§cКРА"), b -> insertFormat("§c"))
                .dimensions(canvasX + 314, tbY, 28, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("§7СЕР"), b -> insertFormat("§7"))
                .dimensions(canvasX + 344, tbY, 28, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("§fБЕЛ"), b -> insertFormat("§f"))
                .dimensions(canvasX + 374, tbY, 28, 16).build());
        this.addDrawableChild(MinimalButton.builder(Text.literal("§cСБР"), b -> insertFormat("§r"))
                .dimensions(canvasX + 404, tbY, 28, 16).build());

        // Node Quick Switcher
        MinimalButton nodeSwitchBtn = MinimalButton.builder(Text.literal("Узел: " + selectedNodeId), button -> {
            List<String> keys = new ArrayList<>(tree.getNodes().keySet());
            int idx = keys.indexOf(selectedNodeId);
            selectedNodeId = keys.get((idx + 1) % keys.size());
            this.init();
        }).dimensions(this.width - 150, tbY, 130, 16).build();
        this.addDrawableChild(nodeSwitchBtn);

        // GIANT Multi-line Writing Canvas
        int canvasY = tbY + 20;
        int bottomSectionH = 110;
        int canvasH = Math.max(60, this.height - canvasY - bottomSectionH - 12);

        this.canvasEditBox = new EditBoxWidget(this.textRenderer, canvasX, canvasY, canvasW, canvasH,
                Text.literal("Введите реплику персонажа..."), Text.literal("Реплика"));
        this.canvasEditBox.setMaxLength(2048);
        this.canvasEditBox.setText(currentNode.getText());
        this.canvasEditBox.setChangeListener(currentNode::setText);
        this.addSelectableChild(this.canvasEditBox);

        // Bottom Section: Choices (Left) and Actions (Right)
        int bottomY = canvasY + canvasH + 8;
        int halfW = (canvasW - 14) / 2;

        // Left: Choices List & Add Choice
        initChoicesSection(canvasX, bottomY, halfW, currentNode);

        // Right: Actions List & Add Action
        initActionsSection(canvasX + halfW + 14, bottomY, halfW, currentNode);
    }

    private void initChoicesSection(int x, int y, int w, DialogueData.Node currentNode) {
        int listY = y + 14;
        int maxShown = Math.min(2, currentNode.getChoices().size());

        for (int i = 0; i < maxShown; i++) {
            DialogueData.Choice c = currentNode.getChoices().get(i);
            final int idx = i;
            int rowY = listY + (i * 20);

            // Choice summary preview button
            String choiceLabel = (i + 1) + ". " + c.getText() + " -> [" + c.getTargetNodeId() + "]";
            MinimalButton prevBtn = MinimalButton.builder(Text.literal(choiceLabel), b -> {})
                    .dimensions(x, rowY, w - 24, 18).build();
            this.addDrawableChild(prevBtn);

            // Delete choice [X]
            MinimalButton delBtn = MinimalButton.builder(Text.literal("X"), b -> {
                if (idx < currentNode.getChoices().size()) {
                    currentNode.getChoices().remove(idx);
                    this.init();
                }
            }).dimensions(x + w - 20, rowY, 18, 18).build();
            delBtn.setCustomBorderColor(0xFF662222);
            this.addDrawableChild(delBtn);
        }

        int addY = y + 56;
        this.newChoiceTextField = new TextFieldWidget(this.textRenderer, x, addY, w - 74, 16, Text.literal("Текст"));
        this.newChoiceTextField.setText("Далее");
        this.addSelectableChild(this.newChoiceTextField);

        this.newChoiceTargetField = new TextFieldWidget(this.textRenderer, x + w - 70, addY, 70, 16, Text.literal("Куда"));
        this.newChoiceTargetField.setText("EXIT");
        this.addSelectableChild(this.newChoiceTargetField);

        MinimalButton addChoiceBtn = MinimalButton.builder(Text.literal("+ Добавить ответ"), b -> {
            String ct = newChoiceTextField.getText().trim();
            String tg = newChoiceTargetField.getText().trim();
            if (!ct.isEmpty()) {
                currentNode.getChoices().add(new DialogueData.Choice(ct, tg.isEmpty() ? "EXIT" : tg));
                newChoiceTextField.setText("Ответ");
                this.init();
            }
        }).dimensions(x, addY + 20, w, 16).build();
        this.addDrawableChild(addChoiceBtn);
    }

    private void initActionsSection(int x, int y, int w, DialogueData.Node currentNode) {
        MinimalButton actTypeBtn = MinimalButton.builder(Text.literal(formatActionTypeName(selectedActionType)), b -> {
            DialogueData.Action.ActionType[] types = DialogueData.Action.ActionType.values();
            int next = (selectedActionType.ordinal() + 1) % types.length;
            selectedActionType = types[next];
            b.setMessage(Text.literal(formatActionTypeName(selectedActionType)));
        }).dimensions(x, y + 14, w, 16).build();
        this.addDrawableChild(actTypeBtn);

        this.actionValField = new TextFieldWidget(this.textRenderer, x, y + 34, w - 28, 16, Text.literal("Значение"));
        this.actionValField.setText("playsound ...");
        this.addSelectableChild(this.actionValField);

        MinimalButton addActionBtn = MinimalButton.builder(Text.literal("+"), b -> {
            String val = actionValField.getText().trim();
            if (!val.isEmpty()) {
                currentNode.getEnterActions().add(new DialogueData.Action(selectedActionType, val));
                this.init();
            }
        }).dimensions(x + w - 24, y + 34, 24, 16).build();
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

    private String formatActionTypeName(DialogueData.Action.ActionType type) {
        return switch (type) {
            case CONSOLE_COMMAND -> "Команда консоли";
            case PLAYER_COMMAND -> "Команда игрока";
            case GIVE_ITEM -> "Выдать предмет";
            case TAKE_ITEM -> "Забрать предмет";
            case PLAY_SOUND -> "Звуковой эффект";
            case SET_FLAG -> "Записать флаг";
            case REMOVE_FLAG -> "Снять флаг";
            case OPEN_TRADE -> "Открыть торговлю";
        };
    }

    private void saveDialogueToServer() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(NpcData.GSON.toJson(tree));
        ClientPlayNetworking.send(NpcNetwork.SAVE_DIALOGUE_C2S, buf);
    }

    // --- Interactive Mouse Handlers for Graph Map ---

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
                int cardH = Math.max(68, 44 + Math.min(3, node.getChoices().size()) * 12);

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
        }

        return super.mouseClicked(mouseX, mouseY, button);
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

            for (int i = 0; i < Math.min(3, node.getChoices().size()); i++) {
                DialogueData.Choice c = node.getChoices().get(i);
                int fromY = panY + np.y + 36 + (i * 12);

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
            int cardH = Math.max(68, 44 + Math.min(3, node.getChoices().size()) * 12);
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

            // Outgoing choices
            for (int i = 0; i < Math.min(3, node.getChoices().size()); i++) {
                DialogueData.Choice c = node.getChoices().get(i);
                int cy = sy + 32 + (i * 12);
                String choiceSummary = (i + 1) + ". " + c.getText();
                String clipped = textRenderer.trimToWidth(choiceSummary, cardW - 45);
                context.drawText(this.textRenderer, "§7" + clipped, sx + 6, cy, 0xAAAAAA, false);
                context.drawText(this.textRenderer, "§b->" + c.getTargetNodeId(), sx + cardW - 38, cy, 0x66CCEE, false);
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

        int canvasX = 20;
        int canvasW = this.width - 40;
        int bottomSectionH = 110;
        int canvasH = Math.max(60, this.height - 50 - bottomSectionH - 12);
        int bottomY = 50 + canvasH + 8;
        int halfW = (canvasW - 14) / 2;

        // Render Canvas EditBox
        if (canvasEditBox != null) {
            canvasEditBox.render(context, mouseX, mouseY, delta);
        }

        // Section Dividers for bottom choices and actions
        context.fill(canvasX, bottomY, canvasX + halfW, bottomY + bottomSectionH, 0xFF0E0E0E);
        context.drawBorder(canvasX, bottomY, halfW, bottomSectionH, 0xFF282828);
        context.drawText(this.textRenderer, "§7Варианты ответа:", canvasX + 8, bottomY + 4, 0xCCCCCC, false);

        int rightSectionX = canvasX + halfW + 14;
        context.fill(rightSectionX, bottomY, rightSectionX + halfW, bottomY + bottomSectionH, 0xFF0E0E0E);
        context.drawBorder(rightSectionX, bottomY, halfW, bottomSectionH, 0xFF282828);
        context.drawText(this.textRenderer, "§7Действия при входе (" + currentNode.getEnterActions().size() + "):", rightSectionX + 8, bottomY + 4, 0xCCCCCC, false);

        if (newChoiceTextField != null) newChoiceTextField.render(context, mouseX, mouseY, 0);
        if (newChoiceTargetField != null) newChoiceTargetField.render(context, mouseX, mouseY, 0);
        if (actionValField != null) actionValField.render(context, mouseX, mouseY, 0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
