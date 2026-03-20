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

    // 💡 แก้ไข: ลบ Override Render ที่ไม่ได้ใช้ออก โค้ดจะสั้นและคลีนขึ้น
    public void addNbtEntry(String key, String value, byte nbtType) {
        NbtEntry entry = new NbtEntry(this.client, key, value, nbtType, this);
        this.addEntry(entry);
    }

    // 💡 แก้ไข: ต้องใช้ super. ไม่งั้นพอกดลบปุ๊บ เกมจะแครช (Stack Overflow) ทันที!
    public void removeEntry(NbtEntry entry) {
        super.removeEntry(entry);
    }

    // 💡 แก้ไข: เอาคำว่า abstract ออก
    public static class NbtEntry extends ElementListWidget.Entry<NbtEntry> {
        public final TextFieldWidget keyField;
        public TextFieldWidget valueField;
        public final CheckboxWidget checkboxWidget;
        public final ButtonWidget deleteButton;

        public NbtEntry(MinecraftClient client, String key, String value, byte nbtType, NbtListWidget parent) {

            // 🟢 1. ช่อง Key (ฝั่งซ้าย)
            this.keyField = new TextFieldWidget(client.textRenderer, 0, 0, 100, 20, Text.literal("Key"));
            this.keyField.setMaxLength(256);
            this.keyField.setText(key);
            this.keyField.setEditable(true); // ปลดล็อคให้แก้ไขชื่อ Key ได้แล้ว! 🔓
            this.valueField = new TextFieldWidget(client.textRenderer, 0, 0, 150, 20, Text.literal("Value"));
            this.valueField.setMaxLength(256);
            this.valueField.setText(value);

            // 🟢 2. ปุ่มถังขยะ (สีแดง)
            this.deleteButton = ButtonWidget.builder(Text.literal("❌").formatted(Formatting.RED), button -> {
                parent.removeEntry(this);
            }).dimensions(0, 0, 20, 20).build();

            // 🟢 3. ช่อง Value หรือ Checkbox (ฝั่งขวา)
            if (nbtType == 1 && (value.equals("true") || value.equals("false") || value.equals("0b") || value.equals("1b"))) {
                boolean isChecked = value.contains("true") || value.equals("1b");
                this.checkboxWidget = CheckboxWidget.builder(Text.literal("เปิด/ปิด"), client.textRenderer)
                        .checked(isChecked).build();
                this.valueField = null;
            } else {
                this.valueField = new TextFieldWidget(client.textRenderer, 0, 0, 150, 20, Text.literal("Value"));
                this.valueField.setMaxLength(999999); // ให้พิมพ์ NBT ยาวๆ ได้
                this.valueField.setText(value);
                this.checkboxWidget = null;
            }
        }

        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // (ชื่อพารามิเตอร์ด้านบน IDE อาจจะตั้งให้ต่างจากนี้นิดหน่อย ปล่อยมันไว้นะครับ)

            // 1. อัปเดตตำแหน่ง
            this.keyField.setY(y + 2);
            this.valueField.setY(y + 2);
            this.deleteButton.setY(y);

            this.keyField.setX(x + 5);
            this.valueField.setX(x + 115);
            this.deleteButton.setX(x + entryWidth - 25);

            // 2. สั่งวาด
            this.keyField.render(context, mouseX, mouseY, tickDelta);
            this.valueField.render(context, mouseX, mouseY, tickDelta);
            this.deleteButton.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends Element> children() {
            List<Element> elements = new ArrayList<>();
            elements.add(this.keyField);
            if (this.checkboxWidget != null) elements.add(this.checkboxWidget);
            if (this.valueField != null) elements.add(this.valueField);
            elements.add(this.deleteButton);
            return List.of(this.keyField, this.valueField, this.deleteButton);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            List<Selectable> elements = new ArrayList<>();
            elements.add(this.keyField);
            if (this.checkboxWidget != null) elements.add(this.checkboxWidget);
            if (this.valueField != null) elements.add(this.valueField);
            elements.add(this.deleteButton);
            return List.of(this.keyField, this.valueField, this.deleteButton);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {

        }
        // 💡 เพิ่ม 2 เมธอดนี้เพื่อบังคับให้ List กว้างเต็มจอ และย้าย Scrollbar ไปขวาสุด

        public int getRowWidth() {
            return this.getWidth() - 40; // ขยายความกว้างให้เกือบเต็มหน้าจอ
        }
        protected int getScrollbarPositionX() {
            return this.getWidth() - 10; // ย้ายแทบเลื่อน (Scrollbar) ไปชิดขอบขวาสุด
        }
    }
}