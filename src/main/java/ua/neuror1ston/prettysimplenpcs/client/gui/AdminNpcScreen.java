package ua.neuror1ston.prettysimplenpcs.client.gui;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ua.neuror1ston.prettysimplenpcs.client.gui.widgets.BarksListWidget;
import ua.neuror1ston.prettysimplenpcs.client.gui.widgets.DialogueFileTreeWidget;
import ua.neuror1ston.prettysimplenpcs.client.gui.widgets.MinimalButton;
import ua.neuror1ston.prettysimplenpcs.client.gui.widgets.MinimalSlider;
import ua.neuror1ston.prettysimplenpcs.data.DialogueData;
import ua.neuror1ston.prettysimplenpcs.data.NpcData;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;

import java.util.*;

/**
 * Modern, clean, and strictly clipped Admin & Lore NPC Configuration Panel.
 * Includes hierarchical dialogue file tree explorer, scrollable barks list,
 * smooth scale slider, and seamless transition into DialogueEditorScreen.
 */
public class AdminNpcScreen extends Screen {
    private final int entityId;
    private final NpcData data;
    private final List<String> availableDialogues;
    private final Map<String, DialogueData.Tree> dialogueTrees;
    private final List<String> availableTrades;

    // Tabs: 0 = Identity, 1 = Navigation, 2 = Dialogues, 3 = Trade
    private int activeTab = 0;
    private boolean confirmDelete = false;

    // Geometry
    private int panelX;
    private int panelY;
    private final int panelWidth = 440;
    private final int panelHeight = 265;

    // Identity Widgets
    private TextFieldWidget nameField;
    private TextFieldWidget titleField;
    private TextFieldWidget skinValueField;
    private MinimalSlider scaleSlider;

    // Navigation Widgets
    private TextFieldWidget speedField;
    private TextFieldWidget roamRadiusField;
    private TextFieldWidget waitTicksField;

    // Dialogue Widgets
    private TextFieldWidget newDialoguePathField;
    private DialogueFileTreeWidget fileTreeWidget;
    private BarksListWidget barksListWidget;
    private TextFieldWidget newBarkField;
    private TextFieldWidget barkIntervalField;
    private TextFieldWidget barkRadiusField;

    public AdminNpcScreen(int entityId, NpcData data, List<String> dialogues, Map<String, DialogueData.Tree> trees, List<String> trades) {
        super(Text.literal("NPC Настройки"));
        this.entityId = entityId;
        this.data = data;
        this.availableDialogues = new ArrayList<>(dialogues);
        this.dialogueTrees = new HashMap<>(trees);
        this.availableTrades = new ArrayList<>(trades);
    }

    @Override
    protected void init() {
        this.panelX = (this.width - panelWidth) / 2;
        this.panelY = (this.height - panelHeight) / 2;
        this.clearChildren();

        // 1. Top Tabs
        String[] tabs = {"Внешность", "Навигация", "Диалоги", "Торговля"};
        int tabW = 100;
        int tabGap = 6;
        for (int i = 0; i < tabs.length; i++) {
            final int t = i;
            MinimalButton tabBtn = MinimalButton.builder(Text.literal(tabs[i]), button -> {
                this.activeTab = t;
                this.init();
            }).dimensions(panelX + 10 + (i * (tabW + tabGap)), panelY + 10, tabW, 20).build();
            tabBtn.setActiveState(activeTab == i);
            this.addDrawableChild(tabBtn);
        }

        // 2. Bottom Action Buttons: Delete and Save
        MinimalButton delBtn = MinimalButton.builder(Text.literal(confirmDelete ? "Точно удалить?" : "Удалить NPC"), button -> {
            if (confirmDelete) {
                deleteAndClose();
            } else {
                confirmDelete = true;
                button.setMessage(Text.literal("Подтвердить удаление"));
            }
        }).dimensions(panelX + 12, panelY + panelHeight - 26, 130, 18).build();
        delBtn.setCustomBorderColor(confirmDelete ? 0xFF990000 : 0xFF552222);
        this.addDrawableChild(delBtn);

        MinimalButton saveBtn = MinimalButton.builder(Text.literal("Сохранить настройки"), button -> saveAndClose())
                .dimensions(panelX + panelWidth - 150, panelY + panelHeight - 26, 138, 18).build();
        saveBtn.setCustomBorderColor(0xFF227722);
        this.addDrawableChild(saveBtn);

        // 3. Tab Contents
        int contentY = panelY + 38;

        if (activeTab == 0) {
            initIdentityTab(contentY);
        } else if (activeTab == 1) {
            initNavigationTab(contentY);
        } else if (activeTab == 2) {
            initDialogueTab(contentY);
        } else if (activeTab == 3) {
            initTradeTab(contentY);
        }
    }

