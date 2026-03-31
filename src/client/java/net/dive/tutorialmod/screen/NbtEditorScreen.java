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
import java.util.List;

public class NbtEditorScreen extends Screen {

    // ── Incoming ──────────────────────────────────────────────────
    private final String editType;
    private final String targetInfo;
    private final String initialNbt;

    // ── Widgets ───────────────────────────────────────────────────
    private NbtListWidget            listWidget;
    private TextFieldWidget          searchField;
    private TextFieldWidget          newKeyField;
    private TextFieldWidget          newValueField;
    private ButtonWidget             newTypeButton;
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();

    // ── State ─────────────────────────────────────────────────────
    private String statusMessage  = "";
    private int    statusColor    = 0xAAAAAA;
    private int    entryCount     = 0;
    private String activeCategory = "All";
    private int    typeCycleIdx   = 0;

    private static final String[] TYPE_CYCLE =
            {"String", "Int", "Float", "Long", "Boolean", "Compound", "List"};

    private final List<NbtListWidget.NbtEntry> masterEntries = new ArrayList<>();
    private final List<String>                 categories    = new ArrayList<>();

    // ── Layout constants ──────────────────────────────────────────
    private static final int HEADER_H  = 48;
    private static final int FOOTER_H  = 68;
    private static final int SIDEBAR_W = 90;
    private static final int ROW_H     = 22;

    // ── Colours ───────────────────────────────────────────────────
    private static final int C_PANEL     = 0xE0101010;
    private static final int C_SIDEBAR   = 0xE0181818;
    private static final int C_DIVIDER   = 0x44FFFFFF;
    private static final int C_BLOCK_BG  = 0xFF1A3A5C;
    private static final int C_ITEM_BG   = 0xFF1A3D1A;
    private static final int C_ENTITY_BG = 0xFF4A2E00;
    private static final int C_BLOCK_TX  = 0xFF74C0FC;
    private static final int C_ITEM_TX   = 0xFF8CE88C;
    private static final int C_ENTITY_TX = 0xFFFFBB55;

    public NbtEditorScreen(String editType, String targetInfo, String initialNbt) {
        super(Text.literal("NBT Editor"));
        this.editType   = editType;
        this.targetInfo = targetInfo;
        this.initialNbt = initialNbt;
    }

    @Override
    public boolean shouldPause() { return true; }

    // ─────────────────────────────────────────────────────────────
    //  init
    // ─────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        super.init();

        int listLeft   = SIDEBAR_W + 1;
        int listTop    = HEADER_H + 1;
        int listBottom = this.height - FOOTER_H;
        int listW      = this.width - listLeft;

        this.listWidget = new NbtListWidget(
                this.client, listW, listBottom, listTop, ROW_H, listLeft);

        parseAndPopulate();
        addDrawableChild(this.listWidget);

        rebuildCategoryButtons();

        // ── Footer row 1: Search ──────────────────────────────────
        int fY1 = this.height - FOOTER_H + 8;
        this.searchField = new TextFieldWidget(
                textRenderer, listLeft + 56, fY1, 140, 16, Text.literal("Search"));
        this.searchField.setMaxLength(128);
        this.searchField.setPlaceholder(
                Text.literal("search keys…").formatted(Formatting.DARK_GRAY));
        this.searchField.setChangedListener(q -> applyFilter());
        addDrawableChild(this.searchField);

        // ── Footer row 2: Add-entry bar ───────────────────────────
        int fY2 = this.height - FOOTER_H + 28;

        this.newKeyField = new TextFieldWidget(
                textRenderer, listLeft, fY2, 110, 16, Text.literal("key"));
        this.newKeyField.setMaxLength(256);
        this.newKeyField.setPlaceholder(Text.literal("new_key").formatted(Formatting.DARK_GRAY));
        addDrawableChild(this.newKeyField);

        this.newValueField = new TextFieldWidget(
                textRenderer, listLeft + 116, fY2, 130, 16, Text.literal("value"));
        this.newValueField.setMaxLength(1024);
        this.newValueField.setPlaceholder(Text.literal("value").formatted(Formatting.DARK_GRAY));
        addDrawableChild(this.newValueField);

