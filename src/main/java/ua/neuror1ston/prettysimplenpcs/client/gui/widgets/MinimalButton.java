package ua.neuror1ston.prettysimplenpcs.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Custom minimalist flat button matching the medieval/dark UI specification:
 * - Black/dark-grey background
 * - Inactive state: Crisp WHITE text, subtle border
 * - Active state: GREY text, visible neat outline
 * - Hover: border highlight
 * - Automatic text clipping to prevent overflow
 * - Safe onPress callback receiving the actual MinimalButton instance (no NPE!)
 */
public class MinimalButton extends ClickableWidget {

    @FunctionalInterface
    public interface PressAction {
        void onPress(MinimalButton button);
    }

    private final PressAction onPress;
    private boolean activeState = false;
    private int customBorderColor = 0;

    public MinimalButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public static Builder builder(Text message, PressAction onPress) {
        return new Builder(message, onPress);
    }

    public void setActiveState(boolean active) {
        this.activeState = active;
    }

    public boolean isActiveState() {
        return activeState;
    }

    public void setCustomBorderColor(int color) {
        this.customBorderColor = color;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.onPress != null) {
            this.onPress.onPress(this); // Passes this instance safely!
        }
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        boolean isHovered = isHovered();

        // 1. Background
        int bgColor = isHovered ? 0xFF222222 : 0xFF121212;
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bgColor);

        // 2. Borders & Outlines
        int borderColor = 0xFF353535;
        if (customBorderColor != 0) {
            borderColor = customBorderColor;
        } else if (activeState) {
            borderColor = 0xFF9E9E9E; // Active neat outline
        } else if (isHovered) {
            borderColor = 0xFF666666; // Hover highlight
        }

        context.drawBorder(getX(), getY(), getWidth(), getHeight(), borderColor);

        // Double border for active state (distinct medieval outline)
        if (activeState) {
            context.drawBorder(getX() + 1, getY() + 1, getWidth() - 2, getHeight() - 2, 0xFF444444);
        }

        // 3. Text color: Inactive = Pure WHITE, Active = GREY
        int textColor;
        if (!this.active) {
            textColor = 0x555555;
        } else if (activeState) {
            textColor = isHovered ? 0xCCCCCC : 0x8E8E8E; // Active: Grey
        } else {
            textColor = 0xFFFFFF; // Inactive: Pure White
        }

        // 4. Safe text rendering with Scissor Box to strictly avoid overflow
        int availableTextWidth = getWidth() - 8;
        String rawText = getMessage().getString();
        String clippedText = textRenderer.trimToWidth(rawText, availableTextWidth);
        if (clippedText.length() < rawText.length() && clippedText.length() > 3) {
            clippedText = textRenderer.trimToWidth(rawText, availableTextWidth - textRenderer.getWidth("...")) + "...";
        }

        int textX = getX() + (getWidth() - textRenderer.getWidth(clippedText)) / 2;
        int textY = getY() + (getHeight() - 8) / 2;

        context.enableScissor(getX() + 2, getY() + 1, getX() + getWidth() - 2, getY() + getHeight() - 1);
        context.drawText(textRenderer, clippedText, textX, textY, textColor, false);
        context.disableScissor();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        this.appendDefaultNarrations(builder);
    }

    public static class Builder {
        private final Text message;
        private final PressAction onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;
        private boolean activeState = false;

        public Builder(Text message, PressAction onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder activeState(boolean active) {
            this.activeState = active;
            return this;
        }

        public MinimalButton build() {
            MinimalButton btn = new MinimalButton(x, y, width, height, message, onPress);
            btn.setActiveState(activeState);
            return btn;
        }
    }
}
