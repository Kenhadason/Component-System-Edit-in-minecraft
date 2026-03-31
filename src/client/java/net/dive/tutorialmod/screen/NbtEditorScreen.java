package net.dive.tutorialmod.screen;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.system.linux.X11.False;

/**
 * Full NBT editor screen.
 *
 * Layout:
 *   ┌─────────────────────────────────────────────────────┐
 *   │  HEADER  (title, target badge, entry count)         │
 *   ├──────────────┬──────────────────────────────────────┤
 *   │  CATEGORY    │  ENTRY LIST (scrollable)             │
 *   │  SIDEBAR     │  each row: icon │ key │ type │ value │
 *   │  (tabs)      │                                      │
 *   ├──────────────┴──────────────────────────────────────┤
 *   │  FOOTER  (search · add · save · cancel · status)   │
 *   └─────────────────────────────────────────────────────┘
 *
 * Data flow:
 *   Server reads live world data → sends pre-filled NBT via OpenNbtEditorPayload
 *   User edits → Save button → SendNbtPayload → server merges back into world
 */
public class NbtEditorScreen extends Screen {

    // ── Incoming data ─────────────────────────────────────────────
    private final String editType;
    private final String targetInfo;
    private final String initialNbt;

    // ── UI state ──────────────────────────────────────────────────
    private NbtListWidget listWidget;
    private TextFieldWidget searchField;
    private TextFieldWidget newKeyField;
    private TextFieldWidget newValueField;
    private ButtonWidget   newTypeButton;

    private String statusMessage = "";
    private int    statusColor   = 0xAAAAAA;
    private int    entryCount    = 0;
    private String activeCategory = "All";
    private String newTypeCycle  = "String";
    private static final String[] TYPE_CYCLE = {"String","Int","Float","Long","Boolean","List","Compound"};
    private int typeCycleIdx = 0;

    // ── Category tabs (populated from NBT keys) ───────────────────
    private final List<String> categories = new ArrayList<>();
    // Master list kept so search+filter can rebuild without re-parsing
    private final List<NbtListWidget.NbtEntry> masterEntries = new ArrayList<>();

    // ── Geometry ──────────────────────────────────────────────────
    private static final int HEADER_H  = 48;
    private static final int FOOTER_H  = 68;
    private static final int SIDEBAR_W = 88;
    private static final int ROW_H     = 22;

    // ── Colours ───────────────────────────────────────────────────
    // Header / footer panel
    private static final int COL_PANEL    = 0xE0101010;
    private static final int COL_DIVIDER  = 0x44FFFFFF;
    // Sidebar
    private static final int COL_SIDEBAR  = 0xE0181818;
    private static final int COL_TAB_ACT  = 0xFF222222;
    // Target badges
    private static final int COL_BLOCK_BG = 0xFF1A3A5C;
    private static final int COL_ITEM_BG  = 0xFF1A3D1A;
    private static final int COL_ENTITY_BG= 0xFF4A2E00;
    private static final int COL_BLOCK_TX = 0xFF74C0FC;
    private static final int COL_ITEM_TX  = 0xFF8CE88C;
    private static final int COL_ENTITY_TX= 0xFFFFBB55;
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
    private boolean expanded = false;

    public NbtEditorScreen(String editType, String targetInfo, String initialNbt) {
        super(Text.literal("NBT Editor"));
        this.editType   = editType;
        this.targetInfo = targetInfo;
        this.initialNbt = initialNbt;
    }



    @Override
     public boolean shouldPause() { return true; }

    // ─────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        super.init();

        int listLeft   = SIDEBAR_W;
        int listTop    = HEADER_H + 1;
        int listBottom = this.height - FOOTER_H;

        // ── List widget ──────────────────────────────────────────
        this.listWidget = new NbtListWidget(
                this.client, this.width - listLeft, listBottom, listTop, ROW_H, listLeft);
        parseAndPopulate();
        addDrawableChild(this.listWidget);

