package net.dive.tutorialmod.screen;

import net.dive.tutorialmod.network.SaveNbtPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.text.Text;

public class NbtEditorScreen extends Screen {
    private final String editType;
    private final String targetInfo;
    private final String initialNbt;
    private NbtListWidget listWidget;

    public NbtEditorScreen(String editType, String targetInfo, String initialNbt) {
        super(Text.literal("NBT Editor"));
        this.editType = editType;
        this.targetInfo = targetInfo;
        this.initialNbt = initialNbt;
    }
    public boolean shouldPause() {
        return true;
    }

    @Override
    protected void init() {
        super.init();

        this.listWidget = new NbtListWidget(this.client, this.width, this.height - 105, 40, 25);

        // 🟢 1. บังคับสร้างข้อมูลทดสอบ (Dummy) 1 อัน
        // ถ้าอันนี้โชว์ขึ้นมา แปลว่าระบบกล่องข้อความทำงานปกติ 100% ครับ
        this.listWidget.addNbtEntry("dummy_test", "Hello NBT World!", (byte) 8);

        try {
            // ใน 1.21 อาจจะใช้ StringNbtReader.parse() (อ้างอิงจาก Error ในรูปที่คุณส่งมา)
            NbtCompound nbt = StringNbtReader.readCompound(this.initialNbt);
            for (String key : nbt.getKeys()) {
                NbtElement element = nbt.get(key);
                if (element != null) {
                    this.listWidget.addNbtEntry(key, String.valueOf(element.asString()), (byte) element.getType());
                }
            }
        } catch (Exception e) {
            // 🔴 2. เปลี่ยนให้มัน Print Error ออกมาแบบเต็มๆ จะได้รู้ว่าทำไมถึงอ่าน NBT เกมไม่ได้
            System.out.println("❌ อ่านข้อมูล NBT ไม่สำเร็จ! สาเหตุ:");
            e.printStackTrace();
        }

        this.addDrawableChild(this.listWidget);
        int btnWidth = 100;
        int btnHeight = 20;

        // 💡 2. ขยับปุ่ม Add ลงมานิดนึง ให้อยู่ระหว่างขอบล่างของ List กับปุ่ม Save/Cancel
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add"), button -> {
            this.listWidget.addNbtEntry("new_key", "0", (byte) 8); // 8 คือ String
        }).dimensions(this.width / 2 - 50, this.height - 60, 100, btnHeight).build());

        int bottomY = this.height - 30; // 💡 ปุ่ม Save กับ Cancel อยู่ที่เดิม ปลอดภัยแล้ว
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
            saveNbt();
        }).dimensions(this.width / 2 - btnWidth - 5, bottomY, btnWidth, btnHeight).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            this.close();
        }).dimensions(this.width / 2 + 5, bottomY, btnWidth, btnHeight).build());
    }
    private void saveNbt() {
        try {
            NbtCompound newNbt = new NbtCompound();

            for (NbtListWidget.NbtEntry entry : this.listWidget.children()) {
                String key = entry.keyField.getText();
                if (key.trim().isEmpty()) continue;

                if (entry.checkboxWidget != null) {
                    newNbt.putBoolean(key, entry.checkboxWidget.isChecked());
                } else if (entry.valueField != null) {
                    String val = entry.valueField.getText();
                    try {
                        NbtCompound temp = StringNbtReader.readCompound("{temp:" + val + "}");
                        newNbt.put(key, temp.get("temp"));
                    } catch (Exception e) {
                        newNbt.putString(key, val);
                    }
                }
            }

            ClientPlayNetworking.send(new SaveNbtPayload(this.editType, this.targetInfo, newNbt.toString()));
        } catch (Exception e) {
            System.out.println("เกิดข้อผิดพลาดตอนเซฟ NBT: " + e.getMessage());
        }
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("NBT edit"), this.width / 2, 15, 0xFFFFFF);
    }
}