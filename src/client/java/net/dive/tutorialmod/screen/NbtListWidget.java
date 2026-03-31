package net.dive.tutorialmod.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable list of NBT entries.
 *
 * Each row shows:
 *   [TYPE BADGE]  key-field          type-label  value-field / toggle  [X]
 *
 * Compound / List entries show an expand chevron; when expanded, child
 * entries render indented below.
 *
 * Offset is the left margin so the sidebar doesn't overlap.
 */
public class NbtListWidget extends ElementListWidget<NbtListWidget.NbtEntry> {

    private final int offsetX;

    public NbtListWidget(MinecraftClient client, int width, int height,
                         int y, int itemHeight, int offsetX) {
        super(client, width, height, y, itemHeight);
        this.offsetX = offsetX;
    }

    public void addNbtEntry(String key, String value, byte nbtType) {
        this.addEntry(new NbtEntry(this.client, key, value, nbtType, this));
    }

    /** Used by the filter/category system — adds a pre-built entry directly. */
    public void addEntryDirect(NbtEntry entry) {
        this.addEntry(entry);
    }

    @Override
    public void removeEntry(NbtEntry entry) {
        super.removeEntry(entry);
    }

    @Override public int getRowWidth()            { return this.width - 20; }
    protected int getScrollbarPositionX()         { return this.width + this.offsetX - 8; }

    // ─────────────────────────────────────────────────────────────
    public static class NbtEntry extends Entry<NbtEntry> {

        // ── Exposed fields (NbtEditorScreen reads these) ──────────
        public final TextFieldWidget  keyField;
        public final TextFieldWidget  valueField;    // null for boolean
        public final CheckboxWidget   checkboxWidget; // null for non-boolean
        public final ButtonWidget     deleteButton;
        public       String           category = "Other";
        public       boolean          expanded  = false;

        private final MinecraftClient client;
        private final byte            nbtType;
        private final NbtListWidget   parent;

        // Child entries for Compound / List display
        private final List<ChildRow> children2 = new ArrayList<>();
        private ButtonWidget expandButton;

        public NbtEntry(MinecraftClient client, String key, String value,
                        byte nbtType, NbtListWidget parent) {
            this.client  = client;
            this.nbtType = nbtType;
            this.parent  = parent;

            // ── Key field ─────────────────────────────────────────
            this.keyField = new TextFieldWidget(
                    client.textRenderer, 0, 0, 110, 16, Text.literal(key));
            this.keyField.setMaxLength(256);
            this.keyField.setText(key);
            this.keyField.setEditableColor(0xFFFFAA);   // yellow keys

            // ── Delete button ─────────────────────────────────────
            this.deleteButton = ButtonWidget.builder(
                    Text.literal("✕").formatted(Formatting.RED),
                    btn -> parent.removeEntry(this)
            ).dimensions(0, 0, 16, 16).build();

            // ── Determine boolean ─────────────────────────────────
            boolean isBoolean = nbtType == NbtElement.BYTE_TYPE
                    && (value.equals("true") || value.equals("false")
                    || value.equals("1b") || value.equals("0b"));

            if (isBoolean) {
                boolean checked = value.equals("true") || value.equals("1b");
                this.checkboxWidget = CheckboxWidget.builder(Text.literal(""), client.textRenderer)
                        .checked(checked).build();
                this.valueField = null;
            } else {
                this.valueField = new TextFieldWidget(
                        client.textRenderer, 0, 0, 180, 16, Text.literal(value));
                this.valueField.setMaxLength(4096);
                this.valueField.setText(value);
                this.valueField.setEditableColor(0xAAFFAA); // green values
                this.checkboxWidget = null;
            }

            // ── Expand button for Compound / List ─────────────────
            if (nbtType == NbtElement.COMPOUND_TYPE || nbtType == NbtElement.LIST_TYPE) {
                this.expandButton = ButtonWidget.builder(
                        Text.literal("▶"),
                        btn -> {
                            expanded = !expanded;
                            btn.setMessage(Text.literal(expanded ? "▼" : "▶"));
                        }
                ).dimensions(0, 0, 16, 16).build();
            } else {
                this.expandButton = null;
            }

            // Parse child entries from compound/list value string
            if (nbtType == NbtElement.COMPOUND_TYPE || nbtType == NbtElement.LIST_TYPE) {
                parseChildren(value);
            }
        }

