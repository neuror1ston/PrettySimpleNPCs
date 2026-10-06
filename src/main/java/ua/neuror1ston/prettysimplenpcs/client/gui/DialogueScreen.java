package ua.neuror1ston.prettysimplenpcs.client.gui;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * CRPG Rogue Trader-style Dialogue Screen featuring:
 * - Unified seamless dialogue container across the bottom with vertical divider
 * - Scaled, crisp typography (TEXT_SCALE = 0.84f) for authentic CRPG aesthetic
 * - Full scrollable conversation transcript (earlier NPC lines + player responses)
 * - Borderless response choices with multi-line wrapping and luminous hover glow
 * - 3D models of NPC (left) and Player (right) with authentic character nameplates
 * - Mouse wheel scrolling and numeric key shortcuts (1..9)
 */
public class DialogueScreen extends Screen {
    public static final float TEXT_SCALE = 0.84f;
    public static final int LINE_ADVANCE = 10;

    // Visual Palette (Aged Bronze & Medieval CRPG Slate)
    public static final int NPC_COLOR = 0xFFBA42;          // Warm antique gold
    public static final int PLAYER_COLOR = 0x7BDC7B;       // Sage emerald
    public static final int TEXT_COLOR = 0xD6D1C7;         // Parchment silver-white

    public static final int CHOICE_UNHOVERED_NUM = 0x558855;   // Muted calm green
    public static final int CHOICE_UNHOVERED_TEXT = 0x8DA88D;  // Muted calm sage
    public static final int CHOICE_HOVERED_NUM = 0x77FF77;     // Radiant phosphor green
    public static final int CHOICE_HOVERED_TEXT = 0xF2FFF2;    // Radiant white-emerald

    private final int entityId;
    private final String dialogueId;
    private final String dialogueTitle;
    private final String npcName;

    private String fullText;
    private final List<ChoiceEntry> choices = new ArrayList<>();
    private final List<HistoryEntry> conversationHistory = new ArrayList<>();

    // Typewriter state
    private int visibleChars = 0;
    private boolean typewriterFinished = false;
    private boolean awaitingResponse = false;

    // Scrolling states
    private int historyScrollOffset = 0;
    private boolean userScrolledHistory = false;
    private int choicesScrollOffset = 0;

    // Unified Geometry
    private int containerX;
    private int containerY;
    private int containerW;
    private int containerH;
    private int leftPaneW;
    private int dividerX;
    private int rightPaneW;
    private int sideMargin;

    // Cached layout lines
    private final List<CachedHistoryItem> cachedHistory = new ArrayList<>();
    private final List<CachedChoice> cachedChoices = new ArrayList<>();

    public record ChoiceEntry(String text, String targetNodeId) {}

    public static class HistoryEntry {
        public final String speakerName;
        public final int speakerColor;
        public final String text;
        public final boolean isPlayer;

        public HistoryEntry(String speakerName, int speakerColor, String text, boolean isPlayer) {
            this.speakerName = speakerName;
            this.speakerColor = speakerColor;
            this.text = text;
            this.isPlayer = isPlayer;
        }
    }

    private static class CachedHistoryItem {
        final List<OrderedText> lines;
        final int height;

        CachedHistoryItem(List<OrderedText> lines, int height) {
            this.lines = lines;
            this.height = height;
        }
    }

    private static class CachedChoice {
        final int index;
        final ChoiceEntry entry;
        final List<OrderedText> unhoveredLines;
        final List<OrderedText> hoveredLines;
        final int height;

        CachedChoice(int index, ChoiceEntry entry, List<OrderedText> unhoveredLines, List<OrderedText> hoveredLines, int height) {
            this.index = index;
            this.entry = entry;
            this.unhoveredLines = unhoveredLines;
            this.hoveredLines = hoveredLines;
            this.height = height;
        }
    }

    public DialogueScreen(int entityId, String dialogueId, String dialogueTitle, String npcName, String startText, List<ChoiceEntry> choices) {
        super(Text.literal(dialogueTitle));
        this.entityId = entityId;
        this.dialogueId = dialogueId;
        this.dialogueTitle = dialogueTitle;
        this.npcName = npcName;
        this.fullText = startText;
        this.choices.addAll(choices);
        this.conversationHistory.add(new HistoryEntry(npcName, NPC_COLOR, startText, false));
    }

