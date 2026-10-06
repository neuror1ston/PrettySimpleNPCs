package ua.neuror1ston.prettysimplenpcs.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Consumer;

/**
 * Hierarchical tree file explorer for dialogues with folder folding, sorting,
 * scrolling, selection, and binding indicators.
 */
public class DialogueFileTreeWidget extends ClickableWidget {

    public interface Entry {
        String getName();
        String getFullPath();
        boolean isFolder();
    }

    public static class FolderEntry implements Entry {
        private final String name;
        private final String fullPath;
        private boolean expanded = true;
        private final List<Entry> children = new ArrayList<>();

        public FolderEntry(String name, String fullPath) {
            this.name = name;
            this.fullPath = fullPath;
        }

        @Override public String getName() { return name; }
        @Override public String getFullPath() { return fullPath; }
        @Override public boolean isFolder() { return true; }
        public boolean isExpanded() { return expanded; }
        public void setExpanded(boolean expanded) { this.expanded = expanded; }
        public List<Entry> getChildren() { return children; }
    }

    public static class FileEntry implements Entry {
        private final String name;
        private final String fullPath;

        public FileEntry(String name, String fullPath) {
            this.name = name;
            this.fullPath = fullPath;
        }

        @Override public String getName() { return name; }
        @Override public String getFullPath() { return fullPath; }
        @Override public boolean isFolder() { return false; }
    }

    private final List<String> rawPaths = new ArrayList<>();
    private final FolderEntry root = new FolderEntry("root", "");
    private final List<FlatItem> visibleItems = new ArrayList<>();

    private String selectedPath = "";
    private String boundPath = "";
    private boolean sortAscending = true;
    private int scrollOffset = 0;
    private final int rowHeight = 18;

    private Consumer<String> onSelectFile;
    private Consumer<String> onBindFile;

    public record FlatItem(Entry entry, int depth) {}

    public DialogueFileTreeWidget(int x, int y, int width, int height, List<String> paths, String initiallyBound, Consumer<String> onSelect, Consumer<String> onBind) {
        super(x, y, width, height, Text.literal("FileTree"));
        this.boundPath = initiallyBound != null ? initiallyBound : "";
        this.selectedPath = this.boundPath;
        this.onSelectFile = onSelect;
        this.onBindFile = onBind;
        setPaths(paths);
    }

    public void setPaths(List<String> paths) {
        this.rawPaths.clear();
        this.rawPaths.addAll(paths);
        rebuildTree();
    }

    public void toggleSort() {
        this.sortAscending = !this.sortAscending;
        rebuildTree();
    }

    public boolean isSortAscending() {
        return sortAscending;
    }

    public String getSelectedPath() {
        return selectedPath;
    }

    public void setSelectedPath(String path) {
        this.selectedPath = path != null ? path : "";
    }

    public void setBoundPath(String bound) {
        this.boundPath = bound != null ? bound : "";
    }

    private void rebuildTree() {
        root.getChildren().clear();

        // Sort paths
        List<String> sorted = new ArrayList<>(rawPaths);
        sorted.sort((a, b) -> sortAscending ? a.compareToIgnoreCase(b) : b.compareToIgnoreCase(a));

        for (String p : sorted) {
            String clean = p.replace('\\', '/');
            String[] parts = clean.split("/");
            FolderEntry current = root;

            for (int i = 0; i < parts.length - 1; i++) {
                String folderName = parts[i];
                FolderEntry next = null;
                for (Entry e : current.getChildren()) {
                    if (e.isFolder() && e.getName().equalsIgnoreCase(folderName)) {
                        next = (FolderEntry) e;
                        break;
                    }
                }
                if (next == null) {
                    String subPath = current.getFullPath().isEmpty() ? folderName : current.getFullPath() + "/" + folderName;
                    next = new FolderEntry(folderName, subPath);
                    current.getChildren().add(next);
                }
                current = next;
            }

            String fileName = parts[parts.length - 1];
            current.getChildren().add(new FileEntry(fileName, clean));
        }

        flattenTree();
    }

    private void flattenTree() {
        visibleItems.clear();
        flattenFolder(root, 0);
    }

    private void flattenFolder(FolderEntry folder, int depth) {
        for (Entry e : folder.getChildren()) {
            visibleItems.add(new FlatItem(e, depth));
            if (e instanceof FolderEntry subFolder && subFolder.isExpanded()) {
                flattenFolder(subFolder, depth + 1);
            }
        }
    }