    private void initIdentityTab(int y) {
        int leftColX = panelX + 75;
        int rightColX = panelX + 270;

        // Name
        this.nameField = new TextFieldWidget(this.textRenderer, leftColX, y + 8, 140, 18, Text.literal("Имя"));
        this.nameField.setText(data.getName());
        this.addSelectableChild(this.nameField);

        // Title
        this.titleField = new TextFieldWidget(this.textRenderer, leftColX, y + 36, 140, 18, Text.literal("Титул"));
        this.titleField.setText(data.getTitle());
        this.addSelectableChild(this.titleField);

        // Model Steve / Alex
        MinimalButton modelBtn = MinimalButton.builder(Text.literal("Модель: " + data.getSkinType().name()), button -> {
            data.setSkinType(data.getSkinType() == NpcData.SkinType.DEFAULT ? NpcData.SkinType.SLIM : NpcData.SkinType.DEFAULT);
            button.setMessage(Text.literal("Модель: " + data.getSkinType().name()));
        }).dimensions(rightColX, y + 8, 150, 18).build();
        this.addDrawableChild(modelBtn);

        // Skin Source Nick / File
        MinimalButton sourceBtn = MinimalButton.builder(Text.literal("Источник: " + (data.getSkinSource() == NpcData.SkinSource.PLAYER_NICK ? "Никнейм" : "Файл")), button -> {
            data.setSkinSource(data.getSkinSource() == NpcData.SkinSource.PLAYER_NICK ? NpcData.SkinSource.LOCAL_FILE : NpcData.SkinSource.PLAYER_NICK);
            button.setMessage(Text.literal("Источник: " + (data.getSkinSource() == NpcData.SkinSource.PLAYER_NICK ? "Никнейм" : "Файл")));
        }).dimensions(rightColX, y + 36, 150, 18).build();
        this.addDrawableChild(sourceBtn);

        // Skin value field
        this.skinValueField = new TextFieldWidget(this.textRenderer, rightColX, y + 64, 150, 18, Text.literal("Скин"));
        this.skinValueField.setText(data.getSkinValue());
        this.addSelectableChild(this.skinValueField);

        // Scale Slider (0.50x to 1.50x)
        this.scaleSlider = new MinimalSlider(leftColX, y + 102, 345, 18, "Масштаб: ", 0.5f, 1.5f, data.getScale(), val -> {
            data.setScale(val);
        });
        this.addDrawableChild(this.scaleSlider);
    }