        this.newTypeButton = ButtonWidget.builder(
                Text.literal("[" + TYPE_CYCLE[typeCycleIdx] + "]"),
                btn -> {
                    typeCycleIdx = (typeCycleIdx + 1) % TYPE_CYCLE.length;
                    btn.setMessage(Text.literal("[" + TYPE_CYCLE[typeCycleIdx] + "]"));
                }
        ).dimensions(listLeft + 252, fY2, 70, 16).build();
        addDrawableChild(this.newTypeButton);

        addDrawableChild(ButtonWidget.builder(
                Text.literal("+ Add"), btn -> addNewEntry()
        ).dimensions(listLeft + 328, fY2, 50, 16).build());

        // ── Footer row 3: action buttons ─────────────────────────
        int fY3 = this.height - FOOTER_H + 48;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("▼ All"),
                btn -> { masterEntries.forEach(e -> e.expanded = true); applyFilter(); }
        ).dimensions(listLeft, fY3, 52, 16).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("▶ All"),
                btn -> { masterEntries.forEach(e -> e.expanded = false); applyFilter(); }
        ).dimensions(listLeft + 56, fY3, 52, 16).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Clear").formatted(Formatting.RED),
                btn -> { masterEntries.clear(); applyFilter();
                    setStatus("Cleared.", 0xFFAA00); }
        ).dimensions(listLeft + 112, fY3, 52, 16).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Save").formatted(Formatting.GREEN),
                btn -> saveNbt()
        ).dimensions(this.width - 170, fY3, 78, 16).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel").formatted(Formatting.RED),
                btn -> this.close()
        ).dimensions(this.width - 86, fY3, 78, 16).build());
    }

    // ─────────────────────────────────────────────────────────────
    //  Render
    //
    //  FIX: render order was wrong in the original.
    //
    //  In MC 1.21, Screen.super.render() fires the background blur
    //  pass internally. If you call renderBackground() manually
    //  before OR after super.render() you get two blur passes in
    //  the same frame → IllegalStateException "Can only blur once
    //  per frame" → crash.
    //
    //  Correct order:
    //   1. Draw opaque background panels FIRST (ctx.fill — no blur)
    //   2. Call super.render() — this is the ONE place blur fires,
    //      and it also renders all child widgets on top
    //   3. Draw any text/overlays that must sit above child widgets
    //
    //  Never call renderBackground() manually anywhere.
    // ─────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {

        // ── STEP 1: Draw opaque panels (no blur, safe before super) ─
        // Header
        ctx.fill(0, 0, this.width, HEADER_H, C_PANEL);
        ctx.fill(0, HEADER_H, this.width, HEADER_H + 1, C_DIVIDER);

        // Sidebar
        ctx.fill(0, HEADER_H + 1, SIDEBAR_W, this.height - FOOTER_H, C_SIDEBAR);
        ctx.fill(SIDEBAR_W, HEADER_H + 1, SIDEBAR_W + 1,
                this.height - FOOTER_H, C_DIVIDER);

        // Footer
        int fTop = this.height - FOOTER_H;
        ctx.fill(0, fTop, this.width, this.height, C_PANEL);
        ctx.fill(0, fTop, this.width, fTop + 1, C_DIVIDER);

        // ── STEP 2: super.render() — fires blur ONCE, renders children ─
        super.render(ctx, mx, my, delta);

        // ── STEP 3: Text drawn above child widgets ─────────────────
        ctx.drawCenteredTextWithShadow(
                textRenderer, Text.literal("NBT Editor"), this.width / 2, 8, 0xFFFFFF);

        drawTargetBadge(ctx, this.width / 2 - 70, 22);

        ctx.drawTextWithShadow(textRenderer,
                Text.literal(entryCount + " entries").formatted(Formatting.GRAY),
                this.width - 72, 8, 0xAAAAAA);

        int hdrY = HEADER_H - 13;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Key").formatted(Formatting.YELLOW),
                SIDEBAR_W + 30, hdrY, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Type").formatted(Formatting.GRAY),
                SIDEBAR_W + 152, hdrY, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Value").formatted(Formatting.YELLOW),
                SIDEBAR_W + 210, hdrY, 0xFFFFFF);

        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Filter").formatted(Formatting.GRAY),
                4, HEADER_H + 5, 0x777777);

        int listLeft = SIDEBAR_W + 1;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Search:").formatted(Formatting.GRAY),
                listLeft, this.height - FOOTER_H + 11, 0x888888);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Add:").formatted(Formatting.GRAY),
                listLeft, this.height - FOOTER_H + 31, 0x888888);

        // Status line
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(statusMessage),
                listLeft, this.height - 9, statusColor);
    }

    // ─────────────────────────────────────────────────────────────
    //  Target badge
    // ─────────────────────────────────────────────────────────────
    private void drawTargetBadge(DrawContext ctx, int x, int y) {
        int    bg, fg;
        String label;
        switch (editType) {
            case "block"  -> { bg = C_BLOCK_BG;  fg = C_BLOCK_TX;
                label = "Block  "  + shortTarget(); }
            case "item"   -> { bg = C_ITEM_BG;   fg = C_ITEM_TX;
                label = "Item  "   + shortTarget(); }
            default       -> { bg = C_ENTITY_BG; fg = C_ENTITY_TX;
                label = "Entity  " + shortTarget(); }
        }
        int tw = textRenderer.getWidth(label) + 14;
        ctx.fill(x, y, x + tw, y + 13, bg);
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), x + 7, y + 3, fg);
    }

    // ─────────────────────────────────────────────────────────────
    //  Parse NBT → master list + categories
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

                byte   type = (byte) el.getType();
                String raw  = rawValueOf(el);
                String cat  = categoryOf(key, type);

                NbtListWidget.NbtEntry entry =
                        new NbtListWidget.NbtEntry(this.client, key, raw, type, listWidget);
                entry.category = cat;
                masterEntries.add(entry);

                if (!categories.contains(cat)) categories.add(cat);
                loaded++;
            }
            setStatus("Loaded " + loaded + " entries — " + editType
                    + " [" + shortTarget() + "]", 0x55FF55);
        } catch (Exception e) {
            setStatus("Parse error: " + e.getMessage(), 0xFF5555);
            System.err.println("[NbtEditor] Parse error: " + e.getMessage());
        }

        this.entryCount = masterEntries.size();
        applyFilter();
    }

    // ─────────────────────────────────────────────────────────────
    //  Category sidebar
    // ─────────────────────────────────────────────────────────────
    private void rebuildCategoryButtons() {
        categoryButtons.forEach(this::remove);
        categoryButtons.clear();

        int tabY = HEADER_H + 18;
        for (int i = 0; i < categories.size(); i++) {
            final String cat   = categories.get(i);
            String       label = cat.length() > 9 ? cat.substring(0, 8) + "…" : cat;
            ButtonWidget btn = ButtonWidget.builder(Text.literal(label), b -> {
                activeCategory = cat;
                applyFilter();
                rebuildCategoryButtons();
            }).dimensions(2, tabY + i * 19, SIDEBAR_W - 4, 17).build();
            btn.active = !cat.equals(activeCategory);
            addDrawableChild(btn);
            categoryButtons.add(btn);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Filter
    //
    //  FIX: the original called listWidget.children().clear() which
    //  mutates ElementListWidget's internal list directly and causes
    //  ConcurrentModificationException when called during iteration
    //  (e.g. from the search field's change listener mid-render).
    //  Use the safe clearEntries() method instead.
    // ─────────────────────────────────────────────────────────────
    private void applyFilter() {
        String q = (searchField != null)
                ? searchField.getText().toLowerCase().trim() : "";

        // FIX: safe clear via the public helper, not children().clear()
        listWidget.clearEntries();

        for (NbtListWidget.NbtEntry e : masterEntries) {
            boolean catOk = activeCategory.equals("All")
                    || e.category.equals(activeCategory);
            boolean qOk   = q.isEmpty()
                    || e.keyField.getText().toLowerCase().contains(q);
            if (catOk && qOk) listWidget.addEntryDirect(e);
        }
        entryCount = listWidget.children().size();
    }

    // ─────────────────────────────────────────────────────────────
    //  Add new entry
    // ─────────────────────────────────────────────────────────────
    private void addNewEntry() {
        String key = newKeyField.getText().trim();
        String val = newValueField.getText().trim();
        if (key.isEmpty()) { setStatus("Key cannot be empty.", 0xFF5555); return; }

        byte   type = typeNameToByte(TYPE_CYCLE[typeCycleIdx]);
        String cat  = categoryOf(key, type);

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
        setStatus("Added \"" + key + "\".", 0x55FF55);
    }

    // ─────────────────────────────────────────────────────────────
    //  Save → send to server
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
                    String  raw   = entry.valueField.getText().trim();
                    boolean saved = false;
                    try {
                        NbtCompound tmp = StringNbtReader.readCompound("{__v:" + raw + "}");
                        NbtElement  el  = tmp.get("__v");
                        if (el != null) { out.put(key, el); saved = true; }
                    } catch (Exception ignored) {}
                    if (!saved) out.putString(key, raw);
                }
            }

            ClientPlayNetworking.send(
                    new SaveNbtPayload(this.editType, this.targetInfo, out.toString()));
            setStatus("Sent " + out.getKeys().size() + " keys to server.", 0x55FF55);
        } catch (Exception e) {
            setStatus("Save error: " + e.getMessage(), 0xFF5555);
            System.err.println("[NbtEditor] Save error: " + e.getMessage());
            return;
        }
        this.close();
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────
    private String shortTarget() {
        if (editType.equals("entity") && targetInfo.length() > 8)
            return targetInfo.substring(0, 8) + "…";
        return targetInfo;
    }

    private void setStatus(String msg, int color) {
        statusMessage = msg;
        statusColor   = color;
    }

    private static String rawValueOf(NbtElement el) {
        String raw = el.toString();
        if (el.getType() == NbtElement.STRING_TYPE
                && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return raw;
    }

    private static String categoryOf(String key, byte type) {
        String k = key.toLowerCase();

        if (k.equals("health") || k.equals("maxhealth") || k.equals("movementspeed")
                || k.equals("attackdamage") || k.equals("attackspeed")
                || k.equals("armor") || k.equals("armortoughness")
                || k.equals("knockbackresistance") || k.equals("absorptionamount")
                || k.equals("deathtime") || k.equals("hurttime") || k.equals("gravity")
                || k.equals("luck") || k.equals("followrange") || k.equals("stepheight")
                || k.equals("flyingspeed") || k.equals("attackknockback")
                || k.equals("entityinteractionrange") || k.equals("blockinteractionrange")
                || k.equals("oxygenbonus"))
            return "Stats";

        if (k.equals("motion") || k.equals("pos") || k.equals("rotation")
                || k.equals("nogravity") || k.equals("onground") || k.equals("falldistance"))
            return "Motion";

        if (k.equals("noai") || k.equals("persistencerequired") || k.equals("angertime")
                || k.equals("angertarget") || k.equals("brain") || k.equals("leashertarget"))
            return "AI";

        if (k.contains("item") || k.equals("handitems") || k.equals("armoritems")
                || k.contains("loot") || k.equals("inventory") || k.equals("equipment"))
            return "Inventory";

        if (k.equals("activeeffects") || k.equals("attributes")
                || k.contains("effect") || k.contains("enchant") || k.contains("potion"))
            return "Effects";

        if (k.equals("customname") || k.equals("customnamevisible") || k.equals("glowing")
                || k.equals("silent") || k.equals("invulnerable") || k.equals("tags")
                || k.equals("uuid") || k.equals("id") || k.equals("type"))
            return "Identity";

        if (k.equals("custom_name") || k.equals("item_name") || k.equals("lore")
                || k.equals("rarity") || k.equals("hide_tooltip"))
            return "Display";

        if (k.equals("damage") || k.equals("max_damage") || k.equals("unbreakable")
                || k.equals("repair_cost"))
            return "Durability";

        if (k.equals("burntime") || k.equals("cooktime") || k.equals("cooktimetotal")
                || k.equals("text1") || k.equals("text2") || k.equals("text3") || k.equals("text4")
                || k.equals("spawndata") || k.equals("spawncount") || k.equals("spawnrange"))
            return "Block";

        if (type == NbtElement.COMPOUND_TYPE || type == NbtElement.LIST_TYPE
                || type == NbtElement.INT_ARRAY_TYPE || type == NbtElement.LONG_ARRAY_TYPE
                || type == NbtElement.BYTE_ARRAY_TYPE)
            return "Structure";

        return "Other";
    }

    private static byte typeNameToByte(String name) {
        return switch (name) {
            case "Boolean"  -> NbtElement.BYTE_TYPE;
            case "Int"      -> NbtElement.INT_TYPE;
            case "Long"     -> NbtElement.LONG_TYPE;
            case "Float"    -> NbtElement.FLOAT_TYPE;
            case "Double"   -> NbtElement.DOUBLE_TYPE;
            case "List"     -> NbtElement.LIST_TYPE;
            case "Compound" -> NbtElement.COMPOUND_TYPE;
            default         -> NbtElement.STRING_TYPE;
        };
    }
}