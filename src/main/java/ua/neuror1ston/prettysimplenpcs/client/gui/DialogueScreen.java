package ua.neuror1ston.prettysimplenpcs.client.gui;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ua.neuror1ston.prettysimplenpcs.client.gui.widgets.MinimalButton;
import ua.neuror1ston.prettysimplenpcs.entity.SimpleNpcEntity;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Immersive cinematic Dialogue Screen featuring:
 * - 3D player on the left and 3D NPC on the right (facing cursor)
 * - Typewriter text effect with click-to-skip
 * - Smooth fade-in response choices
 * - Scrollable conversation history
 */
public class DialogueScreen extends Screen {
    private final int entityId;
    private final String dialogueId;
    private final String dialogueTitle;
    private final String npcName;

    private String fullText;
    private final List<ChoiceEntry> choices = new ArrayList<>();
    private final List<String> conversationHistory = new ArrayList<>();

    // Typewriter state
    private int visibleChars = 0;
    private int tickCounter = 0;
    private boolean typewriterFinished = false;
    private float choicesAlpha = 0.0f;
    private int scrollOffset = 0;

    // Geometry
    private int dialogueX;
    private int dialogueY;
    private int dialogueW;
    private int dialogueH;

    private int choicesX;
    private int choicesY;
    private int choicesW;
    private int choicesH;

    private int choicesScrollOffset = 0;
    private final List<MinimalButton> choiceButtons = new ArrayList<>();

    public record ChoiceEntry(String text, String targetNodeId) {}

    public DialogueScreen(int entityId, String dialogueId, String dialogueTitle, String npcName, String startText, List<ChoiceEntry> choices) {
        super(Text.literal(dialogueTitle));
        this.entityId = entityId;
        this.dialogueId = dialogueId;
        this.dialogueTitle = dialogueTitle;
        this.npcName = npcName;
        this.fullText = startText;
        this.choices.addAll(choices);
        this.conversationHistory.add("§e" + npcName + "§7: " + startText);
    }

    public void updateNode(String newText, List<ChoiceEntry> newChoices) {
        this.fullText = newText;
        this.choices.clear();
        this.choices.addAll(newChoices);
        this.visibleChars = 0;
        this.typewriterFinished = false;
        this.choicesAlpha = 0.0f;
        this.choicesScrollOffset = 0;
        this.conversationHistory.add("§e" + npcName + "§7: " + newText);
        this.init();
    }

    @Override
    protected void init() {
        this.clearChildren();
        this.choiceButtons.clear();

        // Calculate CRPG Layout:
        // Left: NPC (0 to 90px)
        // Middle: Narrative log (100px to width - 260px)
        // Right: Choices panel (width - 250px, width: 155px)
        // Far Right: Player (width - 90px to width)
        this.choicesW = Math.min(170, Math.max(130, this.width / 4));
        int sideMargin = Math.min(95, Math.max(70, this.width / 7));

        this.dialogueX = sideMargin + 10;
        this.dialogueH = Math.min(115, Math.max(85, this.height / 3));
        this.dialogueY = this.height - dialogueH - 14;

        this.choicesX = this.width - sideMargin - choicesW - 8;
        this.choicesY = dialogueY;
        this.choicesH = dialogueH;

        this.dialogueW = choicesX - dialogueX - 10;

        if (typewriterFinished) {
            rebuildChoiceButtons();
        }
    }

