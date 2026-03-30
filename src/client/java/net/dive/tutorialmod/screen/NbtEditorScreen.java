package net.dive.tutorialmod.screen;

import net.minecraft.client.input.KeyInput;
import net.dive.tutorialmod.network.SaveNbtPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.security.Key;
import java.util.List;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public class NbtEditorScreen extends Screen {

    private final String editType;
    private final String targetInfo;
    private final String initialNbt;

    private NbtListWidget listWidget;
    private TextFieldWidget searchField;

    private String statusMessage = "";
    private int statusColor = 0xAAAAAA;
    private int entryCount = 0;

    // ── Panel geometry ──
    private static final int HEADER_H  = 52; // title + search bar
    private static final int FOOTER_H  = 52; // buttons + status
    private static final int ROW_H     = 24;

    public NbtEditorScreen(String editType, String targetInfo, String initialNbt) {
        super(Text.literal("NBT Editor"));
        this.editType   = editType;
        this.targetInfo = targetInfo;
        this.initialNbt = initialNbt;
    }

    @Override
    public boolean shouldPause() { return true; }

    // ─────────────────────────────────────────────
    @Override
    protected void init() {
        super.init();

        int listTop    = HEADER_H + 4;
        int listBottom = this.height - FOOTER_H;

        // ── NBT list ──
        this.listWidget = new NbtListWidget(
                this.client, this.width, listBottom, listTop, ROW_H);
        populateEntries();
        this.addDrawableChild(this.listWidget);

        // ── Search / filter field ──
        this.searchField = new TextFieldWidget(
                this.textRenderer, this.width / 2 - 100, 28, 200, 16,
                Text.literal("Search keys..."));
        this.searchField.setMaxLength(128);
        this.searchField.setPlaceholder(Text.literal("Search keys...").formatted(Formatting.DARK_GRAY));
        this.searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(this.searchField);

        // ── Add entry button ──
        int addY = this.height - FOOTER_H + 6;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("+ Add"),
                btn -> {
                    this.listWidget.addNbtEntry("new_key", "value", (byte) 8);
                    this.listWidget.setScrollY(Double.MAX_VALUE);
                    this.entryCount = this.listWidget.children().size();
                }
        ).dimensions(this.width / 2 - 155, addY, 60, 20).build());

        // ── Clear all button ──
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Clear").formatted(Formatting.RED),
                btn -> {
                    this.listWidget.children().clear();
                    this.entryCount = 0;
                    setStatus("All entries cleared.", 0xFFAA00);
                }
        ).dimensions(this.width / 2 - 88, addY, 60, 20).build());

        // ── Save button ──
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Save").formatted(Formatting.GREEN),
                btn -> saveNbt()
        ).dimensions(this.width / 2 - 21, addY, 80, 20).build());

        // ── Cancel button ──
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel").formatted(Formatting.RED),
                btn -> this.close()
        ).dimensions(this.width / 2 + 66, addY, 80, 20).build());
    }

    // ── Parse NBT and fill list ──
    private void populateEntries() {
        int loaded = 0;
        try {
            NbtCompound nbt = StringNbtReader.readCompound(this.initialNbt);
            for (String key : nbt.getKeys()) {
                NbtElement el = nbt.get(key);
                if (el == null) continue;

                String raw = el.toString();
                // Strip surrounding quotes from string values for cleaner display
                if (el.getType() == NbtElement.STRING_TYPE
                        && raw.startsWith("\"") && raw.endsWith("\"")) {
                    raw = raw.substring(1, raw.length() - 1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\");
                }

                this.listWidget.addNbtEntry(key, raw, (byte) el.getType());
                loaded++;
            }

            if (loaded > 0) {
                setStatus("Loaded " + loaded + " entries  |  " + editType + " : " + targetInfo, 0x55FF55);
            } else {
                setStatus("No entries found — NBT is empty.", 0xFFAA00);
            }
        } catch (Exception e) {
            setStatus("Parse error: " + e.getMessage(), 0xFF5555);
            System.err.println("[NbtEditor] Parse error: " + e.getMessage());
        }
        this.entryCount = this.listWidget.children().size();
    }

    // ── Filter list by search text ──
    private void onSearchChanged(String query) {
        // Rebuild the list with filtered entries
        // We keep the full entry list and just hide non-matching ones by re-adding
        // Simple approach: clear and re-populate with filter
        // For a proper implementation you'd keep a master list separate from the widget
        // This version just filters visually by rebuilding from scratch
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) {
            // Reload all
            this.listWidget.children().clear();
            populateEntries();
        } else {
            // Keep only matching keys
            var snapshot = List.copyOf(this.listWidget.children());
            this.listWidget.children().clear();
            for (var entry : snapshot) {
                if (entry.keyField.getText().toLowerCase().contains(q)) {
                    this.listWidget.addNbtEntry(
                        entry.keyField.getText(),
                        entry.valueField != null ? entry.valueField.getText() : "",
                        entry.checkboxWidget != null ? (byte) 1 : (byte) 8
                    );
                }
            }
        }
    }

    // ── Build NBT compound and send to server ──
    private void saveNbt() {
        try {
            NbtCompound newNbt = new NbtCompound();

            for (NbtListWidget.NbtEntry entry : this.listWidget.children()) {
                String key = entry.keyField.getText().trim();
                if (key.isEmpty()) continue;

                if (entry.checkboxWidget != null) {
                    // Boolean entry
                    newNbt.putBoolean(key, entry.checkboxWidget.isChecked());

                } else if (entry.valueField != null) {
                    String val = entry.valueField.getText().trim();

                    // Try to parse as typed NBT (handles 1b, 2L, 3.14f, {}, [] etc.)
                    boolean parsed = false;
                    try {
                        NbtCompound tmp = StringNbtReader.readCompound("{v:" + val + "}");
                        NbtElement el = tmp.get("v");
                        if (el != null) {
                            newNbt.put(key, el);
                            parsed = true;
                        }
                    } catch (Exception ignored) {}

                    if (!parsed) {
                        // Fall back to string
                        newNbt.putString(key, val);
                    }
                }
            }

            ClientPlayNetworking.send(
                    new SaveNbtPayload(this.editType, this.targetInfo, newNbt.toString()));
            setStatus("Saved " + newNbt.getKeys().size() + " entries.", 0x55FF55);

        } catch (Exception e) {
            setStatus("Save error: " + e.getMessage(), 0xFF5555);
            System.err.println("[NbtEditor] Save error: " + e.getMessage());
            return; // don't close on error
        }
        this.close();
    }

    private void setStatus(String msg, int color) {
        this.statusMessage = msg;
        this.statusColor   = color;
    }

    // ─────────────────────────────────────────────
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Fix: draw background first so the world doesn't show through
        this.renderBackground(context, mouseX, mouseY, delta);

        // ── Header panel ──
        context.fill(0, 0, this.width, HEADER_H, 0xCC111111);

        // Title
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("NBT Editor")
                        .append(Text.literal("  [" + editType + "]").formatted(Formatting.GRAY)),
                this.width / 2, 8, 0xFFFFFF);

        // Entry counter (top right)
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(this.entryCount + " entries").formatted(Formatting.GRAY),
                this.width - 80, 8, 0xAAAAAA);

        // Column headers (above the list)
        int headerLabelY = HEADER_H - 12;
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Key").formatted(Formatting.YELLOW), 8, headerLabelY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Type").formatted(Formatting.GRAY),
                this.width / 2 - 80, headerLabelY, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Value").formatted(Formatting.YELLOW),
                this.width / 2 - 56, headerLabelY, 0xFFFFFF);

        // Divider under header
        context.fill(0, HEADER_H, this.width, HEADER_H + 1, 0x55FFFFFF);

        // ── Footer panel ──
        int footerTop = this.height - FOOTER_H;
        context.fill(0, footerTop, this.width, this.height, 0xCC111111);

        // Divider above footer
        context.fill(0, footerTop, this.width, footerTop + 1, 0x55FFFFFF);

        // Status message
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(this.statusMessage),
                this.width / 2, this.height - 12, this.statusColor);

        // Target info line
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(this.targetInfo).formatted(Formatting.DARK_GRAY),
                4, this.height - 12, 0x666666);

        // ── Render widgets on top ──
        super.render(context, mouseX, mouseY, delta);
    }

    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.getKeycode() == GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyInput);
    }
}
