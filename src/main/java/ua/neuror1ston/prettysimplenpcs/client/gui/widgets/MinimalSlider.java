package ua.neuror1ston.prettysimplenpcs.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Minimalist flat slider widget with customizable range and step display.
 */
public class MinimalSlider extends SliderWidget {
    private final float min;
    private final float max;
    private final String prefix;
    private final Consumer<Float> onValueChange;

    public MinimalSlider(int x, int y, int width, int height, String prefix, float min, float max, float currentValue, Consumer<Float> onValueChange) {
        super(x, y, width, height, Text.empty(), toNormalized(currentValue, min, max));
        this.min = min;
        this.max = max;
        this.prefix = prefix;
        this.onValueChange = onValueChange;
        updateMessage();
    }

    private static double toNormalized(float val, float min, float max) {
        return Math.max(0.0, Math.min(1.0, (val - min) / (max - min)));
    }

    public float getActualValue() {
        return (float) (min + (this.value * (max - min)));
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Text.literal(prefix + String.format("%.2fx", getActualValue())));
    }

    @Override
    protected void applyValue() {
        if (onValueChange != null) {
            onValueChange.accept(getActualValue());
        }
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        boolean isHovered = isHovered();

        // Background track
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF121212);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), isHovered ? 0xFF666666 : 0xFF353535);

        // Thumb slider bar
        int thumbW = 10;
        int thumbX = getX() + (int) (this.value * (getWidth() - thumbW));
        int thumbColor = isHovered ? 0xFF555555 : 0xFF3A3A3A;
        context.fill(thumbX, getY() + 1, thumbX + thumbW, getY() + getHeight() - 1, thumbColor);
        context.drawBorder(thumbX, getY() + 1, thumbW, getHeight() - 2, 0xFF777777);

        // Text
        int textX = getX() + (getWidth() - textRenderer.getWidth(getMessage())) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        context.drawText(textRenderer, getMessage(), textX, textY, 0xFFFFFF, false);
    }
}