    public void updateNode(String newText, List<ChoiceEntry> newChoices) {
        this.fullText = newText;
        this.choices.clear();
        this.choices.addAll(newChoices);
        this.visibleChars = 0;
        this.typewriterFinished = false;
        this.awaitingResponse = false;
        this.choicesScrollOffset = 0;
        this.conversationHistory.add(new HistoryEntry(this.npcName, NPC_COLOR, newText, false));
        this.userScrolledHistory = false;
        this.rebuildCachedChoices();
        this.updateHistoryCache();
    }

    @Override
    protected void init() {
        this.clearChildren();

        // Proportional layout across the bottom:
        // Left & Right: Character model columns
        // Center: Unified dialogue frame spanning seamlessly
        this.sideMargin = Math.min(85, Math.max(62, (int) (this.width * 0.14f)));

        this.containerX = sideMargin + 4;
        this.containerW = this.width - (sideMargin * 2) - 8;
        this.containerH = Math.min(145, Math.max(92, (int) (this.height * 0.38f)));
        this.containerY = this.height - containerH - 12;

        // Split into Left Pane (History transcript) and Right Pane (Choices)
        this.leftPaneW = (int) (containerW * 0.58f);
        this.dividerX = containerX + leftPaneW;
        this.rightPaneW = containerW - leftPaneW;

        this.rebuildCachedChoices();
        this.updateHistoryCache();
    }

    private void rebuildCachedChoices() {
        this.cachedChoices.clear();
        int choicePixelW = rightPaneW - 20;
        int choiceWrapW = Math.max(80, (int) (choicePixelW / TEXT_SCALE));

        for (int i = 0; i < choices.size(); i++) {
            ChoiceEntry choice = choices.get(i);
            String prefix = (i + 1) + ". ";
            String rawText = choice.text();
            String formattedText = (rawText.startsWith("[") || rawText.startsWith("\"")) ? rawText : "\"" + rawText + "\"";

            // Unhovered version
            Text unhoveredText = Text.empty()
                .append(Text.literal(prefix).styled(s -> s.withColor(CHOICE_UNHOVERED_NUM)))
                .append(Text.literal(formattedText).styled(s -> s.withColor(CHOICE_UNHOVERED_TEXT)));
            List<OrderedText> unhoveredLines = this.textRenderer.wrapLines(unhoveredText, choiceWrapW);

            // Hovered version
            Text hoveredText = Text.empty()
                .append(Text.literal(prefix).styled(s -> s.withColor(CHOICE_HOVERED_NUM)))
                .append(Text.literal(formattedText).styled(s -> s.withColor(CHOICE_HOVERED_TEXT)));
            List<OrderedText> hoveredLines = this.textRenderer.wrapLines(hoveredText, choiceWrapW);

            int h = Math.max(unhoveredLines.size(), hoveredLines.size()) * LINE_ADVANCE + 4;
            cachedChoices.add(new CachedChoice(i, choice, unhoveredLines, hoveredLines, h));
        }
    }

    private void updateHistoryCache() {
        this.cachedHistory.clear();
        int historyPixelW = leftPaneW - 20;
        int historyWrapW = Math.max(80, (int) (historyPixelW / TEXT_SCALE));

        for (int i = 0; i < conversationHistory.size(); i++) {
            HistoryEntry entry = conversationHistory.get(i);
            boolean isLast = (i == conversationHistory.size() - 1);
            int maxChars = (isLast && !typewriterFinished) ? visibleChars : -1;
            cachedHistory.add(wrapHistoryEntry(entry, historyWrapW, maxChars));
        }

        if (!userScrolledHistory) {
            this.historyScrollOffset = Math.max(0, getCachedHistoryTotalHeight() - (containerH - 16));
        }
    }