        /** Best-effort parse of compound/list value into displayable child rows. */
        private void parseChildren(String raw) {
            try {
                NbtCompound tmp =
                        StringNbtReader.readCompound("{__root:" + raw + "}");
                NbtElement root = tmp.get("__root");
                if (root instanceof NbtCompound comp) {
                    for (String k : comp.getKeys()) {
                        NbtElement el = comp.get(k);
                        children2.add(new ChildRow(k, el != null ? el.toString() : ""));
                    }
                } else if (root instanceof NbtList list) {
                    for (int i = 0; i < list.size(); i++) {
                        children2.add(new ChildRow("[" + i + "]", list.get(i).toString()));
                    }
                }
            } catch (Exception ignored) {}
        }

        //  Render
        public void render(DrawContext ctx, int index, int y, int x,
                           int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {

            int midY   = y + entryHeight / 2;
            int fieldY = midY - 8;

            // Layout
            int badgeX  = x + 2;
            int keyX    = badgeX + 26;
            int typeX   = keyX + 116;
            int valueX  = typeX + 46;
            int delX    = x + entryWidth - 20;
            int expandX = delX - 20;

            // ── Alternating row bg ────────────────────────────────
            if (index % 2 == 0) ctx.fill(x, y, x + entryWidth, y + entryHeight, 0x0AFFFFFF);

            // ── Hover highlight ───────────────────────────────────
            if (hovered)        ctx.fill(x, y, x + entryWidth, y + entryHeight, 0x18FFFFFF);

            // ── Type badge ────────────────────────────────────────
            int badgeColor = getBadgeColor();
            ctx.fill(badgeX, fieldY, badgeX + 22, fieldY + 16, badgeColor);
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal(getTypeShort()),
                    badgeX + 11, fieldY + 4, getBadgeTextColor());

            // ── Key field ─────────────────────────────────────────
            keyField.setX(keyX); keyField.setY(fieldY);
            keyField.render(ctx, mouseX, mouseY, delta);

            // ── Type label ────────────────────────────────────────
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(getTypeFull()).formatted(Formatting.GRAY),
                    typeX, midY - 4, getTypeTextColor());

            // ── Value: checkbox or text field ─────────────────────
            int maxValW = (expandButton != null ? expandX : delX) - valueX - 6;
            if (checkboxWidget != null) {
                checkboxWidget.setX(valueX); checkboxWidget.setY(fieldY);
                checkboxWidget.render(ctx, mouseX, mouseY, delta);
                // Live true/false label beside toggle
                String boolLabel = checkboxWidget.isChecked() ? "true" : "false";
                int boolColor    = checkboxWidget.isChecked() ? 0x55FF55 : 0xFF5555;
                ctx.drawTextWithShadow(client.textRenderer,
                        Text.literal(boolLabel), valueX + 22, midY - 4, boolColor);
            } else if (valueField != null) {
                valueField.setWidth(Math.max(40, maxValW));
                valueField.setX(valueX); valueField.setY(fieldY);
                valueField.render(ctx, mouseX, mouseY, delta);
            }

            // ── Expand button ─────────────────────────────────────
            if (expandButton != null) {
                expandButton.setX(expandX); expandButton.setY(fieldY);
                expandButton.render(ctx, mouseX, mouseY, delta);
            }

            // ── Delete button ─────────────────────────────────────
            deleteButton.setX(delX); deleteButton.setY(fieldY);
            deleteButton.render(ctx, mouseX, mouseY, delta);

            // ── Child rows (when expanded) ────────────────────────
            if (expanded && !children2.isEmpty()) {
                int cY = y + entryHeight;
                for (ChildRow cr : children2) {
                    ctx.fill(x, cY, x + entryWidth, cY + 18, 0x22FFFFFF);
                    // Indent line
                    ctx.fill(x + 6, cY + 2, x + 8, cY + 16, 0x44FFFFFF);
                    ctx.drawTextWithShadow(client.textRenderer,
                            Text.literal(cr.key).formatted(Formatting.AQUA),
                            x + 14, cY + 5, 0x55DDFF);
                    ctx.drawTextWithShadow(client.textRenderer,
                            Text.literal(truncate(cr.value, 40)).formatted(Formatting.WHITE),
                            x + 14 + 110, cY + 5, 0xCCCCCC);
                    cY += 18;
                }
            }
        }

        // ─────────────────────────────────────────────────────────
        //  Children / selectables
        // ─────────────────────────────────────────────────────────
        @Override
        public List<? extends Element> children() {
            List<Element> list = new ArrayList<>();
            list.add(keyField);
            if (checkboxWidget  != null) list.add(checkboxWidget);
            if (valueField      != null) list.add(valueField);
            if (expandButton    != null) list.add(expandButton);
            list.add(deleteButton);
            return list;
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            List<Selectable> list = new ArrayList<>();
            list.add(keyField);
            if (checkboxWidget  != null) list.add(checkboxWidget);
            if (valueField      != null) list.add(valueField);
            if (expandButton    != null) list.add(expandButton);
            list.add(deleteButton);
            return list;
        }

