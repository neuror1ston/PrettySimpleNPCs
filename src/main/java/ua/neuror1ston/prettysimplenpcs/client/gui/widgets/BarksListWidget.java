package ua.neuror1ston.prettysimplenpcs.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

/**
 * Scrollable list widget for Idle Barks supporting dozens of phrases
 * with mouse wheel scrolling, scrollbar rendering, and individual delete buttons.
 */
public class BarksListWidget extends ClickableWidget {
    private final List<String> barks;
    private final Consumer<Integer> onDeleteBark;
    private int scrollOffset = 0;
    private final int itemHeight = 22;

    public BarksListWidget(int x, int y, int width, int height, List<String> barks, Consumer<Integer> onDeleteBark) {
        super(x, y, width, height, Text.literal("Barks"));
        this.barks = barks;
        this.onDeleteBark = onDeleteBark;
    }

    public int getMaxScroll() {
        int totalContentHeight = barks.size() * itemHeight;
        return Math.max(0, totalContentHeight - (getHeight() - 6));
    }

    public void scroll(double amount) {
        this.scrollOffset = Math.max(0, Math.min(getMaxScroll(), this.scrollOffset - (int) (amount * 16)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isMouseOver(mouseX, mouseY)) {
            scroll(amount);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }

        int relY = (int) mouseY - getY() - 3 + scrollOffset;
        int clickedIndex = relY / itemHeight;

        if (clickedIndex >= 0 && clickedIndex < barks.size()) {
            // Check if clicked the [X] delete button (right side: width - 26 to width - 12)
            int delBtnX = getX() + getWidth() - 26;
            int itemStartY = getY() + 3 + (clickedIndex * itemHeight) - scrollOffset;

            if (mouseX >= delBtnX && mouseX <= delBtnX + 14 && mouseY >= itemStartY + 3 && mouseY <= itemStartY + 17) {
                if (onDeleteBark != null) {
                    onDeleteBark.accept(clickedIndex);
                    this.scrollOffset = Math.max(0, Math.min(getMaxScroll(), this.scrollOffset));
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        // Outer box
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF0E0E0E);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFF353535);

        if (barks.isEmpty()) {
            context.drawText(textRenderer, "§8[Нет добавленных реплик над головой]", getX() + 10, getY() + (getHeight() - 8) / 2, 0x666666, false);
            return;
        }

        // Enable Scissor for safe scrolling without overflow
        context.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1);

        int startY = getY() + 3 - scrollOffset;
        for (int i = 0; i < barks.size(); i++) {
            int currentY = startY + (i * itemHeight);

            // Skip rendering offscreen elements
            if (currentY + itemHeight < getY() || currentY > getY() + getHeight()) {
                continue;
            }

            // Row background hover
            boolean isRowHovered = mouseX >= getX() + 3 && mouseX <= getX() + getWidth() - 10 && mouseY >= currentY && mouseY < currentY + itemHeight - 2;
            context.fill(getX() + 3, currentY, getX() + getWidth() - 10, currentY + itemHeight - 2, isRowHovered ? 0xFF1C1C1C : 0xFF141414);
            context.drawBorder(getX() + 3, currentY, getWidth() - 13, itemHeight - 2, 0xFF2A2A2A);

            // Row number
            context.drawText(textRenderer, "§7" + (i + 1) + ".", getX() + 6, currentY + 5, 0x888888, false);

            // Delete [X] button with safe 4px margin before scrollbar
            int delX = getX() + getWidth() - 26;
            boolean isDelHovered = mouseX >= delX && mouseX <= delX + 14 && mouseY >= currentY + 3 && mouseY <= currentY + 17;
            context.fill(delX, currentY + 3, delX + 14, currentY + 17, isDelHovered ? 0xFF882222 : 0xFF2A1515);
            context.drawBorder(delX, currentY + 3, 14, 14, isDelHovered ? 0xFFFF4444 : 0xFF552222);
            int textDelX = delX + (14 - textRenderer.getWidth("X")) / 2;
            context.drawText(textRenderer, "X", textDelX, currentY + 4, isDelHovered ? 0xFFFFFF : 0xFFAAAAAA, false);

            // Bark text clipped to available space before delete button
            int maxTextW = delX - getX() - 32;
            String text = "«" + barks.get(i) + "»";
            String clipped = textRenderer.trimToWidth(text, maxTextW);
            if (clipped.length() < text.length() && clipped.length() > 3) {
                clipped = textRenderer.trimToWidth(text, maxTextW - textRenderer.getWidth("...")) + "...";
            }
            context.drawText(textRenderer, clipped, getX() + 24, currentY + 5, 0xFFFFFF, false);
        }

        context.disableScissor();

        // Render scrollbar thumb if content overflows
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarH = Math.max(16, (int) ((float) getHeight() / (barks.size() * itemHeight) * (getHeight() - 4)));
            int scrollbarY = getY() + 2 + (int) ((float) scrollOffset / maxScroll * (getHeight() - 4 - scrollbarH));
            int scrollbarX = getX() + getWidth() - 8;

            context.fill(scrollbarX, getY() + 2, scrollbarX + 5, getY() + getHeight() - 2, 0xFF181818);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 5, scrollbarY + scrollbarH, 0xFF555555);
            context.drawBorder(scrollbarX, scrollbarY, 5, scrollbarH, 0xFF888888);
        }
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }
}