    private CachedHistoryItem wrapHistoryEntry(HistoryEntry entry, int wrapWidth, int maxChars) {
        String content = entry.text;
        if (maxChars >= 0 && maxChars < content.length()) {
            content = content.substring(0, maxChars);
        }
        Text formatted = Text.empty()
            .append(Text.literal(entry.speakerName + ": ").styled(s -> s.withColor(entry.speakerColor)))
            .append(Text.literal("\"" + content + "\"").styled(s -> s.withColor(TEXT_COLOR)));
        List<OrderedText> lines = this.textRenderer.wrapLines(formatted, wrapWidth);
        int h = lines.size() * LINE_ADVANCE + 6;
        return new CachedHistoryItem(lines, h);
    }

    private int getCachedHistoryTotalHeight() {
        int total = 0;
        for (CachedHistoryItem item : cachedHistory) {
            total += item.height;
        }
        return total;
    }

    private void selectChoice(int index) {
        if (awaitingResponse || index < 0 || index >= choices.size()) return;
        ChoiceEntry choice = choices.get(index);

        if (this.client != null) {
            this.client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f)
            );
        }

        String pName = (client != null && client.player != null) ? client.player.getName().getString() : "Игрок";
        this.conversationHistory.add(new HistoryEntry(pName, PLAYER_COLOR, choice.text(), true));
        this.userScrolledHistory = false;
        this.updateHistoryCache();

        this.awaitingResponse = true;
        sendChoice(choice.targetNodeId(), index);
    }

    private void sendChoice(String targetNodeId, int choiceIndex) {
        if ("EXIT".equalsIgnoreCase(targetNodeId) || targetNodeId.isEmpty()) {
            this.close();
            return;
        }

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeString(dialogueId);
        buf.writeString(targetNodeId);
        buf.writeInt(choiceIndex);
        ClientPlayNetworking.send(NpcNetwork.DIALOGUE_CHOICE_C2S, buf);
    }

    @Override
    public void tick() {
        super.tick();
        if (!typewriterFinished) {
            visibleChars = Math.min(fullText.length(), visibleChars + 2);
            if (visibleChars >= fullText.length()) {
                typewriterFinished = true;
            }
            updateHistoryCache();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int hoveredChoice = getHoveredChoiceIndex(mouseX, mouseY);
            if (!typewriterFinished) {
                // Clicking directly on a choice selects it immediately
                if (hoveredChoice >= 0) {
                    visibleChars = fullText.length();
                    typewriterFinished = true;
                    updateHistoryCache();
                    selectChoice(hoveredChoice);
                    return true;
                }
                // Clicking anywhere else skips the typewriter text effect
                visibleChars = fullText.length();
                typewriterFinished = true;
                updateHistoryCache();
                return true;
            } else {
                if (hoveredChoice >= 0) {
                    selectChoice(hoveredChoice);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getHoveredChoiceIndex(double mouseX, double mouseY) {
        if (mouseX < dividerX + 4 || mouseX > containerX + containerW - 6 ||
            mouseY < containerY + 4 || mouseY > containerY + containerH - 4) {
            return -1;
        }

        int currentY = containerY + 8 - choicesScrollOffset;
        for (CachedChoice cc : cachedChoices) {
            int choiceH = cc.height;
            if (mouseY >= currentY && mouseY < currentY + choiceH) {
                return cc.index;
            }
            currentY += choiceH + 4;
        }
        return -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < choices.size()) {
                if (!typewriterFinished) {
                    visibleChars = fullText.length();
                    typewriterFinished = true;
                    updateHistoryCache();
                }
                selectChoice(index);
                return true;
            }
        }
        if (!typewriterFinished && (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER)) {
            visibleChars = fullText.length();
            typewriterFinished = true;
            updateHistoryCache();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // Left pane: Narrative transcript scroll
        if (mouseX >= containerX && mouseX < dividerX && mouseY >= containerY && mouseY <= containerY + containerH) {
            int totalH = getCachedHistoryTotalHeight();
            int maxHistoryScroll = Math.max(0, totalH - (containerH - 16));
            if (maxHistoryScroll > 0) {
                historyScrollOffset = Math.max(0, Math.min(maxHistoryScroll, historyScrollOffset - (int) (amount * 16)));
                userScrolledHistory = (historyScrollOffset < maxHistoryScroll);
                return true;
            }
        }
        // Right pane: Response choices scroll
        else if (mouseX >= dividerX && mouseX <= containerX + containerW && mouseY >= containerY && mouseY <= containerY + containerH) {
            int totalH = 0;
            for (CachedChoice cc : cachedChoices) {
                totalH += cc.height + 4;
            }
            int maxChoicesScroll = Math.max(0, totalH - (containerH - 16));
            if (maxChoicesScroll > 0) {
                choicesScrollOffset = Math.max(0, Math.min(maxChoicesScroll, choicesScrollOffset - (int) (amount * 16)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Dark atmospheric vignette overlay
        context.fillGradient(0, 0, this.width, this.height, 0xF2080808, 0xFC040404);

        // 2. Render 3D NPC entity on the LEFT (Facing mouse/dialogue)
        int npcCenterX = sideMargin / 2;
        if (this.client != null && this.client.world != null) {
            net.minecraft.entity.Entity e = this.client.world.getEntityById(entityId);
            if (e instanceof LivingEntity livingNpc) {
                int npcY = this.height - 24;
                float lookX = (float) (npcCenterX - mouseX);
                float lookY = (float) (npcY - 45 - mouseY);
                InventoryScreen.drawEntity(context, npcCenterX, npcY, 44, lookX, lookY, livingNpc);
            }
        }
        drawNameplate(context, npcName, npcCenterX, this.height - 18, NPC_COLOR);

        // 3. Render 3D Player entity on the RIGHT (Facing mouse/dialogue)
        int playerCenterX = this.width - (sideMargin / 2);
        String playerName = (this.client != null && this.client.player != null) ? this.client.player.getName().getString() : "Игрок";
        if (this.client != null && this.client.player != null) {
            int playerY = this.height - 24;
            float lookX = (float) (playerCenterX - mouseX);
            float lookY = (float) (playerY - 45 - mouseY);
            InventoryScreen.drawEntity(context, playerCenterX, playerY, 44, lookX, lookY, this.client.player);
        }
        drawNameplate(context, playerName, playerCenterX, this.height - 18, PLAYER_COLOR);

        // 4. Unified Central Dialogue Container (CRPG Rogue Trader Layout)
        // Outer container background
        context.fill(containerX, containerY, containerX + containerW, containerY + containerH, 0xF00D0E10);
        context.drawBorder(containerX, containerY, containerW, containerH, 0xFF3D372E);
        context.drawBorder(containerX + 1, containerY + 1, containerW - 2, containerH - 2, 0xFF1C1A17);

        // Left pane: Narrative transcript slate
        context.fill(containerX + 2, containerY + 2, dividerX, containerY + containerH - 2, 0xF2121110);

        // Vertical Divider (No gap between panels, seamless bronze recessed seam)
        context.fill(dividerX, containerY + 2, dividerX + 1, containerY + containerH - 2, 0xFF3D372E);
        context.fill(dividerX + 1, containerY + 2, dividerX + 2, containerY + containerH - 2, 0xFF141311);

        // Right pane: Player choices slate
        context.fill(dividerX + 2, containerY + 2, containerX + containerW - 2, containerY + containerH - 2, 0xF20B0D0B);

        // 5. Render Left Pane: Scrollable Narrative Transcript
        int totalHistoryH = getCachedHistoryTotalHeight();
        int maxHistoryScroll = Math.max(0, totalHistoryH - (containerH - 16));

        context.enableScissor(containerX + 4, containerY + 4, dividerX - 2, containerY + containerH - 4);
        int currentHistoryY = containerY + 8 - historyScrollOffset;

        for (CachedHistoryItem item : cachedHistory) {
            int itemH = item.height;
            if (currentHistoryY + itemH >= containerY + 4 && currentHistoryY <= containerY + containerH - 4) {
                for (int lineIdx = 0; lineIdx < item.lines.size(); lineIdx++) {
                    int lineY = currentHistoryY + (lineIdx * LINE_ADVANCE);
                    drawScaledOrderedText(context, item.lines.get(lineIdx), containerX + 8, lineY, 0xFFFFFF, false);
                }
            }
            currentHistoryY += itemH;
        }
        context.disableScissor();

        // Left pane scrollbar
        if (maxHistoryScroll > 0) {
            int visibleH = containerH - 16;
            int sbH = Math.max(14, (int) ((float) visibleH / totalHistoryH * visibleH));
            int sbY = containerY + 8 + (int) ((float) historyScrollOffset / maxHistoryScroll * (visibleH - sbH));
            int sbX = dividerX - 4;
            context.fill(sbX, containerY + 8, sbX + 2, containerY + 8 + visibleH, 0x44201D18);
            context.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF8A775D);
        }

        // 6. Render Right Pane: Borderless Response Choices with luminous hover
        int totalChoicesH = 0;
        for (CachedChoice cc : cachedChoices) {
            totalChoicesH += cc.height + 4;
        }
        int maxChoicesScroll = Math.max(0, totalChoicesH - (containerH - 16));

        context.enableScissor(dividerX + 2, containerY + 4, containerX + containerW - 4, containerY + containerH - 4);
        int currentChoiceY = containerY + 8 - choicesScrollOffset;

        for (CachedChoice cc : cachedChoices) {
            int choiceH = cc.height;
            int choiceY = currentChoiceY;
            currentChoiceY += choiceH + 4;

            boolean isHovered = (mouseX >= dividerX + 4 && mouseX <= containerX + containerW - 6 &&
                                 mouseY >= choiceY && mouseY < choiceY + choiceH &&
                                 mouseY >= containerY + 4 && mouseY <= containerY + containerH - 4);

            if (choiceY + choiceH < containerY + 4 || choiceY > containerY + containerH - 4) {
                continue;
            }

            if (isHovered) {
                // Subtle emerald phosphor highlight
                context.fill(dividerX + 4, choiceY - 1, containerX + containerW - 6, choiceY + choiceH - 1, 0x22356B35);
                // Subtle left accent bar
                context.fill(dividerX + 4, choiceY - 1, dividerX + 6, choiceY + choiceH - 1, 0xAA77FF77);
            }

            List<OrderedText> linesToDraw = isHovered ? cc.hoveredLines : cc.unhoveredLines;
            for (int lineIdx = 0; lineIdx < linesToDraw.size(); lineIdx++) {
                int lineY = choiceY + 2 + (lineIdx * LINE_ADVANCE);
                drawScaledOrderedText(context, linesToDraw.get(lineIdx), dividerX + 10, lineY, 0xFFFFFF, false);
            }
        }
        context.disableScissor();

        // Right pane scrollbar
        if (maxChoicesScroll > 0) {
            int visibleH = containerH - 16;
            int sbH = Math.max(14, (int) ((float) visibleH / totalChoicesH * visibleH));
            int sbY = containerY + 8 + (int) ((float) choicesScrollOffset / maxChoicesScroll * (visibleH - sbH));
            int sbX = containerX + containerW - 5;
            context.fill(sbX, containerY + 8, sbX + 2, containerY + 8 + visibleH, 0x44182418);
            context.fill(sbX, sbY, sbX + 2, sbY + sbH, 0xFF4E7A52);
        }
    }

    private void drawNameplate(DrawContext context, String name, int centerX, int y, int textColor) {
        int nameW = this.textRenderer.getWidth(name);
        int plateW = nameW + 14;
        int plateH = 13;
        int plateX = centerX - plateW / 2;

        context.fill(plateX, y, plateX + plateW, y + plateH, 0xEE121110);
        context.drawBorder(plateX, y, plateW, plateH, 0xFF3D372E);
        context.drawText(this.textRenderer, name, centerX - nameW / 2, y + 3, textColor, false);
    }

    private void drawScaledOrderedText(DrawContext context, OrderedText text, int x, int y, int color, boolean shadow) {
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        context.drawText(this.textRenderer, text, 0, 0, color, shadow);
        context.getMatrices().pop();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