        // ── Category sidebar buttons ─────────────────────────────
        rebuildCategoryButtons();

        // ── Search field ─────────────────────────────────────────
        int fY = this.height - FOOTER_H + 8;
        this.searchField = new TextFieldWidget(
                textRenderer, SIDEBAR_W + 4, fY, 140, 16, Text.literal("Search"));
        this.searchField.setMaxLength(128);
        this.searchField.setPlaceholder(Text.literal("search keys…").formatted(Formatting.DARK_GRAY));
        this.searchField.setChangedListener(q -> applyFilter());
        addDrawableChild(this.searchField);

        // ── New-key / new-value / type / add row ─────────────────
        int addRowY = this.height - FOOTER_H + 30;
        this.newKeyField = new TextFieldWidget(
                textRenderer, SIDEBAR_W + 4, addRowY, 100, 16, Text.literal("key"));
        this.newKeyField.setPlaceholder(Text.literal("new_key").formatted(Formatting.DARK_GRAY));
        this.newKeyField.setMaxLength(256);
        addDrawableChild(this.newKeyField);

        this.newValueField = new TextFieldWidget(
                textRenderer, SIDEBAR_W + 110, addRowY, 120, 16, Text.literal("value"));
        this.newValueField.setPlaceholder(Text.literal("value").formatted(Formatting.DARK_GRAY));
        this.newValueField.setMaxLength(1024);
        addDrawableChild(this.newValueField);

        // Type cycle button
        this.newTypeButton = ButtonWidget.builder(
                Text.literal("[" + newTypeCycle + "]"),
                btn -> {
                    typeCycleIdx = (typeCycleIdx + 1) % TYPE_CYCLE.length;
                    newTypeCycle = TYPE_CYCLE[typeCycleIdx];
                    btn.setMessage(Text.literal("[" + newTypeCycle + "]"));
                }
        ).dimensions(SIDEBAR_W + 236, addRowY, 62, 16).build();
        addDrawableChild(this.newTypeButton);

        // Add button
        addDrawableChild(ButtonWidget.builder(
                Text.literal("+ Add"),
                btn -> addNewEntry()
        ).dimensions(SIDEBAR_W + 304, addRowY, 48, 16).build());

