package ua.neuror1ston.prettysimplenpcs.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structures for trade matrices, currency conversions, and limited stock pools.
 */
public class TradeData {

    public enum PoolType {
        INFINITE,
        LIMITED
    }

    public static class TradeEntry {
        private String id = "trade_" + System.currentTimeMillis();
        private String itemId = "minecraft:bread";
        private int itemCount = 1;

        // Pricing in coins
        private int priceSilver = 1; // 1 silver = 1 iron_nugget
        private int priceGold = 0;   // 1 gold = 1 gold_nugget (10 silver)

        // Stock management
        private PoolType poolType = PoolType.INFINITE;
        private int globalStock = 100;
        private int perPlayerCap = 10;
        private int resetCooldownSeconds = 3600; // 1 hour

        // Barter marker (ready for future expansion)
        private boolean barterEnabled = false;
        private String barterItemId = "";
        private int barterItemCount = 0;

        public TradeEntry() {}

        public TradeEntry(String itemId, int itemCount, int priceSilver, int priceGold) {
            this.itemId = itemId;
            this.itemCount = itemCount;
            this.priceSilver = priceSilver;
            this.priceGold = priceGold;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }

        public int getItemCount() { return itemCount; }
        public void setItemCount(int itemCount) { this.itemCount = itemCount; }

        public int getPriceSilver() { return priceSilver; }
        public void setPriceSilver(int priceSilver) { this.priceSilver = priceSilver; }

        public int getPriceGold() { return priceGold; }
        public void setPriceGold(int priceGold) { this.priceGold = priceGold; }

        public PoolType getPoolType() { return poolType; }
        public void setPoolType(PoolType poolType) { this.poolType = poolType; }

        public int getGlobalStock() { return globalStock; }
        public void setGlobalStock(int globalStock) { this.globalStock = globalStock; }

        public int getPerPlayerCap() { return perPlayerCap; }
        public void setPerPlayerCap(int perPlayerCap) { this.perPlayerCap = perPlayerCap; }

        public int getResetCooldownSeconds() { return resetCooldownSeconds; }
        public void setResetCooldownSeconds(int resetCooldownSeconds) { this.resetCooldownSeconds = resetCooldownSeconds; }

        public boolean isBarterEnabled() { return barterEnabled; }
        public void setBarterEnabled(boolean barterEnabled) { this.barterEnabled = barterEnabled; }

        public String getBarterItemId() { return barterItemId; }
        public void setBarterItemId(String barterItemId) { this.barterItemId = barterItemId; }

        public int getBarterItemCount() { return barterItemCount; }
        public void setBarterItemCount(int barterItemCount) { this.barterItemCount = barterItemCount; }

        /**
         * Calculates total cost in silver coins (10 silver = 1 gold).
         */
        public int getTotalPriceInSilver() {
            return priceSilver + (priceGold * 10);
        }
    }

    public static class TradeMatrix {
        private String id = "default_trades";
        private String title = "Торгівельна лавка";
        private List<TradeEntry> entries = new ArrayList<>();

        public TradeMatrix() {}

        public TradeMatrix(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public List<TradeEntry> getEntries() { return entries; }
        public void setEntries(List<TradeEntry> entries) { this.entries = entries; }

        public TradeEntry getEntry(String entryId) {
            for (TradeEntry e : entries) {
                if (e.getId().equals(entryId)) return e;
            }
            return null;
        }
    }
}
