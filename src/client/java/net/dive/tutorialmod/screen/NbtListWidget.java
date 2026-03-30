package net.dive.tutorialmod.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class NbtListWidget extends ElementListWidget<NbtListWidget.NbtEntry> {

    public NbtListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
        super(client, width, height, y, itemHeight);
    }

    public void addNbtEntry(String key, String value, byte nbtType) {
        this.addEntry(new NbtEntry(this.client, key, value, nbtType, this));
    }

    // Fix: super call prevents StackOverflow
    public void removeEntry(NbtEntry entry) {
        super.removeEntry(entry);
    }

    // Fix: these belong on the WIDGET, not the entry class
    @Override
    public int getRowWidth() {
        return this.width - 20;
    }

    protected int getScrollbarPositionX() {
        return this.width - 8;
    }

    // ─────────────────────────────────────────────
    public static class NbtEntry extends ElementListWidget.Entry<NbtEntry> {

        public final TextFieldWidget keyField;
        public final TextFieldWidget valueField;   // null when nbtType == 1 (boolean)
        public final CheckboxWidget checkboxWidget; // null when not boolean
        public final ButtonWidget deleteButton;
        private final MinecraftClient client;
        private final byte nbtType;

        public NbtEntry(MinecraftClient client, String key, String value, byte nbtType, NbtListWidget parent) {
            this.client = client;
            this.nbtType = nbtType;

            // Key field
            this.keyField = new TextFieldWidget(client.textRenderer, 0, 0, 130, 18, Text.literal("Key"));
            this.keyField.setMaxLength(256);
            this.keyField.setText(key);

            // Delete button
            this.deleteButton = ButtonWidget.builder(
                    Text.literal("X").formatted(Formatting.RED),
                    btn -> parent.removeEntry(this)
            ).dimensions(0, 0, 18, 18).build();

            // Boolean type → checkbox; everything else → text field
            boolean isBoolean = nbtType == 1
                    && (value.equals("true") || value.equals("false")
                    || value.equals("1b") || value.equals("0b"));

            if (isBoolean) {
                boolean checked = value.equals("true") || value.equals("1b");
                this.checkboxWidget = CheckboxWidget.builder(Text.literal(""), client.textRenderer)
                        .checked(checked).build();
                this.valueField = null;
            } else {
                this.valueField = new TextFieldWidget(client.textRenderer, 0, 0, 200, 18, Text.literal("Value"));
                this.valueField.setMaxLength(999999);
                this.valueField.setText(value);
                this.checkboxWidget = null;
            }
        }

        // ── THE critical fix: this is the correct signature for 1.21.x ──
        public void render(DrawContext context, int index, int y, int x,
                           int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float tickDelta) {

            int midY = y + (entryHeight / 2);
            int fieldY = midY - 9; // centres an 18px field

            // Layout constants
            int keyX      = x + 4;
            int tagX      = keyX + 134;  // type badge column
            int valueX    = tagX + 26;
            int deleteX   = x + entryWidth - 22;

            // Clamp value field so it never overlaps the delete button
            int maxValW = deleteX - valueX - 4;

            // ── Position and render key field ──
            this.keyField.setX(keyX);
            this.keyField.setY(fieldY);
            this.keyField.render(context, mouseX, mouseY, tickDelta);

            // ── Type badge ──
            String tag = getTypeTag();
            int tagColor = getTypeColor();
            context.drawTextWithShadow(this.client.textRenderer,
                    Text.literal(tag).formatted(Formatting.GRAY),
                    tagX, midY - 4, tagColor);

            // ── Value: checkbox or text field ──
            if (this.checkboxWidget != null) {
                this.checkboxWidget.setX(valueX);
                this.checkboxWidget.setY(fieldY);
                this.checkboxWidget.render(context, mouseX, mouseY, tickDelta);
            } else if (this.valueField != null) {
                this.valueField.setWidth(Math.max(40, maxValW));
                this.valueField.setX(valueX);
                this.valueField.setY(fieldY);
                this.valueField.render(context, mouseX, mouseY, tickDelta);
            }

            // ── Delete button ──
            this.deleteButton.setX(deleteX);
            this.deleteButton.setY(y + (entryHeight / 2) - 9);
            this.deleteButton.render(context, mouseX, mouseY, tickDelta);

            // ── Hover highlight ──
            if (hovered) {
                context.fill(x, y, x + entryWidth, y + entryHeight, 0x18FFFFFF);
            }
        }

        // ── Fix: null-safe element list ──
        @Override
        public List<? extends Element> children() {
            List<Element> list = new ArrayList<>();
            list.add(this.keyField);
            if (this.checkboxWidget != null) list.add(this.checkboxWidget);
            if (this.valueField != null)    list.add(this.valueField);
            list.add(this.deleteButton);
            return list;
        }

        // ── Fix: null-safe selectable list ──
        @Override
        public List<? extends Selectable> selectableChildren() {
            List<Selectable> list = new ArrayList<>();
            list.add(this.keyField);
            if (this.checkboxWidget != null) list.add(this.checkboxWidget);
            if (this.valueField != null)     list.add(this.valueField);
            list.add(this.deleteButton);
            return list;
        }

        // Human-readable NBT type tag
        private String getTypeTag() {
            if (this.checkboxWidget != null) return "[B]"; // byte/boolean
            if (this.valueField == null) return "[?]";
            String v = this.valueField.getText().trim();
            if (v.endsWith("b") || v.endsWith("B")) return "[b]";
            if (v.endsWith("s") || v.endsWith("S")) return "[s]";
            if (v.endsWith("l") || v.endsWith("L")) return "[l]";
            if (v.endsWith("f") || v.endsWith("F")) return "[f]";
            if (v.endsWith("d") || v.endsWith("D")) return "[d]";
            if (v.startsWith("[I;"))                return "[I]";
            if (v.startsWith("[L;"))                return "[L]";
            if (v.startsWith("[B;"))                return "[B]";
            if (v.startsWith("{"))                  return "[{}]";
            if (v.startsWith("["))                  return "[[]]";
            try { Integer.parseInt(v);  return "[i]"; } catch (NumberFormatException ignored) {}
            try { Double.parseDouble(v); return "[d]"; } catch (NumberFormatException ignored) {}
            return "[s]";
        }

        private int getTypeColor() {
            String tag = getTypeTag();
            return switch (tag) {
                case "[B]", "[b]" -> 0xFF9944; // orange — byte/bool
                case "[i]"        -> 0x55AAFF; // blue   — int
                case "[l]"        -> 0xAA55FF; // purple — long
                case "[f]", "[d]" -> 0x55FF99; // green  — float/double
                case "[s]"        -> 0xFFFFAA; // yellow — string
                case "[{}]"       -> 0xFF5555; // red    — compound
                case "[[]]","[I]","[L]" -> 0xFF55FF; // pink — arrays
                default           -> 0xAAAAAA;
            };
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {

        }
    }
}
