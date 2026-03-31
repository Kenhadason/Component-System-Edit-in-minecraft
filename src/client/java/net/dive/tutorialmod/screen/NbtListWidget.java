package net.dive.tutorialmod.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class NbtListWidget extends ElementListWidget<NbtListWidget.NbtEntry> {

    private final int offsetX;

    public NbtListWidget(MinecraftClient client, int width, int height,
                         int top, int itemHeight, int offsetX) {
        super(client, width, height, top, itemHeight);
        this.offsetX = offsetX;
        // FIX: tell ElementListWidget where its left edge actually is.
        // Without this, scrollbar math produces a negative coordinate
        // that corrupts render state → "blur once per frame" crash.
        this.setX(offsetX);
    }

    // ── Public API ─────────────────────────────────────────────────
    public void addNbtEntry(String key, String value, byte nbtType) {
        addEntry(new NbtEntry(this.client, key, value, nbtType, this));
    }

    public void addEntryDirect(NbtEntry entry) {
        addEntry(entry);
    }

    @Override
    public void removeEntry(NbtEntry entry) {
        super.removeEntry(entry);
    }

    // FIX: safe clear — replaces direct children().clear() calls from
    // NbtEditorScreen.applyFilter(), which mutated the internal list
    // during iteration and caused ConcurrentModificationException.
    public void clearEntries() {
        super.clearEntries();
    }

    // ── Layout ─────────────────────────────────────────────────────
    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    protected int getScrollbarPositionX() {
        return this.offsetX + this.width - 6;
    }

    // ══════════════════════════════════════════════════════════════
    //  NbtEntry — one row in the list
    // ══════════════════════════════════════════════════════════════
    public static class NbtEntry extends ElementListWidget.Entry<NbtEntry> {

        public final TextFieldWidget keyField;
        public final TextFieldWidget valueField;
        public final CheckboxWidget  checkboxWidget;
        public final ButtonWidget    deleteButton;
        public       String          category = "Other";
        public       boolean         expanded = false;

        private final MinecraftClient client;
        private final byte            nbtType;
        private final NbtListWidget   parent;
        private       ButtonWidget    expandBtn;

        private final List<ChildRow> childRows = new ArrayList<>();

        public NbtEntry(MinecraftClient client, String key, String value,
                        byte nbtType, NbtListWidget parent) {
            this.client  = client;
            this.nbtType = nbtType;
            this.parent  = parent;

            this.keyField = new TextFieldWidget(
                    client.textRenderer, 0, 0, 112, 16, Text.literal(key));
            this.keyField.setMaxLength(256);
            this.keyField.setText(key);
            this.keyField.setEditableColor(0xFFFF88);

            this.deleteButton = ButtonWidget.builder(
                    Text.literal("✕").formatted(Formatting.RED),
                    btn -> parent.removeEntry(this)
            ).dimensions(0, 0, 16, 16).build();

            boolean isBool = (nbtType == NbtElement.BYTE_TYPE)
                    && (value.equals("true") || value.equals("false")
                    || value.equals("1b")  || value.equals("0b"));

            if (isBool) {
                boolean on = value.equals("true") || value.equals("1b");
                this.checkboxWidget = CheckboxWidget.builder(Text.literal(""), client.textRenderer)
                        .checked(on).build();
                this.valueField = null;
            } else {
                this.valueField = new TextFieldWidget(
                        client.textRenderer, 0, 0, 190, 16, Text.literal(value));
                this.valueField.setMaxLength(8192);
                this.valueField.setText(value);
                this.valueField.setEditableColor(0x88FF88);
                this.checkboxWidget = null;
            }

            if (nbtType == NbtElement.COMPOUND_TYPE || nbtType == NbtElement.LIST_TYPE) {
                this.expandBtn = ButtonWidget.builder(
                        Text.literal("▶"),
                        btn -> {
                            expanded = !expanded;
                            btn.setMessage(Text.literal(expanded ? "▼" : "▶"));
                        }
                ).dimensions(0, 0, 16, 16).build();
                parseChildRows(value);
            }
        }

        private void parseChildRows(String raw) {
            try {
                NbtCompound wrapper =
                        StringNbtReader.readCompound("{__r:" + raw + "}");
                NbtElement root = wrapper.get("__r");
                if (root instanceof NbtCompound comp) {
                    for (String k : comp.getKeys()) {
                        NbtElement el = comp.get(k);
                        childRows.add(new ChildRow(k, el != null ? el.toString() : ""));
                    }
                } else if (root instanceof NbtList list) {
                    for (int i = 0; i < list.size(); i++) {
                        childRows.add(new ChildRow("[" + i + "]", list.get(i).toString()));
                    }
                }
            } catch (Exception ignored) {}
        }

        // ── Render ────────────────────────────────────────────────
        // FIX: There is exactly ONE render() override here.
        // The original code had a second empty override at the bottom
        // with a slightly different signature — the JVM was picking that
        // one in some call paths, resulting in nothing being drawn and
        // the framework retrying the blur → crash.
        public void render(DrawContext ctx, int index, int y, int x,
                           int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {

            int midY   = y + entryHeight / 2;
            int fieldY = midY - 8;

            int badgeX  = x + 2;
            int keyX    = badgeX + 24;
            int typeX   = keyX + 116;
            int valX    = typeX + 44;
            int expandX = x + entryWidth - 38;
            int delX    = x + entryWidth - 18;

            if (index % 2 == 0) ctx.fill(x, y, x + entryWidth, y + entryHeight, 0x0AFFFFFF);
            if (hovered)        ctx.fill(x, y, x + entryWidth, y + entryHeight, 0x18FFFFFF);

            ctx.fill(badgeX, fieldY, badgeX + 20, fieldY + 16, badgeBg());
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(typeShort()), badgeX + 10, fieldY + 4, badgeFg());

            keyField.setX(keyX);
            keyField.setY(fieldY);
            keyField.render(ctx, mouseX, mouseY, delta);

            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(typeFull()).formatted(Formatting.GRAY),
                    typeX, midY - 4, typeFg());

            int maxValW = (expandBtn != null ? expandX : delX) - valX - 4;

            if (checkboxWidget != null) {
                checkboxWidget.setX(valX);
                checkboxWidget.setY(fieldY);
                checkboxWidget.render(ctx, mouseX, mouseY, delta);
                boolean on  = checkboxWidget.isChecked();
                ctx.drawTextWithShadow(client.textRenderer,
                        Text.literal(on ? "true" : "false"),
                        valX + 20, midY - 4, on ? 0x55FF55 : 0xFF5555);
            } else if (valueField != null) {
                valueField.setWidth(Math.max(40, maxValW));
                valueField.setX(valX);
                valueField.setY(fieldY);
                valueField.render(ctx, mouseX, mouseY, delta);
            }

            if (expandBtn != null) {
                expandBtn.setX(expandX);
                expandBtn.setY(fieldY);
                expandBtn.render(ctx, mouseX, mouseY, delta);
            }

            deleteButton.setX(delX);
            deleteButton.setY(fieldY);
            deleteButton.render(ctx, mouseX, mouseY, delta);

            if (expanded && !childRows.isEmpty()) {
                int cy = y + entryHeight;
                for (ChildRow cr : childRows) {
                    ctx.fill(x, cy, x + entryWidth, cy + 17, 0x22FFFFFF);
                    ctx.fill(x + 6, cy + 2, x + 8, cy + 15, 0x33FFFFFF);
                    ctx.drawTextWithShadow(client.textRenderer,
                            Text.literal(cr.key()).formatted(Formatting.AQUA),
                            x + 12, cy + 5, 0x55DDFF);
                    ctx.drawTextWithShadow(client.textRenderer,
                            Text.literal(cap(cr.value(), 50)),
                            x + 12 + 118, cy + 5, 0xCCCCCC);
                    cy += 17;
                }
            }
        }

        // ── Element / Selectable lists ────────────────────────────
        @Override
        public List<? extends Element> children() {
            List<Element> list = new ArrayList<>();
            list.add(keyField);
            if (checkboxWidget != null) list.add(checkboxWidget);
            if (valueField     != null) list.add(valueField);
            if (expandBtn      != null) list.add(expandBtn);
            list.add(deleteButton);
            return list;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            List<Selectable> list = new ArrayList<>();
            list.add(keyField);
            if (checkboxWidget != null) list.add(checkboxWidget);
            if (valueField     != null) list.add(valueField);
            if (expandBtn      != null) list.add(expandBtn);
            list.add(deleteButton);
            return list;
        }

        // ── Type display helpers ──────────────────────────────────
        private String typeShort() {
            return switch (nbtType) {
                case NbtElement.BYTE_TYPE       -> "b";
                case NbtElement.SHORT_TYPE      -> "s";
                case NbtElement.INT_TYPE        -> "i";
                case NbtElement.LONG_TYPE       -> "L";
                case NbtElement.FLOAT_TYPE      -> "f";
                case NbtElement.DOUBLE_TYPE     -> "d";
                case NbtElement.STRING_TYPE     -> "St";
                case NbtElement.LIST_TYPE       -> "[]";
                case NbtElement.COMPOUND_TYPE   -> "{}";
                case NbtElement.INT_ARRAY_TYPE  -> "I[";
                case NbtElement.LONG_ARRAY_TYPE -> "L[";
                case NbtElement.BYTE_ARRAY_TYPE -> "B[";
                default                         -> "?";
            };
        }

        private String typeFull() {
            return switch (nbtType) {
                case NbtElement.BYTE_TYPE       -> "byte";
                case NbtElement.SHORT_TYPE      -> "short";
                case NbtElement.INT_TYPE        -> "int";
                case NbtElement.LONG_TYPE       -> "long";
                case NbtElement.FLOAT_TYPE      -> "float";
                case NbtElement.DOUBLE_TYPE     -> "double";
                case NbtElement.STRING_TYPE     -> "string";
                case NbtElement.LIST_TYPE       -> "list";
                case NbtElement.COMPOUND_TYPE   -> "compound";
                case NbtElement.INT_ARRAY_TYPE  -> "int[]";
                case NbtElement.LONG_ARRAY_TYPE -> "long[]";
                case NbtElement.BYTE_ARRAY_TYPE -> "byte[]";
                default                         -> "?";
            };
        }

        private int badgeBg() {
            return switch (nbtType) {
                case NbtElement.BYTE_TYPE     -> 0xFF5C2A00;
                case NbtElement.SHORT_TYPE    -> 0xFF3A2A00;
                case NbtElement.INT_TYPE      -> 0xFF002855;
                case NbtElement.LONG_TYPE     -> 0xFF2A0055;
                case NbtElement.FLOAT_TYPE    -> 0xFF003A1A;
                case NbtElement.DOUBLE_TYPE   -> 0xFF003A2A;
                case NbtElement.STRING_TYPE   -> 0xFF3A2800;
                case NbtElement.LIST_TYPE     -> 0xFF002040;
                case NbtElement.COMPOUND_TYPE -> 0xFF3A0028;
                default                       -> 0xFF222222;
            };
        }

        private int badgeFg() {
            return switch (nbtType) {
                case NbtElement.BYTE_TYPE     -> 0xFFFF9944;
                case NbtElement.SHORT_TYPE    -> 0xFFFFCC44;
                case NbtElement.INT_TYPE      -> 0xFF74C0FC;
                case NbtElement.LONG_TYPE     -> 0xFFCC99FF;
                case NbtElement.FLOAT_TYPE    -> 0xFF66FF99;
                case NbtElement.DOUBLE_TYPE   -> 0xFF44FFCC;
                case NbtElement.STRING_TYPE   -> 0xFFFFEE44;
                case NbtElement.LIST_TYPE     -> 0xFF55BBFF;
                case NbtElement.COMPOUND_TYPE -> 0xFFFF77CC;
                default                       -> 0xFFAAAAAA;
            };
        }

        private int typeFg() {
            return switch (nbtType) {
                case NbtElement.INT_TYPE      -> 0xFF74C0FC;
                case NbtElement.LONG_TYPE     -> 0xFFCC99FF;
                case NbtElement.FLOAT_TYPE    -> 0xFF66FF99;
                case NbtElement.DOUBLE_TYPE   -> 0xFF44FFCC;
                case NbtElement.STRING_TYPE   -> 0xFFFFEE44;
                case NbtElement.COMPOUND_TYPE -> 0xFFFF77CC;
                case NbtElement.LIST_TYPE     -> 0xFF55BBFF;
                default                       -> 0xFFAAAAAA;
            };
        }

        private static String cap(String s, int max) {
            return s.length() > max ? s.substring(0, max) + "…" : s;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {

        }

        private record ChildRow(String key, String value) {}
    }
}