    public int getMaxScroll() {
        int totalContentHeight = visibleItems.size() * rowHeight;
        return Math.max(0, totalContentHeight - (getHeight() - 4));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (isMouseOver(mouseX, mouseY)) {
            this.scrollOffset = Math.max(0, Math.min(getMaxScroll(), this.scrollOffset - (int) (amount * 16)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) {
            return false;
        }

        int relY = (int) mouseY - getY() - 2 + scrollOffset;
        int index = relY / rowHeight;

        if (index >= 0 && index < visibleItems.size()) {
            FlatItem item = visibleItems.get(index);
            if (item.entry().isFolder()) {
                FolderEntry folder = (FolderEntry) item.entry();
                folder.setExpanded(!folder.isExpanded());
                flattenTree();
                return true;
            } else {
                this.selectedPath = item.entry().getFullPath();
                if (onSelectFile != null) {
                    onSelectFile.accept(selectedPath);
                }

                // Check if clicked the bind button on the right
                int bindBtnX = getX() + getWidth() - 58;
                if (mouseX >= bindBtnX && mouseX <= bindBtnX + 48) {
                    this.boundPath = selectedPath;
                    if (onBindFile != null) {
                        onBindFile.accept(boundPath);
                    }
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;

        // Container frame
        context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF0E0E0E);
        context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0xFF353535);

        if (visibleItems.isEmpty()) {
            context.drawText(textRenderer, "§8[Нет доступных диалогов]", getX() + 10, getY() + (getHeight() - 8) / 2, 0x666666, false);
            return;
        }

        // Enable scissor box
        context.enableScissor(getX() + 1, getY() + 1, getX() + getWidth() - 1, getY() + getHeight() - 1);

        int startY = getY() + 2 - scrollOffset;

        for (int i = 0; i < visibleItems.size(); i++) {
            int currentY = startY + (i * rowHeight);

            if (currentY + rowHeight < getY() || currentY > getY() + getHeight()) {
                continue;
            }

            FlatItem item = visibleItems.get(i);
            int indent = item.depth() * 10;
            boolean isSelected = !item.entry().isFolder() && item.entry().getFullPath().equals(selectedPath);
            boolean isBound = !item.entry().isFolder() && item.entry().getFullPath().equals(boundPath);
            boolean isHovered = mouseX >= getX() + 2 && mouseX <= getX() + getWidth() - 10 && mouseY >= currentY && mouseY < currentY + rowHeight;

            // Highlight selected or hovered row
            if (isSelected) {
                context.fill(getX() + 2, currentY, getX() + getWidth() - 10, currentY + rowHeight, 0xFF2A2A2A);
                context.drawBorder(getX() + 2, currentY, getWidth() - 12, rowHeight, 0xFF666666);
            } else if (isHovered) {
                context.fill(getX() + 2, currentY, getX() + getWidth() - 10, currentY + rowHeight, 0xFF181818);
            }

            int iconX = getX() + 6 + indent;
            if (item.entry().isFolder()) {
                FolderEntry folder = (FolderEntry) item.entry();
                String prefix = folder.isExpanded() ? "[-] " : "[+] ";
                context.drawText(textRenderer, "§6" + prefix + folder.getName(), iconX, currentY + 5, 0xDDDDDD, false);
            } else {
                int bindX = getX() + getWidth() - 58;
                int maxNameW = bindX - iconX - 6;
                String displayName = item.entry().getName() + ".json";
                String clipped = textRenderer.trimToWidth(displayName, maxNameW);
                if (clipped.length() < displayName.length() && clipped.length() > 3) {
                    clipped = textRenderer.trimToWidth(displayName, maxNameW - textRenderer.getWidth("...")) + "...";
                }

                int textColor = isBound ? 0x88FF88 : (isSelected ? 0xFFFFAA : 0xDDDDDD);
                context.drawText(textRenderer, clipped, iconX, currentY + 5, textColor, false);

                // Bind badge / button on right
                boolean isBindHovered = mouseX >= bindX && mouseX <= bindX + 48 && mouseY >= currentY + 2 && mouseY <= currentY + 16;
                int bindBg = isBound ? 0xFF1C381C : (isBindHovered ? 0xFF2E2E2E : 0xFF141414);
                int bindBorder = isBound ? 0xFF3E823E : (isBindHovered ? 0xFF666666 : 0xFF3A3A3A);
                context.fill(bindX, currentY + 2, bindX + 48, currentY + 16, bindBg);
                context.drawBorder(bindX, currentY + 2, 48, 14, bindBorder);

                String bindText = isBound ? "§aСВЯЗАН" : (isBindHovered ? "§fВЫБРАТЬ" : "§7ВЫБРАТЬ");
                int textX = bindX + (48 - textRenderer.getWidth(bindText)) / 2;
                context.drawText(textRenderer, bindText, textX, currentY + 5, 0xFFFFFF, false);
            }
        }

        context.disableScissor();

        // Render scrollbar
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarH = Math.max(16, (int) ((float) getHeight() / (visibleItems.size() * rowHeight) * (getHeight() - 4)));
            int scrollbarY = getY() + 2 + (int) ((float) scrollOffset / maxScroll * (getHeight() - 4 - scrollbarH));
            int scrollbarX = getX() + getWidth() - 7;

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