    private void initNavigationTab(int y) {
        int leftColX = panelX + 90;
        int rightColX = panelX + 225;

        // Mode
        MinimalButton modeBtn = MinimalButton.builder(Text.literal("Режим: " + data.getAiState().name()), button -> {
            NpcData.AiState next = switch (data.getAiState()) {
                case STATIC -> NpcData.AiState.ROAM_RADIUS;
                case ROAM_RADIUS -> NpcData.AiState.PATROL;
                case PATROL -> NpcData.AiState.STATIC;
            };
            data.setAiState(next);
            button.setMessage(Text.literal("Режим: " + data.getAiState().name()));
        }).dimensions(panelX + 15, y + 8, 195, 18).build();
        this.addDrawableChild(modeBtn);

        this.speedField = new TextFieldWidget(this.textRenderer, leftColX, y + 36, 120, 18, Text.literal("Скорость"));
        this.speedField.setText(String.valueOf(data.getBaseSpeed()));
        this.addSelectableChild(this.speedField);

        this.roamRadiusField = new TextFieldWidget(this.textRenderer, leftColX, y + 64, 120, 18, Text.literal("Радиус"));
        this.roamRadiusField.setText(String.valueOf(data.getRoamRadius()));
        this.addSelectableChild(this.roamRadiusField);

        this.waitTicksField = new TextFieldWidget(this.textRenderer, leftColX, y + 92, 120, 18, Text.literal("Пауза"));
        this.waitTicksField.setText(String.valueOf(data.getWaitTicksPerPoint()));
        this.addSelectableChild(this.waitTicksField);

        // Right column options
        MinimalButton lookBtn = MinimalButton.builder(Text.literal("Смотреть на игроков: " + (data.isLookAtPlayers() ? "ДА" : "НЕТ")), button -> {
            data.setLookAtPlayers(!data.isLookAtPlayers());
            button.setMessage(Text.literal("Смотреть на игроков: " + (data.isLookAtPlayers() ? "ДА" : "НЕТ")));
        }).dimensions(rightColX, y + 8, 195, 18).build();
        this.addDrawableChild(lookBtn);

        MinimalButton loopBtn = MinimalButton.builder(Text.literal("Патруль петля: " + (data.isPatrolLoop() ? "ДА" : "НЕТ")), button -> {
            data.setPatrolLoop(!data.isPatrolLoop());
            button.setMessage(Text.literal("Патруль петля: " + (data.isPatrolLoop() ? "ДА" : "НЕТ")));
        }).dimensions(rightColX, y + 36, 195, 18).build();
        this.addDrawableChild(loopBtn);

        MinimalButton pingPongBtn = MinimalButton.builder(Text.literal("Пинг-понг: " + (data.isPatrolPingPong() ? "ДА" : "НЕТ")), button -> {
            data.setPatrolPingPong(!data.isPatrolPingPong());
            button.setMessage(Text.literal("Пинг-понг: " + (data.isPatrolPingPong() ? "ДА" : "НЕТ")));
        }).dimensions(rightColX, y + 64, 195, 18).build();
        this.addDrawableChild(pingPongBtn);
    }