        // ─────────────────────────────────────────────────────────
        //  Type display helpers
        // ─────────────────────────────────────────────────────────
        private String getTypeShort() {
            return switch (nbtType) {
                case NbtElement.BYTE_TYPE       -> "b";
                case NbtElement.SHORT_TYPE      -> "s";
                case NbtElement.INT_TYPE        -> "i";
                case NbtElement.LONG_TYPE       -> "L";
                case NbtElement.FLOAT_TYPE      -> "f";
                case NbtElement.DOUBLE_TYPE     -> "d";
                case NbtElement.STRING_TYPE     -> "Str";
                case NbtElement.LIST_TYPE       -> "[ ]";
                case NbtElement.COMPOUND_TYPE   -> "{ }";
                case NbtElement.INT_ARRAY_TYPE  -> "I[]";
                case NbtElement.LONG_ARRAY_TYPE -> "L[]";
                case NbtElement.BYTE_ARRAY_TYPE -> "B[]";
                default                         -> "?";
            };
        }

        private String getTypeFull() {
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
                default                         -> "unknown";
            };
        }

        // Badge background colour (dark, readable)
        private int getBadgeColor() {
            return switch (nbtType) {
                case NbtElement.BYTE_TYPE       -> 0xFF5C2A00; // burnt orange
                case NbtElement.SHORT_TYPE      -> 0xFF3A2A00; // dark amber
                case NbtElement.INT_TYPE        -> 0xFF002855; // dark blue
                case NbtElement.LONG_TYPE       -> 0xFF2A0055; // dark purple
                case NbtElement.FLOAT_TYPE      -> 0xFF003A1A; // dark green
                case NbtElement.DOUBLE_TYPE     -> 0xFF003A2A; // dark teal
                case NbtElement.STRING_TYPE     -> 0xFF3A2800; // dark yellow
                case NbtElement.LIST_TYPE       -> 0xFF002040; // navy
                case NbtElement.COMPOUND_TYPE   -> 0xFF3A0028; // dark pink
                default                         -> 0xFF222222;
            };
        }

        // Badge text colour (bright, contrasting)
        private int getBadgeTextColor() {
            return switch (nbtType) {
                case NbtElement.BYTE_TYPE       -> 0xFFFF9944;
                case NbtElement.SHORT_TYPE      -> 0xFFFFCC44;
                case NbtElement.INT_TYPE        -> 0xFF74C0FC;
                case NbtElement.LONG_TYPE       -> 0xFFCC99FF;
                case NbtElement.FLOAT_TYPE      -> 0xFF66FF99;
                case NbtElement.DOUBLE_TYPE     -> 0xFF44FFCC;
                case NbtElement.STRING_TYPE     -> 0xFFFFEE44;
                case NbtElement.LIST_TYPE       -> 0xFF55BBFF;
                case NbtElement.COMPOUND_TYPE   -> 0xFFFF77CC;
                default                         -> 0xFFAAAAAA;
            };
        }

        // Type label colour in the row
        private int getTypeTextColor() {
            return switch (nbtType) {
                case NbtElement.INT_TYPE        -> 0xFF74C0FC;
                case NbtElement.LONG_TYPE       -> 0xFFCC99FF;
                case NbtElement.FLOAT_TYPE      -> 0xFF66FF99;
                case NbtElement.DOUBLE_TYPE     -> 0xFF44FFCC;
                case NbtElement.STRING_TYPE     -> 0xFFFFEE44;
                case NbtElement.COMPOUND_TYPE   -> 0xFFFF77CC;
                case NbtElement.LIST_TYPE       -> 0xFF55BBFF;
                default                         -> 0xFFAAAAAA;
            };
        }

        private static String truncate(String s, int max) {
            return s.length() > max ? s.substring(0, max) + "…" : s;
        }

        // Required no-op override for the Entry abstract class
        @Override
        public void render(DrawContext context, int mouseX, int mouseY,
                           boolean hovered, float deltaTicks) {}

        public void setExpanded(boolean expanded) {
            this.expanded = expanded;
        }

        public boolean isExpanded() {
            return expanded;
        }

        // ─────────────────────────────────────────────────────────
        //  Inner: child row for compound/list display
        // ─────────────────────────────────────────────────────────
        private record ChildRow(String key, String value) {}
    }
}