    private void rebuildChoiceButtons() {
        for (MinimalButton btn : choiceButtons) {
            this.remove(btn);
        }
        choiceButtons.clear();

        int btnH = 18;
        int btnGap = 4;
        int btnW = choicesW - 12;

        for (int i = 0; i < choices.size(); i++) {
            ChoiceEntry choice = choices.get(i);
            final int index = i;
            int btnY = choicesY + 20 + (i * (btnH + btnGap)) - choicesScrollOffset;

            MinimalButton choiceBtn = MinimalButton.builder(Text.literal((i + 1) + ". " + choice.text()), button -> {
                sendChoice(choice.targetNodeId(), index);
            }).dimensions(choicesX + 6, btnY, btnW, btnH).build();

            // Set visibility based on choices scissor viewport
            choiceBtn.visible = (btnY >= choicesY + 16 && btnY + btnH <= choicesY + choicesH);

            this.choiceButtons.add(choiceBtn);
            this.addDrawableChild(choiceBtn);
        }
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
            // Smooth typewriter speed: 2 characters per tick
            visibleChars = Math.min(fullText.length(), visibleChars + 2);
            if (visibleChars >= fullText.length()) {
                typewriterFinished = true;
                rebuildChoiceButtons();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!typewriterFinished && button == 0) {
            // Click anywhere to skip typewriter effect
            visibleChars = fullText.length();
            typewriterFinished = true;
            rebuildChoiceButtons();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // Scroll narrative history or choices panel
        if (mouseX >= choicesX && mouseX <= choicesX + choicesW && mouseY >= choicesY && mouseY <= choicesY + choicesH) {
            int maxScroll = Math.max(0, (choices.size() * 22) - (choicesH - 24));
            choicesScrollOffset = Math.max(0, Math.min(maxScroll, choicesScrollOffset - (int) (amount * 16)));
            rebuildChoiceButtons();
            return true;
        } else if (mouseX >= dialogueX && mouseX <= dialogueX + dialogueW && mouseY >= dialogueY && mouseY <= dialogueY + dialogueH) {
            scrollOffset = Math.max(0, scrollOffset - (int) (amount * 12));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Dark medieval vignette atmospheric overlay
        context.fillGradient(0, 0, this.width, this.height, 0xDD080808, 0xF5040404);

        // 2. Render 3D NPC entity on the LEFT (Facing mouse/dialogue)
        if (this.client != null && this.client.world != null) {
            net.minecraft.entity.Entity e = this.client.world.getEntityById(entityId);
            if (e instanceof LivingEntity livingNpc) {
                int npcX = dialogueX / 2;
                int npcY = this.height - 24;
                float lookX = (float) (npcX - mouseX);
                float lookY = (float) (npcY - 45 - mouseY);
                InventoryScreen.drawEntity(context, npcX, npcY, 44, lookX, lookY, livingNpc);
            }
        }

        // 3. Render 3D Player entity on the RIGHT (Facing mouse/dialogue)
        if (this.client != null && this.client.player != null) {
            int playerX = this.width - ((this.width - (choicesX + choicesW)) / 2);
            int playerY = this.height - 24;
            float lookX = (float) (playerX - mouseX);
            float lookY = (float) (playerY - 45 - mouseY);
            InventoryScreen.drawEntity(context, playerX, playerY, 44, lookX, lookY, this.client.player);
        }

        // 4. Central CRPG Narrative Dialogue Box (Bottom)
        context.fill(dialogueX, dialogueY, dialogueX + dialogueW, dialogueY + dialogueH, 0xE50B0B0B);
        context.drawBorder(dialogueX, dialogueY, dialogueW, dialogueH, 0xFF423B30);
        context.drawBorder(dialogueX + 1, dialogueY + 1, dialogueW - 2, dialogueH - 2, 0xFF221F1A);

        // Speaker Header
        context.drawText(this.textRenderer, "§6" + npcName, dialogueX + 12, dialogueY + 7, 0xFFFFAA, false);

        // History scroll indicator if multiple lines
        if (conversationHistory.size() > 1) {
            context.drawText(this.textRenderer, "§8[История: скролл]", dialogueX + dialogueW - 105, dialogueY + 7, 0x777777, false);
        }

        // Scissor-clipped speech text
        context.enableScissor(dialogueX + 4, dialogueY + 20, dialogueX + dialogueW - 4, dialogueY + dialogueH - 4);
        String displayedText = fullText.substring(0, Math.min(visibleChars, fullText.length()));
        List<String> wrappedLines = wrapText(displayedText, dialogueW - 24);
        int lineStartY = dialogueY + 22 - scrollOffset;
        for (int i = 0; i < wrappedLines.size(); i++) {
            int curY = lineStartY + (i * 12);
            if (curY >= dialogueY + 18 && curY <= dialogueY + dialogueH - 10) {
                context.drawText(this.textRenderer, wrappedLines.get(i), dialogueX + 12, curY, 0xFFFFFF, false);
            }
        }
        context.disableScissor();

        // 5. Right Player Choices Box
        context.fill(choicesX, choicesY, choicesX + choicesW, choicesY + choicesH, 0xE50E0E0E);
        context.drawBorder(choicesX, choicesY, choicesW, choicesH, 0xFF353535);

        // Choices Header
        context.drawText(this.textRenderer, "§7Варианты ответа:", choicesX + 8, choicesY + 7, 0xCCCCCC, false);

        // Enable scissor box around choices list so buttons NEVER draw outside the frame
        context.enableScissor(choicesX + 2, choicesY + 18, choicesX + choicesW - 2, choicesY + choicesH - 2);
        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();

        // Draw choices scrollbar if needed
        int totalChoicesH = choices.size() * 22;
        int maxChoicesScroll = Math.max(0, totalChoicesH - (choicesH - 24));
        if (maxChoicesScroll > 0) {
            int sbH = Math.max(14, (int) ((float) (choicesH - 24) / totalChoicesH * (choicesH - 24)));
            int sbY = choicesY + 20 + (int) ((float) choicesScrollOffset / maxChoicesScroll * (choicesH - 24 - sbH));
            int sbX = choicesX + choicesW - 5;
            context.fill(sbX, sbY, sbX + 3, sbY + sbH, 0xFF666666);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (this.textRenderer.getWidth(testLine) <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? "" : " ").append(word);
            } else {
                if (currentLine.length() > 0) lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