    private void initDialogueTab(int y) {
        // --- Left Side: Hierarchical Dialogue File Tree ---
        int leftX = panelX + 12;
        int leftW = 205;

        // Path field for new dialogue
        this.newDialoguePathField = new TextFieldWidget(this.textRenderer, leftX, y + 16, leftW - 68, 16, Text.literal("Путь"));
        this.newDialoguePathField.setText("taverns/story");
        this.addSelectableChild(this.newDialoguePathField);

        // Add dialogue button [+]
        MinimalButton addDiagBtn = MinimalButton.builder(Text.literal("+"), button -> {
            String path = newDialoguePathField.getText().trim();
            if (!path.isEmpty() && !dialogueTrees.containsKey(path)) {
                DialogueData.Tree newTree = new DialogueData.Tree(path, path);
                newTree.getNodes().put("start", new DialogueData.Node("start", "Приветствую!"));
                newTree.getNode("start").getChoices().add(new DialogueData.Choice("Мне пора.", "EXIT"));
                dialogueTrees.put(path, newTree);
                if (!availableDialogues.contains(path)) availableDialogues.add(path);
                sendSaveDialogue(newTree);
                this.init();
            }
        }).dimensions(leftX + leftW - 64, y + 16, 26, 16).build();
        this.addDrawableChild(addDiagBtn);

        // Sort toggle button
        MinimalButton sortBtn = MinimalButton.builder(Text.literal("Сорт"), button -> {
            if (fileTreeWidget != null) {
                fileTreeWidget.toggleSort();
            }
        }).dimensions(leftX + leftW - 35, y + 16, 35, 16).build();
        this.addDrawableChild(sortBtn);

        // File tree explorer widget
        this.fileTreeWidget = new DialogueFileTreeWidget(leftX, y + 36, leftW, 116, availableDialogues, data.getDialogueId(),
                selected -> {},
                bound -> {
                    data.setDialogueId(bound);
                });
        this.addDrawableChild(this.fileTreeWidget);

        // Open dedicated Dialogue Editor button
        MinimalButton openEditorBtn = MinimalButton.builder(Text.literal("[РЕДАКТОР ДИАЛОГА]"), button -> {
            String target = fileTreeWidget.getSelectedPath();
            if (target.isEmpty() && !availableDialogues.isEmpty()) target = availableDialogues.get(0);

            DialogueData.Tree tree = dialogueTrees.get(target);
            if (tree == null) {
                tree = new DialogueData.Tree(target, target);
                tree.getNodes().put("start", new DialogueData.Node("start", "Приветствую тебя!"));
                dialogueTrees.put(target, tree);
            }

            if (this.client != null) {
                this.client.setScreen(new DialogueEditorScreen(tree, this));
            }
        }).dimensions(leftX, y + 156, leftW, 18).build();
        openEditorBtn.setCustomBorderColor(0xFF445588);
        this.addDrawableChild(openEditorBtn);

        // --- Right Side: Scrollable Idle Barks List ---
        int rightX = panelX + 225;
        int rightW = 202;

        this.newBarkField = new TextFieldWidget(this.textRenderer, rightX, y + 16, rightW - 32, 16, Text.literal("Фраза"));
        this.addSelectableChild(this.newBarkField);

        MinimalButton addBarkBtn = MinimalButton.builder(Text.literal("+"), button -> {
            String text = newBarkField.getText().trim();
            if (!text.isEmpty()) {
                data.getIdleBarks().add(text);
                newBarkField.setText("");
                this.init();
            }
        }).dimensions(rightX + rightW - 28, y + 16, 28, 16).build();
        this.addDrawableChild(addBarkBtn);

        // Scrollable barks widget
        this.barksListWidget = new BarksListWidget(rightX, y + 36, rightW, 96, data.getIdleBarks(), index -> {
            if (index >= 0 && index < data.getIdleBarks().size()) {
                data.getIdleBarks().remove((int) index);
                this.init();
            }
        });
        this.addDrawableChild(this.barksListWidget);

        // Bark Interval & Radius settings (clearly aligned without label truncation)
        int settingsY = y + 136;
        this.barkIntervalField = new TextFieldWidget(this.textRenderer, rightX + 54, settingsY, 38, 16, Text.literal("Интервал"));
        this.barkIntervalField.setText(String.valueOf(data.getBarkIntervalSeconds()));
        this.addSelectableChild(this.barkIntervalField);

        this.barkRadiusField = new TextFieldWidget(this.textRenderer, rightX + 148, settingsY, 38, 16, Text.literal("Радиус"));
        this.barkRadiusField.setText(String.valueOf(data.getBarkRadius()));
        this.addSelectableChild(this.barkRadiusField);
    }

    private void initTradeTab(int y) {
        int leftX = panelX + 20;

        String curTrade = data.getTradeMatrixId().isEmpty() ? "НЕТ" : data.getTradeMatrixId();
        MinimalButton matrixBtn = MinimalButton.builder(Text.literal("Лавка торговли: " + curTrade), button -> {
            if (availableTrades.isEmpty()) return;
            int idx = availableTrades.indexOf(data.getTradeMatrixId());
            int nextIdx = (idx + 1) % (availableTrades.size() + 1);
            String nextId = nextIdx == availableTrades.size() ? "" : availableTrades.get(nextIdx);
            data.setTradeMatrixId(nextId);
            button.setMessage(Text.literal("Лавка торговли: " + (nextId.isEmpty() ? "НЕТ" : nextId)));
        }).dimensions(leftX, y + 10, 240, 20).build();
        this.addDrawableChild(matrixBtn);
    }