        // ── Bottom right: Save + Cancel ──────────────────────────
        int botY = this.height - FOOTER_H + 46;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Save").formatted(Formatting.GREEN),
                btn -> saveNbt()
        ).dimensions(this.width - 172, botY, 80, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel").formatted(Formatting.RED),
                btn -> this.close()
        ).dimensions(this.width - 86, botY, 80, 18).build());
        // ── Bottom left: Expand / Collapse / Clear ───────────────
        addDrawableChild(ButtonWidget.builder(
                Text.literal("▼ All"), btn -> {
                    masterEntries.forEach(e -> e.setExpanded(true));
                    applyFilter();
                }
        ).dimensions(SIDEBAR_W + 4, botY, 48, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("▶ All"), btn -> {
                    masterEntries.forEach(e -> e.setExpanded(false));
                    applyFilter();
                }
        ).dimensions(SIDEBAR_W + 58, botY, 48, 18).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Clear").formatted(Formatting.RED), btn -> {
                    masterEntries.clear();
                    applyFilter();
                    setStatus("Cleared all entries.", 0xFFAA00);
                }
        ).dimensions(SIDEBAR_W + 112, botY, 48, 18).build());
    }

    // ─────────────────────────────────────────────────────────────
    //  Parse incoming NBT → populate masterEntries + categories
    // ─────────────────────────────────────────────────────────────
    private void parseAndPopulate() {
        masterEntries.clear();
        categories.clear();
        categories.add("All");

        int loaded = 0;
        try {
            NbtCompound nbt = StringNbtReader.readCompound(this.initialNbt);
            for (String key : nbt.getKeys()) {
                NbtElement el = nbt.get(key);
                if (el == null) continue;

                byte type = (byte) el.getType();
                String raw = rawValueOf(el);
                String cat  = categoryOf(key, type);

                NbtListWidget.NbtEntry entry =
                        new NbtListWidget.NbtEntry(this.client, key, raw, type, listWidget);
                entry.category = cat;
                masterEntries.add(entry);

                if (!categories.contains(cat)) categories.add(cat);
                loaded++;
            }
            setStatus("Loaded " + loaded + " entries from " + editType + " [" + shortTarget() + "]", 0x55FF55);
        } catch (Exception e) {
            setStatus("Parse error: " + e.getMessage(), 0xFF5555);
        }
        this.entryCount = masterEntries.size();
        applyFilter();
    }

    // ─────────────────────────────────────────────────────────────
    //  Category sidebar — rebuild after parse
    // ─────────────────────────────────────────────────────────────
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();

    private void rebuildCategoryButtons() {
        categoryButtons.forEach(this::remove);
        categoryButtons.clear();

        int tabY = HEADER_H + 6;
        for (String cat : categories) {
            String label = cat.length() > 9 ? cat.substring(0, 8) + "…" : cat;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
                activeCategory = cat;
                applyFilter();
                rebuildCategoryButtons();
            }).dimensions(2, tabY + categoryButtons.size() * 20, SIDEBAR_W - 4, 18).build();
            btn.active = !cat.equals(activeCategory);
            addDrawableChild(btn);
            categoryButtons.add(btn);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Filter: category + search query
    // ─────────────────────────────────────────────────────────────
    private void applyFilter() {
        String q = searchField != null ? searchField.getText().toLowerCase().trim() : "";
        listWidget.children().clear();
        for (NbtListWidget.NbtEntry e : masterEntries) {
            boolean catMatch = activeCategory.equals("All") || e.category.equals(activeCategory);
            boolean qMatch   = q.isEmpty() || e.keyField.getText().toLowerCase().contains(q);
            if (catMatch && qMatch) listWidget.addEntryDirect(e);
        }
        entryCount = listWidget.children().size();
    }

    // ─────────────────────────────────────────────────────────────
    //  Add new entry from the footer row
    // ─────────────────────────────────────────────────────────────
    private void addNewEntry() {
        String key = newKeyField.getText().trim();
        String val = newValueField.getText().trim();
        if (key.isEmpty()) { setStatus("Key cannot be empty.", 0xFF5555); return; }

        byte type = typeNameToByte(newTypeCycle);
        String cat = categoryOf(key, type);

        NbtListWidget.NbtEntry entry =
                new NbtListWidget.NbtEntry(this.client, key, val, type, listWidget);
        entry.category = cat;
        masterEntries.add(entry);

        if (!categories.contains(cat)) {
            categories.add(cat);
            rebuildCategoryButtons();
        }

        newKeyField.setText("");
        newValueField.setText("");
        applyFilter();
        listWidget.setScrollY(Double.MAX_VALUE);
        entryCount = masterEntries.size();
        setStatus("Added: " + key, 0x55FF55);
    }

    // ─────────────────────────────────────────────────────────────
    //  Build compound + send payload
    // ─────────────────────────────────────────────────────────────
    private void saveNbt() {
        try {
            NbtCompound out = new NbtCompound();
            for (NbtListWidget.NbtEntry entry : masterEntries) {
                String key = entry.keyField.getText().trim();
                if (key.isEmpty()) continue;

                if (entry.checkboxWidget != null) {
                    out.putBoolean(key, entry.checkboxWidget.isChecked());
                } else if (entry.valueField != null) {
                    String val = entry.valueField.getText().trim();
                    boolean parsed = false;
                    try {
                        NbtCompound tmp = StringNbtReader.readCompound("{__v:" + val + "}");
                        NbtElement  el  = tmp.get("__v");
                        if (el != null) { out.put(key, el); parsed = true; }
                    } catch (Exception ignored) {}
                    if (!parsed) out.putString(key, val);
                }
            }
            ClientPlayNetworking.send(
                    new SaveNbtPayload(this.editType, this.targetInfo, out.toString()));
            setStatus("Saved " + out.getKeys().size() + " entries.", 0x55FF55);
        } catch (Exception e) {
            setStatus("Save error: " + e.getMessage(), 0xFF5555);
            return;
        }
        this.close();
    }

    // ─────────────────────────────────────────────────────────────
    //  Render
    // ─────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // ── Header ───────────────────────────────────────────────
        ctx.fill(0, 0, this.width, HEADER_H, COL_PANEL);
        ctx.fill(0, HEADER_H, this.width, HEADER_H + 1, COL_DIVIDER);

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("NBT Editor").formatted(Formatting.WHITE),
                this.width / 2, 7, 0xFFFFFF);

        // Target badge
        drawTargetBadge(ctx, this.width / 2 - 60, 20);

        // Entry counter top-right
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(entryCount + " entries").formatted(Formatting.GRAY),
                this.width - 70, 8, 0xAAAAAA);

        // Column headers
        int hY = HEADER_H - 13;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Key").formatted(Formatting.YELLOW),
                SIDEBAR_W + 30, hY, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Type").formatted(Formatting.GRAY),
                SIDEBAR_W + 160, hY, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Value").formatted(Formatting.YELLOW),
                SIDEBAR_W + 220, hY, 0xFFFFFF);

        // ── Sidebar ──────────────────────────────────────────────
        ctx.fill(0, HEADER_H + 1, SIDEBAR_W, this.height - FOOTER_H, COL_SIDEBAR);
        ctx.fill(SIDEBAR_W, HEADER_H + 1, SIDEBAR_W + 1, this.height - FOOTER_H, COL_DIVIDER);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Categories").formatted(Formatting.GRAY),
                4, HEADER_H + 4, 0x888888);

        // ── Footer ──────────────────────────────────────────────
        int fTop = this.height - FOOTER_H;
        ctx.fill(0, fTop, this.width, this.height, COL_PANEL);
        ctx.fill(0, fTop, this.width, fTop + 1, COL_DIVIDER);

        // Footer labels
        ctx.drawTextWithShadow(textRenderer, Text.literal("Search:").formatted(Formatting.GRAY),
                SIDEBAR_W + 4, fTop + 10, 0x888888);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Add row:").formatted(Formatting.GRAY),
                SIDEBAR_W + 4, fTop + 32, 0x888888);

        // Status
        ctx.drawTextWithShadow(textRenderer, Text.literal(statusMessage),
                SIDEBAR_W + 4, this.height - 10, statusColor);

        // ── Widgets on top ───────────────────────────────────────
        super.render(ctx, mx, my, delta);
    }

    private void drawTargetBadge(DrawContext ctx, int x, int y) {
        int bg, fg;
        String label;
        switch (editType) {
            case "block"  -> { bg = COL_BLOCK_BG;  fg = COL_BLOCK_TX;  label = "▣ Block · " + shortTarget(); }
            case "item"   -> { bg = COL_ITEM_BG;   fg = COL_ITEM_TX;   label = "⊞ Item · " + shortTarget(); }
            default       -> { bg = COL_ENTITY_BG; fg = COL_ENTITY_TX; label = "⊕ Entity · " + shortTarget(); }
        }
        int tw = textRenderer.getWidth(label) + 12;
        ctx.fill(x, y, x + tw, y + 14, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 6, y + 3, fg);
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────
    private String shortTarget() {
        if (editType.equals("entity") && targetInfo.length() > 8)
            return targetInfo.substring(0, 8) + "…";
        return targetInfo;
    }

    private void setStatus(String msg, int color) { statusMessage = msg; statusColor = color; }

    /** Human-readable value string from an NbtElement */
    private static String rawValueOf(NbtElement el) {
        String raw = el.toString();
        if (el.getType() == NbtElement.STRING_TYPE
                && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return raw;
    }

    /**
     * Group NBT keys into logical categories so the sidebar is useful.
     * Covers entities, block-entities, and item components.
     */
    private static String categoryOf(String key, byte type) {
        String k = key.toLowerCase();
        // Entity vitals
        if (k.equals("health") || k.equals("maxhealth") || k.equals("absorptionamount")
                || k.equals("deathtime") || k.equals("hurttime") || k.equals("fallflying"))
            return "Health";
        // Movement / physics
        if (k.equals("motion") || k.equals("pos") || k.equals("rotation")
                || k.equals("nogravity") || k.equals("onground") || k.equals("fallDistance"))
            return "Motion";
        // AI / behaviour
        if (k.equals("noai") || k.equals("persistencerequired") || k.equals("leashertarget")
                || k.equals("angertime") || k.equals("angertarget") || k.equals("brain"))
            return "AI";
        // Inventory / drops
        if (k.contains("item") || k.equals("handItems") || k.equals("armorItems")
                || k.contains("loot") || k.equals("inventory") || k.equals("equipment"))
            return "Inventory";
        // Effects / attributes
        if (k.equals("activeeffects") || k.equals("attributes") || k.equals("attribute_modifiers")
                || k.contains("effect") || k.contains("enchant") || k.contains("potion"))
            return "Effects";
        // Identity / display
        if (k.equals("customname") || k.equals("customnamevisible") || k.equals("glowing")
                || k.equals("silent") || k.equals("invulnerable") || k.equals("tags")
                || k.equals("uuid") || k.equals("id") || k.equals("type"))
            return "Identity";
        // Item display components
        if (k.equals("custom_name") || k.equals("item_name") || k.equals("lore")
                || k.equals("rarity") || k.equals("hide_tooltip"))
            return "Display";
        // Item durability
        if (k.equals("damage") || k.equals("max_damage") || k.equals("unbreakable")
                || k.equals("repair_cost"))
            return "Durability";
        // Block-entity container
        if (k.equals("burntime") || k.equals("cooktime") || k.equals("cooktimetotal")
                || k.equals("text1") || k.equals("text2") || k.equals("text3") || k.equals("text4")
                || k.equals("spawndata") || k.equals("spawncount") || k.equals("spawnrange"))
            return "Block";
        // Numeric primitives → Data
        if (type == NbtElement.INT_TYPE || type == NbtElement.LONG_TYPE
                || type == NbtElement.FLOAT_TYPE || type == NbtElement.DOUBLE_TYPE
                || type == NbtElement.SHORT_TYPE || type == NbtElement.BYTE_TYPE)
            return "Numbers";
        // Compound / list → Structure
        if (type == NbtElement.COMPOUND_TYPE || type == NbtElement.LIST_TYPE
                || type == NbtElement.INT_ARRAY_TYPE || type == NbtElement.LONG_ARRAY_TYPE)
            return "Structure";
        return "Other";
    }

    private static byte typeNameToByte(String name) {
        return switch (name) {
            case "Byte","Boolean" -> NbtElement.BYTE_TYPE;
            case "Short"          -> NbtElement.SHORT_TYPE;
            case "Int"            -> NbtElement.INT_TYPE;
            case "Long"           -> NbtElement.LONG_TYPE;
            case "Float"          -> NbtElement.FLOAT_TYPE;
            case "Double"         -> NbtElement.DOUBLE_TYPE;
            case "String"         -> NbtElement.STRING_TYPE;
            case "List"           -> NbtElement.LIST_TYPE;
            case "Compound"       -> NbtElement.COMPOUND_TYPE;
            default               -> NbtElement.STRING_TYPE;
        };
    }
}