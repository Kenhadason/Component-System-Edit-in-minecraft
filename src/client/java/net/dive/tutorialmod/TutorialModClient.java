package net.dive.tutorialmod; // เช็คชื่อ package ให้ตรงด้วยนะครับ

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

// นำเข้า Payload และ Screen (เช็ค Path ให้ตรงกับโฟลเดอร์ของคุณ)
import net.dive.tutorialmod.network.OpenNbtEditorPayload;
import net.dive.tutorialmod.screen.NbtEditorScreen;

public class TutorialModClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		System.out.println("Initializing Client for NBT Editor...");

		// รับข้อมูลจาก Server ที่ส่งมาผ่านคำสั่ง /nbtedit
		ClientPlayNetworking.registerGlobalReceiver(OpenNbtEditorPayload.ID, (payload, context) -> {

			// ให้ Client รันคำสั่งเปิดหน้าจอ
			context.client().execute(() -> {
				// เปิดหน้าจอ NbtEditorScreen และโยนข้อมูล (Type, Target, NBT) เข้าไป
				MinecraftClient.getInstance().setScreen(
						new NbtEditorScreen(payload.editType(), payload.targetInfo(), payload.nbtData())
				);
			});

		});
	}
}