package ua.neuror1ston.prettysimplenpcs.client.gui;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ua.neuror1ston.prettysimplenpcs.client.gui.widgets.MinimalButton;
import ua.neuror1ston.prettysimplenpcs.network.NpcNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom medieval Trade Screen displaying items, pricing in silver/gold,
 * per-player pool limits, and real-time inventory coin balances in the bottom-left corner.
 */
public class TradeScreen extends Screen {
    private final int entityId;
    private final String tradeMatrixId;
    private final String titleText;
    private final int playerSilver;
    private final int playerGold;
    private final List<TradeClientEntry> entries = new ArrayList<>();

    public record TradeClientEntry(
            String id,
            String itemId,
            int itemCount,
            int priceSilver,
            int priceGold,
            String poolType,
            int remainingStock
    ) {}

    private int panelX;
    private int panelY;
    private final int panelWidth = 340;
    private final int panelHeight = 220;

    public TradeScreen(int entityId, String tradeMatrixId, String titleText, int playerSilver, int playerGold, List<TradeClientEntry> entries) {
        super(Text.literal(titleText));
        this.entityId = entityId;
        this.tradeMatrixId = tradeMatrixId;
        this.titleText = titleText;
        this.playerSilver = playerSilver;
        this.playerGold = playerGold;
        this.entries.addAll(entries);
    }

    @Override
    protected void init() {
        this.panelX = (this.width - panelWidth) / 2;
        this.panelY = (this.height - panelHeight) / 2;
        this.clearChildren();

        // Close button
        MinimalButton closeBtn = MinimalButton.builder(Text.literal("Закрыть"), button -> this.close())
                .dimensions(panelX + panelWidth - 75, panelY + panelHeight - 26, 65, 18).build();
        this.addDrawableChild(closeBtn);

        // Trade entry rows
        int rowY = panelY + 32;
        for (int i = 0; i < Math.min(entries.size(), 5); i++) {
            TradeClientEntry entry = entries.get(i);
            int currentY = rowY + (i * 32);

            boolean outOfStock = "LIMITED".equalsIgnoreCase(entry.poolType()) && entry.remainingStock() <= 0;
            int totalSilverCost = entry.priceSilver() + (entry.priceGold() * 10);
            int playerTotalSilver = playerSilver + (playerGold * 10);
            boolean canAfford = playerTotalSilver >= totalSilverCost && !outOfStock;

            MinimalButton buyBtn = MinimalButton.builder(Text.literal(canAfford ? "Купить" : "Нет средств"), button -> {
                if (canAfford) {
                    buyItem(entry.id());
                }
            }).dimensions(panelX + panelWidth - 85, currentY + 4, 75, 18).build();
            buyBtn.active = canAfford;
            if (canAfford) {
                buyBtn.setCustomBorderColor(0xFF226622);
            }
            this.addDrawableChild(buyBtn);
        }
    }

    private void buyItem(String entryId) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(entityId);
        buf.writeString(tradeMatrixId);
        buf.writeString(entryId);
        buf.writeInt(1); // 1 transaction
        ClientPlayNetworking.send(NpcNetwork.PERFORM_TRADE_C2S, buf);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Deep medieval black background
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0D0D0D);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF4A4A4A);

        // Header Title
        context.drawText(this.textRenderer, "§6" + titleText, panelX + 12, panelY + 12, 0xFFFFAA, false);

        // Trade rows
        int rowY = panelY + 32;
        for (int i = 0; i < Math.min(entries.size(), 5); i++) {
            TradeClientEntry entry = entries.get(i);
            int currentY = rowY + (i * 32);

            context.fill(panelX + 8, currentY, panelX + panelWidth - 8, currentY + 28, 0xFF181818);

            // Item Icon and name
            Identifier itemId = Identifier.tryParse(entry.itemId());
            ItemStack stack = ItemStack.EMPTY;
            if (itemId != null && Registries.ITEM.containsId(itemId)) {
                Item item = Registries.ITEM.get(itemId);
                stack = new ItemStack(item, entry.itemCount());
                context.drawItem(stack, panelX + 12, currentY + 6);
            }

            String itemName = stack.isEmpty() ? entry.itemId() : stack.getName().getString();
            context.drawText(this.textRenderer, "§f" + entry.itemCount() + "x " + itemName, panelX + 34, currentY + 5, 0xFFFFFF, false);

            // Price in Silver & Gold
            String priceStr = "§7Цена: ";
            if (entry.priceGold() > 0) priceStr += "§6" + entry.priceGold() + " зол. ";
            if (entry.priceSilver() > 0) priceStr += "§f" + entry.priceSilver() + " сер.";
            context.drawText(this.textRenderer, priceStr, panelX + 34, currentY + 16, 0xCCCCCC, false);

            // Stock label
            if ("LIMITED".equalsIgnoreCase(entry.poolType())) {
                context.drawText(this.textRenderer, "§8Остаток: " + entry.remainingStock(), panelX + 180, currentY + 16, 0x888888, false);
            }
        }

        // --- Bottom Left Corner: Player Coin Balances ---
        int balX = panelX + 12;
        int balY = panelY + panelHeight - 24;
        context.drawText(this.textRenderer, "§7Кошелек: §f" + playerSilver + " сер. §6" + playerGold + " зол.", balX, balY, 0xFFFFFF, false);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
