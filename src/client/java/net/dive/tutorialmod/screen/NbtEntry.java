package net.dive.tutorialmod.screen;

import net.minecraft.client.gui.DrawContext;

public interface NbtEntry {
    // ⚠️ สำคัญมาก: ถ้าบรรทัด @Override public void render... ด้านล่างนี้ขีดแดง
    // ให้คุณลบบล็อก render นี้ทิ้งไปเลย -> กด Alt + Insert -> เลือก Override Methods -> หาคำว่า render แล้วกด Enter
    // จากนั้นค่อยก็อปปี้โค้ดด้านในไปใส่ใหม่ครับ (เพราะเวอร์ชันของเกมอาจจะต้องการตัวแปรต่างกันนิดหน่อย)
    void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta);
}