    private void sendSaveDialogue(DialogueData.Tree tree) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(NpcData.GSON.toJson(tree));
        ClientPlayNetworking.send(NpcNetwork.SAVE_DIALOGUE_C2S, buf);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Deep Black Minimalist Background
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0A0A0A);
        // Subtle grey outer frame
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF383838);

        int contentY = panelY + 38;

        if (activeTab == 0) {
            context.drawText(this.textRenderer, "Имя:", panelX + 20, contentY + 13, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "Титул:", panelX + 20, contentY + 41, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "Скин/Ник:", panelX + 215, contentY + 69, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "Размер:", panelX + 20, contentY + 107, 0xFFFFFF, false);

            if (nameField != null) nameField.render(context, mouseX, mouseY, delta);
            if (titleField != null) titleField.render(context, mouseX, mouseY, delta);
            if (skinValueField != null) skinValueField.render(context, mouseX, mouseY, delta);
        } else if (activeTab == 1) {
            context.drawText(this.textRenderer, "Скорость:", panelX + 15, contentY + 41, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "Радиус (б):", panelX + 15, contentY + 69, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "Пауза (тик):", panelX + 15, contentY + 97, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "§7Точек патруля: " + data.getPatrolPoints().size(), panelX + 225, contentY + 97, 0xAAAAAA, false);

            if (speedField != null) speedField.render(context, mouseX, mouseY, delta);
            if (roamRadiusField != null) roamRadiusField.render(context, mouseX, mouseY, delta);
            if (waitTicksField != null) waitTicksField.render(context, mouseX, mouseY, delta);
        } else if (activeTab == 2) {
            int leftX = panelX + 12;
            int rightX = panelX + 225;

            // Section titles (No emojis)
            context.drawText(this.textRenderer, "Дерево диалогов:", leftX, contentY + 4, 0xCCCCCC, false);
            context.drawText(this.textRenderer, "Реплики над головой:", rightX, contentY + 4, 0xCCCCCC, false);

            if (newDialoguePathField != null) newDialoguePathField.render(context, mouseX, mouseY, delta);
            if (newBarkField != null) newBarkField.render(context, mouseX, mouseY, delta);

            int settingsY = contentY + 136;
            context.drawText(this.textRenderer, "Интервал:", rightX, settingsY + 4, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "Радиус:", rightX + 98, settingsY + 4, 0xFFFFFF, false);

            if (barkIntervalField != null) barkIntervalField.render(context, mouseX, mouseY, delta);
            if (barkRadiusField != null) barkRadiusField.render(context, mouseX, mouseY, delta);

            // Active bound dialogue badge rendered safely inside dialogue tab area (NOT over bottom buttons!)
            String boundId = data.getDialogueId().isEmpty() ? "НЕТ" : data.getDialogueId();
            String boundLabel = this.textRenderer.trimToWidth("Привязан: " + boundId, 200);
            context.drawText(this.textRenderer, "§8" + boundLabel, leftX, contentY + 178, 0x888888, false);
        } else if (activeTab == 3) {
            context.drawText(this.textRenderer, "Экономическая матрица торговли:", panelX + 20, contentY + 45, 0xFFFFFF, false);
            context.drawText(this.textRenderer, "§710 меди = 1 серебро, 10 серебра = 1 золото", panelX + 20, contentY + 65, 0x888888, false);
            context.drawText(this.textRenderer, "§7Валюта: Железные и золотые самородки", panelX + 20, contentY + 80, 0x666666, false);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void saveAndClose() {
        if (nameField != null) data.setName(nameField.getText());
        if (titleField != null) data.setTitle(titleField.getText());
        if (skinValueField != null) data.setSkinValue(skinValueField.getText());
        if (scaleSlider != null) data.setScale(scaleSlider.getActualValue());

        if (speedField != null) {
            try { data.setBaseSpeed(Float.parseFloat(speedField.getText())); } catch (Exception ignored) {}
        }
        if (roamRadiusField != null) {
            try { data.setRoamRadius(Double.parseDouble(roamRadiusField.getText())); } catch (Exception ignored) {}
        }
        if (waitTicksField != null) {
            try { data.setWaitTicksPerPoint(Integer.parseInt(waitTicksField.getText())); } catch (Exception ignored) {}
        }
        if (barkIntervalField != null) {
            try { data.setBarkIntervalSeconds(Integer.parseInt(barkIntervalField.getText())); } catch (Exception ignored) {}
        }
        if (barkRadiusField != null) {
            try { data.setBarkRadius(Double.parseDouble(barkRadiusField.getText())); } catch (Exception ignored) {}
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(NpcData.GSON.toJson(data));
        ClientPlayNetworking.send(NpcNetwork.UPDATE_NPC_C2S, buf);

        this.close();
    }

    private void deleteAndClose() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeString(data.getId());
        ClientPlayNetworking.send(NpcNetwork.DELETE_NPC_C2S, buf);
        this.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
